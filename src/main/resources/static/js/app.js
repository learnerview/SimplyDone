
// ====== SIMPLYDONE FRONTEND REWRITE ======

const AUTH_KEY = 'sd_api_key';
const getApiKey = () => localStorage.getItem(AUTH_KEY);
const setApiKey = (k) => k ? localStorage.setItem(AUTH_KEY, k) : localStorage.removeItem(AUTH_KEY);
const logout = () => { setApiKey(null); window.location.href = '/login'; };

const api = async (url, opts = {}) => {
    const key = getApiKey();
    const headers = {
        'Content-Type': 'application/json',
        ...(key ? { 'X-API-KEY': key } : {})
    };
    const res = await fetch(url, { ...opts, headers: { ...headers, ...opts.headers } });
    if (res.status === 401 || res.status === 403) { logout(); return null; }
    const data = await res.json().catch(() => ({ message: 'Invalid response from server' }));
    if (!res.ok) {
        const msg = data.message || `Server error (${res.status})`;
        toast(msg, 'error');
        throw new Error(msg);
    }
    return data;
};

function toast(msg, type = 'info') {
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.innerHTML = `<strong>${type.toUpperCase()}:</strong> ${msg}`;
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    container.appendChild(el);
    setTimeout(() => { el.style.opacity = '0'; setTimeout(() => el.remove(), 300); }, 4000);
}

