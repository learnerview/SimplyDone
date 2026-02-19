/**
 * SimplyDone UI Component Library
 */
const UI = {
    toastContainer: null,

    init() {
        this.initIcons();
        this.initToasts();
    },

    initIcons() {
        if (window.lucide) {
            window.lucide.createIcons();
        }
    },

    initToasts() {
        this.toastContainer = document.getElementById('toast-container');
        if (!this.toastContainer) {
            this.toastContainer = document.createElement('div');
            this.toastContainer.id = 'toast-container';
            this.toastContainer.style.cssText = 'position: fixed; top: 2rem; right: 2rem; z-index: 1000; display: flex; flex-direction: column; gap: 0.75rem;';
            document.body.appendChild(this.toastContainer);
        }
    },

    showToast(message, type = 'info') {
        if (!this.toastContainer) this.initToasts();

        const toast = document.createElement('div');
        toast.className = 'card glass glass-hover';
        toast.style.padding = '1rem 1.25rem';
        toast.style.minWidth = '280px';
        toast.style.display = 'flex';
        toast.style.alignItems = 'center';
        toast.style.gap = '0.75rem';
        toast.style.animation = 'slideIn 0.3s ease-out';

        const icon = type === 'error' ? 'alert-circle' : 'check-circle';
        const color = type === 'error' ? 'var(--danger)' : 'var(--success)';

        toast.innerHTML = `
            <i data-lucide="${icon}" style="color: ${color}"></i>
            <div style="font-size: 0.875rem; font-weight: 500;">${message}</div>
        `;

        this.toastContainer.appendChild(toast);
        this.initIcons();

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(-10px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    },

    // renders a colored badge based on job status like PENDING/EXECUTED/FAILED
    renderStatusBadge(status) {
        const colors = {
            'PENDING': 'background: #2563eb22; color: #60a5fa;',
            'EXECUTED': 'background: #16a34a22; color: #4ade80;',
            'FAILED': 'background: #dc262622; color: #f87171;',
            'CANCELLED': 'background: #71717a22; color: #a1a1aa;',
            'HIGH': 'background: #9333ea22; color: #c084fc;',
            'LOW': 'background: #0891b222; color: #67e8f9;'
        };
        const style = colors[status] || 'background: #71717a22; color: #a1a1aa;';
        return `<span class="badge" style="${style}">${status}</span>`;
    }
};

window.UI = UI;
document.addEventListener('DOMContentLoaded', () => UI.init());
