<template>
  <div class="sod-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">职责分离约束（RBAC3）</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>新建约束
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="约束名称">
          <el-input v-model="searchForm.constraintName" placeholder="请输入约束名称" clearable />
        </el-form-item>
        <el-form-item label="约束类型">
          <el-select v-model="searchForm.constraintType" placeholder="全部" clearable style="width: 150px">
            <el-option label="静态互斥(SSD)" :value="1" />
            <el-option label="动态互斥(DSD)" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="constraintName" label="约束名称" min-width="180">
          <template #default="{ row }">
            <el-tag :type="row.constraintType === 1 ? 'danger' : 'warning'" effect="plain" size="small" style="margin-right: 8px">
              {{ row.constraintType === 1 ? 'SSD' : 'DSD' }}
            </el-tag>
            {{ row.constraintName }}
          </template>
        </el-table-column>
        <el-table-column label="互斥角色" min-width="250">
          <template #default="{ row }">
            <div class="role-tags">
              <el-tag 
                v-for="roleId in parseRoleSet(row.roleSet)" 
                :key="roleId" 
                size="small" 
                type="info"
                style="margin: 2px"
              >
                {{ getRoleName(roleId) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="约束类型" width="120" align="center">
          <template #default="{ row }">
            <el-tooltip :content="row.constraintType === 1 ? '用户不能同时被分配这些互斥角色' : '用户在同一会话中不能同时激活这些互斥角色'">
              <el-tag :type="row.constraintType === 1 ? 'danger' : 'warning'">
                {{ row.constraintType === 1 ? '静态互斥' : '动态互斥' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row.id)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" destroy-on-close>
      <el-form :model="sodForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="约束名称" prop="constraintName">
          <el-input v-model="sodForm.constraintName" placeholder="请输入约束名称，如：审计员与开发人员互斥" />
        </el-form-item>
        <el-form-item label="约束类型" prop="constraintType">
          <el-radio-group v-model="sodForm.constraintType">
            <el-radio :value="1">
              <span>静态互斥(SSD)</span>
              <el-tooltip content="用户不能同时被分配这些互斥角色">
                <el-icon style="margin-left: 4px"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
            <el-radio :value="2">
              <span>动态互斥(DSD)</span>
              <el-tooltip content="用户在同一会话中不能同时激活这些互斥角色">
                <el-icon style="margin-left: 4px"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="互斥角色" prop="selectedRoles">
          <el-select 
            v-model="sodForm.selectedRoles" 
            multiple 
            placeholder="请选择至少2个互斥角色"
            style="width: 100%"
          >
            <el-option 
              v-for="role in allRoles" 
              :key="role.id" 
              :label="role.roleName" 
              :value="role.id"
            />
          </el-select>
          <div class="form-tip">选择的角色之间将形成互斥约束</div>
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
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Plus, Edit, Delete, Search, Refresh, QuestionFilled } from '@element-plus/icons-vue';

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<any[]>([]);
const allRoles = ref<any[]>([]);
const roleMap = ref<Record<number, string>>({});

const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const searchForm = ref({
  constraintName: '',
  constraintType: null as number | null,
});

const dialogVisible = ref(false);
const dialogTitle = ref('新建约束');
const isEdit = ref(false);
const formRef = ref<FormInstance>();

const emptyForm = () => ({
  id: null as number | null,
  constraintName: '',
  constraintType: 1,
  selectedRoles: [] as number[],
});

const sodForm = ref(emptyForm());

const validateRoles = (_rule: any, value: number[], callback: any) => {
  if (!value || value.length < 2) {
    callback(new Error('请选择至少2个互斥角色'));
  } else {
    callback();
  }
};

const rules: FormRules = {
  constraintName: [{ required: true, message: '请输入约束名称', trigger: 'blur' }],
  constraintType: [{ required: true, message: '请选择约束类型', trigger: 'change' }],
  selectedRoles: [{ validator: validateRoles, trigger: 'change' }],
};

const parseRoleSet = (roleSet: string): number[] => {
  try {
    return JSON.parse(roleSet || '[]');
  } catch {
    return [];
  }
};

const getRoleName = (roleId: number): string => {
  return roleMap.value[roleId] || `角色#${roleId}`;
};

const fetchRoles = async () => {
  try {
    const res: any = await request.get('/api/role/list');
    allRoles.value = res ?? [];
    // 构建roleId到roleName的映射
    allRoles.value.forEach((role: any) => {
      roleMap.value[role.id] = role.roleName;
    });
  } catch (error) {
    console.error('Failed to fetch roles:', error);
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res: any = await request.get('/api/sod-constraint/page', {
      params: {
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        constraintName: searchForm.value.constraintName || undefined,
        constraintType: searchForm.value.constraintType || undefined,
      },
    });
    tableData.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } catch (error) {
    console.error('Failed to fetch SoD constraints:', error);
    ElMessage.error('获取约束列表失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNo.value = 1;
  fetchData();
};

const handleReset = () => {
  searchForm.value = {
    constraintName: '',
    constraintType: null,
  };
  handleSearch();
};

const handleCreate = () => {
  dialogTitle.value = '新建约束';
  isEdit.value = false;
  sodForm.value = emptyForm();
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑约束';
  isEdit.value = true;
  sodForm.value = {
    id: row.id,
    constraintName: row.constraintName,
    constraintType: row.constraintType,
    selectedRoles: parseRoleSet(row.roleSet),
  };
  dialogVisible.value = true;
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该约束吗？删除后相关角色的互斥限制将解除。', '提示', { type: 'warning' });
    await request.delete(`/api/sod-constraint/${id}`);
    ElMessage.success('删除成功');
    fetchData();
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
      const data = {
        constraintName: sodForm.value.constraintName,
        constraintType: sodForm.value.constraintType,
        roleSet: JSON.stringify(sodForm.value.selectedRoles),
      };
      if (isEdit.value) {
        await request.put(`/api/sod-constraint/${sodForm.value.id}`, data);
      } else {
        await request.post('/api/sod-constraint', data);
      }
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      fetchData();
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
    } finally {
      submitLoading.value = false;
    }
  });
};

onMounted(() => {
  fetchRoles();
  fetchData();
});
</script>

<style scoped>
.sod-manage {
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

.search-form {
  margin-bottom: 16px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
