#!/usr/bin/env python3
import argparse
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "app/src/main/assets/extensions/semo/psl.js"
URL = "https://publicsuffix.org/list/public_suffix_list.dat"

TEMPLATE = """(function (root) {
  'use strict';
  var RAW = [
%(chunks)s
  ].join(' ');
  var rules = Object.create(null), exc = Object.create(null);
  RAW.split(' ').forEach(function (r) {
    if (!r) return;
    if (r.charAt(0) === '!') exc[r.slice(1)] = 1; else rules[r] = 1;
  });
  function registrable(host) {
    host = String(host || '').toLowerCase().replace(/\\.$/, '');
    if (!host || host.indexOf(':') >= 0 || /^\\d+\\.\\d+\\.\\d+\\.\\d+$/.test(host)) return host;
    var l = host.split('.'), n = l.length;
    if (n < 2) return host;
    var suffixLen = 1;
    for (var i = 0; i < n; i++) {
      var cand = l.slice(i).join('.');
      if (exc[cand]) { suffixLen = n - i - 1; break; }
      if (rules[cand] || (i + 1 < n && rules['*.' + l.slice(i + 1).join('.')])) { suffixLen = n - i; break; }
    }
    if (n <= suffixLen) return host;
    return l.slice(n - suffixLen - 1).join('.');
  }
  var api = { registrable: registrable };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.__semoPsl = api;
})(typeof self !== 'undefined' ? self : this);
"""


def ascii_label(label):
    if label == "*" or label.isascii():
        return label
    try:
        return label.encode("idna").decode("ascii")
    except UnicodeError:
        return "xn--" + label.encode("punycode").decode("ascii")


def to_ascii(rule):
    bang = rule.startswith("!")
    body = rule[1:] if bang else rule
    body = ".".join(ascii_label(p) for p in body.lower().split("."))
    return ("!" if bang else "") + body


def parse(text):
    out = set()
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        token = line.split()[0]
        if token:
            out.add(to_ascii(token))
    return sorted(out)


def render(rules, width=110):
    chunks, cur = [], ""
    for r in rules:
        if cur and len(cur) + 1 + len(r) > width:
            chunks.append(cur)
            cur = r
        else:
            cur = (cur + " " + r) if cur else r
    if cur:
        chunks.append(cur)
    return TEMPLATE % {"chunks": ",\n".join("    '" + c + "'" for c in chunks)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input")
    ap.add_argument("--output", default=str(DEFAULT_OUT))
    ap.add_argument("--url", default=URL)
    a = ap.parse_args()
    if a.input:
        text = Path(a.input).read_text(encoding="utf-8")
    else:
        with urllib.request.urlopen(a.url, timeout=60) as r:
            text = r.read().decode("utf-8")
    rules = parse(text)
    if len(rules) < 100 and not a.input:
        sys.exit("suspiciously small list: " + str(len(rules)))
    Path(a.output).write_text(render(rules), encoding="utf-8")
    print("rules:", len(rules), "->", a.output)


if __name__ == "__main__":
    main()
