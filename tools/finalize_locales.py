"""Validate and package all supported locales. No network access."""
import json, re, sys
from localize import ROOT, xml
from translate_ui import CODES, CACHE
sys.stdout.reconfigure(encoding='utf-8')
NUMERIC = {'ui_072':[1], 'ui_076':[1], 'ui_099':[2], 'ui_108':[1], 'ui_122':[1], 'ui_123':[1], 'ui_134':[1]}
def typed(data):
    data=dict(data)
    for key,positions in NUMERIC.items():
        for position in positions: data[key]=data[key].replace(f'%{position}$s',f'%{position}$d')
    return data
FIXES = {
    'es': {'ui_004':'Segunda página. Ir a la pantalla de inicio','ui_124':'Ir al inicio','ui_018':'Empezar'},
    'fr': {'ui_004':"Deuxième page. Aller à l’écran d’accueil",'ui_124':"Retour à l’accueil"},
    'de': {'ui_004':'Zweite Seite. Zum Startbildschirm','ui_124':'Zum Startbildschirm'},
    'pt': {'ui_004':'Segunda página. Ir para a tela inicial','ui_124':'Ir para a tela inicial'},
    'it': {'ui_004':'Seconda pagina. Vai alla schermata iniziale','ui_124':'Vai alla schermata iniziale'}
}
if __name__=='__main__':
    base=typed(json.loads((CACHE/'en.json').read_text(encoding='utf-8')))
    for code in CODES:
        path=ROOT/'tools/strings-tr.json' if code=='tr' else CACHE/(code+'.json')
        if not path.exists(): raise ValueError('Missing locale: '+code)
        data=typed(json.loads(path.read_text(encoding='utf-8')))
        data.update(FIXES.get(code,{}))
        assert set(data)==set(base), 'Missing keys: '+code
        for key,value in data.items():
            assert value.strip(), (code,key,'empty')
            assert sorted(re.findall(r'%\d+\$[sd]',value))==sorted(re.findall(r'%\d+\$[sd]',base[key])), (code,key,'format mismatch')
            assert not re.search(r'PHTOKEN|SADEBRAND|ZQ\d+Z',value,re.I), (code,key,'translation token')
        folder=ROOT/'app/src/main/res'/('values' if code=='en' else 'values-tr' if code=='tr' else 'values-b+'+code.replace('-', '+'))
        folder.mkdir(exist_ok=True)
        (folder/'strings.xml').write_text(xml(data),encoding='utf-8')
    print(f'Validated {len(CODES)} locales × {len(base)} strings, including all format arguments.')
