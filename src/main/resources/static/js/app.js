/* ── API helper ──────────────────────────────────────────── */
const api = async (url, opts = {}) => {
    try {
        const res = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            ...opts
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || res.statusText);
        return data;
    } catch (e) {
        toast(e.message, 'error');
        throw e;
    }
};

/* ── Toast notifications ─────────────────────────────────── */
function toast(msg, type = 'info', durationMs = 4000) {
    const container = document.getElementById('toast-container') ||
        (() => {
            const el = document.createElement('div');
            el.id = 'toast-container';
            el.className = 'toast-container';
            document.body.appendChild(el);
            return el;
        })();

    const el = document.createElement('div');
    const icons = { success: '✓', error: '✕', warn: '⚠', info: 'ℹ' };
    el.className = `toast toast-${type === 'error' ? 'error' : type === 'warn' ? 'warn' : type === 'success' ? 'success' : 'info'}`;
    el.innerHTML = `<span>${icons[type] || icons.info}</span><span>${msg}</span>`;
    container.appendChild(el);

    const remove = () => {
        el.style.animation = 'toast-out .2s ease forwards';
        setTimeout(() => el.remove(), 220);
    };
    const timer = setTimeout(remove, durationMs);
    el.addEventListener('click', () => { clearTimeout(timer); remove(); });
}

/* Legacy compat — some inline HTML still calls showResult */
function showResult(msg, isError = false) {
    const box = document.getElementById('result-box');
    if (box) {
        box.style.display = 'block';
        box.style.borderColor = isError ? 'var(--danger)' : 'var(--success)';
        box.textContent = typeof msg === 'object' ? JSON.stringify(msg, null, 2) : msg;
    }
    toast(typeof msg === 'object' ? JSON.stringify(msg) : msg, isError ? 'error' : 'success');
}

/* ── SSE – Server-Sent Events ────────────────────────────── */
let _sse = null;
let _sseReconnectTimer = null;

function connectSse() {
    if (_sse) return;
    _setSseStatus('connecting');

    _sse = new EventSource('/api/events');

    _sse.addEventListener('connected', () => {
        _setSseStatus('live');
        clearTimeout(_sseReconnectTimer);
    });

    const jobEvents = ['JOB_CREATED', 'JOB_STARTED', 'JOB_COMPLETED', 'JOB_FAILED', 'JOB_RETRY'];
    jobEvents.forEach(evtName => {
        _sse.addEventListener(evtName, e => {
            try {
                const d = JSON.parse(e.data);
                _handleSseJob(evtName, d);
            } catch (_) {}
        });
    });

    _sse.onerror = () => {
        _setSseStatus('offline');
        _sse.close();
        _sse = null;
        clearTimeout(_sseReconnectTimer);
        _sseReconnectTimer = setTimeout(connectSse, 5000);
    };
}

function _handleSseJob(evtName, d) {
    const id = d.id || '';
    const short = id.substring(0, 8);
    switch (evtName) {
        case 'JOB_CREATED':
            toast(`Job created: ${short}… (${d.jobType})`, 'info', 3000);
            break;
        case 'JOB_STARTED':
            toast(`Running: ${short}… (${d.jobType})`, 'info', 2500);
            break;
        case 'JOB_COMPLETED':
            toast(`Completed: ${short}… — ${d.result || 'OK'}`, 'success', 4000);
            break;
        case 'JOB_RETRY':
            toast(`Retry ${d.attempt}/${d.maxRetries}: ${short}… in ${(d.retryInMs / 1000).toFixed(1)}s`, 'warn', 5000);
            break;
        case 'JOB_FAILED':
            toast(`Failed → DLQ: ${short}…`, 'error', 6000);
            break;
    }
    // Refresh views if live mode on
    if (_liveMode) {
        loadJobs();
        refreshStats();
    }
}

function _setSseStatus(state) {
    const dot = document.getElementById('sse-dot');
    const label = document.getElementById('sse-label');
    if (!dot || !label) return;
    dot.className = 'dot dot-xs ' + (state === 'live' ? 'dot-green' : state === 'connecting' ? 'dot-yellow' : 'dot-red');
    label.textContent = state === 'live' ? 'Live' : state === 'connecting' ? 'Connecting…' : 'Offline';
}

/* ── Keep-alive ping (Render anti-sleep) ─────────────────── */
async function keepAlivePing() {
    try {
        const res = await fetch('/ping');
        if (res.ok) {
            const label = document.getElementById('ping-label');
            if (label) label.textContent = 'Last ping: ' + new Date().toLocaleTimeString();
        }
    } catch (_) {}
}

/* ── Live / pause toggle ─────────────────────────────────── */
let _liveMode = true;
let _statsTimer = null;
let _jobsTimer = null;

