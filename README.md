# Digital Notice Board System — Government College of Engineering, Salem

A complete, role-based **Digital Notice Board System** for **Government College of Engineering, Salem (Autonomous)** supporting:
1. **GitHub Pages Live Web App (`index.html`, `styles.css`, `app.js` at root)** — Runs directly on `https://ivsara.github.io/Gcesalem/` with zero server setup required.
2. **Python FastAPI + SQLite Backend (`webapp/`)** — Full server-rendered and REST API web application with SQLAlchemy, Jinja2, PBKDF2-HMAC-SHA256 authentication, CSRF protection, and real file uploads.
3. **Android Mobile App (`app/`)** — Native Kotlin + Jetpack Compose + Room SQLite application.

---

## 1. Run Directly on GitHub Pages (Instant Browser Access)

Once pushed to GitHub (`main` branch):
- Open **`https://ivsara.github.io/Gcesalem/`**
- The root `index.html`, `styles.css`, `app.js`, and `.nojekyll` files power the full interactive portal in the browser.
- Use the **Instant Role Switcher** at the top or click **Sign in** with any of the demo accounts below:

| Role | Login ID | Password | Department |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | `ALL` (College-wide) |
| **Faculty (CSE)** | `faculty_cse` | `faculty123` | `CSE` |
| **Faculty (ECE)** | `faculty_ece` | `faculty123` | `ECE` |
| **Student (CSE)** | `student_cse` | `student123` | `CSE` |
| **Student (IT)** | `student_it` | `student123` | `IT` |

---

## 2. Run in GitHub Codespaces (1-Click FastAPI Server)

1. On your GitHub repository page, click the green **`<> Code`** button.
2. Switch to the **Codespaces** tab and click **Create codespace on main**.
3. Codespaces will automatically install `webapp/requirements.txt` and start `uvicorn main:app --host 0.0.0.0 --port 8000` (configured via `.devcontainer/devcontainer.json`).

---

## 3. Run Locally with Python FastAPI

```bash
cd webapp
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload
```
- Web Portal: `http://127.0.0.1:8000`
- Swagger API Docs: `http://127.0.0.1:8000/docs`
- Run Automated Tests: `pytest -q`
