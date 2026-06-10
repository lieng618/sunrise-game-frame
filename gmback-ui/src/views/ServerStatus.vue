<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <el-alert
        v-if="statusLoadError"
        class="load-error-banner"
        type="error"
        :title="statusLoadError"
        show-icon
        :closable="false"
    />
    <el-form label-width="150px" label-position="left">
      <el-form-item label="禁止新客户端连接">
        <div class="flex items-center gap-3 gm-field-md">
          <el-switch
              v-model="isClosed"
              :loading="loading"
              :disabled="!!statusLoadError && !statusLoaded"
              @change="handleSwitch"
          />
          <span :class="isClosed ? 'status-label--closed' : 'status-label--open'">
            <template v-if="statusLoadError && !statusLoaded">状态未知 — 请检查 GM 服务连接</template>
            <template v-else>{{ isClosed ? '已关闭 — 客户端无法连接' : '已开启 — 客户端可正常连接' }}</template>
          </span>
        </div>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {apiFetch, handleApiResult, apiMsg, confirmDialog, isUserCancel, MSG} from '@/utils';

export default {
  setup() {
    const state = reactive({
      isClosed: false,
      loading: false,
      statusLoaded: false,
      statusLoadError: '',
    });

    const fetchStatus = async () => {
      state.statusLoadError = '';
      const result = await apiFetch('/api/server-status');
      if (result.unauthorized) return;
      const status = handleApiResult(result, {errorMsg: '服务器状态加载失败'});
      if (status === 'ok') {
        state.isClosed = !result.data.data.open;
        state.statusLoaded = true;
      } else if (status === 'network') {
        state.statusLoadError = MSG.NETWORK;
      } else if (status === 'failed') {
        state.statusLoadError = apiMsg(result, '服务器状态加载失败');
      }
    };

    const handleSwitch = async (val) => {
      if (!state.statusLoaded) {
        ElementPlus.ElMessage.warning('状态未加载完成，请刷新页面后重试');
        state.isClosed = !val;
        return;
      }
      try {
        await confirmDialog(
            val ? '确定要关闭服务器吗？关闭后客户端将无法连接！' : '确定要开启服务器吗？开启后客户端可以正常连接。',
            val ? '关闭服务器确认' : '开启服务器确认'
        );
      } catch (e) {
        if (isUserCancel(e)) state.isClosed = !val;
        return;
      }

      state.loading = true;
      const result = await apiFetch('/api/server-status', {
        method: 'POST',
        body: {open: !val},
      });
      if (result.unauthorized) {
        state.loading = false;
        return;
      }
      if (handleApiResult(result, {errorMsg: '切换服务器状态失败'}) === 'ok') {
        ElementPlus.ElMessage.success(val ? '服务器已关闭' : '服务器已开启');
        state.statusLoaded = true;
      } else {
        state.isClosed = !val;
      }
      state.loading = false;
    };

    onMounted(fetchStatus);

    return {...toRefs(state), handleSwitch, fetchStatus};
  },
};
</script>
