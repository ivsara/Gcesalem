# GCE Salem Digital Notice Board — Corrected Web Edition

A responsive, mobile-friendly Digital Notice Board for Government College of Engineering, Salem. The **web application is the primary deliverable** and uses FastAPI, SQLAlchemy, SQLite, Jinja2, HTML, CSS and JavaScript. The former Android project is retained in `android/` as a legacy companion scaffold.

## Features

- Responsive public notice board for phones, tablets and desktops.
- Role-based Administrator, Faculty and Student accounts.
- Department-wise notices (CSE, IT, ECE, EEE, ME, CE), search and category filters.
- Notice priority, pinned notices, expiry dates and access-aware visibility.
- Create/delete notices in the web UI; create/update/delete through the JSON API.
- Multiple real file uploads per notice: PDF, JPG/JPEG, PNG, DOC/DOCX and XLS/XLSX, maximum 10 MB per file.
- Server-generated storage names, extension checks, protected attachment downloads, attachment metadata in SQLite.
- Automatic notice refresh every 15 seconds.
- Personal theme preferences: light/dark mode, accent/background colours and font family.
- PBKDF2-HMAC-SHA256 password hashing with per-password random salts, signed bearer tokens, session cookies and role checks.
- Admin-only user listing/creation API.

## Quick start

Python 3.10+ is recommended.

```bash
cd webapp
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload
```

Open `http://127.0.0.1:8000`. The database and uploaded files are created locally in `webapp/`.

### Demo accounts

| Role | Login ID | Password |
|---|---|---|
| Administrator | `admin` | `admin123` |
| Faculty (CSE) | `faculty_cse` | `faculty123` |
| Student (CSE) | `student_cse` | `student123` |

These are development-only demo credentials. Change them before any real deployment and deactivate/remove demo users. This project seeds these accounts only when the database is empty of that login ID.

## Production configuration

1. Set `APP_ENV=production` and a unique, long random `SECRET_KEY`.
2. Set `COOKIE_SECURE=1` when served behind HTTPS.
3. Use HTTPS, a production ASGI server/reverse proxy, backups, and file-storage limits.
4. Replace demo credentials, configure real college users, and review privacy/retention policies.
5. For larger deployments, use a managed database and private object storage. Uploaded file extension checks are not a substitute for antivirus/content scanning.

## JSON API

- `POST /api/auth/login` — JSON `{ "login_id": "admin", "password": "..." }`; returns a bearer token.
- `GET /api/auth/me` — current user.
- `GET /api/departments`
- `GET /api/notices?department=CSE&category=ALL&q=exam`
- `POST /api/notices` — authenticated Administrator/Faculty.
- `PUT /api/notices/{id}` — edit a notice (authoring rules enforced).
- `DELETE /api/notices/{id}` — delete a notice and stored attachments.
- `POST /api/notices/{id}/attachments` — multipart form with one or more `files` fields.
- `GET /attachments/{id}` — access-controlled attachment download.
- `GET /api/users`, `POST /api/users` — Administrator only.
- `GET /health`

Example attachment upload with a bearer token:

```bash
curl -X POST http://127.0.0.1:8000/api/notices/1/attachments \\
  -H 'Authorization: Bearer YOUR_TOKEN' \\
  -F 'files=@circular.pdf'
```

## Testing

```bash
cd webapp
pytest -q
```

Tests cover the health/home routes, login token, role enforcement, department restrictions and unauthenticated upload rejection. The suite is a baseline; run additional end-to-end tests before deployment.

## Android project

The previous Kotlin/Jetpack Compose source is preserved under `android/app/`. It is not the primary frontend in this release; use the responsive web application above for the complete workflow. The Android manifest includes network permission, and `android/app/src/main/java/com/example/data/remote/NoticeBoardApi.kt` provides Retrofit contracts for the same API and multipart uploads. A full Android Gradle build could not be verified in this environment because the Gradle wrapper JAR/scripts and Android SDK are not available here. See `android/ANDROID_STATUS.md` for the exact scope and local build steps.
