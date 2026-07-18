<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="100px">
        <el-form-item label="工序编码" prop="stepCode">
          <el-input
            v-model="queryParams.stepCode"
            placeholder="请输入工序编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="工序名称" prop="stepName">
          <el-input
            v-model="queryParams.stepName"
            placeholder="请输入工序名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <div class="table-title">标准工序列表</div>
        <div class="table-actions">
          <el-button type="primary" icon="Plus" @click="handleAdd">新增</el-button>
          <el-button type="warning" icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="stepList" :row-key="(row) => row.stepId">
        <el-table-column label="工序编码" prop="stepCode" width="120" align="center" />
        <el-table-column label="工序名称" prop="stepName" min-width="150" />
        <el-table-column label="工序类型" prop="stepType" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.stepType === 'general' ? 'success' : 'warning'">
              {{ scope.row.stepType === 'general' ? '通用' : '专用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工序类别" prop="stepCategory" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getCategoryTagType(scope.row.stepCategory)">
              {{ getCategoryLabel(scope.row.stepCategory) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标准工时" width="180" align="center">
          <template #default="scope">
            <div>人工: {{ scope.row.standardLaborHours || 0 }}h</div>
            <div>机器: {{ scope.row.standardMachineHours || 0 }}h</div>
          </template>
        </el-table-column>
        <el-table-column label="设备类型" prop="equipmentType" width="120" />
        <el-table-column label="技能要求" prop="skillLevel" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getSkillLevelTagType(scope.row.skillLevel)">
              {{ scope.row.skillLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" prop="enabled" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'danger'">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="Edit" @click="handleUpdate(scope.row)"
              >编辑</el-button
            >
            <el-button type="primary" link icon="View" @click="handleView(scope.row)"
              >详情</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item label="工序编码" prop="stepCode">
          <el-input v-model="form.stepCode" placeholder="请输入工序编码" />
        </el-form-item>
        <el-form-item label="工序名称" prop="stepName">
          <el-input v-model="form.stepName" placeholder="请输入工序名称" />
        </el-form-item>
        <el-form-item label="工序类型" prop="stepType">
          <el-select v-model="form.stepType" placeholder="请选择工序类型" style="width: 100%">
            <el-option label="通用" value="general" />
            <el-option label="专用" value="special" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序类别" prop="stepCategory">
          <el-select v-model="form.stepCategory" placeholder="请选择工序类别" style="width: 100%">
            <el-option label="印刷" value="printing" />
            <el-option label="冲切" value="cutting" />
            <el-option label="贴合" value="laminating" />
            <el-option label="测试" value="testing" />
            <el-option label="装配" value="assembly" />
            <el-option label="包装" value="packing" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准人工工时(h)" prop="standardLaborHours">
          <el-input-number
            v-model="form.standardLaborHours"
            :min="0"
            :step="0.1"
            :precision="2"
            style="width: 100%"
            placeholder="请输入标准人工工时"
          />
        </el-form-item>
        <el-form-item label="标准机器工时(h)" prop="standardMachineHours">
          <el-input-number
            v-model="form.standardMachineHours"
            :min="0"
            :step="0.1"
            :precision="2"
            style="width: 100%"
            placeholder="请输入标准机器工时"
          />
        </el-form-item>
        <el-form-item label="设备类型" prop="equipmentType">
          <el-input v-model="form.equipmentType" placeholder="请输入设备类型" />
        </el-form-item>
        <el-form-item label="技能等级要求" prop="skillLevel">
          <el-select v-model="form.skillLevel" placeholder="请选择技能等级" style="width: 100%">
            <el-option label="初级" value="初级" />
            <el-option label="中级" value="中级" />
            <el-option label="高级" value="高级" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="工序详情" v-model="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工序编码">{{ detailForm.stepCode }}</el-descriptions-item>
        <el-descriptions-item label="工序名称">{{ detailForm.stepName }}</el-descriptions-item>
        <el-descriptions-item label="工序类型">
          <el-tag :type="detailForm.stepType === 'general' ? 'success' : 'warning'">
            {{ detailForm.stepType === 'general' ? '通用' : '专用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工序类别">
          <el-tag :type="getCategoryTagType(detailForm.stepCategory)">
            {{ getCategoryLabel(detailForm.stepCategory) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标准人工工时"
          >{{ detailForm.standardLaborHours || 0 }}h</el-descriptions-item
        >
        <el-descriptions-item label="标准机器工时"
          >{{ detailForm.standardMachineHours || 0 }}h</el-descriptions-item
        >
        <el-descriptions-item label="设备类型">{{ detailForm.equipmentType }}</el-descriptions-item>
        <el-descriptions-item label="技能等级要求">
          <el-tag :type="getSkillLevelTagType(detailForm.skillLevel)">
            {{ detailForm.skillLevel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="启用状态">
          <el-tag :type="detailForm.enabled ? 'success' : 'danger'">
            {{ detailForm.enabled ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StandardProcessStepList',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

interface StandardStep {
  stepId: number
  stepCode: string
  stepName: string
  stepType: string
  stepCategory: string
  standardLaborHours: number
  standardMachineHours: number
  equipmentType: string
  skillLevel: string
  enabled: boolean
  createTime: string
}

// 模拟数据
const mockData: StandardStep[] = [
  {
    stepId: 1,
    stepCode: 'PRINTING_01',
    stepName: '丝网印刷',
    stepType: 'general',
    stepCategory: 'printing',
    standardLaborHours: 0.5,
    standardMachineHours: 1.0,
    equipmentType: '丝网印刷机',
    skillLevel: '中级',
    enabled: true,
    createTime: '2024-01-15 10:30:00',
  },
  {
    stepId: 2,
    stepCode: 'CUTTING_01',
    stepName: '模切成型',
    stepType: 'general',
    stepCategory: 'cutting',
    standardLaborHours: 0.3,
    standardMachineHours: 0.8,
    equipmentType: '模切机',
    skillLevel: '初级',
    enabled: true,
    createTime: '2024-01-16 14:20:00',
  },
  {
    stepId: 3,
    stepCode: 'LAMINATING_01',
    stepName: '热压贴合',
    stepType: 'general',
    stepCategory: 'laminating',
    standardLaborHours: 0.4,
    standardMachineHours: 1.2,
    equipmentType: '热压贴合机',
    skillLevel: '中级',
    enabled: true,
    createTime: '2024-01-17 09:15:00',
  },
]

const stepList = ref<StandardStep[]>([])
const loading = ref(false)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const formRef = ref()
const queryForm = ref()

const queryParams = reactive({
  stepCode: '',
  stepName: '',
})

const form = reactive({
  stepCode: '',
  stepName: '',
  stepType: 'general',
  stepCategory: 'printing',
  standardLaborHours: 0,
  standardMachineHours: 0,
  equipmentType: '',
  skillLevel: '初级',
  enabled: true,
})

const detailForm = reactive<Record<string, any>>({})

// 初始化数据
onMounted(() => {
  getList()
})

// 获取列表
function getList() {
  loading.value = true
  setTimeout(() => {
    stepList.value = mockData
    loading.value = false
  }, 500)
}

// 搜索
function handleQuery() {
  getList()
}

// 重置搜索
function resetQuery() {
  queryParams.stepCode = ''
  queryParams.stepName = ''
  getList()
}

// 新增
function handleAdd() {
  title.value = '新增标准工序'
  form.stepCode = ''
  form.stepName = ''
  form.stepType = 'general'
  form.stepCategory = 'printing'
  form.standardLaborHours = 0
  form.standardMachineHours = 0
  form.equipmentType = ''
  form.skillLevel = '初级'
  form.enabled = true
  open.value = true
}

// 编辑
function handleUpdate(row: StandardStep) {
  title.value = '编辑标准工序'
  Object.assign(form, row)
  open.value = true
}

// 查看详情
function handleView(row: StandardStep) {
  Object.assign(detailForm, row)
  detailOpen.value = true
}

// 提交表单
function submitForm() {
  if (!form.stepCode || !form.stepName) {
    ElMessage.error('请填写必填项')
    return
  }

  ElMessage.success('保存成功')
  open.value = false
  getList()
}

// 取消
function cancel() {
  open.value = false
}

// 工具函数
function parseTime(time: string) {
  if (!time) return ''
  return time
}

function getCategoryTagType(
  category: string
): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    printing: 'primary',
    cutting: 'success',
    laminating: 'warning',
    testing: 'info',
    assembly: 'danger',
    packing: undefined,
  }
  return map[category] ?? undefined
}

function getCategoryLabel(category: string): string {
  const map: Record<string, string> = {
    printing: '印刷',
    cutting: '冲切',
    laminating: '贴合',
    testing: '测试',
    assembly: '装配',
    packing: '包装',
  }
  return map[category] || category
}

function getSkillLevelTagType(
  level: string
): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    初级: 'info',
    中级: 'warning',
    高级: 'danger',
  }
  return map[level] ?? undefined
}
</script>

<style scoped>
.app-container {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.table-card {
  margin-top: 20px;
}
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.table-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
