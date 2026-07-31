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
            v-hasPermi="['system:eventConfig:remove']"
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
        :columns="uiConfig.tableOptions"
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
          <span>{{ row.targetRole || '-' }}</span>
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
            v-hasPermi="['system:eventConfig:remove']"
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
              <el-input v-model="form.bizModule" placeholder="如: sales" />
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
                <el-option label="办公室任务" value="office" />
                <el-option label="紧急任务" value="emergency" />
                <el-option label="生产工单" value="production" />
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
            <el-form-item label="目标角色" prop="targetRole">
              <el-input v-model="form.targetRole" placeholder='如: [7, 8]' />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Toolbar, DataTable, SearchForm } from '@/components/common-ui/index'
import { eventConfigApi } from '@/api/system/event-config'
import type { SysEventConfig } from '@/types/system'
import * as uiConfig from './index'
import { assignExisting } from '@/utils/object'
import { parseTime } from '@/utils/format'

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const total = ref(0)
const eventList = ref<SysEventConfig[]>([])
const ids = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增事件配置')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
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
  kanbanModule: 'office',
  priority: 'normal',
  isEnabled: 1,
  targetRole: '',
  title: '',
  content: '',
  excludeTrigger: 0,
})

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
  assignExisting(form, { eventId: undefined, eventCode: '', eventName: '', bizModule: '', eventType: 'notification', kanbanModule: 'office', priority: 'normal', isEnabled: 1, targetRole: '', title: '', content: '', excludeTrigger: 0 })
  dialogVisible.value = true
}

// 编辑
function handleUpdate(row: SysEventConfig) {
  dialogTitle.value = '修改事件配置'
  assignExisting(form, row as any)
  dialogVisible.value = true
}

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
  formRef.value?.validate((valid) => {
    if (!valid) return
    submitLoading.value = true
    const api = form.eventId ? eventConfigApi.update(form) : eventConfigApi.add(form)
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
  getList()
})
</script>
