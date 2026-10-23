import os, re, uuid, mimetypes
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional, List
from fastapi import FastAPI, Request, Depends, HTTPException, Form, UploadFile, File, status
from fastapi.responses import HTMLResponse, RedirectResponse, FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from starlette.middleware.sessions import SessionMiddleware
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import or_
from database import Base, engine, get_db, SessionLocal
from models import Department, User, Notice, Attachment, UserPreference
from security import hash_password, verify_password, create_token, verify_token

def utcnow():
    return datetime.now(timezone.utc).replace(tzinfo=None)

ROOT = Path(__file__).resolve().parent
UPLOAD_DIR = ROOT / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
MAX_FILE_SIZE = 10 * 1024 * 1024
ALLOWED_EXTENSIONS = {".pdf", ".jpg", ".jpeg", ".png", ".webp", ".doc", ".docx", ".xls", ".xlsx", ".csv", ".txt"}
ALLOWED_ROLES = {"Administrator", "HOD", "Faculty", "Student"}

def valid_file_signature(ext: str, data: bytes) -> bool:
    """Validate uploaded attachment signature while supporting modern PDF, RTF/OpenXML, CSV/TXT, and WebP formats."""
    if not data:
        return False
    if ext == ".pdf":
        return b"%PDF-" in data[:1024]
    if ext in {".jpg", ".jpeg"}:
        return data.startswith(b"\xff\xd8\xff")
    if ext == ".png":
        return data.startswith(b"\x89PNG\r\n\x1a\n")
    if ext == ".webp":
        return data.startswith(b"RIFF") and b"WEBP" in data[:16]
    if ext in {".doc", ".xls"}:
        return (
            data.startswith(b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1")
            or data.startswith(b"{\\rtf")
            or data.startswith(b"PK\x03\x04")
            or b"\x00" not in data[:512]
        )
    if ext in {".docx", ".xlsx"}:
        return data.startswith(b"PK\x03\x04")
    if ext in {".csv", ".txt"}:
        return len(data) > 0
    return False
SECRET_KEY = os.getenv("SECRET_KEY", "dev-only-change-this-secret-before-deployment")
if os.getenv("APP_ENV", "development").lower() == "production" and SECRET_KEY == "dev-only-change-this-secret-before-deployment":
    raise RuntimeError("Set a strong SECRET_KEY environment variable for production")

app = FastAPI(title="GCE Salem Digital Notice Board", version="2.0.0")
app.add_middleware(SessionMiddleware, secret_key=SECRET_KEY, session_cookie="gce_notice_session", same_site="lax", https_only=os.getenv("COOKIE_SECURE", "0") == "1", max_age=86400)
app.mount("/static", StaticFiles(directory=ROOT / "static"), name="static")
templates = Jinja2Templates(directory=str(ROOT / "templates"))
Base.metadata.create_all(bind=engine)

DEPARTMENTS = [
    ("Civil Engineering", "CIVIL"),
    ("Mechanical Engineering", "MECH"),
    ("Electronics and Communication Engineering", "ECE"),
    ("Electrical and Electronics Engineering", "EEE"),
    ("Computer Science and Engineering", "CSE"),
    ("Metallurgical Engineering", "METT"),
    ("Artificial Intelligence and Machine Learning", "AIML"),
]

def seed_data():
    db = SessionLocal()
    try:
        for name, code in DEPARTMENTS:
            if not db.query(Department).filter_by(code=code).first():
                db.add(Department(name=name, code=code, description=f"{name} department"))
        db.commit()
        depts = {d.code: d for d in db.query(Department).all()}
        demo_users = [
            ("principal", "principal123", "Admin Principal", "Administrator", None, "principal@gcesalem.edu.in"),
            ("admin", "admin123", "Admin Principal", "Administrator", None, "admin@gcesalem.edu.in"),
        ]
        for _, code in DEPARTMENTS:
            d_low = code.lower()
            d_id = depts[code].id
            demo_users.append((f"hod_{d_low}", "hod123", f"HOD - {code}", "HOD", d_id, f"hod.{d_low}@gcesalem.edu.in"))
            for i in range(1, 4):
                demo_users.append((f"faculty_{d_low}{i}", "faculty123", f"Faculty {i} - {code}", "Faculty", d_id, f"faculty{i}.{d_low}@gcesalem.edu.in"))
            for s in range(1, 11):
                demo_users.append((f"student_{d_low}{s}", "student123", f"Student {s} - {code}", "Student", d_id, f"student{s}.{d_low}@student.gcesalem.edu.in"))
            demo_users.append((f"faculty_{d_low}", "faculty123", f"Faculty 1 - {code}", "Faculty", d_id, f"faculty.{d_low}@gcesalem.edu.in"))
            demo_users.append((f"student_{d_low}", "student123", f"Student 1 - {code}", "Student", d_id, f"student.{d_low}@student.gcesalem.edu.in"))

        for login, password, full, role, dept_id, email in demo_users:
            if not db.query(User).filter_by(login_id=login).first():
                db.add(User(login_id=login, password_hash=hash_password(password), full_name=full, role=role, department_id=dept_id, email=email, is_active=True))
        db.commit()
        if db.query(Notice).count() == 0:
            admin = db.query(User).filter_by(login_id="principal").first() or db.query(User).filter_by(login_id="admin").first()
            db.add(Notice(title="Welcome to the GCE Salem Digital Notice Board", description="Official college announcements, examination updates, placement opportunities and department circulars are published here. Sign in to access role-specific features.", category="General", priority="HIGH", author_id=admin.id, is_pinned=True, visibility="PUBLIC"))
            db.add(Notice(title="End Semester Examination Updates", description="Students should check official department communications and the Controller of Examinations announcements regularly.", category="Examination", priority="MEDIUM", author_id=admin.id, is_pinned=False, visibility="PUBLIC"))
            db.commit()
    finally:
        db.close()

@app.on_event("startup")
def startup():
    seed_data()

def current_user(request: Request, db: Session) -> Optional[User]:
    login_id = request.session.get("login_id")
    authorization = request.headers.get("authorization", "")
    if authorization.lower().startswith("bearer "):
        login_id = verify_token(authorization.split(" ", 1)[1].strip(), SECRET_KEY)
    if not login_id:
        return None
    user = db.query(User).options(joinedload(User.department), joinedload(User.preferences)).filter_by(login_id=login_id, is_active=True).first()
    return user

def require_user(request: Request, db: Session) -> User:
    user = current_user(request, db)
    if not user:
        raise HTTPException(status_code=401, detail="Please sign in")
    return user

def require_roles(user: User, *roles: str):
    if user.role not in roles:
        raise HTTPException(status_code=403, detail="You do not have permission to perform this action")

def csrf_ok(request: Request, token: str):
    if not token or not request.session.get("csrf_token") or not __import__("hmac").compare_digest(token, request.session["csrf_token"]):
        raise HTTPException(status_code=403, detail="Security token expired. Refresh the page and try again.")

def visible_notices_query(db: Session, user: Optional[User]):
    now = utcnow()
    query = db.query(Notice).options(joinedload(Notice.department), joinedload(Notice.author), joinedload(Notice.attachments)).filter(Notice.status == "PUBLISHED", or_(Notice.expires_at.is_(None), Notice.expires_at > now))
    if not user:
        query = query.filter(Notice.visibility == "PUBLIC")
    elif user.role == "Student":
        query = query.filter(or_(Notice.visibility == "PUBLIC", Notice.department_id.is_(None), Notice.department_id == user.department_id))
    elif user.role in {"Faculty", "HOD"}:
        query = query.filter(or_(Notice.visibility == "PUBLIC", Notice.department_id.is_(None), Notice.department_id == user.department_id))
    return query

def notice_dict(n: Notice):
    return {"id": n.id, "title": n.title, "description": n.description, "category": n.category, "priority": n.priority, "department_id": n.department_id, "department": n.department.code if n.department else "College-wide", "department_name": n.department.name if n.department else "College-wide", "author": n.author.full_name if n.author else "Admin Principal", "published_at": n.published_at.isoformat() if n.published_at else None, "expires_at": n.expires_at.isoformat() if n.expires_at else None, "is_pinned": n.is_pinned, "visibility": n.visibility, "attachments": [{"id": a.id, "filename": a.original_filename, "size": a.file_size, "content_type": a.content_type, "url": f"/attachments/{a.id}"} for a in n.attachments]}

@app.get("/health")
def health():
    return {"status": "ok", "service": "GCE Salem Digital Notice Board"}

@app.get("/", response_class=HTMLResponse)
def home(request: Request, db: Session = Depends(get_db), department: str = "ALL", category: str = "ALL", q: str = ""):
    user = current_user(request, db)
    if "csrf_token" not in request.session:
        import secrets
        request.session["csrf_token"] = secrets.token_urlsafe(32)
    query = visible_notices_query(db, user)
    if department != "ALL":
        dept = db.query(Department).filter_by(code=department, is_active=True).first()
        if dept: query = query.filter(or_(Notice.department_id == dept.id, Notice.department_id.is_(None)))
    if category != "ALL": query = query.filter(Notice.category == category)
    if q.strip():
        term = f"%{q.strip()}%"
        query = query.filter(or_(Notice.title.ilike(term), Notice.description.ilike(term)))
    notices = query.order_by(Notice.is_pinned.desc(), Notice.published_at.desc()).all()
    departments = db.query(Department).filter_by(is_active=True).order_by(Department.name).all()
    return templates.TemplateResponse(request=request, name="index.html", context={"user": user, "notices": notices, "departments": departments, "department": department, "category": category, "q": q, "csrf_token": request.session["csrf_token"], "categories": ["General", "Examination", "Events", "Placements", "Academic", "Circular", "Sports", "Other"], "now": utcnow()})

@app.get("/login", response_class=HTMLResponse)
def login_page(request: Request, mode: str = "signin"):
    import secrets
    if "csrf_token" not in request.session:
        request.session["csrf_token"] = secrets.token_urlsafe(32)
    captcha_code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(6))
    request.session["captcha_code"] = captcha_code
    return templates.TemplateResponse(
        request=request,
        name="login.html",
        context={
            "request": request,
            "csrf_token": request.session["csrf_token"],
            "error": None,
            "success": None,
            "mode": mode,
            "captcha_code": captcha_code,
            "prefill_login_id": "",
        },
    )

