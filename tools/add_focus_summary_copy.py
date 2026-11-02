import json
from localize import ROOT
for code, values in {
    'en': {'focus_off': 'Off', 'focus_warn_only': 'Warn only at the daily limit',
        'focus_step_apps': 'Which apps?', 'focus_step_limit': 'What is your daily limit?',
        'focus_step_reminder': 'When should we remind you?', 'focus_step_pause': 'Pause before opening?',
        'focus_step_schedule': 'When should apps be blocked?'},
    'tr': {'focus_off': 'Kapalı', 'focus_warn_only': 'Günlük limitte yalnızca uyar',
        'focus_step_apps': 'Hangi uygulamalar?', 'focus_step_limit': 'Günlük sınır ne olsun?',
        'focus_step_reminder': 'Ne zaman hatırlatalım?', 'focus_step_pause': 'Açmadan önce bekleyelim mi?',
        'focus_step_schedule': 'Hangi saatlerde engellensin?'}
}.items():
    path = ROOT/'tools/strings-tr.json' if code == 'tr' else ROOT/'tools/translations/en.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update(values)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
