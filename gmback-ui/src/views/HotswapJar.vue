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
          推荐使用构建生成的 sunrise-hotfix.jar；所有节点必须能访问同一路径
        </div>
      </el-form-item>
      <el-form-item label="上传并热更">
        <el-upload
            :show-file-list="false"
            :http-request="uploadAndHotswap"
            :disabled="uploading"
            accept=".jar">
          <el-button :loading="uploading">选择 JAR 并执行</el-button>
        </el-upload>
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

  <el-card v-if="lastResult" shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center justify-between">
        <span>最近一次热更结果</span>
        <span>
          成功 {{ lastResult.succeeded }} / {{ lastResult.total }}，失败 {{ lastResult.failed }}
        </span>
      </div>
    </template>
    <el-table :data="lastResult.results" stripe class="table-full">
      <el-table-column prop="nodeId" label="Node ID" width="220"/>
      <el-table-column prop="nodeType" label="节点类型" width="150"/>
      <el-table-column prop="status" label="状态" width="150">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重定义" width="110" align="center">
        <template #default="scope">{{ resultCount(scope.row.redefinedClasses) }}</template>
      </el-table-column>
      <el-table-column label="未加载" width="110" align="center">
        <template #default="scope">{{ resultCount(scope.row.skippedNotLoadedClasses) }}</template>
      </el-table-column>
      <el-table-column label="结果" min-width="280">
        <template #default="scope">{{ scope.row.message }}</template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
    <el-table :data="filteredData" stripe class="table-full" v-loading="loading">
      <template #empty>
        <TableEmpty
            :error="loadError"
            empty-title="暂无可热更节点"
            empty-hint="需要至少一个在线的服务节点"
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
  getAuthToken, handleLoginExpired,
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
      lastResult: null,
      uploading: false,
    });

    const filteredData = computed(() => state.allNodes);

    const allReloadDisabledReason = computed(() => {
      if (!state.jarPath.trim()) return '请先填写 JAR 包路径';
      if (filteredData.value.length === 0) return '当前没有可热更的服务节点';
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
        state.lastResult = result.data?.data;
        const failed = Number(state.lastResult?.failed || 0);
        if (failed > 0) {
          ElementPlus.ElMessage.warning(`热更完成，${failed} 个节点失败`);
        } else {
          ElementPlus.ElMessage.success('热更完成，已收到所有节点结果');
        }
        await fetchNodes();
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
            `确定要对所有在线服务节点执行代码热更吗？\nJAR: ${state.jarPath}\n共 ${filteredData.value.length} 个节点。`,
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

    const uploadAndHotswap = async ({file}) => {
      if (!file) return;
      state.uploading = true;
      try {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch('/api/hotswap/upload', {
          method: 'POST',
          headers: {Authorization: getAuthToken() || ''},
          body: formData,
        });
        if (response.status === 401) {
          handleLoginExpired();
          return;
        }
        let data = null;
        try {
          data = await response.json();
        } catch {
          data = null;
        }
        const result = {ok: response.ok, status: response.status, data};
        if (handleApiResult(result, {errorMsg: '上传热更失败'}) === 'ok') {
          state.lastResult = data.data;
          state.jarPath = data.data?.targetJar || state.jarPath;
          const failed = Number(data.data?.failed || 0);
          ElementPlus.ElMessage[failed > 0 ? 'warning' : 'success'](
              failed > 0 ? `热更完成，${failed} 个节点失败` : '上传完成，已收到所有节点结果'
          );
          await fetchNodes();
        }
      } catch {
        ElementPlus.ElMessage.error(MSG.NETWORK);
      } finally {
        state.uploading = false;
      }
    };

    onMounted(fetchNodes);

    return {
      ...toRefs(state),
      filteredData,
      allReloadDisabledReason,
      nodeDisabledReason,
      uploadAndHotswap,
      statusTagType,
      resultCount,
      fetchNodes,
      hotswapAll,
      hotswapNode,
    };
  },
};

function statusTagType(status) {
  if (status === 'FAILED') return 'danger';
  if (status === 'PARTIAL' || status === 'NO_APPLICABLE') return 'warning';
  if (status === 'SUCCESS') return 'success';
  return 'info';
}

function resultCount(value) {
  return Array.isArray(value) ? value.length : 0;
}
</script>
