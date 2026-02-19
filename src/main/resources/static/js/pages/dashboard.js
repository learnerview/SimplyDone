// dashboard page - polls stats and queues, handles job submission
document.addEventListener('DOMContentLoaded', () => {
    const elements = {
        submitForm: document.getElementById('submit-job-form'),
        stats: {
            health: document.getElementById('redis-health'),
            executed: document.getElementById('executed-count'),
            rejected: document.getElementById('rejected-count'),
            rate: document.getElementById('success-rate')
        },
        queues: {
            high: document.getElementById('high-priority-list'),
            low: document.getElementById('low-priority-list'),
            highCount: document.getElementById('high-count'),
            lowCount: document.getElementById('low-count')
        }
    };

    // fetch stats and update the counter cards at the top
    const updateStats = async () => {
        try {
            const stats = await API.getStats();
            const health = await API.getHealth();

            if (elements.stats.health) {
                elements.stats.health.innerText = health.queues ? 'System Active' : 'Service Issue';
                const indicator = elements.stats.health.previousElementSibling;
                if (indicator) {
                    indicator.style.background = health.queues ? 'var(--success)' : 'var(--danger)';
                }
            }

            if (elements.stats.executed) elements.stats.executed.innerText = stats.executedJobs.toLocaleString();
            if (elements.stats.rejected) elements.stats.rejected.innerText = stats.rejectedJobs.toLocaleString();

            const total = stats.executedJobs + stats.rejectedJobs;
            const rate = total > 0 ? ((stats.executedJobs / total) * 100).toFixed(1) : '100';
            if (elements.stats.rate) elements.stats.rate.innerText = `${rate}%`;
        } catch (err) {
            console.error('Stats poll failed', err);
        }
    };

    // fetch both queue lists and render them into the table bodies
    const updateQueues = async () => {
        try {
            const high = await API.getQueueJobs('high');
            const low = await API.getQueueJobs('low');

            renderQueue(elements.queues.high, high);
            renderQueue(elements.queues.low, low);

            if (elements.queues.highCount) elements.queues.highCount.innerText = `${high.length} Items`;
            if (elements.queues.lowCount) elements.queues.lowCount.innerText = `${low.length} Items`;
        } catch (err) {
            console.error('Queue poll failed', err);
        }
    };

    // builds the table rows for a queue - shows job id, type, status and a cancel button
    const renderQueue = (container, jobs) => {
        if (!container) return;

        if (!jobs || jobs.length === 0) {
            container.innerHTML = '<tr><td colspan="4" class="text-center py-8 text-secondary">Queue is clear</td></tr>';
            return;
        }

        container.innerHTML = jobs.map(job => `
            <tr class="hover:bg-zinc-50/50 transition-colors">
                <td class="font-medium">
                    <span class="text-xs font-mono text-zinc-400">${job.id.substring(0, 8)}</span>
                </td>
                <td>
                    <span class="text-xs font-semibold px-2 py-1 rounded bg-zinc-100/10 text-zinc-400">
                        ${job.jobType || 'UNKNOWN'}
                    </span>
                </td>
                <td>${UI.renderStatusBadge(job.status || 'PENDING')}</td>
                <td>
                    <button class="btn btn-secondary p-1" title="Cancel Job" onclick="handleCancelJob('${job.id}')">
                        <i data-lucide="x" class="w-4 h-4 text-danger"></i>
                    </button>
                </td>
            </tr>
        `).join('');

        if (window.lucide) window.lucide.createIcons();
    };

    // cancel a job when the X button is clicked in the queue table
    window.handleCancelJob = async (jobId) => {
        try {
            await API.cancelJob(jobId);
            UI.showToast('Job cancelled', 'success');
            updateQueues();
        } catch (err) {
            UI.showToast(err.message || 'Cancel failed', 'error');
        }
    };

    // form submit - validate payload json, build request, send to backend
    if (elements.submitForm) {
        elements.submitForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const btn = e.target.querySelector('button');
            const originalContent = btn.innerHTML;

            const userId = document.getElementById('user-id').value;
            const jobType = document.getElementById('job-type').value;
            const priority = document.getElementById('priority').value;
            const message = document.getElementById('message').value;

            // Collect parameters from the active section
            const parameters = {};
            const activeSection = document.getElementById(`params-${jobType}`);
            if (activeSection) {
                activeSection.querySelectorAll('input, select, textarea').forEach(input => {
                    if (input.name) {
                        const value = input.type === 'checkbox' ? input.checked : input.value;
                        if (value !== "" && value !== null) {
                            // Try to parse JSON if the field looks like it
                            if (input.name === 'payload' || input.name === 'data' || input.name === 'transformRules') {
                                try {
                                    parameters[input.name] = JSON.parse(value);
                                } catch (e) {
                                    parameters[input.name] = value;
                                }
                            } else {
                                parameters[input.name] = value;
                            }
                        }
                    }
                });
            }

            const data = {
                userId: userId,
                jobType: jobType,
                priority: priority,
                parameters: parameters,
                message: message || `Manual submission of ${jobType}`
            };

            try {
                btn.disabled = true;
                btn.innerHTML = '<i data-lucide="loader-2" class="animate-spin"></i><span>Dispatching...</span>';
                if (window.lucide) window.lucide.createIcons();

                await API.submitJob(data);
                UI.showToast(`${jobType} job queued!`, 'success');

                e.target.reset();
                // Reset dynamic fields visibility
                UI.toggleJobFields(document.getElementById('job-type').value);

                await updateQueues();
                await updateStats();
            } catch (err) {
                UI.showToast(err.message || 'Failed to schedule job', 'error');
            } finally {
                btn.disabled = false;
                btn.innerHTML = originalContent;
                if (window.lucide) window.lucide.createIcons();
            }
        });
    }

    // poll every few seconds to keep data fresh
    setInterval(updateStats, 3000);
    setInterval(updateQueues, 5000);
    updateStats();
    updateQueues();
});
