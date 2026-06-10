<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <div class="flex justify-between items-center mb-4">
      <el-tooltip :content="allReloadDisabledReason" :disabled="!allReloadDisabledReason" placement="top">
        <span class="inline-flex">
          <el-button
              type="primary"
              :loading="allReloading"
              :disabled="!!allReloadDisabledReason"
              @click="reloadAllConfig">
            <el-icon v-if="!allReloading" aria-hidden="true">
              <Refresh/>
            </el-icon>
            全部热更新
          </el-button>
        </span>
      </el-tooltip>
    </div>
    <el-table :data="filteredData" stripe class="table-full" v-loading="loading">
      <template #empty>
        <TableEmpty
            :error="loadError"
            empty-title="暂无可热更节点"
            empty-hint="需要至少一个在线的 GameServer 节点"
            @retry="fetchNodes"
        />
      </template>
      <el-table-column prop="type" label="节点类型" width="240">
        <template #default="scope">
          <el-tag type="success">
            {{ scope.row.type }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="nodeId" label="Node ID" width="240" sortable></el-table-column>
      <el-table-column prop="serverId" label="Server ID" width="180" sortable align="center"></el-table-column>

      <el-table-column label="管理操作" width="240" align="center" fixed="right">
        <template #default="scope">
          <el-tooltip
              :content="nodeDisabledReason(scope.row)"
              :disabled="!nodeDisabledReason(scope.row)"
              placement="top"
          >
            <span class="inline-flex">
              <el-button
                  type="primary"
                  size="small"
                  :disabled="!!nodeDisabledReason(scope.row)"
                  :loading="scope.row.reloading"
                  @click="reloadConfig(scope.row)">
                <el-icon v-if="!scope.row.reloading" aria-hidden="true">
                  <Refresh/>
                </el-icon>
                热更新
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
import {reactive, toRefs, onMounted, computed} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import TableEmpty from '@/components/feedback/TableEmpty.vue';

import {
  apiFetch, handleApiResult, safeArray, confirmDialog, isUserCancel, MSG, apiMsg,
} from '@/utils';

export default {
  components: {TableEmpty},
  setup() {
    const state = reactive({
      loading: false,
      loadError: '',
      allReloading: false,
      allNodes: [],
    });

    const filteredData = computed(() =>
        state.allNodes.filter((node) => node.type === 'GameServer')
    );

    const allReloadDisabledReason = computed(() => {
      if (filteredData.value.length === 0) return '当前没有可热更的 GameServer 节点';
      return '';
    });

    const nodeDisabledReason = (row) => {
      if (row.stopped) return '该节点已禁用';
      if (row.status !== 1) return '节点离线，无法热更新';
      return '';
    };

    const fetchNodes = async () => {
      state.loading = true;
      state.loadError = '';
      const result = await apiFetch('/api/nodes');
      if (result.unauthorized) {
        state.loading = false;
        return;
      }
      const status = handleApiResult(result, {errorMsg: '节点列表加载失败'});
      if (status === 'ok') {
        state.allNodes = safeArray(result.data?.data).map((node) => ({...node, reloading: false}));
      } else if (status === 'network') {
        state.loadError = MSG.NETWORK;
      } else if (status === 'failed') {
        state.loadError = apiMsg(result, '节点列表加载失败');
      }
      state.loading = false;
    };

    const reloadAllConfig = async () => {
      if (allReloadDisabledReason.value) {
        ElementPlus.ElMessage.warning(allReloadDisabledReason.value);
        return;
      }
      try {
        await confirmDialog(
            `确定要对所有 GameServer 节点进行配置热更新吗？\n共 ${filteredData.value.length} 个节点。`,
            '操作确认'
        );
        state.allReloading = true;
        const result = await apiFetch('/api/config/reload', {method: 'POST', body: {}});
        if (result.unauthorized) return;
        if (handleApiResult(result, {errorMsg: '全部热更新失败'}) === 'ok') {
          ElementPlus.ElMessage.success('全部热更新指令已发送');
        }
      } catch (e) {
        if (!isUserCancel(e)) ElementPlus.ElMessage.error('操作失败');
      } finally {
        state.allReloading = false;
      }
    };

    const reloadConfig = async (node) => {
      const reason = nodeDisabledReason(node);
      if (reason) {
        ElementPlus.ElMessage.warning(reason);
        return;
      }
      try {
        await confirmDialog(
            `确定要对节点 [${node.nodeId}] 进行配置热更新吗？\n热更新将重新加载配置表文件。`,
            '操作确认'
        );
        node.reloading = true;
        const result = await apiFetch('/api/config/reload', {
          method: 'POST',
          body: {nodeId: node.nodeId},
        });
        if (result.unauthorized) return;
        if (handleApiResult(result, {errorMsg: '配置热更新失败'}) === 'ok') {
          ElementPlus.ElMessage.success('配置热更新指令已发送');
        }
      } catch (e) {
        if (!isUserCancel(e)) ElementPlus.ElMessage.error('操作失败');
      } finally {
        node.reloading = false;
      }
    };

    onMounted(fetchNodes);

    return {
      ...toRefs(state),
      filteredData,
      allReloadDisabledReason,
      nodeDisabledReason,
      fetchNodes,
      reloadConfig,
      reloadAllConfig,
    };
  },
};
</script>
