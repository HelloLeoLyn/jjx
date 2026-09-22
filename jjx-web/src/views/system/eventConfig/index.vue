<template>
  <div class="event-config-container">
    <el-card class="box-card">
      <!-- 搜索区域 -->
      <SearchForm
        v-model="queryParams"
        :fields="uiConfig.searchOptions"
        @search="handleQuery"
        @reset="resetQuery"
      />

      <!-- 操作按钮区域 -->
      <Toolbar
        :buttons="uiConfig.toolbarOptions"
        :selected-count="ids.length"
        :show-batch-bar="true"
        @click="handleToolbarClick"
        @refresh="getList"
      >
        <template #batch-actions>
          <el-button
            type="danger"
            size="small"
            @click="() => handleDelete()"
            v-hasPermi="['system:eventConfig:delete']"
          >
            批量删除
          </el-button>
        </template>
      </Toolbar>

      <!-- 表格区域 -->
      <DataTable
        :showIndex="false"
        v-model="queryParams"
        :data="eventList"
        :loading="loading"
        :total="total"
        :columns="tableOptions"
        :show-action="true"
        :action-width="250"
        @selection-change="handleSelectionChange"
        @page-change="handleCurrentChange"
        @size-change="handleSizeChange"
      >
        <!-- 事件类型 -->
        <template #eventType="{ row }">
          <el-tag v-if="row.eventType === 'both'" type="warning" size="small">通知+任务</el-tag>
          <el-tag v-else-if="row.eventType === 'task'" type="primary" size="small">任务</el-tag>
          <el-tag v-else type="success" size="small">通知</el-tag>
        </template>

        <!-- 目标角色 -->
        <template #targetRole="{ row }">
          <template v-if="parseTargetRole(row.targetRole).length">
            <el-tag
              v-for="rid in parseTargetRole(row.targetRole)"
              :key="rid"
              size="small"
              style="margin-right: 4px"
            >
              {{ roleName(rid) }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>

        <!-- 排除触发者 -->
        <template #excludeTrigger="{ row }">
          <el-tag :type="row.excludeTrigger === 1 ? 'danger' : 'info'" size="small">
            {{ row.excludeTrigger === 1 ? '是' : '否' }}
          </el-tag>
        </template>

        <!-- 状态 -->
        <template #isEnabled="{ row }">
          <el-tag :type="row.isEnabled === 1 ? 'success' : 'danger'" size="small">
            {{ row.isEnabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- 创建时间 -->
        <template #createTime="{ row }">
          <span>{{ parseTime(row.createTime) }}</span>
        </template>

        <!-- 操作列 -->
        <template #action="{ row }">
          <el-button
            link
            type="primary"
            @click="handleUpdate(row)"
            v-hasPermi="['system:eventConfig:edit']"
          >
            修改
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(row)"
            v-hasPermi="['system:eventConfig:delete']"
          >
            删除
          </el-button>
        </template>
      </DataTable>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="680px"
      append-to-body
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="事件编码" prop="eventCode">
              <el-input
                v-model="form.eventCode"
                placeholder="如: inquiry.converted"
                :disabled="!!form.eventId"
                @change="loadMetadata"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="事件名称" prop="eventName">
              <el-input v-model="form.eventName" placeholder="事件名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="业务模块" prop="bizModule">
              <el-select v-model="form.bizModule" placeholder="选择业务模块" style="width:100%">
                <el-option label="销售" value="sales" />
                <el-option label="采购" value="purchase" />
                <el-option label="生产" value="production" />
                <el-option label="产品工程" value="product" />
                <el-option label="库存" value="inventory" />
                <el-option label="品质" value="quality" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="eventType">
              <el-select v-model="form.eventType" placeholder="请选择" style="width:100%">
                <el-option label="通知" value="notification" />
                <el-option label="任务" value="task" />
                <el-option label="通知+任务" value="both" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="看板模块" prop="kanbanModule">
              <el-select v-model="form.kanbanModule" placeholder="任务进入哪个看板" style="width:100%">
                <el-option label="业务" value="biz" />
                <el-option label="生产" value="prod" />
                <el-option label="开发任务" value="dev" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="任务优先级" style="width:100%">
                <el-option label="紧急" value="urgent" />
                <el-option label="高" value="high" />
                <el-option label="普通" value="normal" />
                <el-option label="低" value="low" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="目标角色" prop="targetRoleList">
              <el-select
                v-model="form.targetRoleList"
                multiple
                placeholder="请选择接收通知/任务的角色"
                style="width:100%"
              >
                <el-option
                  v-for="r in roleOptions"
                  :key="r.roleId"
                  :label="r.roleName"
                  :value="r.roleId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="isEnabled">
              <el-select v-model="form.isEnabled" placeholder="请选择" style="width:100%">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="禁用" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="支持 {eventCode} {bizId} 等占位符"
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            placeholder="通知/任务内容，支持占位符"
          />
        </el-form-item>

        <el-form-item label="可用变量">
          <div class="variable-panel">
            <div v-if="!variables.length" class="variable-empty">该事件暂未登记专用变量，可使用通用变量。</div>
            <div v-for="item in variables" :key="item.key" class="variable-row">
              <el-tag size="small">{{ `{${item.key}}` }}</el-tag>
              <span>{{ item.description }}</span>
              <el-button link type="primary" @click="insertVariable('title', item.key)">插入标题</el-button>
              <el-button link type="primary" @click="insertVariable('content', item.key)">插入内容</el-button>
            </div>
            <el-alert
              v-if="unknownVariables.length"
              type="warning"
              :closable="false"
              :title="`未登记变量：${unknownVariables.join('、')}`"
            />
          </div>
        </el-form-item>

        <el-form-item label="试渲染">
          <div class="preview-panel">
            <div><b>标题：</b>{{ previewTitle || '-' }}</div>
            <div><b>内容：</b>{{ previewContent || '-' }}</div>
          </div>
        </el-form-item>

        <el-form-item label="最近发送">
          <div v-if="latestNotification" class="preview-panel">
            <div><b>标题：</b>{{ latestNotification.title || '-' }}</div>
            <div><b>内容：</b>{{ latestNotification.content || '-' }}</div>
            <div><b>收件人：</b>{{ latestNotification.receiverName || '-' }}　{{ latestNotification.sendTime || '' }}</div>
          </div>
          <span v-else class="variable-empty">暂无实际发送记录</span>
        </el-form-item>

        <el-form-item label="办结关闭事件" prop="closeSourceEvents">
          <el-input
            v-model="form.closeSourceEvents"
            type="textarea"
            :rows="2"
            placeholder="办结时关闭的任务来源事件，逗号分隔；示例：order.submitted,order.review_started"
          />
        </el-form-item>

        <el-form-item label="排除触发者">
          <el-switch
            v-model="form.excludeTrigger"
            :active-value="1"
            :inactive-value="0"
          />
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
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Toolbar, DataTable, SearchForm } from '@/components/common-ui/index'
import { eventConfigApi } from '@/api/system/event-config'
import { roleApi } from '@/api/system/role'
import type { SysEventConfig } from '@/types/system'
import * as uiConfig from './index'
import { assignExisting } from '@/utils/object'
import { parseTime } from '@/utils/format'
import type { TableOptions } from '@/components/common-ui/type'

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const total = ref(0)
const eventList = ref<SysEventConfig[]>([])
const ids = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增事件配置')
const variables = ref<Array<{ key: string; description: string; example: string }>>([])
const latestNotification = ref<{ title?: string; content?: string; receiverName?: string; sendTime?: string }>()

const tableOptions: TableOptions[] = uiConfig.tableOptions.flatMap(column =>
  column.prop === 'title'
    ? [
        {
          prop: 'closeSourceEvents',
          label: '办结关闭事件',
          minWidth: 220,
          formatter: (row: SysEventConfig) => row.closeSourceEvents || '-',
        },
        column,
      ]
    : [column],
)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  bizModule: '',
  eventCode: '',
  eventName: '',
  eventType: '',
  isEnabled: undefined as number | undefined,
})

