/**
 * Toast Notification System
 * Modern, animated toast notifications
 */

class ToastManager {
    constructor() {
        this.container = null;
        this.init();
    }

    /**
     * Initialize toast container
     */
    init() {
        if (!document.getElementById('toast-container')) {
            this.container = document.createElement('div');
            this.container.id = 'toast-container';
            this.container.style.cssText = `
                position: fixed;
                top: 2rem;
                right: 2rem;
                z-index: 9999;
                display: flex;
                flex-direction: column;
                gap: 1rem;
                pointer-events: none;
            `;
            document.body.appendChild(this.container);
        } else {
            this.container = document.getElementById('toast-container');
        }
    }

    /**
     * Show a toast notification
     */
    show(message, type = 'info', duration = 4000) {
        const toast = this.createToast(message, type);
        this.container.appendChild(toast);

        // Animate in
        requestAnimationFrame(() => {
            toast.classList.add('show');
        });

        // Auto dismiss
        if (duration > 0) {
            setTimeout(() => {
                this.dismiss(toast);
            }, duration);
        }

        return toast;
    }

    /**
     * Create toast element
     */
    createToast(message, type) {
        const toast = document.createElement('div');
        toast.className = 'toast notification-enter';
        toast.style.cssText = `
            min-width: 320px;
            max-width: 500px;
            padding: 1rem 1.25rem;
            background: var(--surface-color);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            backdrop-filter: blur(var(--blur-lg)) saturate(180%);
            box-shadow: var(--card-shadow-hover);
            display: flex;
            align-items: center;
            gap: 0.875rem;
            pointer-events: all;
            cursor: pointer;
            transition: all var(--transition-base);
            opacity: 0;
            transform: translateX(100%);
        `;

        const icon = this.getIcon(type);
        const iconColor = this.getIconColor(type);
        
        toast.innerHTML = `
            <div style="
                width: 36px;
                height: 36px;
                border-radius: var(--radius-md);
                background: ${iconColor}15;
                display: flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
            ">
                <i data-lucide="${icon}" style="width: 18px; height: 18px; color: ${iconColor};"></i>
            </div>
            <div style="flex: 1; font-size: 0.9375rem; color: var(--text-primary);">
                ${message}
            </div>
            <button class="toast-close" style="
                width: 28px;
                height: 28px;
                border-radius: var(--radius-md);
                background: transparent;
                border: none;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                color: var(--text-secondary);
                transition: all var(--transition-fast);
                flex-shrink: 0;
            " onmouseover="this.style.background='var(--glass-tint)'; this.style.color='var(--text-primary)';" 
               onmouseout="this.style.background='transparent'; this.style.color='var(--text-secondary)';">
                <i data-lucide="x" style="width: 16px; height: 16px;"></i>
            </button>
        `;

        // Re-initialize icons
        if (typeof lucide !== 'undefined') {
            setTimeout(() => lucide.createIcons(), 0);
        }

        // Add border color based on type
        toast.style.borderLeftWidth = '4px';
        toast.style.borderLeftColor = iconColor;

        // Show on append
        setTimeout(() => {
            toast.style.opacity = '1';
            toast.style.transform = 'translateX(0)';
        }, 10);

        // Click to dismiss
        toast.addEventListener('click', (e) => {
            if (!e.target.closest('.toast-close')) {
                this.dismiss(toast);
            }
        });

        // Close button
        toast.querySelector('.toast-close').addEventListener('click', (e) => {
            e.stopPropagation();
            this.dismiss(toast);
        });

        // Hover to pause auto-dismiss
        let dismissTimeout;
        toast.addEventListener('mouseenter', () => {
            if (toast.dismissTimeout) {
                clearTimeout(toast.dismissTimeout);
            }
        });

        return toast;
    }

    /**
     * Dismiss a toast
     */
    dismiss(toast) {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }

    /**
     * Get icon for toast type
     */
    getIcon(type) {
        const icons = {
            success: 'check-circle',
            error: 'x-circle',
            warning: 'alert-triangle',
            info: 'info',
            loading: 'loader-2'
        };
        return icons[type] || icons.info;
    }

    /**
     * Get color for toast type
     */
    getIconColor(type) {
        const colors = {
            success: 'var(--success-500)',
            error: 'var(--danger-500)',
            warning: 'var(--warning-500)',
            info: 'var(--brand-500)',
            loading: 'var(--brand-500)'
        };
        return colors[type] || colors.info;
    }

    /**
     * Convenience methods
     */
    success(message, duration) {
        return this.show(message, 'success', duration);
    }

    error(message, duration) {
        return this.show(message, 'error', duration);
    }

    warning(message, duration) {
        return this.show(message, 'warning', duration);
    }

    info(message, duration) {
        return this.show(message, 'info', duration);
    }

    loading(message) {
        return this.show(message, 'loading', 0);
    }

    /**
     * Clear all toasts
     */
    clearAll() {
        const toasts = this.container.querySelectorAll('.toast');
        toasts.forEach(toast => this.dismiss(toast));
    }
}

// Create global instance
const Toast = new ToastManager();

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = Toast;
}
