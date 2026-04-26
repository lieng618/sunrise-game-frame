function init() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);

    window.addEventListener('hashchange', handleRoute);
    handleRoute();

    document.getElementById('themeToggle').addEventListener('click', toggleTheme);
    document.getElementById('sidebarToggle').addEventListener('click', toggleSidebar);

    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');

    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.trim().toLowerCase();
        if (!query) {
            searchResults.classList.remove('active');
            return;
        }
        const results = searchIndex.filter(item =>
            item.title.toLowerCase().includes(query) ||
            item.desc.toLowerCase().includes(query)
        );
        if (results.length === 0) {
            searchResults.innerHTML = '<div class="search-result-item"><div class="search-result-title">无搜索结果</div></div>';
        } else {
            searchResults.innerHTML = results.map(item =>
                `<div class="search-result-item" onclick="navigateTo('${item.page}')">
                    <div class="search-result-title">${item.title}</div>
                    <div class="search-result-desc">${item.desc}</div>
                </div>`
            ).join('');
        }
        searchResults.classList.add('active');
    });

    searchInput.addEventListener('blur', () => {
        setTimeout(() => searchResults.classList.remove('active'), 200);
    });

    searchInput.addEventListener('focus', (e) => {
        if (e.target.value.trim()) {
            searchResults.classList.add('active');
        }
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.search-box')) {
            searchResults.classList.remove('active');
        }
    });
}

function handleRoute() {
    const hash = window.location.hash.slice(2) || 'home';
    const page = pages[hash];
    if (page) {
        const content = document.getElementById('contentInner');
        content.innerHTML = page();
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.toggle('active', link.dataset.page === hash);
        });
        window.scrollTo(0, 0);
        content.querySelectorAll('pre code').forEach(block => {
            hljs.highlightElement(block);
        });
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
