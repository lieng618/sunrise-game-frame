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
        init.headers.Authorization = getAuthToken();
    }

    if (body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
    }

    const res = await fetch(url, init);

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

    return {ok: res.ok, status: res.status, data, res};
}

if (typeof window !== 'undefined') {
    window.handleLoginExpired = handleLoginExpired;
}
