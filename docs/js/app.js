const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function init() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);

    window.addEventListener('hashchange', handleRoute);
    handleRoute();

    document.getElementById('themeToggle').addEventListener('click', toggleTheme);
    document.getElementById('sidebarToggle').addEventListener('click', toggleSidebar);

    // Search
    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');

    function setSearchOpen(open) {
        searchResults.classList.toggle('active', open);
        searchInput.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.trim().toLowerCase();
        if (!query) {
            setSearchOpen(false);
            searchResults.innerHTML = '';
            return;
        }
        const results = searchIndex.filter(item =>
            item.title.toLowerCase().includes(query) ||
            item.desc.toLowerCase().includes(query)
        );
        if (results.length === 0) {
            searchResults.innerHTML = '<div class="search-result-empty"><div class="search-result-title">未找到匹配页面</div><div class="search-result-desc">换个关键词，或从左侧导航浏览章节</div></div>';
        } else {
            searchResults.innerHTML = results.map(item =>
                `<button type="button" class="search-result-item" role="option" onclick="navigateTo('${item.page}')">
                    <div class="search-result-title">${item.title}</div>
                    <div class="search-result-desc">${item.desc}</div>
                </button>`
            ).join('');
        }
        setSearchOpen(true);
    });

    searchInput.addEventListener('blur', () => {
        setTimeout(() => setSearchOpen(false), 200);
    });

    searchInput.addEventListener('focus', (e) => {
        if (e.target.value.trim()) {
            setSearchOpen(true);
        }
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.search-box')) {
            setSearchOpen(false);
        }
    });

    // Scroll progress bar
    const scrollProgress = document.getElementById('scrollProgress');
    const backToTop = document.getElementById('backToTop');

    window.addEventListener('scroll', () => {
        const scrollTop = window.scrollY;
        const docHeight = document.documentElement.scrollHeight - window.innerHeight;
        const progress = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
        scrollProgress.style.transform = `scaleX(${progress / 100})`;

        // Back to top visibility
        if (scrollTop > 300) {
            backToTop.classList.add('visible');
        } else {
            backToTop.classList.remove('visible');
        }
    }, { passive: true });

    backToTop.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: prefersReducedMotion ? 'auto' : 'smooth' });
    });

    // Keyboard shortcut: Ctrl/Cmd + K to focus search; arrow keys in results
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            e.preventDefault();
            searchInput.focus();
            return;
        }

        if (!searchResults.classList.contains('active')) return;

        const items = [...searchResults.querySelectorAll('.search-result-item:not([aria-disabled])')];
        if (!items.length) return;

        const current = document.activeElement;
        const index = items.indexOf(current);

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            items[index < 0 ? 0 : Math.min(index + 1, items.length - 1)]?.focus();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (index <= 0) {
                searchInput.focus();
            } else {
                items[index - 1]?.focus();
            }
        } else if (e.key === 'Enter' && index >= 0) {
            e.preventDefault();
            current.click();
        } else if (e.key === 'Escape') {
            setSearchOpen(false);
            searchInput.blur();
        }
    });
}

function handleRoute() {
    const hash = window.location.hash.slice(2) || 'home';
    const page = pages[hash];
    if (page) {
        const content = document.getElementById('contentInner');

        if (!prefersReducedMotion) {
            content.style.opacity = '0';
            content.style.transform = 'translateY(8px)';
        }

        const applyPage = () => {
            content.innerHTML = page();
            document.querySelectorAll('.nav-link').forEach(link => {
                link.classList.toggle('active', link.dataset.page === hash);
            });
            window.scrollTo(0, 0);

            const scrollProgress = document.getElementById('scrollProgress');
            if (scrollProgress) scrollProgress.style.transform = 'scaleX(0)';

            content.querySelectorAll('pre code').forEach(block => {
                hljs.highlightElement(block);
            });

            if (prefersReducedMotion) {
                content.style.opacity = '1';
                content.style.transform = 'none';
                return;
            }

            requestAnimationFrame(() => {
                content.style.transition = 'opacity 250ms cubic-bezier(0.16, 1, 0.3, 1), transform 250ms cubic-bezier(0.16, 1, 0.3, 1)';
                content.style.opacity = '1';
                content.style.transform = 'translateY(0)';
            });
        };

        if (prefersReducedMotion) {
            applyPage();
        } else {
            setTimeout(applyPage, 120);
        }
    }
}

function navigateTo(page) {
    window.location.hash = '#/' + page;
    document.getElementById('searchInput').value = '';
    document.getElementById('searchResults').classList.remove('active');
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('theme', next);

    const hljsTheme = document.getElementById('hljs-theme');
    hljsTheme.href = next === 'dark'
        ? 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css'
        : 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-light.min.css';
}

function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    sidebar.classList.toggle('open');
    let overlay = document.querySelector('.sidebar-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.className = 'sidebar-overlay';
        overlay.addEventListener('click', () => {
            sidebar.classList.remove('open');
            overlay.classList.remove('active');
        });
        document.querySelector('.layout').prepend(overlay);
    }
    overlay.classList.toggle('active', sidebar.classList.contains('open'));
}

function switchTab(btn, tabId) {
    const container = btn.closest('.tabs').parentElement;
    container.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    container.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(tabId).classList.add('active');
}

document.addEventListener('DOMContentLoaded', init);
