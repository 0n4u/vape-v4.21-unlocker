#!/usr/bin/env python3



import os, re, sys

CODE_EXTS = ('.java', '.c', '.cpp', '.h', '.gradle', '.py', '.rc', '.in')
CMAKE_NAMES = ('CMakeLists.txt',)
BAT_EXTS = ('.bat',)

def strip_java_like(text):

    out = []
    i = 0
    in_string = False
    in_char = False
    in_block = False
    string_char = None
    escape = False

    while i < len(text):
        ch = text[i]

        if in_block:
            if ch == '*' and i + 1 < len(text) and text[i + 1] == '/':
                in_block = False
                i += 2
                out.append(' ')
                continue
            i += 1
            continue

        if in_string or in_char:
            if escape:
                escape = False
                out.append(ch); i += 1; continue
            if ch == '\\':
                escape = True
                out.append(ch); i += 1; continue
            if ch == string_char:
                in_string = in_char = False
                out.append(ch); i += 1; continue
            out.append(ch); i += 1; continue

        if ch == '"':
            in_string = True; string_char = '"'
            out.append(ch); i += 1; continue
        if ch == "'":
            in_char = True; string_char = "'"
            out.append(ch); i += 1; continue

        if ch == '/' and i + 1 < len(text) and text[i + 1] == '/':
            while i < len(text) and text[i] != '\n':
                i += 1
            out.append('\n')
            continue

        if ch == '/' and i + 1 < len(text) and text[i + 1] == '*':
            in_block = True
            i += 2
            continue

        out.append(ch); i += 1

    return ''.join(out)

def strip_python(text):

    lines = text.splitlines(keepends=True)
    out = []
    in_docstring = False
    doc_char = None
    for idx, line in enumerate(lines):

        if idx == 0 and line.startswith('#!'):
            out.append(line)
            continue

        stripped = line.strip()
        if not in_docstring and (stripped.startswith('"""') or stripped.startswith("'''")):
            marker = stripped[:3]

            rest = stripped[3:]
            if rest.endswith(marker) and len(rest) >= 3:
                out.append('\n')
                continue
            in_docstring = True
            doc_char = marker

            out.append('\n')
            continue
        if in_docstring:
            if doc_char in line:
                in_docstring = False
            out.append('\n')
            continue

        if stripped.startswith('#'):
            out.append('\n')
            continue

        processed = _strip_python_inline(line)
        out.append(processed)
    return ''.join(out)

def _strip_python_inline(line):

    in_s = False; in_d = False; escape = False
    for i, ch in enumerate(line):
        if escape:
            escape = False; continue
        if ch == '\\':
            escape = True; continue
        if ch == "'" and not in_d:
            in_s = not in_s; continue
        if ch == '"' and not in_s:
            in_d = not in_d; continue
        if ch == '#' and not in_s and not in_d:
            return line[:i].rstrip() + '\n'
    return line

def strip_cmake(text):
    lines = text.splitlines(keepends=True)
    out = []
    for line in lines:
        s = line.strip()
        if s.startswith('#') and not s.startswith('#cmakedefine') and not s.startswith('#define'):
            out.append('\n')
        else:
            out.append(line)
    return ''.join(out)

def strip_batch(text):
    lines = text.splitlines(keepends=True)
    out = []
    for line in lines:
        s = line.lstrip()
        if s[:3].upper() == 'REM' and (len(s) == 3 or s[3].isspace()):
            out.append('\n')
        elif s.startswith('::'):
            out.append('\n')
        else:
            out.append(line)
    return ''.join(out)

def strip_file(path):
    fname = os.path.basename(path)
    rel = path.replace('\\', '/')

    if 'gradle-wrapper' in rel or fname in ('LICENSE', 'README.md', 'README') or fname.endswith(('.md', '.properties.txt', '.csv', '.ttf', '.otf', '.png', '.wav', '.jar', '.map', '.srg')):
        return False
    ext = os.path.splitext(fname)[1].lower()
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            orig = f.read()
    except Exception:
        return False

    if ext in CODE_EXTS:
        if ext == '.py':
            result = strip_python(orig)
        else:
            result = strip_java_like(orig)
    elif fname in CMAKE_NAMES or ext == '.cmake':
        result = strip_cmake(orig)
    elif ext in BAT_EXTS:
        result = strip_batch(orig)
    else:
        return False

    if result != orig:
        with open(path, 'w', encoding='utf-8', newline='') as f:
            f.write(result)
        return True
    return False

if __name__ == '__main__':
    roots = sys.argv[1:] if len(sys.argv) > 1 else ['.']
    total = modified = 0
    for root in roots:
        for dirpath, dirnames, fnames in os.walk(root):
            rel = dirpath.replace('\\', '/')
            if any(skip in rel for skip in ('/.git', '/.gradle', '/build', '/.lunarunlocker', '/.vapeclient')):
                continue
            for fn in fnames:
                full = os.path.join(dirpath, fn)
                total += 1
                if strip_file(full):
                    modified += 1
    print(f"Scanned: {total}, modified: {modified}")