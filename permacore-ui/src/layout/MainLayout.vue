<template>
  <el-container class="main-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo-container">
        <img src="@/assets/logo.svg" alt="logo" class="logo" />
        <span v-show="!isCollapse" class="logo-text">PermaCore IAM</span>
      </div>
      <el-menu
        :default-active="currentRoute"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>控制台</template>
        </el-menu-item>
        
        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/user">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/role">
            <el-icon><UserFilled /></el-icon>
            <template #title>角色管理</template>
          </el-menu-item>
          <el-menu-item index="/permission">
            <el-icon><Key /></el-icon>
            <template #title>权限管理</template>
          </el-menu-item>
          <el-menu-item index="/sod">
            <el-icon><Lock /></el-icon>
            <template #title>职责分离</template>
          </el-menu-item>
          <el-menu-item index="/dept">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>部门管理</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="audit">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>日志审计</span>
          </template>
          <el-menu-item index="/login-log">
            <el-icon><Tickets /></el-icon>
            <template #title>登录日志</template>
          </el-menu-item>
          <el-menu-item index="/oper-log">
            <el-icon><List /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="breadcrumbTitle">{{ breadcrumbTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" class="avatar" :src="userAvatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || 'Admin' }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon>修改密码
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区域 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>

      <!-- 底部 -->
      <el-footer class="footer">
        <span>PermaCore IAM &copy; 2024 - 基于RBAC3的权限管理系统</span>
      </el-footer>
    </el-container>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px" destroy-on-close>
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 个人中心对话框 -->
    <el-dialog v-model="profileDialogVisible" title="个人中心" width="500px" destroy-on-close>
      <div class="profile-container">
        <!-- 头像上传 -->
        <div class="avatar-section">
          <el-upload
            class="avatar-uploader"
            action=""
            :show-file-list="false"
            :before-upload="beforeAvatarUpload"
            :http-request="uploadAvatar"
          >
            <el-avatar :size="100" :src="userAvatar" class="profile-avatar">
              <el-icon :size="40"><UserFilled /></el-icon>
            </el-avatar>
            <div class="avatar-upload-text">点击更换头像</div>
          </el-upload>
        </div>
        <!-- 个人信息表单 -->
        <el-form :model="profileForm" label-width="80px" style="margin-top: 20px">
          <el-form-item label="用户名">
            <el-input :value="userStore.userInfo?.username" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateProfile" :loading="profileLoading">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/store/user';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules, UploadRawFile } from 'element-plus';
import request from '@/utils/request';
import {
  Odometer,
  Setting,
  User,
  UserFilled,
  Key,
  OfficeBuilding,
  Document,
  Tickets,
  List,
  Fold,
  Expand,
  ArrowDown,
  Lock,
  SwitchButton,
} from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const isCollapse = ref(false);

// 用户头像
const userAvatar = ref('');

// 修改密码
const passwordDialogVisible = ref(false);
const passwordLoading = ref(false);
const passwordFormRef = ref<FormInstance>();
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.value.newPassword) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
};

// 个人中心
const profileDialogVisible = ref(false);
const profileLoading = ref(false);
const profileForm = ref({
  nickname: '',
  email: '',
  phone: '',
});

const currentRoute = computed(() => route.path);

const breadcrumbTitle = computed(() => {
  const titles: Record<string, string> = {
    '/dashboard': '控制台',
    '/user': '用户管理',
    '/role': '角色管理',
    '/permission': '权限管理',
    '/sod': '职责分离',
    '/dept': '部门管理',
    '/login-log': '登录日志',
    '/oper-log': '操作日志',
  };
  return titles[route.path] || '';
});

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
};

const handleCommand = async (command: string) => {
  switch (command) {
    case 'profile':
      openProfileDialog();
      break;
    case 'password':
      openPasswordDialog();
      break;
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' });
        userStore.clearToken();
        router.push('/login');
        ElMessage.success('退出成功');
      } catch {
        // 用户取消
      }
      break;
  }
};

const openPasswordDialog = () => {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' };
  passwordDialogVisible.value = true;
};

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return;
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return;
    passwordLoading.value = true;
    try {
      await request.post('/api/auth/change-password', {
        oldPassword: passwordForm.value.oldPassword,
        newPassword: passwordForm.value.newPassword,
      });
      ElMessage.success('密码修改成功，请重新登录');
      passwordDialogVisible.value = false;
      // 清除token并跳转登录页
      userStore.clearToken();
      router.push('/login');
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message || '修改密码失败');
    } finally {
      passwordLoading.value = false;
    }
  });
};

const openProfileDialog = async () => {
  // 从API加载当前用户完整信息
  profileDialogVisible.value = true;
  try {
    await userStore.fetchUserInfo();
    profileForm.value = {
      nickname: userStore.userInfo?.nickname || '',
      email: userStore.userInfo?.email || '',
      phone: userStore.userInfo?.phone || '',
    };
  } catch (error: any) {
    ElMessage.error('获取用户信息失败');
  }
};

const handleUpdateProfile = async () => {
  profileLoading.value = true;
  try {
    await request.put('/api/auth/profile', profileForm.value);
    ElMessage.success('个人信息更新成功');
    profileDialogVisible.value = false;
    // 刷新用户信息
    await userStore.fetchUserInfo();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '更新失败');
  } finally {
    profileLoading.value = false;
  }
};

const beforeAvatarUpload = (rawFile: UploadRawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  if (rawFile.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过2MB');
    return false;
  }
  return true;
};

const uploadAvatar = async (options: any) => {
  const formData = new FormData();
  formData.append('file', options.file);
  try {
    const res: any = await request.post('/api/auth/upload-avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    if (res?.avatarUrl) {
      userAvatar.value = res.avatarUrl;
      // 保存到localStorage
      localStorage.setItem('userAvatar', res.avatarUrl);
      ElMessage.success('头像上传成功');
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '头像上传失败');
  }
};

onMounted(() => {
  // 从localStorage加载头像
  const savedAvatar = localStorage.getItem('userAvatar');
  if (savedAvatar) {
    userAvatar.value = savedAvatar;
  }
});
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;
}

.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  background-color: #002140;
}

.logo {
  width: 32px;
  height: 32px;
}

.logo-text {
  margin-left: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  height: calc(100vh - 60px);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #666;
  transition: color 0.3s;
}

.collapse-btn:hover {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: #f5f5f5;
}

.avatar {
  background-color: #409eff;
}

.username {
  margin: 0 8px;
  font-size: 14px;
  color: #333;
}

.arrow {
  color: #999;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.footer {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fff;
  color: #999;
  font-size: 12px;
  border-top: 1px solid #f0f0f0;
}

/* 页面过渡动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.2s ease;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

/* 个人中心样式 */
.profile-container {
  text-align: center;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-uploader {
  cursor: pointer;
}

.profile-avatar {
  border: 2px solid #e4e7ed;
  transition: border-color 0.3s;
}

.profile-avatar:hover {
  border-color: #409eff;
}

.avatar-upload-text {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
