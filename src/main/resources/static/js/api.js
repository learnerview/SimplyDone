/**
 * SimplyDone API Client
 * 
 * Centralized module for all backend interactions.
 */
const API = {
    baseUrl: '/api',

    async getStats() {
        return this._fetch('/admin/stats');
    },

    async getHealth() {
        return this._fetch('/admin/health');
    },

    async getPerformance() {
        return this._fetch('/admin/performance');
    },

    async getQueueJobs(priority) {
        return this._fetch(`/admin/queues/${priority.toLowerCase()}`);
    },

    async getDeadLetterJobs() {
        return this._fetch('/admin/dead-letter-queue');
    },

    async submitJob(jobData) {
        return this._fetch('/jobs', {
            method: 'POST',
            body: JSON.stringify(jobData)
        });
    },

    async cancelJob(jobId) {
        return this._fetch(`/jobs/${jobId}`, {
            method: 'DELETE'
        });
    },

    async retryJob(jobId) {
        return this._fetch(`/admin/dead-letter-queue/${jobId}/retry`, {
            method: 'POST'
        });
    },

    async clearAllQueues() {
        return this._fetch('/admin/queues/clear', {
            method: 'DELETE'
        });
    },

    async getRateLimit(userId) {
        return this._fetch(`/jobs/rate-limit/${userId}`);
    },

    /**
     * Internal fetch helper with error handling
     */
    async _fetch(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json'
            }
        };

        try {
            const response = await fetch(url, { ...defaultOptions, ...options });
            const data = await response.json();

            if (!response.ok) {
                const error = new Error(data.message || 'API request failed');
                error.status = response.status;
                error.data = data;
                throw error;
            }

            return data;
        } catch (err) {
            console.error(`API Error [${endpoint}]:`, err);
            throw err;
        }
    }
};
