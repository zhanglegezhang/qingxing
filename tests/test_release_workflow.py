"""Release safeguards, with temporary dummy files. No real APKs or devices modified."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('workflow', Path(__file__).resolve().parents[1] / 'scripts/release_workflow.py')
w = importlib.util.module_from_spec(spec)
spec.loader.exec_module(w)


class ReleaseGuards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.version = {'versionName': '0.5.3', 'versionCode': 9}

    def evidence_data(self, **extra):
        plan = w.select([], True, force_full=True)
        return dict(baseTests=True, testPlan=plan, planSha256=w.hash_plan(plan), sourceSha256='source', sha256='apk', tests={}, **extra)

    def tearDown(self):
        self.temp.cleanup()

    def test_process_lock_blocks_other_process_and_recovers(self):
        code = "import importlib.util,pathlib; s=importlib.util.spec_from_file_location('w',%r);w=importlib.util.module_from_spec(s);s.loader.exec_module(w);\nwith w.exclusive(pathlib.Path(%r)): print('ACQUIRED')" % (str(Path(w.__file__)), str(self.root))
        with w.exclusive(self.root):
            second = subprocess.run([sys.executable, '-c', code], capture_output=True)
            self.assertNotEqual(second.returncode, 0)
            self.assertIn(b'Another build/test/release', second.stderr)
        recovered = subprocess.run([sys.executable, '-c', code], capture_output=True)
        self.assertEqual(recovered.returncode, 0, recovered.stderr)

    def test_published_directory_cannot_be_replaced(self):
        published = self.root / 'dist/releases/0.5.3'
        published.mkdir(parents=True)
        (published / 'keep.apk').write_bytes(b'original')
        with self.assertRaisesRegex(RuntimeError, 'cannot be overwritten'):
            w.ensure_new_release(self.version, self.root)
        self.assertEqual((published / 'keep.apk').read_bytes(), b'original')

    def test_old_flat_apk_also_blocks_same_version(self):
        apk = self.root / 'dist/Qingxing-0.5.3.apk'
        apk.parent.mkdir()
        apk.write_bytes(b'existing')
        with self.assertRaises(RuntimeError):
            w.ensure_new_release(self.version, self.root)
        self.assertEqual(apk.read_bytes(), b'existing')

    def test_nonincreasing_version_code_rejected(self):
        w.write(self.root / 'release/latest.json', {'versionCode': 9})
        with self.assertRaisesRegex(RuntimeError, 'must increase'):
            w.ensure_new_release({'versionName': '0.5.4', 'versionCode': 9}, self.root)

    def test_failed_suite_does_not_update_release_pointer(self):
        latest = self.root / 'release/latest.json'
        w.write(latest, {'versionName': '0.5.2', 'versionCode': 8})
        original = latest.read_bytes()
        data = dict(self.version, **self.evidence_data())
        with patch.object(w, 'validate_candidate', return_value=(data, self.root / 'candidate.apk')):
            with self.assertRaisesRegex(RuntimeError, 'passing exact-APK'):
                w.promote(self.root / 'candidate.json', self.root)
        self.assertEqual(latest.read_bytes(), original)
        self.assertFalse((self.root / 'dist/releases/0.5.3').exists())

    def test_skipped_tests_cannot_release(self):
        with self.assertRaisesRegex(RuntimeError, 'skipped'):
            w.check_evidence(self.root / 'candidate.json', {'baseTests': False})

    def test_reports_bound_to_exact_apk(self):
        records = {name: {'passed': True, 'sha256': 'other-apk', 'reports': {}} for name in w.REQUIRED}
        with self.assertRaisesRegex(RuntimeError, 'exact-APK'):
            w.check_evidence(self.root / 'candidate.json', dict(self.evidence_data(), sha256='expected-apk', tests=records))

    def test_changed_report_rejected(self):
        evidence = self.root / 'evidence'
        evidence.mkdir()
        (evidence / 'report.txt').write_text('tampered')
        data = self.evidence_data()
        tests = {suite: {'passed': True, 'sha256': 'apk', 'sourceSha256':'source', 'planSha256':data['planSha256'], 'reports': {'report.txt': 'original-hash'}} for suite in w.REQUIRED}
        with self.assertRaisesRegex(RuntimeError, 'evidence changed'):
            w.check_evidence(self.root / 'candidate.json', dict(self.evidence_data(), tests=tests))

    def test_source_drift_rejects_candidate(self):
        apk = self.root / 'candidate.apk'
        apk.write_bytes(b'apk')
        manifest = self.root / 'candidate.json'
        w.write(manifest, dict(self.version, file='candidate.apk', sha256=w.digest(apk), sourceSha256='old'))
        with patch.object(w, 'version', return_value=self.version), patch.object(w, 'inputs', return_value={'code': 'changed'}):
            with self.assertRaisesRegex(RuntimeError, 'inputs changed'):
                w.validate_candidate(manifest)

    def test_atomic_pointer_and_no_temporary_residue(self):
        target = self.root / 'latest.json'
        w.write(target, {'versionCode': 8})
        w.write(target, {'versionCode': 9})
        self.assertEqual(w.read(target)['versionCode'], 9)
        self.assertEqual(len(list(self.root.iterdir())), 1)

    def test_two_and_three_component_versions_and_unsafe_names(self):
        for name in ('1.0', '1.0.1', '0.8.1'):
            w.write(self.root / 'release/version.json', dict(versionName=name, versionCode=21))
            self.assertEqual(w.version(self.root)['versionName'], name)
        for name in ('1', '1.0.0.0', '../1.0', '1.0/evil', '1.0-beta', ''):
            w.write(self.root / 'release/version.json', dict(versionName=name, versionCode=21))
            with self.assertRaisesRegex(RuntimeError, 'Invalid release/version'):
                w.version(self.root)


if __name__ == '__main__':
    unittest.main(verbosity=2)
