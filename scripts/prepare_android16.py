"""Verify Google's repository checksum and install two isolated Android 16 AVDs."""
from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
archive=root/'.tooling/android16-google-apis-r07.zip'
repository=ET.parse(root/'.tooling/google-sysimages36.xml').getroot()
package=next(p for p in repository if p.tag.endswith('remotePackage') and p.attrib.get('path')=='system-images;android-36;google_apis;x86_64')
def element(name): return next(e for e in package.iter() if e.tag.split('}')[-1]==name)
expected=element('checksum').text
hasher=hashlib.sha1()
with archive.open('rb') as stream:
    for block in iter(lambda:stream.read(1024*1024),b''):hasher.update(block)
if hasher.hexdigest()!=expected:raise SystemExit('Official Android image checksum mismatch')
target=root/'.tooling/android-sdk/system-images/android-36/google_apis'
target.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(archive) as z:
    for info in z.infolist():
        if not (target/info.filename).resolve().is_relative_to(target.resolve()):raise ValueError('Unsafe image archive path')
    z.extractall(target)
for name in ('QingxingAndroid16','QingxingSmoke16'):
    avd=root/f'.tooling/avd/{name}.avd'
    if avd.exists():raise SystemExit(f'Refusing to overwrite existing AVD: {name}')
    avd.mkdir()
    config=f'''AvdId = {name}
avd.ini.displayname = {name}
abi.type = x86_64
hw.cpu.arch = x86_64
hw.cpu.ncore = 2
hw.ramSize = 2560
hw.lcd.width = 1080
hw.lcd.height = 2400
hw.lcd.density = 420
hw.keyboard = yes
hw.gpu.enabled = yes
hw.gpu.mode = swiftshader
disk.dataPartition.size = 6442450944
image.sysdir.1 = system-images\\android-36\\google_apis\\x86_64\\
tag.id = google_apis
tag.display = Google APIs
PlayStore.enabled = false
showDeviceFrame = no
fastboot.forceColdBoot = yes
hw.audioInput = no
hw.audioOutput = yes
hw.gps = yes
hw.battery = yes
'''
    (avd/'config.ini').write_text(config,encoding='utf-8')
    # Android native tools use the existing ASCII junction to avoid Unicode path bugs.
    import os
    alias=Path(os.environ['TEMP'])/'qingxing-android-runtime'
    (root/f'.tooling/avd/{name}.ini').write_text(f'avd.ini.encoding=UTF-8\npath={alias}/avd/{name}.avd\npath.rel=avd/{name}.avd\ntarget=android-36\n',encoding='utf-8')
record={'api':36,'android':'16','tag':'google_apis','revision':7,'url':'https://dl.google.com/android/repository/sys-img/google_apis/x86_64-36_r07.zip','sha1':expected,'avds':['QingxingAndroid16','QingxingSmoke16']}
(root/'docs/ANDROID16-image.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
print('Verified official Android 16 image; created visible-preview and disposable-test AVDs.')
