<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <div class="flex justify-between items-center mb-4">
      <el-button
          type="primary"
          :loading="allReloading"
          :disabled="filteredData.length === 0"
          @click="reloadAllConfig">
        <el-icon v-if="!allReloading">
          <Refresh/>
        </el-icon>
        全部热更新
      </el-button>
    </div>
    <el-table :data="filteredData" stripe style="width: 100%" v-loading="loading">
      <el-table-column prop="type" label="节点类型" width="240">
        <template #default="scope">
          <el-tag type="success" effect="dark">
            {{ scope.row.type }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="nodeId" label="Node ID" width="240" sortable></el-table-column>
      <el-table-column prop="serverId" label="Server ID" width="180" sortable align="center"></el-table-column>

      <el-table-column label="管理操作" width="240" align="center" fixed="right">
        <template #default="scope">
          <el-button
              type="primary"
              size="small"
              :disabled="scope.row.status !== 1 || scope.row.stopped"
              :loading="scope.row.reloading"
              @click="reloadConfig(scope.row)">
            <el-icon v-if="!scope.row.reloading">
              <Refresh/>
            </el-icon>
            热更新
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
import {reactive, toRefs, onMounted, computed} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';

import {apiFetch, isApiSuccess, apiMsg, confirmDialog, isUserCancel} from '@/utils';

export default {
  setup() {
    const state = reactive({
      loading: false,
      allReloading: false,
      allNodes: [],
    });

    const fetchNodes = async () => {
      state.loading = true;
      try {
        const result = await apiFetch('/api/nodes');
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          state.allNodes = result.data.data.map((node) => ({...node, reloading: false}));
        }
      } catch (e) {
        ElementPlus.ElMessage.error('加载失败');
      } finally {
        state.loading = false;
      }
    };

    const filteredData = computed(() => {
      return state.allNodes.filter(node => node.type === 'GameServer');
    });

    // 全部热更新配置
    const reloadAllConfig = async () => {
      try {
        await confirmDialog(
            `确定要对所有 GameServer 节点进行配置热更新吗？\n共 ${filteredData.value.length} 个节点。`,
            '操作确认'
        );
        state.allReloading = true;
        const result = await apiFetch('/api/config/reload', {method: 'POST', body: {}});
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('全部热更新指令已发送');
        } else {
          ElementPlus.ElMessage.error(apiMsg(result));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('操作失败');
        }
      } finally {
        state.allReloading = false;
      }
    };

    // 热更新配置
    const reloadConfig = async (node) => {
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
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('配置热更新指令已发送');
        } else {
          ElementPlus.ElMessage.error(apiMsg(result));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('操作失败');
        }
      } finally {
        node.reloading = false;
      }
    };

    onMounted(() => {
      fetchNodes();
    });

    return {...toRefs(state), filteredData, fetchNodes, reloadConfig, reloadAllConfig};
  }
};
</script>