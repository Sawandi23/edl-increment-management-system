"""
fill_form.py — Called by Spring Boot FormGenerationService as a subprocess.

Usage:
    python fill_form.py <formType> <template.docx> <output.pdf> <data.json>

IMPORTANT: <template.docx> here is HR's ORIGINAL, untouched Word file —
the exact one they issued, with a real sample employee's data baked in
(e.g. "M.K.M.Presanna", "39337", ...). This script NEVER edits or saves
over that file. It only ever reads it, builds a brand-new filled copy
in a temp/output location, converts that copy to PDF, and discards the
intermediate docx. HR's original file on disk is untouched by every
single run — nothing about it changes, ever.

How the substitution works (all done fresh, in memory, every run):
  1. Read the original docx's word/document.xml (read-only).
  2. Apply the "numbering fix" — HR's forms use Word's auto-numbered
     lists for the 01./02./03... items, and LibreOffice's headless PDF
     conversion doesn't always reproduce Word's exact numbering for
     these multi-level lists. So each auto-number is replaced with a
     literal typed number (removing the underlying <w:numPr> so the
     visible number can never drift), computed correctly from the
     document's own numbering.xml definitions.
  3. Apply the per-form-type map of "HR's original sample value" ->
     "the data key that should replace it" (e.g. the sample name
     "M.K.M.Presanna" -> data["name_en"]). This is what actually swaps
     in the real employee's data for a given generation run.
  4. Save the result as a new filled docx, convert to PDF via
     LibreOffice, and clean up the intermediate file.

Requires: LibreOffice (soffice) on the system. No python-docx needed.
"""

import sys
import json
import re
import subprocess
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape


# ════════════════════════════════════════════════════════════════════════════
# 1. NUMBERING FIX — generic, mechanical, works on any docx's numbering.xml
# ════════════════════════════════════════════════════════════════════════════

def _roman(n: int) -> str:
    vals = [(10, "x"), (9, "ix"), (5, "v"), (4, "iv"), (1, "i")]
    r = ""
    for v, s in vals:
        while n >= v:
            r += s
            n -= v
    return r


def _zero_pad(n: int) -> str:
    return f"{n:02d}."


# Per form-type plan: which numId(s) appear in that template and how to
# render them as literal text. 'counter' = sequential auto-count starting
# at the given value; 'literal' = an explicit ordered list of exact strings
# (used for the ES template's nested "5.1 / 5.2 / 5.3" sub-items, whose
# real format is a two-level "parent.child" counter, not a plain number).
NUMBERING_PLANS = {
    "ES_AP": {
        "1": ("counter", _zero_pad, 1),
        "7": ("literal", ["5.1", "5.2", "5.3"]),
        "2": ("counter", _roman, 1),
        "3": ("counter", _roman, 1),
        "5": ("counter", _roman, 1),
        "6": ("counter", _roman, 1),
    },
    "SKILLED_AP": {
        "1": ("counter", _zero_pad, 1),
    },
    "ES_CERT": {},
    "TECH_CERT": {},
}

# Explicit tab-stop positions (in twips) applied uniformly to every field
# paragraph in a numId group -- see normalize_field_tabs(). Chosen wide
# enough to clear the longest label in each group without an oversized gap
# for short ones. numId "1" is the main 01./02./03... items; numId "7" is
# the nested 5.1/5.2/5.3 sub-items (which sit at a smaller left indent).
# HR's native numbered-list indent per numId, read directly from each
# template's own numbering.xml (w:ind on the abstractNum level Word was
# using). left/hanging preserves where the label starts; value_col is an
# explicit second tab stop for the ':-' value column, wide enough to clear
# the longest label in that group so it never depends on Word's default
# tab grid (which is what caused the remaining inconsistency: the FIRST
# tab landing correctly via the preserved indent wasn't enough on its own,
# since the SECOND tab -- before the value -- was still falling back to
# default stops that vary with label length).
FIELD_LAYOUT = {
    "ES_AP": {
        "1": {"left": 1080, "hanging": 360, "value_col": 5000},
        "7": {"left": 1440, "hanging": 360, "value_col": 5000},
    },
    "SKILLED_AP": {
        "1": {"left": 360, "hanging": 360, "value_col": 5000},
    },
    "ES_CERT": {},
    "TECH_CERT": {},
}