@app.post("/login")
def login_form(
    request: Request,
    login_id: str = Form(...),
    password: str = Form(...),
    csrf_token: str = Form(...),
    captcha_input: str = Form(""),
    captcha_expected: str = Form(""),
    db: Session = Depends(get_db),
):
    csrf_ok(request, csrf_token)
    expected = (captcha_expected or request.session.get("captcha_code") or "").strip().upper()
    entered = captcha_input.strip().upper()
    if expected and entered and entered != expected:
        import secrets
        new_code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(6))
        request.session["captcha_code"] = new_code
        return templates.TemplateResponse(
            request=request,
            name="login.html",
            context={
                "request": request,
                "csrf_token": request.session["csrf_token"],
                "error": "Incorrect CAPTCHA code. Please enter the new security code shown.",
                "success": None,
                "captcha_code": new_code,
                "prefill_login_id": login_id.strip(),
            },
            status_code=400,
        )
    user = db.query(User).filter_by(login_id=login_id.strip(), is_active=True).first()
    if not user or not verify_password(password, user.password_hash):
        import secrets
        new_code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(6))
        request.session["captcha_code"] = new_code
        return templates.TemplateResponse(
            request=request,
            name="login.html",
            context={
                "request": request,
                "csrf_token": request.session["csrf_token"],
                "error": "Invalid login ID or password.",
                "success": None,
                "captcha_code": new_code,
                "prefill_login_id": login_id.strip(),
            },
            status_code=400,
        )
    request.session.clear()
    import secrets
    request.session["login_id"] = user.login_id
    request.session["csrf_token"] = secrets.token_urlsafe(32)
    return RedirectResponse("/", status_code=303)

