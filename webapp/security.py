import base64, hashlib, hmac, json, os, secrets, time
from typing import Optional

ROUNDS = 310_000

def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, ROUNDS)
    return "pbkdf2_sha256${}${}${}".format(ROUNDS, base64.urlsafe_b64encode(salt).decode(), base64.urlsafe_b64encode(digest).decode())

def verify_password(password: str, stored: str) -> bool:
    try:
        algorithm, rounds, salt_b64, digest_b64 = stored.split("$", 3)
        if algorithm != "pbkdf2_sha256": return False
        salt = base64.urlsafe_b64decode(salt_b64.encode())
        expected = base64.urlsafe_b64decode(digest_b64.encode())
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, int(rounds))
        return hmac.compare_digest(actual, expected)
    except (ValueError, TypeError):
        return False

def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

def create_token(login_id: str, secret: str, ttl: int = 86400) -> str:
    payload = _b64(json.dumps({"sub": login_id, "exp": int(time.time()) + ttl}, separators=(",", ":")).encode())
    signature = _b64(hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest())
    return f"{payload}.{signature}"

def verify_token(token: str, secret: str) -> Optional[str]:
    try:
        payload, signature = token.split(".", 1)
        expected = _b64(hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(signature, expected): return None
        data = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        if int(data.get("exp", 0)) < int(time.time()): return None
        return data.get("sub")
    except (ValueError, TypeError, json.JSONDecodeError):
        return None
