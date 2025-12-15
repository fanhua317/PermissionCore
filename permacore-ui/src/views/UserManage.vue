<template>
  <div class="user-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">刷新</el-button>
            <el-button type="primary" @click="handleCreate">新建用户</el-button>
          </div>
        </div>
      </template>
      <el-table :data="userList" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status ? 'success' : 'danger'">
              {{ row.status ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="getUserList"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

const loading = ref(false);
const userList = ref<any[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const dialogVisible = ref(false);
const dialogTitle = ref('新建用户');
const isEdit = ref(false);
const emptyForm = () => ({
  id: null,
  username: '',
  nickname: '',
  email: '',
  password: '',
  status: 1,
});
const userForm = ref(emptyForm());

const getUserList = async () => {
  loading.value = true;
  try {
    const res: any = await request.get('/api/user/page', {
      params: { pageNo: pageNo.value, pageSize: pageSize.value },
    });
    if (res && res.data) {
      userList.value = res.data.records ?? [];
      total.value = res.data.total ?? 0;
    } else {
      userList.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error('Failed to get user list:', error);
    ElMessage.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const handleRefresh = () => {
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
  userForm.value = { ...row, password: '', status: row.status ?? 1 };
  dialogVisible.value = true;
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
  try {
    if (isEdit.value) {
      const payload = {
        nickname: userForm.value.nickname,
        email: userForm.value.email,
        status: userForm.value.status,
      };
      await request.put(`/api/user/${userForm.value.id}`, payload);
    } else {
      const payload = {
        username: userForm.value.username,
        nickname: userForm.value.nickname,
        email: userForm.value.email,
        password: userForm.value.password,
        deptId: 0,
        status: userForm.value.status,
      };
      await request.post('/api/user', payload);
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
    dialogVisible.value = false;
    handleRefresh();
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
  }
};

onMounted(() => {
  getUserList();
});
</script>

<style scoped>
.user-manage {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 12px;
}
</style>