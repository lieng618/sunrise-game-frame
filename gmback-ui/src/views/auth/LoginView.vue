<template>
  <div class="login-container min-h-screen flex items-center justify-center">
    <el-card class="login-card w-96 rounded-xl relative z-10">
      <template #header>
        <div class="text-center">Sunrise Game Frame</div>
      </template>
      <el-form label-position="top" class="px-2">
        <el-form-item label="账号" class="mb-6">
          <el-input v-model="form.user" prefix-icon="User" placeholder="请输入账号" size="large"/>
        </el-form-item>
        <el-form-item label="密码" class="mb-6">
          <el-input
              v-model="form.pass"
              type="password"
              prefix-icon="Lock"
              placeholder="请输入密码"
              size="large"
              @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
            type="primary"
            class="login-button w-full mt-4"
            size="large"
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
import {useAuth} from '@/composables/useAuth';

export default {
  name: 'LoginView',
  setup() {
    const {form, loading, handleLogin} = useAuth();
    return {form, loading, handleLogin};
  },
};
</script>
