<template>
  <div class="permission-demo-container">
    <!-- 1. 权限概览 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>🔐 权限系统概览</span>
          <el-tag type="success" size="small">当前用户权限数: {{ permissions.length }}</el-tag>
        </div>
      </template>
      <div class="overview-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名" width="120">{{
            userStore.userName
          }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="role in roles" :key="role" size="small" style="margin-right: 4px">
              {{ role }}
            </el-tag>
            <el-tag v-if="roles.length === 0" type="info" size="small">无角色</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="权限列表" :span="2">
            <div class="permissions-tags">
              <el-tag
                v-for="perm in permissions"
                :key="perm"
                size="small"
                type="warning"
                style="margin: 2px"
              >
                {{ perm }}
              </el-tag>
              <el-tag v-if="permissions.length === 0" type="info" size="small">无权限</el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="权限列表2" :span="2">
            <div class="permissions-tags">
              <el-tag
                v-for="perm in permissions2"
                :key="perm"
                size="small"
                type="warning"
                style="margin: 2px"
              >
                {{ perm }}
              </el-tag>
              <el-tag v-if="permissions2.length === 0" type="info" size="small">无权限</el-tag>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 2. v-hasPermi 指令演示 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>🎯 v-hasPermi 指令演示</span>
          <el-tag type="info" size="small">根据权限控制元素显示/隐藏</el-tag>
        </div>
      </template>

      <el-alert
        title="v-hasPermi 指令会根据当前用户的权限列表，自动移除没有权限的 DOM 元素。支持超级权限 *:*:*"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-row :gutter="16">
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">有权限时显示</p>
            <el-button v-hasPermi="['system:user:add']" type="primary" size="small">
              system:user:add
            </el-button>
            <el-tag
              v-if="hasPermission('system:user:add')"
              type="success"
              size="small"
              style="margin-left: 8px"
            >
              有权限
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px">
              无权限（已隐藏）
            </el-tag>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">无权限时隐藏</p>
            <el-button v-hasPermi="['system:xxx:delete']" type="danger" size="small">
              system:xxx:delete
            </el-button>
            <el-tag
              v-if="hasPermission('system:xxx:delete')"
              type="success"
              size="small"
              style="margin-left: 8px"
            >
              有权限
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px">
              无权限（已隐藏）
            </el-tag>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">超级权限 *:*:*</p>
            <el-button v-hasPermi="['*:*:*']" type="warning" size="small"> *:*:* </el-button>
            <el-tag
              v-if="hasPermission('*:*:*')"
              type="success"
              size="small"
              style="margin-left: 8px"
            >
              超级管理员
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px">
              非超级管理员
            </el-tag>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 3. v-hasRole 指令演示 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>👑 v-hasRole 指令演示</span>
          <el-tag type="info" size="small">根据角色控制元素显示/隐藏</el-tag>
        </div>
      </template>

      <el-alert
        title="v-hasRole 指令会根据当前用户的角色列表，自动移除没有角色的 DOM 元素。支持 admin 超级角色"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-row :gutter="16">
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">admin 角色</p>
            <el-button v-hasRole="['admin']" type="primary" size="small"> admin </el-button>
            <el-tag v-if="hasRole('admin')" type="success" size="small" style="margin-left: 8px">
              是 admin
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px"> 非 admin </el-tag>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">普通角色</p>
            <el-button v-hasRole="['common']" type="success" size="small"> common </el-button>
            <el-tag v-if="hasRole('common')" type="success" size="small" style="margin-left: 8px">
              有 common 角色
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px">
              无 common 角色
            </el-tag>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="demo-box">
            <p class="demo-box-title">多角色匹配</p>
            <el-button v-hasRole="['admin', 'common']" type="warning" size="small">
              admin / common
            </el-button>
            <el-tag
              v-if="hasRole('admin') || hasRole('common')"
              type="success"
              size="small"
              style="margin-left: 8px"
            >
              匹配任一角色
            </el-tag>
            <el-tag v-else type="danger" size="small" style="margin-left: 8px"> 不匹配 </el-tag>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 4. usePermission composable 演示 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>🛠️ usePermission Composable 演示</span>
          <el-tag type="info" size="small">在 script 中编程式检查权限</el-tag>
        </div>
      </template>

      <el-alert
        title="usePermission() 提供了 hasPermission、hasAnyPermission、hasAllPermissions、hasRole 四个方法，可在 script 中编程式使用"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-table :data="composableDemoData" border stripe>
        <el-table-column prop="method" label="方法" width="220" />
        <el-table-column prop="usage" label="调用示例" width="300" />
        <el-table-column prop="result" label="结果" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.result" type="success" size="small">true</el-tag>
            <el-tag v-else type="danger" size="small">false</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" />
      </el-table>
    </el-card>

    <!-- 5. 权限场景模拟 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>📋 权限场景模拟</span>
          <el-tag type="info" size="small">模拟真实业务场景中的权限控制</el-tag>
        </div>
      </template>

      <el-alert
        title="以下模拟了用户管理页面的权限控制场景，展示不同权限对应的界面元素"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <!-- 场景：用户管理 -->
      <div class="scene-section">
        <h4 class="scene-title">场景：用户管理</h4>

        <!-- 工具栏 -->
        <div class="scene-toolbar">
          <el-button v-hasPermi="['system:user:add']" type="primary" size="small" icon="Plus">
            新增用户
          </el-button>
          <el-button v-hasPermi="['system:user:edit']" type="success" size="small" icon="Edit">
            编辑
          </el-button>
          <el-button v-hasPermi="['system:user:remove']" type="danger" size="small" icon="Delete">
            删除
          </el-button>
          <el-button
            v-hasPermi="['system:user:export']"
            type="warning"
            size="small"
            icon="Download"
          >
            导出
          </el-button>
          <el-button v-hasPermi="['system:user:resetPwd']" size="small" icon="Key">
            重置密码
          </el-button>
        </div>

        <!-- 权限矩阵 -->
        <div class="permission-matrix">
          <p class="matrix-title">权限矩阵</p>
          <el-table :data="permissionMatrix" border stripe size="small">
            <el-table-column prop="permission" label="权限标识" width="200" />
            <el-table-column prop="description" label="说明" width="150" />
            <el-table-column prop="status" label="当前状态" width="120">
              <template #default="{ row }">
                <el-tag :type="hasPermission(row.permission) ? 'success' : 'danger'" size="small">
                  {{ hasPermission(row.permission) ? '有权限' : '无权限' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" />
          </el-table>
        </div>
      </div>
    </el-card>

    <!-- 6. 权限检查工具 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>🔍 权限检查工具</span>
          <el-tag type="info" size="small">手动输入权限标识进行验证</el-tag>
        </div>
      </template>

      <div class="check-tool">
        <el-input
          v-model="checkPermissionInput"
          placeholder="输入权限标识，如 system:user:add"
          style="width: 300px; margin-right: 12px"
          clearable
          @keyup.enter="checkPermission"
        />
        <el-button type="primary" @click="checkPermission">检查权限</el-button>
        <el-button @click="checkPermissionInput = ''">清空</el-button>

        <div v-if="checkResult !== null" class="check-result" style="margin-top: 12px">
          <el-alert
            :title="`权限 '${checkPermissionInput}' ${checkResult ? '✓ 有权限' : '✗ 无权限'}`"
            :type="checkResult ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </el-card>

    <!-- 7. 权限使用指南 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>📖 权限使用指南</span>
          <el-tag type="info" size="small">开发参考</el-tag>
        </div>
      </template>

      <el-collapse v-model="activeGuideSections">
        <el-collapse-item title="1. 指令方式：v-hasPermi" name="1">
          <div class="guide-content">
            <p>在模板中使用 v-hasPermi 指令控制元素显示：</p>
            <pre class="code-block"><code><!-- 单个权限 -->
<el-button v-hasPermi="['system:user:add']" type="primary">新增用户</el-button>

<!-- 多个权限（满足任一即可） -->
<el-button v-hasPermi="['system:user:add', 'system:user:edit']">操作</el-button>

<!-- 超级管理员权限 -->
<el-button v-hasPermi="['*:*:*']">超级管理操作</el-button></code></pre>
          </div>
        </el-collapse-item>

        <el-collapse-item title="2. 指令方式：v-hasRole" name="2">
          <div class="guide-content">
            <p>在模板中使用 v-hasRole 指令控制元素显示：</p>
            <pre class="code-block"><code><!-- 单个角色 -->
<el-button v-hasRole="['admin']" type="primary">管理员操作</el-button>

<!-- 多个角色（满足任一即可） -->
<el-button v-hasRole="['admin', 'common']">操作</el-button></code></pre>
          </div>
        </el-collapse-item>

        <el-collapse-item title="3. 编程式：usePermission" name="3">
          <div class="guide-content">
            <p>在 script 中使用 usePermission composable：</p>
            <pre
              class="code-block"
            ><code>import { usePermission } from '@/composables/usePermission'

const { hasPermission, hasAnyPermission, hasAllPermissions, hasRole } = usePermission()

// 检查单个权限
if (hasPermission('system:user:add')) {
  // 有权限
}

// 检查任一权限
if (hasAnyPermission(['system:user:add', 'system:user:edit'])) {
  // 有任一权限
}

// 检查所有权限
if (hasAllPermissions(['system:user:add', 'system:user:edit'])) {
  // 有所有权限
}

// 检查角色
if (hasRole('admin')) {
  // 是管理员
}</code></pre>
          </div>
        </el-collapse-item>

        <el-collapse-item title="4. Store Getter 方式" name="4">
          <div class="guide-content">
            <p>通过 userStore 的 getter 检查权限：</p>
            <pre class="code-block"><code>import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

// 检查单个权限
userStore.hasPermission('system:user:add')

// 检查任一权限
userStore.hasAnyPermission(['system:user:add', 'system:user:edit'])

// 检查所有权限
userStore.hasAllPermissions(['system:user:add', 'system:user:edit'])</code></pre>
          </div>
        </el-collapse-item>

        <el-collapse-item title="5. 权限标识命名规范" name="5">
          <div class="guide-content">
            <p>权限标识采用 <code>模块:功能:操作</code> 的三段式命名规范：</p>
            <el-table :data="namingExamples" border stripe size="small">
              <el-table-column prop="module" label="模块" width="120" />
              <el-table-column prop="feature" label="功能" width="120" />
              <el-table-column prop="action" label="操作" width="120" />
              <el-table-column prop="permission" label="完整标识" />
            </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PermissionDemo' })

import { ref, computed } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { usePermission } from '@/composables/usePermission'
import { authApi } from '@/api/auth'
const userStore = useUserStore()
const { hasPermission, hasAnyPermission, hasAllPermissions, hasRole } = usePermission()

// ==================== 状态 ====================
const checkPermissionInput = ref('')
const checkResult = ref<boolean | null>(null)
const activeGuideSections = ref(['1', '2', '3', '4', '5'])

// ==================== 计算属性 ====================
const permissions = computed(() => userStore.permissions)
const permissions2 = computed(() => authApi.getPermission().then((res) => res.data)) // 模拟从 API 获取权限列表
const roles = computed(() => userStore.roles)

// ==================== 数据 ====================
const composableDemoData = computed(() => [
  {
    method: 'hasPermission',
    usage: "hasPermission('system:user:add')",
    result: hasPermission('system:user:add'),
    description: '检查单个权限',
  },
  {
    method: 'hasPermission',
    usage: "hasPermission('*:*:*')",
    result: hasPermission('*:*:*'),
    description: '检查超级管理员权限',
  },
  {
    method: 'hasAnyPermission',
    usage: "hasAnyPermission(['system:user:add', 'system:user:edit'])",
    result: hasAnyPermission(['system:user:add', 'system:user:edit']),
    description: '检查是否有任一权限',
  },
  {
    method: 'hasAllPermissions',
    usage: "hasAllPermissions(['system:user:add', 'system:user:edit'])",
    result: hasAllPermissions(['system:user:add', 'system:user:edit']),
    description: '检查是否拥有所有权限',
  },
  {
    method: 'hasRole',
    usage: "hasRole('admin')",
    result: hasRole('admin'),
    description: '检查是否有 admin 角色',
  },
  {
    method: 'hasRole',
    usage: "hasRole('common')",
    result: hasRole('common'),
    description: '检查是否有 common 角色',
  },
])

const permissionMatrix = [
  { permission: 'system:user:add', description: '新增用户', note: '控制新增按钮显示' },
  { permission: 'system:user:edit', description: '修改用户', note: '控制编辑按钮显示' },
  { permission: 'system:user:remove', description: '删除用户', note: '控制删除按钮显示' },
  { permission: 'system:user:export', description: '导出用户', note: '控制导出按钮显示' },
  { permission: 'system:user:resetPwd', description: '重置密码', note: '控制重置密码按钮显示' },
  { permission: 'system:role:add', description: '新增角色', note: '角色管理新增' },
  { permission: 'system:menu:add', description: '新增菜单', note: '菜单管理新增' },
  { permission: 'system:dept:add', description: '新增部门', note: '部门管理新增' },
]

const namingExamples = [
  { module: 'system', feature: 'user', action: 'add', permission: 'system:user:add' },
  { module: 'system', feature: 'user', action: 'edit', permission: 'system:user:edit' },
  { module: 'system', feature: 'user', action: 'remove', permission: 'system:user:remove' },
  { module: 'system', feature: 'role', action: 'add', permission: 'system:role:add' },
  { module: 'system', feature: 'menu', action: 'add', permission: 'system:menu:add' },
  { module: 'system', feature: 'dept', action: 'list', permission: 'system:dept:list' },
  { module: 'inventory', feature: 'material', action: 'add', permission: 'inventory:material:add' },
  { module: 'sales', feature: 'order', action: 'view', permission: 'sales:order:view' },
]

// ==================== 方法 ====================
const checkPermission = () => {
  if (!checkPermissionInput.value.trim()) {
    checkResult.value = null
    return
  }
  checkResult.value = hasPermission(checkPermissionInput.value.trim())
}
</script>

<style scoped lang="scss">
.permission-demo-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;

  .section-card {
    margin-bottom: 20px;

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }

  .overview-content {
    .permissions-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 2px;
    }
  }

  .demo-box {
    padding: 16px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    text-align: center;
    min-height: 80px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;

    .demo-box-title {
      font-size: 13px;
      color: #909399;
      margin: 0;
    }
  }

  .scene-section {
    .scene-title {
      margin: 0 0 12px 0;
      font-size: 15px;
      color: #303133;
    }

    .scene-toolbar {
      margin-bottom: 16px;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 6px;
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .permission-matrix {
      .matrix-title {
        font-size: 13px;
        color: #909399;
        margin: 0 0 8px 0;
      }
    }
  }

  .check-tool {
    padding: 8px 0;
  }

  .guide-content {
    p {
      margin: 0 0 8px 0;
      color: #606266;
    }

    .code-block {
      background: #f5f7fa;
      border: 1px solid #e4e7ed;
      border-radius: 6px;
      padding: 16px;
      overflow-x: auto;
      margin: 0;

      code {
        font-family: 'Courier New', Courier, monospace;
        font-size: 13px;
        line-height: 1.6;
        color: #303133;
      }
    }
  }
}
</style>
