
function handleLoginExpired() {
    localStorage.removeItem('admin_token');
    window.top.location.href = 'index.html';
}