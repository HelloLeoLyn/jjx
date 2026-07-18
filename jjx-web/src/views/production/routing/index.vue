<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="100px">
        <el-form-item label="工艺路线编码" prop="routingCode">
          <el-input
            v-model="queryParams.routingCode"
            placeholder="请输入工艺路线编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="工艺路线名称" prop="routingName">
          <el-input
            v-model="queryParams.routingName"
            placeholder="请输入工艺路线名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="适用产品" prop="productName">
          <el-input
            v-model="queryParams.productName"
            placeholder="请输入适用产品"
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
        <div class="table-title">工艺路线列表</div>
        <div class="table-actions">
          <el-button type="primary" icon="Plus" @click="handleAdd">新增工艺路线</el-button>
          <el-button type="warning" icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="routingList" :row-key="(row: any) => row.routingId">
        <el-table-column label="工艺路线编码" prop="routingCode" width="150" align="center" />
        <el-table-column label="工艺路线名称" prop="routingName" min-width="150" />
        <el-table-column label="适用产品" prop="productName" width="120" />
        <el-table-column label="工序数量" prop="stepCount" width="80" align="center" />
        <el-table-column label="标准总工时(h)" prop="totalHours" width="120" align="center">
          <template #default="scope">
            {{ scope.row.totalHours?.toFixed(2) || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column label="启用状态" prop="enabled" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'danger'">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160" align="center">
          <template #default="scope">
            {{ parseTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link icon="Edit" @click="handleUpdate(scope.row)"
              >编辑</el-button
            >
            <el-button type="primary" link icon="View" @click="handleView(scope.row)"
              >详情</el-button
            >
            <el-button type="success" link icon="Setting" @click="handleDesign(scope.row)"
              >工序设计</el-button
            >
            <el-button type="info" link icon="CopyDocument" @click="handleCopy(scope.row)"
              >复制</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item label="工艺路线编码" prop="routingCode">
          <el-input v-model="form.routingCode" placeholder="请输入工艺路线编码" />
        </el-form-item>
        <el-form-item label="工艺路线名称" prop="routingName">
          <el-input v-model="form.routingName" placeholder="请输入工艺路线名称" />
        </el-form-item>
        <el-form-item label="适用产品" prop="productName">
          <el-input v-model="form.productName" placeholder="请输入适用产品" />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本号" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="工艺路线详情" v-model="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工艺路线编码">{{
          detailForm.routingCode
        }}</el-descriptions-item>
        <el-descriptions-item label="工艺路线名称">{{
          detailForm.routingName
        }}</el-descriptions-item>
        <el-descriptions-item label="适用产品">{{ detailForm.productName }}</el-descriptions-item>
        <el-descriptions-item label="版本号">{{ detailForm.version }}</el-descriptions-item>
        <el-descriptions-item label="工序数量">{{
          detailForm.stepCount || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="标准总工时"
          >{{ detailForm.totalHours?.toFixed(2) || '0.00' }}h</el-descriptions-item
        >
        <el-descriptions-item label="启用状态">
          <el-tag :type="detailForm.enabled ? 'success' : 'danger'">
            {{ detailForm.enabled ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(detailForm.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          parseTime(detailForm.updateTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detailForm.remark || '无'
        }}</el-descriptions-item>
      </el-descriptions>

      <div class="step-list" v-if="detailForm.steps && detailForm.steps.length > 0">
        <div class="step-title">工序列表</div>
        <el-table :data="detailForm.steps" size="small">
          <el-table-column label="序号" prop="sequence" width="60" align="center" />
          <el-table-column label="工序编码" prop="stepCode" width="120" />
          <el-table-column label="工序名称" prop="stepName" width="150" />
          <el-table-column label="工序类型" prop="stepType" width="100">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.stepType === 'general' ? 'success' : 'warning'">
                {{ scope.row.stepType === 'general' ? '通用' : '专用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标准工时(h)" width="120">
            <template #default="scope">
              <div>人工: {{ scope.row.standardLaborHours?.toFixed(2) || '0.00' }}</div>
              <div>机器: {{ scope.row.standardMachineHours?.toFixed(2) || '0.00' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="设备类型" prop="equipmentType" width="120" />
        </el-table>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="工序设计" v-model="designOpen" width="1000px" append-to-body>
      <div class="design-container">
        <div class="design-header">
          <div class="design-title">工艺路线: {{ designForm.routingName }}</div>
          <div class="design-actions">
            <el-button type="primary" icon="Plus" @click="addStep">添加工序</el-button>
            <el-button type="success" @click="saveDesign">保存设计</el-button>
          </div>
        </div>

        <div class="step-list-container">
          <draggable v-model="designSteps" item-key="tempId" handle=".drag-handle" @end="onDragEnd">
            <template #item="{ element, index }">
              <div class="step-item">
                <div class="step-header">
                  <div class="step-info">
                    <el-icon class="drag-handle"><Rank /></el-icon>
                    <span class="step-sequence">工序 {{ index + 1 }}</span>
                    <el-button type="danger" link icon="Delete" @click="removeStep(index)" />
                  </div>
                </div>
                <div class="step-content">
                  <el-form :model="element" label-width="100px" size="small">
                    <el-row>
                      <el-col :span="12">
                        <el-form-item label="工序">
                          <el-select
                            v-model="element.stepId"
                            placeholder="请选择工序"
                            style="width: 100%"
                          >
                            <el-option label="丝网印刷" value="1" />
                            <el-option label="模切成型" value="2" />
                            <el-option label="热压贴合" value="3" />
                            <el-option label="测试检验" value="4" />
                            <el-option label="包装入库" value="5" />
                          </el-select>
                        </el-form-item>
                      </el-col>
                      <el-col :span="12">
                        <el-form-item label="工序顺序">
                          <el-input-number
                            v-model="element.sequence"
                            :min="1"
                            :max="designSteps.length"
                            style="width: 100%"
                          />
                        </el-form-item>
                      </el-col>
                    </el-row>
                    <el-form-item label="标准工时(h)">
                      <el-input-number
                        v-model="element.standardLaborHours"
                        :min="0"
                        :step="0.1"
                        :precision="2"
                        placeholder="人工工时"
                        style="width: 48%; margin-right: 4%"
                      />
                      <el-input-number
                        v-model="element.standardMachineHours"
                        :min="0"
                        :step="0.1"
                        :precision="2"
                        placeholder="机器工时"
                        style="width: 48%"
                      />
                    </el-form-item>
                    <el-form-item label="备注">
                      <el-input
                        v-model="element.remark"
                        type="textarea"
                        :rows="2"
                        placeholder="请输入备注"
                      />
                    </el-form-item>
                  </el-form>
                </div>
              </div>
            </template>
          </draggable>
        </div>

        <div class="design-summary">
          <div class="summary-item">总工序数: {{ designSteps.length }}</div>
          <div class="summary-item">总人工工时: {{ totalLaborHours.toFixed(2) }}h</div>
          <div class="summary-item">总机器工时: {{ totalMachineHours.toFixed(2) }}h</div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="saveDesign">保存设计</el-button>
          <el-button @click="designOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProcessRoutingList',
})

import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'

interface RoutingStep {
  stepId: number | string
  stepCode?: string
  stepName?: string
  stepType?: string
  standardLaborHours: number
  standardMachineHours: number
  equipmentType?: string
  sequence: number
  remark?: string
  tempId?: string
}

interface RoutingItem {
  routingId: number
  routingCode: string
  routingName: string
  productName: string
  version: string
  stepCount: number
  totalHours: number
  enabled: boolean
  createTime: string
  updateTime: string
  remark: string
  steps: RoutingStep[]
}

// 模拟数据
const mockData: RoutingItem[] = [
  {
    routingId: 1,
    routingCode: 'ROUTING001',
    routingName: '标准薄膜开关工艺路线',
    productName: '薄膜开关A型',
    version: '1.0',
    stepCount: 5,
    totalHours: 3.5,
    enabled: true,
    createTime: '2024-01-10 10:30:00',
    updateTime: '2024-01-15 14:20:00',
    remark: '标准生产工艺路线',
    steps: [
      {
        stepId: 1,
        stepCode: 'PRINTING_01',
        stepName: '丝网印刷',
        stepType: 'general',
        standardLaborHours: 0.5,
        standardMachineHours: 1.0,
        equipmentType: '丝网印刷机',
        sequence: 1,
      },
      {
        stepId: 2,
        stepCode: 'CUTTING_01',
        stepName: '模切成型',
        stepType: 'general',
        standardLaborHours: 0.3,
        standardMachineHours: 0.8,
        equipmentType: '模切机',
        sequence: 2,
      },
      {
        stepId: 3,
        stepCode: 'LAMINATING_01',
        stepName: '热压贴合',
        stepType: 'general',
        standardLaborHours: 0.4,
        standardMachineHours: 1.2,
        equipmentType: '热压贴合机',
        sequence: 3,
      },
      {
        stepId: 4,
        stepCode: 'TESTING_01',
        stepName: '测试检验',
        stepType: 'general',
        standardLaborHours: 0.2,
        standardMachineHours: 0.5,
        equipmentType: '测试仪',
        sequence: 4,
      },
      {
        stepId: 5,
        stepCode: 'PACKING_01',
        stepName: '包装入库',
        stepType: 'general',
        standardLaborHours: 0.1,
        standardMachineHours: 0.0,
        equipmentType: '包装机',
        sequence: 5,
      },
    ],
  },
  {
    routingId: 2,
    routingCode: 'ROUTING002',
    routingName: '定制薄膜开关工艺路线',
    productName: '薄膜开关B型',
    version: '1.0',
    stepCount: 4,
    totalHours: 2.8,
    enabled: true,
    createTime: '2024-01-12 09:15:00',
    updateTime: '2024-01-18 11:30:00',
    remark: '定制生产工艺路线',
    steps: [
      {
        stepId: 1,
        stepCode: 'PRINTING_01',
        stepName: '丝网印刷',
        stepType: 'general',
        standardLaborHours: 0.5,
        standardMachineHours: 1.0,
        equipmentType: '丝网印刷机',
        sequence: 1,
      },
      {
        stepId: 2,
        stepCode: 'CUTTING_01',
        stepName: '模切成型',
        stepType: 'general',
        standardLaborHours: 0.3,
        standardMachineHours: 0.8,
        equipmentType: '模切机',
        sequence: 2,
      },
      {
        stepId: 3,
        stepCode: 'TESTING_01',
        stepName: '测试检验',
        stepType: 'general',
        standardLaborHours: 0.2,
        standardMachineHours: 0.5,
        equipmentType: '测试仪',
        sequence: 3,
      },
      {
        stepId: 5,
        stepCode: 'PACKING_01',
        stepName: '包装入库',
        stepType: 'general',
        standardLaborHours: 0.1,
        standardMachineHours: 0.0,
        equipmentType: '包装机',
        sequence: 4,
      },
    ],
  },
]

const routingList = ref<RoutingItem[]>([])
const loading = ref(false)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const designOpen = ref(false)
const formRef = ref()
const queryForm = ref()

const queryParams = reactive({
  routingCode: '',
  routingName: '',
  productName: '',
})

const form = reactive({
  routingCode: '',
  routingName: '',
  productName: '',
  version: '1.0',
  enabled: true,
  remark: '',
})

const detailForm = reactive<Record<string, any>>({})
const designForm = reactive<Record<string, any>>({})
const designSteps = ref<RoutingStep[]>([])

// 计算总工时
const totalLaborHours = computed(() => {
  return designSteps.value.reduce((sum, step) => sum + (step.standardLaborHours || 0), 0)
})

const totalMachineHours = computed(() => {
  return designSteps.value.reduce((sum, step) => sum + (step.standardMachineHours || 0), 0)
})

// 初始化数据
onMounted(() => {
  getList()
})

// 获取列表
function getList() {
  loading.value = true
  setTimeout(() => {
    routingList.value = mockData
    loading.value = false
  }, 500)
}

// 搜索
function handleQuery() {
  getList()
}

// 重置搜索
function resetQuery() {
  queryParams.routingCode = ''
  queryParams.routingName = ''
  queryParams.productName = ''
  getList()
}

// 新增
function handleAdd() {
  title.value = '新增工艺路线'
  form.routingCode = ''
  form.routingName = ''
  form.productName = ''
  form.version = '1.0'
  form.enabled = true
  form.remark = ''
  open.value = true
}

// 编辑
function handleUpdate(row: any) {
  title.value = '编辑工艺路线'
  Object.assign(form, row)
  open.value = true
}

// 查看详情
function handleView(row: any) {
  Object.assign(detailForm, row)
  detailOpen.value = true
}

// 工序设计
function handleDesign(row: any) {
  Object.assign(designForm, row)
  designSteps.value =
    row.steps?.map((step: any, index: number) => ({
      ...step,
      tempId: `step_${index}_${Date.now()}`,
      sequence: index + 1,
    })) || []
  designOpen.value = true
}

// 复制
function handleCopy(row: any) {
  ElMessageBox.confirm('确认复制该工艺路线？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    ElMessage.success('复制成功')
    getList()
  })
}

// 提交表单
function submitForm() {
  if (!form.routingCode || !form.routingName) {
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

// 添加工序
function addStep() {
  designSteps.value.push({
    tempId: `step_${designSteps.value.length}_${Date.now()}`,
    stepId: '',
    sequence: designSteps.value.length + 1,
    standardLaborHours: 0,
    standardMachineHours: 0,
    remark: '',
  })
}

// 删除工序
function removeStep(index: number) {
  designSteps.value.splice(index, 1)
  // 重新排序
  designSteps.value.forEach((step, idx) => {
    step.sequence = idx + 1
  })
}

// 拖拽结束
function onDragEnd() {
  // 重新排序
  designSteps.value.forEach((step, idx) => {
    step.sequence = idx + 1
  })
}

// 保存设计
function saveDesign() {
  if (designSteps.value.length === 0) {
    ElMessage.error('请至少添加一个工序')
    return
  }

  const hasEmptyStep = designSteps.value.some((step) => !step.stepId)
  if (hasEmptyStep) {
    ElMessage.error('请为所有工序选择工序')
    return
  }

  ElMessage.success('设计保存成功')
  designOpen.value = false
  getList()
}

// 工具函数
function parseTime(time: string) {
  if (!time) return ''
  return time
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
.step-list {
  margin-top: 20px;
}
.step-title {
  font-size: 14px;
  font-weight: bold;
  margin-bottom: 10px;
}
.design-container {
  padding: 10px;
}
.design-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.design-title {
  font-size: 16px;
  font-weight: bold;
}
.step-item {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 10px;
  padding: 10px;
  background: #fafafa;
}
.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}
.step-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.drag-handle {
  cursor: move;
  color: #909399;
}
.step-sequence {
  font-weight: bold;
}
.design-summary {
  display: flex;
  justify-content: space-around;
  margin-top: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}
.summary-item {
  font-size: 14px;
  font-weight: bold;
}
</style>
