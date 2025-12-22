<template>
  <div class="role-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">角色管理</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索角色名称"
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
              <el-icon><Plus /></el-icon>新建角色
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="roleList" border v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="roleKey" label="角色编码" width="150">
          <template #default="{ row }">
            <el-tag>{{ row.roleKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="remark" label="描述" show-overflow-tooltip />
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
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handlePermission(row)">
              <el-icon><Key /></el-icon>权限
            </el-button>
            <el-button size="small" type="primary" link @click="handleInheritance(row)">
              <el-icon><Connection /></el-icon>继承
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
          @size-change="getRoleList"
          @current-change="getRoleList"
        />
      </div>
    </el-card>

    <!-- 新建/编辑角色对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form :model="roleForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色编码" prop="roleKey">
          <el-input v-model="roleForm.roleKey" :disabled="isEdit" placeholder="请输入角色编码，如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述" prop="remark">
          <el-input v-model="roleForm.remark" type="textarea" :rows="3" placeholder="请输入角色描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="roleForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permissionDialogVisible" title="分配权限" width="500px" destroy-on-close>
      <div class="permission-tree-container">
        <el-tree
          ref="permissionTreeRef"
          :data="permissionTree"
          :props="{ label: 'permName', children: 'children' }"
          show-checkbox
          node-key="id"
          :default-checked-keys="checkedPermissionIds"
          check-strictly
        />
      </div>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePermissions" :loading="submitLoading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色继承对话框 -->
    <el-dialog v-model="inheritanceDialogVisible" title="角色继承配置" width="600px" destroy-on-close>
      <el-alert type="info" show-icon :closable="false" style="margin-bottom: 16px">
        <template #title>
          角色继承说明：选中的父角色的所有权限将自动继承给当前角色
        </template>
      </el-alert>
      <el-transfer
        v-model="selectedParentRoles"
        :data="availableParentRoles"
        :titles="['可选父角色', '已继承角色']"
        :props="{ key: 'id', label: 'roleName' }"
      />
      <template #footer>
        <el-button @click="inheritanceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveInheritance" :loading="submitLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Search, Refresh, Plus, Key, Connection, Edit, Delete } from '@element-plus/icons-vue';

const loading = ref(false);
const submitLoading = ref(false);
const roleList = ref<any[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const searchKeyword = ref('');

const dialogVisible = ref(false);
const dialogTitle = ref('新建角色');
const isEdit = ref(false);
const formRef = ref<FormInstance>();

const permissionDialogVisible = ref(false);
const permissionTreeRef = ref();
const permissionTree = ref<any[]>([]);
const checkedPermissionIds = ref<number[]>([]);
const currentRoleId = ref<number | null>(null);

const inheritanceDialogVisible = ref(false);
const selectedParentRoles = ref<number[]>([]);
const availableParentRoles = ref<any[]>([]);

const emptyForm = () => ({
  id: null as number | null,
  roleKey: '',
  roleName: '',
  remark: '',
  status: 1,
});

const roleForm = ref(emptyForm());

const rules: FormRules = {
  roleKey: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z_]+$/, message: '角色编码只能包含大写字母和下划线', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
};

const getRoleList = async () => {
  loading.value = true;
  try {
    const res: any = await request.get('/api/role/page', {
      params: { pageNo: pageNo.value, pageSize: pageSize.value, keyword: searchKeyword.value },
    });
    roleList.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } catch (error) {
    console.error('Failed to get role list:', error);
    ElMessage.error('获取角色列表失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNo.value = 1;
  getRoleList();
};

const handleRefresh = () => {
  searchKeyword.value = '';
  getRoleList();
};

const handleCreate = () => {
  dialogTitle.value = '新建角色';
  isEdit.value = false;
  roleForm.value = emptyForm();
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑角色';
  isEdit.value = true;
  roleForm.value = { ...row };
  dialogVisible.value = true;
};

const handleStatusChange = async (row: any) => {
  try {
    await request.put(`/api/role/${row.id}`, { status: row.status });
    ElMessage.success('状态更新成功');
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1;
    ElMessage.error('状态更新失败');
  }
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该角色吗？删除后相关用户将失去该角色的权限。', '提示', { type: 'warning' });
    await request.delete(`/api/role/${id}`);
    ElMessage.success('删除成功');
    getRoleList();
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
        await request.put(`/api/role/${roleForm.value.id}`, roleForm.value);
      } else {
        await request.post('/api/role', roleForm.value);
      }
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      getRoleList();
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    } finally {
      submitLoading.value = false;
    }
  });
};

const handlePermission = async (row: any) => {
  currentRoleId.value = row.id;
  try {
    // 获取权限树
    const perms: any = await request.get('/api/permission/tree');
    permissionTree.value = perms ?? [];
    // 获取当前角色已有权限
    const rolePerms: any = await request.get(`/api/role/${row.id}/permissions`);
    checkedPermissionIds.value = (rolePerms ?? []).map((p: any) => p.id);
    permissionDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('获取权限数据失败');
  }
};

const handleSavePermissions = async () => {
  if (!currentRoleId.value) return;
  submitLoading.value = true;
  try {
    const checkedKeys = permissionTreeRef.value?.getCheckedKeys() ?? [];
    await request.put(`/api/role/${currentRoleId.value}/permissions`, { permissionIds: checkedKeys });
    ElMessage.success('权限分配成功');
    permissionDialogVisible.value = false;
  } catch (error) {
    ElMessage.error('权限分配失败');
  } finally {
    submitLoading.value = false;
  }
};

const handleInheritance = async (row: any) => {
  currentRoleId.value = row.id;
  try {
    // 获取所有角色（排除自身）
    const allRoles: any = await request.get('/api/role/list');
    availableParentRoles.value = (allRoles ?? []).filter((r: any) => r.id !== row.id);
    // 获取当前角色的父角色
    const parents: any = await request.get(`/api/role-inheritance/parents/${row.id}`);
    selectedParentRoles.value = (parents ?? []).map((r: any) => r.id);
    inheritanceDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('获取角色继承数据失败');
  }
};

const handleSaveInheritance = async () => {
  if (!currentRoleId.value) return;
  submitLoading.value = true;
  try {
    await request.put(`/api/role-inheritance/${currentRoleId.value}`, { parentRoleIds: selectedParentRoles.value });
    ElMessage.success('角色继承配置成功');
    inheritanceDialogVisible.value = false;
  } catch (error) {
    ElMessage.error('角色继承配置失败');
  } finally {
    submitLoading.value = false;
  }
};

onMounted(() => {
  getRoleList();
});
</script>

<style scoped>
.role-manage {
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

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.permission-tree-container {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
}
</style>
