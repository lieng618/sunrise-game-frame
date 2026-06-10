import {ElementPlus} from '@/plugins/element-plus';
import {MSG} from './messages';

export {MSG};

export {apiFetch, getAuthToken, clearAuthStorage, handleLoginExpired} from '@/api/client';

export function isApiSuccess(result) {
    return result?.ok && result?.data?.code === 200;
}

export function apiMsg(result, fallback = '操作失败') {
    return result?.data?.msg || fallback;
}

export function safeArray(value) {
    return Array.isArray(value) ? value : [];
}

/**
 * 统一处理 API 响应
 * @returns {'ok'|'unauthorized'|'network'|'failed'}
 */
export function handleApiResult(result, {errorMsg = '操作失败', showToast = true} = {}) {
    if (!result) {
        if (showToast) ElementPlus.ElMessage.error(errorMsg);
        return 'failed';
    }
    if (result.unauthorized) return 'unauthorized';
    if (result.networkError) {
        if (showToast) ElementPlus.ElMessage.error(MSG.NETWORK);
        return 'network';
    }
    if (isApiSuccess(result)) return 'ok';
    if (showToast) ElementPlus.ElMessage.error(apiMsg(result, errorMsg));
    return 'failed';
}

export function parsePagedData(payload) {
    const data = payload?.data ?? payload ?? {};
    return {
        list: safeArray(data.list),
        total: Number(data.total) || 0,
        extra: data,
    };
}

export function formatTime(ts) {
    if (ts == null || ts === '') return '-';
    const d = new Date(typeof ts === 'number' ? ts : Number(ts) || ts);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleString();
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
