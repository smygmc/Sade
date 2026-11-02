import json
from pathlib import Path
cache = Path(__file__).resolve().parent / 'translations'
titles = {
'de':'Uhr & Datum','fr':'Horloge et date','es':'Reloj y fecha','it':'Orologio e data','pt':'Relógio e data','nl':'Klok en datum','sv':'Klocka och datum','nb':'Klokke og dato','da':'Ur og dato','fi':'Kello ja päivämäärä','is':'Klukka og dagsetning','pl':'Zegar i data','cs':'Hodiny a datum','sk':'Hodiny a dátum','hu':'Óra és dátum','ro':'Ceas și dată','bg':'Часовник и дата','el':'Ρολόι και ημερομηνία','hr':'Sat i datum','sr':'Сат и датум','bs':'Sat i datum','sl':'Ura in datum','sq':'Ora dhe data','mk':'Часовник и датум','uk':'Годинник і дата','be':'Гадзіннік і дата','ru':'Часы и дата','et':'Kell ja kuupäev','lv':'Pulkstenis un datums','lt':'Laikrodis ir data','ga':'Clog agus dáta','mt':'Arloġġ u data','cy':'Cloc a dyddiad','ca':'Rellotge i data','eu':'Erlojua eta data','gl':'Reloxo e data','lb':'Auer an Datum','hy':'Ժամացույց և ամսաթիվ','ka':'საათი და თარიღი','ar':'الساعة والتاريخ','fa':'ساعت و تاریخ','ja':'時計と日付','ko':'시계 및 날짜','zh':'时钟与日期','zh-Hant':'時鐘與日期','he':'שעון ותאריך','id':'Jam dan tanggal','th':'นาฬิกาและวันที่'}
for code, title in titles.items():
    p=cache/(code+'.json')
    data=json.loads(p.read_text(encoding='utf-8'))
    data['ui_024']=title
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
