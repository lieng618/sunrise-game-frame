<template>
  <el-header class="main-header" height="60px">
    <div class="page-title">{{ title }}</div>
    <el-dropdown>
      <span class="user-dropdown el-dropdown-link">
        <el-icon class="user-icon"><User/></el-icon>
        <span class="user-name">{{ username || '管理员' }}</span>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item divided @click="$emit('logout')">
            <el-icon class="mr-2">
              <Switch/>
            </el-icon>
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-header>
</template>

<script>
import {Switch, User} from '@element-plus/icons-vue';

export default {
  name: 'AppHeader',
  components: {Switch, User},
  props: {
    title: {type: String, default: ''},
    username: {type: String, default: ''},
  },
  emits: ['logout'],
};
</script>
