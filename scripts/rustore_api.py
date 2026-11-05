"""Minimal RuStore API transport with fixed hosts, redacted errors and no retries."""
import base64
from datetime import datetime, timezone
import json
import re
import urllib.error
import urllib.request
import uuid

import build_release as release

META_HOST = "https://public-api-m.rustore.ru"
UPLOAD_HOST = "https://public-api.rustore.ru"
APP_PATH = "/public/v1/application/" + release.PACKAGE + "/version"


class ApiError(Exception):
    """Never includes response bodies, credentials or signed authentication payloads."""
    def __init__(self, message, *, definitive=False, phase="api", http_status=None, reason=None):
        super().__init__(message)
        self.definitive = definitive
        self.phase = phase if phase in {"authentication", "metadata", "upload"} else "api"
        self.http_status = http_status if type(http_status) is int and 100 <= http_status <= 599 else None
        self.reason = reason if reason in {"http-error", "transport-error", "invalid-response", "server-rejected", "redirect-blocked"} else None

    def diagnostic(self):
        """Expose only fixed context fields, never an exception or server message."""
        details = ([f"HTTP {self.http_status}"] if self.http_status is not None else [])
        if self.reason:
            details.append(self.reason)
        suffix = " (" + "; ".join(details) + ")" if details else ""
        return f"RuStore {self.phase} request failed{suffix}."


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if fp is not None:
            fp.close()
        raise ApiError("RuStore redirects are forbidden", http_status=code, reason="redirect-blocked")


def rsa_sign(key_bytes, message):
    from Crypto.Hash import SHA512
    from Crypto.PublicKey import RSA
    from Crypto.Signature import pkcs1_15
    key = RSA.import_key(key_bytes)
    if not key.has_private() or key.size_in_bits() < 2048:
        raise ValueError("A private RSA key of at least 2048 bits is required")
    return pkcs1_15.new(key).sign(SHA512.new(message))


def auth_payload(key_id, private_base64, *, timestamp=None):
    if not re.fullmatch(r"[A-Za-z0-9-]{1,128}", key_id) or not 0 < len(private_base64) < 65536:
        raise ValueError("RuStore API key ID and private key are required")
    key = base64.b64decode(private_base64, validate=True)
    timestamp = timestamp or datetime.now(timezone.utc).isoformat(timespec="milliseconds")
    signature = rsa_sign(key, (key_id + timestamp).encode("utf-8"))
    return dict(keyId=key_id, timestamp=timestamp, signature=base64.b64encode(signature).decode("ascii"))


def version_path(version_id):
    if type(version_id) is not int or version_id <= 0:
        raise ValueError("Invalid RuStore version ID")
    return APP_PATH + "/" + str(version_id)


class Client:
    def __init__(self):
        self._opener = urllib.request.build_opener(NoRedirect())
        self._token = None

    def request(self, method, path, *, payload=None, multipart=None, upload=False, auth=False):
        if not path.startswith("/public/") or ".." in path or method not in ("GET", "POST"):
            raise ValueError("Unsupported RuStore request")
        phase = "authentication" if auth else "upload" if upload else "metadata"
        headers = {"Accept": "application/json"}
        if not auth:
            if not self._token:
                raise ApiError("RuStore authentication required")
            headers["Public-Token"] = self._token
        body = None
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if multipart is not None:
            body, headers["Content-Type"] = multipart
        request = urllib.request.Request((UPLOAD_HOST if upload else META_HOST) + path,
                                         data=body, headers=headers, method=method)
        status = None
        try:
            with self._opener.open(request, timeout=90) as response:
                status = getattr(response, "status", None)
                raw = response.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024:
                raise ApiError("RuStore response exceeded limit", phase=phase, http_status=status, reason="invalid-response")
            data = json.loads(raw)
            if not isinstance(data, dict) or data.get("code") != "OK":
                raise ApiError("RuStore rejected the request",
                               phase=phase, http_status=status, reason="server-rejected",
                               definitive=isinstance(data, dict) and str(data.get("code")).lower() == "error")
            return data.get("body")
        except urllib.error.HTTPError as error:
            code = error.code
            error.close()
            raise ApiError("RuStore HTTP request failed",
                           phase=phase, http_status=code, reason="http-error",
                           definitive=code in (400, 401, 403, 404, 405, 413, 415, 422)) from None
        except OSError:
            raise ApiError("RuStore request failed or its result is uncertain", phase=phase, reason="transport-error") from None
        except (ValueError, KeyError, TypeError):
            raise ApiError("RuStore response could not be read", phase=phase, http_status=status, reason="invalid-response") from None

    def authenticate(self, key_id, private_base64):
        body = self.request("POST", "/public/auth/", payload=auth_payload(key_id, private_base64), auth=True)
        if (not isinstance(body, dict) or not isinstance(body.get("jwe"), str)
                or not re.fullmatch(r"[A-Za-z0-9_.-]{1,16384}", body["jwe"])):
            raise ApiError("Invalid RuStore authentication response", phase="authentication", reason="invalid-response")
        self._token = body["jwe"]

    def versions(self):
        result = []
        for page in range(100):
            body = self.request("GET", APP_PATH + f"?page={page}&size=100&filterTestingType=ALL")
            if (not isinstance(body, dict) or not isinstance(body.get("content"), list)
                    or body.get("pageNumber") != page or type(body.get("totalPages")) is not int):
                raise ApiError("Invalid RuStore version listing")
            result.extend(body["content"])
            if page + 1 >= body["totalPages"]:
                return result
        raise ApiError("RuStore version pagination exceeded limit")

    def detail(self, version_id):
        path = version_path(version_id).replace("/public/v1/", "/public/v2/", 1)
        body = self.request("GET", path)
        if not isinstance(body, dict) or body.get("versionId") != version_id:
            raise ApiError("RuStore version identity mismatch")
        return body

    def create(self, payload):
        body = self.request("POST", APP_PATH, payload=payload)
        version_id = body.get("versionId") if isinstance(body, dict) else body
        if type(version_id) is not int or version_id <= 0:
            raise ApiError("Draft creation result is uncertain")
        return version_id

    def upload(self, version_id, apk):
        boundary = "antcalc-" + uuid.uuid4().hex
        prefix = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"antcalc.apk\"\r\n"
                  "Content-Type: application/vnd.android.package-archive\r\n\r\n").encode()
        data = prefix + apk.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        return self.request("POST", version_path(version_id) + "/apk?servicesType=Unknown&isMainApk=true",
                            multipart=(data, "multipart/form-data; boundary=" + boundary), upload=True)

    def submit(self, version_id):
        return self.request("POST", version_path(version_id) + "/commit?priorityUpdate=0", upload=True)

    def publish(self, version_id):
        return self.request("POST", version_path(version_id) + "/publish")
