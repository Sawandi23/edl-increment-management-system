import openpyxl
import re

path = "2026_වැටුප්_වර්ධක_Softwear_All.xlsx"
wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
target = None
for name in wb.sheetnames:
    if 'software' in name.lower() or 'use this' in name.lower():
        target = name; break
if not target:
    target = wb.sheetnames[0]
ws = wb[target]
rows = list(ws.iter_rows(values_only=True))
data_rows = rows[1:]
blank_svc_total = 0
has_col19 = 0
sample_printed = 0
for i, row in enumerate(data_rows):
    if not row or len(row) < 12:
        continue
    svc_total = row[21] if len(row) > 21 else None
    col19 = row[19] if len(row) > 19 else None
    inc_date = row[14] if len(row) > 14 else None
    txt = str(svc_total).strip() if svc_total is not None else ""
    m = re.search(r'(\d+)', txt)
    if not m or not inc_date:
        blank_svc_total += 1
        if col19 is not None:
            has_col19 += 1
        if sample_printed < 15:
            print(f"Row {i+2}: EPF={row[4]!r}  svc_total={svc_total!r}  inc_date={inc_date!r}  col19(appointment date)={col19!r}")
            sample_printed += 1
print(f"\nTotal rows failing current increment_date logic: {blank_svc_total}")
print(f"Of those, rows that DO have a value in column 19 (appointment date): {has_col19}")
