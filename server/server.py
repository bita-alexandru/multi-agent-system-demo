from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs, unquote
from pathlib import Path
import re

# Directories
CV_DIR = Path("./employees_cv")
ID_DIR = Path("./employees_id")
DOCS_DIR = Path("./employees_docs")
INTERNAL_DIR = Path("./internal_docs")

SAFE_NAME = re.compile(r"^[A-Za-z0-9 _.\-]+$")  # allow spaces now
ALLOWED_DIRS = {
    "employees_cv": CV_DIR,
    "employees_id": ID_DIR,
    "employees_docs": DOCS_DIR,
    "internal_docs": INTERNAL_DIR,
}

class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: str, content_type: str = "text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.end_headers()
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.wfile.write(body)

    def _get_first_name(self, query):
        if "first_name" not in query:
            self.send_error(404, "Missing required parameter: first_name")
            return None
        first_name = query["first_name"][0]
        if not SAFE_NAME.match(first_name):
            self.send_error(404, "Invalid first_name")
            return None
        return first_name

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        # Root endpoint -> list folders
        if path == "/":
            body = "<html><body><h1>Docs</h1><ul>"
            for folder_name in ALLOWED_DIRS:
                body += f'<li><a href="/browse/{folder_name}">{folder_name}</a></li>'
            body += "</ul></body></html>"
            self._send(200, body, "text/html")
            return

        # Browse folder listing
        if path.startswith("/browse/"):
            folder_name = path.split("/browse/")[-1]
            if folder_name not in ALLOWED_DIRS:
                self.send_error(404, "Folder not allowed")
                return
            folder = ALLOWED_DIRS[folder_name]
            items = "".join(
                f'<li><a href="/{folder_name}/{f.name}">{f.name}</a></li>'
                for f in folder.iterdir()
            )
            body = f"<html><body><h1>{folder_name}</h1><ul>{items}</ul></body></html>"
            self._send(200, body, "text/html")
            return

        # Serve any file under allowed folders
        parts = path.strip("/").split("/", 1)
        if len(parts) == 2 and parts[0] in ALLOWED_DIRS:
            folder = ALLOWED_DIRS[parts[0]]
            filename = unquote(parts[1])   # <--- decode %20, %5F etc
            if not SAFE_NAME.match(filename):
                self.send_error(404, "Invalid filename")
                return
            fp = folder / filename
            if fp.exists() and fp.is_file():
                content_type = "text/html" if fp.suffix == ".html" else "text/plain"
                self._send(200, fp.read_bytes(), content_type)
            else:
                self.send_error(404, f"{filename} not found in {parts[0]}")
            return

        # GET /employee_cv shortcut
        if path == "/employee_cv":
            first_name = self._get_first_name(query)
            if first_name is None:
                return
            fp = CV_DIR / f"cv_{first_name}.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, f"cv_{first_name}.html does not exist")
            return

        # GET /employee_id shortcut
        if path == "/employee_id":
            first_name = self._get_first_name(query)
            if first_name is None:
                return
            fp = ID_DIR / f"id_{first_name}.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, f"id_{first_name}.html does not exist")
            return

        # GET /internal_docs shortcut
        if path == "/internal_docs":
            fp = INTERNAL_DIR / "internal_docs.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, "internal_docs.html does not exist")
            return

        self.send_error(404, "Not Found")

    def do_POST(self):
        if self.path == "/employee_profile":
            self._send(200, "an employee profile was posted")
            return
        if self.path == "/employee_docs":
            self._send(200, "some employee docs were posted")
            return
        self.send_error(404, "Not Found")


if __name__ == "__main__":
    for d in ALLOWED_DIRS.values():
        d.mkdir(parents=True, exist_ok=True)
    server = HTTPServer(("127.0.0.1", 8000), Handler)
    print("Server running on http://127.0.0.1:8000")
    server.serve_forever()
