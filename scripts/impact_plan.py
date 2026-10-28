"""Conservative file-level impact policy. Unknown changes always expand to full."""
import hashlib
import json

SUITES = ('latest-upgrade', 'upgrade', 'layout-upgrade', 'android', 'location', 'holiday',
          'baidu-location', 'baidu-map', 'baidu-address', 'smoke-min', 'smoke')
MANDATORY = ('latest-upgrade', 'smoke-min')
POLICY_VERSION = 1
JAVA = 'app/src/main/java/cn/qingxing/reminder/'
MAPPING = {
    'BaiduLocator.java': ('location', 'android', 'baidu-location'),
    'LocationHealth.java': ('location', 'android', 'baidu-location'),
    'MonitorService.java': ('location', 'android', 'baidu-location'),
    'Rules.java': ('location', 'android', 'holiday'),
    'Planner.java': ('android', 'location', 'holiday', 'smoke'),
    'AlarmReceiver.java': ('android', 'location', 'holiday', 'smoke'),
    'Alerts.java': ('android', 'location', 'smoke'),
    'HolidayCalendar.java': ('holiday', 'android', 'location', 'smoke'),
    'HolidayParser.java': ('holiday', 'android', 'location'),
    'HolidaySync.java': ('holiday', 'android', 'location'),
    'HolidaySyncJob.java': ('holiday', 'android', 'location'),
    'CompanyAddress.java': ('baidu-address', 'baidu-map', 'smoke'),
    'MapActivity.java': ('baidu-map', 'baidu-address', 'smoke'),
    'Screens.java': ('smoke', 'android', 'location', 'holiday'),
    # MainActivity, Store, AttendanceData and BaiduSupport cross many boundaries: full.
}
TEST_MAPPING = {
    'LocationTests.java': ('location',), 'LocationHealthTest.java': ('location',),
    'RulesTest.java': ('location', 'android', 'holiday'), 'HolidayTests.java': ('holiday',),
    'HolidayParserTest.java': ('holiday',), 'FlowTests.java': ('android', 'location', 'holiday', 'smoke'),
    'SmokeTests.java': ('smoke',), 'MinimalSmokeTests.java': ('smoke-min',),
    'LayoutUpgradeProbe.java': ('latest-upgrade', 'layout-upgrade'),
    'UpgradeProbe.java': ('upgrade',), 'CompactUpgradeProbe.java': ('upgrade',),
    'BaiduTests.java': ('baidu-location', 'baidu-map', 'baidu-address'),
}


def hash_plan(plan):
    return hashlib.sha256(json.dumps(plan, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def select(changed, baseline, force_full=False, additions=()):
    unknown_additions = set(additions) - set(SUITES)
    if unknown_additions:
        raise ValueError('Unknown additional suites: ' + ', '.join(sorted(unknown_additions)))
    reasons = {s: ['mandatory each release'] for s in MANDATORY}
    full_reasons = []
    if not baseline:
        full_reasons.append('no verified baseline')
    if force_full:
        full_reasons.append('explicit full validation')
    for path in sorted(changed):
        if path == 'release/version.json':
            continue  # Version declarations are checked independently; changed fixtures remain conservative.
        if path.endswith('.md') and path != 'AGENTS.md' or path.startswith('docs/'):
            continue
        suites = None
        if path.startswith(JAVA):
            suites = MAPPING.get(path[len(JAVA):])
        elif path.startswith('tests/'):
            suites = TEST_MAPPING.get(path[len('tests/'):])
        if suites is None:
            full_reasons.append('unclassified or shared infrastructure: ' + path)
        else:
            for suite in suites:
                reasons.setdefault(suite, []).append(path)
    if full_reasons:
        for suite in SUITES:
            reasons.setdefault(suite, []).extend(full_reasons)
    for suite in sorted(set(additions)):
        reasons.setdefault(suite, []).append('explicit additional coverage')
    return {
        'policyVersion': POLICY_VERSION, 'forcedFull': force_full, 'full': bool(full_reasons),
        'additions': sorted(set(additions)), 'changed': sorted(changed),
        'required': [s for s in SUITES if s in reasons], 'reasons': reasons,
        'skipped': {s: 'no mapped impact; not tested on this APK' for s in SUITES if s not in reasons},
    }
