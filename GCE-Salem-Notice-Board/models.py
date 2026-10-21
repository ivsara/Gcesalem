from datetime import datetime
from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    login_id = Column(String(50), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    full_name = Column(String(100), nullable=False)
    role = Column(String(20), nullable=False)  # Administrator, Faculty, Student
    department_id = Column(Integer, ForeignKey("departments.id"), nullable=True)
    email = Column(String(100), nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    department = relationship("Department", back_populates="users")
    notices = relationship("Notice", back_populates="author")
    preferences = relationship("UserPreference", back_populates="user", uselist=False)

class Department(Base):
    __tablename__ = "departments"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(100), nullable=False)
    code = Column(String(20), unique=True, index=True, nullable=False)  # CSE, IT, ECE, EEE, ME, CE
    description = Column(String(255), nullable=True)
    is_active = Column(Boolean, default=True)

    users = relationship("User", back_populates="department")
    notices = relationship("Notice", back_populates="department")

class Notice(Base):
    __tablename__ = "notices"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(200), nullable=False)
    description = Column(Text, nullable=False)
    department_id = Column(Integer, ForeignKey("departments.id"), nullable=True)
    category = Column(String(50), nullable=False)  # Examination, Events, Placements, General, etc.
    priority = Column(String(20), default="MEDIUM")  # HIGH, MEDIUM, LOW
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    published_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=True)
    status = Column(String(20), default="PUBLISHED")  # PUBLISHED, ARCHIVED, DRAFT
    is_pinned = Column(Boolean, default=False)
    visibility = Column(String(20), default="PUBLIC")  # PUBLIC, RESTRICTED
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    department = relationship("Department", back_populates="notices")
    author = relationship("User", back_populates="notices")
    attachments = relationship("Attachment", back_populates="notice", cascade="all, delete-orphan")

class Attachment(Base):
    __tablename__ = "attachments"

    id = Column(Integer, primary_key=True, index=True)
    notice_id = Column(Integer, ForeignKey("notices.id"), nullable=False)
    original_filename = Column(String(255), nullable=False)
    stored_filename = Column(String(255), nullable=False)
    file_type = Column(String(20), nullable=False)  # PDF, IMAGE, DOC, XLS
    file_size = Column(Integer, nullable=False)
    uploaded_by = Column(String(50), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    notice = relationship("Notice", back_populates="attachments")

class UserPreference(Base):
    __tablename__ = "user_preferences"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), unique=True, nullable=False)
    theme_mode = Column(String(20), default="LIGHT")  # LIGHT, DARK
    background_colour = Column(String(20), default="#F8FAFC")
    accent_colour = Column(String(20), default="#0B3C5D")
    font_family = Column(String(50), default="Inter")
    font_size = Column(String(20), default="MEDIUM")
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="preferences")