function setLiveMode(on) {
    _liveMode = on;
    clearInterval(_statsTimer);
    clearInterval(_jobsTimer);
    if (on) {
        _statsTimer = setInterval(refreshStats, 10000);
        _jobsTimer  = setInterval(loadJobs,    10000);
    }
    document.querySelectorAll('.live-toggle').forEach(btn => {
        btn.classList.toggle('paused', !on);
        const dot = btn.querySelector('.dot');
        if (dot) dot.className = 'dot dot-xs ' + (on ? 'dot-green' : 'dot-yellow');
        const span = btn.querySelector('span:last-child');
        if (span) span.textContent = on ? 'Live' : 'Paused';
    });
}

function toggleLive() {
    setLiveMode(!_liveMode);
}

/* ── Click-to-copy job ID ────────────────────────────────── */
function copyId(id) {
    navigator.clipboard?.writeText(id).then(() => toast('ID copied to clipboard', 'success', 2000))
        .catch(() => toast('Copy failed', 'error'));
}

/* ── Payload templates ───────────────────────────────────── */
const PAYLOAD_TEMPLATES = {
    'echo': {
        template: '{"message": "hello world"}',
        hint: 'Required: message (string) — returned as the job result'
    },
    'delay': {
        template: '{"seconds": 3}',
        hint: 'Required: seconds (number) — how long to sleep (simulates slow work)'
    },
    'http-call': {
        template: JSON.stringify({
            url: 'https://httpbin.org/post',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: '{"key": "value"}'
        }, null, 2),
        hint: 'Required: url. Optional: method (GET|POST, default GET), headers (object), body (string for POST)'
    }
};

function updatePayloadTemplate(jobType) {
    const payloadEl = document.getElementById('payload');
    const hintEl = document.getElementById('payload-hint');
    if (!payloadEl) return;
    const tpl = PAYLOAD_TEMPLATES[jobType];
    if (tpl) {
        payloadEl.value = tpl.template;
        if (hintEl) hintEl.textContent = tpl.hint;
    } else {
        payloadEl.value = '{}';
        if (hintEl) hintEl.textContent = '';
    }
}

/* ── Job submission ──────────────────────────────────────── */
async function submitJob() {
    const jobType  = document.getElementById('jobType')?.value;
    const priority = document.getElementById('priority')?.value || 'NORMAL';
    const userId   = document.getElementById('userId')?.value   || 'anonymous';
    let payload = {};
    try { payload = JSON.parse(document.getElementById('payload')?.value || '{}'); } catch (_) {}

    const data = await api('/api/jobs', {
        method: 'POST',
        body: JSON.stringify({ jobType, priority, userId, payload })
    });
    toast('Job submitted: ' + (data.data?.id?.substring(0, 8) || ''), 'success');
    loadJobs();
}

async function submitWorkflow() {
    const raw = document.getElementById('workflowJson')?.value;
    if (!raw) return;
    let body;
    try { body = JSON.parse(raw); } catch (_) { toast('Invalid JSON', 'error'); return; }
    const data = await api('/api/jobs/workflow', { method: 'POST', body: JSON.stringify(body) });
    toast('Workflow submitted (' + (data.data?.length || 0) + ' jobs)', 'success');
    loadJobs();
}

/* ── Job list with filters + expand ─────────────────────── */
let _allJobs = [];

async function loadJobs() {
    const tbody = document.getElementById('jobs-tbody');
    if (!tbody) return;
    try {
        const data = await api('/api/jobs?size=50');
        _allJobs = data.data?.content || [];
        renderJobs();
    } catch (_) {}
}

