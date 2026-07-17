#!/usr/bin/env python3
"""Drive jdtls (Eclipse JDT Language Server) for semantic Java edits, headlessly.

Everything runs on the AST/project model, so package declarations, references and
imports stay correct across the whole project -- unlike hand-written text or
find-and-replace. Use this for the operations below (see CLAUDE.md >
"Creating & refactoring Java code").

Prerequisites: `jdtls` on PATH (or set JDTLS_BIN), a JDK, run from inside the repo.

Commands:
  create <file.java> <class|interface|record|enum>
        Create a type. The package is derived from the file's path and the type
        name from the filename (both by jdtls).
  rename <file> <line> <col> <newName>
  rename --token <file> <newName> <identifier>
        Rename the symbol at a position (or the first occurrence of <identifier>)
        and every reference to it across the project.
  organize-imports <file>
        Remove unused imports and sort the rest.
  extract-var    <file> <startLine> <startCol> <endLine> <endCol>
  extract-const  <file> <startLine> <startCol> <endLine> <endCol>
  extract-method <file> <startLine> <startCol> <endLine> <endCol>
  extract-field  <file> <startLine> <startCol> <endLine> <endCol>
        Extract the selected expression/statements into a local variable, constant,
        method, or field. jdtls names methods/fields with a default (rename after).
  inline <file> <line> <col>
        Inline the local variable or method at the position.

Positions are 1-based (line:col as shown in an editor). Add --dry-run to preview
without writing anything. Exit 0 = something was created/applied (or previewed).

More jdtls actions can be unlocked by loading the vscode-java extension bundles via
initializationOptions.bundles (test running, extra source actions). See CLAUDE.md.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

JDTLS_BIN = os.environ.get("JDTLS_BIN") or shutil.which("jdtls")


def project_root():
    try:
        return subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
    except Exception:
        return os.getcwd()


ROOT = project_root()
# Persistent per-project workspace so jdtls' Maven import/index stays warm between runs.
DATA_DIR = str(Path.home() / ".cache" / "jdtls-ws" / Path(ROOT).name)


def uri_to_path(uri):
    return uri[len("file://"):] if uri.startswith("file://") else uri


def path_to_uri(path):
    return "file://" + str(Path(path).resolve())


class Lsp:
    def __init__(self, dry_run):
        self.dry_run = dry_run
        os.makedirs(DATA_DIR, exist_ok=True)
        self.proc = subprocess.Popen(
            [JDTLS_BIN, "-data", DATA_DIR],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        )
        self.lock = threading.Lock()
        self.responses = {}
        self.msg_id = 0
        self.service_ready = False
        self.pushed_edits = []  # WorkspaceEdits the server asks us to apply
        threading.Thread(target=self._read_loop, daemon=True).start()

    def _send(self, obj):
        body = json.dumps(obj).encode()
        self.proc.stdin.write(f"Content-Length: {len(body)}\r\n\r\n".encode() + body)
        self.proc.stdin.flush()

    def _read_loop(self):
        buf = b""
        while True:
            c = self.proc.stdout.read(1)
            if not c:
                break
            buf += c
            if buf.endswith(b"\r\n\r\n"):
                length = 0
                for line in buf.decode(errors="replace").split("\r\n"):
                    if line.lower().startswith("content-length"):
                        length = int(line.split(":")[1].strip())
                body = b""
                while len(body) < length:
                    body += self.proc.stdout.read(length - len(body))
                buf = b""
                try:
                    self._handle(json.loads(body.decode()))
                except Exception:
                    pass

    def _handle(self, msg):
        method = msg.get("method")
        if "id" in msg and ("result" in msg or "error" in msg):
            with self.lock:
                self.responses[msg["id"]] = msg
        elif method == "language/status":
            if (msg.get("params") or {}).get("type") in ("ServiceReady", "Started"):
                self.service_ready = True
        elif method == "workspace/applyEdit":
            self.pushed_edits.append(msg["params"]["edit"])
            self._send({"jsonrpc": "2.0", "id": msg["id"], "result": {"applied": not self.dry_run}})

    def _next_id(self):
        self.msg_id += 1
        return self.msg_id

    def request(self, method, params, timeout=30):
        rid = self._next_id()
        self._send({"jsonrpc": "2.0", "id": rid, "method": method, "params": params})
        end = time.time() + timeout
        while time.time() < end:
            with self.lock:
                if rid in self.responses:
                    return self.responses.pop(rid)
            time.sleep(0.15)
        return None

    def notify(self, method, params):
        self._send({"jsonrpc": "2.0", "method": method, "params": params})

    def initialize(self):
        root_uri = "file://" + ROOT
        self.request("initialize", {
            "processId": os.getpid(),
            "rootUri": root_uri,
            "workspaceFolders": [{"uri": root_uri, "name": Path(ROOT).name}],
            "capabilities": {
                "workspace": {"applyEdit": True, "workspaceEdit": {"documentChanges": True, "resourceOperations": ["rename"]}},
                "textDocument": {"rename": {"prepareSupport": True},
                                 "completion": {"completionItem": {"snippetSupport": True}},
                                 "codeAction": {
                                     "codeActionLiteralSupport": {"codeActionKind": {"valueSet": [
                                         "refactor", "refactor.extract", "refactor.inline", "refactor.rewrite",
                                         "source", "source.organizeImports"]}},
                                     "resolveSupport": {"properties": ["edit"]},
                                     "dataSupport": True}},
            },
            "initializationOptions": {"bundles": []},
        }, timeout=40)
        self.notify("initialized", {})
        end = time.time() + 90
        while time.time() < end and not self.service_ready:
            time.sleep(0.5)
        time.sleep(3)  # let the Maven project import settle

    def did_open(self, path, text=None):
        if text is None:
            text = Path(path).read_text()
        self.notify("textDocument/didOpen", {"textDocument": {
            "uri": path_to_uri(path), "languageId": "java", "version": 1, "text": text}})
        time.sleep(2)

    def shutdown(self):
        self.request("shutdown", None, timeout=5)
        self.notify("exit", {})
        self.proc.terminate()


# --- WorkspaceEdit application --------------------------------------------
def _apply_text_edits(text, edits):
    lines = text.split("\n")
    starts, pos = [], 0
    for ln in lines:
        starts.append(pos)
        pos += len(ln) + 1

    def off(p):
        return starts[p["line"]] + p["character"]

    for e in sorted(edits, key=lambda e: off(e["range"]["start"]), reverse=True):
        s, en = off(e["range"]["start"]), off(e["range"]["end"])
        text = text[:s] + e["newText"] + text[en:]
    return text


def collect(edit):
    file_edits, renames = {}, []
    if edit.get("documentChanges"):
        for ch in edit["documentChanges"]:
            if "kind" in ch:
                if ch["kind"] == "rename":
                    renames.append((ch["oldUri"], ch["newUri"]))
            else:
                file_edits.setdefault(ch["textDocument"]["uri"], []).extend(ch["edits"])
    for uri, edits in (edit.get("changes") or {}).items():
        file_edits.setdefault(uri, []).extend(edits)
    return file_edits, renames


def describe(edit):
    file_edits, renames = collect(edit)
    for uri, edits in file_edits.items():
        rel = os.path.relpath(uri_to_path(uri), ROOT)
        print(f"  {rel}: {len(edits)} edit(s)")
        for e in sorted(edits, key=lambda e: (e["range"]["start"]["line"], e["range"]["start"]["character"])):
            r = e["range"]
            preview = e["newText"].replace("\n", "\\n")
            if len(preview) > 70:
                preview = preview[:67] + "..."
            print(f"      L{r['start']['line']+1}:{r['start']['character']+1}"
                  f"-L{r['end']['line']+1}:{r['end']['character']+1} -> \"{preview}\"")
    for old, new in renames:
        print(f"  RENAME FILE {os.path.relpath(uri_to_path(old), ROOT)} -> {os.path.relpath(uri_to_path(new), ROOT)}")


def apply(edit):
    file_edits, renames = collect(edit)
    changed = []
    for uri, edits in file_edits.items():
        p = uri_to_path(uri)
        Path(p).write_text(_apply_text_edits(Path(p).read_text(), edits))
        changed.append(os.path.relpath(p, ROOT))
    for old, new in renames:
        os.rename(uri_to_path(old), uri_to_path(new))
        changed.append(f"{os.path.relpath(uri_to_path(old), ROOT)} -> {os.path.relpath(uri_to_path(new), ROOT)}")
    return changed


def report(label, edits, dry_run):
    edits = [e for e in edits if e and (collect(e)[0] or collect(e)[1])]
    if not edits:
        print(f"jdtls returned no edit for {label} "
              "(nothing to change, position not applicable, or project still importing).", file=sys.stderr)
        return 1
    print(f"=== {label} {'(DRY RUN, nothing written)' if dry_run else ''} ===")
    changed = []
    for e in edits:
        if dry_run:
            describe(e)
        else:
            changed += apply(e)
    if not dry_run:
        print("Applied. Changed:")
        for c in changed:
            print(f"  {c}")
    return 0


# --- helpers ---------------------------------------------------------------
def pos(line, col):
    return {"line": int(line) - 1, "character": int(col) - 1}


def resolve_token(path, token):
    for i, line in enumerate(Path(path).read_text().split("\n")):
        j = line.find(token)
        if j != -1:
            return i + 1, j + 1
    sys.exit(f"token {token!r} not found in {path}")


def desnippet(s):
    s = s.replace("\\$", "\x00")
    s = re.sub(r"\$\{\d+:([^}]*)\}", r"\1", s)         # ${1:default} -> default
    s = re.sub(r"\$\{\d+\|([^,|]*)[^}]*\}", r"\1", s)  # ${1|a,b|} -> a
    s = re.sub(r"\$\{\d+\}", "", s)                    # ${0} -> ""
    s = re.sub(r"\$\d+", "", s)                        # $0 -> ""
    return s.replace("\x00", "$")


def completion_items(result):
    return result.get("items", []) if isinstance(result, dict) else (result or [])


# --- commands --------------------------------------------------------------
def do_create(lsp, args):
    file, kind = args[0], args[1]
    if kind not in ("class", "interface", "record", "enum"):
        sys.exit("kind must be one of: class interface record enum")
    p = Path(file)
    if p.exists() and p.read_text().strip():
        sys.exit(f"{file} already exists and is not empty; refusing to overwrite.")
    p.parent.mkdir(parents=True, exist_ok=True)
    pre_existed = p.exists()
    p.write_text("")  # jdtls derives the package name from the on-disk path

    source_kind = "class" if kind == "enum" else kind  # this jdtls build has no enum snippet
    snippets = {}
    try:
        lsp.did_open(file, text="")
        for _ in range(8):
            comp = lsp.request("textDocument/completion", {
                "textDocument": {"uri": path_to_uri(file)}, "position": {"line": 0, "character": 0}})
            snippets = {it["label"]: (it.get("textEdit") or {}).get("newText") or it.get("insertText")
                        for it in completion_items((comp or {}).get("result"))
                        if it.get("kind") == 15 and it.get("label") in ("class", "interface", "record")}
            if source_kind in snippets:
                break
            time.sleep(8)
    finally:
        if lsp.dry_run and not pre_existed:
            p.unlink(missing_ok=True)

    if source_kind not in snippets:
        print(f"jdtls offered no '{source_kind}' snippet (project still importing?).", file=sys.stderr)
        return 1
    text = desnippet(snippets[source_kind])
    if kind == "enum":
        text = text.replace("public class", "public enum", 1)
    if not text.endswith("\n"):
        text += "\n"

    if lsp.dry_run:
        print(f"=== create {kind} {file} (DRY RUN, nothing written) ===")
    else:
        p.write_text(text)
        print(f"=== created {kind} {file} ===")
    print(text)
    return 0


def do_rename(lsp, args):
    if args and args[0] == "--token":
        file, new_name, token = args[1], args[2], args[3]
        line, col = resolve_token(file, token)
    else:
        file, line, col, new_name = args[0], args[1], args[2], args[3]
    lsp.did_open(file)
    label = f"rename @ {file}:{line}:{col} -> {new_name}"
    for _ in range(8):  # retry while the Maven import completes
        resp = lsp.request("textDocument/rename", {
            "textDocument": {"uri": path_to_uri(file)}, "position": pos(line, col), "newName": new_name})
        if resp and resp.get("result") and collect(resp["result"])[0]:
            return report(label, [resp["result"]], lsp.dry_run)
        time.sleep(8)
    return report(label, [], lsp.dry_run)


def do_organize(lsp, args):
    file = args[0]
    lsp.did_open(file)
    label = f"organize-imports @ {file}"
    for _ in range(8):
        lsp.pushed_edits.clear()
        resp = lsp.request("workspace/executeCommand", {
            "command": "java.edit.organizeImports", "arguments": [path_to_uri(file)]}, timeout=30)
        edits = []
        result = (resp or {}).get("result")
        if isinstance(result, dict):
            edits.append(result)
        edits += list(lsp.pushed_edits)
        if any(collect(e)[0] for e in edits):
            return report(label, edits, lsp.dry_run)
        if resp is not None:  # server answered; imports already clean -> stop retrying
            return report(label, edits, lsp.dry_run)
        time.sleep(8)
    return report(label, [], lsp.dry_run)


# Refactorings jdtls exposes as resolvable code actions:
#   name -> (CodeActionKind, title substring to prefer, avoid substring)
REFACTORS = {
    "extract-var": ("refactor.extract.variable", "local variable", "all occurrences"),
    "extract-const": ("refactor.extract.constant", "constant", None),
    "extract-method": ("refactor.extract.function", "method", None),
    "extract-field": ("refactor.extract.field", "field", None),
    "inline": ("refactor.inline", "inline", None),
}


def do_refactor(lsp, name, file, rng):
    kind, prefer, avoid = REFACTORS[name]
    lsp.did_open(file)
    label = f"{name} @ {file}"
    for _ in range(8):
        resp = lsp.request("textDocument/codeAction", {
            "textDocument": {"uri": path_to_uri(file)}, "range": rng,
            "context": {"diagnostics": [], "only": [kind], "triggerKind": 1}})
        actions = [a for a in ((resp or {}).get("result") or [])
                   if isinstance(a, dict) and a.get("kind", "").startswith(kind)]
        if avoid:
            actions = [a for a in actions if avoid.lower() not in a.get("title", "").lower()] or actions
        action = next((a for a in actions if prefer.lower() in a.get("title", "").lower()), None) \
            or (actions[0] if actions else None)
        if action:
            return _run_action(lsp, action, f"{label} ({action.get('title')})")
        time.sleep(8)
    print(f"jdtls offered no '{kind}' action for that selection.", file=sys.stderr)
    return 1


def _run_action(lsp, action, label):
    if action.get("edit") is None and action.get("data") is not None:
        resolved = lsp.request("codeAction/resolve", action)
        if resolved and resolved.get("result"):
            action = resolved["result"]
    if action.get("edit"):
        return report(label, [action["edit"]], lsp.dry_run)
    if action.get("command"):
        cmd = action["command"]
        cmd = cmd if isinstance(cmd, dict) else {"command": cmd}
        lsp.pushed_edits.clear()
        lsp.request("workspace/executeCommand",
                    {"command": cmd["command"], "arguments": cmd.get("arguments", [])}, timeout=30)
        time.sleep(1)
        return report(label, list(lsp.pushed_edits), lsp.dry_run)
    print("Selected action carried no applicable edit.", file=sys.stderr)
    return 1


def selection(args):
    """<file> <startLine> <startCol> <endLine> <endCol> -> (file, range)."""
    f, sl, sc, el, ec = args[0], args[1], args[2], args[3], args[4]
    return f, {"start": pos(sl, sc), "end": pos(el, ec)}


COMMANDS = {"create", "rename", "organize-imports",
            "extract-var", "extract-const", "extract-method", "extract-field", "inline"}


def main():
    argv = sys.argv[1:]
    dry_run = "--dry-run" in argv
    argv = [a for a in argv if a != "--dry-run"]
    if not argv or argv[0] not in COMMANDS:
        print(__doc__)
        sys.exit(2)
    if not JDTLS_BIN:
        sys.exit("jdtls not found. Put it on PATH or set JDTLS_BIN. See CLAUDE.md.")
    cmd, args = argv[0], argv[1:]

    lsp = Lsp(dry_run)
    try:
        print(f"Starting jdtls (workspace {DATA_DIR})...", file=sys.stderr)
        lsp.initialize()
        if cmd == "create":
            rc = do_create(lsp, args)
        elif cmd == "rename":
            rc = do_rename(lsp, args)
        elif cmd == "organize-imports":
            rc = do_organize(lsp, args)
        elif cmd == "inline":
            file, line, col = args[0], args[1], args[2]
            rc = do_refactor(lsp, "inline", file, {"start": pos(line, col), "end": pos(line, col)})
        elif cmd in REFACTORS:  # extract-var / extract-const / extract-method / extract-field
            f, rng = selection(args)
            rc = do_refactor(lsp, cmd, f, rng)
    finally:
        lsp.shutdown()
    sys.exit(rc)


if __name__ == "__main__":
    main()
