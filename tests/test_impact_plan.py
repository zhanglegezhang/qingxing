"""Impact selection and exact candidate resume guards. No emulator or real release touched."""
import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import release_workflow as w
from impact_plan import JAVA, SUITES, MANDATORY, select, hash_plan

class ImpactPolicy(unittest.TestCase):
    def test_location_excludes_calendar_address_history(self):
        p=select([JAVA+'BaiduLocator.java'], True)
        self.assertFalse(p['full'])
        self.assertEqual(set(p['required']),set(MANDATORY)|{'location','android','baidu-location'})
        self.assertTrue({'holiday','baidu-map','baidu-address','upgrade','smoke'}.issubset(p['skipped']))
    def test_ui_covers_actions_and_states(self):
        p=select([JAVA+'Screens.java'],True)
        self.assertIn('smoke',p['required']);self.assertIn('android',p['required'])
    def test_workday_includes_location_and_reminders(self):
        p=select([JAVA+'HolidayCalendar.java'],True)
        self.assertTrue({'holiday','location','android'}.issubset(p['required']))
    def test_map_and_address_are_separate_from_location(self):
        p=select([JAVA+'CompanyAddress.java'],True)
        self.assertIn('baidu-address',p['required']);self.assertNotIn('baidu-location',p['required'])
    def test_union_of_impacts(self):
        p=select([JAVA+'BaiduLocator.java',JAVA+'CompanyAddress.java'],True)
        self.assertTrue({'location','baidu-address','baidu-map'}.issubset(p['required']))
    def test_unknown_shared_scripts_sdk_are_full(self):
        for name in [JAVA+'NewFeature.java',JAVA+'Store.java',JAVA+'MainActivity.java',JAVA+'BaiduSupport.java','scripts/release_workflow.py','.tooling/baidu/dependencies.json','app/src/main/AndroidManifest.xml']:
            with self.subTest(name=name):self.assertEqual(select([name],True)['required'],list(SUITES))
    def test_missing_baseline_and_explicit_full(self):
        self.assertTrue(select([],None)['full']);self.assertTrue(select([],True,True)['full'])
    def test_additions_never_remove_required(self):
        p=select([JAVA+'BaiduLocator.java'],True,additions=['holiday'])
        self.assertTrue(set(MANDATORY)|{'location','holiday'}<=set(p['required']))
        with self.assertRaises(ValueError):select([],True,additions=['typo'])
    def test_docs_do_not_add_functional_suites(self):
        self.assertEqual(select(['README.md','docs/BUILD.md'],True)['required'],list(MANDATORY))
    def test_deleted_unknown_and_fixture_changes_full(self):
        self.assertTrue(select(['app/deleted.java'],True)['full'])
        self.assertTrue(select(['tests/fixtures/changed.apk'],True)['full'])
    def test_version_only_keeps_mandatory(self):
        self.assertEqual(select(['release/version.json'],True)['required'],list(MANDATORY))

