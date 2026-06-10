<template>
  <el-container class="app-layout">
    <AppSidebar :menu-items="visibleMenuItems" :active-menu-path="activeMenuPath"/>

    <el-container class="app-layout__main" direction="vertical">
      <AppHeader
          :title="pageTitle"
          :username="form.user"
          @logout="logout"
      />

      <el-main class="main-content">
        <div v-if="!hasAnyPagePermission" class="empty-permission">
          <div class="empty-state">
            <p class="empty-state__title">暂无页面访问权限</p>
            <p class="empty-state__desc">请联系管理员分配权限后重新登录</p>
          </div>
        </div>
        <router-view v-else v-slot="{ Component }">
          <main class="page-content">
            <component :is="Component"/>
          </main>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import AppSidebar from '@/components/layout/AppSidebar.vue';
import AppHeader from '@/components/layout/AppHeader.vue';
import {useAuth} from '@/composables/useAuth';

export default {
  name: 'MainLayout',
  components: {AppSidebar, AppHeader},
  setup() {
    const auth = useAuth();
    return auth;
  },
};
</script>