@app.post("/logout")
def logout(request: Request, csrf_token: str = Form(...)):
    csrf_ok(request, csrf_token)
    request.session.clear()
    return RedirectResponse("/", status_code=303)

@app.post("/notices/create")
async def create_notice_form(request: Request, title: str = Form(...), description: str = Form(...), department_code: str = Form("ALL"), category: str = Form("General"), priority: str = Form("MEDIUM"), visibility: str = Form("PUBLIC"), expires_at: str = Form(""), is_pinned: Optional[str] = Form(None), csrf_token: str = Form(...), files: List[UploadFile] = File(default=[]), db: Session = Depends(get_db)):
    csrf_ok(request, csrf_token)
    user = require_user(request, db)
    require_roles(user, "Administrator", "HOD", "Faculty")
    title = title.strip(); description = description.strip()
    if not title or len(title) > 200 or not description:
        raise HTTPException(status_code=400, detail="Title and description are required; title must be under 200 characters")
    department_id = None
    if department_code != "ALL":
        dept = db.query(Department).filter_by(code=department_code, is_active=True).first()
        if not dept: raise HTTPException(status_code=400, detail="Select a valid department")
        department_id = dept.id
    if user.role in {"Faculty", "HOD"} and department_id != user.department_id:
        raise HTTPException(status_code=403, detail="HOD and Faculty may publish only to their assigned department")
    if priority not in {"LOW", "MEDIUM", "HIGH"}: priority = "MEDIUM"
    if category not in {"General", "Examination", "Events", "Placements", "Academic", "Circular", "Sports", "Other"}: category = "General"
    if visibility not in {"PUBLIC", "RESTRICTED"}: visibility = "PUBLIC"
    if user.role in {"Faculty", "HOD"}: visibility = "RESTRICTED" if department_id else "PUBLIC"
    expiry = None
    if expires_at:
        try: expiry = datetime.fromisoformat(expires_at)
        except ValueError: raise HTTPException(status_code=400, detail="Invalid expiry date")
        if expiry <= utcnow(): raise HTTPException(status_code=400, detail="Expiry must be in the future")
    notice = Notice(title=title, description=description, department_id=department_id, category=category, priority=priority, author_id=user.id, expires_at=expiry, is_pinned=bool(is_pinned) and user.role == "Administrator", visibility=visibility)
    db.add(notice); db.flush()
    saved_files = []
    try:
        for upload in files:
            if not upload.filename: continue
            original = Path(upload.filename.replace("\\", "/")).name
            ext = Path(original).suffix.lower()
            if ext not in ALLOWED_EXTENSIONS: raise HTTPException(status_code=400, detail=f"Unsupported file type: {original}")
            data = await upload.read(MAX_FILE_SIZE + 1)
            if len(data) > MAX_FILE_SIZE: raise HTTPException(status_code=413, detail=f"{original} exceeds the 10 MB limit")
            if not data: continue
            if not valid_file_signature(ext, data): raise HTTPException(status_code=400, detail=f"File content does not match its extension: {original}")
            stored = f"{uuid.uuid4().hex}{ext}"
            (UPLOAD_DIR / stored).write_bytes(data); saved_files.append(stored)
            content_type = mimetypes.guess_type(original)[0] or "application/octet-stream"
            db.add(Attachment(notice_id=notice.id, original_filename=original[:255], stored_filename=stored, content_type=content_type, file_size=len(data), uploaded_by=user.login_id))
        db.commit()
    except Exception:
        db.rollback()
        for stored in saved_files: (UPLOAD_DIR / stored).unlink(missing_ok=True)
        raise
    return RedirectResponse("/?created=1", status_code=303)