def apply_numbering_fix(xml: str, plan: dict, layout: dict = None) -> str:
    if not plan:
        return xml
    layout = layout or {}

    parts = re.split(r"(<w:p [^>]*>.*?</w:p>)", xml)
    counters = {}
    literal_idx = {}
    out = []

    for part in parts:
        if not part.startswith("<w:p "):
            out.append(part)
            continue

        m = re.search(r'<w:numPr>.*?<w:numId w:val="(\d+)"/>.*?</w:numPr>', part)
        if not m or m.group(1) not in plan:
            out.append(part)
            continue

        numid = m.group(1)
        mode = plan[numid]
        if mode[0] == "literal":
            i = literal_idx.get(numid, 0)
            text = mode[1][i] if i < len(mode[1]) else ""
            literal_idx[numid] = i + 1
        else:
            _, fmt_func, start = mode
            n = counters.get(numid, start)
            text = fmt_func(n)
            counters[numid] = n + 1

        new_part = part[: m.start()] + part[m.end():]

        cfg = layout.get(numid)
        if cfg:
            ppr_close = new_part.find("</w:pPr>")
            if ppr_close != -1:
                pre_ppr = new_part[:ppr_close]
                if "<w:ind " not in pre_ppr:
                    pre_ppr += f'<w:ind w:left="{cfg["left"]}" w:hanging="{cfg["hanging"]}"/>'
                pre_ppr = _TABS_BLOCK_RE.sub("", pre_ppr)
                pre_ppr += f'<w:tabs><w:tab w:val="left" w:pos="{cfg["left"]}"/><w:tab w:val="left" w:pos="{cfg["value_col"]}"/></w:tabs>'
                new_part = pre_ppr + new_part[ppr_close:]

        num_run = (
            '<w:r><w:rPr><w:rFonts w:ascii="Iskoola Pota" w:hAnsi="Iskoola Pota" '
            'w:cs="Iskoola Pota"/><w:b w:val="0"/><w:bCs w:val="0"/>'
            '<w:lang w:bidi="si-LK"/></w:rPr>'
            f'<w:t xml:space="preserve">{text}</w:t></w:r><w:r><w:tab/></w:r>'
        )

        ppr_end = new_part.find("</w:pPr>")
        insert_at = ppr_end + len("</w:pPr>") if ppr_end != -1 else new_part.find(">") + 1
        new_part = new_part[:insert_at] + num_run + new_part[insert_at:]
        new_part = squeeze_tab_sequence(new_part)

        # If this paragraph has no tab left at all beyond our own inserted
        # one (e.g. 5.1 in the ES template, which has zero tabs in the
        # original -- unlike 5.2/5.3 which each have one), a tab-stop
        # definition alone does nothing. Insert a tab right before the
        # ':-' marker so it actually has something to jump to.
        after_number = new_part[insert_at + len(num_run):]
        if cfg and "<w:tab/>" not in after_number:
            colon_run = re.search(r'<w:r(?:\s[^>]*)?>(?:(?!<w:r[\s>]).)*?:-', new_part[insert_at:], re.DOTALL)
            if colon_run:
                abs_pos = insert_at + colon_run.start()
                new_part = new_part[:abs_pos] + "<w:r><w:tab/></w:r>" + new_part[abs_pos:]

        out.append(new_part)

    return "".join(out)


_TAB_SEQ_RUN_RE = re.compile(
    r'<w:r(?:\s[^>]*)?>((?:(?!<w:r[\s>]).)*?)<w:tab/>'
    r'(?:<w:t(?:\s[^>]*)?>([^<]*)</w:t>)?</w:r>',
    re.DOTALL,
)


