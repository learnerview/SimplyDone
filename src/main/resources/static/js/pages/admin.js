// admin page - shows system vitals and dead letter queue management
document.addEventListener('DOMContentLoaded', () => {
  const elements = {
    perfStats: document.getElementById('perf-stats'),
    dlqTable: document.getElementById('dlq-table-body'),
    clearAllBtn: document.getElementById('clear-all-btn'),
    refreshBtn: document.getElementById('refresh-admin-btn')
  };

  // fetches performance metrics and dlq, then renders both sections
  const updateAdminData = async () => {
    try {
      const perf = await API.getPerformance();
      const jobs = await API.getDeadLetterJobs();

      renderPerformance(perf);
      renderDLQ(jobs);  // jobs is already the array after ApiResponse unwrap
      lucide.createIcons();
    } catch (err) {
      UI.showToast('Failed to sync system vitals', 'error');
    }
  };

  // renders the 3 stat cards using jvm + job processing data
  const renderPerformance = (perf) => {
    if (!perf || !perf.jvm || !perf.jobProcessing) {
      elements.perfStats.innerHTML = '<div class="glass-card text-secondary">No metrics available</div>';
      return;
    }
    const memUsage = (perf.jvm.usedMemory / perf.jvm.totalMemory * 100).toFixed(0);

    elements.perfStats.innerHTML = `
        <div class="glass-card">
          <div class="flex items-center gap-2 text-secondary text-xs mb-2">
            <i data-lucide="microchip" class="w-4 h-4"></i>
            <span>Heap Memory</span>
          </div>
          <div class="text-xl font-bold mb-2">
            ${(perf.jvm.usedMemory / 1024 / 1024).toFixed(1)} <span class="text-xs text-secondary font-normal">MB</span>
          </div>
          <div class="h-1.5 glass-border-b rounded-full overflow-hidden bg-white/5">
            <div class="h-full bg-brand rounded-full transition-all duration-500" style="width: ${memUsage}%"></div>
          </div>
          <div class="text-[10px] text-secondary mt-2 flex justify-between">
            <span>Utilization: ${memUsage}%</span>
            <span>Limit: ${(perf.jvm.maxMemory / 1024 / 1024).toFixed(0)}MB</span>
          </div>
        </div>

        <div class="glass-card">
          <div class="flex items-center gap-2 text-secondary text-xs mb-2">
            <i data-lucide="check-circle-2" class="w-4 h-4 text-brand"></i>
            <span>Efficiency</span>
          </div>
          <div class="text-xl font-bold mb-2 text-brand">${perf.jobProcessing.successRate.toFixed(1)}%</div>
          <div class="text-[10px] text-secondary mt-auto">
            ${perf.jobProcessing.executedJobs} Total Dispatches
          </div>
        </div>

        <div class="glass-card border-danger/20">
          <div class="flex items-center gap-2 text-secondary text-xs mb-2">
            <i data-lucide="x-circle" class="w-4 h-4 text-danger"></i>
            <span>Rejected</span>
          </div>
          <div class="text-xl font-bold mb-2 text-danger">${perf.jobProcessing.rejectedJobs}</div>
          <div class="text-[10px] text-secondary mt-auto">
            Jobs in Terminal Failure State
          </div>
        </div>
      `;
  };

  // renders the dead letter queue table - each row has a retry button
  const renderDLQ = (jobs) => {
    if (!jobs || jobs.length === 0) {
      elements.dlqTable.innerHTML = `
        <tr class="glass-border-b">
          <td colspan="5" class="px-6 py-12 text-center text-secondary">
            <i data-lucide="info" class="w-8 h-8 mx-auto mb-2 opacity-20"></i>
            <p>Dead Letter Queue is clear</p>
          </td>
        </tr>
      `;
      return;
    }

    elements.dlqTable.innerHTML = jobs.map(job => {
      const origId = (job.originalJob ? job.originalJob.id : job.id) || '';
      return `
        <tr class="glass-border-b hover:bg-white/[0.02] transition-colors">
          <td class="px-6 py-4 font-mono text-[10px] text-brand" title="${origId}">${origId.substring(0, 8)}...</td>
          <td class="px-6 py-4 truncate max-w-[200px]">${job.originalJob ? job.originalJob.message : job.message || 'N/A'}</td>
          <td class="px-6 py-4">${job.retryAttempts || job.retryCount || 0}</td>
          <td class="px-6 py-4 text-danger text-xs italic">${job.failureReason || 'Unknown'}</td>
          <td class="px-6 py-4 text-right">
            <button onclick="handleDLQRetry('${origId}')" class="btn btn-secondary py-1 px-3 text-xs">
              <i data-lucide="rotate-ccw" class="w-3 h-3"></i>
              <span>Re-run</span>
            </button>
          </td>
        </tr>
      `;
    }).join('');
  };

  // retry a dead letter job - moves it back into the processing queue
  window.handleDLQRetry = async (jobId) => {
    try {
      await API.retryJob(jobId);
      UI.showToast('Job queued for manual recovery', 'success');
      updateAdminData();
    } catch (err) {
      UI.showToast(err.message, 'error');
    }
  };

  elements.clearAllBtn.addEventListener('click', async () => {
    if (confirm('SYSTEM WARNING: This will immediately purge all pending and queued jobs. Proceed?')) {
      try {
        await API.clearAllQueues();
        UI.showToast('System queues flushed', 'success');
        updateAdminData();
      } catch (err) {
        UI.showToast('Purge failed', 'error');
      }
    }
  });

  elements.refreshBtn.addEventListener('click', () => {
    UI.showToast('Syncing system state...', 'info');
    updateAdminData();
  });

  updateAdminData();
  setInterval(updateAdminData, 10000);
});
