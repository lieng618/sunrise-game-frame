<template>
  <!-- 创建兑换码 -->
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="gm-text-emphasis">创建兑换码</span>
      </div>
    </template>
    <el-form :model="cdkForm" label-width="120px" label-position="left">
      <el-form-item label="生成方式" required>
        <el-radio-group v-model="cdkForm.randomGenerate">
          <el-radio :value="false">手动填写</el-radio>
          <el-radio :value="true">随机生成</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="兑换码" v-if="!cdkForm.randomGenerate" required>
        <el-input v-model="cdkForm.code" placeholder="如 vip666" class="gm-field-code"></el-input>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
            v-model="cdkForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            class="gm-field-date">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
            v-model="cdkForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            class="gm-field-date">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="可兑换数量" required>
        <el-input-number v-model="cdkForm.totalCount" :min="1" class="gm-field-sm"></el-input-number>
      </el-form-item>
      <el-form-item label="邮件模板ID">
        <el-input-number v-model="cdkForm.templateId" :min="1" class="gm-field-sm"></el-input-number>
      </el-form-item>
      <el-form-item label="奖励附件">
        <div class="gm-field-lg">
          <div v-for="(attachment, index) in cdkForm.attachments" :key="index"
               class="attachment-item flex items-center gap-3 mb-2">
            <el-input-number v-model="attachment.itemId" :min="1" placeholder="道具ID"
                             style="flex: 1;"></el-input-number>
            <el-input-number v-model="attachment.count" :min="1" placeholder="数量"
                             style="flex: 1;"></el-input-number>
            <el-button type="danger" size="small" @click="removeAttachment(index)"
                       :disabled="cdkForm.attachments.length <= 1" circle
                       aria-label="删除此附件">
              <el-icon>
                <Delete/>
              </el-icon>
            </el-button>
          </div>
          <el-button type="primary" @click="addAttachment">添加附件</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="addCdk" :loading="submitting">
          <el-icon class="mr-1">
            <Plus/>
          </el-icon>
          创建兑换码
        </el-button>
        <el-button @click="resetForm">
          <el-icon class="mr-1">
            <RefreshLeft/>
          </el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <!-- 兑换码列表 -->
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="page-toolbar">
        <span class="gm-text-emphasis">兑换码列表</span>
        <el-button type="primary" :icon="Refresh" @click="fetchCdkList" :loading="loadingData">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <el-table-column prop="id" label="ID" width="70"></el-table-column>
      <el-table-column prop="code" label="兑换码" width="160">
        <template #default="scope">
          <span class="gm-text-code">{{ scope.row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="120">
        <template #default="scope">
          <span>{{ scope.row.usedCount }} / {{ scope.row.totalCount }}</span>
          <span class="gm-text-muted">(剩{{ scope.row.totalCount - scope.row.usedCount }})</span>
        </template>
      </el-table-column>
      <el-table-column label="附件" min-width="180">
        <template #default="scope">
          <span v-if="!scope.row.attachments || scope.row.attachments.length === 0" class="cell-empty">无</span>
          <span v-else class="gm-text-secondary">
                        <span v-for="(a, i) in scope.row.attachments" :key="i">
                            {{ a.itemId }}×{{ a.count }}<span v-if="i < scope.row.attachments.length - 1">, </span>
                        </span>
                    </span>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170">
        <template #default="scope">{{ formatTime(scope.row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="endTime" label="结束时间" width="170">
        <template #default="scope">{{ formatTime(scope.row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row)" size="small">{{ getStatusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" align="center" fixed="right">
        <template #default="scope">
          <el-button type="primary" size="small" @click="openEditDialog(scope.row)" :icon="Edit">修改</el-button>
          <el-button type="warning" size="small" @click="openAdjustDialog(scope.row)">调数量</el-button>
          <el-button type="danger" size="small" plain @click="removeCdk(scope.row)" :icon="Delete">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-end mt-4">
      <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="fetchCdkList"
          @current-change="fetchCdkList">
      </el-pagination>
    </div>
  </el-card>

  <!-- 编辑对话框 -->
  <el-dialog v-model="editDialogVisible" title="编辑兑换码" width="600px">
    <el-form :model="editForm" label-width="120px">
      <el-form-item label="兑换码">
        <el-input v-model="editForm.code" disabled></el-input>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker v-model="editForm.startTime" type="datetime" format="YYYY-MM-DD HH:mm:ss"
                        value-format="x" class="gm-field-full"></el-date-picker>
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker v-model="editForm.endTime" type="datetime" format="YYYY-MM-DD HH:mm:ss"
                        value-format="x" class="gm-field-full"></el-date-picker>
      </el-form-item>
      <el-form-item label="邮件模板ID">
        <el-input-number v-model="editForm.templateId" :min="1" class="gm-field-full"></el-input-number>
      </el-form-item>
      <el-form-item label="奖励附件">
        <div v-for="(attachment, index) in editForm.attachments" :key="index"
             class="flex items-center gap-2 mb-2">
          <el-input-number v-model="attachment.itemId" :min="1" style="flex: 1;"></el-input-number>
          <el-input-number v-model="attachment.count" :min="1" style="flex: 1;"></el-input-number>
          <el-button type="danger" size="small" @click="removeEditAttachment(index)"
                     :disabled="editForm.attachments.length <= 1" circle
                     aria-label="删除此附件">
            <el-icon>
              <Delete/>
            </el-icon>
          </el-button>
        </div>
        <el-button type="primary" size="small" @click="addEditAttachment">添加附件</el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="updateCdk" :loading="updating">保存修改</el-button>
    </template>
  </el-dialog>

  <!-- 调整数量对话框 -->
  <el-dialog v-model="adjustDialogVisible" title="调整兑换数量" width="400px">
    <p class="gm-text-secondary mb-4">兑换码：<strong>{{ adjustForm.code }}</strong></p>
    <p class="gm-text-secondary mb-4">当前：已用 {{ adjustForm.usedCount }} / 总量 {{ adjustForm.totalCount }}</p>
    <el-form label-width="100px">
      <el-form-item label="调整数量">
        <el-input-number v-model="adjustForm.delta" placeholder="正数增加，负数减少"></el-input-number>
        <div class="form-hint">正数为增加总量，负数为减少总量</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adjustDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="adjustCount" :loading="adjusting">确认调整</el-button>
    </template>
  </el-dialog>
</template>

<script>
import {Delete, Edit, Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {
  apiFetch, isApiSuccess, apiMsg, formatTime, defaultPagination,
  buildPageQuery, confirmDialog, isUserCancel,
} from '@/utils';

export default {
  setup() {
    const state = reactive({
      submitting: false,
      updating: false,
      adjusting: false,
      loadingData: false,
      editDialogVisible: false,
      adjustDialogVisible: false,
      cdkForm: {
        code: '',
        randomGenerate: false,
        startTime: null,
        endTime: null,
        totalCount: 100,
        templateId: 1,
        attachments: [{itemId: null, count: null}],
      },
      editForm: {
        id: null,
        code: '',
        startTime: null,
        endTime: null,
        templateId: 1,
        attachments: [{itemId: null, count: null}],
      },
      adjustForm: {
        id: null,
        code: '',
        usedCount: 0,
        totalCount: 0,
        delta: 0,
      },
      tableData: [],
      pagination: defaultPagination(),
    });

    const addAttachment = () => {
      state.cdkForm.attachments.push({itemId: null, count: null});
    };
    const removeAttachment = (index) => {
      if (state.cdkForm.attachments.length > 1) {
        state.cdkForm.attachments.splice(index, 1);
      }
    };
    const addEditAttachment = () => {
      state.editForm.attachments.push({itemId: null, count: null});
    };
    const removeEditAttachment = (index) => {
      if (state.editForm.attachments.length > 1) {
        state.editForm.attachments.splice(index, 1);
      }
    };

    const filterValidAttachments = (attachments) => {
      return (attachments || []).filter(a => a.itemId && a.count);
    };

    const fetchCdkList = async () => {
      state.loadingData = true;
      try {
        const qs = buildPageQuery(state.pagination.page, state.pagination.size);
        const result = await apiFetch(`/api/cdk?${qs}`);
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.tableData = result.data.data.list || [];
          state.pagination.total = result.data.data.total || 0;
        }
      } catch (e) {
        console.error(e);
      } finally {
        state.loadingData = false;
      }
    };

    const addCdk = async () => {
      if (!state.cdkForm.randomGenerate && (!state.cdkForm.code || !state.cdkForm.code.trim())) {
        ElementPlus.ElMessage.warning('请输入兑换码');
        return;
      }
      if (!state.cdkForm.startTime || !state.cdkForm.endTime) {
        ElementPlus.ElMessage.warning('请选择生效时间');
        return;
      }
      if (parseInt(state.cdkForm.startTime) >= parseInt(state.cdkForm.endTime)) {
        ElementPlus.ElMessage.warning('结束时间必须晚于开始时间');
        return;
      }
      if (!state.cdkForm.totalCount || state.cdkForm.totalCount <= 0) {
        ElementPlus.ElMessage.warning('可兑换数量必须大于0');
        return;
      }

      state.submitting = true;
      try {
        const result = await apiFetch('/api/cdk', {
          method: 'POST',
          body: {
            code: state.cdkForm.code ? state.cdkForm.code.trim() : '',
            randomGenerate: state.cdkForm.randomGenerate,
            startTime: parseInt(state.cdkForm.startTime, 10),
            endTime: parseInt(state.cdkForm.endTime, 10),
            totalCount: state.cdkForm.totalCount,
            templateId: state.cdkForm.templateId,
            attachments: filterValidAttachments(state.cdkForm.attachments),
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('创建成功，兑换码：' + (result.data.data?.code || ''));
          resetForm();
          fetchCdkList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '创建失败'));
        }
      } catch (e) {
        ElementPlus.ElMessage.error('网络错误');
      } finally {
        state.submitting = false;
      }
    };

    const resetForm = () => {
      state.cdkForm = {
        code: '', randomGenerate: false,
        startTime: null, endTime: null,
        totalCount: 100, templateId: 1,
        attachments: [{itemId: null, count: null}]
      };
    };

    const openEditDialog = (row) => {
      state.editForm = {
        id: row.id,
        code: row.code,
        startTime: row.startTime,
        endTime: row.endTime,
        templateId: row.templateId || 1,
        attachments: row.attachments && row.attachments.length > 0
            ? JSON.parse(JSON.stringify(row.attachments))
            : [{itemId: null, count: null}]
      };
      state.editDialogVisible = true;
    };

    const updateCdk = async () => {
      if (!state.editForm.startTime || !state.editForm.endTime) {
        ElementPlus.ElMessage.warning('请选择生效时间');
        return;
      }
      state.updating = true;
      try {
        const result = await apiFetch('/api/cdk/update', {
          method: 'POST',
          body: {
            id: state.editForm.id,
            startTime: parseInt(state.editForm.startTime, 10),
            endTime: parseInt(state.editForm.endTime, 10),
            templateId: state.editForm.templateId,
            attachments: filterValidAttachments(state.editForm.attachments),
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('修改成功');
          state.editDialogVisible = false;
          fetchCdkList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '修改失败'));
        }
      } catch (e) {
        ElementPlus.ElMessage.error('网络错误');
      } finally {
        state.updating = false;
      }
    };

    const openAdjustDialog = (row) => {
      state.adjustForm = {
        id: row.id,
        code: row.code,
        usedCount: row.usedCount,
        totalCount: row.totalCount,
        delta: 0
      };
      state.adjustDialogVisible = true;
    };

    const adjustCount = async () => {
      if (!state.adjustForm.delta || state.adjustForm.delta === 0) {
        ElementPlus.ElMessage.warning('请输入非零的调整数量');
        return;
      }
      state.adjusting = true;
      try {
        const result = await apiFetch('/api/cdk/adjust-count', {
          method: 'POST',
          body: {id: state.adjustForm.id, delta: state.adjustForm.delta},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('数量调整成功');
          state.adjustDialogVisible = false;
          fetchCdkList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '调整失败'));
        }
      } catch (e) {
        ElementPlus.ElMessage.error('网络错误');
      } finally {
        state.adjusting = false;
      }
    };

    const removeCdk = async (row) => {
      try {
        await confirmDialog(`确定要删除兑换码「${row.code}」吗？`);
        const result = await apiFetch('/api/cdk/remove', {
          method: 'POST',
          body: {id: row.id},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('删除成功');
          fetchCdkList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '删除失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) ElementPlus.ElMessage.error('操作失败');
      }
    };

    const getStatusType = (row) => {
      const now = Date.now();
      const remaining = row.totalCount - row.usedCount;
      if (remaining <= 0) return 'info';
      if (now < row.startTime) return 'info';
      if (now >= row.startTime && now < row.endTime) return 'success';
      return 'danger';
    };

    const getStatusText = (row) => {
      const now = Date.now();
      const remaining = row.totalCount - row.usedCount;
      if (remaining <= 0) return '已领完';
      if (now < row.startTime) return '待生效';
      if (now >= row.startTime && now < row.endTime) return '生效中';
      return '已过期';
    };

    onMounted(() => fetchCdkList());

    return {
      ...toRefs(state),
      Refresh,
      Edit,
      Delete,
      addAttachment, removeAttachment, addEditAttachment, removeEditAttachment,
      fetchCdkList, addCdk, resetForm, openEditDialog, updateCdk,
      openAdjustDialog, adjustCount, removeCdk, formatTime, getStatusType, getStatusText
    };
  }
};
</script>