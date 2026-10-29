"""Verify pinned AARs and prepare private resources without logging credentials."""
import hashlib
import json
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def prepare():
    records = json.loads((ROOT / 'scripts/baidu-sdk-lock.json').read_text(encoding='utf-8'))
    jars = []
    for item in records:
        folder = ROOT / '.tooling/baidu' / (item['artifact'] + '-' + item['version'])
        aar = folder / (folder.name + '.aar')
        if hashlib.sha256(aar.read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError('SDK artifact hash mismatch: ' + item['artifact'])
        with zipfile.ZipFile(aar) as archive:
            for member in archive.infolist():
                if not (folder / 'unpacked' / member.filename).resolve().is_relative_to((folder / 'unpacked').resolve()):
                    raise RuntimeError('Unsafe SDK archive entry')
            archive.extractall(folder / 'unpacked')
        jars.extend(sorted((folder / 'unpacked').rglob('*.jar')))
    (ROOT / 'build/sdk-jars.txt').write_text('\n'.join(str(p) for p in jars), encoding='utf-8')
    key = (ROOT / '.tooling/baidu-ak.txt').read_text(encoding='utf-8-sig').strip()
    if not re.fullmatch('[A-Za-z0-9]{20,80}', key):
        raise RuntimeError('Missing or invalid local Android AK')
    res = ET.Element('resources')
    ET.SubElement(res, 'string', name='baidu_ak', translatable='false').text = key
    ET.ElementTree(res).write(ROOT / 'build/wifi-res/values/baidu.xml', encoding='utf-8', xml_declaration=True)
    print('Verified pinned map/location SDK dependencies; private resource prepared')

if __name__ == '__main__':
    prepare()
