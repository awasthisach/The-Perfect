import copy
import io
import json
import unittest
import zipfile
from datetime import datetime, timedelta, timezone
from unittest.mock import patch
import evidence_contract as ec

SHA = 'a'*40
class EvidenceContractTest(unittest.TestCase):
    def setUp(self):
        now = datetime.now(timezone.utc)
        self.entry = dict(id='EVID-001', control_id='STORAGE-INV-001', test_id='STORAGE-TEST-001', source_commit=SHA,
            ci_run_id='123', ci_job_id=ec.SOURCES['unit'][1], scope='unit', artifact='cpas-evidence-run', result='PASS', environment='github-actions-unit',
            executed_at=(now-timedelta(minutes=1)).isoformat(), expires_at=(now+timedelta(days=1)).isoformat(), reports=[{'path':'reports/'+ 'a'*64 + '.xml','sha256':'a'*64}])
    def check(self, entry):
        return ec.verify_entries([entry], SHA, ['STORAGE-INV-001'], verify=lambda *_: None)
    def test_valid_candidate_contract(self):
        self.assertEqual([], self.check(self.entry))
    def test_negative_contract_cases(self):
        for field, values in {'result':['FAIL',None,'SKIP','success',[],{}], 'source_commit':['b'*40,None],
          'control_id':['DB-INV-001','UNKNOWN'], 'test_id':['DB-TEST-001'], 'scope':['database'],
          'artifact':['unrelated'], 'environment':['',None], 'id':['bad',False], 'unexpected':['value'], 'reports':[[],None], 'ci_run_id':['local-1'],
          'executed_at':['2099-01-01T00:00:00Z','2000-01-01T00:00:00Z','invalid',None],
          'expires_at':['2000-01-01T00:00:00Z','2099-01-01T00:00:00Z','invalid']}.items():
            for value in values:
                with self.subTest(field=field,value=value):
                    entry = copy.deepcopy(self.entry); entry[field]=value
                    self.assertTrue(self.check(entry))
    def test_missing_field_and_coverage(self):
        for field in self.entry:
            entry = copy.deepcopy(self.entry); del entry[field]
            self.assertTrue(self.check(entry), field)
        self.assertTrue(ec.verify_entries([],SHA,ec.CONTROLS))
        self.assertTrue(ec.verify_entries(None,SHA,ec.CONTROLS))
        self.assertTrue(ec.verify_entries([self.entry,self.entry],SHA,['STORAGE-INV-001'],verify=lambda *_:None))
    def test_original_false_pass_is_rejected(self):
        entry=copy.deepcopy(self.entry);entry.update(result='FAIL',source_commit='0'*40)
        self.assertTrue(self.check(entry))
    def test_report_fail_skip_empty_and_malformed(self):
        for xml in [b'<testsuite/>', b'bad', b'<testsuite failures="1"><testcase classname="X"/></testsuite>',
                    b'<testsuite><testcase classname="X"><skipped/></testcase></testsuite>',
                    b'<testsuite><testcase classname="X"><failure/></testcase></testsuite>']:
            with self.subTest(xml=xml), self.assertRaises((ValueError,ec.ET.ParseError)):
                ec.report_classes(xml)
        self.assertEqual({'X'},ec.report_classes(b'<testsuite><testcase classname="X" name="positive"/></testsuite>'))
    def test_provenance_rejects_missing_skipped_cancelled_failed_stale_runs(self):
        base=dict(head_sha=SHA,status='completed',conclusion='success',path='.github/workflows/ci.yml')
        for field, values in {'head_sha':['b'*40,None], 'conclusion':['skipped','cancelled','failure',None], 'status':['in_progress'], 'path':['.github/workflows/unrelated.yml']}.items():
            for value in values:
                run=dict(base);run[field]=value;ec.remote_bundle.cache_clear()
                with patch.object(ec,'gh_json',return_value=run), self.assertRaises(ValueError):
                    ec.remote_bundle('123','unit',SHA)
        for conclusion in ['skipped','cancelled','failure',None]:
            ec.remote_bundle.cache_clear()
            jobs={'jobs':[dict(name=ec.SOURCES['unit'][1],status='completed',conclusion='success',steps=[dict(name='Run unit tests',status='completed',conclusion=conclusion)])]}
            with patch.object(ec,'gh_json',side_effect=[base,{'workflow_runs':[dict(base,id=123)]},jobs]),self.assertRaises(ValueError):
                ec.remote_bundle('123','unit',SHA)
    def test_latest_failed_run_supersedes_earlier_success(self):
        runs = [dict(id=123, head_sha=SHA, status='completed', conclusion='success'),
                dict(id=124, head_sha=SHA, status='completed', conclusion='failure')]
        with patch.object(ec, 'gh_json', return_value={'workflow_runs': runs}):
            self.assertEqual(124, ec.latest_candidate_run('ci.yml', SHA)['id'])
        ec.remote_bundle.cache_clear()
        old = dict(runs[0], path='.github/workflows/ci.yml')
        with patch.object(ec, 'gh_json', side_effect=[old, {'workflow_runs': runs}]), self.assertRaisesRegex(ValueError, 'superseded'):
            ec.remote_bundle('123', 'unit', SHA)

    def test_optional_artifact_digest_must_match_downloaded_archive(self):
        raw=b'<testsuite><testcase classname="com.vvf.smartmanager.core.data.storage.StorageBoundaryTest" name="boundary"/></testsuite>'
        entry=copy.deepcopy(self.entry)
        digest=ec.hashlib.sha256(raw).hexdigest()
        entry['reports']=[{'path':f'reports/{digest}.xml','sha256':digest}]
        archive=unittest.mock.Mock();archive.read.return_value=raw;archive.cpas_sha256='b'*64
        entry['artifact_sha256']='a'*64
        with patch.object(ec,'remote_bundle',return_value=({'entries':[entry]},archive)),self.assertRaisesRegex(ValueError,'archive hash mismatch'):
            ec.verify_remote(entry,SHA)
        entry['artifact_sha256']='b'*64
        with patch.object(ec,'remote_bundle',return_value=({'entries':[entry]},archive)):
            ec.verify_remote(entry,SHA)

    def test_unrelated_report_cannot_claim_control(self):
        raw=b'<testsuite><testcase classname="Unrelated" name="test"/></testsuite>'
        entry=copy.deepcopy(self.entry);digest=ec.hashlib.sha256(raw).hexdigest(); entry['reports']=[{'path':f'reports/{digest}.xml','sha256':digest}]
        archive=unittest.mock.Mock();archive.read.return_value=raw
        with patch.object(ec,'remote_bundle',return_value=({'entries':[entry]},archive)),self.assertRaises(ValueError):
            ec.verify_remote(entry,SHA)

