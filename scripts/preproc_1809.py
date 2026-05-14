#!/usr/bin/env python3
"""
Resolve ReplayMod preprocess directives for the 1.8.9-forge node.

Symbols:
  MC=10809, FORGE=1, FABRIC=0, NEOFORGE=0, FORGELIKE=1, MODERN=0

Directive forms (whitespace insensitive):
  //#if EXPR ... //#elseif EXPR ... //#else ... //#endif

Inside disabled branches, code lines either are bare source (we drop them) or
are commented with a leading `//$$` token (we drop them).
Inside enabled branches, lines prefixed with `//$$` get uncommented; bare
source lines pass through.

Expressions are tiny: identifiers, integer literals, &&, ||, !, parens,
==, !=, <, <=, >, >=.
"""

import ast
import os
import re
import sys
from pathlib import Path

SYMBOLS = {
    "MC": 10809,
    "FORGE": 1,
    "FABRIC": 0,
    "NEOFORGE": 0,
    "FORGELIKE": 1,
    "MODERN": 0,
}

DIRECTIVE = re.compile(r"^(\s*)//#(if|elseif|else|endif)\b(.*)$")
DOLLAR = re.compile(r"^(\s*)//\$\$ ?(.*)$")


def eval_expr(expr: str) -> bool:
    # Translate to Python: && -> and, || -> or, ! -> not, leave the rest alone.
    py = expr.strip()
    py = re.sub(r"&&", " and ", py)
    py = re.sub(r"\|\|", " or ", py)
    py = re.sub(r"(?<![<>=!])!(?!=)", " not ", py)
    try:
        return bool(eval(py, {"__builtins__": {}}, SYMBOLS))
    except Exception as e:
        raise RuntimeError(f"bad expr {expr!r}: {e}")


def process(src: str) -> str:
    lines = src.splitlines(keepends=False)
    out: list[str] = []
    # stack of frames: dict(active=bool, taken=bool, parent_active=bool)
    stack: list[dict] = []

    def in_active() -> bool:
        return all(f["active"] for f in stack)

    for raw in lines:
        m = DIRECTIVE.match(raw)
        if m:
            kind = m.group(2)
            expr = m.group(3).strip()
            if kind == "if":
                parent_active = in_active()
                cond = eval_expr(expr) if parent_active else False
                stack.append({
                    "active": cond,
                    "taken": cond,
                    "parent_active": parent_active,
                })
            elif kind == "elseif":
                if not stack:
                    raise RuntimeError("dangling //#elseif")
                f = stack[-1]
                if f["taken"]:
                    f["active"] = False
                else:
                    cond = eval_expr(expr) if f["parent_active"] else False
                    f["active"] = cond
                    if cond:
                        f["taken"] = True
            elif kind == "else":
                if not stack:
                    raise RuntimeError("dangling //#else")
                f = stack[-1]
                f["active"] = (not f["taken"]) and f["parent_active"]
                if f["active"]:
                    f["taken"] = True
            elif kind == "endif":
                if not stack:
                    raise RuntimeError("dangling //#endif")
                stack.pop()
            continue

        if not in_active():
            # Drop the line entirely (commented or not).
            continue

        d = DOLLAR.match(raw)
        if d:
            # Uncomment.
            out.append(d.group(1) + d.group(2))
        else:
            out.append(raw)

    if stack:
        raise RuntimeError("unclosed preprocess block")
    return "\n".join(out) + ("\n" if src.endswith("\n") else "")


def main(src_root: Path, dst_root: Path):
    suffixes = {".kt", ".java", ".gradle", ".json", ".mcmeta", ".cfg", ".accesswidener"}
    for path in src_root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(src_root)
        out_path = dst_root / rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        if path.suffix in suffixes:
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                out_path.write_bytes(path.read_bytes())
                continue
            try:
                out_path.write_text(process(text), encoding="utf-8")
            except Exception as e:
                print(f"FAIL {rel}: {e}", file=sys.stderr)
                out_path.write_text(text, encoding="utf-8")
        else:
            out_path.write_bytes(path.read_bytes())


if __name__ == "__main__":
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])
    main(src, dst)
    print(f"flattened {src} -> {dst}")
