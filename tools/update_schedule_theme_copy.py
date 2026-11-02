import json
from localize import ROOT
updates = {
    'en': {'ui_023': 'Screen time control', 'ui_106': 'Remove custom limit',
        'theme_menu': 'Theme', 'focus_previous': 'Previous step', 'focus_back_summary': 'Back to summary',
        'focus_all_day': 'All day', 'focus_block_days': 'Blocked days',
        'focus_distinct_times': 'Choose different start and end times, or enable All day.'},
    'tr': {'ui_023': 'Ekran süresi kontrolü', 'ui_106': 'Özel limiti kaldır',
        'theme_menu': 'Tema', 'focus_previous': 'Önceki adım', 'focus_back_summary': 'Özete dön',
        'focus_all_day': 'Tüm gün', 'focus_block_days': 'Engellenecek günler',
        'focus_distinct_times': 'Farklı başlangıç ve bitiş saatleri seç veya Tüm gün seçeneğini aç.'}
}
for code, _ in json.loads((ROOT/'tools/locales.json').read_text(encoding='utf-8')):
    path = ROOT/'tools/strings-tr.json' if code == 'tr' else ROOT/f'tools/translations/{code}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    if code in updates: data.update(updates[code])
    else:
        for key in updates['en']: data.pop(key, None)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