@app.post("/notices/{notice_id}/delete")
def delete_notice_form(notice_id: int, request: Request, csrf_token: str = Form(...), db: Session = Depends(get_db)):
    csrf_ok(request, csrf_token)
    user = require_user(request, db)
    require_roles(user, "Administrator", "HOD", "Faculty")
    notice = db.query(Notice).options(joinedload(Notice.attachments)).filter_by(id=notice_id).first()
    if not notice: raise HTTPException(status_code=404, detail="Notice not found")
    if user.role == "Faculty" and notice.author_id != user.id: raise HTTPException(status_code=403, detail="Faculty can delete only their own notices")
    if user.role == "HOD" and notice.department_id != user.department_id and notice.author_id != user.id: raise HTTPException(status_code=403, detail="HOD can delete only notices in their department")
    for a in notice.attachments: (UPLOAD_DIR / a.stored_filename).unlink(missing_ok=True)
    db.delete(notice); db.commit()
    return RedirectResponse("/?deleted=1", status_code=303)

@app.get("/attachments/{attachment_id}")
def download_attachment(attachment_id: int, request: Request, inline: int = 0, db: Session = Depends(get_db)):
    user = current_user(request, db)
    attachment = db.query(Attachment).options(joinedload(Attachment.notice)).filter_by(id=attachment_id).first()
    if not attachment: raise HTTPException(status_code=404, detail="Attachment not found")
    notice = db.query(Notice).filter_by(id=attachment.notice_id).first()
    if not notice: raise HTTPException(status_code=404, detail="Notice not found")
    if notice.status != "PUBLISHED" or (notice.expires_at and notice.expires_at <= utcnow()): raise HTTPException(status_code=404, detail="This notice is no longer available")
    if notice.visibility != "PUBLIC":
        if not user: raise HTTPException(status_code=401, detail="Sign in to download this attachment")
        if user.role == "Student" and notice.department_id not in (None, user.department_id): raise HTTPException(status_code=403, detail="Not available for your department")
        if user.role in {"Faculty", "HOD"} and notice.department_id not in (None, user.department_id) and notice.author_id != user.id: raise HTTPException(status_code=403, detail="Not permitted")
    path = UPLOAD_DIR / attachment.stored_filename
    if not path.is_file(): raise HTTPException(status_code=404, detail="File is missing from storage")
    disposition = "inline" if inline == 1 else "attachment"
    return FileResponse(path, media_type=attachment.content_type, filename=attachment.original_filename, content_disposition_type=disposition, headers={"X-Content-Type-Options": "nosniff"})

