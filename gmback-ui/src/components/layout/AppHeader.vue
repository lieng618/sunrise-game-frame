<template>
  <el-header class="main-header" height="56px">
    <h1 class="page-title">{{ title }}</h1>
    <el-dropdown trigger="click" @command="handleCommand">
      <button type="button" class="user-dropdown" aria-haspopup="menu" :aria-label="`用户菜单：${username || '管理员'}`">
        <el-icon class="user-icon" aria-hidden="true"><User/></el-icon>
        <span class="user-name">{{ username || '管理员' }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="logout">
            <el-icon class="mr-2" aria-hidden="true">
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
  methods: {
    handleCommand(command) {
      if (command === 'logout') this.$emit('logout');
    },
  },
};
</script>
