"""Build-time machine translation of static UI copy only. Never runs in the APK."""
from pathlib import Path
import json, re, urllib.request, urllib.parse, time, concurrent.futures, sys, subprocess, tempfile, shutil
from localize import ROOT, xml
sys.stdout.reconfigure(encoding='utf-8')
CODES = [entry[0] for entry in json.loads((ROOT/'tools/locales.json').read_text(encoding='utf-8'))]
CACHE = ROOT/'tools/translations'; CACHE.mkdir(exist_ok=True)

def request(text, source, target):
    # Use the host's working PowerShell HTTP transport, with data in files (not shell code).
    with tempfile.TemporaryDirectory(dir=CACHE) as temp:
        input_file=Path(temp)/'request.json'; output_file=Path(temp)/'response.txt'
        input_file.write_text(json.dumps({'text':text,'source':source,'target':target},ensure_ascii=False),encoding='utf-8')
        shell = shutil.which('pwsh') or shutil.which('powershell')
        if shell is None:
            raise RuntimeError('PowerShell is required. Install it and add it to PATH.')
        run=subprocess.run([str(shell),'-NoProfile','-File',str(ROOT/'tools/translate_bing_request.ps1'),'-InputFile',str(input_file),'-OutputFile',str(output_file)],capture_output=True)
        if run.returncode: raise RuntimeError(run.stderr.decode('utf-8',errors='replace')[:300])
        return output_file.read_text(encoding='utf-8-sig')
def legacy_request(text, source, target):
    query = urllib.parse.urlencode({'client':'gtx','sl':source,'tl':target,'dt':'t','q':text})
    req=urllib.request.Request('https://translate.googleapis.com/translate_a/single?'+query,headers={'User-Agent':'Mozilla/5.0'})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=25) as response: data=json.load(response)
            return ''.join(part[0] for part in data[0] if part[0])
        except urllib.error.HTTPError as error:
            if error.code == 429:
                if attempt==3: raise
                time.sleep(min(60, max(30, int(error.headers.get('Retry-After','30')))))
            elif attempt==3: raise
            else: time.sleep(2+attempt)
        except Exception:
            if attempt==3: raise
            time.sleep(1+attempt)

def prepare(text):
    text=text.replace('Sade','SADEBRAND').replace('\n',' ')
    return re.sub(r'%(\d+)\$s',lambda m:'PHTOKEN'+m[1]+'END',text)

def restore(text, original):
    text=re.sub(r'SADE\s*BRAND','Sade',text,flags=re.I)
    text=re.sub(r'PH[A-Z\s]*?(\d+)\s*END',lambda m:'%'+m[1]+'$s',text,flags=re.I)
    if sorted(re.findall(r'%\d+\$s',text))!=sorted(re.findall(r'%\d+\$s',original)):
        raise ValueError('Translation changed placeholders: '+text)
    if 'SADEBRAND' in text or 'PHTOKEN' in text: raise ValueError('Unrestored token')
    return text.strip()

def translate(source, source_code, code):
    target={'zh':'zh-Hans', 'sr':'sr-Cyrl'}.get(code,code)
    cache=CACHE/(code+'.json')
    result=json.loads(cache.read_text(encoding='utf-8')) if cache.exists() else {}
    remaining=[(k,v) for k,v in source.items() if k not in result]
    batches=[]; batch=[]; length=0
    for k,v in remaining:
        if length+len(v)+15>850 and batch: batches.append(batch); batch=[]; length=0
        batch.append((k,v)); length+=len(v)+15
    if batch: batches.append(batch)
    for batch in batches:
        text='\n'.join(prepare(v) for k,v in batch)
        output=request(text,source_code,target)
        lines=output.splitlines()
        values=dict(enumerate(lines)) if len(lines)==len(batch) else {}
        for i,(key,original) in enumerate(batch):
            try: result[key]=restore(values[i],original)
            except (KeyError,ValueError): result[key]=restore(request(prepare(original),source_code,target),original)
        cache.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        time.sleep(1)
    assert set(result)==set(source),code
    for k,v in result.items(): restore(v,source[k])
    folder=ROOT/'app/src/main/res'/('values' if code=='en' else 'values-b+'+code.replace('-', '+'))
    folder.mkdir(exist_ok=True)
    (folder/'strings.xml').write_text(xml(result),encoding='utf-8')
    print(code+': '+str(len(result))+' strings',flush=True)
    return result

if __name__=='__main__':
    source=json.loads((ROOT/'tools/strings-tr.json').read_text(encoding='utf-8'))
    if '--english' in sys.argv: translate(source,'tr','en')
    else:
        en=json.loads((CACHE/'en.json').read_text(encoding='utf-8'))
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures={pool.submit(translate,en,'en',c):c for c in CODES if c not in ('en','tr')}
            errors=[]
            for f in concurrent.futures.as_completed(futures):
                try: f.result()
                except Exception as e:
                    errors.append((futures[f],str(e))); print('FAILED',futures[f],str(e),flush=True)
            if errors: sys.exit(1)
