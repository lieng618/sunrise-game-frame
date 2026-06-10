<template>
  <div v-if="!sessionReady" class="login-container min-h-screen flex items-center justify-center">
    <div class="app-loading" role="status" aria-live="polite" aria-label="正在加载">
      <div class="app-loading__bar" aria-hidden="true"></div>
      <span>正在恢复登录状态</span>
    </div>
  </div>
  <LoginView v-else-if="!isLoggedIn"/>
  <MainLayout v-else/>
</template>

<script>
import {onMounted} from 'vue';
import LoginView from '@/views/auth/LoginView.vue';
import MainLayout from '@/layouts/MainLayout.vue';
import {useAuth} from '@/composables/useAuth';

export default {
  name: 'App',
  components: {LoginView, MainLayout},
  setup() {
    const auth = useAuth();
    onMounted(() => auth.bootstrap());
    return auth;
  },
};
</script>
