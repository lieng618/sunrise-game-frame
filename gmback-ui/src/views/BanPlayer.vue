<template>
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="font-medium">封禁玩家</span>
      </div>
    </template>
    <el-form :model="banForm" label-width="100px" label-position="left" inline>
      <el-form-item label="玩家ID" required>
        <el-input v-model="banForm.humanId" placeholder="请输入玩家ID" style="width: 200px;"></el-input>
      </el-form-item>
      <el-form-item label="封禁原因" required>
        <el-input v-model="banForm.reason" placeholder="请输入封禁原因" style="width: 200px;"></el-input>
      </el-form-item>
      <el-form-item label="到期时间" required>
        <el-date-picker
            v-model="banForm.expireTime"
            type="datetime"
            placeholder="选择到期时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            :disabled-date="disabledDate"
            style="width: 220px;">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="danger" @click="banPlayer" :loading="banning">
          <el-icon class="mr-1">
            <Lock/>
          </el-icon>
          封禁
        </el-button>
        <el-button type="warning" @click="banForever">
          <el-icon class="mr-1">
            <Lock/>
          </el-icon>
          永久封禁
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-2">
          <span class="font-medium">封禁列表</span>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchBanList" :loading="loadingData" size="default">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe style="width: 100%" v-loading="loadingData" border>
      <el-table-column prop="humanId" label="玩家ID" width="180">
        <template #default="scope">
          <div class="flex items-center font-medium text-gray-700">
            <el-icon class="mr-1">
              <User/>
            </el-icon>
            {{ scope.row.humanId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="banTime" label="封禁时间" width="200">
        <template #default="scope">
          <div class="flex items-center text-gray-600">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.banTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="banExpireTime" label="解封时间" width="200">
        <template #default="scope">
          <div class="flex items-center text-gray-600">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ !scope.row.banExpireTime ? '永久封禁' : formatTime(scope.row.banExpireTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="封禁原因" min-width="150">
        <template #default="scope">
          <span class="text-gray-800">{{ scope.row.reason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button type="primary" size="small" @click="unbanPlayer(scope.row)">
            <el-icon class="mr-1">
              <Unlock/>
            </el-icon>
            解封
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
          @size-change="fetchBanList"
          @current-change="fetchBanList">
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
      banning: false,
      loadingData: false,
      banForm: {humanId: '', reason: '', expireTime: null},
      tableData: [],
      pagination: defaultPagination(),
    });

    const disabledDate = (time) => time.getTime() < Date.now();

    const fetchBanList = async () => {
      state.loadingData = true;
      try {
        const qs = buildPageQuery(state.pagination.page, state.pagination.size);
        const result = await apiFetch(`/api/ban/list?${qs}`);
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.tableData = result.data.data.list || [];
          state.pagination.total = result.data.data.total || 0;
        }
      } catch (e) {
        console.error('获取封禁列表失败', e);
      } finally {
        state.loadingData = false;
      }
    };

    const doBan = async (humanId, reason, banExpireTime) => {
      state.banning = true;
      try {
        const result = await apiFetch('/api/ban', {
          method: 'POST',
          body: {humanId, reason, banExpireTime},
        });
        if (result.unauthorized) return false;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('封禁成功');
          state.banForm.humanId = '';
          state.banForm.reason = '';
          state.banForm.expireTime = null;
          fetchBanList();
          return true;
        }
        ElementPlus.ElMessage.error(apiMsg(result, '封禁失败'));
        return false;
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
        return false;
      } finally {
        state.banning = false;
      }
    };

    const banPlayer = async () => {
      if (!state.banForm.humanId?.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家ID');
        return;
      }
      if (!state.banForm.reason?.trim()) {
        ElementPlus.ElMessage.warning('请输入封禁原因');
        return;
      }
      if (!state.banForm.expireTime) {
        ElementPlus.ElMessage.warning('请选择到期时间');
        return;
      }
      await doBan(
          state.banForm.humanId.trim(),
          state.banForm.reason.trim(),
          parseInt(state.banForm.expireTime, 10)
      );
    };

    const banForever = async () => {
      if (!state.banForm.humanId?.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家ID');
        return;
      }
      if (!state.banForm.reason?.trim()) {
        ElementPlus.ElMessage.warning('请输入封禁原因');
        return;
      }
      await doBan(state.banForm.humanId.trim(), state.banForm.reason.trim(), null);
    };

    const unbanPlayer = async (row) => {
      try {
        await confirmDialog(`确定要解封玩家 ${row.humanId} 吗？`);
        const result = await apiFetch('/api/unban', {
          method: 'POST',
          body: {humanId: row.humanId},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('解封成功');
          fetchBanList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '解封失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('网络错误，请重试');
        }
      }
    };

    onMounted(fetchBanList);

    return {
      ...toRefs(state),
      Refresh,
      disabledDate,
      fetchBanList,
      banPlayer,
      banForever,
      unbanPlayer,
      formatTime,
    };
  },
};
</script>