class GeneratorTest(unittest.TestCase):
    def test_database_job_only_claims_database_control(self):
        import tempfile, subprocess, sys
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            reports=root/'core/database/build/outputs/androidTest-results/connected/device'
            reports.mkdir(parents=True)
            (reports/'TEST.xml').write_text('<testsuite><testcase classname="com.vvf.smartmanager.core.database.EncryptedDatabaseLifecycleTest" name="lifecycle"/></testsuite>')
            out=root/'evidence'
            subprocess.run([sys.executable,str(Path(__file__).with_name('generate_evidence.py')),'--root',str(root),'--commit-sha',SHA,'--run-id','123','--scope','database','--output',str(out)],check=True,capture_output=True)
            entries=json.loads((out/'bundle.json').read_text())['entries']
            self.assertEqual(['DB-INV-001'],[e['control_id'] for e in entries])
            self.assertTrue((out/entries[0]['reports'][0]['path']).is_file())
    def test_missing_reports_never_create_pass_bundle(self):
        import tempfile, subprocess, sys
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'evidence'
            result=subprocess.run([sys.executable,str(Path(__file__).with_name('generate_evidence.py')),'--root',str(root),'--commit-sha',SHA,'--run-id','123','--scope','unit','--output',str(out)],capture_output=True)
            self.assertNotEqual(0,result.returncode)
            self.assertFalse((out/'bundle.json').exists())

