<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <el-button type="primary" @click="showAddDialog = true" :icon="Plus">
          添加用户
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <el-table-column prop="username" label="用户名" width="200">
        <template #default="scope">
          <div class="flex items-center gm-text-emphasis">
            <el-icon class="mr-2">
              <User/>
            </el-icon>
            {{ scope.row.username }}
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="创建时间" width="200" sortable>
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.createTime) }}
          </div>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="320" align="center">
        <template #default="scope">
          <el-button
              type="success"
              size="small"
              @click="handleEditPermissions(scope.row)"
              :icon="Setting">
            权限管理
          </el-button>
          <el-button
              type="primary"
              size="small"
              @click="handleEditPassword(scope.row)"
              :icon="Edit">
            修改密码
          </el-button>
          <el-button
              type="danger"
              size="small"
              plain
              @click="handleDelete(scope.row)"
              :icon="Delete">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <!-- 添加用户对话框 -->
  <el-dialog v-model="showAddDialog" width="400px" @close="resetAddForm">
    <template #header>
      <div class="dialog-header">
        <el-icon class="dialog-header__icon">
          <UserFilled/>
        </el-icon>
        <span>添加用户</span>
      </div>
    </template>
    <el-form :model="addForm" label-width="80px">
      <el-form-item label="用户名" required>
        <el-input v-model="addForm.username" placeholder="请输入用户名" clearable>
          <template #prefix>
            <el-icon>
              <User/>
            </el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="密码" required>
        <el-input v-model="addForm.password" type="password" placeholder="请输入密码" clearable
                  show-password>
          <template #prefix>
            <el-icon>
              <Lock/>
            </el-icon>
          </template>
        </el-input>
      </el-form-item>
    </el-form>
    <template #footer>
            <span class="dialog-footer">
                <el-button @click="showAddDialog = false">取消</el-button>
                <el-button type="primary" @click="handleAdd" :loading="addLoading">添加用户</el-button>
            </span>
    </template>
  </el-dialog>

  <!-- 权限管理对话框 -->
  <el-dialog v-model="showPermDialog" width="560px" @close="resetPermForm">
    <template #header>
      <div class="dialog-header">
        <el-icon class="dialog-header__icon dialog-header__icon--success">
          <Setting/>
        </el-icon>
        <span>权限管理 — {{ permForm.username }}</span>
      </div>
    </template>
    <div class="dialog-hint">勾选该用户可访问的前端页面，保存后该用户需重新登录生效。</div>
    <el-checkbox-group v-model="permForm.permissions" class="perm-checkbox-grid">
      <el-checkbox
          v-for="page in permForm.pages"
          :key="page.key"
          :label="page.key"
          class="perm-checkbox-item">
        {{ page.label }}
      </el-checkbox>
    </el-checkbox-group>
    <template #footer>
            <span class="dialog-footer">
                <el-button @click="showPermDialog = false">取消</el-button>
                <el-button type="primary" @click="handleSavePermissions" :loading="permLoading">保存</el-button>
            </span>
    </template>
  </el-dialog>

  <!-- 修改密码对话框 -->
  <el-dialog v-model="showEditDialog" width="400px" @close="resetEditForm">
    <template #header>
      <div class="dialog-header">
        <el-icon class="dialog-header__icon">
          <Edit/>
        </el-icon>
        <span>修改密码</span>
      </div>
    </template>
    <el-form :model="editForm" label-width="80px">
      <el-form-item label="用户名">
        <el-input v-model="editForm.username" disabled>
          <template #prefix>
            <el-icon>
              <User/>
            </el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="新密码" required>
        <el-input v-model="editForm.password" type="password" placeholder="请输入新密码" clearable
                  show-password>
          <template #prefix>
            <el-icon>
              <Lock/>
            </el-icon>
          </template>
        </el-input>
      </el-form-item>
    </el-form>
    <template #footer>
            <span class="dialog-footer">
                <el-button @click="showEditDialog = false">取消</el-button>
                <el-button type="primary" @click="handleUpdatePassword" :loading="editLoading">保存密码</el-button>
            </span>
    </template>
  </el-dialog>
</template>

<script>
import {Delete, Edit, Plus, Setting} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {
  apiFetch, isApiSuccess, apiMsg, formatTime, confirmDialog, isUserCancel,
} from '@/utils';

const {ElMessage, ElMessageBox} = ElementPlus;