const form = reactive({
  eventId: undefined as number | undefined,
  eventCode: '',
  eventName: '',
  bizModule: '',
  eventType: 'notification',
  kanbanModule: 'biz',
  priority: 'normal',
  isEnabled: 1,
  targetRole: '',
  targetRoleList: [] as number[],
  title: '',
  content: '',
  closeSourceEvents: '',
  excludeTrigger: 0,
})

// 角色选项
const roleOptions = ref<{ roleId: number; roleName: string }[]>([])

// 加载角色列表
function loadRoles() {
  roleApi.list({ pageNum: 1, pageSize: 100 }).then((res: any) => {
    roleOptions.value = (res.data || []).map((r: any) => ({ roleId: r.roleId, roleName: r.roleName }))
  })
}

/** 解析 targetRole JSON → 数组 */
function parseTargetRole(str: string | null | undefined): number[] {
  if (!str) return []
  try {
    const arr = JSON.parse(str)
    return Array.isArray(arr) ? arr.map(Number).filter(n => !Number.isNaN(n)) : []
  } catch {
    return []
  }
}

/** 角色ID → 角色名 */
function roleName(roleId: number): string {
  return roleOptions.value.find(r => r.roleId === roleId)?.roleName ?? `角色${roleId}`
}

const rules = {
  eventCode: [{ required: true, message: '事件编码不能为空', trigger: 'blur' }],
  eventName: [{ required: true, message: '事件名称不能为空', trigger: 'blur' }],
  eventType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

// 查询列表
function getList() {
  loading.value = true
  eventConfigApi.page(queryParams).then((res: any) => {
    if (res.code === 200) {
      eventList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  }).finally(() => {
    loading.value = false
  })
}

// 搜索/重置
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() {
  queryParams.bizModule = ''
  queryParams.eventCode = ''
  queryParams.eventName = ''
  queryParams.eventType = ''
  queryParams.isEnabled = undefined
  queryParams.pageNum = 1
  getList()
}

// 分页
function handleCurrentChange(val: number) { queryParams.pageNum = val; getList() }
function handleSizeChange(val: number) { queryParams.pageSize = val; handleQuery() }

// 选择
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.eventId)
}

