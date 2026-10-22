(() => {
  const STORAGE_KEY = 'gce_salem_notice_board_db_v4';
  const SESSION_KEY = 'gce_salem_current_user_v4';
  const CLOUD_SYNC_TOPIC = 'gce_salem_notice_board_sync_v4';
  const CLOUD_SYNC_URL = `https://ntfy.sh/${CLOUD_SYNC_TOPIC}`;

  const escapeHtml = val =>
    String(val ?? '').replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[c]));

  const dayMs = 24 * 60 * 60 * 1000;
  const nowMs = () => Date.now();

  function createSampleDataUrl(filename, content) {
    return 'data:text/plain;charset=utf-8,' + encodeURIComponent(content);
  }

  const DEPT_LIST = [
    { code: 'CIVIL', name: 'Civil Engineering', isActive: true },
    { code: 'MECH', name: 'Mechanical Engineering', isActive: true },
    { code: 'ECE', name: 'Electronics and Communication Engineering', isActive: true },
    { code: 'EEE', name: 'Electrical and Electronics Engineering', isActive: true },
    { code: 'CSE', name: 'Computer Science and Engineering', isActive: true },
    { code: 'METT', name: 'Metallurgical Engineering', isActive: true },
    { code: 'AIML', name: 'Artificial Intelligence and Machine Learning', isActive: true }
  ];

  function buildDefaultUsers() {
    const users = [
      {
        loginId: 'principal',
        password: 'principal123',
        fullName: 'Admin Principal',
        role: 'Administrator',
        department: 'ALL',
        isActive: true
      },
      {
        loginId: 'admin',
        password: 'admin123',
        fullName: 'Admin Principal',
        role: 'Administrator',
        department: 'ALL',
        isActive: true
      }
    ];

    DEPT_LIST.forEach(d => {
      const lower = d.code.toLowerCase();
      // 1 HOD per department
      users.push({
        loginId: `hod_${lower}`,
        password: 'hod123',
        fullName: `HOD - ${d.code}`,
        role: 'HOD',
        department: d.code,
        isActive: true
      });
      // 3 Faculty per department
      for (let f = 1; f <= 3; f++) {
        users.push({
          loginId: `faculty_${lower}${f}`,
          password: 'faculty123',
          fullName: `Faculty ${f} - ${d.code}`,
          role: 'Faculty',
          department: d.code,
          isActive: true
        });
      }
      // 10 Students per department
      for (let s = 1; s <= 10; s++) {
        users.push({
          loginId: `student_${lower}${s}`,
          password: 'student123',
          fullName: `Student ${s} - ${d.code}`,
          role: 'Student',
          department: d.code,
          isActive: true
        });
      }
    });

    return users;
  }

  function defaultDatabase() {
    const now = nowMs();
    return {
      departments: DEPT_LIST.map(d => ({ ...d })),
      users: buildDefaultUsers(),
      notices: [
        {
          id: 101,
          title: 'End Semester Examination Schedule — Nov/Dec 2026 (Autonomous)',
          description: 'Controller of Examinations (Autonomous) announces the official timetable for 3rd, 5th, and 7th Semester B.E. Degree Examinations across CIVIL, MECH, ECE, EEE, CSE, METT, and AIML. Hall tickets will be issued through respective department offices.',
          department: 'ALL',
          category: 'Examination',
          priority: 'HIGH',
          authorLoginId: 'principal',
          authorName: 'Admin Principal',
          publishedAt: now - 2 * dayMs,
          expiresAt: now + 30 * dayMs,
          isPinned: true,
          visibility: 'PUBLIC',
          attachments: [
            {
              id: 1,
              filename: 'GCE_Salem_End_Sem_Timetable_NovDec2026.pdf',
              fileType: 'PDF',
              size: 1420500,
              previewText: 'CONTROLLER OF EXAMINATIONS — GCE SALEM (AUTONOMOUS)\nForenoon Session: 09:30 AM – 12:30 PM\nAfternoon Session: 01:30 PM – 04:30 PM\nAll departments (CIVIL, MECH, ECE, EEE, CSE, METT, AIML) theory examinations commence from 16-Nov-2026.',
              dataUrl: createSampleDataUrl('GCE_Salem_End_Sem_Timetable_NovDec2026.pdf', 'GCE SALEM OFFICIAL EXAMINATION TIMETABLE NOV/DEC 2026')
            }
          ]
        },
        {
          id: 102,
          title: 'Mega Campus Placement Drive 2026: Zoho, TCS & Caterpillar',
          description: 'Training & Placement Cell invites eligible Final Year B.E. students (CIVIL, MECH, ECE, EEE, CSE, METT, AIML) with 6.5+ CGPA and no standing arrears. Aptitude and coding assessments start Monday at 9:00 AM in the Central Computing Centre.',
          department: 'ALL',
          category: 'Placements',
          priority: 'HIGH',
          authorLoginId: 'principal',
          authorName: 'Admin Principal',
          publishedAt: now - 1 * dayMs,
          expiresAt: now + 15 * dayMs,
          isPinned: true,
          visibility: 'PUBLIC',
          attachments: [
            {
              id: 2,
              filename: 'Eligible_Students_Placement_Drive_2026.xlsx',
              fileType: 'XLS',
              size: 582400,
              previewText: 'Shortlisted Candidates across CIVIL, MECH, ECE, EEE, CSE, METT, AIML.\nVenue: Central Computing Centre Lab 1 & 2.',
              dataUrl: createSampleDataUrl('Eligible_Students_Placement_Drive_2026.xlsx', 'RegNo,Role,Department,CGPA,Slot\n737722104001,Student 1 - CSE,CSE,8.7,Batch-1')
            }
          ]
        },
        {
          id: 103,
          title: 'Smart India Hackathon (SIH 2026) — Internal College Evaluation',
          description: 'Department of CSE and AIML are organizing the Internal SIH 2026 Hackathon on Saturday at Seminar Hall 1. Teams of 6 members must submit their prototype presentation deck before Friday 5:00 PM.',
          department: 'CSE',
          category: 'Events',
          priority: 'HIGH',
          authorLoginId: 'hod_cse',
          authorName: 'HOD - CSE',
          publishedAt: now - 6 * 3600 * 1000,
          expiresAt: now + 10 * dayMs,
          isPinned: false,
          visibility: 'PUBLIC',
          attachments: [
            {
              id: 3,
              filename: 'SIH_2026_Campus_Poster.png',
              fileType: 'IMAGE',
              size: 1940000,
              previewText: 'SIH 2026 Internal Hackathon Poster — Venue: CSE Seminar Hall',
              dataUrl: ''
            }
          ]
        },
        {
          id: 104,
          title: 'CSE Model Practical Examination & Batch Allotment Circular',
          description: 'All 2nd and 3rd year CSE students are informed that Model Practical Examinations for Operating Systems, DBMS, and Computer Networks will be conducted strictly as per the batch schedule attached.',
          department: 'CSE',
          category: 'Department Circulars',
          priority: 'MEDIUM',
          authorLoginId: 'faculty_cse1',
          authorName: 'Faculty 1 - CSE',
          publishedAt: now - 12 * 3600 * 1000,
          expiresAt: now + 12 * dayMs,
          isPinned: false,
          visibility: 'RESTRICTED',
          attachments: [
            {
              id: 4,
              filename: 'CSE_Model_Lab_Batches_2026.docx',
              fileType: 'DOC',
              size: 345000,
              previewText: 'Batch A: Student 1 - CSE to Student 5 - CSE (09:00 AM)\nBatch B: Student 6 - CSE to Student 10 - CSE (01:30 PM)\nObservation notebook and Lab Coat mandatory.',
              dataUrl: createSampleDataUrl('CSE_Model_Lab_Batches_2026.docx', 'CSE Model Lab Schedule - Batch A & Batch B')
            }
          ]
        },
        {
          id: 105,
          title: 'IEEE Workshop on VLSI Design & Embedded Systems — ECE Dept',
          description: 'Hands-on training session on FPGA synthesis and embedded C programming for ECE students. Registration open at the ECE Department Office.',
          department: 'ECE',
          category: 'Department Circulars',
          priority: 'MEDIUM',
          authorLoginId: 'hod_ece',
          authorName: 'HOD - ECE',
          publishedAt: now - 18 * 3600 * 1000,
          expiresAt: now + 9 * dayMs,
          isPinned: false,
          visibility: 'RESTRICTED',
          attachments: []
        }
      ],
      deletedIds: [],
      preferences: {}
    };
  }

  function loadDb() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        const initial = defaultDatabase();
        localStorage.setItem(STORAGE_KEY, JSON.stringify(initial));
        return initial;
      }
      const parsed = JSON.parse(raw);
      parsed.deletedIds = parsed.deletedIds || [];
      return parsed;
    } catch (_) {
      return defaultDatabase();
    }
  }

  function saveDb(db) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch (_) {}
  }

  let db = loadDb();
  let currentUser = null;
  try {
    const savedLogin = localStorage.getItem(SESSION_KEY);
    if (savedLogin) {
      currentUser = db.users.find(u => u.loginId === savedLogin && u.isActive) || null;
    }
  } catch (_) {}

  // Filter State
  let selectedDept = 'ALL';
  let selectedCategory = 'ALL';
  let selectedPriority = 'ALL';
  let searchQuery = '';
  let showExpired = false;
  let cardStyle = 'elevated';

  // DOM Elements
  const noticeListEl = document.getElementById('notice-list');
  const headingCountEl = document.getElementById('heading-count');
  const statTotalEl = document.getElementById('stat-total');
  const statDeptsEl = document.getElementById('stat-depts');
  const statPinnedEl = document.getElementById('stat-pinned');
  const statRoleEl = document.getElementById('stat-role');
  const accessSubtitleEl = document.getElementById('access-subtitle');
  const lastSyncTimeEl = document.getElementById('last-sync-time');
  const syncStatusTextEl = document.getElementById('sync-status-text');

  const navPublishEl = document.getElementById('nav-publish');
  const navAdminEl = document.getElementById('nav-admin');
  const publishSectionEl = document.getElementById('publish');
  const adminSectionEl = document.getElementById('admin-panel');
  const expiredToggleWrapEl = document.getElementById('expired-toggle-wrap');

  const userChipContainerEl = document.getElementById('user-chip-container');
  const userAvatarEl = document.getElementById('user-avatar');
  const userFullnameEl = document.getElementById('user-fullname');
  const userRoleDeptEl = document.getElementById('user-role-dept');
  const btnOpenLogin = document.getElementById('btn-open-login');
  const btnLogout = document.getElementById('btn-logout');

  const deptQuickTabsEl = document.getElementById('dept-quick-tabs');
  const filterDeptSelect = document.getElementById('filter-dept');
  const filterCategorySelect = document.getElementById('filter-category');
  const filterPrioritySelect = document.getElementById('filter-priority');
  const filterQInput = document.getElementById('filter-q');
  const filterExpiredCheckbox = document.getElementById('filter-expired');

  const noticeForm = document.getElementById('notice-form');
  const editNoticeIdInput = document.getElementById('edit-notice-id');
  const noticeTitleInput = document.getElementById('notice-title');
  const noticeDescInput = document.getElementById('notice-desc');
  const noticeDeptSelect = document.getElementById('notice-dept');
  const noticeCategorySelect = document.getElementById('notice-category');
  const noticePrioritySelect = document.getElementById('notice-priority');
  const noticeExpiryInput = document.getElementById('notice-expiry');
  const noticeVisibilitySelect = document.getElementById('notice-visibility');
  const noticePinnedCheckbox = document.getElementById('notice-pinned');
  const noticeFilesInput = document.getElementById('notice-files');
  const formSectionTitle = document.getElementById('form-section-title');
  const btnSubmitNotice = document.getElementById('btn-submit-notice');
  const btnCancelEdit = document.getElementById('btn-cancel-edit');

  // Theme Elements
  const themeModeSelect = document.getElementById('theme-mode');
  const themeAccentInput = document.getElementById('theme-accent');
  const themeBgInput = document.getElementById('theme-bg');
  const themeFontSelect = document.getElementById('theme-font');
  const themeSizeSelect = document.getElementById('theme-size');
  const themeCardStyleSelect = document.getElementById('theme-card-style');

  const icons = {
    Examination: '▣',
    Placements: '↗',
    Events: '✦',
    General: '▤',
    'Department Circulars': '⌘',
    'Important Announcements': '⚑'
  };

  function isPublisherRole(role) {
    return role === 'Administrator' || role === 'HOD' || role === 'Faculty';
  }

  function formatDate(ms) {
    if (!ms) return '';
    return new Date(ms).toLocaleDateString('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  // ---------------- CLOUD SYNC ACROSS PHONES ----------------

  function applyCloudPayload(payload) {
    if (!payload || !payload.action) return false;
    db.deletedIds = db.deletedIds || [];

    if (payload.action === 'DELETE' && payload.noticeId) {
      const id = Number(payload.noticeId);
      if (!db.deletedIds.includes(id)) db.deletedIds.push(id);
      const beforeLen = db.notices.length;
      db.notices = db.notices.filter(n => Number(n.id) !== id);
      return db.notices.length !== beforeLen;
    }

    if (payload.action === 'UPSERT' && payload.notice) {
      const incoming = payload.notice;
      const id = Number(incoming.id);
      if (db.deletedIds.includes(id)) return false;

      const idx = db.notices.findIndex(n => Number(n.id) === id);
      const normalized = {
        id,
        title: String(incoming.title || ''),
        description: String(incoming.description || ''),
        department: String(incoming.department || 'ALL'),
        category: String(incoming.category || 'General'),
        priority: String(incoming.priority || 'MEDIUM'),
        authorLoginId: String(incoming.authorLoginId || 'principal'),
        authorName: String(incoming.authorName || 'Admin Principal'),
        publishedAt: Number(incoming.publishedAt || nowMs()),
        expiresAt: Number(incoming.expiresAt || 0),
        isPinned: Boolean(incoming.isPinned),
        visibility: String(incoming.visibility || 'PUBLIC'),
        attachments: (incoming.attachments || []).map(a => ({
          id: Number(a.id || Date.now()),
          filename: String(a.filename || 'Circular.pdf'),
          fileType: String(a.fileType || 'PDF'),
          size: Number(a.size || 102400),
          previewText: String(a.previewText || 'Official GCE Salem Circular'),
          dataUrl: a.dataUrl || createSampleDataUrl(a.filename || 'Circular.pdf', a.previewText || a.filename || 'GCE Salem Circular')
        }))
      };

      if (idx === -1) {
        db.notices.unshift(normalized);
        return true;
      } else {
        // Keep local dataUrl if already present
        const existingAtts = db.notices[idx].attachments || [];
        if (existingAtts.length && (!normalized.attachments || !normalized.attachments.length)) {
          normalized.attachments = existingAtts;
        }
        db.notices[idx] = normalized;
        return true;
      }
    }
    return false;
  }

  async function publishCloudEvent(eventObj) {
    try {
      const compactEvent = JSON.parse(JSON.stringify(eventObj));
      if (compactEvent.notice && compactEvent.notice.attachments) {
        compactEvent.notice.attachments = compactEvent.notice.attachments.map(a => ({
          id: a.id,
          filename: a.filename,
          fileType: a.fileType,
          size: a.size,
          previewText: String(a.previewText || '').slice(0, 240)
        }));
      }
      await fetch(CLOUD_SYNC_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain;charset=utf-8' },
        body: JSON.stringify(compactEvent)
      });
    } catch (_) {}
  }

  async function syncFromCloud() {
    try {
      if (syncStatusTextEl) syncStatusTextEl.textContent = 'Syncing notices…';
      const resp = await fetch(`${CLOUD_SYNC_URL}/json?poll=1&since=24h&_t=${Date.now()}`, {
        cache: 'no-store'
      });
      if (!resp.ok) throw new Error('Sync status ' + resp.status);
      const text = await resp.text();
      const lines = text.trim().split('\n').filter(Boolean);
      let changed = false;
      for (const line of lines) {
        try {
          const env = JSON.parse(line);
          if (env.event === 'message' && env.message) {
            const payload = JSON.parse(env.message);
            if (applyCloudPayload(payload)) changed = true;
          }
        } catch (_) {}
      }
      if (changed) {
        saveDb(db);
      }
      renderNotices();
      if (syncStatusTextEl) syncStatusTextEl.textContent = 'Cloud sync active';
    } catch (_) {
      renderNotices();
      if (syncStatusTextEl) syncStatusTextEl.textContent = 'Local + Cloud ready';
    }
  }

  function startRealtimeStream() {
    if (typeof EventSource === 'undefined') return;
    try {
      const es = new EventSource(`${CLOUD_SYNC_URL}/sse`);
      es.onmessage = ev => {
        try {
          const env = JSON.parse(ev.data);
          if (env.event === 'message' && env.message) {
            const payload = JSON.parse(env.message);
            if (applyCloudPayload(payload)) {
              saveDb(db);
              renderNotices();
            }
          }
        } catch (_) {}
      };
    } catch (_) {}
  }

  // ---------------- THEME & DISPLAY ----------------

  function applyThemePreferences() {
    const key = currentUser ? currentUser.loginId : 'guest';
    const pref = (db.preferences && db.preferences[key]) || {
      mode: 'light',
      accent: '#155eef',
      bg: '#f5f7fb',
      font: "'DM Sans', system-ui, sans-serif",
      size: '14px',
      cardStyle: 'elevated'
    };

    document.documentElement.setAttribute('data-theme', pref.mode);
    document.documentElement.style.setProperty('--accent', pref.accent);
    document.documentElement.style.setProperty('--page-bg', pref.bg);
    document.documentElement.style.setProperty('--font', pref.font);
    document.documentElement.style.setProperty('--font-size-base', pref.size);
    cardStyle = pref.cardStyle || 'elevated';

    if (themeModeSelect) themeModeSelect.value = pref.mode;
    if (themeAccentInput) themeAccentInput.value = pref.accent;
    if (themeBgInput) themeBgInput.value = pref.bg;
    if (themeFontSelect) themeFontSelect.value = pref.font;
    if (themeSizeSelect) themeSizeSelect.value = pref.size;
    if (themeCardStyleSelect) themeCardStyleSelect.value = cardStyle;
  }

  function saveCurrentTheme() {
    const key = currentUser ? currentUser.loginId : 'guest';
    db.preferences = db.preferences || {};
    db.preferences[key] = {
      mode: themeModeSelect.value,
      accent: themeAccentInput.value,
      bg: themeBgInput.value,
      font: themeFontSelect.value,
      size: themeSizeSelect.value,
      cardStyle: themeCardStyleSelect.value
    };
    saveDb(db);
    applyThemePreferences();
    renderNotices();
  }

  // ---------------- PERMISSIONS & FILTERING ----------------

  function canUserViewNotice(user, n, now) {
    const isExpired = n.expiresAt && n.expiresAt > 0 && n.expiresAt < now;
    if (showExpired && user && user.role === 'Administrator') {
      if (!isExpired) return false;
    } else {
      if (isExpired) return false;
    }

    if (!user) {
      return n.visibility === 'PUBLIC';
    }
    if (user.role === 'Student') {
      return n.visibility === 'PUBLIC' || n.department === 'ALL' || n.department === user.department;
    }
    if (user.role === 'Faculty' || user.role === 'HOD') {
      return (
        n.visibility === 'PUBLIC' ||
        n.department === 'ALL' ||
        n.department === user.department ||
        n.authorLoginId === user.loginId
      );
    }
    return true; // Administrator (Admin Principal)
  }

  function getFilteredNotices() {
    const now = nowMs();
    return db.notices
      .filter(n => {
        if (!canUserViewNotice(currentUser, n, now)) return false;
        if (selectedDept !== 'ALL' && n.department !== selectedDept && n.department !== 'ALL') {
          return false;
        }
        if (selectedCategory !== 'ALL' && n.category !== selectedCategory) {
          return false;
        }
        if (selectedPriority !== 'ALL' && n.priority !== selectedPriority) {
          return false;
        }
        if (searchQuery.trim() !== '') {
          const q = searchQuery.trim().toLowerCase();
          const match =
            n.title.toLowerCase().includes(q) ||
            n.description.toLowerCase().includes(q) ||
            n.category.toLowerCase().includes(q) ||
            n.department.toLowerCase().includes(q) ||
            (n.authorName && n.authorName.toLowerCase().includes(q));
          if (!match) return false;
        }
        return true;
      })
      .sort((a, b) => {
        if (Boolean(b.isPinned) !== Boolean(a.isPinned)) {
          return b.isPinned ? 1 : -1;
        }
        return (b.publishedAt || 0) - (a.publishedAt || 0);
      });
  }

  function renderDepartmentOptions() {
    const activeDepts = db.departments.filter(d => d.isActive);
    statDeptsEl.textContent = activeDepts.length;

    // Quick Tabs
    const tabsHtml = [
      `<button type="button" class="dept-tab ${selectedDept === 'ALL' ? 'active' : ''}" data-dept="ALL">All Campus</button>`
    ]
      .concat(
        activeDepts.map(
          d =>
            `<button type="button" class="dept-tab ${selectedDept === d.code ? 'active' : ''}" data-dept="${escapeHtml(d.code)}">${escapeHtml(d.code)} — ${escapeHtml(d.name)}</button>`
        )
      )
      .join('');
    deptQuickTabsEl.innerHTML = tabsHtml;

    // Filter Dropdown
    filterDeptSelect.innerHTML =
      `<option value="ALL">All departments</option>` +
      activeDepts
        .map(
          d =>
            `<option value="${escapeHtml(d.code)}" ${selectedDept === d.code ? 'selected' : ''}>${escapeHtml(d.code)} — ${escapeHtml(d.name)}</option>`
        )
        .join('');

    // Notice Publish Form Dept Select
    if (currentUser) {
      if (currentUser.role === 'Administrator') {
        noticeDeptSelect.innerHTML =
          `<option value="ALL">College-wide (All Departments)</option>` +
          activeDepts.map(d => `<option value="${escapeHtml(d.code)}">${escapeHtml(d.code)} — ${escapeHtml(d.name)}</option>`).join('');
      } else if (currentUser.role === 'Faculty' || currentUser.role === 'HOD') {
        noticeDeptSelect.innerHTML = activeDepts
          .filter(d => d.code === currentUser.department)
          .map(d => `<option value="${escapeHtml(d.code)}">${escapeHtml(d.code)} — ${escapeHtml(d.name)}</option>`)
          .join('');
      }
    }

    // Admin New User Dept Select & Filter
    const newUserDept = document.getElementById('new-user-dept');
    if (newUserDept) {
      newUserDept.innerHTML =
        `<option value="ALL">ALL</option>` +
        activeDepts.map(d => `<option value="${escapeHtml(d.code)}">${escapeHtml(d.code)}</option>`).join('');
    }
    const adminUserFilter = document.getElementById('admin-user-dept-filter');
    if (adminUserFilter && adminUserFilter.options.length <= 1) {
      adminUserFilter.innerHTML =
        `<option value="ALL">All Departments</option>` +
        activeDepts.map(d => `<option value="${escapeHtml(d.code)}">${escapeHtml(d.code)}</option>`).join('');
    }
  }

  function renderAuthUI() {
    if (!currentUser) {
      userChipContainerEl.classList.add('hidden');
      btnOpenLogin.classList.remove('hidden');
      btnLogout.classList.add('hidden');
      navPublishEl.classList.add('hidden');
      navAdminEl.classList.add('hidden');
      publishSectionEl.classList.add('hidden');
      adminSectionEl.classList.add('hidden');
      expiredToggleWrapEl.classList.add('hidden');
      statRoleEl.textContent = 'Guest';
      accessSubtitleEl.textContent =
        'Showing public college announcements. Sign in to view restricted department circulars.';
    } else {
      userChipContainerEl.classList.remove('hidden');
      userAvatarEl.textContent = currentUser.fullName.charAt(0).toUpperCase();
      userFullnameEl.textContent = currentUser.fullName;
      userRoleDeptEl.textContent = `${currentUser.role} · ${currentUser.department}`;
      btnOpenLogin.classList.add('hidden');
      btnLogout.classList.remove('hidden');
      statRoleEl.textContent = currentUser.fullName;

      const canPublish = isPublisherRole(currentUser.role);
      navPublishEl.classList.toggle('hidden', !canPublish);
      publishSectionEl.classList.toggle('hidden', !canPublish);
      document.getElementById('publish-role-badge').textContent = `${currentUser.fullName}`;
      document.getElementById('pin-field-wrap').classList.toggle('hidden', currentUser.role !== 'Administrator');

      const isAdmin = currentUser.role === 'Administrator';
      navAdminEl.classList.toggle('hidden', !isAdmin);
      adminSectionEl.classList.toggle('hidden', !isAdmin);
      expiredToggleWrapEl.classList.toggle('hidden', !isAdmin);

      accessSubtitleEl.textContent =
        currentUser.role === 'Student'
          ? `Signed in as ${currentUser.fullName}. Showing college-wide announcements and ${currentUser.department} department circulars.`
          : `Signed in as ${currentUser.fullName}.`;
    }

    renderDepartmentOptions();
    applyThemePreferences();
    renderNotices();
    if (currentUser && currentUser.role === 'Administrator') {
      renderAdminPanel();
    }
  }

  function renderNotices() {
    const notices = getFilteredNotices();
    const now = nowMs();
    headingCountEl.textContent = notices.length;
    statTotalEl.textContent = notices.length;
    statPinnedEl.textContent = notices.filter(n => n.isPinned).length;
    lastSyncTimeEl.textContent = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    if (notices.length === 0) {
      noticeListEl.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">⌕</div>
          <h3>No matching notices found</h3>
          <p>No announcements match your current search or department filters.</p>
          <button type="button" class="button button-secondary" id="btn-empty-reset">Clear filters</button>
        </div>`;
      const emptyReset = document.getElementById('btn-empty-reset');
      if (emptyReset) emptyReset.addEventListener('click', resetFilters);
      return;
    }

    const extraCardClass =
      cardStyle === 'outlined' ? 'card-outlined' : cardStyle === 'compact' ? 'card-compact' : '';

    noticeListEl.innerHTML = notices
      .map(n => {
        const isExpired = n.expiresAt && n.expiresAt > 0 && n.expiresAt < now;
        const canManage =
          currentUser &&
          (currentUser.role === 'Administrator' ||
            (currentUser.role === 'HOD' && n.department === currentUser.department) ||
            (currentUser.role === 'Faculty' && n.authorLoginId === currentUser.loginId));

        const cls =
          n.priority === 'HIGH'
            ? 'symbol-high'
            : n.category === 'Placements'
            ? 'symbol-career'
            : n.category === 'Examination'
            ? 'symbol-exam'
            : 'symbol-general';

        const attachmentsHtml = (n.attachments || [])
          .map(
            a => `
            <button type="button" class="attachment" data-preview-notice="${n.id}" data-preview-att="${a.id}">
              <span>↧</span>
              ${escapeHtml(a.filename)}
              <small>${(Number(a.size || 102400) / 1048576).toFixed(2)} MB</small>
            </button>`
          )
          .join('');

        const actionsHtml = canManage
          ? `<div class="notice-actions">
               <button class="icon-button edit-button" type="button" data-edit-id="${n.id}" title="Edit notice">✎</button>
               <button class="icon-button delete-button" type="button" data-delete-id="${n.id}" title="Delete notice">×</button>
             </div>`
          : '';

        return `
          <article class="notice-card ${extraCardClass}" data-notice-id="${n.id}">
            <div class="notice-symbol ${cls}">${icons[n.category] || '▤'}</div>
            <div class="notice-main">
              <div class="notice-meta">
                <span class="category-pill">${escapeHtml(n.category)}</span>
                ${n.priority === 'HIGH' ? '<span class="priority-pill">HIGH PRIORITY</span>' : ''}
                ${n.isPinned ? '<span class="pinned-pill">⌖ PINNED</span>' : ''}
                ${n.visibility === 'RESTRICTED' ? '<span class="restricted-pill">🔒 DEPT ONLY</span>' : ''}
                ${isExpired ? '<span class="expired-pill">EXPIRED</span>' : ''}
                <span class="notice-date">Published ${escapeHtml(formatDate(n.publishedAt))}</span>
              </div>
              <h3>${escapeHtml(n.title)}</h3>
              <p>${escapeHtml(n.description)}</p>
              ${attachmentsHtml ? `<div class="attachments">${attachmentsHtml}</div>` : ''}
              <div class="notice-foot">
                <span class="department-tag">${n.department === 'ALL' ? 'COLLEGE-WIDE' : 'DEPT: ' + escapeHtml(n.department)}</span>
                <span>Published by ${escapeHtml(n.authorName || 'Admin Principal')}</span>
                ${n.expiresAt ? `<span>· Valid until ${escapeHtml(formatDate(n.expiresAt))}</span>` : ''}
              </div>
            </div>
            ${actionsHtml}
          </article>`;
      })
      .join('');
  }

  function renderAdminPanel() {
    const userListEl = document.getElementById('admin-user-list');
    const deptListEl = document.getElementById('admin-dept-list');
    const countEl = document.getElementById('admin-user-count');
    const deptFilterEl = document.getElementById('admin-user-dept-filter');
    if (!userListEl || !deptListEl) return;

    const filterVal = deptFilterEl ? deptFilterEl.value : 'ALL';
    const filteredUsers = db.users.filter(
      u => filterVal === 'ALL' || u.department === filterVal
    );
    if (countEl) countEl.textContent = filteredUsers.length;

    userListEl.innerHTML = filteredUsers
      .map(
        u => `
        <div class="table-row">
          <div>
            <b>${escapeHtml(u.fullName)}</b> <small>(ID: <code>${escapeHtml(u.loginId)}</code>)</small><br>
            <span style="font-size:11px;color:#667085;">Role: ${escapeHtml(u.role)} · Dept: ${escapeHtml(u.department)} · ${u.isActive ? 'Active' : 'Deactivated'}</span>
          </div>
          <div style="display:flex;gap:6px;">
            ${
              u.loginId !== 'principal' && u.loginId !== 'admin'
                ? `<button type="button" class="role-pill" data-toggle-user="${escapeHtml(u.loginId)}">${u.isActive ? 'Deactivate' : 'Activate'}</button>`
                : '<span class="demo-badge">Admin Principal</span>'
            }
          </div>
        </div>`
      )
      .join('');

    deptListEl.innerHTML = db.departments
      .map(
        d => `
        <div class="table-row">
          <div>
            <b>${escapeHtml(d.code)}</b> — ${escapeHtml(d.name)}
          </div>
          <button type="button" class="role-pill" data-toggle-dept="${escapeHtml(d.code)}">${d.isActive ? 'Active ✓' : 'Disabled'}</button>
        </div>`
      )
      .join('');
  }

  const adminDeptFilterEl = document.getElementById('admin-user-dept-filter');
  if (adminDeptFilterEl) {
    adminDeptFilterEl.addEventListener('change', renderAdminPanel);
  }

  function resetFilters() {
    selectedDept = 'ALL';
    selectedCategory = 'ALL';
    selectedPriority = 'ALL';
    searchQuery = '';
    showExpired = false;
    filterDeptSelect.value = 'ALL';
    filterCategorySelect.value = 'ALL';
    filterPrioritySelect.value = 'ALL';
    filterQInput.value = '';
    if (filterExpiredCheckbox) filterExpiredCheckbox.checked = false;
    renderDepartmentOptions();
    renderNotices();
  }

  // Event Listeners: Department Quick Tabs
  deptQuickTabsEl.addEventListener('click', e => {
    const btn = e.target.closest('[data-dept]');
    if (!btn) return;
    selectedDept = btn.getAttribute('data-dept') || 'ALL';
    filterDeptSelect.value = selectedDept;
    renderDepartmentOptions();
    renderNotices();
  });

  // Filter Inputs
  filterQInput.addEventListener('input', e => {
    searchQuery = e.target.value;
    renderNotices();
  });
  filterDeptSelect.addEventListener('change', e => {
    selectedDept = e.target.value;
    renderDepartmentOptions();
    renderNotices();
  });
  filterCategorySelect.addEventListener('change', e => {
    selectedCategory = e.target.value;
    renderNotices();
  });
  filterPrioritySelect.addEventListener('change', e => {
    selectedPriority = e.target.value;
    renderNotices();
  });
  if (filterExpiredCheckbox) {
    filterExpiredCheckbox.addEventListener('change', e => {
      showExpired = e.target.checked;
      renderNotices();
    });
  }
  document.getElementById('btn-clear-filters').addEventListener('click', resetFilters);
  document.getElementById('btn-manual-refresh').addEventListener('click', () => {
    syncFromCloud();
  });
  const btnSectionRefresh = document.getElementById('btn-section-refresh');
  if (btnSectionRefresh) {
    btnSectionRefresh.addEventListener('click', () => {
      syncFromCloud();
    });
  }

  // Notice Card Actions (Attachment Preview, Edit, Delete)
  noticeListEl.addEventListener('click', async e => {
    const previewBtn = e.target.closest('[data-preview-notice]');
    if (previewBtn) {
      const noticeId = Number(previewBtn.getAttribute('data-preview-notice'));
      const attId = Number(previewBtn.getAttribute('data-preview-att'));
      const notice = db.notices.find(n => n.id === noticeId);
      const att = notice && (notice.attachments || []).find(a => a.id === attId);
      if (att) openAttachmentPreview(att);
      return;
    }

    const editBtn = e.target.closest('[data-edit-id]');
    if (editBtn) {
      const id = Number(editBtn.getAttribute('data-edit-id'));
      const n = db.notices.find(item => item.id === id);
      if (!n) return;
      editNoticeIdInput.value = String(n.id);
      noticeTitleInput.value = n.title;
      noticeDescInput.value = n.description;
      noticeDeptSelect.value = n.department;
      noticeCategorySelect.value = n.category;
      noticePrioritySelect.value = n.priority;
      noticeVisibilitySelect.value = n.visibility;
      noticePinnedCheckbox.checked = Boolean(n.isPinned);
      formSectionTitle.textContent = 'Edit notice';
      btnSubmitNotice.textContent = 'Save changes →';
      btnCancelEdit.classList.remove('hidden');
      publishSectionEl.scrollIntoView({ behavior: 'smooth' });
      return;
    }

    const deleteBtn = e.target.closest('[data-delete-id]');
    if (deleteBtn) {
      const id = Number(deleteBtn.getAttribute('data-delete-id'));
      if (!window.confirm('Delete this notice and its attachments permanently?')) return;
      db.deletedIds = db.deletedIds || [];
      if (!db.deletedIds.includes(id)) db.deletedIds.push(id);
      db.notices = db.notices.filter(item => Number(item.id) !== id);
      saveDb(db);
      renderNotices();
      await publishCloudEvent({ action: 'DELETE', noticeId: id });
    }
  });

  btnCancelEdit.addEventListener('click', () => {
    noticeForm.reset();
    editNoticeIdInput.value = '';
    formSectionTitle.textContent = 'Publish a notice';
    btnSubmitNotice.textContent = 'Publish notice →';
    btnCancelEdit.classList.add('hidden');
  });

  // Publish or Update Notice (with immediate Cloud Sync so other phones receive it on refresh)
  noticeForm.addEventListener('submit', async e => {
    e.preventDefault();
    if (!currentUser || !isPublisherRole(currentUser.role)) {
      window.alert('Only authorized Admin Principal, HOD, and Faculty accounts can publish notices.');
      return;
    }

    const title = noticeTitleInput.value.trim();
    const description = noticeDescInput.value.trim();
    if (!title || !description) return;

    const dept =
      currentUser.role === 'Administrator' ? noticeDeptSelect.value || 'ALL' : currentUser.department;

    const files = Array.from(noticeFilesInput.files || []);
    const uploadedAttachments = [];
    for (const file of files) {
      if (file.size > 10 * 1024 * 1024) {
        window.alert(`File ${file.name} exceeds the 10 MB limit.`);
        return;
      }
      const dataUrl = await new Promise(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = () => resolve('');
        reader.readAsDataURL(file);
      });
      const ext = file.name.split('.').pop().toUpperCase();
      uploadedAttachments.push({
        id: Date.now() + Math.floor(Math.random() * 1000),
        filename: file.name,
        fileType: ['JPG', 'JPEG', 'PNG'].includes(ext) ? 'IMAGE' : ext,
        size: file.size,
        previewText: `Attached Circular: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`,
        dataUrl
      });
    }

    const expiryVal = noticeExpiryInput.value ? new Date(noticeExpiryInput.value).getTime() : 0;
    const editId = Number(editNoticeIdInput.value);
    let savedNotice = null;

    if (editId) {
      const idx = db.notices.findIndex(n => Number(n.id) === editId);
      if (idx !== -1) {
        db.notices[idx] = {
          ...db.notices[idx],
          title,
          description,
          department: dept,
          category: noticeCategorySelect.value,
          priority: noticePrioritySelect.value,
          expiresAt: expiryVal || db.notices[idx].expiresAt,
          visibility: noticeVisibilitySelect.value,
          isPinned: currentUser.role === 'Administrator' ? noticePinnedCheckbox.checked : db.notices[idx].isPinned,
          attachments: uploadedAttachments.length
            ? [...(db.notices[idx].attachments || []), ...uploadedAttachments]
            : db.notices[idx].attachments
        };
        savedNotice = db.notices[idx];
      }
    } else {
      savedNotice = {
        id: Date.now(),
        title,
        description,
        department: dept,
        category: noticeCategorySelect.value,
        priority: noticePrioritySelect.value,
        authorLoginId: currentUser.loginId,
        authorName: currentUser.fullName,
        publishedAt: nowMs(),
        expiresAt: expiryVal,
        isPinned: currentUser.role === 'Administrator' ? noticePinnedCheckbox.checked : false,
        visibility: noticeVisibilitySelect.value,
        attachments: uploadedAttachments
      };
      db.notices.unshift(savedNotice);
    }

    saveDb(db);
    noticeForm.reset();
    editNoticeIdInput.value = '';
    formSectionTitle.textContent = 'Publish a notice';
    btnSubmitNotice.textContent = 'Publish notice →';
    btnCancelEdit.classList.add('hidden');
    renderNotices();
    document.getElementById('notices').scrollIntoView({ behavior: 'smooth' });

    if (savedNotice) {
      await publishCloudEvent({ action: 'UPSERT', notice: savedNotice });
    }
  });

  // Attachment Preview Modal
  const previewModal = document.getElementById('preview-modal');
  function openAttachmentPreview(att) {
    document.getElementById('preview-title').textContent = att.filename;
    const bodyEl = document.getElementById('preview-body');
    const downloadBtn = document.getElementById('preview-download-btn');

    if (att.fileType === 'IMAGE' && att.dataUrl && att.dataUrl.startsWith('data:image')) {
      bodyEl.innerHTML = `<img src="${att.dataUrl}" alt="${escapeHtml(att.filename)}" style="max-width:100%;border-radius:12px;">`;
    } else {
      bodyEl.innerHTML = `
        <div style="background:#f8fafc;color:#1e293b;padding:14px;border-radius:10px;border:1px solid #e2e8f0;white-space:pre-line;font-family:monospace;font-size:12px;">
          ${escapeHtml(att.previewText || 'Official GCE Salem Circular Attachment')}
        </div>`;
    }

    downloadBtn.setAttribute('download', att.filename);
    downloadBtn.setAttribute(
      'href',
      att.dataUrl || createSampleDataUrl(att.filename, att.previewText || att.filename)
    );
    previewModal.classList.add('open');
  }
  document.getElementById('btn-close-preview').addEventListener('click', () => {
    previewModal.classList.remove('open');
  });

  // Login Modal (Manual Authentication Only)
  const loginModal = document.getElementById('login-modal');
  const loginErrorEl = document.getElementById('login-error');

  btnOpenLogin.addEventListener('click', () => {
    loginErrorEl.classList.add('hidden');
    document.getElementById('login-id-input').value = '';
    document.getElementById('login-pass-input').value = '';
    loginModal.classList.add('open');
  });
  document.getElementById('btn-close-login').addEventListener('click', () => {
    loginModal.classList.remove('open');
  });
  btnLogout.addEventListener('click', () => {
    currentUser = null;
    localStorage.removeItem(SESSION_KEY);
    renderAuthUI();
  });

  document.getElementById('login-form').addEventListener('submit', e => {
    e.preventDefault();
    const id = document.getElementById('login-id-input').value.trim().toLowerCase();
    const pass = document.getElementById('login-pass-input').value;
    const user = db.users.find(u => u.loginId.toLowerCase() === id && u.password === pass);
    if (!user) {
      loginErrorEl.textContent = 'Invalid Login ID or password.';
      loginErrorEl.classList.remove('hidden');
      return;
    }
    if (!user.isActive) {
      loginErrorEl.textContent = 'This account has been deactivated by the Admin Principal.';
      loginErrorEl.classList.remove('hidden');
      return;
    }
    currentUser = user;
    localStorage.setItem(SESSION_KEY, user.loginId);
    loginModal.classList.remove('open');
    renderAuthUI();
  });

  // Admin Console Handlers
  document.getElementById('create-user-form').addEventListener('submit', e => {
    e.preventDefault();
    const loginId = document.getElementById('new-user-login').value.trim().toLowerCase();
    const password = document.getElementById('new-user-pass').value;
    const fullName = document.getElementById('new-user-name').value.trim();
    const role = document.getElementById('new-user-role').value;
    const department = document.getElementById('new-user-dept').value;

    if (db.users.some(u => u.loginId.toLowerCase() === loginId)) {
      window.alert('Login ID already exists.');
      return;
    }
    db.users.push({ loginId, password, fullName, role, department, isActive: true });
    saveDb(db);
    e.target.reset();
    renderAdminPanel();
  });

  document.getElementById('create-dept-form').addEventListener('submit', e => {
    e.preventDefault();
    const code = document.getElementById('new-dept-code').value.trim().toUpperCase();
    const name = document.getElementById('new-dept-name').value.trim();
    if (db.departments.some(d => d.code === code)) {
      window.alert('Department code already exists.');
      return;
    }
    db.departments.push({ code, name, isActive: true });
    saveDb(db);
    e.target.reset();
    renderDepartmentOptions();
    renderAdminPanel();
  });

  document.getElementById('admin-panel').addEventListener('click', e => {
    const toggleUserBtn = e.target.closest('[data-toggle-user]');
    if (toggleUserBtn) {
      const loginId = toggleUserBtn.getAttribute('data-toggle-user');
      const user = db.users.find(u => u.loginId === loginId);
      if (user && user.loginId !== 'principal' && user.loginId !== 'admin') {
        user.isActive = !user.isActive;
        saveDb(db);
        renderAdminPanel();
      }
      return;
    }
    const toggleDeptBtn = e.target.closest('[data-toggle-dept]');
    if (toggleDeptBtn) {
      const code = toggleDeptBtn.getAttribute('data-toggle-dept');
      const dept = db.departments.find(d => d.code === code);
      if (dept) {
        dept.isActive = !dept.isActive;
        saveDb(db);
        renderDepartmentOptions();
        renderAdminPanel();
      }
    }
  });

  document.getElementById('btn-reset-demo-data').addEventListener('click', () => {
    if (!window.confirm('Reset all accounts and departments to default GCE Salem roles?')) return;
    db = defaultDatabase();
    saveDb(db);
    renderAuthUI();
    syncFromCloud();
  });

  // Theme Controls
  [themeModeSelect, themeAccentInput, themeBgInput, themeFontSelect, themeSizeSelect, themeCardStyleSelect].forEach(el => {
    if (el) el.addEventListener('change', saveCurrentTheme);
  });

  document.getElementById('btn-reset-theme').addEventListener('click', () => {
    const key = currentUser ? currentUser.loginId : 'guest';
    if (db.preferences) delete db.preferences[key];
    saveDb(db);
    applyThemePreferences();
    renderNotices();
  });

  // Live 15-Second Automatic Cloud Polling
  window.setInterval(() => {
    if (document.hidden) return;
    syncFromCloud();
  }, 15000);

  window.addEventListener('storage', () => {
    db = loadDb();
    renderAuthUI();
  });

  // Initial Boot
  renderAuthUI();
  syncFromCloud();
  startRealtimeStream();
})();
