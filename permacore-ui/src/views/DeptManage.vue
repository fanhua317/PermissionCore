<template>
  <div class="dept-manage">
    <el-row :gutter="20">
      <!-- 部门树 -->
      <el-col :xs="24" :sm="8" :md="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>部门结构</span>
              <el-button type="primary" size="small" @click="handleCreate(null)">
                <el-icon><Plus /></el-icon>
              </el-button>
            </div>
          </template>
          <el-input
            v-model="filterText"
            placeholder="搜索部门"
            clearable
            style="margin-bottom: 12px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-tree
            ref="treeRef"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            :filter-node-method="filterNode"
            highlight-current
            default-expand-all
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <el-icon><OfficeBuilding /></el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span class="node-count">({{ data.userCount || 0 }})</span>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 部门详情 -->
      <el-col :xs="24" :sm="16" :md="18">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ currentDept ? currentDept.deptName : '请选择部门' }}</span>
              <div class="header-actions" v-if="currentDept">
                <el-button type="primary" @click="handleCreate(currentDept)">
                  <el-icon><Plus /></el-icon>添加子部门
                </el-button>
                <el-button @click="handleEdit(currentDept)">
                  <el-icon><Edit /></el-icon>编辑
                </el-button>
                <el-button type="danger" @click="handleDelete(currentDept.id)">
                  <el-icon><Delete /></el-icon>删除
                </el-button>
              </div>
            </div>
          </template>

          <template v-if="currentDept">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="部门ID">{{ currentDept.id }}</el-descriptions-item>
              <el-descriptions-item label="部门名称">{{ currentDept.deptName }}</el-descriptions-item>
              <el-descriptions-item label="部门编码">
                <el-tag>{{ currentDept.deptCode || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="负责人">{{ currentDept.leader || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ currentDept.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentDept.status ? 'success' : 'danger'">
                  {{ currentDept.status ? '正常' : '停用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="排序">{{ currentDept.orderNum }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ currentDept.createTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <!-- 部门用户列表 -->
            <div class="dept-users">
              <h4>部门成员</h4>
              <el-table :data="deptUsers" border stripe v-loading="usersLoading" max-height="300">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="username" label="用户名" width="120" />
                <el-table-column prop="nickname" label="昵称" />
                <el-table-column prop="email" label="邮箱" />
                <el-table-column prop="status" label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.status ? 'success' : 'danger'" size="small">
                      {{ row.status ? '正常' : '禁用' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>

          <el-empty v-else description="请在左侧选择一个部门" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建/编辑部门对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form :model="deptForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="deptForm.parentId"
            :data="deptTreeForSelect"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择上级部门"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="deptForm.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="deptForm.deptCode" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="deptForm.leader" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="deptForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="deptForm.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
import { ref, onMounted, watch, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Plus, Search, Edit, Delete, OfficeBuilding } from '@element-plus/icons-vue';

const deptTree = ref<any[]>([]);
const currentDept = ref<any>(null);
const deptUsers = ref<any[]>([]);
const usersLoading = ref(false);
const filterText = ref('');
const treeRef = ref();

const dialogVisible = ref(false);
const dialogTitle = ref('新建部门');
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstance>();

const emptyForm = () => ({
  id: null as number | null,
  parentId: 0,
  deptName: '',
  deptCode: '',
  leader: '',
  phone: '',
  orderNum: 0,
  status: 1,
});

const deptForm = ref(emptyForm());

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
};

const deptTreeForSelect = computed(() => {
  return [{ id: 0, deptName: '顶级部门', children: deptTree.value }];
});

watch(filterText, (val) => {
  treeRef.value?.filter(val);
});

const filterNode = (value: string, data: any) => {
  if (!value) return true;
  return data.deptName.includes(value);
};

const getDeptTree = async () => {
  try {
    const res: any = await request.get('/api/dept/tree');
    deptTree.value = res ?? [];
  } catch (error) {
    console.error('Failed to get dept tree:', error);
    ElMessage.error('获取部门列表失败');
  }
};

const handleNodeClick = async (data: any) => {
  currentDept.value = data;
  usersLoading.value = true;
  try {
    const res: any = await request.get('/api/user/page', {
      params: { pageNo: 1, pageSize: 100, deptId: data.id },
    });
    deptUsers.value = res?.records ?? [];
  } catch (error) {
    deptUsers.value = [];
  } finally {
    usersLoading.value = false;
  }
};

const handleCreate = (parent: any) => {
  dialogTitle.value = '新建部门';
  isEdit.value = false;
  deptForm.value = emptyForm();
  if (parent) {
    deptForm.value.parentId = parent.id;
  }
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑部门';
  isEdit.value = true;
  deptForm.value = { ...row };
  dialogVisible.value = true;
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该部门吗？如有子部门或用户将无法删除。', '提示', { type: 'warning' });
    await request.delete(`/api/dept/${id}`);
    ElMessage.success('删除成功');
    currentDept.value = null;
    getDeptTree();
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
        await request.put(`/api/dept/${deptForm.value.id}`, deptForm.value);
      } else {
        await request.post('/api/dept', deptForm.value);
      }
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      getDeptTree();
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    } finally {
      submitLoading.value = false;
    }
  });
};

onMounted(() => {
  getDeptTree();
});
</script>

<style scoped>
.dept-manage {
  min-height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  padding-right: 8px;
}

.node-label {
  margin-left: 6px;
}

.node-count {
  margin-left: 4px;
  color: #909399;
  font-size: 12px;
}

.dept-users {
  margin-top: 24px;
}

.dept-users h4 {
  margin-bottom: 12px;
  color: #303133;
}
</style>
