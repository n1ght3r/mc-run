#!/usr/bin/env python3
"""
Generates .claude/codemap.json, the machine-readable knowledge graph of mc-run.

Nodes are Kotlin source files (package, declarations, line count, role). Edges are "uses"
relations: file A references a top-level declaration of file B. The role of a file is the first
sentence of the KDoc above its first top-level declaration, so keeping KDoc accurate keeps the graph
accurate.

Usage:
    python tools/codemap.py          # rewrite .claude/codemap.json
    python tools/codemap.py --check  # exit 1 if the file is stale (use before committing)
"""

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = {"main": ROOT / "src/main/kotlin", "test": ROOT / "src/test/kotlin"}
OUTPUT = ROOT / ".claude/codemap.json"
BASE_PACKAGE = "io.github.n1ght3r.mcrun"

DECLARATION = re.compile(
    r"^(?:(?:public|internal|private|abstract|open|sealed|data|enum|annotation|inline|value)\s+)*"
    r"(class|interface|object|fun|val|const val)\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
KDOC = re.compile(r"/\*\*(.*?)\*/", re.DOTALL)
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
COMMENT_OR_STRING = re.compile(r'/\*.*?\*/|//[^\n]*|"""(?:.|\n)*?"""|"(?:\\.|[^"\\])*"', re.DOTALL)


def top_level_declarations(text):
    """Declarations that start at column 0, i.e. top level, with their KDoc if any."""
    found = []
    for match in DECLARATION.finditer(text):
        kind, name = match.group(1), match.group(2)
        if kind in ("fun", "val", "const val"):
            # Top-level functions and properties are rare here; classes carry the structure.
            continue
        before = text[: match.start()].rstrip()
        doc = None
        # Skip annotations between the KDoc and the declaration.
        while before.endswith(")") or re.search(r"@\w+(\([^)]*\))?$", before):
            stripped = re.sub(r"@[\w.:]+(\([^()]*(\([^()]*\))*[^()]*\))?$", "", before).rstrip()
            if stripped == before:
                break
            before = stripped
        if before.endswith("*/"):
            start = before.rfind("/**")
            if start != -1:
                doc = before[start:]
        found.append({"kind": kind, "name": name, "doc": clean_doc(doc)})
    return found


def clean_doc(doc):
    if not doc:
        return None
    body = KDOC.match(doc)
    if not body:
        return None
    lines = [re.sub(r"^\s*\*\s?", "", line) for line in body.group(1).splitlines()]
    text = " ".join(line.strip() for line in lines if line.strip())
    # The first sentence is the role; stop at a period followed by a space or the end.
    sentence = re.split(r"(?<=\.)\s", text, maxsplit=1)[0]
    return sentence.strip()


def file_role(stem, decls):
    """The KDoc of the declaration named like the file, else the first documented one."""
    named = next((d["doc"] for d in decls if d["name"] == stem and d["doc"]), None)
    return named or next((d["doc"] for d in decls if d["doc"]), None)


def git_commit():
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, stderr=subprocess.DEVNULL, text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def build():
    files = []
    for source_set, src_root in SOURCE_ROOTS.items():
        for path in sorted(src_root.rglob("*.kt")):
            text = path.read_text(encoding="utf-8")
            package = PACKAGE.search(text).group(1)
            decls = top_level_declarations(text)
            files.append(
                {
                    "path": path.relative_to(ROOT).as_posix(),
                    "sourceSet": source_set,
                    "package": package,
                    "lines": text.count("\n") + 1,
                    "declarations": [{"name": d["name"], "kind": d["kind"], "role": d["doc"]} for d in decls],
                    "role": file_role(path.stem, decls),
                    "_text": COMMENT_OR_STRING.sub(" ", text),
                }
            )

    owner = {}
    for f in files:
        if f["sourceSet"] == "main":
            for decl in f["declarations"]:
                owner[decl["name"]] = f["path"]

    edges = []
    for f in files:
        own = {d["name"] for d in f["declarations"]}
        used = set()
        for name, target in owner.items():
            if name in own or target == f["path"]:
                continue
            if re.search(r"\b" + re.escape(name) + r"\b", f["_text"]):
                used.add(target)
        for target in sorted(used):
            edges.append({"from": f["path"], "to": target, "type": "uses"})

    for f in files:
        del f["_text"]

    packages = {}
    for f in files:
        if f["sourceSet"] != "main":
            continue
        short = f["package"][len(BASE_PACKAGE):].lstrip(".") or "(root)"
        doc_path = ROOT / Path(f["path"]).parent / "AGENTS.md"
        packages.setdefault(
            short,
            {
                "package": f["package"],
                "doc": doc_path.relative_to(ROOT).as_posix() if doc_path.exists() else None,
                "files": [],
            },
        )["files"].append(f["path"])

    main_files = [f for f in files if f["sourceSet"] == "main"]
    return {
        "codemapVersion": 1,
        "generatedBy": "tools/codemap.py",
        "generatedFromCommit": git_commit(),
        "regenerateHint": "Do not edit by hand. Run `python tools/codemap.py` after adding, removing or "
        "renaming a file or changing a declaration's KDoc; `--check` fails when it is stale.",
        "project": {
            "name": "mc-run",
            "description": "Gradle plugin that launches a Paper/Folia dev server and a vanilla Minecraft "
            "client from one build, with a runDev task that starts both and auto-joins.",
            "basePackage": BASE_PACKAGE,
            "language": "Kotlin (kotlin-dsl), Java 17 bytecode",
            "pluginId": "io.github.n1ght3r.mc-run",
            "docs": {"root": "AGENTS.md", "codemap": ".claude/CODEMAP.md"},
            "stats": {
                "mainFiles": len(main_files),
                "mainLines": sum(f["lines"] for f in main_files),
                "testFiles": len(files) - len(main_files),
            },
        },
        "packages": dict(sorted(packages.items())),
        "nodes": files,
        "edges": edges,
    }


def render(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main():
    data = build()
    check = "--check" in sys.argv[1:]
    if check:
        if not OUTPUT.exists():
            print(f"{OUTPUT.relative_to(ROOT)} is missing; run python tools/codemap.py")
            return 1
        current = json.loads(OUTPUT.read_text(encoding="utf-8"))
        # The commit hash changes on every commit; compare everything else.
        current["generatedFromCommit"] = data["generatedFromCommit"]
        if render(current) != render(data):
            print(f"{OUTPUT.relative_to(ROOT)} is stale; run python tools/codemap.py")
            return 1
        print("codemap is up to date")
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(data), encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)}: {len(data['nodes'])} nodes, {len(data['edges'])} edges")
    return 0


if __name__ == "__main__":
    sys.exit(main())
