<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-4">
          <el-select v-model="filterServerId" placeholder="全部服务器" clearable style="width: 200px;"
                     @change="handleFilterChange">
            <el-option v-for="sid in serverIds" :key="sid" :label="'服务器 ' + sid" :value="sid"/>
          </el-select>
          <span class="text-sm text-gray-500">
                        <el-icon class="mr-1"><Clock/></el-icon>
                        上次更新: <span class="font-medium text-gray-700">{{ lastUpdateTime }}</span>
                    </span>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchPlayers" :loading="loadingData" size="default">
          刷新
        </el-button>
      </div>
    </template>

    <el-table :data="tableData" stripe style="width: 100%" v-loading="loadingData" border>
      <el-table-column prop="serverId" label="服务器ID" width="240" sortable align="center">
        <template #default="scope">
          <el-tag type="success" effect="dark">
            {{ scope.row.serverId }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="humanId" label="玩家ID" min-width="300">
        <template #default="scope">
          <div class="flex items-center font-medium text-gray-700">
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

import {apiFetch, isApiSuccess, defaultPagination, buildPageQuery} from '@/utils';

export default {
  setup() {
    const state = reactive({
      loadingData: false,
      tableData: [],
      serverIds: [],
      filterServerId: null,
      lastUpdateTime: '-',
      timer: null,
      pagination: defaultPagination(),
    });

    const fetchPlayers = async () => {
      state.loadingData = true;
      try {
        const filters = {};
        if (state.filterServerId !== null && state.filterServerId !== '') {
          filters.serverId = state.filterServerId;
        }
        const qs = buildPageQuery(state.pagination.page, state.pagination.size, filters);
        const result = await apiFetch(`/api/online-players?${qs}`);
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          const data = result.data.data;
          state.tableData = data.list || [];
          state.pagination.total = data.total || 0;
          if (data.serverIds) state.serverIds = data.serverIds;
          state.lastUpdateTime = new Date().toLocaleTimeString();
        }
      } catch (e) {
        console.error(e);
      } finally {
        state.loadingData = false;
      }
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