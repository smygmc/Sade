import json
from localize import ROOT

updates = {
    'en': {
        'ui_046': 'Use selected color as wallpaper',
        'ui_049': 'Stop using Sade as Home',
        'ui_050': 'Use Sade as Home',
        'ui_062': 'Your wallpaper will follow Sade’s color. Choosing another wallpaper stops syncing. The old image is not saved; a linked lock screen may also change.',
        'choose_other_home': 'Choose another Home app from the list.'
    },
    'tr': {
        'ui_046': 'Seçili rengi duvar kâğıdı yap',
        'ui_049': 'Sade’yi ana ekran olarak kullanmayı bırak',
        'ui_050': 'Sade’yi ana ekran olarak kullan',
        'ui_062': 'Duvar kâğıdı Sade’nin rengiyle eşleşir. Başka bir duvar kâğıdı seçersen eşleştirme durur. Eski resim kaydedilmez; bağlı kilit ekranı da değişebilir.',
        'choose_other_home': 'Listeden başka bir ana ekran uygulaması seç.'
    }
}
for code, _ in json.loads((ROOT/'tools/locales.json').read_text(encoding='utf-8')):
    path = ROOT/'tools/strings-tr.json' if code == 'tr' else ROOT/f'tools/translations/{code}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    if code in updates: data.update(updates[code])
    else:
        for key in updates['en']: data.pop(key, None)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
