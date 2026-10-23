(() => {
  const STORAGE_KEY = 'gce_salem_notice_board_db_v4';
  const SESSION_KEY = 'gce_salem_current_user_v4';
  const CLOUD_SYNC_TOPIC = 'gce_salem_notice_board_sync_v4';
  const CLOUD_SYNC_URL = `https://ntfy.sh/${CLOUD_SYNC_TOPIC}`;
  const CLOUD_FILES_TOPIC = 'gce_salem_notice_files_v4';
  const CLOUD_FILES_URL = `https://ntfy.sh/${CLOUD_FILES_TOPIC}`;
  const CLOUD_BLOBS_TOPIC = 'gce_salem_notice_blobs_v4';
  const CLOUD_BLOBS_URL = `https://ntfy.sh/${CLOUD_BLOBS_TOPIC}`;

  const cloudBlobChunks = {}; // attId -> { total, parts: {}, count }
  const cloudBlobComplete = {}; // attId -> full dataUrl string
  const cloudFilesByMeta = []; // [{ name, baseKey, size, url }]
  const processedBlobMsgIds = new Set();

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

  const FAKE_DEMO_SIZES = new Set([1420500, 582400, 420500, 1890000, 345000, 2140000, 720000, 1850000, 340000, 102400, 52]);

  function decodedBase64ByteLength(dataUrl) {
    if (!dataUrl || !dataUrl.includes(';base64,')) return 0;
    const b64 = dataUrl.split(';base64,')[1] || '';
    if (!b64) return 0;
    const padding = b64.endsWith('==') ? 2 : b64.endsWith('=') ? 1 : 0;
    return Math.max(0, Math.floor((b64.length * 3) / 4) - padding);
  }

  function getRealAttachmentSize(att) {
    if (!att) return 0;
    const rawSize = Number(att.size || 0);
    if (rawSize > 0 && !FAKE_DEMO_SIZES.has(rawSize)) {
      return rawSize;
    }
    const fullData = cloudBlobComplete[att.id] || (att.isFullQuality ? att.dataUrl : '');
    const b64Bytes = decodedBase64ByteLength(fullData);
    if (b64Bytes > 0) return b64Bytes;
    try {
      const blob = buildDownloadBlob(att);
      if (blob && blob.size > 0) return blob.size;
    } catch (_) {}
    return rawSize > 0 ? rawSize : 0;
  }

  function formatFileSize(bytes) {
    const b = Math.max(0, Math.round(Number(bytes || 0)));
    if (b < 1024) return `${b} B`;
    if (b < 1048576) {
      const kb = (b / 1024).toFixed(1).replace(/\.0$/, '');
      return `${kb} KB`;
    }
    return `${(b / 1048576).toFixed(2).replace(/\.00$/, '')} MB`;
  }

  function normalizeBaseFilename(name) {
    return String(name || '')
      .toLowerCase()
      .replace(/\.[a-z0-9]+$/i, '')
      .replace(/\(\d+\)|__\d+_/g, '')
      .replace(/[^a-z0-9]/g, '');
  }

  function isImageFilenameOrType(filename, fileType) {
    const ext = String(filename || '').split('.').pop().toLowerCase();
    return (
      fileType === 'IMAGE' ||
      ['jpg', 'jpeg', 'png', 'webp', 'gif', 'svg', 'bmp'].includes(ext)
    );
  }

  function normalizeAttachmentDataUrl(dataUrl, filename, fileType) {
    const raw = String(dataUrl || '');
    if (!raw || !raw.includes(';base64,')) return '';
    if (isImageFilenameOrType(filename, fileType) && !raw.startsWith('data:image/')) {
      const ext = String(filename || '').split('.').pop().toLowerCase();
      const mime =
        ext === 'png'
          ? 'image/png'
          : ext === 'webp'
          ? 'image/webp'
          : ext === 'gif'
          ? 'image/gif'
          : ext === 'svg'
          ? 'image/svg+xml'
          : 'image/jpeg';
      return `data:${mime};base64,${raw.split(';base64,')[1]}`;
    }
    return raw;
  }

  function createSampleDataUrl(filename, content) {
    return 'data:text/plain;charset=utf-8,' + encodeURIComponent(content);
  }

  function createValidPdfBytes(title, bodyText) {
    const clean = str => String(str || '').replace(/[()\\]/g, '').replace(/[^\x20-\x7E]/g, ' ');
    const lines = [
      'GOVERNMENT COLLEGE OF ENGINEERING, SALEM - 636011',
      'OFFICIAL DIGITAL NOTICE BOARD ATTACHMENT',
      '------------------------------------------------------------',
      `Document: ${clean(title)}`,
      `Generated: ${new Date().toLocaleString()}`,
      '------------------------------------------------------------',
      ...String(bodyText || 'Official College Circular').split(/\r?\n/).map(clean)
    ];
    let y = 760;
    const ops = ['BT', '/F1 11 Tf'];
    lines.slice(0, 38).forEach(line => {
      ops.push(`1 0 0 1 50 ${y} Tm (${line.slice(0, 85)}) Tj`);
      y -= 18;
    });
    ops.push('ET');
    const stream = ops.join('\n');
    const pdf =
      '%PDF-1.4\n' +
      '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n' +
      '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n' +
      '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n' +
      `4 0 obj << /Length ${stream.length} >> stream\n${stream}\nendstream endobj\n` +
      '5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n' +
      'trailer << /Root 1 0 R >>\n%%EOF';
    return new TextEncoder().encode(pdf);
  }

  function buildDownloadBlob(att) {
    const filename = String((att && att.filename) || 'GCE_Salem_Circular.pdf');
    const ext = filename.split('.').pop().toLowerCase();
    const rawDataUrl = String((att && (cloudBlobComplete[att.id] || att.dataUrl)) || '');
    const dataUrl = normalizeAttachmentDataUrl(rawDataUrl, filename, att && att.fileType);

    // If a real uploaded file (base64 data URL) is stored, decode exact binary bytes
    if (dataUrl.startsWith('data:') && dataUrl.includes(';base64,')) {
      try {
        const parts = dataUrl.split(';base64,');
        const mime = parts[0].slice(5) || 'application/octet-stream';
        const binary = window.atob(parts[1]);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        return new Blob([bytes], { type: mime });
      } catch (_) {}
    }

    const preview = String((att && att.previewText) || `Official GCE Salem Attachment: ${filename}`);
    if (ext === 'pdf') {
      return new Blob([createValidPdfBytes(filename, preview)], { type: 'application/pdf' });
    }
    if (ext === 'xls' || ext === 'xlsx' || ext === 'csv') {
      const csvContent = preview.includes(',')
        ? preview
        : `Document,Department,Details\n"${filename.replace(/"/g, '""')}","GCE Salem","${preview.replace(/\r?\n/g, ' ').replace(/"/g, '""')}"`;
      return new Blob([csvContent], { type: 'text/csv;charset=utf-8' });
    }
    if (rawDataUrl.startsWith('data:text/plain;charset=utf-8,')) {
      const decoded = decodeURIComponent(rawDataUrl.slice('data:text/plain;charset=utf-8,'.length));
      if (ext === 'pdf') {
        return new Blob([createValidPdfBytes(filename, decoded)], { type: 'application/pdf' });
      }
      return new Blob([decoded], { type: 'text/plain;charset=utf-8' });
    }
    return new Blob([preview], { type: 'text/plain;charset=utf-8' });
  }

  function findSiblingOrIndexedMatch(att) {
    if (!att) return null;
    const targetBase = normalizeBaseFilename(att.filename);
    const targetSize = Number(att.size || 0);

    // 1. Check other notices in db for an attachment with the same file size or base filename that has remoteUrl or full dataUrl
    for (const n of db.notices || []) {
      for (const other of n.attachments || []) {
        if (!other || other === att) continue;
        const sameSize = targetSize > 0 && Number(other.size) === targetSize;
        const sameName = targetBase && normalizeBaseFilename(other.filename) === targetBase;
        if (sameSize || sameName) {
          const validSize = Number(other.size || 0) > 0 && !FAKE_DEMO_SIZES.has(Number(other.size)) ? Number(other.size) : 0;
          if (cloudBlobComplete[other.id]) {
            return { dataUrl: cloudBlobComplete[other.id], remoteUrl: other.remoteUrl || '', size: validSize };
          }
          if (other.remoteUrl) {
            return { dataUrl: other.dataUrl || '', remoteUrl: other.remoteUrl, size: validSize };
          }
          if (other.dataUrl && other.dataUrl.includes(';base64,') && other.dataUrl.length > (att.dataUrl || '').length) {
            return { dataUrl: other.dataUrl, remoteUrl: other.remoteUrl || '', size: validSize };
          }
        }
      }
    }

    // 2. Check cloudFilesByMeta indexed from CLOUD_FILES_URL
    for (const item of cloudFilesByMeta) {
      const sameSize = targetSize > 0 && item.size === targetSize;
      const sameName = targetBase && item.baseKey === targetBase;
      if ((sameSize || sameName) && item.url) {
        return { dataUrl: '', remoteUrl: item.url, size: item.size || 0 };
      }
    }
    return null;
  }

  async function ensureAttachmentHydrated(att) {
    if (!att) return att;
    att.dataUrl = normalizeAttachmentDataUrl(att.dataUrl, att.filename, att.fileType);

    if (cloudBlobComplete[att.id]) {
      att.dataUrl = normalizeAttachmentDataUrl(cloudBlobComplete[att.id], att.filename, att.fileType);
      att.isFullQuality = true;
      saveDb(db);
      return att;
    }

    if (att.isFullQuality && att.dataUrl && att.dataUrl.includes(';base64,') && att.dataUrl.length > 4000) {
      return att;
    }

    // Pull any recent blobs and files index from cloud
    await syncCloudBlobs();

    if (cloudBlobComplete[att.id]) {
      att.dataUrl = normalizeAttachmentDataUrl(cloudBlobComplete[att.id], att.filename, att.fileType);
      att.isFullQuality = true;
      saveDb(db);
      return att;
    }

    // Check if a sibling attachment or indexed file matches by size or filename
    if (!att.remoteUrl) {
      const match = findSiblingOrIndexedMatch(att);
      if (match) {
        if (match.remoteUrl) att.remoteUrl = match.remoteUrl;
        if (match.size && match.size > 0 && !FAKE_DEMO_SIZES.has(match.size)) {
          att.size = match.size;
        }
        if (match.dataUrl && match.dataUrl.length > (att.dataUrl || '').length) {
          att.dataUrl = normalizeAttachmentDataUrl(match.dataUrl, att.filename, att.fileType);
        }
      }
    }

    // If remoteUrl is available from ntfy file upload, fetch binary and convert to dataUrl
    if (att.remoteUrl) {
      try {
        const resp = await fetch(att.remoteUrl);
        if (resp.ok) {
          const blob = await resp.blob();
          if (blob && blob.size > 0) {
            att.size = blob.size;
          }
          const fetchedDataUrl = await new Promise(resolve => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result || ''));
            reader.onerror = () => resolve('');
            reader.readAsDataURL(blob);
          });
          const normalized = normalizeAttachmentDataUrl(fetchedDataUrl, att.filename, att.fileType);
          if (normalized && normalized.includes(';base64,')) {
            att.dataUrl = normalized;
            att.isFullQuality = true;
            cloudBlobComplete[att.id] = normalized;
            saveDb(db);
            return att;
          }
        }
      } catch (_) {}
    }
    return att;
  }

  async function triggerAttachmentDownload(att) {
    if (!att) return;
    await ensureAttachmentHydrated(att);
    const filename = String(att.filename || 'GCE_Salem_Notice_Attachment.pdf');
    const blob = buildDownloadBlob(att);
    const blobUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    setTimeout(() => {
      document.body.removeChild(link);
      URL.revokeObjectURL(blobUrl);
    }, 2000);
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
              size: 0,
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
              size: 0,
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
              filename: 'SIH_2026_Internal_Evaluation_Circular.pdf',
              fileType: 'PDF',
              size: 0,
              previewText: 'SMART INDIA HACKATHON (SIH 2026) — INTERNAL EVALUATION\nVenue: CSE Seminar Hall 1\nEligibility: 6-member student teams across CSE & AIML.',
              dataUrl: createSampleDataUrl('SIH_2026_Internal_Evaluation_Circular.pdf', 'SIH 2026 Internal Hackathon Circular — Venue: CSE Seminar Hall')
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
              size: 0,
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
      const parsed = raw ? JSON.parse(raw) : defaultDatabase();
      parsed.deletedIds = parsed.deletedIds || [];
      // Clean up any stale text/plain placeholder dataUrls and compute exact real byte sizes
      (parsed.notices || []).forEach(n => {
        if (n.id === 103 && n.attachments && n.attachments[0] && n.attachments[0].filename === 'SIH_2026_Campus_Poster.png') {
          n.attachments[0] = {
            id: 3,
            filename: 'SIH_2026_Internal_Evaluation_Circular.pdf',
            fileType: 'PDF',
            size: 0,
            previewText: 'SMART INDIA HACKATHON (SIH 2026) — INTERNAL EVALUATION\nVenue: CSE Seminar Hall 1\nEligibility: 6-member student teams across CSE & AIML.',
            dataUrl: ''
          };
        }
        (n.attachments || []).forEach(a => {
          if (isImageFilenameOrType(a.filename, a.fileType) && a.dataUrl && !a.dataUrl.includes(';base64,')) {
            a.dataUrl = '';
          } else if (a.dataUrl && a.dataUrl.includes(';base64,')) {
            a.dataUrl = normalizeAttachmentDataUrl(a.dataUrl, a.filename, a.fileType);
          }
          a.size = getRealAttachmentSize(a);
        });
      });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed));
      return parsed;
    } catch (_) {
      return defaultDatabase();
    }
  }

  function saveDb(db) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch (_) {
      // If localStorage quota is exceeded, trim large base64 strings on older notices to their compact inlineDataUrl
      try {
        (db.notices || []).slice(2).forEach(n => {
          (n.attachments || []).forEach(a => {
            if (a.dataUrl && a.dataUrl.length > 40000) {
              cloudBlobComplete[a.id] = a.dataUrl;
              a.dataUrl = a.inlineDataUrl || '';
              a.isFullQuality = false;
            }
          });
        });
        localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
      } catch (_2) {}
    }
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
        attachments: (incoming.attachments || []).map(a => {
          const attId = Number(a.id || Date.now());
          const filename = String(a.filename || 'Circular.pdf');
          const fileType = String(a.fileType || 'PDF');
          const fullBlob = cloudBlobComplete[attId] || '';
          const incomingDataUrl = normalizeAttachmentDataUrl(a.dataUrl || '', filename, fileType);
          const bestDataUrl = fullBlob
            ? normalizeAttachmentDataUrl(fullBlob, filename, fileType)
            : incomingDataUrl;
          const rawIncomingSize = Number(a.size || 0);
          const attRecord = {
            id: attId,
            filename,
            fileType,
            size: FAKE_DEMO_SIZES.has(rawIncomingSize) ? 0 : rawIncomingSize,
            previewText: String(a.previewText || 'Official GCE Salem Circular'),
            remoteUrl: String(a.remoteUrl || ''),
            inlineDataUrl: incomingDataUrl && incomingDataUrl.length <= 3600 ? incomingDataUrl : '',
            isFullQuality: Boolean(fullBlob || (incomingDataUrl && incomingDataUrl.length > 4000)),
            dataUrl: bestDataUrl
          };
          attRecord.size = getRealAttachmentSize(attRecord);
          return attRecord;
        })
      };

      if (idx === -1) {
        db.notices.unshift(normalized);
        return true;
      } else {
        const existingAtts = db.notices[idx].attachments || [];
        if (existingAtts.length) {
          normalized.attachments = (normalized.attachments || []).map(att => {
            const match = existingAtts.find(e => e.id === att.id || e.filename === att.filename);
            if (match) {
              const existingUrl = normalizeAttachmentDataUrl(match.dataUrl || '', att.filename, att.fileType);
              const newUrl = normalizeAttachmentDataUrl(att.dataUrl || '', att.filename, att.fileType);
              const bestUrl = existingUrl.length >= newUrl.length ? existingUrl : newUrl;
              return {
                ...att,
                dataUrl: bestUrl,
                inlineDataUrl: att.inlineDataUrl || match.inlineDataUrl || '',
                isFullQuality: Boolean(match.isFullQuality || att.isFullQuality || bestUrl.length > 4000),
                remoteUrl: att.remoteUrl || match.remoteUrl || ''
              };
            }
            return att;
          });
        }
        db.notices[idx] = normalized;
        return true;
      }
    }

    if (payload.action === 'PASSWORD_RESET' && payload.loginId && payload.newPassword) {
      const user = db.users.find(u => u.loginId.toLowerCase() === String(payload.loginId).toLowerCase());
      if (user && user.password !== String(payload.newPassword)) {
        user.password = String(payload.newPassword);
        return true;
      }
    }
    return false;
  }

  function applyFullBlobToNotices(attId, fullDataUrl) {
    if (!attId || !fullDataUrl || !fullDataUrl.startsWith('data:')) return false;
    cloudBlobComplete[attId] = fullDataUrl;
    let updated = false;
    db.notices.forEach(n => {
      (n.attachments || []).forEach(a => {
        if (Number(a.id) === Number(attId)) {
          const norm = normalizeAttachmentDataUrl(fullDataUrl, a.filename, a.fileType);
          if (norm && norm.length > (a.dataUrl || '').length) {
            a.dataUrl = norm;
            a.isFullQuality = true;
            updated = true;
          }
        }
      });
    });
    return updated;
  }

  function applyBlobChunkPayload(payload) {
    if (!payload) return false;
    if (payload.action === 'FILE_FULL' && payload.attId && payload.dataUrl) {
      return applyFullBlobToNotices(Number(payload.attId), String(payload.dataUrl));
    }
    if (payload.action !== 'FILE_CHUNK' || !payload.attId) return false;
    const attId = Number(payload.attId);
    const total = Number(payload.total || 1);
    const idx = Number(payload.idx || 0);
    const chunk = String(payload.chunk || '');
    if (!cloudBlobChunks[attId]) {
      cloudBlobChunks[attId] = { total, parts: {}, count: 0 };
    }
    const entry = cloudBlobChunks[attId];
    if (entry.parts[idx] === undefined) {
      entry.parts[idx] = chunk;
      entry.count += 1;
    }
    if (entry.count >= entry.total) {
      let full = '';
      for (let i = 0; i < entry.total; i++) {
        full += entry.parts[i] || '';
      }
      if (full.startsWith('data:')) {
        return applyFullBlobToNotices(attId, full);
      }
    }
    return false;
  }

  async function syncCloudFilesIndex() {
    try {
      const resp = await fetch(`${CLOUD_FILES_URL}/json?poll=1&since=24h&_t=${Date.now()}`, {
        cache: 'no-store'
      });
      if (!resp.ok) return false;
      const text = await resp.text();
      const lines = text.trim().split('\n').filter(Boolean);
      let updated = false;
      for (const line of lines) {
        try {
          const env = JSON.parse(line);
          if (env.event === 'message' && env.attachment && env.attachment.url) {
            const name = String(env.attachment.name || '');
            const size = Number(env.attachment.size || 0);
            const url = String(env.attachment.url);
            const baseKey = normalizeBaseFilename(name);
            if (!cloudFilesByMeta.some(x => x.url === url)) {
              cloudFilesByMeta.push({ name, baseKey, size, url });
            }
            // Link any notice attachment missing remoteUrl that matches size or baseKey
            db.notices.forEach(n => {
              (n.attachments || []).forEach(a => {
                const sameSize = size > 0 && Number(a.size) === size;
                const sameName = baseKey && normalizeBaseFilename(a.filename) === baseKey;
                if (sameSize || sameName) {
                  if (!a.remoteUrl) {
                    a.remoteUrl = url;
                    updated = true;
                  }
                  if (size > 0 && Number(a.size) !== size) {
                    a.size = size;
                    updated = true;
                  }
                }
              });
            });
          }
        } catch (_) {}
      }
      return updated;
    } catch (_) {
      return false;
    }
  }

  async function syncCloudBlobs() {
    let updated = false;
    try {
      if (await syncCloudFilesIndex()) updated = true;
      const resp = await fetch(`${CLOUD_BLOBS_URL}/json?poll=1&since=24h&_t=${Date.now()}`, {
        cache: 'no-store'
      });
      if (!resp.ok) return updated;
      const text = await resp.text();
      const lines = text.trim().split('\n').filter(Boolean);
      for (const line of lines) {
        try {
          const env = JSON.parse(line);
          if (env.event !== 'message') continue;
          if (env.attachment && env.attachment.url) {
            if (!processedBlobMsgIds.has(env.id)) {
              processedBlobMsgIds.add(env.id);
              try {
                const attResp = await fetch(env.attachment.url);
                if (attResp.ok) {
                  const payload = await attResp.json();
                  if (applyBlobChunkPayload(payload)) updated = true;
                }
              } catch (_) {}
            }
          } else if (env.message) {
            const payload = JSON.parse(env.message);
            if (applyBlobChunkPayload(payload)) updated = true;
          }
        } catch (_) {}
      }
      if (updated) saveDb(db);
      return updated;
    } catch (_) {
      return updated;
    }
  }

  async function createResizedImageDataUrl(dataUrl, maxDim, quality) {
    if (!dataUrl || !dataUrl.startsWith('data:image/')) {
      return dataUrl;
    }
    return new Promise(resolve => {
      const img = new Image();
      img.onload = () => {
        try {
          let w = img.width || 600;
          let h = img.height || 400;
          if (w > maxDim || h > maxDim) {
            if (w > h) {
              h = Math.max(1, Math.round((h * maxDim) / w));
              w = maxDim;
            } else {
              w = Math.max(1, Math.round((w * maxDim) / h));
              h = maxDim;
            }
          }
          const c = document.createElement('canvas');
          c.width = w;
          c.height = h;
          const ctx = c.getContext('2d');
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(0, 0, w, h);
          ctx.drawImage(img, 0, 0, w, h);
          const out = c.toDataURL('image/jpeg', quality);
          resolve(out);
        } catch (_) {
          resolve(dataUrl);
        }
      };
      img.onerror = () => resolve(dataUrl);
      img.src = dataUrl;
    });
  }

  async function uploadAttachmentToCloud(att, rawFile) {
    if (!att) return;
    const safeName = String(att.filename || 'attachment.bin').replace(/[^a-zA-Z0-9._-]/g, '_');

    // Run direct binary PUT to CLOUD_FILES_URL and single-POST FILE_FULL to CLOUD_BLOBS_URL in parallel
    const putPromise = (async () => {
      try {
        const blobToUpload = rawFile || buildDownloadBlob(att);
        const putResp = await fetch(CLOUD_FILES_URL, {
          method: 'PUT',
          headers: { Filename: safeName },
          body: blobToUpload
        });
        if (putResp.ok) {
          const meta = await putResp.json();
          if (meta && meta.attachment && meta.attachment.url) {
            att.remoteUrl = meta.attachment.url;
          }
        }
      } catch (_) {}
    })();

    const postBlobPromise = (async () => {
      try {
        const syncUrl = att.dataUrl || '';
        if (syncUrl && syncUrl.startsWith('data:') && syncUrl.length <= 280000) {
          const resp = await fetch(CLOUD_BLOBS_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain;charset=utf-8' },
            body: JSON.stringify({
              action: 'FILE_FULL',
              attId: att.id,
              filename: att.filename,
              size: att.size,
              dataUrl: syncUrl
            })
          });
          if (resp.ok && !att.remoteUrl) {
            try {
              const meta = await resp.json();
              if (meta && meta.attachment && meta.attachment.url && !syncUrl.startsWith('data:')) {
                att.remoteUrl = meta.attachment.url;
              }
            } catch (_) {}
          }
        }
      } catch (_) {}
    })();

    await Promise.allSettled([putPromise, postBlobPromise]);
  }

  async function publishCloudEvent(eventObj) {
    try {
      const compactEvent = JSON.parse(JSON.stringify(eventObj));
      if (compactEvent.notice && compactEvent.notice.attachments) {
        compactEvent.notice.attachments = compactEvent.notice.attachments.map(a => {
          const inlineCandidate = a.inlineDataUrl || (a.dataUrl && String(a.dataUrl).length <= 2800 ? a.dataUrl : '');
          return {
            id: a.id,
            filename: a.filename,
            fileType: a.fileType,
            size: a.size,
            remoteUrl: a.remoteUrl || '',
            previewText: String(a.previewText || '').slice(0, 240),
            dataUrl: inlineCandidate && String(inlineCandidate).length <= 2800 ? inlineCandidate : undefined
          };
        });
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
      await syncCloudBlobs();
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
          if (env.event === 'message') {
            if (env.attachment && env.attachment.url) {
              try {
                const attResp = await fetch(env.attachment.url);
                if (attResp.ok) {
                  const attPayload = await attResp.json();
                  if (applyCloudPayload(attPayload)) changed = true;
                }
              } catch (_) {}
            } else if (env.message) {
              const payload = JSON.parse(env.message);
              if (applyCloudPayload(payload)) changed = true;
            }
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
      es.onmessage = async ev => {
        try {
          const env = JSON.parse(ev.data);
          if (env.event === 'message') {
            let payload = null;
            if (env.attachment && env.attachment.url) {
              const r = await fetch(env.attachment.url);
              if (r.ok) payload = await r.json();
            } else if (env.message) {
              payload = JSON.parse(env.message);
            }
            if (payload) {
              await syncCloudBlobs();
              if (applyCloudPayload(payload)) {
                saveDb(db);
                renderNotices();
              }
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

  const btnHeaderForgot = document.getElementById('btn-header-forgot');

  function renderAuthUI() {
    if (!currentUser) {
      userChipContainerEl.classList.add('hidden');
      btnOpenLogin.classList.remove('hidden');
      if (btnHeaderForgot) btnHeaderForgot.classList.remove('hidden');
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
      if (btnHeaderForgot) btnHeaderForgot.classList.add('hidden');
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
            <div class="attachment" style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;flex-wrap:wrap;max-width:100%;">
              <button type="button" data-download-notice="${n.id}" data-download-att="${a.id}" style="all:unset;cursor:pointer;display:inline-flex;align-items:center;gap:6px;font-weight:700;color:var(--accent);flex-wrap:wrap;" title="Download ${escapeHtml(a.filename)}">
                <span>⬇ Download</span>
                <span style="word-break:break-all;">${escapeHtml(a.filename)}</span>
                <small style="color:var(--muted);font-weight:500;">(${formatFileSize(getRealAttachmentSize(a))})</small>
              </button>
              <button type="button" data-preview-notice="${n.id}" data-preview-att="${a.id}" style="border:1px solid var(--line);background:var(--surface);color:var(--text);border-radius:6px;padding:3px 8px;font-size:11px;font-weight:600;cursor:pointer;" title="Preview attachment">
                Preview
              </button>
            </div>`
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

  // Notice Card Actions (Attachment Download, Preview, Edit, Delete)
  noticeListEl.addEventListener('click', async e => {
    const downloadBtn = e.target.closest('[data-download-notice]');
    if (downloadBtn) {
      const noticeId = Number(downloadBtn.getAttribute('data-download-notice'));
      const attId = Number(downloadBtn.getAttribute('data-download-att'));
      const notice = db.notices.find(n => Number(n.id) === noticeId);
      const att = notice && (notice.attachments || []).find(a => Number(a.id) === attId);
      if (att) triggerAttachmentDownload(att);
      return;
    }

    const previewBtn = e.target.closest('[data-preview-notice]');
    if (previewBtn) {
      const noticeId = Number(previewBtn.getAttribute('data-preview-notice'));
      const attId = Number(previewBtn.getAttribute('data-preview-att'));
      const notice = db.notices.find(n => Number(n.id) === noticeId);
      const att = notice && (notice.attachments || []).find(a => Number(a.id) === attId);
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
    const rawFileMap = {};
    btnSubmitNotice.disabled = true;
    btnSubmitNotice.textContent = files.length ? 'Uploading & publishing…' : 'Publishing…';

    try {
      for (const file of files) {
        if (file.size > 10 * 1024 * 1024) {
          window.alert(`File ${file.name} exceeds the 10 MB limit.`);
          btnSubmitNotice.disabled = false;
          btnSubmitNotice.textContent = 'Publish notice →';
          return;
        }
        const ext = file.name.split('.').pop().toUpperCase();
        const fileType = ['JPG', 'JPEG', 'PNG', 'WEBP', 'GIF', 'SVG', 'BMP'].includes(ext) ? 'IMAGE' : ext;

        const rawDataUrl = await new Promise(resolve => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result || ''));
          reader.onerror = () => resolve('');
          reader.readAsDataURL(file);
        });
        let dataUrl = normalizeAttachmentDataUrl(rawDataUrl, file.name, fileType);
        let inlineDataUrl = '';

        if (fileType === 'IMAGE' && dataUrl.startsWith('data:image/')) {
          inlineDataUrl = await createResizedImageDataUrl(dataUrl, 160, 0.46);
        }

        let snippet = `Attached Circular: ${file.name} (${formatFileSize(file.size)}) — Uploaded by ${currentUser.fullName}`;
        if (['TXT', 'CSV'].includes(ext) && file.size < 200000) {
          const textContent = await new Promise(resolve => {
            const r = new FileReader();
            r.onload = () => resolve(String(r.result || ''));
            r.onerror = () => resolve('');
            r.readAsText(file);
          });
          if (textContent.trim()) {
            snippet = textContent.trim().slice(0, 600);
          }
        }
        const attId = Date.now() + Math.floor(Math.random() * 1000);
        if (dataUrl && dataUrl.includes(';base64,')) {
          cloudBlobComplete[attId] = dataUrl;
        }
        const attObj = {
          id: attId,
          filename: file.name,
          fileType,
          size: file.size,
          previewText: snippet,
          remoteUrl: '',
          inlineDataUrl,
          isFullQuality: true,
          dataUrl
        };
        uploadedAttachments.push(attObj);
        rawFileMap[attId] = file;
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
      btnSubmitNotice.disabled = false;
      btnSubmitNotice.textContent = 'Publish notice →';
      btnCancelEdit.classList.add('hidden');
      renderNotices();
      document.getElementById('notices').scrollIntoView({ behavior: 'smooth' });

      if (savedNotice) {
        // 1. Broadcast notice immediately with inline preview so other phones see it within 1 second
        await publishCloudEvent({ action: 'UPSERT', notice: savedNotice });
        // 2. Upload full high-res binary & blob in parallel and update remoteUrl
        if (uploadedAttachments.length > 0) {
          for (const att of uploadedAttachments) {
            await uploadAttachmentToCloud(att, rawFileMap[att.id]);
          }
          saveDb(db);
          await publishCloudEvent({ action: 'UPSERT', notice: savedNotice });
        }
      }
    } finally {
      btnSubmitNotice.disabled = false;
    }
  });

  // Attachment Preview Modal (Previewable & Downloadable across all devices)
  const previewModal = document.getElementById('preview-modal');
  let activePreviewAttachment = null;
  let activePreviewBlobUrl = null;

  async function openAttachmentPreview(att) {
    activePreviewAttachment = att;
    if (activePreviewBlobUrl) {
      URL.revokeObjectURL(activePreviewBlobUrl);
      activePreviewBlobUrl = null;
    }

    const filename = String(att.filename || 'Circular.pdf');
    const ext = filename.split('.').pop().toLowerCase();
    const isImage = isImageFilenameOrType(filename, att.fileType);
    const isPdf =
      att.fileType === 'PDF' ||
      ext === 'pdf' ||
      (att.dataUrl && att.dataUrl.startsWith('data:application/pdf'));

    document.getElementById('preview-title').textContent = filename;
    const bodyEl = document.getElementById('preview-body');
    previewModal.classList.add('open');

    // If image already has an inline or full dataUrl/remoteUrl, render it immediately while upgrading to full quality
    if (isImage) {
      const initialUrl = normalizeAttachmentDataUrl(att.dataUrl, filename, att.fileType) || att.remoteUrl || '';
      if (initialUrl) {
        bodyEl.innerHTML = `
          <div style="text-align:center;background:#0f172a;padding:12px;border-radius:12px;">
            <img id="preview-live-img" src="${escapeHtml(initialUrl)}" alt="${escapeHtml(filename)}" style="max-width:100%;max-height:55vh;border-radius:8px;object-fit:contain;display:block;margin:0 auto;">
          </div>
          <div style="margin-top:10px;font-size:12px;color:#475467;display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px;">
            <span><b>File:</b> ${escapeHtml(filename)}</span>
            <span><b>Size:</b> ${formatFileSize(getRealAttachmentSize(att))}</span>
          </div>`;
      } else {
        bodyEl.innerHTML = `<div style="padding:24px;text-align:center;color:#475467;">Loading uploaded photo preview…</div>`;
      }

      await ensureAttachmentHydrated(att);
      if (activePreviewAttachment !== att) return;

      const finalImgSrc =
        normalizeAttachmentDataUrl(cloudBlobComplete[att.id] || att.dataUrl, filename, att.fileType) ||
        att.remoteUrl ||
        initialUrl;

      if (finalImgSrc) {
        bodyEl.innerHTML = `
          <div style="text-align:center;background:#0f172a;padding:12px;border-radius:12px;">
            <img id="preview-live-img" src="${escapeHtml(finalImgSrc)}" alt="${escapeHtml(filename)}" style="max-width:100%;max-height:55vh;border-radius:8px;object-fit:contain;display:block;margin:0 auto;">
          </div>
          <div style="margin-top:10px;font-size:12px;color:#475467;display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px;">
            <span><b>File:</b> ${escapeHtml(filename)}</span>
            <span><b>Size:</b> ${formatFileSize(getRealAttachmentSize(att))}</span>
          </div>`;
        const imgEl = document.getElementById('preview-live-img');
        if (imgEl && att.remoteUrl && finalImgSrc !== att.remoteUrl) {
          imgEl.onerror = () => {
            imgEl.onerror = null;
            imgEl.src = att.remoteUrl;
          };
        }
      } else {
        bodyEl.innerHTML = `
          <div style="padding:24px;text-align:center;background:#f8fafc;border-radius:12px;border:1px solid #e2e8f0;color:#334155;">
            <b>${escapeHtml(filename)}</b> (${formatFileSize(getRealAttachmentSize(att))})<br>
            <span style="font-size:12px;color:#64748b;">Image is syncing from publisher device. Tap Refresh and try again.</span>
          </div>`;
      }
      return;
    }

    bodyEl.innerHTML = `<div style="padding:18px;text-align:center;color:#475467;">Loading attachment preview…</div>`;
    await ensureAttachmentHydrated(att);
    if (activePreviewAttachment !== att) return;

    const blob = buildDownloadBlob(att);
    activePreviewBlobUrl = URL.createObjectURL(blob);

    if (isPdf) {
      bodyEl.innerHTML = `
        <iframe src="${activePreviewBlobUrl}" style="width:100%;height:340px;border:1px solid #cbd5e1;border-radius:10px;background:#fff;" title="${escapeHtml(filename)}"></iframe>
        <div style="margin-top:10px;background:#f8fafc;color:#1e293b;padding:12px;border-radius:10px;border:1px solid #e2e8f0;white-space:pre-line;font-family:monospace;font-size:12px;">
          <b>PDF Document Summary (${escapeHtml(filename)} · ${formatFileSize(blob.size)}):</b>\n${escapeHtml(att.previewText || 'Official GCE Salem PDF Circular')}
        </div>`;
    } else {
      let textPreview = att.previewText || 'Official GCE Salem Circular Attachment';
      if (att.dataUrl && att.dataUrl.startsWith('data:text/')) {
        try {
          if (att.dataUrl.includes(';base64,')) {
            textPreview = window.atob(att.dataUrl.split(';base64,')[1]);
          } else if (att.dataUrl.includes(',')) {
            textPreview = decodeURIComponent(att.dataUrl.split(',')[1]);
          }
        } catch (_) {}
      }
      bodyEl.innerHTML = `
        <div style="background:#f8fafc;color:#1e293b;padding:14px;border-radius:10px;border:1px solid #e2e8f0;white-space:pre-line;font-family:monospace;font-size:12px;">
          <b>Document Preview (${escapeHtml(filename)} · ${formatFileSize(blob.size)}):</b>\n\n${escapeHtml(textPreview)}
        </div>`;
    }
  }

  document.getElementById('preview-download-btn').addEventListener('click', () => {
    if (activePreviewAttachment) {
      triggerAttachmentDownload(activePreviewAttachment);
    }
  });

  const previewOpenTabBtn = document.getElementById('preview-open-tab-btn');
  if (previewOpenTabBtn) {
    previewOpenTabBtn.addEventListener('click', async () => {
      if (!activePreviewAttachment) return;
      await ensureAttachmentHydrated(activePreviewAttachment);
      const blob = buildDownloadBlob(activePreviewAttachment);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
    });
  }

  document.getElementById('btn-close-preview').addEventListener('click', () => {
    previewModal.classList.remove('open');
  });

  // Login Modal & Full Forgot Password Setup with CAPTCHA
  const loginModal = document.getElementById('login-modal');
  const loginModalTitleEl = document.getElementById('login-modal-title');
  const tabLoginModeBtn = document.getElementById('tab-login-mode');
  const tabForgotModeBtn = document.getElementById('tab-forgot-mode');
  const loginViewEl = document.getElementById('login-view');
  const forgotViewEl = document.getElementById('forgot-view');
  const loginErrorEl = document.getElementById('login-error');
  const loginSuccessEl = document.getElementById('login-success');
  const forgotErrorEl = document.getElementById('forgot-error');
  const forgotAccountStatusEl = document.getElementById('forgot-account-status');
  const captchaMatchStatusEl = document.getElementById('captcha-match-status');
  const captchaCanvas = document.getElementById('captcha-canvas');
  let currentCaptchaCode = '';

  function updateModalTabStyles(isForgot) {
    if (tabLoginModeBtn && tabForgotModeBtn) {
      if (isForgot) {
        tabForgotModeBtn.style.background = '#155eef';
        tabForgotModeBtn.style.color = '#fff';
        tabLoginModeBtn.style.background = 'transparent';
        tabLoginModeBtn.style.color = '#334155';
      } else {
        tabLoginModeBtn.style.background = '#155eef';
        tabLoginModeBtn.style.color = '#fff';
        tabForgotModeBtn.style.background = 'transparent';
        tabForgotModeBtn.style.color = '#334155';
      }
    }
  }

  function generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    currentCaptchaCode = code;

    if (captchaMatchStatusEl) {
      captchaMatchStatusEl.textContent = 'Enter the 6-character security code shown above.';
      captchaMatchStatusEl.style.color = '#475467';
    }

    if (captchaCanvas && captchaCanvas.getContext) {
      const ctx = captchaCanvas.getContext('2d');
      const w = captchaCanvas.width;
      const h = captchaCanvas.height;
      ctx.clearRect(0, 0, w, h);

      // Background gradient
      const grad = ctx.createLinearGradient(0, 0, w, h);
      grad.addColorStop(0, '#eef4ff');
      grad.addColorStop(1, '#dbeafe');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);

      // Security noise lines
      for (let i = 0; i < 6; i++) {
        ctx.strokeStyle = i % 2 === 0 ? 'rgba(21, 94, 239, 0.38)' : 'rgba(15, 23, 42, 0.25)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(Math.random() * 25, Math.random() * h);
        ctx.lineTo(w - Math.random() * 25, Math.random() * h);
        ctx.stroke();
      }

      // Security noise dots
      for (let i = 0; i < 32; i++) {
        ctx.fillStyle = 'rgba(30, 41, 59, 0.28)';
        ctx.beginPath();
        ctx.arc(Math.random() * w, Math.random() * h, 1.4, 0, Math.PI * 2);
        ctx.fill();
      }

      // Draw each character with slight rotation
      ctx.font = 'bold 25px monospace';
      ctx.textBaseline = 'middle';
      for (let i = 0; i < code.length; i++) {
        const ch = code[i];
        const x = 24 + i * 28;
        const y = h / 2 + (Math.random() * 8 - 4);
        const angle = (Math.random() - 0.5) * 0.35;
        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(angle);
        ctx.fillStyle = i % 2 === 0 ? '#0f172a' : '#155eef';
        ctx.fillText(ch, -7, 0);
        ctx.restore();
      }
    }
  }

  function updateForgotAccountLookup() {
    if (!forgotAccountStatusEl) return;
    const raw = document.getElementById('forgot-login-id').value.trim().toLowerCase();
    if (!raw) {
      forgotAccountStatusEl.textContent = 'Enter your registered GCE Salem Login ID.';
      forgotAccountStatusEl.style.color = '#475467';
      return;
    }
    const found = db.users.find(u => u.loginId.toLowerCase() === raw);
    if (found) {
      forgotAccountStatusEl.textContent = `✓ Account Verified: ${found.fullName} (Role: ${found.role} · Dept: ${found.department})`;
      forgotAccountStatusEl.style.color = '#067647';
    } else {
      forgotAccountStatusEl.textContent = '✗ No registered account matches this Login ID.';
      forgotAccountStatusEl.style.color = '#b42318';
    }
  }

  function showLoginView(successMessage = '') {
    updateModalTabStyles(false);
    if (loginModalTitleEl) loginModalTitleEl.textContent = 'Sign in to GCE Salem Portal';
    if (loginViewEl) loginViewEl.classList.remove('hidden');
    if (forgotViewEl) forgotViewEl.classList.add('hidden');
    loginErrorEl.classList.add('hidden');
    if (loginSuccessEl) {
      if (successMessage) {
        loginSuccessEl.textContent = successMessage;
        loginSuccessEl.classList.remove('hidden');
      } else {
        loginSuccessEl.classList.add('hidden');
      }
    }
  }

  function showForgotView() {
    updateModalTabStyles(true);
    if (loginModalTitleEl) loginModalTitleEl.textContent = 'Forgot Password — CAPTCHA Reset';
    if (loginViewEl) loginViewEl.classList.add('hidden');
    if (forgotViewEl) forgotViewEl.classList.remove('hidden');
    if (forgotErrorEl) forgotErrorEl.classList.add('hidden');
    const prefillId = document.getElementById('login-id-input').value.trim();
    document.getElementById('forgot-login-id').value = prefillId;
    document.getElementById('forgot-captcha-input').value = '';
    document.getElementById('forgot-new-pass').value = '';
    document.getElementById('forgot-confirm-pass').value = '';
    updateForgotAccountLookup();
    generateCaptcha();
  }

  btnOpenLogin.addEventListener('click', () => {
    showLoginView('');
    document.getElementById('login-id-input').value = '';
    document.getElementById('login-pass-input').value = '';
    loginModal.classList.add('open');
  });
  if (btnHeaderForgot) {
    btnHeaderForgot.addEventListener('click', () => {
      document.getElementById('login-id-input').value = '';
      showForgotView();
      loginModal.classList.add('open');
    });
  }
  document.getElementById('btn-close-login').addEventListener('click', () => {
    loginModal.classList.remove('open');
  });
  document.getElementById('btn-open-forgot').addEventListener('click', showForgotView);
  document.getElementById('btn-back-to-login').addEventListener('click', () => showLoginView(''));
  if (tabLoginModeBtn) tabLoginModeBtn.addEventListener('click', () => showLoginView(''));
  if (tabForgotModeBtn) tabForgotModeBtn.addEventListener('click', showForgotView);
  document.getElementById('btn-refresh-captcha').addEventListener('click', generateCaptcha);

  document.getElementById('forgot-login-id').addEventListener('input', updateForgotAccountLookup);
  document.getElementById('forgot-captcha-input').addEventListener('input', e => {
    if (!captchaMatchStatusEl) return;
    const val = e.target.value.trim().toUpperCase();
    if (!val) {
      captchaMatchStatusEl.textContent = 'Enter the 6-character security code shown above.';
      captchaMatchStatusEl.style.color = '#475467';
    } else if (val === currentCaptchaCode.toUpperCase()) {
      captchaMatchStatusEl.textContent = '✓ CAPTCHA code verified!';
      captchaMatchStatusEl.style.color = '#067647';
    } else {
      captchaMatchStatusEl.textContent = 'Keep typing the 6-character CAPTCHA code…';
      captchaMatchStatusEl.style.color = '#475467';
    }
  });

  const loginShowPass = document.getElementById('login-show-pass');
  if (loginShowPass) {
    loginShowPass.addEventListener('change', e => {
      document.getElementById('login-pass-input').type = e.target.checked ? 'text' : 'password';
    });
  }
  const forgotShowPass = document.getElementById('forgot-show-pass');
  if (forgotShowPass) {
    forgotShowPass.addEventListener('change', e => {
      const t = e.target.checked ? 'text' : 'password';
      document.getElementById('forgot-new-pass').type = t;
      document.getElementById('forgot-confirm-pass').type = t;
    });
  }

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
      loginErrorEl.textContent = 'Invalid Login ID or password. Use "Forgot Password (CAPTCHA)" above if you need to reset your password.';
      loginErrorEl.classList.remove('hidden');
      if (loginSuccessEl) loginSuccessEl.classList.add('hidden');
      return;
    }
    if (!user.isActive) {
      loginErrorEl.textContent = 'This account has been deactivated by the Admin Principal.';
      loginErrorEl.classList.remove('hidden');
      if (loginSuccessEl) loginSuccessEl.classList.add('hidden');
      return;
    }
    currentUser = user;
    localStorage.setItem(SESSION_KEY, user.loginId);
    loginModal.classList.remove('open');
    renderAuthUI();
  });

  document.getElementById('forgot-form').addEventListener('submit', async e => {
    e.preventDefault();
    const id = document.getElementById('forgot-login-id').value.trim().toLowerCase();
    const captchaEntered = document.getElementById('forgot-captcha-input').value.trim().toUpperCase();
    const newPass = document.getElementById('forgot-new-pass').value;
    const confirmPass = document.getElementById('forgot-confirm-pass').value;

    if (!captchaEntered || captchaEntered !== currentCaptchaCode.toUpperCase()) {
      forgotErrorEl.textContent = 'Incorrect CAPTCHA security code. Please enter the new code generated.';
      forgotErrorEl.classList.remove('hidden');
      document.getElementById('forgot-captcha-input').value = '';
      generateCaptcha();
      return;
    }

    const user = db.users.find(u => u.loginId.toLowerCase() === id);
    if (!user) {
      forgotErrorEl.textContent = 'No account found with that Login ID.';
      forgotErrorEl.classList.remove('hidden');
      generateCaptcha();
      return;
    }

    if (!user.isActive) {
      forgotErrorEl.textContent = 'This account is deactivated. Contact Admin Principal.';
      forgotErrorEl.classList.remove('hidden');
      return;
    }

    if (newPass.length < 4) {
      forgotErrorEl.textContent = 'New password must be at least 4 characters long.';
      forgotErrorEl.classList.remove('hidden');
      return;
    }

    if (newPass !== confirmPass) {
      forgotErrorEl.textContent = 'New password and confirm password do not match.';
      forgotErrorEl.classList.remove('hidden');
      return;
    }

    user.password = newPass;
    saveDb(db);
    document.getElementById('login-id-input').value = user.loginId;
    document.getElementById('login-pass-input').value = '';
    showLoginView(`✓ Password reset successful for ${user.loginId} (${user.fullName}). Sign in below with your new password.`);
    await publishCloudEvent({ action: 'PASSWORD_RESET', loginId: user.loginId, newPassword: newPass });
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