class ResumeGuards(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.path=self.root/'candidate.json';(self.root/'evidence').mkdir()
        plan=select([],True)
        self.data={'testPlan':plan,'planSha256':hash_plan(plan),'sha256':'apk','sourceSha256':'source','baseTests':True,'tests':{}}
    def tearDown(self):self.tmp.cleanup()
    def passing(self,suite):
        reports={}
        for name in w.REPORTS[suite]:
            p=self.root/'evidence'/name;p.write_text('PASS exact candidate')
            reports[name]=w.digest(p)
        self.data['tests'][suite]=dict(passed=True,reports=reports,**{k:self.data[k] for k in ('sha256','sourceSha256','planSha256')})
    def test_matching_result_reused(self):
        self.passing('smoke-min');self.assertTrue(w.suite_passed(self.path,self.data,'smoke-min'))
    def test_changed_hash_source_plan_fail(self):
        self.passing('smoke-min')
        for key in ('sha256','sourceSha256','planSha256'):
            original=self.data[key];self.data[key]='changed'
            self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'));self.data[key]=original
    def test_missing_tampered_and_empty_reports_fail(self):
        self.passing('smoke-min');self.data['tests']['smoke-min']['reports']={}
        self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'))
        self.passing('smoke-min');(self.root/'evidence'/'smoke-min-test-output.txt').write_text('tampered')
        self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'))
        self.passing('smoke-min');(self.root/'evidence'/'smoke-min-test-output.txt').unlink()
        self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'))
    def test_failed_or_unrun_never_count(self):
        self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'))
        self.passing('smoke-min');self.data['tests']['smoke-min']['passed']=False
        self.assertFalse(w.suite_passed(self.path,self.data,'smoke-min'))
    def test_required_subset_is_enough_but_missing_is_blocked(self):
        for suite in MANDATORY:self.passing(suite)
        w.check_evidence(self.path,self.data)
        del self.data['tests']['latest-upgrade']
        with self.assertRaisesRegex(RuntimeError,'exact-APK'):w.check_evidence(self.path,self.data)
    def test_plan_cannot_drop_mandatory(self):
        self.data['testPlan']['required']=[];self.data['planSha256']=hash_plan(self.data['testPlan'])
        with self.assertRaisesRegex(RuntimeError,'test plan'):w.check_evidence(self.path,self.data)
    def test_retry_runs_only_failed_or_missing(self):
        self.passing('latest-upgrade')
        with patch.object(w,'validate_candidate',return_value=(self.data,self.root/'candidate.apk')),patch.object(w,'ensure_new_release'),patch.object(w,'test') as execute,patch.object(w,'promote') as promote:
            w.release_candidate(self.path)
            self.assertEqual(execute.call_args_list[0].args[1],'smoke-min');self.assertEqual(execute.call_count,1);promote.assert_called_once()
    def test_failure_stops_before_promotion(self):
        with patch.object(w,'validate_candidate',return_value=(self.data,self.root/'candidate.apk')),patch.object(w,'ensure_new_release'),patch.object(w,'test',side_effect=RuntimeError('FAIL')),patch.object(w,'promote') as promote:
            with self.assertRaises(RuntimeError):w.release_candidate(self.path)
            promote.assert_not_called()
    def test_changed_plan_rejected_even_with_recomputed_hash(self):
        self.data['testPlan']['required']=list(SUITES);self.data['planSha256']=hash_plan(self.data['testPlan'])
        with patch.object(w,'make_plan',return_value=select([],True)):
            with self.assertRaisesRegex(RuntimeError,'plan/baseline'):w.validate_plan(self.data,self.root)

class ReportGuards(unittest.TestCase):
    def test_empty_crash_buffers_are_valid_for_both_smoke_groups(self):
        for name in ('smoke-crash-buffer.txt','smoke-min-crash-buffer.txt'):w.validate_report(name,'')
    def test_real_crash_rejected_for_both_smoke_groups(self):
        for name in ('smoke-crash-buffer.txt','smoke-min-crash-buffer.txt'):
            with self.assertRaisesRegex(RuntimeError,'Crash recorded'):w.validate_report(name,'FATAL EXCEPTION')
    def test_empty_or_failed_functional_report_rejected(self):
        for content in ('','FAIL assertion','PASS 12 checks; FAIL later'):
            with self.assertRaisesRegex(RuntimeError,'Missing passing'):w.validate_report('location-test-output.txt',content)
        w.validate_report('location-test-output.txt','PASS 32 checks')

class BaselineGuards(unittest.TestCase):
    def test_no_baseline_full_and_bad_baseline_blocks(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            self.assertTrue(w.make_plan(root)['full'])
            w.write(root/'release/latest.json',{'apk':'dist/releases/x/app.apk','sha256':'wrong'})
            (root/'dist/releases/x').mkdir(parents=True);(root/'dist/releases/x/app.apk').write_bytes(b'bad')
            with self.assertRaisesRegex(RuntimeError,'baseline APK mismatch'):w.make_plan(root)
    def test_real_difference_and_documentation_only(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);folder=root/'dist/releases/1.0.0';folder.mkdir(parents=True)
            apk=folder/'app.apk';apk.write_bytes(b'apk');w.write(folder/'inputs.json',{})
            w.write(root/'release/latest.json',dict(apk='dist/releases/1.0.0/app.apk',sha256=w.digest(apk),sourceSha256=w.fingerprint({}),versionName='1.0.0',versionCode=1))
            (root/'README.md').write_text('docs')
            self.assertTrue(w.make_plan(root)['documentationOnly'])
            w.write(folder/'inputs.json',{'tampered':'bad'})
            with self.assertRaisesRegex(RuntimeError,'baseline input'):w.make_plan(root)

if __name__=='__main__':unittest.main(verbosity=2)
