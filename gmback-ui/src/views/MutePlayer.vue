<template>
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="font-medium">禁言玩家</span>
      </div>
    </template>
    <el-form :model="muteForm" label-width="100px" label-position="left" inline>
      <el-form-item label="玩家ID" required>
        <el-input v-model="muteForm.humanId" placeholder="请输入玩家ID" class="gm-field-sm"></el-input>
      </el-form-item>
      <el-form-item label="禁言原因" required>
        <el-input v-model="muteForm.reason" placeholder="请输入禁言原因" class="gm-field-sm"></el-input>
      </el-form-item>
      <el-form-item label="到期时间" required>
        <el-date-picker
            v-model="muteForm.expireTime"
            type="datetime"
            placeholder="选择到期时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            :disabled-date="disabledDate"
            class="gm-field-date">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="danger" @click="mutePlayer" :loading="muting">
          <el-icon class="mr-1">
            <Mute/>
          </el-icon>
          禁言
        </el-button>
        <el-button type="warning" @click="muteForever">
          <el-icon class="mr-1">
            <Mute/>
          </el-icon>
          永久禁言
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-2">
          <span class="font-medium">禁言列表</span>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchMuteList" :loading="loadingData" size="default">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <el-table-column prop="humanId" label="玩家ID" width="180">
        <template #default="scope">
          <div class="flex items-center gm-text-emphasis">
            <el-icon class="mr-1">
              <User/>
            </el-icon>
            {{ scope.row.humanId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="muteTime" label="禁言时间" width="200">
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.muteTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="muteExpireTime" label="解禁时间" width="200">
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ !scope.row.muteExpireTime ? '永久禁言' : formatTime(scope.row.muteExpireTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="禁言原因" min-width="150">
        <template #default="scope">
          <span class="gm-text-emphasis">{{ scope.row.reason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button type="primary" size="small" @click="unmutePlayer(scope.row)">
            <el-icon class="mr-1">
              <Microphone/>
            </el-icon>
            解禁
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
          @size-change="fetchMuteList"
          @current-change="fetchMuteList">
      </el-pagination>
    </div>
  </el-card>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {
  apiFetch, handleApiResult, parsePagedData, isApiSuccess, apiMsg, formatTime, defaultPagination,
  buildPageQuery, confirmDialog, isUserCancel,
} from '@/utils';

export default {
  setup() {
    const state = reactive({
      muting: false,
      loadingData: false,
      muteForm: {humanId: '', reason: '', expireTime: null},
      tableData: [],
      pagination: defaultPagination(),
    });

    const disabledDate = (time) => time.getTime() < Date.now();

    const fetchMuteList = async () => {
      state.loadingData = true;
      const qs = buildPageQuery(state.pagination.page, state.pagination.size);
      const result = await apiFetch(`/api/mute/list?${qs}`);
      if (result.unauthorized) {
        state.loadingData = false;
        return;
      }
      if (handleApiResult(result, {errorMsg: '禁言列表加载失败'}) === 'ok') {
        const page = parsePagedData(result.data?.data);
        state.tableData = page.list;
        state.pagination.total = page.total;
      }
      state.loadingData = false;
    };

    const doMute = async (humanId, reason, muteExpireTime) => {
      state.muting = true;
      try {
        const result = await apiFetch('/api/mute', {
          method: 'POST',
          body: {humanId, reason, muteExpireTime},
        });
        if (result.unauthorized) return false;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('禁言成功');
          state.muteForm.humanId = '';
          state.muteForm.reason = '';
          state.muteForm.expireTime = null;
          fetchMuteList();
          return true;
        }
        ElementPlus.ElMessage.error(apiMsg(result, '禁言失败'));
        return false;
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
        return false;
      } finally {
        state.muting = false;
      }
    };

    const mutePlayer = async () => {
      if (!state.muteForm.humanId?.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家ID');
        return;
      }
      if (!state.muteForm.reason?.trim()) {
        ElementPlus.ElMessage.warning('请输入禁言原因');
        return;
      }
      if (!state.muteForm.expireTime) {
        ElementPlus.ElMessage.warning('请选择到期时间');
        return;
      }
      await doMute(
          state.muteForm.humanId.trim(),
          state.muteForm.reason.trim(),
          parseInt(state.muteForm.expireTime, 10)
      );
    };

    const muteForever = async () => {
      if (!state.muteForm.humanId?.trim()) {
        ElementPlus.ElMessage.warning('请输入玩家ID');
        return;
      }
      if (!state.muteForm.reason?.trim()) {
        ElementPlus.ElMessage.warning('请输入禁言原因');
        return;
      }
      await doMute(state.muteForm.humanId.trim(), state.muteForm.reason.trim(), null);
    };

    const unmutePlayer = async (row) => {
      try {
        await confirmDialog(`确定要解除玩家 ${row.humanId} 的禁言吗？`);
        const result = await apiFetch('/api/unmute', {
          method: 'POST',
          body: {humanId: row.humanId},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('解除禁言成功');
          fetchMuteList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '解除禁言失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('网络错误，请重试');
        }
      }
    };

    onMounted(fetchMuteList);

    return {
      ...toRefs(state),
      Refresh,
      disabledDate,
      fetchMuteList,
      mutePlayer,
      muteForever,
      unmutePlayer,
      formatTime,
    };
  },
};
</script>