def squeeze_tab_sequence(paragraph_xml: str) -> str:
    """Within one paragraph, find every run of 2+ CONSECUTIVE tab-carrying
    runs (a run containing <w:tab/>, optionally followed by text) and
    collapse them to a single tab. If the last run in the sequence has
    real (non-whitespace) trailing content -- e.g. a run holding
    '    :- ' after two earlier redundant tabs -- that content is kept
    but its leading padding spaces are stripped, since our own explicit
    tab stops now control the gap, not manual space-padding. Runs
    separated by real content elsewhere in the paragraph (e.g. a second,
    intentional 'දිනය :' column) are untouched since they aren't adjacent."""

    runs = list(_TAB_SEQ_RUN_RE.finditer(paragraph_xml))
    if len(runs) < 2:
        return paragraph_xml

    out = []
    last_end = 0
    i = 0
    while i < len(runs):
        m = runs[i]
        # only treat as part of a sequence if directly adjacent (nothing
        # but the previous run's closing tag between them)
        j = i
        while j + 1 < len(runs) and paragraph_xml[runs[j].end():runs[j + 1].start()] == "":
            j += 1

        if j == i:
            i += 1
            continue  # standalone tab run, leave untouched

        out.append(paragraph_xml[last_end:m.start()])
        rpr_m = re.search(r"<w:rPr>.*?</w:rPr>", m.group(0), re.DOTALL)
        rpr = rpr_m.group(0) if rpr_m else ""
        last_text = runs[j].group(2) or ""
        stripped = last_text.lstrip(" ")
        if stripped:
            out.append(f'<w:r>{rpr}<w:tab/><w:t xml:space="preserve">{stripped}</w:t></w:r>')
        else:
            out.append(f'<w:r>{rpr}<w:tab/></w:r>')
        last_end = runs[j].end()
        i = j + 1

    out.append(paragraph_xml[last_end:])
    return "".join(out)


_TABS_BLOCK_RE = re.compile(r"<w:tabs>.*?</w:tabs>", re.DOTALL)


_EXCESS_SPACE_RE = re.compile(r"( {3,})")


def collapse_excess_spaces(xml: str) -> str:
    """HR occasionally padded a label with many literal spaces instead of a
    tab (e.g. the '5.1' service-years label), which can force an unwanted
    line wrap once a real tab-stop is also in play. Safe to collapse now
    that alignment is handled by explicit tab stops instead of manual
    padding -- only touches runs of 3+ spaces inside text content."""
    def repl(m):
        return " "
    return re.sub(r"(<w:t[^>]*>)([^<]*)(</w:t>)",
                   lambda m: m.group(1) + _EXCESS_SPACE_RE.sub(" ", m.group(2)) + m.group(3),
                   xml)


_RUN_RE = re.compile(r"<w:r(?:\s[^>]*)?>(?:(?!<w:r[\s>]).)*?</w:r>", re.DOTALL)
_RPR_RE = re.compile(r"<w:rPr>.*?</w:rPr>", re.DOTALL)
_SIMPLE_T_RE = re.compile(r'^<w:r(?:\s[^>]*)?>(?:<w:rPr>.*?</w:rPr>)?<w:t(?:\s[^>]*)?>(.*?)</w:t></w:r>$', re.DOTALL)


def merge_adjacent_runs(xml: str) -> str:
    """Word fragments a single visible phrase across many <w:r> elements
    (spell-check boundaries, revision markers, etc.), which breaks exact
    text matching. This merges adjacent runs that share identical
    formatting (<w:rPr>) and contain only plain text, concatenating their
    text into the first run and dropping the rest. Runs containing tabs,
    breaks, fields, or differing formatting are left as natural merge
    boundaries and never combined. Only touches the matched run spans —
    everything else in the file is untouched byte-for-byte."""

    def merge_paragraph(p_match):
        para = p_match.group(0)
        runs = list(_RUN_RE.finditer(para))
        if len(runs) < 2:
            return para

        pieces = []           # list of (text_or_None, raw_span)
        last_end = 0
        out = []
        i = 0
        while i < len(runs):
            m = runs[i]
            out.append(para[last_end:m.start()])
            simple = _SIMPLE_T_RE.match(m.group(0))
            if not simple:
                out.append(m.group(0))
                last_end = m.end()
                i += 1
                continue

            rpr_m = _RPR_RE.search(m.group(0))
            rpr = rpr_m.group(0) if rpr_m else ""
            text_parts = [simple.group(1)]
            j = i + 1
            end = m.end()
            while j < len(runs) and re.sub(r"<w:proofErr[^>]*/>", "", para[end:runs[j].start()]).strip() == "":
                nxt = _SIMPLE_T_RE.match(runs[j].group(0))
                if not nxt:
                    break
                nxt_rpr_m = _RPR_RE.search(runs[j].group(0))
                nxt_rpr = nxt_rpr_m.group(0) if nxt_rpr_m else ""
                if nxt_rpr != rpr:
                    break
                text_parts.append(nxt.group(1))
                end = runs[j].end()
                j += 1

            if j == i + 1:
                # nothing to merge -- leave this single run completely untouched
                out.append(m.group(0))
                last_end = m.end()
                i = j
                continue

            merged_text = "".join(text_parts)
            rpr_block = rpr if rpr else ""
            out.append(f'<w:r>{rpr_block}<w:t xml:space="preserve">{merged_text}</w:t></w:r>')
            last_end = end
            i = j

        out.append(para[last_end:])
        return "".join(out)

    return re.sub(r"<w:p [^>]*>.*?</w:p>", merge_paragraph, xml, flags=re.DOTALL)


