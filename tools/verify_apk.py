"""Inspect the main APK; instrumentation and sample documents are separate."""
from pathlib import Path
import hashlib,json,sys,zipfile
apk=Path(sys.argv[1])
with zipfile.ZipFile(apk) as z:
    names=z.namelist()
    assert not any(n.lower().endswith(('.sqlite','.sqlite3','.db','.p12','.jks','.keystore')) for n in names), 'Unexpected database or private signing material'
    assert not any(n.startswith('assets/') and n.lower().endswith(('.json','.csv','.workflow','.pdf','.jpg')) for n in names), 'Unexpected business/sample assets'
    dex=b''.join(z.read(n) for n in names if n.endswith('.dex'))
    for marker in ['Persona sintética','Responsable sintético','Persona de prueba','Operador sintético','Supervisora de prueba']:
        assert marker.encode() not in dex, 'Synthetic test record in main APK'
    fonts={
      'nunito_medium':'74920a51bc96148840daa512e1e7f132bc4acc422389a558f85df9602769bbce',
      'nunito_regular':'ce4a6ed4e751214fe08c58e1f7e42b0dd2119cafa2f0bf8aa06f77ab63e9ec6c',
      'nunito_semibold':'8697328550b10d787f15eecdbbf8bff1339c5f3423a89cb8eb0ad7e2e0d1ea24'
    }
    for name,expected in fonts.items():
        assert hashlib.sha256(z.read('res/font/'+name+'.ttf')).hexdigest()==expected, 'Original font changed: '+name
result={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'no_preloaded_business_files':True,'no_synthetic_test_markers':True,'no_private_signing_material':True,'original_fonts_verified':True}
target=Path('app/build/reports/apk-verification.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
