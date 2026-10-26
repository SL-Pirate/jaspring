#!/usr/bin/env python3
"""Extracts (html, expected humanized nodes) pairs from Angular's r3_template_transform_spec.ts.

Usage: extract-r3-cases.py <spec.ts> <out.json>
The output feeds jaspring-core's R3TemplateTransformCompatTest, which parses the same HTML through the
bundled compiler and compares the humanized tree. Cases that rely on the spec's mock schema registry
or on selectorless syntax are skipped because the real compiler behaves differently there.
"""
import ast, json, re, sys

BINDING_TYPE = {'Property': 0, 'Attribute': 1, 'Class': 2, 'Style': 3, 'LegacyAnimation': 4, 'TwoWay': 5, 'Animation': 6}
EVENT_TYPE = {'Regular': 0, 'LegacyAnimation': 1, 'TwoWay': 2, 'Animation': 3}
SKIP_MARKERS = ('mappedAttr', 'invalidProp', 'un-known', 'unknown', 'onEvent', 'selectorless')

src = open(sys.argv[1], encoding='utf-8').read()

def read_balanced(text, start, open_ch, close_ch):
    """Returns (content, end_index) for the bracketed block starting at text[start] == open_ch, string-aware."""
    depth, i, in_str, quote = 0, start, False, ''
    while i < len(text):
        ch = text[i]
        if in_str:
            if ch == '\\': i += 2; continue
            if ch == quote: in_str = False
        elif ch in ('"', "'", '`'):
            in_str, quote = True, ch
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0: return text[start + 1:i], i + 1
        i += 1
    raise ValueError('unbalanced')

STRING_LITERAL = re.compile(r"""(['"`])((?:\\.|(?!\1).)*)\1""", re.S)
ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '\\': '\\', "'": "'", '"': '"', '`': '`', '$': '$'}

def js_string(literal):
    return re.sub(r'\\(.)', lambda m: ESCAPES.get(m.group(1), m.group(1)), literal)

def split_args(text):
    """Splits a TS argument list at top-level commas, string-aware."""
    parts, depth, in_str, quote, start = [], 0, False, '', 0
    for i, ch in enumerate(text):
        if in_str:
            if ch == '\\': continue
            if ch == quote: in_str = False
        elif ch in ('"', "'", '`'): in_str, quote = True, ch
        elif ch in '([{': depth += 1
        elif ch in ')]}': depth -= 1
        elif ch == ',' and depth == 0:
            parts.append(text[start:i]); start = i + 1
    parts.append(text[start:])
    return [p.strip() for p in parts if p.strip()]

def html_arg(text):
    """Evaluates a string expression made of literals joined with `+`."""
    literals = STRING_LITERAL.findall(text)
    leftover = STRING_LITERAL.sub('', text).replace('+', '').strip()
    if not literals or leftover:
        raise ValueError('not a string concatenation: ' + text[:40])
    return ''.join(js_string(body) for _, body in literals)

def ts_literal(text):
    """Evaluates a TS array/scalar literal; keywords are only rewritten outside string literals."""
    def rewrite(code):
        code = re.sub(r'BindingType\.(\w+)', lambda m: str(BINDING_TYPE[m.group(1)]), code)
        code = re.sub(r'ParsedEventType\.(\w+)', lambda m: str(EVENT_TYPE[m.group(1)]), code)
        code = re.sub(r'\bnull\b', 'None', code)
        code = re.sub(r'\btrue\b', 'True', code)
        code = re.sub(r'\bfalse\b', 'False', code)
        return re.sub(r'/\*.*?\*/', '', code, flags=re.S)
    out, last = [], 0
    for m in STRING_LITERAL.finditer(text):
        out.append(rewrite(text[last:m.start()]))
        out.append(repr(js_string(m.group(2))))
        last = m.end()
    out.append(rewrite(text[last:]))
    return ast.literal_eval(''.join(out).strip().rstrip(','))

def current_test_name(pos):
    names = [m for m in re.finditer(r"\bit\(\s*(['\"`])((?:\\.|(?!\1).)*)\1", src[:pos])]
    return names[-1].group(2) if names else '?'

cases, skipped = [], 0
for m in re.finditer(r'expectFromHtml\(', src):
    args_text, end = read_balanced(src, m.end() - 1, '(', ')')
    rest = src[end:]
    eq = re.match(r'\s*\.toEqual\(', rest)
    if not eq:
        skipped += 1; continue
    expected_text, _ = read_balanced(rest, eq.end() - 1, '(', ')')
    args = split_args(args_text)
    try:
        html = html_arg(args[0])
    except ValueError:
        skipped += 1; continue
    ignore_error = len(args) > 1 and args[1].startswith('true')
    selectorless = len(args) > 2 and args[2].startswith('true')
    if selectorless or any(marker in html for marker in SKIP_MARKERS):
        skipped += 1; continue
    try:
        expected = ts_literal(expected_text)
    except Exception:
        skipped += 1; continue
    cases.append({'name': current_test_name(m.start()), 'html': html, 'ignoreError': ignore_error, 'expected': expected})

json.dump(cases, open(sys.argv[2], 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print(f'{len(cases)} cases written, {skipped} skipped')
