"""Move duration units from headings to fixed, localized field suffixes."""
import json, re
from localize import ROOT

units = {
    'en': ('min', 'sec'), 'tr': ('dakika', 'saniye'), 'de': ('Min.', 'Sek.'),
    'is': ('mín', 'sek'), 'hu': ('perc', 'mp'), 'bg': ('мин', 'сек'),
    'el': ('λεπτά', 'δευτ.'), 'sr': ('мин', 'сек'), 'mk': ('мин', 'сек'),
    'uk': ('хв', 'с'), 'be': ('хв', 'с'), 'ru': ('мин', 'с'),
    'lv': ('min', 'sek'), 'ga': ('nóim', 'soic'), 'cy': ('mun', 'eil'),
    'hy': ('րոպե', 'վրկ'), 'ka': ('წთ', 'წმ'), 'ar': ('دقيقة', 'ثانية'),
    'fa': ('دقیقه', 'ثانیه'), 'ja': ('分', '秒'), 'ko': ('분', '초'),
    'zh': ('分钟', '秒'), 'zh-Hant': ('分鐘', '秒'), 'he': ('דק׳', 'שנ׳'),
    'id': ('mnt', 'dtk'), 'th': ('นาที', 'วินาที')
}
for code, _ in json.loads((ROOT/'tools/locales.json').read_text(encoding='utf-8')):
    file = ROOT/'tools/strings-tr.json' if code == 'tr' else ROOT/f'tools/translations/{code}.json'
    data = json.loads(file.read_text(encoding='utf-8'))
    for key in ('ui_085', 'ui_088', 'ui_131', 'ui_103'):
        data[key] = re.split('[·・]', data[key], maxsplit=1)[0].strip()
    data['duration_minutes'], data['duration_seconds'] = units.get(code, ('min', 's'))
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
