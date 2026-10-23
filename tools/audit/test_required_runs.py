import unittest
from require_workflow_runs import validate_run, validate_codeql_checks, require_codeql

class RequiredRunsTest(unittest.TestCase):
    def test_only_complete_exact_sha_success_passes(self):
        run=dict(head_sha='a'*40,status='completed',conclusion='success',path='.github/workflows/ci.yml')
        step=dict(name='test',status='completed',conclusion='success')
        job=dict(name='job',status='completed',conclusion='success',steps=[step])
        def check(r=run,jobs=None):
            return validate_run(r,[job] if jobs is None else jobs,'a'*40,'ci.yml','job',['test'])
        self.assertTrue(check())
        self.assertTrue(check(dict(run, path='.github/workflows/ci.yml@main')))
        self.assertTrue(check(dict(run, path='.github/workflows/ci.yml@refs/heads/candidate/test')))
        self.assertFalse(check(dict(run, path='.github/workflows/ci.yml@')))
        self.assertFalse(check(dict(run, path='.github/workflows/other.yml@main')))
        self.assertFalse(check(dict(run, path=None)))
        self.assertFalse(check(jobs=[]))
        self.assertFalse(check(jobs=[dict(job,steps=[])]))
        self.assertFalse(check(dict(run,head_sha='b'*40)))
        for outcome in ['skipped','cancelled','failure',None,'neutral']:
            self.assertFalse(check(dict(run,conclusion=outcome)))
            self.assertFalse(check(jobs=[dict(job,conclusion=outcome)]))
            self.assertFalse(check(jobs=[dict(job,steps=[dict(step,conclusion=outcome)])]))

    def test_latest_unsuccessful_run_blocks_despite_previous_success(self):
        import require_workflow_runs as gate
        from unittest.mock import patch
        for conclusion in ['failure', 'cancelled', 'skipped', None]:
            run = dict(id=2, head_sha='a'*40, path='.github/workflows/ci.yml', status='completed', conclusion=conclusion)
            with self.subTest(conclusion=conclusion), patch('sys.argv', ['gate', '--revision', 'a'*40]), \
                    patch.object(gate, 'REQUIRED', {'ci.yml': ('job', ['test'])}), \
                    patch.object(gate, 'latest_candidate_run', return_value=run), \
                    patch.object(gate, 'gh_json', return_value={'jobs': []}), self.assertRaises(SystemExit):
                gate.main()

    def test_codeql_security_conclusion_requires_latest_exact_trusted_check(self):
        sha = 'a' * 40
        check = dict(id=10, name='CodeQL', head_sha=sha, status='completed', conclusion='success',
                     app=dict(id=57789, slug='github-advanced-security'))
        self.assertTrue(validate_codeql_checks([check], sha))
        self.assertFalse(validate_codeql_checks([], sha))
        self.assertFalse(validate_codeql_checks([dict(check, head_sha='b'*40)], sha))
        self.assertFalse(validate_codeql_checks([dict(check, app={'id': 1, 'slug': 'github-advanced-security'})], sha))
        self.assertFalse(validate_codeql_checks([dict(check, app=None)], sha))
        for outcome in ['failure', 'skipped', 'cancelled', 'neutral', None]:
            newer = dict(check, id=20, conclusion=outcome)
            self.assertFalse(validate_codeql_checks([newer, check], sha))
            self.assertFalse(validate_codeql_checks([check, newer], sha))
        self.assertFalse(validate_codeql_checks([dict(check, status='in_progress')], sha))

    def test_codeql_paginates_and_cannot_hide_newer_failure(self):
        from unittest.mock import patch
        sha = 'a'*40
        check = dict(id=1, name='CodeQL', head_sha=sha, status='completed', conclusion='success',
                     app=dict(id=57789, slug='github-advanced-security'))
        batches = [{'check_runs': [dict(check, id=i+1) for i in range(100)]},
                   {'check_runs': [dict(check, id=101, conclusion='failure')]}]
        with patch('require_workflow_runs.gh_json', side_effect=batches) as api, self.assertRaises(SystemExit):
            require_codeql(sha)
        self.assertEqual(2, api.call_count)

    def test_release_token_can_read_security_conclusions(self):
        from pathlib import Path
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/release.yml').read_text()
        self.assertIn('  checks: read\n', workflow.split('jobs:')[0])
