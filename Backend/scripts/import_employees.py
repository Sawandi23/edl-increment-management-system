"""
CEB Increment System — Employee Import Script
Reads the "ALL this one ok" sheet and POSTs to Spring Boot API.

Column mapping (0-indexed) — verified directly against the uploaded file:
  0  S/No       1  Name (English)    2  Name (Sinhala)
  3  Designation (Sinhala)           4  EPF No
  5  Designation (English)           6  Salary scale string
  7  Previous salary                 8  Previous step label
  9  Increment amount                10 New salary
  11 New step label                  12 New step number/surplus label
  13 Increment FROM date             14 Increment / anniversary date
  15 Suspended (නැත/ඇත)              16 Praise      17 Punishment
    18 Permanent post date             19 Appointment date to current post (unused — increment_date now comes from col 14)
  20 5.1 Total service                21 5.2 Post service
  22 5.3 Grade service               23 Increment type (Sinhala text)
  24 Evaluation score                25 Clerk name
    26 Year                            27 Unit
    28 Unit (Sinhala)                  29 Unit (English)
  30 Reference year-month            31 Mobile number
"""

import sys, re
import requests
from datetime import datetime, date, timedelta

# ── Config ────────────────────────────────────────────────────────────────────
EXCEL_PATH  = "employees.xlsx"
SHEET_NAME  = "ALL this one ok"
API_BASE    = "http://localhost:8085/api"
SKIP_ROWS   = 1

ES_CODES = {
    "ELSpl","ELspl","EL1","EL2","EL3",
    "SLspl","SL1","SL2","SL3","SL4",
    "SMspl","SM1","SM2","SM3","SM4",
    "ENspl","ENSpl","EN1","EN2",       # added ENSpl
    "SNspl","SN1xSPL",
}

EXCEL_EPOCH = datetime(1899, 12, 30)

# ── Helpers ───────────────────────────────────────────────────────────────────

def to_date(v):
    if v is None: return None
    if isinstance(v, (int, float)) and v > 1000:
        return (EXCEL_EPOCH + timedelta(days=int(v))).date()
    if isinstance(v, datetime): return v.date()
    if isinstance(v, date): return v
    if isinstance(v, str):
        for fmt in ('%Y-%m-%d','%d/%m/%Y','%m/%d/%Y','%Y/%m/%d','%Y.%m.%d','%d.%m.%Y'):
            try: return datetime.strptime(v.strip(), fmt).date()
            except ValueError: pass
        try: return to_date(int(float(v.strip())))
        except: pass
    return None

def s(v): return str(v).strip() if v is not None else ""

def clean_epf(v):
    try: return str(int(float(s(v))))
    except: return None

def clean_money(v):
    if v is None: return None
    try: return float(str(v).replace(',','').strip())
    except: return None

def extract_code(scale_str):
    # Handles both "(S5)" and "(SM 2)" style codes — strips internal spaces.
    m = re.search(r'\(([A-Za-z][A-Za-z0-9 ]*)\)', s(scale_str))
    return m.group(1).replace(' ', '') if m else None

def is_es(code):
    return code in ES_CODES if code else False

def parse_step(label_str):
    txt = s(label_str)
    m = re.search(r'\((\d+)', txt)
    if m: return int(m.group(1))
    try: return int(float(txt))
    except: pass
    if 'වේතනාධිකය' in txt or 'surplus' in txt.lower() or 'serpul' in txt.lower():
        return 15
    return 0

def inc_type(col_val):
    v = s(col_val)
    if 'කාර්යක' in v or 'කඩඉම' in v: return 'EFFICIENCY_BAR'
    if 'වේතනාධ' in v: return 'SURPLUS'
    return 'NORMAL'

def is_surplus(col11):
    t = s(col11).lower()
    return 'වේතනාධිකය' in s(col11) or 'serpul' in t or 'surplus' in t

def ref_no_ap(epf, unit, form_is_es):
    unit_clean = s(unit)
    prefix = "නිසා(කො.න)/මාසනි/ඊ5"
    return f"{prefix}/{unit_clean}/{epf}"

def ref_no_cert(epf, unit):
    return f"DGM (CC)/HRO/E5/{s(unit)}/{epf}"

def post(endpoint, data):
    try:
        r = requests.post(f"{API_BASE}/{endpoint}", json=data, timeout=15)
        if r.status_code in (200,201): return r.json()
        print(f"  POST /{endpoint} → {r.status_code}: {r.text[:120]}")
        return None
    except Exception as e:
        print(f"  Connection error: {e}")
        return None

def get(endpoint):
    try:
        r = requests.get(f"{API_BASE}/{endpoint}", timeout=10)
        return r.json() if r.status_code == 200 else None
    except: return None

# ── Main import ───────────────────────────────────────────────────────────────

