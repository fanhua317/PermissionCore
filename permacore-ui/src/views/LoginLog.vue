<template>
  <div class="login-log">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">登录日志</span>
          <div class="header-actions">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px; margin-right: 12px"
              @change="handleSearch"
            />
            <el-input
              v-model="searchUsername"
              placeholder="搜索用户名"
              style="width: 150px; margin-right: 12px"
              clearable
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select
              v-model="searchStatus"
              placeholder="登录状态"
              style="width: 120px; margin-right: 12px"
              clearable
              @change="handleSearch"
            >
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-button @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="danger" @click="handleClear">
              <el-icon><Delete /></el-icon>清空日志
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="logList" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="ipAddr" label="登录IP" width="140" />
        <el-table-column prop="loginLocation" label="登录地点" width="150" />
        <el-table-column prop="browser" label="浏览器" width="120" show-overflow-tooltip />
        <el-table-column prop="os" label="操作系统" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="消息" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="登录时间" width="180" />
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getLogList"
          @current-change="getLogList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';
import { Search, Refresh, Delete } from '@element-plus/icons-vue';

const loading = ref(false);
const logList = ref<any[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const searchUsername = ref('');
const searchStatus = ref<number | undefined>(undefined);
const dateRange = ref<[string, string] | null>(null);

const getLogList = async () => {
  loading.value = true;
  try {
    const params: any = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    };
    if (searchUsername.value) params.username = searchUsername.value;
    if (searchStatus.value !== undefined) params.status = searchStatus.value;
    if (dateRange.value) {
      params.startTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }
    const res: any = await request.get('/api/login-log/page', { params });
    logList.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } catch (error) {
    console.error('Failed to get login log list:', error);
    ElMessage.error('获取登录日志失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNo.value = 1;
  getLogList();
};

const handleRefresh = () => {
  searchUsername.value = '';
  searchStatus.value = undefined;
  dateRange.value = null;
  getLogList();
};

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有登录日志吗？此操作不可恢复。', '警告', { type: 'warning' });
    await request.delete('/api/login-log/clear');
    ElMessage.success('清空成功');
    getLogList();
  } catch (error) {
    // 用户取消
  }
};

onMounted(() => {
  getLogList();
});
</script>

<style scoped>
.login-log {
  min-height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
