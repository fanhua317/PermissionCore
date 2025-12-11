<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">PermaCore IAM</h2>
      <el-form :model="loginForm" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/user';
import router from '@/router';
import axios from 'axios';

const userStore = useUserStore();

const loginForm = reactive({
  username: 'admin',
  password: 'Admin@123456',
});

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.error('请输入用户名和密码');
    return;
  }

  try {
    const res = await axios.post('http://localhost:8080/api/auth/login', loginForm);
    const { accessToken, refreshToken } = res.data.data;
    userStore.setToken(accessToken, refreshToken);
    ElMessage.success('登录成功');
    router.push('/');
  } catch (error: any) {
    ElMessage.error(error.response?.data?.msg || '登录失败');
  }
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
}
.login-title {
  text-align: center;
  margin-bottom: 30px;
}
</style>