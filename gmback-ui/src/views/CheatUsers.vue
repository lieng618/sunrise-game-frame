<template>
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="font-medium">添加金手指用户</span>
      </div>
    </template>
    <el-form :model="addForm" label-width="110px" label-position="left">
      <el-form-item label="添加方式" required>
        <el-radio-group v-model="addForm.targetType">
          <el-radio value="uid">玩家 UID</el-radio>
          <el-radio value="humanId">游戏角色 ID</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="addForm.targetType === 'uid' ? '玩家 UID' : '游戏角色 ID'" required>
        <el-input
            v-model="addForm.target"
            :placeholder="addForm.targetType === 'uid' ? '请输入玩家 UID' : '请输入游戏角色 ID'"
            class="gm-field-lg">
        </el-input>
        <div class="form-hint">
          UID 授权该账号下的所有角色，角色 ID 仅授权当前角色。
        </div>
      </el-form-item>
      <el-form-item label="GM 能力" required>
        <el-checkbox-group v-model="addForm.abilities" class="flex flex-wrap gap-2">
          <el-checkbox
              v-for="ability in abilities"
              :key="ability.command"
              :value="ability.command"
              border>
            {{ ability.label }}（{{ ability.command }}）
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="addForm.remark" placeholder="请输入备注（非必须）" class="gm-field-lg"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="addCheatUser" :loading="adding">
          <el-icon class="mr-1">
            <Plus/>
          </el-icon>
          添加
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <span class="font-medium">金手指用户列表</span>
        <el-button type="primary" :icon="Refresh" @click="fetchCheatUsers" :loading="loadingData">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <el-table-column label="授权对象" width="130">
        <template #default="scope">
          <el-tag :type="scope.row.uid ? 'primary' : 'warning'" size="small">
            {{ scope.row.uid ? '玩家 UID' : '角色 ID' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="UID / 角色 ID" min-width="220">
        <template #default="scope">
          <span class="gm-text-emphasis">{{ scope.row.uid || scope.row.humanId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="GM 能力" min-width="280">
        <template #default="scope">
          <div class="flex flex-wrap gap-1">
            <el-tag v-for="command in scope.row.abilities" :key="command" size="small">
              {{ abilityLabel(command) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160">
        <template #default="scope">
          <span class="gm-text-secondary">{{ scope.row.remark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="添加时间" width="180">
        <template #default="scope">{{ formatTime(scope.row.addTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="scope">
          <el-button type="primary" size="small" @click="openEditDialog(scope.row)">编辑</el-button>
          <el-button type="danger" size="small" @click="removeCheatUser(scope.row)">移除</el-button>
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
          @size-change="fetchCheatUsers"
          @current-change="fetchCheatUsers">
      </el-pagination>
    </div>
  </el-card>

  <el-dialog v-model="editDialogVisible" title="编辑金手指权限" width="620px">
    <el-form :model="editForm" label-width="110px">
      <el-form-item label="授权对象">
        <el-input :model-value="editForm.uid || editForm.humanId" disabled></el-input>
      </el-form-item>
      <el-form-item label="GM 能力" required>
        <el-checkbox-group v-model="editForm.abilities" class="flex flex-wrap gap-2">
          <el-checkbox
              v-for="ability in abilities"
              :key="ability.command"
              :value="ability.command"
              border>
            {{ ability.label }}（{{ ability.command }}）
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="editForm.remark" placeholder="请输入备注（非必须）"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="updateCheatUser" :loading="updating">保存修改</el-button>
    </template>
  </el-dialog>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue';
import {onMounted, reactive, toRefs} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {
  apiFetch,
  apiMsg,
  buildPageQuery,
  confirmDialog,
  defaultPagination,
  formatTime,
  handleApiResult,
  isApiSuccess,
  isUserCancel,
  parsePagedData,
} from '@/utils';

export default {
  setup() {
    const state = reactive({
      abilities: [],
      adding: false,
      updating: false,
      loadingData: false,
      editDialogVisible: false,
      addForm: {
        targetType: 'uid',
        target: '',
        abilities: [],
        remark: '',
      },
      editForm: {
        uid: null,
        humanId: null,
        abilities: [],
        remark: '',
      },
      tableData: [],
      pagination: defaultPagination(),
    });

    const abilityLabel = (command) => {
      const ability = state.abilities.find((item) => item.command === command);
      return ability ? ability.label : command;
    };

    const fetchAbilities = async () => {
      const result = await apiFetch('/api/cheat-users/abilities');
      if (handleApiResult(result, {errorMsg: 'GM 能力加载失败'}) === 'ok') {
        state.abilities = result.data.data || [];
      }
    };

    const fetchCheatUsers = async () => {
      state.loadingData = true;
      try {
        const qs = buildPageQuery(state.pagination.page, state.pagination.size);
        const result = await apiFetch(`/api/cheat-users?${qs}`);
        if (result.unauthorized) return;
        if (handleApiResult(result, {errorMsg: '金手指用户加载失败'}) === 'ok') {
          const page = parsePagedData(result.data?.data);
          state.tableData = page.list;
          state.pagination.total = page.total;
        }
      } finally {
        state.loadingData = false;
      }
    };

    const addCheatUser = async () => {
      const target = state.addForm.target?.trim();
      if (!target) {
        ElementPlus.ElMessage.warning(
            state.addForm.targetType === 'uid' ? '请输入玩家 UID' : '请输入游戏角色 ID'
        );
        return;
      }
      if (state.addForm.abilities.length === 0) {
        ElementPlus.ElMessage.warning('请至少选择一项 GM 能力');
        return;
      }

      state.adding = true;
      try {
        const targetField = state.addForm.targetType === 'uid' ? 'uid' : 'humanId';
        const result = await apiFetch('/api/cheat-users', {
          method: 'POST',
          body: {
            [targetField]: target,
            abilities: state.addForm.abilities,
            remark: state.addForm.remark.trim() || null,
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('添加金手指用户成功');
          state.addForm.target = '';
          state.addForm.abilities = [];
          state.addForm.remark = '';
          await fetchCheatUsers();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '添加失败'));
        }
      } finally {
        state.adding = false;
      }
    };

    const openEditDialog = (row) => {
      state.editForm = {
        uid: row.uid || null,
        humanId: row.humanId || null,
        abilities: [...(row.abilities || [])],
        remark: row.remark || '',
      };
      state.editDialogVisible = true;
    };

    const updateCheatUser = async () => {
      if (state.editForm.abilities.length === 0) {
        ElementPlus.ElMessage.warning('请至少选择一项 GM 能力');
        return;
      }

      state.updating = true;
      try {
        const result = await apiFetch('/api/cheat-users/update', {
          method: 'POST',
          body: {
            uid: state.editForm.uid,
            humanId: state.editForm.humanId,
            abilities: state.editForm.abilities,
            remark: state.editForm.remark.trim() || null,
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('金手指权限更新成功');
          state.editDialogVisible = false;
          await fetchCheatUsers();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '更新失败'));
        }
      } finally {
        state.updating = false;
      }
    };

    const removeCheatUser = async (row) => {
      const target = row.uid || row.humanId;
      try {
        await confirmDialog(`确定要移除金手指用户 ${target} 吗？`);
        const result = await apiFetch('/api/cheat-users/remove', {
          method: 'POST',
          body: {uid: row.uid || null, humanId: row.humanId || null},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('移除成功');
          await fetchCheatUsers();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '移除失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('网络错误，请重试');
        }
      }
    };

    onMounted(() => {
      fetchAbilities();
      fetchCheatUsers();
    });

    return {
      ...toRefs(state),
      Refresh,
      abilityLabel,
      fetchCheatUsers,
      addCheatUser,
      openEditDialog,
      updateCheatUser,
      removeCheatUser,
      formatTime,
    };
  },
};
</script>
