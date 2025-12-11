<template>
  <div class="user-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="handleCreate">新建用户</el-button>
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
const userList = ref([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const dialogVisible = ref(false);
const dialogTitle = ref('新建用户');
const isEdit = ref(false);
const userForm = ref({
  id: null,
  username: '',
  nickname: '',
  email: '',
  password: '',
});

const getUserList = async () => {
  loading.value = true;
  try {
    const res = await request.get('/api/user/page', {
      params: { pageNo: pageNo.value, pageSize: pageSize.value },
    });
    userList.value = res.records;
    total.value = res.total;
  } catch (error) {
    ElMessage.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const handleCreate = () => {
  dialogTitle.value = '新建用户';
  isEdit.value = false;
  userForm.value = { id: null, username: '', nickname: '', email: '', password: '' };
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户';
  isEdit.value = true;
  userForm.value = { ...row, password: '' };
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
      await request.put(`/api/user/${userForm.value.id}`, userForm.value);
    } else {
      await request.post('/api/user', userForm.value);
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
    dialogVisible.value = false;
    getUserList();
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
}
</style>