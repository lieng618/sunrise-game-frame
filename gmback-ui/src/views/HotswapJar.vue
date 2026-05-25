<template>
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <el-form label-position="top">
      <el-form-item label="JAR 包路径">
        <el-input
            v-model="jarPath"
            placeholder="例如: E:/sunrise-game-frame/start/jar/sunrise-game.jar 或者 /home/sunrise-game-frame/start/jar/sunrise-game.jar"
            clearable>
        </el-input>
        <div class="text-xs text-gray-500 mt-1" style="margin-top:4px;color:#909399;font-size:12px;">
          填写 Game 服进程可访问的 JAR 绝对路径
        </div>
      </el-form-item>
    </el-form>
    <div class="flex justify-end">
      <el-button
          type="primary"
          :loading="allReloading"
          :disabled="!jarPath || filteredData.length === 0"
          @click="hotswapAll">
        <el-icon v-if="!allReloading">
          <Upload/>
        </el-icon>
        全部热更
      </el-button>
    </div>
  </el-card>

  <el-card shadow="never" class="page-card rounded-lg">
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
              :disabled="!jarPath || scope.row.status !== 1 || scope.row.stopped"
              :loading="scope.row.reloading"
              @click="hotswapNode(scope.row)">
            <el-icon v-if="!scope.row.reloading">
              <Upload/>
            </el-icon>
            热更
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
      jarPath: '',
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

    const filteredData = computed(() =>
        state.allNodes.filter((node) => node.type === 'GameServer')
    );

    const doHotswap = async (nodeId) => {
      const result = await apiFetch('/api/hotswap/jar', {
        method: 'POST',
        body: {
          jarPath: state.jarPath.trim(),
          nodeId: nodeId || undefined,
        },
      });
      if (result.unauthorized) return false;
      if (isApiSuccess(result)) {
        ElementPlus.ElMessage.success('代码热更指令已发送');
        return true;
      }
      ElementPlus.ElMessage.error(apiMsg(result));
      return false;
    };

    const hotswapAll = async () => {
      if (!state.jarPath.trim()) {
        ElementPlus.ElMessage.warning('请输入 JAR 包路径');
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
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('操作失败');
        }
      } finally {
        state.allReloading = false;
      }
    };

    const hotswapNode = async (node) => {
      if (!state.jarPath.trim()) {
        ElementPlus.ElMessage.warning('请输入 JAR 包路径');
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
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('操作失败');
        }
      } finally {
        node.reloading = false;
      }
    };

    onMounted(fetchNodes);

    return {...toRefs(state), filteredData, fetchNodes, hotswapAll, hotswapNode};
  },
};
</script>