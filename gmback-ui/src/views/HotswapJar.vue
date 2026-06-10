<template>
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <el-form label-position="top">
      <el-form-item label="JAR 包路径">
        <el-input
            v-model="jarPath"
            placeholder="例如: E:/sunrise-game-frame/start/jar/sunrise-game.jar 或者 /home/sunrise-game-frame/start/jar/sunrise-game.jar"
            clearable>
        </el-input>
        <div class="form-hint">
          填写 Game 服进程可访问的 JAR 绝对路径
        </div>
      </el-form-item>
    </el-form>
    <div class="flex justify-end">
      <el-tooltip :content="allReloadDisabledReason" :disabled="!allReloadDisabledReason" placement="top">
        <span class="inline-flex">
          <el-button
              type="primary"
              :loading="allReloading"
              :disabled="!!allReloadDisabledReason"
              @click="hotswapAll">
            <el-icon v-if="!allReloading" aria-hidden="true">
              <Upload/>
            </el-icon>
            全部热更
          </el-button>
        </span>
      </el-tooltip>
    </div>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
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
                  @click="hotswapNode(scope.row)">
                <el-icon v-if="!scope.row.reloading" aria-hidden="true">
                  <Upload/>
                </el-icon>
                热更
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
  apiFetch, handleApiResult, safeArray, apiMsg, confirmDialog, isUserCancel, MSG,
} from '@/utils';

export default {
  components: {TableEmpty},
  setup() {
    const state = reactive({
      loading: false,
      loadError: '',
      allReloading: false,
      allNodes: [],
      jarPath: '',
    });

    const filteredData = computed(() =>
        state.allNodes.filter((node) => node.type === 'GameServer')
    );

    const allReloadDisabledReason = computed(() => {
      if (!state.jarPath.trim()) return '请先填写 JAR 包路径';
      if (filteredData.value.length === 0) return '当前没有可热更的 GameServer 节点';
      return '';
    });

    const nodeDisabledReason = (row) => {
      if (!state.jarPath.trim()) return '请先填写 JAR 包路径';
      if (row.stopped) return '该节点已禁用';
      if (row.status !== 1) return '节点离线，无法热更';
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

    const doHotswap = async (nodeId) => {
      const result = await apiFetch('/api/hotswap/jar', {
        method: 'POST',
        body: {
          jarPath: state.jarPath.trim(),
          nodeId: nodeId || undefined,
        },
      });
      if (result.unauthorized) return false;
      if (handleApiResult(result, {errorMsg: '代码热更失败'}) === 'ok') {
        ElementPlus.ElMessage.success('代码热更指令已发送');
        return true;
      }
      return false;
    };

    const hotswapAll = async () => {
      if (allReloadDisabledReason.value) {
        ElementPlus.ElMessage.warning(allReloadDisabledReason.value);
        return;
      }
      try {
        await confirmDialog(
            `确定要对所有 GameServer 节点执行代码热更吗？\nJAR: ${state.jarPath}\n共 ${filteredData.value.length} 个节点。`,
            '操作确认'
        );
        state.allReloading = true;
        await doHotswap(null);
      } catch (e) {
        if (!isUserCancel(e)) ElementPlus.ElMessage.error('操作失败');
      } finally {
        state.allReloading = false;
      }
    };

    const hotswapNode = async (node) => {
      const reason = nodeDisabledReason(node);
      if (reason) {
        ElementPlus.ElMessage.warning(reason);
        return;
      }
      try {
        await confirmDialog(
            `确定要对节点 [${node.nodeId}] 执行代码热更吗？\nJAR: ${state.jarPath}`,
            '操作确认'
        );
        node.reloading = true;
        await doHotswap(node.nodeId);
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
      hotswapAll,
      hotswapNode,
    };
  },
};
</script>
