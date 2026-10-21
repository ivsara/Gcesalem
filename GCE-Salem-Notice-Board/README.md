# Digital Notice Board System for College Students
**Institution:** Government College of Engineering, Salem (Autonomous)  
**Affiliation:** Anna University, Chennai | Approved by AICTE, New Delhi  
**Project Category:** Master Academic Project / Mobile & Web Full-Stack System

---

## 1. Project Overview
The **Digital Notice Board System** digitizes announcement workflows for Government College of Engineering, Salem. It enables authorized administrators and faculty members to publish announcements, upload circulars and documents, and allows students to access department-wise notices in real time without missing examination schedules, placement updates, or college events.

### Primary Objectives
1. **Digitize Announcements:** End delays and manual distribution of paper circulars.
2. **Role-Based Access Control (RBAC):** Distinct Administrator, Faculty, and Student privileges.
3. **Department Organization:** Dedicated channels for CSE, IT, ECE, EEE, ME, and CE.
4. **Attachments Support:** Image previews, PDF circulars, Word docs, and Excel sheets.
5. **Customizable Themes:** Light/Dark modes, accent colors, and notice card appearances.
6. **Live Auto-Updates:** 10-second polling & reactive updates without manual refresh.
7. **Notice Expiry:** Automated expiry calculation preventing outdated circular display.

---

## 2. Default Demonstration Accounts

| Role | Login ID | Password | Department | Full Name |
| :--- | :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | ALL | Dr. R. Vijayakumar (Principal & Admin) |
| **Faculty (CSE)** | `faculty_cse` | `faculty123` | CSE | Prof. K. Sundararajan (HOD CSE) |
| **Faculty (ECE)** | `faculty_ece` | `faculty123` | ECE | Dr. M. Geetha (Associate Prof. ECE) |
| **Student (CSE)** | `student_cse` | `student123` | CSE | Elango S. (Reg: 737722104012) |
| **Student (IT)** | `student_it` | `student123` | IT | Priya Dharshini K. (Reg: 737722105028) |
| **Guest / Public** | *None* | *None* | ALL | Public Guest (Unauthenticated) |

*Note: In the Android app, tap **"Switch Role"** in the top bar to test any account with 1-tap.*

---

## 3. Technology Stack

### Android Mobile Application
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Local Persistence:** Android Room ORM (SQLite) with KSP
- **Concurrency:** Kotlin Coroutines & Reactive StateFlow
- **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern
- **Image Loading:** Coil Compose
- **Security:** Salted SHA-256 cryptographic password hashing

### Reference Backend (Python FastAPI)
- **Framework:** FastAPI + Pydantic v2
- **ORM / Database:** SQLAlchemy + SQLite
- **Authentication:** OAuth2 JWT Bearer Tokens with SHA-256 salted hashes
- **Server:** Uvicorn ASGI

---

## 4. How to Run

### Running the Android Application
The Android app is pre-configured in this repository. Build and run using standard Gradle commands or the AI Studio Emulator:
```bash
# Compile and build the debug APK
gradle assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest
```

### Running the Python FastAPI Backend (Optional Web API)
```bash
cd GCE-Salem-Notice-Board
pip install -r requirements.txt
python main.py
```
Open your browser to:
- Swagger Interactive Documentation: `http://localhost:8000/docs`
- Redoc Alternative Documentation: `http://localhost:8000/redoc`

---

## 5. Automated Testing & Verification
The project satisfies all 18 verification scenarios specified in the master prompt:
- [x] Scenario 1: Administrator logs in successfully
- [x] Scenario 2: Invalid credentials are rejected securely
- [x] Scenario 3: Inactive/Deactivated users cannot authenticate
- [x] Scenario 4: Students cannot access administrative controls
- [x] Scenario 5: Faculty cannot manage user accounts
- [x] Scenario 6: Faculty cannot edit other faculty members' notices
- [x] Scenario 7: Unauthenticated guests view public announcements
- [x] Scenario 8: Students view notices authorized for their department
- [x] Scenario 9: Restricted notices require authorization
- [x] Scenario 10: Complete Notice CRUD operations functional
- [x] Scenario 11: Expired notices automatically filtered out
- [x] Scenario 12: Attachments (PDF, Images, Docs, Spreadsheets) supported
- [x] Scenario 13: Invalid file types rejected
- [x] Scenario 14: Search by keyword, title, category, and department
- [x] Scenario 15: Theme preferences persist in database across sessions
- [x] Scenario 16: Live polling ticker updates announcements automatically
- [x] Scenario 17: Mobile-first responsive layout (Edge-to-Edge)
- [x] Scenario 18: Ready for physical digital display integration
