from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs, unquote
from pathlib import Path
import re

CV_DIR = Path("./DB/EMPLOYEES/CVS")
ID_DIR = Path("./DB/EMPLOYEES/IDS")
DOCS_DIR = Path("./DB/EMPLOYEES/DOCS")
INTERNAL_DIR = Path("./DB/INTERNAL/DOCS")

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
        return first_name

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        if path == "/employee/cv":
            first_name = self._get_first_name(query)
            if first_name is None:
                return
            fp = CV_DIR / f"cv_{first_name}.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, f"cv_{first_name}.html does not exist")
            return

        if path == "/employee/id":
            first_name = self._get_first_name(query)
            if first_name is None:
                return
            fp = ID_DIR / f"id_{first_name}.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, f"id_{first_name}.html does not exist")
            return

        if path == "/internal/docs":
            fp = INTERNAL_DIR / "internal_docs.html"
            if fp.exists():
                self._send(200, fp.read_text("utf-8"), "text/html")
            else:
                self._send(404, "internal_docs.html does not exist")
            return

        self.send_error(404, "Not Found")

    def do_POST(self):
        if self.path == "/employee/profile":
            self._send(200, "an employee profile was posted")
            return
        if self.path == "/employee/docs":
            self._send(200, "some employee docs were posted")
            return
        self.send_error(404, "Not Found")


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 8000), Handler)
    print("Server running on http://127.0.0.1:8000")
    server.serve_forever()
