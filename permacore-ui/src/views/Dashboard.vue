<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">用户总数</div>
              <div class="stat-value">{{ stats.userCount }}</div>
            </div>
            <div class="stat-icon user-icon">
              <el-icon :size="40"><User /></el-icon>
            </div>
          </div>
          <div class="stat-footer">
            <span class="trend up">
              <el-icon><Top /></el-icon> 12%
            </span>
            <span class="label">较上月</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">角色数量</div>
              <div class="stat-value">{{ stats.roleCount }}</div>
            </div>
            <div class="stat-icon role-icon">
              <el-icon :size="40"><UserFilled /></el-icon>
            </div>
          </div>
          <div class="stat-footer">
            <span class="trend stable">
              <el-icon><Minus /></el-icon> 0%
            </span>
            <span class="label">较上月</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">权限点数</div>
              <div class="stat-value">{{ stats.permissionCount }}</div>
            </div>
            <div class="stat-icon permission-icon">
              <el-icon :size="40"><Key /></el-icon>
            </div>
          </div>
          <div class="stat-footer">
            <span class="trend up">
              <el-icon><Top /></el-icon> 5%
            </span>
            <span class="label">较上月</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">今日登录</div>
              <div class="stat-value">{{ stats.todayLogin }}</div>
            </div>
            <div class="stat-icon login-icon">
              <el-icon :size="40"><Monitor /></el-icon>
            </div>
          </div>
          <div class="stat-footer">
            <span class="trend up">
              <el-icon><Top /></el-icon> 28%
            </span>
            <span class="label">较昨日</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作和系统信息 -->
    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="16">
        <el-card class="quick-actions" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>快捷操作</span>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="6" v-for="action in quickActions" :key="action.title">
              <div class="action-item" @click="handleAction(action.path)">
                <div class="action-icon" :style="{ backgroundColor: action.color }">
                  <el-icon :size="24"><component :is="action.icon" /></el-icon>
                </div>
                <div class="action-title">{{ action.title }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="system-info" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          <div class="info-list">
            <div class="info-item" v-for="item in systemInfo" :key="item.label">
              <span class="info-label">{{ item.label }}</span>
              <span class="info-value">{{ item.value }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近操作日志 -->
    <el-card class="recent-logs" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>最近操作日志</span>
          <el-button text type="primary" @click="$router.push('/oper-log')">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </template>
      <el-table :data="recentLogs" stripe style="width: 100%">
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="title" label="操作模块" width="150" />
        <el-table-column prop="method" label="请求方法" />
        <el-table-column prop="operTime" label="操作时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue';
import { useRouter } from 'vue-router';
import request from '@/utils/request';
import {
  User,
  UserFilled,
  Key,
  Monitor,
  Top,
  Minus,
  ArrowRight,
  Plus,
  Setting,
  Document,
  OfficeBuilding,
} from '@element-plus/icons-vue';

const router = useRouter();

const stats = ref({
  userCount: 0,
  roleCount: 0,
  permissionCount: 0,
  todayLogin: 0,
});

const quickActions = ref([
  { title: '新建用户', icon: markRaw(Plus), color: '#409eff', path: '/user' },
  { title: '角色管理', icon: markRaw(UserFilled), color: '#67c23a', path: '/role' },
  { title: '权限配置', icon: markRaw(Key), color: '#e6a23c', path: '/permission' },
  { title: '系统日志', icon: markRaw(Document), color: '#909399', path: '/oper-log' },
]);

const systemInfo = ref([
  { label: '系统版本', value: 'v1.0.0' },
  { label: '后端框架', value: 'Spring Boot 3.x' },
  { label: '前端框架', value: 'Vue 3 + Element Plus' },
  { label: '权限模型', value: 'RBAC3' },
  { label: '数据库', value: 'MySQL 8.x' },
  { label: '缓存', value: 'Redis + Caffeine' },
]);

const recentLogs = ref<any[]>([]);

const handleAction = (path: string) => {
  router.push(path);
};

const fetchStats = async () => {
  try {
    const res: any = await request.get('/api/dashboard/stats');
    stats.value = {
      userCount: res?.userCount ?? 0,
      roleCount: res?.roleCount ?? 0,
      permissionCount: res?.permissionCount ?? 0,
      todayLogin: res?.todayLoginCount ?? 0,
    };
  } catch (error) {
    console.error('Failed to fetch stats:', error);
    // 使用默认值
    stats.value = {
      userCount: 0,
      roleCount: 0,
      permissionCount: 0,
      todayLogin: 0,
    };
  }
};

const fetchRecentLogs = async () => {
  try {
    const res: any = await request.get('/api/oper-log/page', {
      params: { pageNo: 1, pageSize: 5 },
    });
    recentLogs.value = res?.records ?? [];
  } catch (error) {
    console.error('Failed to fetch logs:', error);
    recentLogs.value = [];
  }
};

onMounted(() => {
  fetchStats();
  fetchRecentLogs();
});
</script>

<style scoped>
.dashboard {
  min-height: 100%;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  overflow: hidden;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.user-icon { background: linear-gradient(135deg, #409eff, #66b1ff); }
.role-icon { background: linear-gradient(135deg, #67c23a, #85ce61); }
.permission-icon { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.login-icon { background: linear-gradient(135deg, #f56c6c, #f78989); }

.stat-footer {
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 12px;
}

.trend {
  display: inline-flex;
  align-items: center;
  margin-right: 8px;
}

.trend.up { color: #67c23a; }
.trend.down { color: #f56c6c; }
.trend.stable { color: #909399; }

.label {
  color: #909399;
}

.content-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s;
}

.action-item:hover {
  background-color: #f5f7fa;
  transform: translateY(-2px);
}

.action-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 12px;
}

.action-title {
  font-size: 14px;
  color: #606266;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  color: #909399;
  font-size: 14px;
}

.info-value {
  color: #303133;
  font-size: 14px;
  font-weight: 500;
}

.recent-logs {
  border-radius: 8px;
}

@media (max-width: 768px) {
  .stat-cards .el-col {
    margin-bottom: 16px;
  }
}
</style>