class OriginalProbeIntegrationTest(unittest.TestCase):
    def test_actual_verifier_rejects_original_false_pass_with_risks_isolated(self):
        import tempfile, subprocess, shutil, re, sys
        from pathlib import Path
        repo=Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            shutil.copytree(repo/'docs',root/'docs')
            for directory in repo.iterdir():
                if directory.is_dir() and directory.name not in {'docs','.git'}:
                    (root/directory.name).symlink_to(directory,target_is_directory=True)
            # Resolve the same HEAD without touching the real checkout or tracked documents.
            gitdir=subprocess.check_output(['git','rev-parse','--absolute-git-dir'],cwd=repo,text=True).strip()
            (root/'.git').write_text('gitdir: '+gitdir+'\n')
            risks=root/'docs/assurance/08-risk-register.yaml'
            risks.write_text(re.sub(r'status: (triaged|repair_implemented)', 'status: closed', risks.read_text()))
            ledger=root/'docs/assurance/historical-evidence-ledger.json';d=json.loads(ledger.read_text())
            for entry in d['entries']: entry.update(result='FAIL',source_commit='0'*40)
            ledger.write_text(json.dumps(d))
            result=subprocess.run([sys.executable,str(repo/'tools/audit/cpas_verify.py'),'--root',str(root),'--ledger',str(ledger)],text=True,capture_output=True)
            self.assertEqual(1,result.returncode,result.stderr)
            status=json.loads(result.stdout)
            checks={c['name']:c['status'] for c in status['checks']}
            self.assertEqual('PASS', checks['risk_register_gate'])
            self.assertEqual('BLOCKED', checks['candidate_control_provenance'])
            self.assertEqual('BLOCKED', status['production_status']['value'])
            # CI-only verification does not close owner-controlled high risks.
            opened, closed = risks.read_text().split('closed_findings:', 1)
            risks.write_text(opened.replace('status: closed', 'status: ci_verified') + 'closed_findings:' + closed)
            result=subprocess.run([sys.executable,str(repo/'tools/audit/cpas_verify.py'),'--root',str(root),'--ledger',str(ledger)],text=True,capture_output=True)
            self.assertEqual(1,result.returncode,result.stderr)
            self.assertIn('PROD-008',json.loads(result.stdout)['blocking_findings'])

class GeneratorIsolationTest(unittest.TestCase):
    def test_unrelated_skip_is_ignored_but_required_skip_and_malformed_xml_block(self):
        import tempfile, subprocess, sys
        from pathlib import Path
        for extra, succeeds in [('<testsuite><testcase classname="Unrelated"><skipped/></testcase></testsuite>',True),
                                ('<testsuite><testcase classname="com.vvf.smartmanager.core.database.EncryptedDatabaseLifecycleTest"><skipped/></testcase></testsuite>',False),
                                ('<broken',False)]:
            with self.subTest(extra=extra), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); reports=root/'core/database/build/outputs/androidTest-results/connected/device';reports.mkdir(parents=True)
                (reports/'valid.xml').write_text('<testsuite><testcase classname="com.vvf.smartmanager.core.database.EncryptedDatabaseLifecycleTest" name="lifecycle"/></testsuite>')
                (reports/'extra.xml').write_text(extra)
                out=root/'bundle'
                result=subprocess.run([sys.executable,str(Path(__file__).with_name('generate_evidence.py')),'--root',str(root),'--commit-sha',SHA,'--run-id','123','--scope','database','--output',str(out)],capture_output=True)
                self.assertEqual(succeeds,result.returncode==0,result.stderr)
                self.assertEqual(succeeds,(out/'bundle.json').exists())
