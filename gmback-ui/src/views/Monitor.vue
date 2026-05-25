<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-4">
                    <span class="text-sm text-gray-500">
                        <el-icon class="mr-1"><Clock/></el-icon>
                        上次更新: <span class="font-medium text-gray-700">{{ lastUpdateTime }}</span>
                    </span>
          <el-button type="primary" :icon="Refresh" @click="fetchNodes" :loading="loadingData" size="default">
            刷新
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="filteredTableData" stripe style="width: 100%" v-loading="loadingData" table-layout="auto">
      <el-table-column prop="type" label="节点类型" min-width="140">
        <template #default="scope">
          <el-tag :type="'success'" effect="dark">
            {{ scope.row.type }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="nodeId" label="Node ID" min-width="120" sortable/>
      <el-table-column prop="serverId" label="Server ID" min-width="110" sortable align="center"/>

      <el-table-column label="网络地址" min-width="160">
        <template #default="scope">
                    <span class="address-badge">
                        {{ scope.row.ip }}:{{ scope.row.port }}
                    </span>
        </template>
      </el-table-column>
      <el-table-column prop="processId" label="进程 ID" min-width="100" sortable align="center">
        <template #default="scope">
          <span>{{ scope.row.processId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="online" label="在线人数" min-width="100" sortable align="center">
        <template #default="scope">
          <span class="text-gray-400">{{ scope.row.online }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="120" align="center">
        <template #default="scope">
          <div v-if="scope.row.stopped"
               class="text-gray-400 font-bold flex items-center justify-center">
            <span class="status-dot bg-gray-400"></span>已禁用
          </div>

          <div v-else-if="scope.row.status === 1"
               class="text-green-600 font-bold flex items-center justify-center">
            <span class="status-dot bg-online"></span>在线
          </div>

          <div v-else
               class="text-red-500 flex items-center justify-center">
            <span class="status-dot bg-offline"></span>离线
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted, computed, onUnmounted} from 'vue';
import {apiFetch, isApiSuccess, formatTime} from '@/utils';

export default {
  setup() {
    const state = reactive({
      loadingData: false,
      tableData: [],
      lastUpdateTime: '-',
      timer: null,
      selectedType: null // 选中的节点类型，null 表示全部
    });

    // 过滤后的表格数据
    const filteredTableData = computed(() => {
      return state.tableData;
    });

    // 获取数据
    const fetchNodes = async () => {
      state.loadingData = true;
      try {
        const result = await apiFetch('/api/nodes');
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.tableData = result.data.data;
          state.lastUpdateTime = new Date().toLocaleTimeString();
        }
      } catch (e) {
        console.error(e);
      } finally {
        state.loadingData = false;
      }
    };

    // 自动刷新逻辑
    const startTimer = () => {
      stopTimer();
      state.timer = setInterval(fetchNodes, 60000); // 3秒刷新一次
    };

    const stopTimer = () => {
      if (state.timer) clearInterval(state.timer);
    };

    onMounted(() => {
      fetchNodes();
      startTimer();
    });

    onUnmounted(() => {
      stopTimer();
    });

    return {
      ...toRefs(state),
      Refresh,
      formatTime,
      fetchNodes,
      filteredTableData,
    };
  }
};
</script>