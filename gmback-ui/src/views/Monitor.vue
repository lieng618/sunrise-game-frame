<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="page-toolbar">
        <div class="page-toolbar__actions">
          <span class="refresh-meta">
            <el-icon><Clock/></el-icon>
            上次更新：<strong>{{ lastUpdateTime }}</strong>
          </span>
          <el-button type="primary" :icon="Refresh" @click="fetchNodes" :loading="loadingData">
            刷新
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="filteredTableData" stripe class="table-full" v-loading="loadingData" table-layout="auto">
      <template #empty>
        <TableEmpty
            :error="loadError"
            empty-title="暂无节点"
            empty-hint="当前没有可显示的节点，可点击刷新重试"
            @retry="fetchNodes"
        />
      </template>
      <el-table-column prop="type" label="节点类型" min-width="140">
        <template #default="scope">
          <el-tag type="success">
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
      <el-table-column label="开放协议" min-width="120" align="center">
        <template #default="scope">
          <span v-if="scope.row.type === 'ExternalServer'" class="address-badge listen-types">
            {{ formatExternalListenTypes(scope.row) }}
          </span>
          <span v-else class="cell-empty">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="processId" label="进程 ID" min-width="100" sortable align="center">
        <template #default="scope">
          <span>{{ scope.row.processId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="online" label="在线人数" min-width="100" sortable align="center">
        <template #default="scope">
          <span class="gm-text-muted">{{ scope.row.online }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="120" align="center">
        <template #default="scope">
          <div v-if="scope.row.stopped" class="gm-status-disabled">
            <span class="status-dot bg-disabled"></span>已禁用
          </div>

          <div v-else-if="scope.row.status === 1" class="gm-status-online">
            <span class="status-dot bg-online"></span>在线
          </div>

          <div v-else class="gm-status-offline">
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
import TableEmpty from '@/components/feedback/TableEmpty.vue';
import {apiFetch, handleApiResult, safeArray, formatTime, MSG, apiMsg} from '@/utils';

export default {
  components: {TableEmpty},
  setup() {
    const state = reactive({
      loadingData: false,
      loadError: '',
      tableData: [],
      lastUpdateTime: '-',
      timer: null,
      selectedType: null // 选中的节点类型，null 表示全部
    });

    // 过滤后的表格数据
    const filteredTableData = computed(() => {
      return state.tableData;
    });

    const formatExternalListenTypes = (row) => {
      const types = [];
      if (row.tcpEnabled) types.push('tcp');
      if (row.wsEnabled) types.push('ws');
      if (row.kcpEnabled) types.push('kcp');
      return types.length ? types.join('|') : '-';
    };

    // 获取数据
    const fetchNodes = async () => {
      state.loadingData = true;
      state.loadError = '';
      const result = await apiFetch('/api/nodes');
      if (result.unauthorized) {
        state.loadingData = false;
        return;
      }
      const status = handleApiResult(result, {errorMsg: '节点列表加载失败'});
      if (status === 'ok') {
        state.tableData = safeArray(result.data?.data);
        state.lastUpdateTime = new Date().toLocaleTimeString();
      } else if (status === 'network') {
        state.loadError = MSG.NETWORK;
      } else if (status === 'failed') {
        state.loadError = apiMsg(result, '节点列表加载失败');
      }
      state.loadingData = false;
    };

    // 自动刷新逻辑
    const startTimer = () => {
      stopTimer();
      state.timer = setInterval(fetchNodes, 60000);
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
      formatExternalListenTypes,
    };
  }
};
</script>