_TAB_RUN_RE = re.compile(
    r'<w:r(?:\s[^>]*)?>(?:(?!<w:r[\s>]).)*?<w:tab/>'
    r'(?:<w:t(?:\s[^>]*)?>( *)</w:t>)?</w:r>',
    re.DOTALL,
)


def collapse_repeated_tabs(xml: str) -> str:
    """HR's original forms have several label/value lines authored with two
    or three consecutive tabs between the label and the ':-' value --
    sometimes bare tabs, sometimes a tab followed by a run of literal
    padding spaces (e.g. one real case: tab, then a run with just a space,
    then a bare tab, then a third tab followed by four literal spaces
    before the real ':-' text). All of that reads as an oversized,
    inconsistent gap once real data is filled in and our own explicit tab
    stops are in play. This finds any run of 2+ consecutive "tab-carrying"
    runs (a tab optionally followed by whitespace-only text) and collapses
    them down to a single tab, dropping the redundant ones and their
    padding. Runs with real (non-whitespace) text stop the match, so
    genuine multi-column content (e.g. a second 'දිනය :' label) is left
    untouched."""

    def _collapse(m):
        first_run_end = m.group(0).find("</w:r>") + len("</w:r>")
        return m.group(0)[:first_run_end]

    pattern = re.compile(r'(?:' + _TAB_RUN_RE.pattern + r'){2,}', re.DOTALL)
    return pattern.sub(_collapse, xml)



# ════════════════════════════════════════════════════════════════════════════
# 2. SAMPLE-VALUE -> DATA-KEY MAPS — one per form type, in document order.
#    Each tuple is (exact original XML snippet, data key to substitute, or
#    None to just delete the snippet). Applied count=1, in this exact order,
#    against HR's pristine original file's document.xml.
# ════════════════════════════════════════════════════════════════════════════

ES_AP_MAP = [
    ("<w:t>2025</w:t>", "period_year"),
    ("<w:t>2025</w:t>", "period_year"),
    ("<w:t>නිසා(කො.න)/මාසනි/ඊ</w:t>", None),
    ("<w:t>5</w:t>", None),
    ("<w:t>/</w:t>", None),
    ("<w:t>North</w:t>", None),
    ("<w:t>/</w:t>", None),
    ("<w:t>26856</w:t>", "ref_no"),
    ("<w:t>8/16/2024</w:t>", "period_from"),
    ("<w:t>2025-08-15</w:t>", "period_to"),
    ("<w:t>2025-08-15</w:t>", "period_to"),
    ('<w:t xml:space="preserve"> 2024-11-13  </w:t>', "period_from"),
    ("<w:t>ඒ.එම්.එස්.අධිකාරි මහතා</w:t>", "name_si"),
    ("<w:t>26856</w:t>", "epf"),
    ('<w:t xml:space="preserve">විදුලි අධිකාරි </w:t>', "desig_si"),
    ("<w:t>I</w:t>", None),
    ("<w:t>8/15/2000</w:t>", "perm_date"),
    ("<w:t>අවු 37</w:t>", "service_total"),
    ("<w:t>අවු 25</w:t>", "service_post"),
    ("<w:t>අවු 25</w:t>", "service_grade"),
    ("<w:t>172,070</w:t>", "prev_salary"),
    ('<w:t xml:space="preserve">(15 </w:t>', "prev_step"),
    ("<w:t>පියවර)</w:t>", None),
    ("<w:t>174,590</w:t>", "new_salary"),
    ("<w:t>(</w:t>", "new_step"),
    ('<w:t xml:space="preserve">I </w:t>', None),
    ("<w:t>වේතනාධිකය)</w:t>", None),
    ("<w:t>2025-08-15</w:t>", "inc_date"),
    ("<w:t>134,870-5x2,400-10x2,520-172,070/-(EL1)</w:t>", "salary_scale"),
    ("<w:t>වේතනාධක වාසි සහිත වැ.වැ</w:t>", "inc_type"),
    ("<w:t>නැත</w:t>", "suspended"),
    ("<w:t>නැත</w:t>", "praise"),
    ("<w:t>නැත</w:t>", "punishment"),
    ("<w:t>අනුරංජන</w:t>", "clerk_name"),
]

