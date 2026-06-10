<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <el-form :model="form" label-width="120px" label-position="left">
      <el-form-item label="玩家ID" required>
        <div class="form-stack gm-field-md">
          <div class="form-stack__row">
            <span class="gm-text-emphasis">全服发送</span>
            <el-switch v-model="isServerWide"></el-switch>
          </div>
          <el-input v-model="form.humanId" placeholder="请输入玩家ID" :disabled="isServerWide"></el-input>
        </div>
      </el-form-item>

      <el-form-item label="邮件模板ID" required>
        <el-input-number v-model="form.templateId" :min="1" placeholder="请输入邮件模板ID"
                         class="gm-field-md"></el-input-number>
      </el-form-item>

      <el-form-item label="邮件附件">
        <div class="gm-field-lg">
          <div v-for="(attachment, index) in form.attachments" :key="index"
               class="attachment-item flex items-center gap-3">
            <el-input-number v-model="attachment.itemId" :min="1" placeholder="道具ID"
                             style="flex: 1;"></el-input-number>
            <el-input-number v-model="attachment.count" :min="1" placeholder="数量"
                             style="flex: 1;"></el-input-number>
            <el-button type="danger" size="small" @click="removeAttachment(index)"
                       :disabled="form.attachments.length <= 1" circle
                       aria-label="删除此附件">
              <el-icon>
                <Delete/>
              </el-icon>
            </el-button>
          </div>
          <el-button type="primary" @click="addAttachment">
            添加附件
          </el-button>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="sendMail" :loading="sending" :disabled="sending">
          发送邮件
        </el-button>
        <el-button @click="resetForm">清空</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import {reactive, toRefs, watch} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {apiFetch, handleApiResult, confirmDialog, isUserCancel} from '@/utils';

export default {
  setup() {
    const state = reactive({
      sending: false,
      isServerWide: false, // 全服发送开关，默认关闭
      form: {
        humanId: '',
        templateId: 1,
        senderName: '',
        attachments: [
          {itemId: null, count: null}
        ]
      }
    });

    // 监听全服发送开关变化
    watch(() => state.isServerWide, (newVal) => {
      if (newVal) {
        // 开启全服发送时，清空输入框
        state.form.humanId = '';
      } else {
        if (state.form.humanId === '-1') {
          state.form.humanId = '';
        }
      }
    });

    // 监听输入框变化，防止手动输入-1
    watch(() => state.form.humanId, (newVal) => {
      if (newVal === '-1' && !state.isServerWide) {
        // 如果不是全服发送模式，不允许输入-1
        state.form.humanId = '';
        ElementPlus.ElMessage.warning('非法输入');
      }
    });

    const addAttachment = () => {
      state.form.attachments.push({itemId: null, count: null});
    };

    const removeAttachment = (index) => {
      if (state.form.attachments.length > 1) {
        state.form.attachments.splice(index, 1);
      }
    };

    const resetForm = () => {
      state.isServerWide = false;
      state.form = {
        humanId: '',
        templateId: 1,
        senderName: '',
        attachments: [
          {itemId: null, count: null}
        ]
      };
    };

    const sendMail = async () => {
      // 如果开启了全服发送，进行二次确认
      if (state.isServerWide) {
        try {
          await confirmDialog(
              '确定要向全服玩家发送邮件吗？\n此操作将向所有在线玩家发送邮件，请谨慎操作！',
              '全服发送确认',
              {confirmButtonText: '确定发送'}
          );
        } catch (e) {
          if (isUserCancel(e)) return;
        }
      } else {
        // 单发模式：验证必填项
        if (!state.form.humanId || !state.form.humanId.trim()) {
          ElementPlus.ElMessage.warning('请输入玩家ID');
          return;
        }
      }

      // 验证邮件模板ID
      if (!state.form.templateId || state.form.templateId < 1) {
        ElementPlus.ElMessage.warning('请输入有效的邮件模板ID');
        return;
      }

      // 验证附件
      const validAttachments = [];
      for (const attachment of state.form.attachments) {
        if (attachment.itemId && attachment.count && attachment.itemId > 0 && attachment.count > 0) {
          validAttachments.push({
            itemId: attachment.itemId,
            count: attachment.count
          });
        }
      }

      // 构建请求数据
      const requestData = {
        humanId: state.isServerWide ? '-1' : state.form.humanId.trim(),
        templateId: state.form.templateId,
        senderName: state.form.senderName.trim() || null,
        attachments: validAttachments.length > 0 ? validAttachments : null
      };

      state.sending = true;
      const result = await apiFetch('/api/gm/send-mail', {
        method: 'POST',
        body: requestData,
      });
      if (result.unauthorized) {
        state.sending = false;
        return;
      }
      if (handleApiResult(result, {errorMsg: '邮件发送失败'}) === 'ok') {
        ElementPlus.ElMessage.success('邮件发送指令已发送');
        resetForm();
      }
      state.sending = false;
    };

    return {
      ...toRefs(state),
      addAttachment,
      removeAttachment,
      resetForm,
      sendMail
    };
  }
};
</script>