@app.post("/settings/theme")
def save_theme(request: Request, accent_colour: str = Form("#155eef"), background_colour: str = Form("#f5f7fb"), font_family: str = Form("Inter"), theme_mode: str = Form("light"), csrf_token: str = Form(...), db: Session = Depends(get_db)):
    csrf_ok(request, csrf_token)
    user = require_user(request, db)
    def valid_colour(value): return bool(re.fullmatch(r"#[0-9a-fA-F]{6}", value))
    if not valid_colour(accent_colour) or not valid_colour(background_colour): raise HTTPException(status_code=400, detail="Invalid colour")
    if font_family not in {"Inter", "Arial", "Georgia", "Verdana", "system-ui"}: font_family = "Inter"
    if theme_mode not in {"light", "dark"}: theme_mode = "light"
    pref = db.query(UserPreference).filter_by(user_id=user.id).first()
    if not pref: pref = UserPreference(user_id=user.id); db.add(pref)
    pref.accent_colour = accent_colour; pref.background_colour = background_colour; pref.font_family = font_family; pref.theme_mode = theme_mode
    db.commit()
    return RedirectResponse("/", status_code=303)

# JSON API used by the responsive frontend and any future mobile client.
@app.post("/api/auth/login")
def api_login(payload: dict, db: Session = Depends(get_db)):
    login_id = str(payload.get("login_id", "")).strip(); password = str(payload.get("password", ""))
    user = db.query(User).filter_by(login_id=login_id, is_active=True).first()
    if not user or not verify_password(password, user.password_hash): raise HTTPException(status_code=401, detail="Invalid login credentials")
    return {"access_token": create_token(user.login_id, SECRET_KEY), "token_type": "bearer", "user": {"login_id": user.login_id, "full_name": user.full_name, "role": user.role, "department": user.department.code if user.department else None}}

@app.get("/api/auth/me")
def api_me(request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db)
    return {"login_id": user.login_id, "full_name": user.full_name, "role": user.role, "department": user.department.code if user.department else None}

@app.get("/api/departments")
def api_departments(db: Session = Depends(get_db)):
    return [{"id": d.id, "name": d.name, "code": d.code} for d in db.query(Department).filter_by(is_active=True).order_by(Department.name).all()]

@app.get("/api/notices")
def api_notices(request: Request, department: str = "ALL", category: str = "ALL", q: str = "", since: Optional[str] = None, db: Session = Depends(get_db)):
    user = current_user(request, db)
    query = visible_notices_query(db, user)
    if department != "ALL":
        dept = db.query(Department).filter_by(code=department, is_active=True).first()
        if dept: query = query.filter(or_(Notice.department_id == dept.id, Notice.department_id.is_(None)))
    if category != "ALL": query = query.filter(Notice.category == category)
    if q.strip():
        term = f"%{q.strip()}%"; query = query.filter(or_(Notice.title.ilike(term), Notice.description.ilike(term)))
    notices = query.order_by(Notice.is_pinned.desc(), Notice.published_at.desc()).all()
    return {"notices": [notice_dict(n) for n in notices], "server_time": utcnow().isoformat()}