SKILLED_AP_MAP = [
    ("<w:t>2026</w:t>", "period_year"),
    ("<w:t>2026</w:t>", "period_year"),
    ('<w:t xml:space="preserve">නිසා(කො.න)/මාසනි/ඊ5/</w:t>', None),
    ("<w:t>Main</w:t>", None),
    ("<w:t>/</w:t>", None),
    ("<w:t>42034</w:t>", "ref_no"),
    ('<w:t xml:space="preserve">12:00:00 </w:t>', "period_from_sp"),
    ("<w:t>AM</w:t>", None),
    ("<w:t>2026-02-14</w:t>", "period_to"),
    ("<w:t>එම්.එච්.කුමාර මහතා</w:t>", "name_si"),
    ("<w:t>42034</w:t>", "epf"),
    ('<w:t xml:space="preserve">පුහුණු කාර්මික </w:t>', "desig_si"),
    ("<w:t>II</w:t>", None),
    ("<w:t>82,740-5x1165-10x1310-101,665/- (EN3)</w:t>", "salary_scale"),
    ("<w:t>86235</w:t>", "prev_salary"),
    ('<w:t xml:space="preserve">(03 </w:t>', "prev_step"),
    ("<w:t>පියවර)</w:t>", None),
    ("<w:t>1165</w:t>", "inc_amount"),
    ("<w:t>87400</w:t>", "new_salary"),
    ("<w:t>(04 පියවර)</w:t>", "new_step"),
    ('<w:t xml:space="preserve">12:00:00 </w:t>', "period_from_sp"),
    ("<w:t>AM</w:t>", None),
    ("<w:t>2026-02-14</w:t>", "period_to"),
    ("<w:t>නැත</w:t>", "suspended"),
    ("<w:t>නැත</w:t>", "praise_punishment"),
    ("<w:t>අනුරංජන</w:t>", "clerk_name"),
    ('<w:t xml:space="preserve">12:00:00 </w:t>', "period_from_sp"),
    ("<w:t>AM</w:t>", None),
    ("<w:t>2026-02-14</w:t>", "inc_date"),
]

