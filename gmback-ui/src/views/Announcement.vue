<template>
  <!-- 发布公告区域 -->
  <el-card shadow="never" class="page-card rounded-lg mb-4">
    <template #header>
      <div class="flex items-center gap-2">
        <span class="gm-text-emphasis">发布公告</span>
      </div>
    </template>
    <el-form :model="announcementForm" label-width="100px" label-position="left">
      <el-form-item label="公告标题" required>
        <el-input v-model="announcementForm.title" placeholder="请输入公告标题" class="gm-field-md"></el-input>
      </el-form-item>
      <el-form-item label="公告内容" required>
        <el-input v-model="announcementForm.content" type="textarea" :rows="4" placeholder="请输入公告内容"
                  class="gm-field-md"></el-input>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
            v-model="announcementForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            class="gm-field-date">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
            v-model="announcementForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            :disabled-date="disabledEndDate"
            class="gm-field-date">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="addAnnouncement" :loading="submitting">
          <el-icon class="mr-1">
            <Promotion/>
          </el-icon>
          发布公告
        </el-button>
        <el-button @click="resetForm">清空</el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <!-- 公告列表 -->
  <el-card shadow="never" class="page-card rounded-lg">
    <template #header>
      <div class="page-toolbar">
        <span class="gm-text-emphasis">公告列表</span>
        <el-button type="primary" :icon="Refresh" @click="fetchAnnouncementList" :loading="loadingData">
          刷新
        </el-button>
      </div>
    </template>
    <el-table :data="tableData" stripe class="table-full" v-loading="loadingData" border>
      <el-table-column prop="id" label="ID" width="80">
      </el-table-column>
      <el-table-column prop="title" label="标题" width="200">
        <template #default="scope">
          <span class="gm-text-emphasis">{{ scope.row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="250">
        <template #default="scope">
          <span class="gm-text-secondary">{{ scope.row.content }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="180">
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.startTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="结束时间" width="180">
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.endTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180">
        <template #default="scope">
          <div class="flex items-center gm-text-secondary">
            <el-icon class="mr-1">
              <Clock/>
            </el-icon>
            {{ formatTime(scope.row.createTime) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row)" size="small">{{ getStatusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center">
        <template #default="scope">
          <el-button
              type="primary"
              size="small"
              @click="openEditDialog(scope.row)"
              :icon="Edit">
            修改
          </el-button>
          <el-button
              type="danger"
              size="small"
              plain
              @click="removeAnnouncement(scope.row)"
              :icon="Delete">
            删除
          </el-button>
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
          @size-change="fetchAnnouncementList"
          @current-change="fetchAnnouncementList">
      </el-pagination>
    </div>
  </el-card>

  <!-- 编辑对话框 -->
  <el-dialog v-model="editDialogVisible" title="编辑公告" width="550px">
    <el-form :model="editForm" label-width="100px" label-position="left">
      <el-form-item label="公告标题" required>
        <el-input v-model="editForm.title" placeholder="请输入公告标题"></el-input>
      </el-form-item>
      <el-form-item label="公告内容" required>
        <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="请输入公告内容"></el-input>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
            v-model="editForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            class="gm-field-full">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
            v-model="editForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="x"
            :disabled-date="disabledEndDate"
            class="gm-field-full">
        </el-date-picker>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="updateAnnouncement" :loading="updating">保存修改</el-button>
    </template>
  </el-dialog>
</template>

<script>
import {Delete, Edit, Refresh} from '@element-plus/icons-vue';
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {
  apiFetch, handleApiResult, parsePagedData, isApiSuccess, apiMsg, formatTime, defaultPagination,
  buildPageQuery, confirmDialog, isUserCancel,
} from '@/utils';

export default {
  setup() {
    const state = reactive({
      submitting: false,
      updating: false,
      loadingData: false,
      editDialogVisible: false,
      announcementForm: {title: '', content: '', startTime: null, endTime: null},
      editForm: {id: null, title: '', content: '', startTime: null, endTime: null},
      tableData: [],
      pagination: defaultPagination(),
    });

    const disabledEndDate = (time) => {
      return time.getTime() < Date.now() - 86400000;
    };

    const fetchAnnouncementList = async () => {
      state.loadingData = true;
      const qs = buildPageQuery(state.pagination.page, state.pagination.size);
      const result = await apiFetch(`/api/announcements?${qs}`);
      if (result.unauthorized) {
        state.loadingData = false;
        return;
      }
      if (handleApiResult(result, {errorMsg: '公告列表加载失败'}) === 'ok') {
        const page = parsePagedData(result.data?.data);
        state.tableData = page.list;
        state.pagination.total = page.total;
      }
      state.loadingData = false;
    };

    const addAnnouncement = async () => {
      if (!state.announcementForm.title || !state.announcementForm.title.trim()) {
        ElementPlus.ElMessage.warning('请输入公告标题');
        return;
      }
      if (!state.announcementForm.content || !state.announcementForm.content.trim()) {
        ElementPlus.ElMessage.warning('请输入公告内容');
        return;
      }
      if (!state.announcementForm.startTime) {
        ElementPlus.ElMessage.warning('请选择开始时间');
        return;
      }
      if (!state.announcementForm.endTime) {
        ElementPlus.ElMessage.warning('请选择结束时间');
        return;
      }
      if (parseInt(state.announcementForm.startTime) >= parseInt(state.announcementForm.endTime)) {
        ElementPlus.ElMessage.warning('结束时间必须晚于开始时间');
        return;
      }

      state.submitting = true;
      try {
        const result = await apiFetch('/api/announcements', {
          method: 'POST',
          body: {
            title: state.announcementForm.title.trim(),
            content: state.announcementForm.content.trim(),
            startTime: parseInt(state.announcementForm.startTime, 10),
            endTime: parseInt(state.announcementForm.endTime, 10),
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('发布公告成功');
          resetForm();
          fetchAnnouncementList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '发布公告失败'));
        }
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
      } finally {
        state.submitting = false;
      }
    };

    const resetForm = () => {
      state.announcementForm.title = '';
      state.announcementForm.content = '';
      state.announcementForm.startTime = null;
      state.announcementForm.endTime = null;
    };

    const openEditDialog = (row) => {
      state.editForm.id = row.id;
      state.editForm.title = row.title;
      state.editForm.content = row.content;
      state.editForm.startTime = row.startTime;
      state.editForm.endTime = row.endTime;
      state.editDialogVisible = true;
    };

    const updateAnnouncement = async () => {
      if (!state.editForm.title || !state.editForm.title.trim()) {
        ElementPlus.ElMessage.warning('请输入公告标题');
        return;
      }
      if (!state.editForm.content || !state.editForm.content.trim()) {
        ElementPlus.ElMessage.warning('请输入公告内容');
        return;
      }
      if (!state.editForm.startTime) {
        ElementPlus.ElMessage.warning('请选择开始时间');
        return;
      }
      if (!state.editForm.endTime) {
        ElementPlus.ElMessage.warning('请选择结束时间');
        return;
      }
      if (parseInt(state.editForm.startTime) >= parseInt(state.editForm.endTime)) {
        ElementPlus.ElMessage.warning('结束时间必须晚于开始时间');
        return;
      }

      state.updating = true;
      try {
        const result = await apiFetch('/api/announcements/update', {
          method: 'POST',
          body: {
            id: state.editForm.id,
            title: state.editForm.title.trim(),
            content: state.editForm.content.trim(),
            startTime: parseInt(state.editForm.startTime, 10),
            endTime: parseInt(state.editForm.endTime, 10),
          },
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('修改公告成功');
          state.editDialogVisible = false;
          fetchAnnouncementList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '修改公告失败'));
        }
      } catch (e) {
        console.error(e);
        ElementPlus.ElMessage.error('网络错误，请重试');
      } finally {
        state.updating = false;
      }
    };

    const removeAnnouncement = async (row) => {
      try {
        await confirmDialog(`确定要删除公告「${row.title}」吗？`);
        const result = await apiFetch('/api/announcements/remove', {
          method: 'POST',
          body: {id: row.id},
        });
        if (result.unauthorized) return;
        if (isApiSuccess(result)) {
          ElementPlus.ElMessage.success('删除公告成功');
          fetchAnnouncementList();
        } else {
          ElementPlus.ElMessage.error(apiMsg(result, '删除公告失败'));
        }
      } catch (e) {
        if (!isUserCancel(e)) {
          console.error(e);
          ElementPlus.ElMessage.error('网络错误，请重试');
        }
      }
    };

    const getStatusType = (row) => {
      const now = Date.now();
      if (now < row.startTime) return 'info';
      if (now >= row.startTime && now < row.endTime) return 'success';
      return 'danger';
    };

    const getStatusText = (row) => {
      const now = Date.now();
      if (now < row.startTime) return '待生效';
      if (now >= row.startTime && now < row.endTime) return '生效中';
      return '已过期';
    };

    onMounted(() => {
      fetchAnnouncementList();
    });

    return {
      ...toRefs(state),
      Refresh,
      Edit,
      Delete,
      disabledEndDate,
      fetchAnnouncementList,
      addAnnouncement,
      resetForm,
      openEditDialog,
      updateAnnouncement,
      removeAnnouncement,
      formatTime,
      getStatusType,
      getStatusText
    };
  }
};
</script>