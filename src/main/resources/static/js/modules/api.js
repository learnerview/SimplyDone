// all backend calls go through this object
const API = {
    baseUrl: '/api',

    // stats for dashboard counters
    async getStats() { return this._fetch('/admin/stats'); },

    // health info for the status pill
    async getHealth() { return this._fetch('/admin/health'); },

    // jvm + job processing metrics for admin page
    async getPerformance() { return this._fetch('/admin/performance'); },

    // get jobs in a specific priority queue (high or low)
    async getQueueJobs(priority) { return this._fetch(`/admin/queues/${priority.toLowerCase()}`); },

    // jobs that failed all retries and landed in dead letter queue
    async getDeadLetterJobs() { return this._fetch('/admin/dead-letter-queue'); },

    // get jobs from the high priority queue (shortcut for getQueueJobs('high'))
    async getHighPriorityJobs() { return this._fetch('/admin/queues/high'); },

    // get jobs from the low priority queue (shortcut for getQueueJobs('low'))
    async getLowPriorityJobs() { return this._fetch('/admin/queues/low'); },

    // get a single job by its id
    async getJob(jobId) { return this._fetch(`/jobs/${jobId}`); },

    // check the rate limit status for a specific user
    async getRateLimit(userId) { return this._fetch(`/jobs/rate-limit/${userId}`); },

    // get all jobs submitted by a user
    async getJobsByUser(userId) { return this._fetch(`/admin/jobs/user/${userId}`); },

    // submit a new job to be scheduled
    async submitJob(jobData) {
        return this._fetch('/jobs', {
            method: 'POST',
            body: JSON.stringify(jobData)
        });
    },

    // cancel a specific job by id
    async cancelJob(jobId) {
        return this._fetch(`/jobs/${jobId}`, { method: 'DELETE' });
    },

    // move a dlq job back into the queue for another try
    async retryJob(jobId) {
        return this._fetch(`/admin/dead-letter-queue/${jobId}/retry`, { method: 'POST' });
    },

    // wipe everything from all queues
    async clearAllQueues() {
        return this._fetch('/admin/queues/clear', { method: 'DELETE' });
    },

    // base fetch wrapper - handles errors and unwraps ApiResponse.data
    async _fetch(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;
        const defaultOptions = { headers: { 'Content-Type': 'application/json' } };
        
        console.group(`🚀 API Request: ${options.method || 'GET'} ${endpoint}`);
        if (options.body) console.log('Payload:', JSON.parse(options.body));
        console.groupEnd();

        try {
            const response = await fetch(url, { ...defaultOptions, ...options });
            const json = await response.json();

            console.group(`✅ API Response: ${response.status} ${endpoint}`);
            console.log('Data:', json);
            console.groupEnd();

            if (!response.ok) {
                // Improved error extraction
                const errorMsg = json.message || (json.validationErrors ? 'Validation failed' : 'Request failed');
                const error = new Error(errorMsg);
                error.status = response.status;
                error.data = json;
                throw error;
            }
            // unwrap the ApiResponse wrapper so callers get the actual data
            return json.data !== undefined ? json.data : json;
        } catch (err) {
            console.group(`❌ API Error: ${endpoint}`);
            console.error('Message:', err.message);
            if (err.data) console.error('Details:', err.data);
            console.groupEnd();
            throw err;
        }
    }
};

window.API = API;
