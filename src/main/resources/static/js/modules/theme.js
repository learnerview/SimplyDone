/**
 * SimplyDone Theme Engine
 */
const Theme = {
    CURRENT_THEME_KEY: 'simplydone-theme',

    init() {
        const savedTheme = localStorage.getItem(this.CURRENT_THEME_KEY) ||
            (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        this.setTheme(savedTheme);
    },

    toggle() {
        const newTheme = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        this.setTheme(newTheme);
    },

    setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(this.CURRENT_THEME_KEY, theme);
        this.updateIcon(theme);
    },

    updateIcon(theme) {
        const icon = document.getElementById('theme-icon');
        if (!icon) return;

        // Simple icon change logic - using Lucide-like SVG paths could go here
        icon.innerHTML = theme === 'dark' ?
            '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"></path></svg>' :
            '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2"></path><path d="M12 20v2"></path><path d="m4.93 4.93 1.41 1.41"></path><path d="m17.66 17.66 1.41 1.41"></path><path d="M2 12h2"></path><path d="M22 12h2"></path><path d="m6.34 17.66-1.41 1.41"></path><path d="m19.07 4.93-1.41 1.41"></path></svg>';
    }
};

const Sidebar = {
    toggle() {
        const sidebar = document.querySelector('.sidebar');
        if (!sidebar) return;
        sidebar.classList.toggle('collapsed');
        localStorage.setItem('simplydone-sidebar-collapsed', sidebar.classList.contains('collapsed'));
    },
    init() {
        const isCollapsed = localStorage.getItem('simplydone-sidebar-collapsed') === 'true';
        const sidebar = document.querySelector('.sidebar');
        if (sidebar && isCollapsed) {
            sidebar.classList.add('collapsed');
        }
    }
};

window.Theme = Theme;
window.Sidebar = Sidebar;
Theme.init();
document.addEventListener('DOMContentLoaded', () => Sidebar.init());
