"""Shared, fail-closed CPAS report contract. GitHub is the provenance authority.

Reports prove only their mapped tests ran. They do not close owner/device risks.
"""
from __future__ import annotations
import hashlib
import io
import json
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone, timedelta
from functools import lru_cache

REPOSITORY = 'awasthisach/The-Perfect'
# Explicit test classes, not a job-wide PASS flag. Adding claims requires review.
CONTROLS = {
    'STORAGE-INV-001': ('STORAGE-TEST-001', ['com.vvf.smartmanager.core.data.storage.StorageBoundaryTest']),
    'STORAGE-INV-002': ('STORAGE-TEST-002', ['com.vvf.smartmanager.core.data.storage.StorageReadPathTest']),
    'RESTORE-INV-001': ('RESTORE-TEST-001', ['com.vvf.smartmanager.core.domain.restore.FailClosedRestorePipelineTest', 'com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplierTest', 'com.vvf.smartmanager.core.domain.restore.impl.Sha256BackupVerifierTest']),
    'WORK-INV-001': ('WORK-TEST-001', ['com.vvf.smartmanager.core.background.BackgroundSyncManagerTest', 'com.vvf.smartmanager.core.domain.cloud.DurableUploadContractTest']),
    'DB-INV-001': ('DB-TEST-001', ['com.vvf.smartmanager.core.database.EncryptedDatabaseLifecycleTest']),
}
SOURCES = {
    'unit': ('ci.yml', 'Build, Test, Lint & Quality', 'Run unit tests', 'cpas-evidence-run'),
    'database': ('weekly-instrumented.yml', 'SQLCipher Instrumented (hard)', 'SQLCipher instrumentation (HARD)', 'weekly-cpas-evidence'),
}


def report_classes(raw: bytes) -> set[str]:
    if b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
        raise ValueError('XML entities are not evidence')
    root = ET.fromstring(raw)
    cases = list(root.iter('testcase'))
    if not cases or any(list(case) for case in cases if any(child.tag in {'failure', 'error', 'skipped'} for child in case)):
        raise ValueError('empty, failed or skipped report')
    for suite in root.iter('testsuite'):
        if any(int(suite.get(key, '0')) != 0 for key in ('failures', 'errors', 'skipped', 'disabled')):
            raise ValueError('unsuccessful test suite')
    return {case.attrib['classname'] for case in cases}


def validate_entry(entry, candidate: str, now=None) -> None:
    now = now or datetime.now(timezone.utc)
    if not isinstance(entry, dict) or entry.get('result') != 'PASS':
        raise ValueError('evidence result must be PASS')
    control = entry.get('control_id')
    if control not in CONTROLS or entry.get('test_id') != CONTROLS[control][0]:
        raise ValueError('unsupported control/test mapping')
    if entry.get('source_commit') != candidate:
        raise ValueError('evidence revision differs from candidate')
    start = datetime.fromisoformat(str(entry['executed_at']).replace('Z', '+00:00'))
    end = datetime.fromisoformat(str(entry['expires_at']).replace('Z', '+00:00'))
    max_age = timedelta(days=14 if control == 'DB-INV-001' else 30)
    if not start.tzinfo or not end.tzinfo or not start <= now < end or now-start > max_age or end-start > max_age:
        raise ValueError('stale, future or invalid evidence dates')
    scope = 'database' if control == 'DB-INV-001' else 'unit'
    if entry.get('scope') != scope or entry.get('artifact') != SOURCES[scope][3] or entry.get('ci_job_id') != SOURCES[scope][1]:
        raise ValueError('job cannot claim this control')
    for report in entry.get('reports', []) or []:
        if not isinstance(report, dict) or not isinstance(report.get('sha256'), str) or len(report['sha256']) != 64 or any(c not in '0123456789abcdef' for c in report['sha256']) or report.get('path') != f"reports/{report['sha256']}.xml":
            raise ValueError('invalid report identity')
    if not str(entry.get('ci_run_id', '')).isdigit() or not isinstance(entry.get('reports'), list) or not entry['reports']:
        raise ValueError('missing run or reports')


def gh_json(endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPOSITORY}/{endpoint}'], stderr=subprocess.DEVNULL))


def latest_candidate_run(workflow: str, candidate: str):
    runs = gh_json(f'actions/workflows/{workflow}/runs?head_sha={candidate}&per_page=100')['workflow_runs']
    matching = [run for run in runs if run.get('head_sha') == candidate]
    if not matching:
        raise ValueError('no run exists for required candidate workflow')
    # Do not query status=success: a newer failed/cancelled run supersedes old evidence.
    return max(matching, key=lambda run: int(run['id']))


@lru_cache(maxsize=32)
def remote_bundle(run_id: str, scope: str, candidate: str):
    workflow, job_name, step_name, artifact_name = SOURCES[scope]
    run = gh_json(f'actions/runs/{run_id}')
    if run.get('head_sha') != candidate or run.get('status') != 'completed' or run.get('conclusion') != 'success' or run.get('path') != f'.github/workflows/{workflow}':
        raise ValueError('run is not successful exact-candidate evidence from required workflow')
    if str(latest_candidate_run(workflow, candidate)['id']) != str(run_id):
        raise ValueError('evidence run was superseded')
    jobs = gh_json(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
    jobs = [j for j in jobs if j.get('name') == job_name and j.get('conclusion') == 'success' and j.get('status') == 'completed']
    if len(jobs) != 1 or not any(s.get('name') == step_name and s.get('conclusion') == 'success' and s.get('status') == 'completed' for s in jobs[0]['steps']):
        raise ValueError('required job/test step did not succeed')
    artifacts = gh_json(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    artifacts = [a for a in artifacts if a.get('name') == artifact_name and not a.get('expired')]
    if len(artifacts) != 1:
        raise ValueError('required artifact missing or ambiguous')
    raw = subprocess.check_output(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{artifacts[0]["id"]}/zip'], stderr=subprocess.DEVNULL)
    archive = zipfile.ZipFile(io.BytesIO(raw))
    if len(archive.namelist()) != len(set(archive.namelist())):
        raise ValueError('duplicate archive members')
    bundle = json.loads(archive.read('bundle.json'))
    return bundle, archive


def verify_remote(entry, candidate):
    validate_entry(entry, candidate)
    bundle, archive = remote_bundle(str(entry['ci_run_id']), entry['scope'], candidate)
    if entry not in bundle.get('entries', []):
        raise ValueError('ledger claim differs from published artifact')
    classes = set()
    for report in entry['reports']:
        raw = archive.read(report['path'])
        if hashlib.sha256(raw).hexdigest() != report['sha256']:
            raise ValueError('test report hash mismatch')
        classes.update(report_classes(raw))
    if not set(CONTROLS[entry['control_id']][1]).issubset(classes):
        raise ValueError('claimed control was not executed')


def verify_entries(entries, candidate, required, verify=verify_remote):
    errors = []
    if not isinstance(entries, list) or not entries:
        return ['missing evidence']
    seen = set()
    for entry in entries:
        try:
            validate_entry(entry, candidate)
            if entry['control_id'] in seen:
                raise ValueError('duplicate control evidence')
            verify(entry, candidate)
            seen.add(entry['control_id'])
        except (ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError, ET.ParseError, zipfile.BadZipFile):
            errors.append('invalid, unverified or inconsistent control evidence')
    if set(required) - seen:
        errors.append('missing verified required controls: ' + ', '.join(sorted(set(required)-seen)))
    return errors
