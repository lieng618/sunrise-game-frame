import {reactive, toRefs, computed, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ElementPlus} from '@/plugins/element-plus';
import {apiFetch, isApiSuccess} from '@/utils';
import {MENU_ITEMS, ROUTE_TITLES, getFirstAllowedRoute} from '@/constants/menu';

const state = reactive({
    isLoggedIn: false,
    loading: false,
    form: {user: '', pass: ''},
    isAdmin: false,
    permissions: [],
    sessionReady: false,
});

export function useAuth() {
    const route = useRoute();
    const router = useRouter();

    const hasPermission = (pageKey) => {
        if (state.isAdmin) return true;
        return Array.isArray(state.permissions) && state.permissions.includes(pageKey);
    };

    const visibleMenuItems = computed(() =>
        MENU_ITEMS.filter((item) => {
            if (item.adminOnly) return state.isAdmin;
            return hasPermission(item.key);
        })
    );

    const hasAnyPagePermission = computed(() => visibleMenuItems.value.length > 0);
    const activeMenuPath = computed(() => route.path);
    const pageTitle = computed(() => ROUTE_TITLES[route.name] || '');

    const savePermissions = (permissions, isAdmin) => {
        state.permissions = permissions || [];
        state.isAdmin = !!isAdmin;
        localStorage.setItem('admin_permissions', JSON.stringify(state.permissions));
        localStorage.setItem('admin_is_admin', state.isAdmin ? '1' : '0');
    };

    const loadPermissionsFromStorage = () => {
        try {
            const saved = localStorage.getItem('admin_permissions');
            if (saved) {
                const parsed = JSON.parse(saved);
                state.permissions = Array.isArray(parsed) ? parsed : [];
            }
            state.isAdmin = localStorage.getItem('admin_is_admin') === '1';
        } catch {
            state.permissions = [];
            state.isAdmin = false;
        }
    };

    const applyLegacySession = async (token) => {
        const savedPerms = localStorage.getItem('admin_permissions');
        if (savedPerms !== null) {
            try {
                const parsed = JSON.parse(savedPerms);
                state.permissions = Array.isArray(parsed) ? parsed : [];
            } catch {
                state.permissions = [];
            }
        } else {
            state.permissions = [];
        }
        try {
            const result = await apiFetch('/api/users', {
                headers: {Authorization: token},
            });
            state.isAdmin = isApiSuccess(result);
        } catch {
            state.isAdmin = false;
        }
        savePermissions(state.permissions, state.isAdmin);
        state.isLoggedIn = true;
        return true;
    };

    const refreshSession = async () => {
        const token = localStorage.getItem('admin_token');
        if (!token) {
            state.isLoggedIn = false;
            return false;
        }
        try {
            const result = await apiFetch('/api/auth/info', {
                headers: {Authorization: token},
            });
            if (result.unauthorized) {
                localStorage.removeItem('admin_token');
                localStorage.removeItem('admin_username');
                localStorage.removeItem('admin_permissions');
                localStorage.removeItem('admin_is_admin');
                state.isLoggedIn = false;
                state.permissions = [];
                state.isAdmin = false;
                return false;
            }
            if (isApiSuccess(result) && result.data.data) {
                const info = result.data.data;
                savePermissions(info.permissions, info.isAdmin);
                if (info.username) {
                    state.form.user = info.username;
                    localStorage.setItem('admin_username', info.username);
                }
                state.isLoggedIn = true;
                return true;
            }
            return await applyLegacySession(token);
        } catch (e) {
            console.error('刷新会话失败，使用本地缓存', e);
            return await applyLegacySession(token);
        }
    };

    const navigateToFirstAllowed = async () => {
        const path = getFirstAllowedRoute(hasPermission, state.isAdmin);
        if (path && route.path !== path) {
            await router.replace(path);
        }
    };

    const ensureRouteAllowed = async () => {
        if (!state.isLoggedIn) return;
        const meta = route.meta;
        if (meta?.adminOnly && !state.isAdmin) {
            await navigateToFirstAllowed();
            return;
        }
        if (meta?.permission && !hasPermission(meta.permission)) {
            await navigateToFirstAllowed();
        }
    };

    const handleLogin = async () => {
        if (!state.form.user || !state.form.pass) {
            ElementPlus.ElMessage.warning('请输入账号密码');
            return;
        }
        state.loading = true;
        try {
            const result = await apiFetch('/api/login', {
                method: 'POST',
                body: state.form,
                auth: false,
            });
            const data = result.data;
            if (data?.code === 200) {
                localStorage.setItem('admin_token', data.token);
                localStorage.setItem('admin_username', state.form.user);
                if (Array.isArray(data.permissions)) {
                    savePermissions(data.permissions, data.isAdmin);
                } else {
                    await refreshSession();
                }
                state.isLoggedIn = true;
                await navigateToFirstAllowed();
                ElementPlus.ElMessage.success('登录成功');
            } else {
                ElementPlus.ElMessage.error(data?.msg || '登录失败');
            }
        } catch {
            ElementPlus.ElMessage.error('网络连接错误');
        } finally {
            state.loading = false;
        }
    };

    const logout = async () => {
        state.isLoggedIn = false;
        state.form.pass = '';
        state.form.user = '';
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_username');
        localStorage.removeItem('admin_permissions');
        localStorage.removeItem('admin_is_admin');
        state.permissions = [];
        state.isAdmin = false;
        ElementPlus.ElMessage.success('已退出登录');
        await router.replace('/');
    };

    const bootstrap = async () => {
        const token = localStorage.getItem('admin_token');
        if (token) {
            const savedUsername = localStorage.getItem('admin_username');
            if (savedUsername) state.form.user = savedUsername;
            loadPermissionsFromStorage();
            const ok = await refreshSession();
            if (ok) await navigateToFirstAllowed();
        }
        state.sessionReady = true;
    };

    if (!useAuth._watchersBound) {
        useAuth._watchersBound = true;
        watch(
            () => state.isLoggedIn,
            async (loggedIn) => {
                if (loggedIn) await ensureRouteAllowed();
            }
        );
        watch(
            () => route.path,
            async () => {
                if (state.isLoggedIn) await ensureRouteAllowed();
            }
        );
    }

    return {
        ...toRefs(state),
        visibleMenuItems,
        hasAnyPagePermission,
        activeMenuPath,
        pageTitle,
        handleLogin,
        logout,
        bootstrap,
    };
}

useAuth._watchersBound = false;
