<template>
  <div class="permission-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">权限管理</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="primary" @click="handleCreate(null)">
              <el-icon><Plus /></el-icon>新建权限
            </el-button>
            <el-button type="success" @click="expandAll">
              <el-icon><Expand /></el-icon>全部展开
            </el-button>
            <el-button @click="collapseAll">
              <el-icon><Fold /></el-icon>全部折叠
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        ref="tableRef"
        :data="permissionTree"
        row-key="id"
        v-loading="loading"
        border
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
      >
        <el-table-column prop="permName" label="权限名称" min-width="200">
          <template #default="{ row }">
            <el-icon v-if="row.type === 'MENU'" class="perm-icon"><Menu /></el-icon>
            <el-icon v-else-if="row.type === 'BUTTON'" class="perm-icon"><Switch /></el-icon>
            <el-icon v-else class="perm-icon"><Operation /></el-icon>
            <span>{{ row.permName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="permCode" label="权限编码" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.permCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.type)">{{ getTypeName(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status ? 'success' : 'danger'" size="small">
              {{ row.status ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleCreate(row)">
              <el-icon><Plus /></el-icon>添加
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
    </el-card>

    <!-- 新建/编辑权限对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" destroy-on-close>
      <el-form :model="permForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级权限">
          <el-tree-select
            v-model="permForm.parentId"
            :data="permissionTreeForSelect"
            :props="{ label: 'permName', value: 'id', children: 'children' }"
            placeholder="请选择上级权限（可选）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="权限类型" prop="type">
          <el-radio-group v-model="permForm.type">
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
            <el-radio value="API">接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限名称" prop="permName">
          <el-input v-model="permForm.permName" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permCode">
          <el-input v-model="permForm.permCode" placeholder="如：user:view, role:create" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="permForm.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="permForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="permForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Refresh, Plus, Edit, Delete, Menu, Switch, Operation, Expand, Fold } from '@element-plus/icons-vue';

const loading = ref(false);
const submitLoading = ref(false);
const permissionTree = ref<any[]>([]);
const tableRef = ref();

const dialogVisible = ref(false);
const dialogTitle = ref('新建权限');
const isEdit = ref(false);
const formRef = ref<FormInstance>();

const emptyForm = () => ({
  id: null as number | null,
  parentId: null as number | null,
  permName: '',
  permCode: '',
  type: 'MENU',
  orderNum: 0,
  status: 1,
  remark: '',
});

const permForm = ref(emptyForm());

const rules: FormRules = {
  permName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  permCode: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    { pattern: /^[a-z:_]+$/, message: '权限编码只能包含小写字母、冒号和下划线', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择权限类型', trigger: 'change' }],
};

const permissionTreeForSelect = computed(() => {
  const addRoot = [{ id: 0, permName: '根节点', children: permissionTree.value }];
  return addRoot;
});

const getTypeName = (type: string) => {
  const map: Record<string, string> = { MENU: '菜单', BUTTON: '按钮', API: '接口' };
  return map[type] || type;
};

const getTypeTag = (type: string) => {
  const map: Record<string, string> = { MENU: '', BUTTON: 'warning', API: 'info' };
  return map[type] || 'info';
};

const getPermissionTree = async () => {
  loading.value = true;
  try {
    const res: any = await request.get('/api/permission/tree');
    permissionTree.value = res ?? [];
  } catch (error) {
    console.error('Failed to get permission tree:', error);
    ElMessage.error('获取权限列表失败');
  } finally {
    loading.value = false;
  }
};

const handleRefresh = () => {
  getPermissionTree();
};

const expandAll = () => {
  toggleRowExpansion(permissionTree.value, true);
};

const collapseAll = () => {
  toggleRowExpansion(permissionTree.value, false);
};

const toggleRowExpansion = (data: any[], expanded: boolean) => {
  data.forEach((item) => {
    tableRef.value?.toggleRowExpansion(item, expanded);
    if (item.children && item.children.length > 0) {
      toggleRowExpansion(item.children, expanded);
    }
  });
};

const handleCreate = (parent: any) => {
  dialogTitle.value = '新建权限';
  isEdit.value = false;
  permForm.value = emptyForm();
  if (parent) {
    permForm.value.parentId = parent.id;
  }
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑权限';
  isEdit.value = true;
  permForm.value = { ...row };
  dialogVisible.value = true;
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该权限吗？如有子权限将一并删除。', '提示', { type: 'warning' });
    await request.delete(`/api/permission/${id}`);
    ElMessage.success('删除成功');
    getPermissionTree();
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
        await request.put(`/api/permission/${permForm.value.id}`, permForm.value);
      } else {
        await request.post('/api/permission', permForm.value);
      }
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      getPermissionTree();
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    } finally {
      submitLoading.value = false;
    }
  });
};

onMounted(() => {
  getPermissionTree();
});
</script>

<style scoped>
.permission-manage {
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
  gap: 8px;
}

.perm-icon {
  margin-right: 8px;
  color: #409eff;
}
</style>
