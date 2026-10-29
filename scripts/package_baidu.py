"""Merge official AAR assets/native libraries into the direct-toolchain APK."""
from pathlib import Path
import json
import zipfile
ROOT = Path(__file__).resolve().parents[1]
members = {}
records = json.loads((ROOT / 'scripts/baidu-sdk-lock.json').read_text(encoding='utf-8'))
for item in records:
    folder = ROOT / '.tooling/baidu' / (item['artifact'] + '-' + item['version']) / 'unpacked'
    for source, prefix in [('assets', 'assets'), ('jni', 'lib')]:
        for file in (folder / source).rglob('*'):
            if not file.is_file():
                continue
            relative = file.relative_to(folder / source)
            if source == 'jni' and relative.parts[0] not in ('arm64-v8a', 'armeabi-v7a', 'x86_64'):
                continue
            key = prefix + '/' + relative.as_posix()
            value = file.read_bytes()
            if key in members and members[key] != value:
                raise ValueError('Conflicting SDK file: ' + key)
            members[key] = value
with zipfile.ZipFile(ROOT / 'build/unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    for key, value in members.items():
        archive.writestr(key, value)
print(f'Packaged {len(members)} SDK assets/native libraries')
