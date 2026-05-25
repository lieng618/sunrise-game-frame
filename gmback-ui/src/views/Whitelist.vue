<template>
  <!-- 添加白名单区域 -->
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="font-medium">添加白名单</span>
      </div>
    </template>
    <el-form :model="addForm" label-width="100px" label-position="left" inline>
      <el-form-item label="玩家UID" required>
        <el-input v-model="addForm.uid" placeholder="请输入玩家UID" style="width: 200px;"></el-input>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="addForm.remark" placeholder="请输入备注（非必须）" style="width: 200px;"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="addToWhitelist" :loading="adding">
          <el-icon class="mr-1">
            <Plus/>
          </el-icon>
          添加
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <!-- 白名单列表 -->
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-2">
          <span class="font-medium">白名单列表</span>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchWhitelist" :loading="loadingData" size="default">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe style="width: 100%" v-loading="loadingData" border>
      <el-table-column prop="uid" label="玩家UID" width="200">
        <template #default="scope">
          <div class="flex items-center font-medium text-gray-700">
            <el-icon class="mr-1">
              <User/>
            </el-icon>
            {{ scope.row.uid }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200">
        <template #default="scope">
          <span class="text-gray-800">{{ scope.row.remark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="addTime" label="添加时间" width="200">
        <template #default="scope">
          <div class="flex items-center text-gray-600">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.addTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button type="danger" size="small" @click="removeFromWhitelist(scope.row)">
            <el-icon class="mr-1">
              <Delete/>
            </el-icon>
            移除
          </el-button>
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
          @size-change="fetchWhitelist"
          @current-change="fetchWhitelist">
      </el-pagination>
    </div>
  </el-card>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {
  apiFetch, isApiSuccess, apiMsg, formatTime, defaultPagination,
  buildPageQuery, confirmDialog, isUserCancel,
} from '@/utils';

export default {
  setup() {
    const state = reactive({
      adding: false,
      loadingData: false,
      addForm: {uid: '', remark: ''},
      tableData: [],
      pagination: defaultPagination(),
    });

    const fetchWhitelist = async () => {
      state.loadingData = true;
      try {
        const qs = buildPageQuery(state.pagination.page, state.pagination.size);
        const result = await apiFetch(`/api/whitelist?${qs}`);
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.tableData = result.data.data.list || [];
          state.pagination.total = result.data.data.total || 0;
        }
      } catch (e) {
        console.error('获取白名单失败', e);
      } finally {
        state.loadingData = false;
      }
    };

    const addToWhitelist = async () => {
      if (!state.addForm.uid?.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家UID');
        return;
      }
      state.adding = true;
      try {
        const result = await apiFetch('/api/whitelist', {
          method: 'POST',
          body: {
            uid: state.addForm.uid.trim(),
            remark: state.addForm.remark.trim() || null,
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('添加白名单成功');
          state.addForm.uid = '';
          state.addForm.remark = '';
          fetchWhitelist();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '添加失败'));
        }
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
      } finally {
        state.adding = false;
      }
    };

    const removeFromWhitelist = async (row) => {
      try {
        await confirmDialog(`确定要移除白名单中的 ${row.uid} 吗？`);
        const result = await apiFetch('/api/whitelist/remove', {
          method: 'POST',
          body: {uid: row.uid},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('移除成功');
          fetchWhitelist();
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

    onMounted(fetchWhitelist);

    return {
      ...toRefs(state),
      Refresh,
      fetchWhitelist,
      addToWhitelist,
      removeFromWhitelist,
      formatTime,
    };
  },
};
</script>