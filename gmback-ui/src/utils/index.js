import {ElementPlus} from '@/plugins/element-plus';

export {apiFetch, getAuthToken, clearAuthStorage, handleLoginExpired} from '@/api/client';

export function isApiSuccess(result) {
    return result?.ok && result?.data?.code === 200;
}

export function apiMsg(result, fallback = '操作失败') {
    return result?.data?.msg || fallback;
}

export function formatTime(ts) {
    if (!ts) return '-';
    return new Date(ts).toLocaleString();
}

export function defaultPagination(size = 20) {
    return {page: 1, size, total: 0};
}

export function buildPageQuery(page, size, filters = {}) {
    const params = new URLSearchParams({
        page: String(page),
        size: String(size),
    });
    for (const [key, value] of Object.entries(filters)) {
        if (value != null && value !== '') {
            params.set(key, String(value));
        }
    }
    return params.toString();
}

/** 确认对话框，取消时抛出 'cancel' */
export async function confirmDialog(message, title = '提示', extra = {}) {
    return ElementPlus.ElMessageBox.confirm(message, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        ...extra,
    });
}

export function isUserCancel(error) {
    return error === 'cancel';
}
