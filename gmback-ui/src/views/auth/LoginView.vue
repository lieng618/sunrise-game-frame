<template>
  <div class="login-container min-h-screen flex items-center justify-center">
    <el-card class="login-card w-96 relative z-10">
      <template #header>
        <div class="login-logo">
          <img
              :src="logoBig"
              alt="Sunrise Game Frame"
              class="login-logo__img"
          />
        </div>
      </template>
      <el-form label-position="top" @submit.prevent="handleLogin" aria-label="登录表单">
        <el-form-item label="账号">
          <el-input
              v-model="form.user"
              prefix-icon="User"
              placeholder="请输入账号"
              autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
              v-model="form.pass"
              type="password"
              prefix-icon="Lock"
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
            type="primary"
            class="login-button w-full"
            :loading="loading"
            @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import logoBig from '@/assets/images/logo_big.png';
import {useAuth} from '@/composables/useAuth';

export default {
  name: 'LoginView',
  setup() {
    const {form, loading, handleLogin} = useAuth();
    return {form, loading, handleLogin, logoBig};
  },
};
</script>
