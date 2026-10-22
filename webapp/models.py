from datetime import datetime, timezone
from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from database import Base

def utcnow():
    return datetime.now(timezone.utc).replace(tzinfo=None)

class Department(Base):
    __tablename__ = "departments"
    id = Column(Integer, primary_key=True)
    name = Column(String(120), nullable=False)
    code = Column(String(12), unique=True, nullable=False, index=True)
    description = Column(String(255), default="")
    is_active = Column(Boolean, default=True, nullable=False)
    users = relationship("User", back_populates="department")
    notices = relationship("Notice", back_populates="department")

class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True)
    login_id = Column(String(60), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    full_name = Column(String(120), nullable=False)
    role = Column(String(20), nullable=False, default="Student")
    department_id = Column(Integer, ForeignKey("departments.id"), nullable=True)
    email = Column(String(160), default="")
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=utcnow, nullable=False)
    department = relationship("Department", back_populates="users")
    notices = relationship("Notice", back_populates="author")
    preferences = relationship("UserPreference", back_populates="user", uselist=False, cascade="all, delete-orphan")

class Notice(Base):
    __tablename__ = "notices"
    id = Column(Integer, primary_key=True)
    title = Column(String(200), nullable=False)
    description = Column(Text, nullable=False)
    department_id = Column(Integer, ForeignKey("departments.id"), nullable=True)
    category = Column(String(40), nullable=False, default="General")
    priority = Column(String(12), nullable=False, default="MEDIUM")
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    published_at = Column(DateTime, default=utcnow, nullable=False)
    expires_at = Column(DateTime, nullable=True)
    status = Column(String(20), default="PUBLISHED", nullable=False)
    is_pinned = Column(Boolean, default=False, nullable=False)
    visibility = Column(String(20), default="PUBLIC", nullable=False)
    updated_at = Column(DateTime, default=utcnow, onupdate=utcnow, nullable=False)
    department = relationship("Department", back_populates="notices")
    author = relationship("User", back_populates="notices")
    attachments = relationship("Attachment", back_populates="notice", cascade="all, delete-orphan")

class Attachment(Base):
    __tablename__ = "attachments"
    id = Column(Integer, primary_key=True)
    notice_id = Column(Integer, ForeignKey("notices.id", ondelete="CASCADE"), nullable=False)
    original_filename = Column(String(255), nullable=False)
    stored_filename = Column(String(255), unique=True, nullable=False)
    content_type = Column(String(120), nullable=False)
    file_size = Column(Integer, nullable=False)
    uploaded_by = Column(String(60), nullable=False)
    created_at = Column(DateTime, default=utcnow, nullable=False)
    notice = relationship("Notice", back_populates="attachments")

class UserPreference(Base):
    __tablename__ = "user_preferences"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    theme_mode = Column(String(10), default="light", nullable=False)
    accent_colour = Column(String(7), default="#155eef", nullable=False)
    background_colour = Column(String(7), default="#f5f7fb", nullable=False)
    font_family = Column(String(40), default="Inter", nullable=False)
    user = relationship("User", back_populates="preferences")
