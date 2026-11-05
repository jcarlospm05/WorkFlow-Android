"""Offline structure/security checks. Does NOT replace Kotlin compilation or Android tests."""
from pathlib import Path
import re, xml.etree.ElementTree as ET, json
root = Path(__file__).resolve().parents[1]
checks = []
def check(name, ok):
    checks.append({'check': name, 'passed': bool(ok)})
    if not ok: raise AssertionError(name)
xml_files = list(root.glob('app/src/main/**/*.xml'))
for path in xml_files: ET.parse(path)
check('All Android XML files parse', bool(xml_files))
a = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
check('Android backup disabled', app.get(a+'allowBackup') == 'false')
check('Cleartext disabled', app.get(a+'usesCleartextTraffic') == 'false')
check('No Internet or broad storage permission', {p.get(a+'name') for p in manifest.findall('uses-permission')} <= {'android.permission.USE_BIOMETRIC'})
provider = app.find('provider')
check('FileProvider private and transient grants', provider.get(a+'exported') == 'false' and provider.get(a+'grantUriPermissions') == 'true')
paths = ET.parse(root / 'app/src/main/res/xml/file_paths.xml').getroot()
check('Only scoped cache share directory exposed', len(paths) == 1 and paths[0].tag == 'cache-path' and paths[0].get('path') == 'shared/')
kt = list(root.rglob('*.kt'))
all_code = '\n'.join(p.read_text() for p in kt)
check('No destructive Room fallback', 'fallbackToDestructiveMigration' not in all_code)
check('No debug signing in release config', 'signingConfigs.getByName("debug")' not in (root/'app/build.gradle.kts').read_text())
workflow = (root/'.github/workflows/android.yml').read_text()
check('CI checks not ignored', 'continue-on-error' not in workflow and ':core:test' in workflow and ':app:lintDebug' in workflow)
codec = (root/'core/src/main/kotlin/com/workflow/core/DailyCodec.kt').read_text()
check('Bounded JSON and version checks present', all(s in codec for s in ['MAX_BYTES', 'depth <= 32', 'schemaVersion == 1', 'ignoreUnknownKeys = false']))
repo = (root/'app/src/main/java/com/workflow/operaciones/data/WorkflowRepository.kt').read_text()
check('Transactional writes and atomic files present', 'db.withTransaction' in repo and 'AtomicFile' in repo and 'validateStockHistory' in repo)
check('JSON data saved with revision inside transaction', 'dao.putDocument(DocumentRow' in repo)
check('No recognizable embedded credential', not re.search(r'(?:AKIA[A-Z0-9]{16}|AIza[\w-]{35}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{30,})', all_code))
check('Safe areas and keyboard accommodation declared', 'WindowInsets.safeDrawing' in all_code and '.imePadding()' in all_code)
unit = root/'core/src/test/kotlin/com/workflow/core/DailyCodecTest.kt'
check('Protocol behavior tests present', unit.read_text().count('@Test') >= 10)
print(json.dumps({'scope': 'Static offline checks only; no Kotlin/Android execution', 'kotlin_files': len(kt), 'checks': checks}, ensure_ascii=False, indent=2))
