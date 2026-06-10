<template>
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-4">
          <el-select v-model="queryForm.operationType" placeholder="筛选操作类型" clearable
                     class="gm-field-filter" @change="handleFilterChange"
                     @clear="handleClearFilter">
            <el-option label="全部" value=""></el-option>
            <el-option label="登录" value="LOGIN"></el-option>
            <el-option label="发送邮件" value="SEND_MAIL"></el-option>
            <el-option label="踢出玩家" value="KICK_PLAYER"></el-option>
            <el-option label="热更配置" value="RELOAD_CONFIG"></el-option>
            <el-option label="代码热更" value="HOTSWAP_JAR"></el-option>
            <el-option label="切换节点状态" value="TOGGLE_NODE"></el-option>
            <el-option label="用户管理" value="USER_MANAGER"></el-option>
            <el-option label="封禁玩家" value="BAN_PLAYER"></el-option>
            <el-option label="解封玩家" value="UNBAN_PLAYER"></el-option>
            <el-option label="服务器关闭" value="SERVER_STATUS"></el-option>
            <el-option label="全服公告" value="ANNOUNCEMENT"></el-option>
            <el-option label="白名单" value="WHITELIST"></el-option>
            <el-option label="兑换码" value="CDK"></el-option>
            <el-option label="其他操作" value="OTHER"></el-option>
          </el-select>
        </div>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <template #empty>
        <TableEmpty
            :error="loadError"
            empty-title="暂无操作记录"
            empty-hint="调整筛选条件或稍后再查看"
            @retry="fetchLogs"
        />
      </template>

      <el-table-column prop="createTime" label="操作时间" width="200" sortable>
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.createTime) }}
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="operator" label="操作人员" width="180">
        <template #default="scope">
          <div class="flex items-center gm-text-emphasis">
            {{ scope.row.operator }}
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="ip" label="IP 地址" width="180">
        <template #default="scope">
                    <span class="ip-badge">
                        {{ scope.row.ip }}
                    </span>
        </template>
      </el-table-column>

      <el-table-column prop="operationType" label="操作类型" width="150">
        <template #default="scope">
                    <span class="operation-type" :class="scope.row.operationType">
                        {{ getOperationTypeDesc(scope.row.operationType) }}
                    </span>
        </template>
      </el-table-column>

      <el-table-column prop="action" label="操作行为" min-width="300">
        <template #default="scope">
                    <span class="gm-text-emphasis">
                        {{ scope.row.action }}
                    </span>
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
          @size-change="fetchLogs"
          @current-change="fetchLogs"></el-pagination>
    </div>
  </el-card>
</template>

<script>
import {reactive, toRefs, onMounted} from 'vue';
import TableEmpty from '@/components/feedback/TableEmpty.vue';

import {
  apiFetch, handleApiResult, parsePagedData, formatTime, defaultPagination, buildPageQuery, MSG, apiMsg,
} from '@/utils';

export default {
  components: {TableEmpty},
  setup() {
    const state = reactive({
      loadingData: false,
      loadError: '',
      tableData: [],
      queryForm: {operationType: ''},
      pagination: defaultPagination(),
    });

    const fetchLogs = async () => {
      state.loadingData = true;
      state.loadError = '';
      const qs = buildPageQuery(state.pagination.page, state.pagination.size, {
        operationType: state.queryForm.operationType,
      });
      const result = await apiFetch(`/api/logs?${qs}`);
      if (result.unauthorized) {
        state.loadingData = false;
        return;
      }
      const status = handleApiResult(result, {errorMsg: '操作日志加载失败'});
      if (status === 'ok') {
        const page = parsePagedData(result.data?.data);
        state.tableData = page.list;
        state.pagination.total = page.total;
      } else if (status === 'network') {
        state.loadError = MSG.NETWORK;
      } else if (status === 'failed') {
        state.loadError = apiMsg(result, '操作日志加载失败');
      }
      state.loadingData = false;
    };

    // 筛选变化时自动请求数据
    const handleFilterChange = () => {
      state.pagination.page = 1; // 筛选时重置到第一页
      fetchLogs();
    };

    // 清除筛选时请求全部数据
    const handleClearFilter = () => {
      state.queryForm.operationType = '';
      state.pagination.page = 1; // 重置到第一页
      fetchLogs();
    };

    // 操作类型描述映射
    const getOperationTypeDesc = (type) => {
      const typeMap = {
        'LOGIN': '登录',
        'SEND_MAIL': '发送邮件',
        'KICK_PLAYER': '踢出玩家',
        'RELOAD_CONFIG': '热更配置',
        'HOTSWAP_JAR': '代码热更',
        'TOGGLE_NODE': '切换节点状态',
        'USER_MANAGER': '用户管理',
        'MUTE_PLAYER': '禁言玩家',
        'UNMUTE_PLAYER': '解除禁言',
        'BAN_PLAYER': '封禁玩家',
        'UNBAN_PLAYER': '解除封禁',
        'SERVER_STATUS': '服务器关闭',
        'ANNOUNCEMENT': '全服公告',
        'WHITELIST': '白名单',
        'CDK': '兑换码',
        'OTHER': '其他操作'
      };
      return typeMap[type] || type || '未知';
    };

    onMounted(() => {
      fetchLogs();
    });

    return {
      ...toRefs(state),
      fetchLogs,
      handleFilterChange,
      handleClearFilter,
      formatTime,
      getOperationTypeDesc
    };
  }
};
</script>