function escHtml(str) {
    if (str == null) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function statusBadge(status) {
    const map = { SUCCESS: 'badge-success', FAILED: 'badge-failed', DLQ: 'badge-failed', RUNNING: 'badge-warn', QUEUED: 'badge-warn' };
    return map[status] ?? 'badge-warn';
}

async function loadStats() {
    if (!getApiKey()) return;
    try {
        const path = window.location.pathname;
        const isAdmin = (path === '/admin' || path === '/dlq');
        const d = await api(isAdmin ? '/api/admin/stats' : '/api/jobs/health');
        if (!d?.data) return;
        // Map all possible stat fields
        const s = d.data;
        const statMap = {
            'stat-high': s.highQueueSize ?? 0,
            'stat-normal': s.normalQueueSize ?? 0,
            'stat-low': s.lowQueueSize ?? 0,
            'stat-queued': s.totalQueued ?? s.queued ?? 0,
            'stat-running': s.totalRunning ?? s.running ?? 0,
            'stat-success': s.totalSuccess ?? s.success ?? 0,
            'stat-failed': s.totalFailed ?? s.failed ?? 0,
            'stat-dlq': s.totalDlq ?? s.dlq ?? 0
        };
        Object.entries(statMap).forEach(([id, v]) => {
            const el = document.getElementById(id);
            if (el) el.textContent = v;
        });
    } catch (e) { /* silent */ }
}

async function loadJobs() {
    const tbody = document.getElementById('jobs-tbody');
    if (!tbody || !getApiKey()) return;
    try {
        const d = await api('/api/jobs?size=50');
        const jobs = d?.data?.content ?? [];
        if (!jobs.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">No jobs found</td></tr>';
            return;
        }
        tbody.innerHTML = jobs.map(j => `
            <tr>
                <td><code>${(j.id||'').substring(0,8)}</code></td>
                <td>${escHtml(j.jobType ?? '')}</td>
                <td><span class="badge ${statusBadge(j.status)}">${j.status ?? ''}</span></td>
                <td>${j.priority ?? ''}</td>
                <td>${j.attemptCount ?? 0} / ${j.maxAttempts ?? 5}</td>
                <td>${j.createdAt ? new Date(j.createdAt).toLocaleTimeString() : ''}</td>
                <td style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:0.8rem">${escHtml(j.result ?? 'Processing...')}</td>
            </tr>`).join('');
    } catch (e) { tbody.innerHTML = '<tr><td colspan="7" class="empty">Error loading jobs</td></tr>'; }
}

// Always refresh stats and jobs after job actions
async function submitJob() {
    const jobType = document.getElementById('jobType')?.value;
    const priority = document.getElementById('priority')?.value;
    const executionEndpoint = document.getElementById('executionEndpoint')?.value;
    const payloadStr = document.getElementById('payload')?.value ?? '{}';
    const maxAttempts = document.getElementById('maxAttempts')?.value || '3';
    const timeoutSeconds = document.getElementById('timeoutSeconds')?.value || '30';
    const callbackUrl = document.getElementById('callbackUrl')?.value;
    if (!executionEndpoint) { toast('Execution endpoint is required', 'error'); return; }
    let payload;
    try { payload = JSON.parse(payloadStr); }
    catch { toast('Malformed JSON payload', 'error'); return; }
    try {
        const idempotencyKey = crypto.randomUUID ? crypto.randomUUID() : 'sd_uid_' + Math.random().toString(36).substring(2);
        const reqBody = {
            jobType,
            idempotencyKey,
            priority,
            execution: { type: 'HTTP', endpoint: executionEndpoint },
            payload,
            maxAttempts: parseInt(maxAttempts),
            timeoutSeconds: parseInt(timeoutSeconds)
        };
        if (callbackUrl) reqBody.callbackUrl = callbackUrl;
        await api('/api/jobs', { method: 'POST', body: JSON.stringify(reqBody) });
        toast('Task provisioned successfully', 'info');
        await loadJobs();
        await loadStats();
    } catch (e) {
        toast('Failed to schedule task: ' + (e?.message || 'Unknown error'), 'error');
        console.error('Job scheduling error:', e);
    }
}

async function retryDlqJob(id) {
    try {
        await api(`/api/admin/dlq/${id}/retry`, { method: 'POST' });
        toast('Job rescued and re-queued', 'info');
        await loadDlq();
        await loadStats();
    } catch (_) {}
}

async function clearQueues() {
    if (!confirm('PERMANENT: Flush all cluster buffers?')) return;
    try {
        await api('/api/admin/queues', { method: 'DELETE' });
        toast('All queues cleared', 'info');
        await loadStats();
    } catch (_) {}
}

// DLQ, Keys, Handlers, SSE, and other helpers remain unchanged for brevity
// ...existing code...

/* ── DLQ Monitor ──────────────────────────────────── */
async function loadDlq() {
    const tbody = document.getElementById('dlq-tbody');
    if (!tbody || !getApiKey()) return;
    try {
        const d = await api('/api/admin/dlq');
        const jobs = d?.data ?? [];
        if (!jobs.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">Terminal failure queue is empty</td></tr>';
            return;
        }
        tbody.innerHTML = jobs.map(j => `
            <tr>
                <td><code>${j.id.substring(0,8)}</code></td>
                <td>${escHtml(j.jobType)}</td>
                <td><span class="badge badge-failed">DLQ</span></td>
                <td><code>${escHtml(j.producer)}</code></td>
                <td>${new Date(j.createdAt).toLocaleString()}</td>
                <td style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--danger)">${escHtml(j.result ?? 'Unhandled exception')}</td>
                <td><button class="btn btn-outline" style="padding:0.3rem 0.6rem;font-size:0.75rem" onclick="retryDlqJob('${j.id}')">REQUEUE</button></td>
            </tr>`).join('');
    } catch (_) {}
}

/* ── API Key Registry ─────────────────────────────── */
async function loadKeys() {
    const tbody = document.getElementById('keys-tbody');
    if (!tbody || !getApiKey()) return;
    try {
        const d = await api('/api/admin/keys');
        const keys = d?.data ?? [];
        if (!keys.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">No merchant tokens found</td></tr>';
            return;
        }
        tbody.innerHTML = keys.map(k => `
            <tr id="key-row-${k.id}">
                <td><strong>${escHtml(k.label ?? 'Unnamed')}</strong></td>
                <td><code>${escHtml(k.producer)}</code></td>
                <td><code style="font-size:0.75rem">${escHtml(k.apiKey)}</code></td>
                <td><span class="badge ${k.active ? 'badge-success' : 'badge-failed'}" id="key-status-${k.id}">${k.active ? 'VALID' : 'REVOKED'}</span></td>
                <td><span style="font-size:0.75rem">${k.admin ? 'GLOBAL_ADMIN' : 'TENANT_OWNER'}</span></td>
                <td><button class="btn btn-outline" style="padding:0.3rem 0.6rem;font-size:0.75rem" onclick="copyToClipboard('${k.apiKey}')" title="Copy Key">Copy</button></td>
                <td>${k.active
                    ? `<button class="btn btn-outline" style="padding:0.3rem 0.6rem;font-size:0.75rem;color:var(--danger)" onclick="revokeKey('${k.id}')">REVOKE</button>`
                    : '—'}</td>
            </tr>`).join('');
    } catch (_) {}
}

/* ── Handlers Registry ────────────────────────────── */
async function loadHandlers() {
    try {
        const d = await api('/api/jobs/types');
        const handlers = d?.data ?? [];

        const select = document.getElementById('jobType');
        if (select) select.innerHTML = handlers.map(h => `<option value="${h.jobType}">${h.jobType.toUpperCase()} — ${h.description}</option>`).join('');

        const tbody = document.getElementById('handlers-tbody');
        if (tbody) tbody.innerHTML = handlers.map(h => `
            <tr>
                <td><code>${h.jobType}</code></td>
                <td>${escHtml(h.description)}</td>
                <td><code>${escHtml(h.handlerClass)}</code></td>
            </tr>`).join('');
    } catch (_) {}
}

/* ── Actions ──────────────────────────────────────── */
async function submitJob() {
    const jobType = document.getElementById('jobType')?.value;
    const priority = document.getElementById('priority')?.value;
    const executionEndpoint = document.getElementById('executionEndpoint')?.value;
    const payloadStr = document.getElementById('payload')?.value ?? '{}';

    if (!executionEndpoint) { toast('Execution endpoint is required', 'error'); return; }

    let payload;
    try { payload = JSON.parse(payloadStr); }
    catch { toast('Malformed JSON payload', 'error'); return; }

    try {
        const idempotencyKey = crypto.randomUUID ? crypto.randomUUID() : 'sd_uid_' + Math.random().toString(36).substring(2);
        const reqBody = {
            jobType,
            idempotencyKey,
            priority,
            execution: { type: 'HTTP', endpoint: executionEndpoint },
            payload
        };
        await api('/api/jobs', { method: 'POST', body: JSON.stringify(reqBody) });
        toast('Task provisioned successfully', 'info');
        loadJobs();
        loadStats();
    } catch (_) {}
}

async function retryDlqJob(id) {
    try {
        await api(`/api/admin/dlq/${id}/retry`, { method: 'POST' });
        toast('Job rescued and re-queued', 'info');
        loadDlq();
        loadStats();
    } catch (_) {}
}

async function clearQueues() {
    if (!confirm('PERMANENT: Flush all cluster buffers?')) return;
    try {
        await api('/api/admin/queues', { method: 'DELETE' });
        toast('All queues cleared', 'info');
        loadStats();
    } catch (_) {}
}

async function submitCreateKey() {
    const label    = document.getElementById('newKeyLabel')?.value?.trim();
    const producer = document.getElementById('newKeyProducer')?.value?.trim();
    const admin    = document.getElementById('newKeyIsAdmin')?.value === 'true';

    if (!label || !producer) { toast('Label and Producer ID are required', 'error'); return; }

    try {
        const d = await api('/api/admin/keys', {
            method: 'POST',
            body: JSON.stringify({ label, producer, admin })
        });
        toast('Merchant token issued', 'info');
        document.getElementById('key-modal').style.display = 'none';

        // Reset form
        document.getElementById('newKeyLabel').value = '';
        document.getElementById('newKeyProducer').value = '';

        // Reload keys and offer to copy
        await loadKeys();
        if (d?.data?.apiKey) {
            copyToClipboard(d.data.apiKey);
        }
    } catch (_) {}
}

async function revokeKey(id) {
    if (!confirm('Deactivate this merchant token permanently?')) return;
    try {
        await api(`/api/admin/keys/${id}`, { method: 'DELETE' });

        // Update UI in-place without full reload
        const badge = document.getElementById(`key-status-${id}`);
        if (badge) {
            badge.textContent = 'REVOKED';
            badge.className = 'badge badge-failed';
        }
        const row = document.getElementById(`key-row-${id}`);
        if (row) {
            const lastCell = row.cells[row.cells.length - 1];
            if (lastCell) lastCell.textContent = '—';
        }
        toast('Merchant token revoked', 'info');
    } catch (_) {}
}

/* ── SSE (Live stream) ────────────────────────────── */
let _sse = null;
function connectSse() {
    if (_sse || !getApiKey()) return;
    _sse = new EventSource('/api/events?apiKey=' + encodeURIComponent(getApiKey()));

    _sse.addEventListener('connected', () => {
        const el = document.getElementById('sse-label');
        if (el) el.textContent = 'Live Sync: Active';
    });

    ['JOB_CREATED','JOB_STARTED','JOB_COMPLETED','JOB_FAILED','JOB_RETRY'].forEach(evt => {
        _sse.addEventListener(evt, e => {
            const d = JSON.parse(e.data);
            const short = (d.id ?? '').substring(0, 8);
            toast(`${evt.replace('_',' ')}: ${short}`, evt === 'JOB_FAILED' ? 'error' : 'info');
            loadStats();
            loadJobs();
            if (window.location.pathname === '/dlq') loadDlq();
        });
    });

    _sse.onerror = () => {
        _sse.close(); _sse = null;
        const el = document.getElementById('sse-label');
        if (el) el.textContent = 'Reconnecting...';
        setTimeout(connectSse, 6000);
    };
}

/* ── Security & Role Management ──────────────────── */
async function syncSecurity() {
    if (!getApiKey()) return;
    try {
        const res = await fetch('/api/admin/stats', { headers: { 'X-API-KEY': getApiKey() } });
        if (res.ok) {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.remove('admin-only'));
        }
    } catch (_) {}
}

