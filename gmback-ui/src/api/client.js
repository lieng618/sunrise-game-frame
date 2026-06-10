import {MSG} from '@/utils/messages';

export function getAuthToken() {
    return localStorage.getItem('admin_token');
}

export function clearAuthStorage() {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_username');
    localStorage.removeItem('admin_permissions');
    localStorage.removeItem('admin_is_admin');
}

export function handleLoginExpired() {
    clearAuthStorage();
    window.location.href = '/';
}

/**
 * 带鉴权的 API 请求
 * @param {string} url
 * @param {{ method?: string, body?: any, headers?: Record<string,string>, parseJson?: boolean, auth?: boolean }} options
 */
export async function apiFetch(url, options = {}) {
    const {
        method = 'GET',
        body,
        headers = {},
        parseJson = true,
        auth = true,
    } = options;

    const init = {
        method,
        headers: {...headers},
    };

    if (auth) {
        const token = getAuthToken();
        if (token) {
            init.headers.Authorization = token;
        }
    }

    if (body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
    }

    let res;
    try {
        res = await fetch(url, init);
    } catch (error) {
        return {
            ok: false,
            status: 0,
            data: null,
            networkError: true,
            error,
            res: null,
        };
    }

    if (res.status === 401) {
        handleLoginExpired();
        return {ok: false, status: 401, data: null, unauthorized: true, res};
    }

    let data = null;
    if (parseJson) {
        try {
            data = await res.json();
        } catch {
            data = null;
        }
    }

    if (!res.ok && data == null) {
        return {
            ok: false,
            status: res.status,
            data: {code: res.status, msg: MSG.LOAD_FAILED},
            res,
        };
    }

    return {ok: res.ok, status: res.status, data, res};
}

if (typeof window !== 'undefined') {
    window.handleLoginExpired = handleLoginExpired;
}
