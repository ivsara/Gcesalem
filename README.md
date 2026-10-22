# Digital Notice Board System — Government College of Engineering, Salem

A complete, role-based **Digital Notice Board System** for **Government College of Engineering, Salem (Autonomous)** supporting:
1. **GitHub Pages Live Web App (`index.html`, `styles.css`, `app.js` at root)** — Runs directly on `https://ivsara.github.io/Gcesalem/` with real-time cross-phone cloud synchronization.
2. **Android Mobile App (`app/`)** — Native Kotlin + Jetpack Compose + Room SQLite application synchronized with the same live cross-phone notice channel.
3. **Python FastAPI + SQLite Backend (`webapp/`)** — Server-rendered and REST API web application with SQLAlchemy, Jinja2, PBKDF2-HMAC-SHA256 authentication, and file uploads.

---

## 1. Departments & Role Hierarchy (No Personal Names)

Supported Academic Departments (**7 Departments**):
- **`CIVIL`** — Civil Engineering
- **`MECH`** — Mechanical Engineering
- **`ECE`** — Electronics and Communication Engineering
- **`EEE`** — Electrical and Electronics Engineering
- **`CSE`** — Computer Science and Engineering
- **`METT`** — Metallurgical Engineering
- **`AIML`** — Artificial Intelligence and Machine Learning

### Role-Only Accounts (99 Total Accounts)

| Role Designation | Login ID Pattern | Password | Department Scope | Count |
| :--- | :--- | :--- | :--- | :--- |
| **Admin Principal** | `principal` (or `admin`) | `principal123` (or `admin123`) | `ALL` (College-wide) | 1 |
| **HOD (Head of Dept)** | `hod_civil`, `hod_mech`, `hod_ece`, `hod_eee`, `hod_cse`, `hod_mett`, `hod_aiml` | `hod123` | Respective Department | 7 (1 per dept) |
| **Faculty (1 to 3)** | `faculty_<dept>1` .. `faculty_<dept>3` (e.g. `faculty_cse1`, `faculty_ece2`) | `faculty123` | Respective Department | 21 (3 per dept) |
| **Student (1 to 10)** | `student_<dept>1` .. `student_<dept>10` (e.g. `student_cse1`, `student_aiml10`) | `student123` | Respective Department | 70 (10 per dept) |

---

## 2. Cross-Phone Notice Synchronization

When an **Admin Principal**, **HOD**, or **Faculty** publishes, edits, or deletes a notice on **Phone 1** (either via the GitHub Pages web app or the Android app):
- The notice is immediately broadcast to the shared GCE Salem cloud sync channel.
- On **Phone 2**, clicking **Refresh** (or waiting for the automatic 10–15s live poll) immediately fetches and displays the newly published announcement.

---

## 3. Run Locally with Python FastAPI

```bash
cd webapp
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload
```
- Web Portal: `http://127.0.0.1:8000`
- Swagger API Docs: `http://127.0.0.1:8000/docs`
