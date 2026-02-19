/**
 * SimplyDone Dashboard Application Logic
 */
document.addEventListener('DOMContentLoaded', () => {
    // State management
    const state = {
        activeTab: 'high', // 'high', 'low', 'dlq'
        stats: null,
        health: null,
        pollingInterval: null
    };

    // DOM Elements
    const elements = {
        serviceIndicator: document.getElementById('service-indicator'),
        serviceStatus: document.getElementById('service-status'),
        redisHealth: document.getElementById('redis-health'),
        executedCount: document.getElementById('executed-count'),
        rejectedCount: document.getElementById('rejected-count'),
        successRate: document.getElementById('success-rate'),
        submitForm: document.getElementById('submit-job-form'),
        tableHeader: document.getElementById('table-header'),
        tableBody: document.getElementById('table-body'),
        tabs: document.querySelectorAll('.tab'),
        clearQueuesBtn: document.getElementById('clear-queues-btn'),
        refreshBtn: document.getElementById('refresh-btn'),
        toastContainer: document.getElementById('toast-container')
    };

    /**
     * Initialization
     */
    const init = () => {
        setupEventListeners();
        startPolling();
        refreshAll();
    };

    /**
     * Event Listeners
     */
    const setupEventListeners = () => {
        // Tab switching
        elements.tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                elements.tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                state.activeTab = tab.dataset.target;
                renderQueueTable();
            });
        });

        // Job Submission
        elements.submitForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const submitBtn = document.getElementById('submit-btn') || e.target.querySelector('button[type="submit"]');
            const originalText = submitBtn.innerText;

            let parameters = {};
            const payloadRaw = document.getElementById('payload').value.trim();
            if (payloadRaw) {
                try {
                    parameters = JSON.parse(payloadRaw);
                } catch (err) {
                    showToast('Invalid JSON in payload', 'error');
                    return;
                }
            }

            const formData = {
                userId: document.getElementById('user-id').value,
                jobType: document.getElementById('job-type').value,
                message: "Manual job submission", // or pull from somewhere if available
                priority: document.getElementById('priority').value,
                delay: parseInt(document.getElementById('delay')?.value) || 0,
                parameters: parameters
            };

            try {
                submitBtn.disabled = true;
                submitBtn.innerText = 'Scheduling...';

                const result = await API.submitJob(formData);
                showToast(`Job ${result.id.substring(0, 8)} scheduled!`, 'success');
                elements.submitForm.reset();
                refreshAll();
            } catch (err) {
                showToast(err.message, 'error');
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerText = originalText;
            }
        });

        // Admin Commands
        elements.clearQueuesBtn.addEventListener('click', async () => {
            if (confirm('Are you sure you want to clear ALL queues? This cannot be undone.')) {
                try {
                    await API.clearAllQueues();
                    showToast('All queues cleared successfully', 'success');
                    refreshAll();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            }
        });

        elements.refreshBtn.addEventListener('click', () => {
            refreshAll();
            showToast('Data refreshed', 'success');
        });
    };

    /**
     * Polling
     */
    const startPolling = () => {
        // Poll every 3 seconds for stats and health
        state.pollingInterval = setInterval(() => {
            updateStatsAndHealth();
            updateQueueData();
        }, 3000);
    };

    const refreshAll = () => {
        updateStatsAndHealth();
        updateQueueData();
    };

    /**
     * Data Refreshers
     */
    const updateStatsAndHealth = async () => {
        try {
            const stats = await API.getStats();
            const health = await API.getHealth();

            state.stats = stats;
            state.health = health;

            // Update UI
            elements.serviceIndicator.classList.add('online');
            elements.serviceStatus.innerText = 'Connected';

            elements.redisHealth.innerText = health.queues ? 'Connected' : 'Error';
            elements.redisHealth.style.color = health.queues ? 'var(--success-color)' : 'var(--danger-color)';

            elements.executedCount.innerText = stats.executedJobs.toLocaleString();
            elements.rejectedCount.innerText = stats.rejectedJobs.toLocaleString();

            const total = stats.executedJobs + stats.rejectedJobs;
            const rate = total > 0 ? ((stats.executedJobs / total) * 100).toFixed(1) : '100';
            elements.successRate.innerText = `${rate}%`;

        } catch (err) {
            elements.serviceIndicator.classList.remove('online');
            elements.serviceStatus.innerText = 'Disconnected';
            console.error('Failed to fetch heartbeat:', err);
        }
    };

    const updateQueueData = async () => {
        try {
            let data;
            if (state.activeTab === 'dlq') {
                const result = await API.getDeadLetterJobs();
                data = result.deadLetterJobs;
            } else {
                data = await API.getQueueJobs(state.activeTab);
            }

            state.queueData = data;
            renderQueueTable();
        } catch (err) {
            console.error('Failed to update queue data:', err);
        }
    };

    /**
     * Rendering Logic
     */
    const renderQueueTable = () => {
        const data = state.queueData || [];

        // Define Headers
        let headers = [];
        if (state.activeTab === 'dlq') {
            headers = ['Job ID', 'Original Msg', 'User', 'Attempts', 'Reason', 'Actions'];
        } else {
            headers = ['Job ID', 'Message', 'User', 'Status', 'Scheduled For', 'Actions'];
        }

        elements.tableHeader.innerHTML = headers.map(h => `<th>${h}</th>`).join('');

        // Define Rows
        if (data.length === 0) {
            elements.tableBody.innerHTML = `<tr><td colspan="${headers.length}" style="text-align: center; padding: 3rem; color: var(--text-secondary);">No jobs found in this queue.</td></tr>`;
            return;
        }

        elements.tableBody.innerHTML = data.map(job => {
            if (state.activeTab === 'dlq') {
                return `
                    <tr>
                        <td title="${job.id}">${job.id.substring(0, 8)}...</td>
                        <td>${job.originalJob.message}</td>
                        <td>${job.originalJob.userId}</td>
                        <td>${job.retryCount} / 3</td>
                        <td style="color: var(--danger-color); font-size: 0.75rem;">${job.failureReason}</td>
                        <td>
                            <button class="secondary" style="padding: 0.25rem 0.5rem; font-size: 0.7rem;" onclick="handleRetry('${job.id}')">Retry</button>
                        </td>
                    </tr>
                `;
            } else {
                return `
                    <tr>
                        <td title="${job.id}">${job.id.substring(0, 8)}...</td>
                        <td>${job.message}</td>
                        <td>${job.userId}</td>
                        <td><span class="priority-badge ${job.status === 'PENDING' ? 'priority-low' : 'priority-high'}">${job.status}</span></td>
                        <td>${job.executeAt ? new Date(job.executeAt).toLocaleTimeString() : 'Immediate'}</td>
                        <td>
                            <button class="secondary danger" style="padding: 0.25rem 0.5rem; font-size: 0.7rem;" onclick="handleCancel('${job.id}')">Cancel</button>
                        </td>
                    </tr>
                `;
            }
        }).join('');
    };

    /**
     * Global Event Handlers (Called from HTML string injection)
     */
    window.handleCancel = async (jobId) => {
        try {
            await API.cancelJob(jobId);
            showToast('Job cancelled', 'success');
            refreshAll();
        } catch (err) {
            showToast(err.message, 'error');
        }
    };

    window.handleRetry = async (jobId) => {
        try {
            await API.retryJob(jobId);
            showToast('Job queued for retry', 'success');
            refreshAll();
        } catch (err) {
            showToast(err.message, 'error');
        }
    };

    /**
     * Toast System
     */
    const showToast = (message, type = 'info') => {
        const toast = document.createElement('div');
        toast.className = 'stat-card pulsing';
        toast.style.padding = '1rem 1.5rem';
        toast.style.minWidth = '200px';
        toast.style.borderLeft = `4px solid var(--${type === 'error' ? 'danger' : 'success'}-color)`;
        toast.style.marginBottom = '0';

        toast.innerHTML = `
            <div style="font-size: 0.75rem; color: var(--text-secondary); margin-bottom: 0.25rem;">${type.toUpperCase()}</div>
            <div style="font-size: 0.875rem; font-weight: 500;">${message}</div>
        `;

        elements.toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(20px)';
            toast.style.transition = 'all 0.4s ease';
            setTimeout(() => toast.remove(), 400);
        }, 5000);
    };

    init();
});