TECH_CERT_MAP = [
    ("<w:t>South</w:t>", "unit_short"),
    ("<w:t>39337</w:t>", "epf"),
    ("<w:t>2026</w:t>", "period_year"),
    ("<w:t>Mr.M.K.M.Presanna</w:t>", "name_en"),
    ("<w:t>39337</w:t>", "epf"),
    ("<w:t>Skilled Technical Service Gr.I</w:t>", "desig_en"),
    ("<w:t>2026-01-14</w:t>", "inc_date"),
    ("<w:t>88,425-5x1,380-10x1,485-110,175/-(EN2)</w:t>", "salary_scale"),
    ("<w:t>93</w:t>", "new_salary"),
    ("<w:t>,</w:t>", None),
    ("<w:t>945</w:t>", None),
    ("<w:t>03</w:t>", "step_padded"),
    ('<w:t xml:space="preserve">Sgd. /Eng. R.M.S.K.Rathnayaka </w:t>', "dgm_name_sgd"),
    ('<w:t xml:space="preserve">W.D.Aruna Withana</w:t>', "hr_officer"),
    ("<w:t>Mr.M.K.M.Presanna</w:t>", "name_en"),
    ("<w:t>South</w:t>", "unit_short"),
    ("<w:t>Skilled Technical Service Gr.I</w:t>", "desig_en"),
    ("<w:t>39337</w:t>", "epf"),
    ('<w:t xml:space="preserve">Date :  16 February 2026  </w:t>', "cert_date_full"),
    # Institutional letterhead change — HR's original template still says
    # "Deputy General Manager" / "Ceylon Electricity Board", but the org
    # is now "Director" / "Electricity Distribution Lanka (Pvt) Ltd".
    # Applied to every generated certificate (not per-employee data).
    ("<w:t>Deputy General Manager (Colombo City)</w:t>", "org_title_header"),
    ("<w:t>Ceylon Electricity Board</w:t>", "org_name"),
    ('<w:t xml:space="preserve">Deputy General Manager (Colombo City) </w:t>', "org_title_signature"),
]

FORM_MAPS = {
    "ES_AP": ES_AP_MAP,
    "SKILLED_AP": SKILLED_AP_MAP,
    "TECH_CERT": TECH_CERT_MAP,
    "ES_CERT": TECH_CERT_MAP,  # same layout family; adjust if ES cert differs
}


def apply_value_map(xml: str, value_map: list, data: dict) -> str:
    for old, key in value_map:
        if xml.count(old) == 0:
            print(f"  WARNING: expected text not found in template, skipped: {old[:60]}", file=sys.stderr)
            continue

        if key is None:
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", r"<w:t\1></w:t>", old)
        elif key == "period_from_sp":
            val = data.get("period_from", "") or ""
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", rf"<w:t\1>{xml_escape(val)} </w:t>", old)
        elif key == "dgm_name_sgd":
            val = data.get("dgm_name", "") or ""
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", rf"<w:t\1>Sgd. /{xml_escape(val)}    </w:t>", old)
        elif key == "cert_date_full":
            val = data.get("cert_date", "") or ""
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", rf"<w:t\1>Date :  {xml_escape(val)}  </w:t>", old)
        elif key == "org_title_header":
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", r"<w:t\1>Director (Colombo City) Office</w:t>", old)
        elif key == "org_name":
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", r"<w:t\1>Electricity Distribution Lanka (Pvt) Ltd</w:t>", old)
        elif key == "org_title_signature":
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", r'<w:t\1>Director (Colombo City) Office </w:t>', old)
        else:
            val = data.get(key, "")
            val = "" if val is None else str(val)
            new = re.sub(r"<w:t([^>]*)>.*?</w:t>", rf"<w:t\1>{xml_escape(val)}</w:t>", old)

        xml = xml.replace(old, new, 1)

    return xml


# ════════════════════════════════════════════════════════════════════════════
# 3. DOCX / PDF PLUMBING
# ════════════════════════════════════════════════════════════════════════════

