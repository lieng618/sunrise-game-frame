import {ElementPlus} from '@/plugins/element-plus';

function loadSessionFlags() {
    const isAdmin = localStorage.getItem('admin_is_admin') === '1';
    let permissions = [];
    try {
        const saved = localStorage.getItem('admin_permissions');
        if (saved) permissions = JSON.parse(saved);
    } catch {
        permissions = [];
    }
    const hasPermission = (key) =>
        isAdmin || (Array.isArray(permissions) && permissions.includes(key));
    return {isAdmin, hasPermission};
}

export function setupRouterGuards(router) {
    router.beforeEach((to, _from, next) => {
        const token = localStorage.getItem('admin_token');
        if (!token) {
            next();
            return;
        }

        if (to.meta.adminOnly) {
            const {isAdmin} = loadSessionFlags();
            if (!isAdmin) {
                ElementPlus.ElMessage.warning('权限不足：只有管理员可以访问用户管理');
                next(false);
                return;
            }
        } else if (to.meta.permission) {
            const {hasPermission} = loadSessionFlags();
            if (!hasPermission(to.meta.permission)) {
                ElementPlus.ElMessage.warning('权限不足：无法访问该页面');
                next(false);
                return;
            }
        }

        next();
    });
}
