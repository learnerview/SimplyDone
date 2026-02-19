/**
 * Enhanced UI Utilities
 * Modern micro-interactions and UI enhancements
 */

const UIEnhancements = {
    /**
     * Initialize all UI enhancements
     */
    init() {
        this.initializeAnimations();
        this.initializeTooltips();
        this.initializeRippleEffects();
        this.initializeCardInteractions();
        this.initializeLucideIcons();
    },

    /**
     * Initialize page animations
     */
    initializeAnimations() {
        // Fade in page content
        const mainContent = document.querySelector('.main-content');
        if (mainContent) {
            mainContent.classList.add('fade-in');
        }

        // Stagger animation for stat cards
        const statCards = document.querySelectorAll('.stat-card');
        statCards.forEach((card, index) => {
            card.style.animationDelay = `${index * 0.1}s`;
            card.classList.add('fade-in-up');
        });

        // Intersection Observer for scroll animations
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('fade-in-up');
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.1
        });

        document.querySelectorAll('.glass-card').forEach(card => {
            observer.observe(card);
        });
    },

    /**
     * Initialize tooltips
     */
    initializeTooltips() {
        const tooltipElements = document.querySelectorAll('[data-tooltip]');
        
        tooltipElements.forEach(element => {
            const tooltipText = element.getAttribute('data-tooltip');
            
            element.addEventListener('mouseenter', (e) => {
                const tooltip = document.createElement('div');
                tooltip.className = 'tooltip show';
                tooltip.textContent = tooltipText;
                tooltip.id = 'active-tooltip';
                
                document.body.appendChild(tooltip);
                
                const rect = element.getBoundingClientRect();
                tooltip.style.top = `${rect.top - tooltip.offsetHeight - 8}px`;
                tooltip.style.left = `${rect.left + (rect.width / 2) - (tooltip.offsetWidth / 2)}px`;
            });
            
            element.addEventListener('mouseleave', () => {
                const tooltip = document.getElementById('active-tooltip');
                if (tooltip) {
                    tooltip.classList.remove('show');
                    setTimeout(() => tooltip.remove(), 200);
                }
            });
        });
    },

    /**
     * Add ripple effect to buttons
     */
    initializeRippleEffects() {
        document.querySelectorAll('.btn, .nav-item').forEach(button => {
            button.addEventListener('click', function(e) {
                const ripple = document.createElement('span');
                const rect = this.getBoundingClientRect();
                const size = Math.max(rect.width, rect.height);
                const x = e.clientX - rect.left - size / 2;
                const y = e.clientY - rect.top - size / 2;
                
                ripple.style.width = ripple.style.height = `${size}px`;
                ripple.style.left = `${x}px`;
                ripple.style.top = `${y}px`;
                ripple.style.position = 'absolute';
                ripple.style.borderRadius = '50%';
                ripple.style.background = 'rgba(255, 255, 255, 0.3)';
                ripple.style.pointerEvents = 'none';
                ripple.style.animation = 'ripple 0.6s ease-out';
                
                this.style.position = 'relative';
                this.style.overflow = 'hidden';
                this.appendChild(ripple);
                
                setTimeout(() => ripple.remove(), 600);
            });
        });
    },

    /**
     * Enhanced card interactions
     */
    initializeCardInteractions() {
        document.querySelectorAll('.glass-hover').forEach(card => {
            card.addEventListener('mouseenter', function() {
                this.style.transform = 'translateY(-6px) scale(1.02)';
            });
            
            card.addEventListener('mouseleave', function() {
                this.style.transform = '';
            });
        });
    },

    /**
     * Re-initialize Lucide icons after dynamic content
     */
    initializeLucideIcons() {
        if (typeof lucide !== 'undefined') {
            lucide.createIcons();
        }
    },

    /**
     * Show loading skeleton
     */
    showSkeleton(container) {
        if (!container) return;
        
        container.innerHTML = `
            <div class="skeleton" style="height: 60px; margin-bottom: 1rem; border-radius: var(--radius-lg);"></div>
            <div class="skeleton" style="height: 60px; margin-bottom: 1rem; border-radius: var(--radius-lg);"></div>
            <div class="skeleton" style="height: 60px; border-radius: var(--radius-lg);"></div>
        `;
    },

    /**
     * Smooth scroll to element
     */
    scrollTo(element) {
        if (element) {
            element.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    },

    /**
     * Copy text to clipboard with visual feedback
     */
    async copyToClipboard(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            
            if (button) {
                const originalHTML = button.innerHTML;
                button.innerHTML = '<i data-lucide="check"></i> Copied!';
                button.classList.add('btn-success');
                
                setTimeout(() => {
                    button.innerHTML = originalHTML;
                    button.classList.remove('btn-success');
                    this.initializeLucideIcons();
                }, 2000);
            }
            
            return true;
        } catch (err) {
            console.error('Failed to copy:', err);
            return false;
        }
    }
};

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    UIEnhancements.init();
});

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = UIEnhancements;
}