def run(path=EXCEL_PATH):
    try:
        import openpyxl
    except ImportError:
        import subprocess, sys as _sys
        subprocess.run([_sys.executable,"-m","pip","install","openpyxl","-q"])
        import openpyxl

    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)

    # Find sheet — try exact configured name first, then fuzzy match as fallback.
    target = None
    if SHEET_NAME in wb.sheetnames:
        target = SHEET_NAME
    else:
        for name in wb.sheetnames:
            if 'software' in name.lower() or 'use this' in name.lower() or 'all this' in name.lower():
                target = name; break
    if not target: target = wb.sheetnames[0]
    print(f"Sheet: '{target}'")

    ws  = wb[target]
    rows = list(ws.iter_rows(values_only=True))
    data_rows = rows[SKIP_ROWS:]
    print(f"Rows: {len(data_rows)}\n")

    ok = skip = errors = 0

    for i, row in enumerate(data_rows):
        if not row or len(row) < 12: skip += 1; continue

        epf = clean_epf(row[4])
        if not epf or not s(row[1]): skip += 1; continue

        name_en   = s(row[1])
        name_si   = s(row[2])
        desig_si  = s(row[3])
        desig_en  = s(row[5])
        scale_str = s(row[6])
        code      = extract_code(scale_str)
        form_es   = is_es(code)

        prev_sal  = clean_money(row[7])
        inc_amt   = clean_money(row[9])
        new_sal   = clean_money(row[10])

        prev_step = parse_step(row[8])
        new_step  = parse_step(row[11])
        surplus   = is_surplus(row[11])
        surplus_lbl = s(row[11]) if surplus else None

        from_date = to_date(row[13])
        inc_date  = to_date(row[14])
        perm_date = to_date(row[18])

        # Skip rows with no increment date at all — these need HR to fill in
        # the date before they can be imported (nothing to calculate from).
        if not inc_date:
            print(f"SKIP EPF={epf} ({name_en[:30]}) — no increment date")
            skip += 1
            continue

        cycle_year  = int(row[26]) if row[26] else inc_date.year
        unit        = s(row[27])
        unit_si     = s(row[28])
        unit_en     = s(row[29])
        mobile      = s(row[31])
        if mobile:
            digits = re.sub(r'\D','', mobile)
            if len(digits) == 9: digits = '0' + digits
            mobile = digits[:10]

        clerk       = s(row[25])
        eval_score  = s(row[24])
        inc_type_val = inc_type(row[23])
        if surplus: inc_type_val = 'SURPLUS'

        svc_total = s(row[20])
        svc_post  = s(row[21])
        svc_grade = s(row[22])

        # Increment date: HR confirmed the increment date (col 14) is the
        # anniversary date to use directly — no separate appointment-date lookup needed.
        increment_date = inc_date

        print(f"[{i+1:3d}] EPF {epf:>6}  {'ES' if form_es else 'SK'}  {name_en[:40]:<40}", end=" ")

        emp_payload = {
            "epfNo": epf,
            "nameEnglish": name_en,
            "nameSinhala": name_si,
            "designationEnglish": desig_en,
            "designationSinhala": desig_si,
            "incrementDate": str(increment_date) if increment_date else None,
            "permanentDate": str(perm_date) if perm_date else None,
            "unit": unit_en or unit_si or unit,
            "phoneNumber": mobile or None,
            "isActive": True,
        }
        emp = post("employees/import", emp_payload)
        if not emp:
            emp = get(f"employees/search?epfNo={epf}")
        if not emp or not emp.get("id"):
            print("FAILED (no employee)")
            errors += 1; continue

        emp_id = emp["id"]

        ref_ap   = ref_no_ap(epf, unit_en or unit_si or unit, form_es)
        ref_cert = ref_no_cert(epf, unit_en or unit_si or unit)

        cycle_payload = {
            "employeeId": emp_id,
            "salaryTypeCode": code,
            "cycleYear": cycle_year,
            "incrementDate": str(inc_date) if inc_date else None,
            "previousSalary": prev_sal,
            "previousStep": prev_step,
            "incrementAmount": inc_amt,
            "newSalary": new_sal,
            "newStep": new_step,
            "isSurplus": surplus,
            "surplusLabel": surplus_lbl,
            "isCertificateYear": True,
            "incrementType": inc_type_val,
            "evaluationScore": eval_score or None,
            "suspended": s(row[15]) == "ඇත",
            "hasPraise": s(row[16]) == "ඇත",
            "hasPunishment": s(row[17]) == "ඇත",
            "periodFrom": str(from_date) if from_date else None,
            "periodTo": str(inc_date) if inc_date else None,
            "status": "ELIGIBLE",
            "clerkName": clerk or None,
            "referenceNumber": ref_ap,
            "formType": "ES_AP" if form_es else "SKILLED_AP",
            "formRefCert": ref_cert,
            "serviceTotal": svc_total,
            "servicePost": svc_post,
            "serviceGrade": svc_grade,
        }
        cyc = post(f"employees/{emp_id}/import-cycle", cycle_payload)
        if not cyc:
            print("FAILED (cycle)")
            errors += 1; continue

        print("✓")
        ok += 1

    print(f"\n{'='*55}")
    print(f"Done — imported: {ok}  skipped: {skip}  errors: {errors}")
    print(f"{'='*55}\n")

if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else EXCEL_PATH
    run(path)