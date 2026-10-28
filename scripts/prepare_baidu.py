"""Fetch the pinned official Maven Central SDK artifacts; never handles the AK."""
import hashlib
import json
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tooling' / 'baidu'
REPO = 'https://repo.maven.apache.org/maven2/'
NS = {'p': 'http://maven.apache.org/POM/4.0.0'}
seen, records = set(), []

def fetch(group, name, version):
    identity = (group, name, version)
    if identity in seen:
        return
    seen.add(identity)
    folder = CACHE / f'{name}-{version}'
    folder.mkdir(parents=True, exist_ok=True)
    base = REPO + group.replace('.', '/') + f'/{name}/{version}/{name}-{version}'
    def download(ext):
        path = folder / f'{name}-{version}.{ext}'
        if not path.exists():
            with urllib.request.urlopen(base + '.' + ext, timeout=60) as response:
                path.write_bytes(response.read())
        return path
    pom = ET.parse(download('pom')).getroot()
    packaging = pom.findtext('p:packaging', 'jar', NS)
    artifact = download(packaging)
    if packaging == 'aar':
        with zipfile.ZipFile(artifact) as archive:
            for member in archive.infolist():
                if not (folder / 'unpacked' / member.filename).resolve().is_relative_to((folder / 'unpacked').resolve()):
                    raise ValueError('Unsafe archive path')
            archive.extractall(folder / 'unpacked')
    records.append({'artifact': name, 'version': version, 'url': base + '.' + packaging,
                    'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()})
    print(f'Ready {name} {version}', flush=True)
    for dep in pom.findall('p:dependencies/p:dependency', NS):
        if dep.findtext('p:scope', '', NS) == 'test' or dep.findtext('p:optional', '', NS) == 'true':
            continue
        fetch(dep.findtext('p:groupId', namespaces=NS), dep.findtext('p:artifactId', namespaces=NS),
              dep.findtext('p:version', namespaces=NS))

for artifact, version in [('BaiduMapSDK_Map', '8.2.0'), ('BaiduMapSDK_Util', '8.2.0'), ('BaiduMapSDK_Search', '8.2.0'), ('BaiduMapSDK_Location', '9.6.9')]:
    fetch('com.baidu.lbsyun', artifact, version)
(CACHE / 'dependencies.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
