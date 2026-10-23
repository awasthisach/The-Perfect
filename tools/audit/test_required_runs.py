import unittest
from require_workflow_runs import validate_run

class RequiredRunsTest(unittest.TestCase):
    def test_only_complete_exact_sha_success_passes(self):
        run=dict(head_sha='a'*40,status='completed',conclusion='success',path='.github/workflows/ci.yml')
        step=dict(name='test',status='completed',conclusion='success')
        job=dict(name='job',status='completed',conclusion='success',steps=[step])
        def check(r=run,jobs=None):
            return validate_run(r,[job] if jobs is None else jobs,'a'*40,'ci.yml','job',['test'])
        self.assertTrue(check())
        self.assertFalse(check(jobs=[]))
        self.assertFalse(check(jobs=[dict(job,steps=[])]))
        self.assertFalse(check(dict(run,head_sha='b'*40)))
        for outcome in ['skipped','cancelled','failure',None,'neutral']:
            self.assertFalse(check(dict(run,conclusion=outcome)))
            self.assertFalse(check(jobs=[dict(job,conclusion=outcome)]))
            self.assertFalse(check(jobs=[dict(job,steps=[dict(step,conclusion=outcome)])]))