function renderJobs() {
    const tbody = document.getElementById('jobs-tbody');
    if (!tbody) return;

    const filterStatus   = document.getElementById('filter-status')?.value   || '';
    const filterPriority = document.getElementById('filter-priority')?.value || '';
    const filterSearch   = (document.getElementById('filter-search')?.value  || '').toLowerCase();

    const jobs = _allJobs.filter(j => {
        if (filterStatus   && j.status?.toLowerCase()   !== filterStatus.toLowerCase())   return false;
        if (filterPriority && j.priority?.toLowerCase() !== filterPriority.toLowerCase()) return false;
        if (filterSearch   && !j.jobType?.toLowerCase().includes(filterSearch) &&
                              !j.id?.toLowerCase().includes(filterSearch))                return false;
        return true;
    });

    if (jobs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty">No jobs found</td></tr>';
        return;
    }

    tbody.innerHTML = jobs.map(j => {
        const shortId = j.id?.substring(0, 8) || '';
        const retries = j.attemptCount > 0 ? `${j.attemptCount} / ${j.maxRetries}` : '—';
        const result  = j.result ? escape(j.result).substring(0, 60) : '—';
        const payloadStr = j.payload ? JSON.stringify(j.payload, null, 2) : '{}';

        return `<tr class="expand-toggle" onclick="toggleDetail('${j.id}')">
            <td><span class="copy-id" onclick="event.stopPropagation();copyId('${j.id}')">
                <code>${shortId}…</code></span></td>
            <td>${escHtml(j.jobType || '')}</td>
            <td><span class="badge badge-${(j.status || '').toLowerCase()}">${j.status || ''}</span></td>
            <td>${j.priority || ''}</td>
            <td>${retries}</td>
            <td>${j.createdAt ? new Date(j.createdAt).toLocaleTimeString() : '—'}</td>
            <td style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:.78rem">${escHtml(j.result || '—')}</td>
        </tr>
        <tr id="detail-${j.id}" class="detail-row" style="display:none">
            <td colspan="7">
                <div class="detail-grid">
                    <div>
                        <div class="detail-label">Full ID</div>
                        <div class="detail-pre">${j.id}</div>
                    </div>
                    <div>
                        <div class="detail-label">User</div>
                        <div class="detail-pre">${escHtml(j.userId || '—')}</div>
                    </div>
                    <div>
                        <div class="detail-label">Payload</div>
                        <div class="detail-pre">${escHtml(payloadStr)}</div>
                    </div>
                    <div>
                        <div class="detail-label">Result</div>
                        <div class="detail-pre">${escHtml(j.result || '—')}</div>
                    </div>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function toggleDetail(id) {
    const row = document.getElementById('detail-' + id);
    if (row) row.style.display = row.style.display === 'none' ? '' : 'none';
}

function escHtml(s) {
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

/* ── Client-side filter handlers ────────────────────────── */
function applyFilters() { renderJobs(); }

/* ── CSV export ──────────────────────────────────────────── */
function exportCsv() {
    if (_allJobs.length === 0) { toast('No jobs to export', 'warn'); return; }
    const cols = ['id', 'jobType', 'status', 'priority', 'attemptCount', 'maxRetries', 'userId', 'createdAt', 'result'];
    const lines = [cols.join(',')];
    _allJobs.forEach(j => {
        lines.push(cols.map(c => {
            const v = j[c] ?? '';
            const s = String(v).replace(/"/g, '""');
            return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s}"` : s;
        }).join(','));
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'jobs-' + new Date().toISOString().substring(0, 19).replace(/:/g, '-') + '.csv';
    a.click();
    toast('CSV exported (' + _allJobs.length + ' rows)', 'success');
}

/* ── Stats + metrics ─────────────────────────────────────── */
async function refreshStats() {
    try {
        const data = await api('/api/admin/stats');
        const s = data.data;
        const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
        const setClass = (id, cls) => {
            const el = document.getElementById(id);
            if (el) { el.className = el.className.replace(/value-(success|warn|danger)/g, ''); if (cls) el.classList.add(cls); }
        };

        set('stat-high',    s.highQueueSize);
        set('stat-normal',  s.normalQueueSize);
        set('stat-low',     s.lowQueueSize);
        set('stat-queued',  s.totalQueued);
        set('stat-running', s.totalRunning);
        set('stat-success', s.totalSuccess);
        set('stat-failed',  s.totalFailed);
        set('stat-dlq',     s.totalDlq);

        // Enhanced metrics
        if (s.successRate !== undefined) {
            const sr = s.successRate.toFixed(1) + '%';
            set('stat-success-rate', sr);
            setClass('stat-success-rate',
                s.successRate >= 90 ? 'value-success' :
                s.successRate >= 70 ? 'value-warn' : 'value-danger');
        }
        if (s.retryRate !== undefined) {
            set('stat-retry-rate', s.retryRate.toFixed(1) + '%');
            setClass('stat-retry-rate',
                s.retryRate <= 10 ? 'value-success' :
                s.retryRate <= 30 ? 'value-warn' : 'value-danger');
        }
        if (s.throughputPerMinute !== undefined) {
            set('stat-throughput', s.throughputPerMinute.toFixed(1) + '/min');
        }
        if (s.avgLatencyMs !== undefined) {
            const ms = Math.round(s.avgLatencyMs);
            set('stat-latency', ms > 0 ? ms + 'ms' : '—');
            setClass('stat-latency',
                ms <= 1000 ? 'value-success' :
                ms <= 5000 ? 'value-warn' : 'value-danger');
        }
    } catch (_) {}
}

/* ── DLQ actions ─────────────────────────────────────────── */
async function retryDlqJob(id) {
    await api(`/api/admin/dlq/${id}/retry`, { method: 'POST' });
    toast('Job re-queued from DLQ', 'success');
    setTimeout(() => location.reload(), 800);
}

async function clearQueues() {
    if (!confirm('Clear all queues?')) return;
    await api('/api/admin/queues', { method: 'DELETE' });
    toast('All queues cleared', 'warn');
    refreshStats();
}

/* ── Bootstrap ───────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    // Payload template on job form
    const jobTypeEl = document.getElementById('jobType');
    if (jobTypeEl) {
        updatePayloadTemplate(jobTypeEl.value);
        jobTypeEl.addEventListener('change', e => updatePayloadTemplate(e.target.value));
    }

    // Wire filter controls
    ['filter-status', 'filter-priority', 'filter-search'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', renderJobs);
    });

    // Initial data load
    loadJobs();
    refreshStats();

    // Start polling intervals (10s)
    setLiveMode(true);

    // SSE connection
    connectSse();

    // Keep-alive ping every 4 minutes
    keepAlivePing();
    setInterval(keepAlivePing, 4 * 60 * 1000);
});