def build_filled_docx(template_path: Path, filled_path: Path, form_type: str, data: dict):
    with zipfile.ZipFile(template_path, "r") as zin:
        doc_xml = zin.read("word/document.xml").decode("utf-8")

    doc_xml = merge_adjacent_runs(doc_xml)
    doc_xml = collapse_repeated_tabs(doc_xml)
    doc_xml = apply_numbering_fix(doc_xml, NUMBERING_PLANS.get(form_type, {}), FIELD_LAYOUT.get(form_type, {}))
    doc_xml = collapse_excess_spaces(doc_xml)

    value_map = FORM_MAPS.get(form_type)
    if value_map is None:
        print(f"WARNING: no value map defined for form type '{form_type}' — "
              f"output will still contain HR's original sample data.", file=sys.stderr)
    else:
        doc_xml = apply_value_map(doc_xml, value_map, data)

    with zipfile.ZipFile(template_path, "r") as zin, \
         zipfile.ZipFile(filled_path, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == "word/document.xml":
                zout.writestr(item, doc_xml)
            else:
                zout.writestr(item, zin.read(item.filename))


def to_pdf(docx_path: Path) -> Path:
    out_dir = docx_path.parent
    pdf_path = out_dir / (docx_path.stem + ".pdf")

    # Prefer real Microsoft Word (via docx2pdf) when available — it renders
    # this document's Sinhala/complex-script text noticeably more compactly
    # than LibreOffice does (confirmed: 2 pages in Word vs 3 in LibreOffice
    # for the same untouched file), which matters for double-sided printing.
    try:
        from docx2pdf import convert as word_convert
        word_convert(str(docx_path), str(pdf_path))
        if pdf_path.exists():
            return pdf_path
        print("  Word conversion did not produce a PDF, falling back to LibreOffice", file=sys.stderr)
    except ImportError:
        pass  # docx2pdf not installed — fall through to LibreOffice
    except SystemExit as e:
        # docx2pdf can call sys.exit() internally when Word's AppleScript
        # automation fails (e.g. Word running in View Only / unlicensed
        # mode) -- SystemExit is NOT caught by "except Exception", so
        # without this the whole process would die here instead of
        # falling through to LibreOffice below.
        print(f"  Word conversion failed (exit {e.code}), falling back to LibreOffice", file=sys.stderr)
    except Exception as e:
        print(f"  Word conversion failed ({e}), falling back to LibreOffice", file=sys.stderr)

    # ── LibreOffice fallback ────────────────────────────────────────────────
    soffice_candidates = [
        "soffice",
        "/usr/bin/soffice",
        r"C:\Program Files\LibreOffice\program\soffice.exe",
        r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
        "/Applications/LibreOffice.app/Contents/MacOS/soffice",
    ]
    soffice = None
    for candidate in soffice_candidates:
        try:
            result = subprocess.run([candidate, "--version"], capture_output=True, timeout=5)
            if result.returncode == 0:
                soffice = candidate
                break
        except (FileNotFoundError, subprocess.TimeoutExpired):
            continue

    if not soffice:
        raise RuntimeError(
            "Neither Word (docx2pdf) nor LibreOffice is available.\n"
            "Install docx2pdf: pip3 install docx2pdf --break-system-packages\n"
            "  (requires Microsoft Word installed on this machine)\n"
            "Or install LibreOffice: brew install --cask libreoffice"
        )

    subprocess.run(
        [soffice, "--headless", "--convert-to", "pdf", str(docx_path), "--outdir", str(out_dir)],
        capture_output=True,
        timeout=60,
    )

    if not pdf_path.exists():
        raise RuntimeError(f"PDF conversion failed — output not found at {pdf_path}")
    return pdf_path


def main():
    if len(sys.argv) != 5:
        print("Usage: python fill_form.py <formType> <template.docx> <output.pdf> <data.json>", file=sys.stderr)
        sys.exit(1)

    form_type = sys.argv[1]
    template_path = Path(sys.argv[2])
    output_path = Path(sys.argv[3])
    data_path = Path(sys.argv[4])

    if not template_path.exists():
        print(f"ERROR: Template not found: {template_path}", file=sys.stderr)
        sys.exit(1)
    if not data_path.exists():
        print(f"ERROR: Data file not found: {data_path}", file=sys.stderr)
        sys.exit(1)

    with open(data_path, encoding="utf-8") as f:
        data = json.load(f)

    print(f"Form type : {form_type}")
    print(f"Template  : {template_path}  (read-only, never modified)")
    print(f"Output    : {output_path}")
    print(f"Fields    : {len(data)}")

    output_path.parent.mkdir(parents=True, exist_ok=True)

    if output_path.suffix.lower() == ".docx":
        # Docx requested directly -- build it and stop, no PDF conversion.
        build_filled_docx(template_path, output_path, form_type, data)
        print(f"\u2713 Word doc ready: {output_path}")
        sys.exit(0)

    filled_docx = output_path.with_suffix(".docx")

    build_filled_docx(template_path, filled_docx, form_type, data)
    print(f"Saved filled docx: {filled_docx}")

    pdf_path = to_pdf(filled_docx)

    if pdf_path != output_path:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        pdf_path.rename(output_path)

    filled_docx.unlink(missing_ok=True)
    print(f"\u2713 PDF ready: {output_path}")
    sys.exit(0)


if __name__ == "__main__":
    main()