/* ── Login ────────────────────────────────────────── */
async function performLogin() {
    const key = document.getElementById('apiKeyInput')?.value?.trim();
    if (!key) { toast('Please enter your API Key', 'error'); return; }

    try {
        const res = await fetch('/api/jobs/health', { headers: { 'X-API-KEY': key } });
        if (res.ok) {
            setApiKey(key);
            window.location.href = '/';
        } else {
            toast('Invalid or deactivated credentials', 'error');
        }
    } catch {
        toast('Auth service unreachable', 'error');
    }
}

/* ── Helpers ──────────────────────────────────────── */
function escHtml(str) {
    if (str == null) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function statusBadge(status) {
    const map = { SUCCESS: 'badge-success', FAILED: 'badge-failed', DLQ: 'badge-failed', RUNNING: 'badge-warn', QUEUED: 'badge-warn' };
    return map[status] ?? 'badge-warn';
}

/* ── Boot ─────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    if (window.location.pathname === '/login') return;

    if (!getApiKey()) { logout(); return; }

    syncSecurity();
    loadHandlers();
    loadStats();
    loadJobs();
    if (window.location.pathname === '/dlq') loadDlq();
    connectSse();

    setInterval(loadStats, 10000);
    setInterval(loadJobs, 15000);
    if (window.location.pathname === '/dlq') setInterval(loadDlq, 10000);

    // Patch: force stats refresh on admin tab switch
    if (window.location.pathname === '/admin') {
        window.switchTab = function(tab, event) {
            document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
            document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
            document.getElementById('tab-' + tab).style.display = 'block';
            event.target.classList.add('active');
            if (tab === 'keys') loadKeys();
            if (tab === 'stats') loadStats();
        }
    }
});
