import subprocess,re,json,sys
pdf=sys.argv[1]
n=int(re.search(r'Pages:\s+(\d+)',subprocess.run(['pdfinfo',pdf],capture_output=True,text=True).stdout).group(1))
pages={p:subprocess.run(['pdftotext','-f',str(p),'-l',str(p),'-layout',pdf,'-'],capture_output=True,text=True).stdout for p in range(1,n+1)}
def norm(s): return re.sub(r'\s+',' ',s).lower()
toc_page=next(p for p in range(1,n+1) if 'содержание' in norm(pages[p])[:60])
keys=['Введение','1 Требования охраны труда, техники безопасности, пожарной безопасности','2 Анализ предметной области','3 Разработка','4 Интеграция','5 Тестирование работы','Заключение','Перечень используемых информационных ресурсов']
found={}; start=toc_page+1
for k in keys:
    kk=norm(k)[:28]
    for p in range(start,n+1):
        if kk in norm(pages[p])[:300]: found[k]=p-toc_page+3; start=p; break
    else: print('NOT FOUND', k)
json.dump(found,open(sys.argv[2],'w'),ensure_ascii=False)
print('pages:',n,'| toc physical:',toc_page,'|',found)
for p in range(1,n+1): print(f'{p:2}', repr(norm(pages[p])[:80]))
