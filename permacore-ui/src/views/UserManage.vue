<template>
  <div class="user-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">用户管理</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名/昵称"
              style="width: 200px; margin-right: 12px"
              clearable
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>新建用户
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="userList" border v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="120">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="32" class="user-avatar">
                {{ row.nickname?.charAt(0) || row.username?.charAt(0) || 'U' }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleAssignRole(row)">
              <el-icon><UserFilled /></el-icon>角色
            </el-button>
            <el-button size="small" type="primary" link @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row.id)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getUserList"
          @current-change="getUserList"
        />
      </div>
    </el-card>

    <!-- 新建/编辑用户对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" destroy-on-close>
      <el-form :model="userForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select
            v-model="userForm.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择部门"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userForm.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px" destroy-on-close>
      <el-alert type="info" show-icon :closable="false" style="margin-bottom: 16px">
        <template #title>
          为用户 <strong>{{ currentUser?.nickname || currentUser?.username }}</strong> 分配角色
        </template>
      </el-alert>
      <el-checkbox-group v-model="selectedRoleIds" class="role-checkbox-group">
        <el-checkbox
          v-for="role in allRoles"
          :key="role.id"
          :value="role.id"
          :label="role.roleName"
          border
          class="role-checkbox"
        >
          <div class="role-item">
            <span class="role-name">{{ role.roleName }}</span>
            <el-tag size="small" type="info">{{ role.roleCode }}</el-tag>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles" :loading="submitLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Search, Refresh, Plus, UserFilled, Edit, Delete } from '@element-plus/icons-vue';

const loading = ref(false);
const submitLoading = ref(false);
const userList = ref<any[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const searchKeyword = ref('');

const dialogVisible = ref(false);
const dialogTitle = ref('新建用户');
const isEdit = ref(false);
const formRef = ref<FormInstance>();
const deptTree = ref<any[]>([]);

const roleDialogVisible = ref(false);
const currentUser = ref<any>(null);
const allRoles = ref<any[]>([]);
const selectedRoleIds = ref<number[]>([]);

const emptyForm = () => ({
  id: null as number | null,
  username: '',
  nickname: '',
  email: '',
  password: '',
  deptId: null as number | null,
  status: 1,
});

const userForm = ref(emptyForm());

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 个字符', trigger: 'blur' },
  ],
};

const getUserList = async () => {
  loading.value = true;
  try {
    const res: any = await request.get('/api/user/page', {
      params: { pageNo: pageNo.value, pageSize: pageSize.value, keyword: searchKeyword.value },
    });
    userList.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } catch (error) {
    console.error('Failed to get user list:', error);
    ElMessage.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const getDeptTree = async () => {
  try {
    const res: any = await request.get('/api/dept/tree');
    deptTree.value = res ?? [];
  } catch (error) {
    console.error('Failed to get dept tree:', error);
  }
};

const handleSearch = () => {
  pageNo.value = 1;
  getUserList();
};

const handleRefresh = () => {
  searchKeyword.value = '';
  getUserList();
};

const handleCreate = () => {
  dialogTitle.value = '新建用户';
  isEdit.value = false;
  userForm.value = emptyForm();
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户';
  isEdit.value = true;
  userForm.value = { ...row, password: '' };
  dialogVisible.value = true;
};

const handleStatusChange = async (row: any) => {
  try {
    await request.put(`/api/user/${row.id}`, { status: row.status });
    ElMessage.success('状态更新成功');
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1;
    ElMessage.error('状态更新失败');
  }
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该用户吗？', '提示', { type: 'warning' });
    await request.delete(`/api/user/${id}`);
    ElMessage.success('删除成功');
    getUserList();
  } catch (error) {
    // 用户取消
  }
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    submitLoading.value = true;
    try {
      if (isEdit.value) {
        const payload = {
          nickname: userForm.value.nickname,
          email: userForm.value.email,
          deptId: userForm.value.deptId,
          status: userForm.value.status,
        };
        await request.put(`/api/user/${userForm.value.id}`, payload);
      } else {
        await request.post('/api/user', userForm.value);
      }
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      getUserList();
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    } finally {
      submitLoading.value = false;
    }
  });
};

const handleAssignRole = async (row: any) => {
  currentUser.value = row;
  try {
    // 获取所有角色
    const roles: any = await request.get('/api/role/list');
    allRoles.value = roles ?? [];
    // 获取用户当前角色
    const userRoles: any = await request.get(`/api/user/${row.id}/roles`);
    selectedRoleIds.value = (userRoles ?? []).map((r: any) => r.id);
    roleDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('获取角色数据失败');
  }
};

const handleSaveRoles = async () => {
  if (!currentUser.value) return;
  submitLoading.value = true;
  try {
    await request.put(`/api/user/${currentUser.value.id}/roles`, { roleIds: selectedRoleIds.value });
    ElMessage.success('角色分配成功');
    roleDialogVisible.value = false;
  } catch (error) {
    ElMessage.error('角色分配失败');
  } finally {
    submitLoading.value = false;
  }
};

onMounted(() => {
  getUserList();
  getDeptTree();
});
</script>

<style scoped>
.user-manage {
  min-height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  font-size: 14px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-checkbox {
  width: 100%;
  margin-left: 0 !important;
}

.role-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.role-name {
  font-weight: 500;
}
</style>