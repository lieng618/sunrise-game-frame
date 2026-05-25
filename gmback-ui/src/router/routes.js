import {MENU_ITEMS, pageKeyToComponentName} from '@/constants/menu';

/** 仅业务页懒加载；auth 等由 App 静态引用 */
const viewModules = import.meta.glob('../views/*.vue');

function lazyView(componentName) {
    const path = `../views/${componentName}.vue`;
    const loader = viewModules[path];
    if (!loader) {
        throw new Error(`View not found: ${componentName} (${path})`);
    }
    return loader;
}

function defaultRedirect() {
    try {
        const isAdmin = localStorage.getItem('admin_is_admin') === '1';
        let permissions = [];
        const saved = localStorage.getItem('admin_permissions');
        if (saved) permissions = JSON.parse(saved);
        const has = (key) =>
            isAdmin || (Array.isArray(permissions) && permissions.includes(key));
        for (const item of MENU_ITEMS) {
            if (item.adminOnly) {
                if (isAdmin) return item.path;
                continue;
            }
            if (has(item.key)) return item.path;
        }
    } catch {
        /* ignore */
    }
    return '/monitor';
}

export const routes = [
    {path: '/', redirect: defaultRedirect},
    ...MENU_ITEMS.map((item) => ({
        path: item.path,
        name: item.key,
        component: lazyView(pageKeyToComponentName(item.key)),
        meta: {
            title: item.title,
            permission: item.adminOnly ? null : item.key,
            adminOnly: !!item.adminOnly,
        },
    })),
];
