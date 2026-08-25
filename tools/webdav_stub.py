#!/usr/bin/env python3
"""Minimal HTTP PUT/GET file store for Tracker WebDAV tests.

Speaks enough of what :webdav WebDavClient sends: Basic auth, Overwrite: T,
no redirects, GET 404 if missing, PUT 201/204. Not a real DAV server.

  python3 tools/webdav_stub.py [--port 8765] [--user paul] [--password secret] [--dir DIR]

Emulator host loopback is 10.0.2.2. App URL:
  http://10.0.2.2:8765/tracker.json
"""

from __future__ import annotations

import argparse
import base64
import http.server
import os
import sys
from pathlib import Path


class Handler(http.server.BaseHTTPRequestHandler):
    root: Path
    user: str
    password: str

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _auth_ok(self) -> bool:
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return False
        try:
            raw = base64.b64decode(header[6:].encode("ascii"), validate=True)
            got = raw.decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            return False
        return got == "%s:%s" % (self.user, self.password)

    def _path_file(self) -> Path | None:
        rel = self.path.split("?", 1)[0].lstrip("/")
        if not rel or ".." in rel.split("/"):
            return None
        return self.root / rel

    def do_GET(self) -> None:
        if not self._auth_ok():
            self.send_response(401)
            self.send_header("WWW-Authenticate", 'Basic realm="tracker"')
            self.end_headers()
            return
        path = self._path_file()
        if path is None:
            self.send_error(400)
            return
        if not path.is_file():
            self.send_error(404)
            return
        data = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_PUT(self) -> None:
        if not self._auth_ok():
            self.send_response(401)
            self.send_header("WWW-Authenticate", 'Basic realm="tracker"')
            self.end_headers()
            return
        path = self._path_file()
        if path is None:
            self.send_error(400)
            return
        n = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(n)
        path.parent.mkdir(parents=True, exist_ok=True)
        existed = path.is_file()
        path.write_bytes(body)
        self.send_response(204 if existed else 201)
        self.end_headers()

    def do_HEAD(self) -> None:
        self.do_GET()


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--user", default="paul")
    p.add_argument("--password", default="secret")
    p.add_argument("--dir", default="")
    p.add_argument("--bind", default="0.0.0.0")
    args = p.parse_args()
    root = Path(args.dir).resolve() if args.dir else Path.cwd() / "webdav-stub-data"
    root.mkdir(parents=True, exist_ok=True)
    Handler.root = root
    Handler.user = args.user
    Handler.password = args.password
    http.server.ThreadingHTTPServer.allow_reuse_address = True
    server = http.server.ThreadingHTTPServer((args.bind, args.port), Handler)
    print("webdav-stub root=%s  http://127.0.0.1:%d/tracker.json" % (root, args.port), flush=True)
    print("auth %s:%s" % (args.user, args.password), flush=True)
    print("emulator URL http://10.0.2.2:%d/tracker.json" % args.port, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