export default {
  setup() {
    const state = reactive({
      loadingData: false,
      tableData: [],
      showAddDialog: false,
      showEditDialog: false,
      showPermDialog: false,
      addLoading: false,
      editLoading: false,
      permLoading: false,
      addForm: {
        username: '',
        password: ''
      },
      editForm: {
        username: '',
        password: ''
      },
      permForm: {
        username: '',
        permissions: [],
        pages: []
      }
    });

    // 获取用户列表
    const fetchUsers = async () => {
      state.loadingData = true;
      try {
        const result = await apiFetch('/api/users');
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以访问用户管理');
          return;
        }
        if (isApiSuccess(result)) {
          state.tableData = result.data.data || [];
        } else {
          ElMessage.error(apiMsg(result, '获取用户列表失败'));
        }
      } catch (e) {
        console.error('获取用户列表失败', e);
        ElMessage.error('网络连接错误');
      } finally {
        state.loadingData = false;
      }
    };

    // 添加用户
    const handleAdd = async () => {
      if (!state.addForm.username || !state.addForm.password) {
        ElMessage.warning('请输入用户名和密码');
        return;
      }

      state.addLoading = true;
      try {
        const result = await apiFetch('/api/users', {
          method: 'POST',
          body: state.addForm,
        });
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以添加用户');
          return;
        }
        if (isApiSuccess(result)) {
          ElMessage.success('添加用户成功');
          state.showAddDialog = false;
          resetAddForm();
          fetchUsers();
        } else {
          ElMessage.error(apiMsg(result, '添加用户失败'));
        }
      } catch (e) {
        console.error('添加用户失败', e);
        ElMessage.error('网络连接错误');
      } finally {
        state.addLoading = false;
      }
    };

    // 删除用户
    const handleDelete = async (row) => {
      try {
        await confirmDialog(`确定要删除用户 "${row.username}" 吗？`, '确认删除');
        const result = await apiFetch(
            `/api/users/${encodeURIComponent(row.username)}`,
            {method: 'DELETE'}
        );
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以删除用户');
          return;
        }
        if (isApiSuccess(result)) {
          ElMessage.success('删除用户成功');
          fetchUsers();
        } else {
          ElMessage.error(apiMsg(result, '删除用户失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error('删除用户失败', e);
          ElMessage.error('网络连接错误');
        }
      }
    };

    // 打开权限管理对话框
    const handleEditPermissions = async (row) => {
      state.permLoading = true;
      try {
        const result = await apiFetch(
            `/api/users/${encodeURIComponent(row.username)}/permissions`
        );
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以管理用户权限');
          return;
        }
        if (isApiSuccess(result)) {
          const data = result.data.data;
          state.permForm.username = row.username;
          state.permForm.permissions = [...(data.permissions || [])];
          state.permForm.pages = data.pages || [];
          state.showPermDialog = true;
        } else {
          ElMessage.error(apiMsg(result, '获取用户权限失败'));
        }
      } catch (e) {
        console.error('获取用户权限失败', e);
        ElMessage.error('网络连接错误');
      } finally {
        state.permLoading = false;
      }
    };

    // 保存权限
    const handleSavePermissions = async () => {
      state.permLoading = true;
      try {
        const result = await apiFetch(
            `/api/users/${encodeURIComponent(state.permForm.username)}/permissions`,
            {
              method: 'PUT',
              body: {permissions: state.permForm.permissions},
            }
        );
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以修改用户权限');
          return;
        }
        if (isApiSuccess(result)) {
          ElMessage.success('权限保存成功，该用户需重新登录后生效');
          state.showPermDialog = false;
          resetPermForm();
          fetchUsers();
        } else {
          ElMessage.error(apiMsg(result, '保存权限失败'));
        }
      } catch (e) {
        console.error('保存权限失败', e);
        ElMessage.error('网络连接错误');
      } finally {
        state.permLoading = false;
      }
    };

    const resetPermForm = () => {
      state.permForm.username = '';
      state.permForm.permissions = [];
      state.permForm.pages = [];
    };

    // 打开修改密码对话框
    const handleEditPassword = (row) => {
      state.editForm.username = row.username;
      state.editForm.password = '';
      state.showEditDialog = true;
    };

    // 修改密码
    const handleUpdatePassword = async () => {
      if (!state.editForm.password) {
        ElMessage.warning('请输入新密码');
        return;
      }

      state.editLoading = true;
      try {
        const result = await apiFetch(
            `/api/users/${encodeURIComponent(state.editForm.username)}/password`,
            {
              method: 'PUT',
              body: {password: state.editForm.password},
            }
        );
        if (result.unauthorized) return;
        if (result.status === 403) {
          ElMessage.error('权限不足：只有管理员可以修改用户密码');
          return;
        }
        if (isApiSuccess(result)) {
          ElMessage.success('修改密码成功');
          state.showEditDialog = false;
          resetEditForm();
        } else {
          ElMessage.error(apiMsg(result, '修改密码失败'));
        }
      } catch (e) {
        console.error('修改密码失败', e);
        ElMessage.error('网络连接错误');
      } finally {
        state.editLoading = false;
      }
    };

    // 重置添加表单
    const resetAddForm = () => {
      state.addForm.username = '';
      state.addForm.password = '';
    };

    // 重置编辑表单
    const resetEditForm = () => {
      state.editForm.username = '';
      state.editForm.password = '';
    };

    // 格式化时间
    const formatTime = (ts) => {
      if (!ts) return '-';
      return new Date(ts).toLocaleString('zh-CN');
    };

    onMounted(() => {
      fetchUsers();
    });

    return {
      ...toRefs(state),
      Plus,
      Setting,
      Edit,
      Delete,
      fetchUsers,
      handleAdd,
      handleDelete,
      handleEditPermissions,
      handleSavePermissions,
      handleEditPassword,
      handleUpdatePassword,
      resetAddForm,
      resetEditForm,
      resetPermForm,
      formatTime
    };
  }
};
</script>
