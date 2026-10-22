(() => {
  const list = document.getElementById('notice-list');
  if (!list) return;
  const params = new URLSearchParams(location.search);
  const department = params.get('department') || 'ALL';
  const category = params.get('category') || 'ALL';
  const q = params.get('q') || '';
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const icons = {Examination:'▣',Placements:'↗',Events:'✦',General:'▤',Academic:'▤',Circular:'▤',Sports:'✦',Other:'▤'};
  function renderNotice(n) {
    const role = document.body.dataset.role || '';
    const loginId = document.body.dataset.loginId || '';
    const canManage = role === 'Administrator' || (role === 'Faculty' && n.author_login_id === loginId);
    const actions = canManage ? `<div class="notice-actions"><button class="icon-button edit-button" type="button" data-id="${n.id}" data-title="${escapeHtml(n.title)}" data-description="${escapeHtml(n.description)}" title="Edit notice" aria-label="Edit notice">✎</button><button class="icon-button dynamic-delete-button" type="button" data-id="${n.id}" title="Delete notice" aria-label="Delete notice">×</button></div>` : '';
    const cls = n.priority === 'HIGH' ? 'symbol-high' : n.category === 'Placements' ? 'symbol-career' : n.category === 'Examination' ? 'symbol-exam' : 'symbol-general';
    const date = n.published_at ? new Date(n.published_at + (n.published_at.endsWith('Z') ? '' : 'Z')).toLocaleDateString('en-GB',{day:'2-digit',month:'short',year:'numeric'}) : '';
    const attachments = (n.attachments || []).map(a => `<a class="attachment" href="${escapeHtml(a.url)}"><span>↧</span>${escapeHtml(a.filename)} <small>${(Number(a.size || 0)/1048576).toFixed(1)} MB</small></a>`).join('');
    return `<article class="notice-card" data-notice-id="${n.id}"><div class="notice-symbol ${cls}">${icons[n.category] || '▤'}</div><div class="notice-main"><div class="notice-meta"><span class="category-pill">${escapeHtml(n.category)}</span>${n.priority === 'HIGH' ? '<span class="priority-pill">IMPORTANT</span>' : ''}${n.is_pinned ? '<span class="pinned-pill">⌖ PINNED</span>' : ''}<span class="notice-date">${escapeHtml(date)}</span></div><h3>${escapeHtml(n.title)}</h3><p>${escapeHtml(n.description)}</p>${attachments ? `<div class="attachments">${attachments}</div>` : ''}<div class="notice-foot"><span class="department-tag">${escapeHtml(n.department || 'COLLEGE-WIDE')}</span><span>Posted by ${escapeHtml(n.author || 'College administration')}</span></div></div>${actions}</article>`;
  }
  async function refresh() {
    try {
      const url = new URL('/api/notices', location.origin);
      url.searchParams.set('department', department); url.searchParams.set('category', category); url.searchParams.set('q', q);
      const response = await fetch(url, {headers:{'Accept':'application/json'}, cache:'no-store'});
      if (!response.ok) return;
      const data = await response.json();
      const notices = data.notices || [];
      const before = [...list.querySelectorAll('[data-notice-id]')].map(el => el.dataset.noticeId).join(',');
      const after = notices.map(n => String(n.id)).join(',');
      if (before !== after || list.children.length === 0) {
        if (notices.length) list.innerHTML = notices.map(renderNotice).join('');
        else list.innerHTML = '<div class="empty-state"><div class="empty-icon">⌕</div><h3>No notices found</h3><p>Try another search or department filter. New notices will appear here automatically.</p><a href="/" class="button button-secondary">Clear filters</a></div>';
        const count = document.getElementById('heading-count'); if (count) count.textContent = notices.length;
        const total = document.getElementById('stat-total'); if (total) total.textContent = notices.length;
        const live = document.getElementById('live-announcement'); if (live) live.textContent = 'Notice board updated.';
      } else {
        // Keep content and attachments fresh when a notice is edited without changing IDs.
        const old = new Map([...list.querySelectorAll('[data-notice-id]')].map(el => [el.dataset.noticeId, el]));
        notices.forEach(n => { const node = old.get(String(n.id)); if (node) { const temp = document.createElement('div'); temp.innerHTML = renderNotice(n); const fresh = temp.firstElementChild; if (node.outerHTML !== fresh.outerHTML) node.replaceWith(fresh); } });
      }
    } catch (_) { /* Keep the last successfully rendered notices when offline. */ }
  }
  list.addEventListener('click', async event => {
    const deleteButton = event.target.closest('.dynamic-delete-button');
    if (deleteButton) {
      if (!window.confirm('Delete this notice and its attachments?')) return;
      try { const response = await fetch(`/api/notices/${deleteButton.dataset.id}`, {method:'DELETE'}); if (!response.ok) { const result = await response.json().catch(() => ({})); window.alert(result.detail || 'Could not delete the notice.'); return; } await refresh(); } catch (_) { window.alert('Network error while deleting the notice.'); }
      return;
    }
    const button = event.target.closest('.edit-button');
    if (!button) return;
    const title = window.prompt('Edit notice title:', button.dataset.title || '');
    if (title === null) return;
    const description = window.prompt('Edit notice description:', button.dataset.description || '');
    if (description === null) return;
    if (!title.trim() || !description.trim()) { window.alert('Title and description cannot be empty.'); return; }
    try {
      const response = await fetch(`/api/notices/${button.dataset.id}`, {
        method: 'PUT', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({title:title.trim(), description:description.trim()})
      });
      if (!response.ok) { const result = await response.json().catch(() => ({})); window.alert(result.detail || 'Could not update the notice. Please sign in again.'); return; }
      await refresh();
    } catch (_) { window.alert('Network error while updating the notice.'); }
  });
  window.setInterval(refresh, 15000);
})();
