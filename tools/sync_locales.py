"""Keep the picker, Android locale declarations and packaged languages in sync."""
import json, re
from pathlib import Path
from localize import ROOT

entries = json.loads((ROOT/'tools/locales.json').read_text(encoding='utf-8'))
codes = [code for code, name in entries]
assert len(codes) == len(set(codes)) == 50
source = ROOT/'app/src/main/java/com/sade/launcher/Lang.kt'
text = source.read_text(encoding='utf-8')
for declaration, values in [('val codes', codes), ('private val names', [name for code, name in entries])]:
    array = ', '.join(json.dumps(v, ensure_ascii=False) for v in values)
    text, count = re.subn(r'(?m)^    '+declaration+r' = arrayOf\([^\n]*\)', '    '+declaration+' = arrayOf('+array+')', text)
    assert count == 1
source.write_text(text, encoding='utf-8')
config = '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'
config += ''.join(f'    <locale android:name="{code}" />\n' for code in codes)
config += '</locale-config>\n'
(ROOT/'app/src/main/res/xml/locales_config.xml').write_text(config, encoding='utf-8')

# Only remove generated string files for retired locales, after all replacements exist.
res = (ROOT/'app/src/main/res').resolve()
expected = {'values' if c == 'en' else 'values-tr' if c == 'tr' else 'values-b+'+c.replace('-', '+') for c in codes}
assert all((res/folder/'strings.xml').is_file() for folder in expected)
for file in res.glob('values-b+*/strings.xml'):
    assert file.resolve().is_relative_to(res)
    if file.parent.name not in expected:
        file.unlink()
        if not any(file.parent.iterdir()): file.parent.rmdir()
print('Synced 50 language options; retired translations remain in tools/translations.')
