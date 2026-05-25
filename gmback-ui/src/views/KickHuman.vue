<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <el-form :model="form" label-width="120px" label-position="left">
      <el-form-item label="玩家ID" required>
        <el-input v-model="form.humanId" placeholder="请输入玩家ID" style="width: 400px;"></el-input>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="kick" :loading="sending" size="large">
          执行下线
        </el-button>
        <el-button @click="resetForm" size="large">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import {reactive, toRefs} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {apiFetch, isApiSuccess} from '@/utils';

export default {
  setup() {
    const state = reactive({
      sending: false,
      form: {
        humanId: '',
      }
    });

    const resetForm = () => {
      state.form = {
        humanId: '',
      };
    };

    const kick = async () => {
      // 验证必填项
      if (!state.form.humanId || !state.form.humanId.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家ID');
        return;
      }

      // 构建请求数据
      const requestData = {
        humanId: state.form.humanId.trim(),
      };

      state.sending = true;
      try {
        const result = await apiFetch('/api/gm/kick', {method: 'POST', body: requestData});
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('玩家下线指令已发送');
          resetForm();
        } else {
          ElementPlus.ElMessage.error(result.data?.msg || '发送失败');
        }
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
      } finally {
        state.sending = false;
      }
    };

    return {
      ...toRefs(state),
      resetForm,
      kick
    };
  }
};
</script>