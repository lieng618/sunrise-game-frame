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
          暂无页面访问权限，请联系管理员分配权限后重新登录
        </div>
        <router-view v-else v-slot="{ Component }">
          <div class="page-content">
            <component :is="Component"/>
          </div>
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