// 工具栏点击
function handleToolbarClick(key: string) {
  if (key === 'add') handleAdd()
}

// 新增
function handleAdd() {
  dialogTitle.value = '新增事件配置'
  assignExisting(form, { eventId: undefined, eventCode: '', eventName: '', bizModule: '', eventType: 'notification', kanbanModule: 'biz', priority: 'normal', isEnabled: 1, targetRole: '', targetRoleList: [], title: '', content: '', closeSourceEvents: '', excludeTrigger: 0 })
  variables.value = []
  latestNotification.value = undefined
  dialogVisible.value = true
}

// 编辑
function handleUpdate(row: SysEventConfig) {
  dialogTitle.value = '修改事件配置'
  assignExisting(form, row as any)
  form.targetRoleList = parseTargetRole(row.targetRole as string)
  loadMetadata()
  dialogVisible.value = true
}

async function loadMetadata() {
  if (!form.eventCode) {
    variables.value = []
    latestNotification.value = undefined
    return
  }
  const { data } = await eventConfigApi.metadata(form.eventCode)
  variables.value = data?.variables || []
  latestNotification.value = data?.latest
}

function insertVariable(field: 'title' | 'content', key: string) {
  form[field] = `${form[field] || ''}{${key}}`
}

const placeholderKeys = computed(() => {
  const keys = new Set<string>()
  const text = `${form.title || ''}\n${form.content || ''}`
  for (const match of text.matchAll(/\$?\{([^}]+)\}/g)) {
    match[1].split('|').map((key) => key.trim()).filter(Boolean).forEach((key) => keys.add(key))
  }
  return [...keys]
})
const unknownVariables = computed(() => {
  const allowed = new Set(variables.value.map((item) => item.key))
  return placeholderKeys.value.filter((key) => !allowed.has(key))
})
const examplePayload = computed(() =>
  Object.fromEntries(variables.value.map((item) => [item.key, item.example]))
)
function renderPreview(template: string) {
  return (template || '').replace(/\$?\{([^}]+)\}/g, (_, expression: string) => {
    for (const candidate of expression.split('|')) {
      const value = examplePayload.value[candidate.trim()]
      if (value != null) return value
    }
    return ''
  })
}
const previewTitle = computed(() => renderPreview(form.title))
const previewContent = computed(() => renderPreview(form.content))

// 删除
function handleDelete(row?: SysEventConfig) {
  const deleteIds = row ? [row.eventId!] : ids.value
  if (!deleteIds.length) {
    ElMessage.warning('请选择要删除的记录')
    return
  }
  ElMessageBox.confirm(`确认删除 ${deleteIds.length} 条事件配置？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    eventConfigApi.remove(deleteIds).then((res: any) => {
      if (res.code === 200) {
        ElMessage.success('删除成功')
        getList()
      }
    })
  }).catch(() => {})
}

// 提交
function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    if (unknownVariables.value.length) {
      try {
        await ElMessageBox.confirm(
          `模板包含未登记变量：${unknownVariables.value.join('、')}。保存后可能渲染为空，仍要保存吗？`,
          '变量校验提醒',
          { type: 'warning' }
        )
      } catch {
        return
      }
    }
    submitLoading.value = true
    const { targetRoleList, ...rest } = form
    const payload = { ...rest, targetRole: JSON.stringify(form.targetRoleList) }
    const api = form.eventId ? eventConfigApi.update(payload) : eventConfigApi.add(payload)
    api.then((res: any) => {
      if (res.code === 200) {
        ElMessage.success(form.eventId ? '修改成功' : '新增成功')
        dialogVisible.value = false
        getList()
      }
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

onMounted(() => {
  loadRoles()
  getList()
})
</script>

<style scoped>
.variable-panel,
.preview-panel {
  width: 100%;
}
.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.variable-row > span {
  flex: 1;
  color: #606266;
}
.variable-empty {
  color: #909399;
}
.preview-panel {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
  line-height: 1.7;
}
</style>
