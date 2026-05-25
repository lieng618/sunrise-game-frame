<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <el-form label-width="150px" label-position="left">
      <el-form-item label="禁止新客户端连接">
        <div class="flex items-center gap-3" style="width: 400px;">
          <el-switch
              v-model="isClosed"
              active-color="#409EFF"
              inactive-color="#67C23A"
              size="default"
              :loading="loading"
              @change="handleSwitch">
          </el-switch>
          <span class="font-medium" :class="isClosed ? 'text-blue-500' : 'text-green-500'">
                        {{ isClosed ? '服务器已关闭' : '服务器已开启' }}
                    </span>
        </div>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {apiFetch, isApiSuccess, apiMsg, confirmDialog, isUserCancel} from '@/utils';

export default {
  setup() {
    const state = reactive({isClosed: false, loading: false});

    const fetchStatus = async () => {
      try {
        const result = await apiFetch('/api/server-status');
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.isClosed = !result.data.data.open;
        }
      } catch (e) {
        console.error(e);
      }
    };

    const handleSwitch = async (val) => {
      try {
        await confirmDialog(
            val ? '确定要关闭服务器吗？关闭后客户端将无法连接！' : '确定要开启服务器吗？开启后客户端可以正常连接。',
            val ? '关闭服务器确认' : '开启服务器确认'
        );
      } catch (e) {
        state.isClosed = !val;
        return;
      }

      state.loading = true;
      try {
        const result = await apiFetch('/api/server-status', {
          method: 'POST',
          body: {open: !val},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success(val ? '服务器已关闭' : '服务器已开启');
        } else {
          state.isClosed = !val;
          ElementPlus.ElMessage.error(apiMsg(result));
        }
      } catch (e) {
        state.isClosed = !val;
        ElementPlus.ElMessage.error('网络错误，请重试');
      } finally {
        state.loading = false;
      }
    };

    onMounted(fetchStatus);

    return {...toRefs(state), handleSwitch};
  },
};
</script>