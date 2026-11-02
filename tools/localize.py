"""One-time extraction of Kotlin UI literals into standard Android resources."""
from pathlib import Path
import json, re, html, sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/sade/launcher'
CAT = ROOT / 'tools/strings-tr.json'
catalog = {}
lookup = {}
ASCII_UI = {'Saat','Siyah','Beyaz','Sade','Kitap','Daktilo','Kurulum','Devam','Tamam','Uygula','Sil','Kaydet','Dil','Gruplar','Bilgi'}

def string(s, i):
    j = i + 1; start = j; parts = []
    while j < len(s):
        if s[j] == '\\': j += 2; continue
        if s[j] == '"':
            parts.append(('text', s[start:j])); return j + 1, parts
        if s[j] == '$':
            match = re.match(r'\$([A-Za-z_]\w*)', s[j:])
            if s[j:j+2] == '${':
                parts.append(('text',s[start:j])); k = code_end(s,j+2)
                parts.append(('expr',s[j+2:k])); j=k+1; start=j; continue
            if match:
                parts.append(('text',s[start:j])); parts.append(('expr',match[1])); j+=len(match[0]); start=j; continue
        j += 1
    raise ValueError('Unclosed Kotlin string')

def code_end(s,i):
    level=1
    while i<len(s):
        if s[i]=='"': i,_=string(s,i); continue
        if s[i]=='{': level+=1
        if s[i]=='}':
            level-=1
            if not level: return i
        i+=1
    raise ValueError('Unclosed interpolation')

def human(text):
    if text in ('d MMMM','HH:mm') or '\\s*' in text: return False
    if re.search('[çğıöşüÇĞİÖŞÜ]',text): return True
    return text in ASCII_UI or bool(re.search('[A-Za-z]',text) and ' ' in text)

def convert(s, context, filename):
    output=[]; i=0
    while i<len(s):
        if s.startswith('//',i):
            j=s.find('\n',i); j=len(s) if j<0 else j
            output.append(s[i:j]); i=j; continue
        if s.startswith('/*',i):
            j=s.index('*/',i)+2; output.append(s[i:j]); i=j; continue
        if s[i]!='"': output.append(s[i]); i+=1; continue
        end,parts=string(s,i)
        literal=''.join(v for t,v in parts if t=='text')
        line=s[s.rfind('\n',0,i)+1:i]
        eligible=human(literal) and '@Deprecated' not in line
        if filename=='Lang.kt' and i<s.find('fun choose'): eligible=False
        if eligible:
            args=[]; text=''
            for t,v in parts:
                if t=='expr':
                    args.append(convert(v,context,filename)); text+=f'%{len(args)}$s'
                else: text+=v.replace('\\n','\n').replace('\\"','"').replace('\\\'','\'')
            key=lookup.get(text)
            if key is None:
                key=f'ui_{len(catalog)+1:03d}'; lookup[text]=key; catalog[key]=text
            output.append(f'{context}(R.string.{key}'+('，' if False else '')+''.join(', '+a for a in args)+')')
        else:
            rebuilt='"'
            for t,v in parts: rebuilt+=v if t=='text' else '${'+convert(v,context,filename)+'}'
            output.append(rebuilt+'"')
        i=end
    return ''.join(output)

def xml(data):
    def escape(s):
        return html.escape(s,quote=False).replace('\\','\\\\').replace("'","\\'").replace('"','\\"').replace('\n','\\n')
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+''.join(f'    <string name="{k}">{escape(v)}</string>\n' for k,v in data.items())+'</resources>\n'

if __name__=='__main__':
    assert not CAT.exists(), 'Extraction already completed; do not run twice'
    changes={}
    for name in ['MainActivity.kt','SettingsActivity.kt','FocusActivity.kt','FocusService.kt','Lang.kt']:
        context='getString' if name.endswith('Activity.kt') else ('activity.getString' if name=='Lang.kt' else 'Lang.wrap(this@FocusService).getString')
        path=SRC/name; changes[path]=convert(path.read_text(encoding='utf-8-sig'),context,name)
    import xml.etree.ElementTree as ET
    for e in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot(): catalog[e.attrib['name']]=e.text
    CAT.write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
    for path,code in changes.items(): path.write_text(code,encoding='utf-8')
    (ROOT/'app/src/main/res/values/strings.xml').write_text(xml(catalog),encoding='utf-8')
    tr=ROOT/'app/src/main/res/values-tr'; tr.mkdir(exist_ok=True)
    (tr/'strings.xml').write_text(xml(catalog),encoding='utf-8')
    print(json.dumps(catalog,ensure_ascii=False,indent=2))
