import os
from datetime import datetime
from typing import List, Optional
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from database import Base, engine, get_db
from models import Department, Notice, User, UserPreference
from schemas import (
    DepartmentCreate,
    DepartmentResponse,
    LoginRequest,
    NoticeCreate,
    NoticeResponse,
    TokenResponse,
    UserCreate,
    UserResponse,
)
from auth import (
    create_access_token,
    get_current_user,
    hash_password,
    require_role,
    verify_password,
)

Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="GCE Salem Digital Notice Board API",
    description="Government College of Engineering, Salem - Digital Notice Board System Backend",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------------- SEED INITIAL DATA -----------------
@app.on_event("startup")
def startup_populate():
    db = next(get_db())
    if db.query(Department).count() == 0:
        depts = [
            Department(name="Computer Science and Engineering", code="CSE", description="Department of CSE"),
            Department(name="Information Technology", code="IT", description="Department of IT"),
            Department(name="Electronics and Communication Engineering", code="ECE", description="Department of ECE"),
            Department(name="Electrical and Electronics Engineering", code="EEE", description="Department of EEE"),
            Department(name="Mechanical Engineering", code="ME", description="Department of ME"),
            Department(name="Civil Engineering", code="CE", description="Department of CE")
        ]
        db.add_all(depts)
        db.commit()

    if db.query(User).count() == 0:
        admin_user = User(
            login_id="admin",
            password_hash=hash_password("admin123"),
            full_name="Dr. R. Vijayakumar (Principal & Admin)",
            role="Administrator",
            email="principal@gcesalem.edu.in",
            is_active=True
        )
        faculty_user = User(
            login_id="faculty_cse",
            password_hash=hash_password("faculty123"),
            full_name="Prof. K. Sundararajan (HOD CSE)",
            role="Faculty",
            department_id=1,
            email="hod.cse@gcesalem.edu.in",
            is_active=True
        )
        student_user = User(
            login_id="student_cse",
            password_hash=hash_password("student123"),
            full_name="Elango S. (737722104012)",
            role="Student",
            department_id=1,
            email="elango@student.gcesalem.edu.in",
            is_active=True
        )
        db.add_all([admin_user, faculty_user, student_user])
        db.commit()

        # Seed sample notice
        notice1 = Notice(
            title="End Semester Examination Schedule - Nov/Dec 2026",
            description="Controller of Examinations announces the official timetable for all B.E. students. Download timetable and verify registration numbers.",
            category="Examination",
            priority="HIGH",
            author_id=admin_user.id,
            is_pinned=True,
            visibility="PUBLIC"
        )
        db.add(notice1)
        db.commit()

# ----------------- AUTH ROUTES -----------------
@app.post("/api/auth/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.login_id == request.login_id.strip()).first()
    if not user or not verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=400, detail="Invalid login credentials")
    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account is deactivated")

    access_token = create_access_token(data={"sub": user.login_id, "role": user.role})
    return {"access_token": access_token, "token_type": "bearer", "user": user}

@app.get("/api/auth/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    if not current_user:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return current_user

# ----------------- NOTICE ROUTES -----------------
@app.get("/api/notices", response_model=List[NoticeResponse])
def get_notices(
    department_id: Optional[int] = None,
    category: Optional[str] = None,
    query: Optional[str] = None,
    current_user: Optional[User] = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    now = datetime.utcnow()
    q = db.query(Notice).filter(Notice.status == "PUBLISHED")

    # Expiry filter
    q = q.filter((Notice.expires_at == None) | (Notice.expires_at > now))

    # Access control
    if current_user is None:
        q = q.filter(Notice.visibility == "PUBLIC")
    elif current_user.role == "Student":
        q = q.filter(
            (Notice.visibility == "PUBLIC") |
            (Notice.department_id == None) |
            (Notice.department_id == current_user.department_id)
        )

    if department_id:
        q = q.filter((Notice.department_id == department_id) | (Notice.department_id == None))
    if category and category != "ALL":
        q = q.filter(Notice.category == category)
    if query:
        search_filter = f"%{query}%"
        q = q.filter(Notice.title.ilike(search_filter) | Notice.description.ilike(search_filter))

    return q.order_by(Notice.is_pinned.desc(), Notice.published_at.desc()).all()

@app.post("/api/notices", response_model=NoticeResponse)
def create_notice(
    notice: NoticeCreate,
    current_user: User = Depends(require_role(["Administrator", "Faculty"])),
    db: Session = Depends(get_db)
):
    if current_user.role == "Faculty" and notice.department_id and notice.department_id != current_user.department_id:
        raise HTTPException(status_code=403, detail="Faculty can only publish to their assigned department")

    db_notice = Notice(
        title=notice.title,
        description=notice.description,
        department_id=notice.department_id,
        category=notice.category,
        priority=notice.priority,
        author_id=current_user.id,
        expires_at=notice.expires_at,
        is_pinned=notice.is_pinned if current_user.role == "Administrator" else False,
        visibility=notice.visibility
    )
    db.add(db_notice)
    db.commit()
    db.refresh(db_notice)
    return db_notice

@app.delete("/api/notices/{notice_id}")
def delete_notice(
    notice_id: int,
    current_user: User = Depends(require_role(["Administrator", "Faculty"])),
    db: Session = Depends(get_db)
):
    notice = db.query(Notice).filter(Notice.id == notice_id).first()
    if not notice:
        raise HTTPException(status_code=404, detail="Notice not found")
    if current_user.role == "Faculty" and notice.author_id != current_user.id:
        raise HTTPException(status_code=403, detail="Cannot delete notices published by other users")

    db.delete(notice)
    db.commit()
    return {"message": "Notice deleted successfully"}

# ----------------- DEPARTMENTS -----------------
@app.get("/api/departments", response_model=List[DepartmentResponse])
def get_departments(db: Session = Depends(get_db)):
    return db.query(Department).filter(Department.is_active == True).all()

# ----------------- USERS (ADMIN ONLY) -----------------
@app.get("/api/users", response_model=List[UserResponse])
def get_all_users(
    current_user: User = Depends(require_role(["Administrator"])),
    db: Session = Depends(get_db)
):
    return db.query(User).all()

@app.post("/api/users", response_model=UserResponse)
def create_user(
    user_in: UserCreate,
    current_user: User = Depends(require_role(["Administrator"])),
    db: Session = Depends(get_db)
):
    existing = db.query(User).filter(User.login_id == user_in.login_id).first()
    if existing:
        raise HTTPException(status_code=400, detail="Login ID already in use")

    new_user = User(
        login_id=user_in.login_id,
        password_hash=hash_password(user_in.password),
        full_name=user_in.full_name,
        role=user_in.role,
        department_id=user_in.department_id,
        email=user_in.email,
        is_active=user_in.is_active
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    return new_user

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
