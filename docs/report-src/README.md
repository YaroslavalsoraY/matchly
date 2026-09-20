# Сборка отчёта по практике из шаблона колледжа

`fill_template.py` заполняет DOCX-шаблон АТК ДГТУ (титульный лист, задание, дневник, аттестационный лист,
содержание, основной текст, штамп) данными студента и текстом о проекте, не меняя оформление шаблона.
`pages.py` вычисляет номера страниц заголовков по PDF для таблицы содержания.

```bash
unzip -q Шаблон_отчета_по_учебной_практике.docx -d tpl && python3 <docx-skill>/scripts/merge_runs.py tpl
python3 fill_template.py tpl report_v2.docx && soffice --headless --convert-to pdf report_v2.docx
python3 pages.py report_v2.pdf pages.json
python3 fill_template.py tpl report.docx pages.json   # второй проход с номерами страниц
```
