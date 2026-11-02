"""Apply the shared dialog palette to app-owned dialogs."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/sade/launcher'
for name in ['SettingsActivity.kt','MainActivity.kt','FocusActivity.kt','Lang.kt']:
    file = src/name
    text = file.read_text(encoding='utf-8')
    text = text.replace('AlertDialog.Builder(this)', 'AlertDialog.Builder(DialogStyle.context(this))')
    text = text.replace('AlertDialog.Builder(activity)', 'AlertDialog.Builder(DialogStyle.context(activity))')
    text = text.replace('TimePickerDialog(this,', 'TimePickerDialog(DialogStyle.context(this),')
    text = text.replace('AlertDialog.Builder(DialogStyle.context(this))', 'DialogStyle.builder(this)')
    text = text.replace('AlertDialog.Builder(DialogStyle.context(activity))', 'DialogStyle.builder(activity)')
    text = text.replace('TimePickerDialog(DialogStyle.context(this), {', 'TimePickerDialog(DialogStyle.context(this), R.style.SadeDialogOverlay, {')
    file.write_text(text,encoding='utf-8')
for name, fill, stroke in [('paper','#F3F0E8','#40272B27'),('white','#FFFFFF','#40272B27'),
                           ('black','#000000','#50EAE7DE'),('sage','#202724','#50EAE7DE')]:
    (root/f'app/src/main/res/drawable/dialog_{name}.xml').write_text(f'''<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{fill}" />
    <corners android:radius="20dp" />
    <stroke android:width="1dp" android:color="{stroke}" />
</shape>
''',encoding='utf-8')
