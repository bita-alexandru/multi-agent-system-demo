import re
import shutil
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs

BASE_DIR = Path(__file__).resolve().parent
EMPLOYEES_CVS_DIR = BASE_DIR / "DB" / "EMPLOYEES" / "CVS"
EMPLOYEES_IDS_DIR = BASE_DIR / "DB" / "EMPLOYEES" / "IDS"
EMPLOYEES_DOCS_DIR = BASE_DIR / "DB" / "EMPLOYEES" / "DOCS"
EMPLOYEES_PROFILES_DIR = BASE_DIR / "DB" / "EMPLOYEES" / "PROFILES"
INTERNAL_DOCS_DIR = BASE_DIR / "DB" / "INTERNAL" / "DOCS"


class Handler(BaseHTTPRequestHandler):
  def _send(self, code: int, body: str, content_type: str = "text/plain"):
    self.send_response(code)
    self.send_header("Content-Type", content_type)
    self.end_headers()
    if isinstance(body, str):
      body = body.encode("utf-8")
    self.wfile.write(body)

  def _get_secret(self, query):
    if "secret" not in query:
      self.send_error(404, "Missing required parameter: secret")
      return None
    return query["secret"][0]

  def do_GET(self):
    parsed = urlparse(self.path)
    path = parsed.path
    query = parse_qs(parsed.query)

    if path == "/employee/cv":
      secret = self._get_secret(query)
      if secret is None:
        return
      fp = EMPLOYEES_CVS_DIR / f"cv_{secret}.html"
      if fp.exists():
        self._send(200, fp.read_text("utf-8"), "text/html")
      else:
        self._send(404, f"cv_{secret}.html does not exist")
      return

    if path == "/employee/id":
      secret = self._get_secret(query)
      if secret is None:
        return
      fp = EMPLOYEES_IDS_DIR / f"id_{secret}.html"
      if fp.exists():
        self._send(200, fp.read_text("utf-8"), "text/html")
      else:
        self._send(404, f"id_{secret}.html does not exist")
      return

    if path == "/internal/docs":
      if not INTERNAL_DOCS_DIR.exists() or not INTERNAL_DOCS_DIR.is_dir():
        self.send_error(404, "internal docs directory not found")
        return
      files = sorted(INTERNAL_DOCS_DIR.glob("*.html"))
      if not files:
        self._send(404, "no internal docs found")
        return
      merged = "\n".join(fp.read_text("utf-8") for fp in files)
      self._send(200, merged, "text/html")
      return

    self.send_error(404, "Not Found")

  def do_POST(self):
    parsed = urlparse(self.path)
    path = parsed.path
    query = parse_qs(parsed.query)

    content_type = self.headers.get("Content-Type", "").lower()
    if content_type != "text/html":
      self.send_error(400, "Content-Type must be text/html")
      return

    content_length = int(self.headers.get("Content-Length", 0))
    body = self.rfile.read(content_length).decode("utf-8")

    if path == "/employee/profile":
      secret = self._get_secret(query)
      if secret is None:
        return

      EMPLOYEES_PROFILES_DIR.mkdir(parents=True, exist_ok=True)
      fp = EMPLOYEES_PROFILES_DIR / f"profile_{secret}.html"
      fp.write_text(body, encoding="utf-8")

      self._send(200, f"profile_{secret}.html has been saved")
      return

    if path == "/employee/docs":
      secret = self._get_secret(query)
      if secret is None:
        return

      match = re.search(r"<title>(.*?)</title>", body, re.IGNORECASE | re.DOTALL)
      if not match:
        self.send_error(400, "HTML body must contain a <title> tag")
        return
      title = match.group(1).strip().replace(" ", "_")
      if not title:
        self.send_error(400, "Empty <title> tag not allowed")
        return

      target_dir = EMPLOYEES_DOCS_DIR / f"docs_{secret}"
      target_dir.mkdir(parents=True, exist_ok=True)
      fp = target_dir / f"{title}.html"
      fp.write_text(body, encoding="utf-8")

      self._send(200, f"{title}.html has been saved under docs_{secret}")
      return

    self.send_error(404, "Not Found")

  def do_DELETE(self):
    parsed = urlparse(self.path)
    path = parsed.path
    query = parse_qs(parsed.query)

    if path == "/employee/profile":
      secret = self._get_secret(query)
      if secret is None:
        return

      fp = EMPLOYEES_PROFILES_DIR / f"profile_{secret}.html"
      if fp.exists() and fp.is_file():
        try:
          fp.unlink()
          self._send(200, f"profile_{secret} has been deleted")
        except Exception as e:
          self.send_error(500, f"Failed to delete: {e}")
      else:
        self._send(404, "Not Found")
      return

    if path == "/employee/docs":
      secret = self._get_secret(query)
      if secret is None:
        return

      dirpath = EMPLOYEES_DOCS_DIR / f"docs_{secret}"
      if dirpath.exists() and dirpath.is_dir():
        try:
          shutil.rmtree(dirpath)
          self._send(200, f"docs_{secret} has been deleted")
        except Exception as e:
          self.send_error(500, f"Failed to delete: {e}")
      else:
        self._send(404, "Not Found")
      return

    self._send(404, "Not Found")


if __name__ == "__main__":
  server = HTTPServer(("127.0.0.1", 8000), Handler)
  print("Server running on http://127.0.0.1:8000")
  server.serve_forever()
