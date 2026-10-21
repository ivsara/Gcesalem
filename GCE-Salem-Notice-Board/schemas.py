from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, EmailStr

class DepartmentBase(BaseModel):
    name: str
    code: str
    description: Optional[str] = None
    is_active: bool = True

class DepartmentCreate(DepartmentBase):
    pass

class DepartmentResponse(DepartmentBase):
    id: int
    class Config:
        from_attributes = True

class UserBase(BaseModel):
    login_id: str
    full_name: str
    role: str
    department_id: Optional[int] = None
    email: Optional[str] = None
    is_active: bool = True

class UserCreate(UserBase):
    password: str

class UserResponse(UserBase):
    id: int
    created_at: datetime
    class Config:
        from_attributes = True

class AttachmentResponse(BaseModel):
    id: int
    notice_id: int
    original_filename: str
    file_type: str
    file_size: int
    uploaded_by: str
    created_at: datetime
    class Config:
        from_attributes = True

class NoticeBase(BaseModel):
    title: str
    description: str
    department_id: Optional[int] = None
    category: str
    priority: str = "MEDIUM"
    expires_at: Optional[datetime] = None
    is_pinned: bool = False
    visibility: str = "PUBLIC"

class NoticeCreate(NoticeBase):
    pass

class NoticeResponse(NoticeBase):
    id: int
    author_id: int
    published_at: datetime
    status: str
    created_at: datetime
    updated_at: datetime
    attachments: List[AttachmentResponse] = []
    class Config:
        from_attributes = True

class LoginRequest(BaseModel):
    login_id: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str
    user: UserResponse
