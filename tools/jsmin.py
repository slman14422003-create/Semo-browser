import re

WORD = re.compile(r"[A-Za-z0-9_$\u0080-\uffff]")
KEYWORDS = {"return", "typeof", "case", "in", "of", "delete", "void", "throw", "new", "else", "do", "instanceof", "yield", "await"}
OPEN_BEFORE = set("({[,;:=?&|+-*/%<>^~!")
CLOSE_AFTER = set(")}],;:.?&|=*/%<>^")


def minify(src):
    out = []
    i = 0
    n = len(src)
    last = ""
    word_tail = ""
    pending = ""

    def emit(text):
        nonlocal last, word_tail
        out.append(text)
        last = text[-1]
        if WORD.match(last):
            m = re.search(r"[A-Za-z0-9_$\u0080-\uffff]+$", "".join(out[-3:]))
            word_tail = m.group(0) if m else ""
        else:
            word_tail = ""

    def flush(nxt):
        nonlocal pending
        if not pending:
            return
        had_nl = "\n" in pending
        pending = ""
        if not last:
            return
        if WORD.match(last) and WORD.match(nxt):
            emit("\n" if had_nl else " ")
        elif (last == "+" and nxt == "+") or (last == "-" and nxt == "-"):
            emit(" ")
        elif had_nl and last not in OPEN_BEFORE and nxt not in CLOSE_AFTER:
            emit("\n")

    def regex_allowed():
        if not last:
            return True
        if last in "({[,;:=?&|+-*%<>^~!{}":
            return last != "}" or True
        if last in ")]":
            return False
        if WORD.match(last):
            return word_tail in KEYWORDS
        return False

    while i < n:
        c = src[i]
        if c in " \t\r\n":
            pending += c
            i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
            pending += "\n" if j >= 0 else ""
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            pending += " "
            continue
        flush(c)
        if c in "'\"":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            emit(src[i : j + 1])
            i = j + 1
            continue
        if c == "`":
            j = i + 1
            while j < n and src[j] != "`":
                j += 2 if src[j] == "\\" else 1
            emit(src[i : j + 1])
            i = j + 1
            continue
        if c == "/" and regex_allowed():
            j = i + 1
            in_class = False
            while j < n:
                ch = src[j]
                if ch == "\\":
                    j += 2
                    continue
                if ch == "[":
                    in_class = True
                elif ch == "]":
                    in_class = False
                elif ch == "/" and not in_class:
                    break
                j += 1
            j += 1
            while j < n and re.match(r"[a-z]", src[j]):
                j += 1
            emit(src[i:j])
            i = j
            continue
        m = re.match(r"[A-Za-z0-9_$\u0080-\uffff]+", src[i:])
        if m:
            emit(m.group(0))
            i += len(m.group(0))
            continue
        emit(c)
        i += 1
    return "".join(out).strip() + "\n"
