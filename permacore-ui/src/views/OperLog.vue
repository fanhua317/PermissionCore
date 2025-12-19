<template>
  <div class="oper-log">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">操作日志</span>
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
              v-model="searchOperator"
              placeholder="搜索操作人"
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
              placeholder="操作状态"
              style="width: 120px; margin-right: 12px"
              clearable
              @change="handleSearch"
            >
              <el-option label="成功" :value="0" />
              <el-option label="失败" :value="1" />
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
        <el-table-column prop="title" label="操作模块" width="120" />
        <el-table-column prop="businessType" label="业务类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getBusinessTypeTag(row.businessType)" size="small">
              {{ getBusinessTypeName(row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="method" label="请求方法" show-overflow-tooltip />
        <el-table-column prop="operIp" label="操作IP" width="140" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operTime" label="操作时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleDetail(row)">
              <el-icon><View /></el-icon>详情
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
          @size-change="getLogList"
          @current-change="getLogList"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="700px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="操作模块">{{ currentLog.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          <el-tag :type="getBusinessTypeTag(currentLog.businessType)">
            {{ getBusinessTypeName(currentLog.businessType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ currentLog.operIp }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.operTime }}</el-descriptions-item>
        <el-descriptions-item label="操作状态">
          <el-tag :type="currentLog.status === 0 ? 'success' : 'danger'">
            {{ currentLog.status === 0 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求方法" :span="2">{{ currentLog.method }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <el-scrollbar max-height="150px">
            <pre class="json-content">{{ formatJson(currentLog.operParam) }}</pre>
          </el-scrollbar>
        </el-descriptions-item>
        <el-descriptions-item label="返回结果" :span="2">
          <el-scrollbar max-height="150px">
            <pre class="json-content">{{ formatJson(currentLog.jsonResult) }}</pre>
          </el-scrollbar>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="currentLog.errorMsg">
          <span class="error-msg">{{ currentLog.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';
import { Search, Refresh, Delete, View } from '@element-plus/icons-vue';

const loading = ref(false);
const logList = ref<any[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);

const searchOperator = ref('');
const searchStatus = ref<number | undefined>(undefined);
const dateRange = ref<[string, string] | null>(null);

const detailVisible = ref(false);
const currentLog = ref<any>(null);

const businessTypeMap: Record<number, { name: string; tag: string }> = {
  0: { name: '其他', tag: 'info' },
  1: { name: '新增', tag: 'success' },
  2: { name: '修改', tag: 'warning' },
  3: { name: '删除', tag: 'danger' },
  4: { name: '查询', tag: '' },
  5: { name: '导出', tag: 'info' },
  6: { name: '导入', tag: 'info' },
};

const getBusinessTypeName = (type: number) => businessTypeMap[type]?.name || '未知';
const getBusinessTypeTag = (type: number) => businessTypeMap[type]?.tag || 'info';

const formatJson = (str: string) => {
  if (!str) return '-';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
};

const getLogList = async () => {
  loading.value = true;
  try {
    const params: any = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    };
    if (searchOperator.value) params.operatorName = searchOperator.value;
    if (searchStatus.value !== undefined) params.status = searchStatus.value;
    if (dateRange.value) {
      params.startTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }
    const res: any = await request.get('/api/oper-log/page', { params });
    logList.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } catch (error) {
    console.error('Failed to get oper log list:', error);
    ElMessage.error('获取操作日志失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNo.value = 1;
  getLogList();
};

const handleRefresh = () => {
  searchOperator.value = '';
  searchStatus.value = undefined;
  dateRange.value = null;
  getLogList();
};

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有操作日志吗？此操作不可恢复。', '警告', { type: 'warning' });
    await request.delete('/api/oper-log/clear');
    ElMessage.success('清空成功');
    getLogList();
  } catch (error) {
    // 用户取消
  }
};

const handleDetail = (row: any) => {
  currentLog.value = row;
  detailVisible.value = true;
};

onMounted(() => {
  getLogList();
});
</script>

<style scoped>
.oper-log {
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

.json-content {
  margin: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-msg {
  color: #f56c6c;
}
</style>
