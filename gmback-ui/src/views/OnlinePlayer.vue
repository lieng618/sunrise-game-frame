<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="page-toolbar">
        <div class="page-toolbar__actions">
          <el-select
              v-model="filterServerId"
              placeholder="全部服务器"
              clearable
              class="gm-field-select"
              @change="handleFilterChange"
          >
            <el-option v-for="sid in serverIds" :key="sid" :label="'服务器 ' + sid" :value="sid"/>
          </el-select>
          <span class="refresh-meta">
            <el-icon><Clock/></el-icon>
            上次更新：<strong>{{ lastUpdateTime }}</strong>
          </span>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchPlayers" :loading="loadingData">
          刷新
        </el-button>
      </div>
    </template>

    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <template #empty>
        <TableEmpty
            :error="loadError"
            empty-title="暂无在线玩家"
            empty-hint="当前没有玩家在线，或筛选条件下无匹配结果"
            @retry="fetchPlayers"
        />
      </template>
      <el-table-column prop="serverId" label="服务器ID" width="240" sortable align="center">
        <template #default="scope">
          <el-tag type="success">
            {{ scope.row.serverId }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="humanId" label="玩家ID" min-width="300">
        <template #default="scope">
          <div class="flex items-center gm-text-emphasis">
            <el-icon class="mr-1">
              <User/>
            </el-icon>
            {{ scope.row.humanId }}
          </div>
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
          @size-change="fetchPlayers"
          @current-change="fetchPlayers">
      </el-pagination>
    </div>
  </el-card>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted, onUnmounted} from 'vue';
import TableEmpty from '@/components/feedback/TableEmpty.vue';

import {
  apiFetch, handleApiResult, parsePagedData, safeArray, defaultPagination, buildPageQuery, MSG, apiMsg,
} from '@/utils';

export default {
  components: {TableEmpty},
  setup() {
    const state = reactive({
      loadingData: false,
      loadError: '',
      tableData: [],
      serverIds: [],
      filterServerId: null,
      lastUpdateTime: '-',
      timer: null,
      pagination: defaultPagination(),
    });

    const fetchPlayers = async () => {
      state.loadingData = true;
      state.loadError = '';
      const filters = {};
      if (state.filterServerId !== null && state.filterServerId !== '') {
        filters.serverId = state.filterServerId;
      }
      const qs = buildPageQuery(state.pagination.page, state.pagination.size, filters);
      const result = await apiFetch(`/api/online-players?${qs}`);
      if (result.unauthorized) {
        state.loadingData = false;
        return;
      }
      const status = handleApiResult(result, {errorMsg: '在线玩家列表加载失败'});
      if (status === 'ok') {
        const page = parsePagedData(result.data?.data);
        state.tableData = page.list;
        state.pagination.total = page.total;
        if (page.extra.serverIds) state.serverIds = safeArray(page.extra.serverIds);
        state.lastUpdateTime = new Date().toLocaleTimeString();
      } else if (status === 'network') {
        state.loadError = MSG.NETWORK;
      } else if (status === 'failed') {
        state.loadError = apiMsg(result, '在线玩家列表加载失败');
      }
      state.loadingData = false;
    };

    const handleFilterChange = () => {
      state.pagination.page = 1;
      fetchPlayers();
    };

    const startTimer = () => {
      stopTimer();
      state.timer = setInterval(fetchPlayers, 60000);
    };

    const stopTimer = () => {
      if (state.timer) clearInterval(state.timer);
    };

    onMounted(() => {
      fetchPlayers();
      startTimer();
    });

    onUnmounted(() => {
      stopTimer();
    });

    return {
      ...toRefs(state),
      Refresh,
      fetchPlayers,
      handleFilterChange
    };
  }
};
</script>