<template>
  <el-container class="main-layout">
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

        <el-sub-menu v-if="systemMenus.length" index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item v-for="item in systemMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="auditMenus.length" index="audit">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>日志审计</span>
          </template>
          <el-menu-item v-for="item in auditMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
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
          <el-button v-if="availableRoles.length" :icon="Key" class="role-switch" @click="openRoleDialog">
            {{ activeRoleLabel }}
          </el-button>

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

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>

      <el-footer class="footer">
        <span>PermaCore IAM &copy; 2024 - 基于 RBAC3 的权限管理系统</span>
      </el-footer>
    </el-container>

    <el-dialog v-model="roleDialogVisible" title="当前激活角色" width="480px" destroy-on-close>
      <el-checkbox-group v-model="roleForm.activeRoleIds" class="role-list">
        <el-checkbox v-for="role in availableRoles" :key="role.id" :label="role.id" border>
          <span class="role-name">{{ role.roleName }}</span>
          <span class="role-key">{{ role.roleKey }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="saveRoleSelection">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px" destroy-on-close>
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
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

    <el-dialog v-model="profileDialogVisible" title="个人中心" width="500px" destroy-on-close>
      <div class="profile-container">
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
        <el-form :model="profileForm" label-width="80px" class="profile-form">
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
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore, type SessionRole } from '@/store/user';
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
const userAvatar = ref('');

const allSystemMenus = [
  { path: '/user', title: '用户管理', permission: 'system:user', icon: User },
  { path: '/role', title: '角色管理', permission: 'system:role', icon: UserFilled },
  { path: '/permission', title: '权限管理', permission: 'system:permission', icon: Key },
  { path: '/sod', title: '职责分离', permission: 'system:sod', icon: Lock },
  { path: '/dept', title: '部门管理', permission: 'system:dept', icon: OfficeBuilding },
];

const allAuditMenus = [
  { path: '/login-log', title: '登录日志', permission: 'system:log', icon: Tickets },
  { path: '/oper-log', title: '操作日志', permission: 'system:log', icon: List },
];

const systemMenus = computed(() => allSystemMenus.filter((item) => userStore.hasPermission(item.permission)));
const auditMenus = computed(() => allAuditMenus.filter((item) => userStore.hasPermission(item.permission)));

const currentRoute = computed(() => route.path);
const breadcrumbTitle = computed(() => (route.meta.title as string) || '');

const availableRoles = computed<SessionRole[]>(() => userStore.userInfo?.roles ?? []);
const activeRoleLabel = computed(() => {
  const roles = availableRoles.value.filter((role) => userStore.userInfo?.activeRoleIds.includes(role.id));
  if (!roles.length) return '未激活角色';
  const firstRole = roles[0];
  if (!firstRole) return '未激活角色';
  if (roles.length === 1) return firstRole.roleName;
  return `${firstRole.roleName} 等 ${roles.length} 个角色`;
});

const roleDialogVisible = ref(false);
const roleSaving = ref(false);
const roleForm = ref({ activeRoleIds: [] as number[] });

const passwordDialogVisible = ref(false);
const passwordLoading = ref(false);
const passwordFormRef = ref<FormInstance>();
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const profileDialogVisible = ref(false);
const profileLoading = ref(false);
const profileForm = ref({
  nickname: '',
  email: '',
  phone: '',
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

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
};

const openRoleDialog = async () => {
  try {
    await userStore.loadSessionRoles();
    roleForm.value.activeRoleIds = [...(userStore.userInfo?.activeRoleIds ?? [])];
    roleDialogVisible.value = true;
  } catch (error: any) {
    ElMessage.error(error?.message || '获取会话角色失败');
  }
};

const saveRoleSelection = async () => {
  roleSaving.value = true;
  try {
    await userStore.switchActiveRoles(roleForm.value.activeRoleIds);
    roleDialogVisible.value = false;
    ElMessage.success('角色切换成功');

    const requiredPermission = route.meta.permission as string | undefined;
    if (requiredPermission && !userStore.hasPermission(requiredPermission)) {
      router.push('/dashboard');
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '角色切换失败');
  } finally {
    roleSaving.value = false;
  }
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
        await userStore.logout();
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
      await userStore.logout();
      router.push('/login');
    } catch (error: any) {
      ElMessage.error(error?.message || '修改密码失败');
    } finally {
      passwordLoading.value = false;
    }
  });
};

const openProfileDialog = async () => {
  profileDialogVisible.value = true;
  try {
    await userStore.fetchUserInfo();
    profileForm.value = {
      nickname: userStore.userInfo?.nickname || '',
      email: userStore.userInfo?.email || '',
      phone: userStore.userInfo?.phone || '',
    };
  } catch {
    ElMessage.error('获取用户信息失败');
  }
};

const handleUpdateProfile = async () => {
  profileLoading.value = true;
  try {
    await request.put('/api/auth/profile', profileForm.value);
    ElMessage.success('个人信息更新成功');
    profileDialogVisible.value = false;
    await userStore.fetchUserInfo();
  } catch (error: any) {
    ElMessage.error(error?.message || '更新失败');
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
      localStorage.setItem('userAvatar', res.avatarUrl);
      ElMessage.success('头像上传成功');
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '头像上传失败');
  }
};

onMounted(() => {
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
  overflow: hidden;
  transition: width 0.3s;
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
  height: calc(100vh - 60px);
  border-right: none;
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

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}

.collapse-btn:hover {
  color: #409eff;
}

.role-switch {
  max-width: 220px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 4px;
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
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.role-list {
  display: grid;
  gap: 10px;
}

.role-list :deep(.el-checkbox) {
  width: 100%;
  height: auto;
  padding: 10px 12px;
  margin-right: 0;
}

.role-name {
  font-weight: 600;
}

.role-key {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

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

.profile-form {
  margin-top: 20px;
}
</style>
