import json, subprocess, sys, re
pdf, headings_json, out = sys.argv[1], sys.argv[2], sys.argv[3]
headings = json.load(open(headings_json))
npages = int(re.search(r'Pages:\s+(\d+)', subprocess.run(['pdfinfo', pdf], capture_output=True, text=True).stdout).group(1))
texts = {}
for p in range(1, npages + 1):
    texts[p] = subprocess.run(['pdftotext', '-f', str(p), '-l', str(p), '-layout', pdf, '-'], capture_output=True, text=True).stdout
def norm(s): return re.sub(r'\s+', ' ', s).strip().lower()
pages = {}
toc_page = next(p for p in range(1, npages + 1) if 'содержание' in norm(texts[p])[:200])
start = toc_page + 1   # титульный лист и содержание пропускаем
print('TOC on page', toc_page)
for level, title in headings:
    key = norm(title)[:40]
    found = None
    for p in range(start, npages + 1):
        if key in norm(texts[p]):
            found = p; break
    if found is None:
        print('NOT FOUND:', title, file=sys.stderr)
    else:
        pages[title] = found; start = found
print(json.dumps(pages, ensure_ascii=False, indent=0)[:600])
json.dump(pages, open(out, 'w'), ensure_ascii=False)
print('pages total:', npages, '| headings located:', len(pages), 'of', len(headings))