@app.post("/api/notices")
def api_create_notice(payload: dict, request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator", "HOD", "Faculty")
    title = str(payload.get("title", "")).strip(); description = str(payload.get("description", "")).strip()
    if not title or len(title) > 200 or not description: raise HTTPException(status_code=400, detail="Title and description are required")
    dept_code = payload.get("department", "ALL"); dept = None
    if dept_code not in (None, "", "ALL"):
        dept = db.query(Department).filter_by(code=dept_code, is_active=True).first()
        if not dept: raise HTTPException(status_code=400, detail="Invalid department")
    if user.role in {"Faculty", "HOD"} and (not dept or dept.id != user.department_id): raise HTTPException(status_code=403, detail="HOD and Faculty may publish only to their assigned department")
    expiry = None
    if payload.get("expires_at"):
        try: expiry = datetime.fromisoformat(payload["expires_at"])
        except (ValueError, TypeError): raise HTTPException(status_code=400, detail="Invalid expiry date")
        if expiry <= utcnow(): raise HTTPException(status_code=400, detail="Expiry must be in the future")
    priority = payload.get("priority", "MEDIUM"); category = payload.get("category", "General")
    if priority not in {"LOW", "MEDIUM", "HIGH"}: raise HTTPException(status_code=400, detail="Invalid priority")
    allowed_categories = {"General", "Examination", "Events", "Placements", "Academic", "Circular", "Sports", "Other"}
    if category not in allowed_categories: raise HTTPException(status_code=400, detail="Invalid category")
    visibility = payload.get("visibility", "PUBLIC") if user.role == "Administrator" else ("RESTRICTED" if dept else "PUBLIC")
    if visibility not in {"PUBLIC", "RESTRICTED"}: raise HTTPException(status_code=400, detail="Invalid visibility")
    n = Notice(title=title, description=description, department_id=dept.id if dept else None, category=category, priority=priority, author_id=user.id, expires_at=expiry, is_pinned=bool(payload.get("is_pinned")) and user.role == "Administrator", visibility=visibility)
    db.add(n); db.commit(); db.refresh(n)
    n = db.query(Notice).options(joinedload(Notice.department), joinedload(Notice.author), joinedload(Notice.attachments)).filter_by(id=n.id).first()
    return notice_dict(n)

@app.put("/api/notices/{notice_id}")
def api_update_notice(notice_id: int, payload: dict, request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator", "HOD", "Faculty")
    n = db.query(Notice).filter_by(id=notice_id).first()
    if not n: raise HTTPException(status_code=404, detail="Notice not found")
    if user.role == "Faculty" and n.author_id != user.id: raise HTTPException(status_code=403, detail="Faculty may edit only their own notices")
    if user.role == "HOD" and n.department_id != user.department_id and n.author_id != user.id: raise HTTPException(status_code=403, detail="HOD may edit only their department's notices")
    if "title" in payload and (not str(payload["title"]).strip() or len(str(payload["title"]).strip()) > 200): raise HTTPException(status_code=400, detail="Title is required and must be under 200 characters")
    if "description" in payload and not str(payload["description"]).strip(): raise HTTPException(status_code=400, detail="Description cannot be empty")
    if "category" in payload and payload["category"] not in {"General", "Examination", "Events", "Placements", "Academic", "Circular", "Sports", "Other"}: raise HTTPException(status_code=400, detail="Invalid category")
    if "priority" in payload and payload["priority"] not in {"LOW", "MEDIUM", "HIGH"}: raise HTTPException(status_code=400, detail="Invalid priority")
    for field in ("title", "description", "category", "priority"):
        if field in payload: setattr(n, field, str(payload[field]).strip())
    if user.role == "Administrator" and "is_pinned" in payload: n.is_pinned = bool(payload["is_pinned"])
    if user.role == "Administrator" and "visibility" in payload and payload["visibility"] in {"PUBLIC", "RESTRICTED"}: n.visibility = payload["visibility"]
    n.updated_at = utcnow(); db.commit()
    n = db.query(Notice).options(joinedload(Notice.department), joinedload(Notice.author), joinedload(Notice.attachments)).filter_by(id=n.id).first()
    return notice_dict(n)

@app.delete("/api/notices/{notice_id}")
def api_delete_notice(notice_id: int, request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator", "HOD", "Faculty")
    n = db.query(Notice).options(joinedload(Notice.attachments)).filter_by(id=notice_id).first()
    if not n: raise HTTPException(status_code=404, detail="Notice not found")
    if user.role == "Faculty" and n.author_id != user.id: raise HTTPException(status_code=403, detail="Faculty may delete only their own notices")
    if user.role == "HOD" and n.department_id != user.department_id and n.author_id != user.id: raise HTTPException(status_code=403, detail="HOD may delete only their department's notices")
    for a in n.attachments: (UPLOAD_DIR / a.stored_filename).unlink(missing_ok=True)
    db.delete(n); db.commit(); return {"ok": True}

@app.post("/api/notices/{notice_id}/attachments")
async def api_upload_attachments(notice_id: int, request: Request, files: List[UploadFile] = File(...), db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator", "HOD", "Faculty")
    notice = db.query(Notice).filter_by(id=notice_id).first()
    if not notice: raise HTTPException(status_code=404, detail="Notice not found")
    if user.role == "Faculty" and notice.author_id != user.id: raise HTTPException(status_code=403, detail="Faculty may attach files only to their own notices")
    if user.role == "HOD" and notice.department_id != user.department_id and notice.author_id != user.id: raise HTTPException(status_code=403, detail="HOD may attach files only to their department's notices")
    saved = []
    try:
        for upload in files:
            if not upload.filename: continue
            original = Path(upload.filename.replace("\\", "/")).name
            ext = Path(original).suffix.lower()
            if ext not in ALLOWED_EXTENSIONS: raise HTTPException(status_code=400, detail=f"Unsupported file type: {original}")
            data = await upload.read(MAX_FILE_SIZE + 1)
            if len(data) > MAX_FILE_SIZE: raise HTTPException(status_code=413, detail=f"{original} exceeds the 10 MB limit")
            if not data: continue
            if not valid_file_signature(ext, data): raise HTTPException(status_code=400, detail=f"File content does not match its extension: {original}")
            stored = f"{uuid.uuid4().hex}{ext}"; (UPLOAD_DIR / stored).write_bytes(data)
            item = Attachment(notice_id=notice.id, original_filename=original[:255], stored_filename=stored, content_type=mimetypes.guess_type(original)[0] or "application/octet-stream", file_size=len(data), uploaded_by=user.login_id)
            db.add(item); saved.append((stored, item))
        db.commit()
    except Exception:
        db.rollback()
        for stored, _ in saved: (UPLOAD_DIR / stored).unlink(missing_ok=True)
        raise
    return {"uploaded": [{"id": item.id, "filename": item.original_filename, "size": item.file_size, "url": f"/attachments/{item.id}"} for _, item in saved]}

@app.get("/api/users")
def api_users(request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator")
    return [{"id": u.id, "login_id": u.login_id, "full_name": u.full_name, "role": u.role, "department": u.department.code if u.department else None, "email": u.email, "is_active": u.is_active} for u in db.query(User).options(joinedload(User.department)).order_by(User.full_name).all()]

@app.post("/api/users")
def api_create_user(payload: dict, request: Request, db: Session = Depends(get_db)):
    user = require_user(request, db); require_roles(user, "Administrator")
    login_id = str(payload.get("login_id", "")).strip(); password = str(payload.get("password", "")); full_name = str(payload.get("full_name", "")).strip(); role = payload.get("role", "Student")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{3,60}", login_id): raise HTTPException(status_code=400, detail="Login ID must be 3-60 letters, numbers, dots, underscores or hyphens")
    if len(password) < 8: raise HTTPException(status_code=400, detail="Password must be at least 8 characters")
    if not full_name or role not in ALLOWED_ROLES: raise HTTPException(status_code=400, detail="Valid name and role are required")
    if db.query(User).filter_by(login_id=login_id).first(): raise HTTPException(status_code=409, detail="Login ID already exists")
    dept = None
    if payload.get("department"):
        dept = db.query(Department).filter_by(code=payload["department"]).first()
        if not dept: raise HTTPException(status_code=400, detail="Invalid department")
    new = User(login_id=login_id, password_hash=hash_password(password), full_name=full_name, role=role, department_id=dept.id if dept else None, email=str(payload.get("email", "")))
    db.add(new); db.commit(); return {"id": new.id, "login_id": new.login_id, "full_name": new.full_name, "role": new.role}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=int(os.getenv("PORT", "8000")), reload=True)
