from pathlib import Path
from docx import Document
from docx.shared import Pt, Cm
from docx.oxml.ns import qn
import re

# Paths
ROOT = Path(__file__).resolve().parents[1]
MD_PATH = ROOT / 'ProjectReport.md'
OUT_PATH = ROOT / 'ProjectReport.docx'

# Font settings
BODY_FONT = 'SimSun'  # 宋体
BODY_SIZE_PT = 12  # 小四 -> 12pt
LINE_SPACING = 1.25

# Heading sizes (pt)
HEADING_SIZES = {
    1: 20,
    2: 16,
    3: 14,
    4: 12,
}


def set_run_font(run, name=BODY_FONT, size_pt=BODY_SIZE_PT, bold=False, italic=False):
    run.font.name = name
    run._element.rPr.rFonts.set(qn('w:eastAsia'), name)
    run.font.size = Pt(size_pt)
    run.font.bold = bold
    run.font.italic = italic


def set_paragraph_format(paragraph):
    pf = paragraph.paragraph_format
    pf.line_spacing = LINE_SPACING


def add_heading(doc, text, level):
    p = doc.add_paragraph()
    run = p.add_run(text)
    size = HEADING_SIZES.get(level, 12)
    set_run_font(run, size_pt=size, bold=True)
    set_paragraph_format(p)


def add_paragraph(doc, text):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run)
    set_paragraph_format(p)


def add_code_block(doc, code_lines):
    for line in code_lines:
        p = doc.add_paragraph()
        run = p.add_run(line.rstrip('\n'))
        # Use monospace font
        set_run_font(run, name='Courier New', size_pt=10)
        set_paragraph_format(p)


def add_table_from_md(doc, rows):
    # rows: list of lists
    if not rows:
        return
    cols = len(rows[0])
    table = doc.add_table(rows=len(rows), cols=cols)
    table.style = 'Table Grid'
    for i, row in enumerate(rows):
        for j, cell_text in enumerate(row):
            cell = table.rows[i].cells[j]
            # clear existing paragraphs in cell
            cell.text = ''
            p = cell.paragraphs[0]
            run = p.add_run(cell_text.strip())
            set_run_font(run)
            set_paragraph_format(p)


def parse_table_block(block_lines):
    # Parse markdown table lines into rows of cells
    rows = []
    for line in block_lines:
        # split on | but ignore leading/trailing |
        parts = [c.strip() for c in re.split(r'\|', line.strip())]
        # remove empty leading/trailing parts if they are from borders
        if parts and parts[0] == '':
            parts = parts[1:]
        if parts and parts[-1] == '':
            parts = parts[:-1]
        rows.append(parts)
    # Remove separator row if present (---|---)
    if len(rows) >= 2 and re.match(r'^[\-:\s|]+$', ''.join(block_lines[1])):
        # naive; but better to remove second row if it looks like ---|---
        rows.pop(1)
    return rows


def main():
    if not MD_PATH.exists():
        print(f"Markdown file not found: {MD_PATH}")
        return

    text = MD_PATH.read_text(encoding='utf-8')
    lines = text.splitlines()

    doc = Document()
    # Set A4 page size and margins
    section = doc.sections[0]
    section.page_height = Cm(29.7)
    section.page_width = Cm(21.0)
    section.left_margin = Cm(2.54)
    section.right_margin = Cm(2.54)
    section.top_margin = Cm(2.54)
    section.bottom_margin = Cm(2.54)

    i = 0
    n = len(lines)
    in_code = False
    code_lines = []
    in_table = False
    table_lines = []

    while i < n:
        line = lines[i]

        # detect code fence
        if line.strip().startswith('```'):
            if not in_code:
                in_code = True
                code_lines = []
            else:
                # close code
                add_code_block(doc, code_lines)
                in_code = False
            i += 1
            continue

        if in_code:
            code_lines.append(line)
            i += 1
            continue

        # detect table start: a line with | and next line contains ---
        if '|' in line:
            # look ahead to see if it's a table header-separator
            if i+1 < n and re.match(r'^[\s\|:-]+$', lines[i+1]):
                # collect table lines until a blank or non-| line
                table_lines = [line]
                j = i+1
                # include separator line
                table_lines.append(lines[j])
                j += 1
                while j < n and '|' in lines[j]:
                    table_lines.append(lines[j])
                    j += 1
                rows = parse_table_block(table_lines)
                add_table_from_md(doc, rows)
                i = j
                continue

        # headings
        m = re.match(r'^(#{1,6})\s+(.*)$', line)
        if m:
            level = len(m.group(1))
            text = m.group(2).strip()
            add_heading(doc, text, level)
            i += 1
            continue

        # unordered list
        m = re.match(r'^[\*\-\+]\s+(.*)$', line)
        if m:
            p = doc.add_paragraph(style='List Bullet')
            run = p.add_run(m.group(1).strip())
            set_run_font(run)
            set_paragraph_format(p)
            i += 1
            continue

        # ordered list
        m = re.match(r'^\d+\.\s+(.*)$', line)
        if m:
            p = doc.add_paragraph(style='List Number')
            run = p.add_run(m.group(1).strip())
            set_run_font(run)
            set_paragraph_format(p)
            i += 1
            continue

        # blank line
        if line.strip() == '':
            # add a blank paragraph to ensure spacing
            doc.add_paragraph('')
            i += 1
            continue

        # normal paragraph
        add_paragraph(doc, line)
        i += 1

    # Save
    doc.save(OUT_PATH)
    print(f"Saved DOCX to: {OUT_PATH}")


if __name__ == '__main__':
    main()

