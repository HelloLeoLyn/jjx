<template>
  <div class="work-project-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" :inline="true" label-width="80px">
        <el-form-item label="项目名称" prop="processName">
          <el-input
            v-model="queryParams.processName"
            placeholder="请输入项目名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="项目结构" prop="processCategory">
          <el-select
            v-model="queryParams.processCategory"
            placeholder="请选择项目结构"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in categoryOptions"
              :key="item.itemValue"
              :label="item.label"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="作业类型" prop="processType">
          <el-select
            v-model="queryParams.processType"
            placeholder="请选择作业类型"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item.itemValue"
              :label="item.label"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <div class="operation-bar">
        <el-button type="primary" icon="Plus" @click="handleAdd">新增作业项目</el-button>
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="icon" label="图标" width="90" align="center">
          <template #default="scope">
            <SvgIcon v-if="scope.row.icon" :name="scope.row.icon" :size="24" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="processCode" label="项目编码" width="130" />
        <el-table-column prop="processName" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="processCategory" label="项目结构" width="100" align="center">
          <template #default="scope">
            <el-tag size="small">{{ getCategoryLabel(scope.row.processCategory) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processType" label="作业类型" width="120" align="center">
          <template #default="scope">
            <el-tag size="small">{{ getTypeLabel(scope.row.processType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" icon="Delete" v-hasPermi="['engineering:standardProcess:edit']" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑作业项目' : '新增作业项目'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-form-item label="项目编码" prop="processCode">
          <el-input
            v-model="formData.processCode"
            :disabled="isEdit"
            placeholder="请输入项目编码，如 SP-101"
          />
        </el-form-item>
        <el-form-item label="项目名称" prop="processName">
          <el-input v-model="formData.processName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="项目结构" prop="processCategory">
              <el-select
                v-model="formData.processCategory"
                placeholder="请选择项目结构"
                style="width: 100%"
              >
                <el-option
                  v-for="item in categoryOptions"
                  :key="item.itemValue"
                  :label="item.label"
                  :value="item.itemValue"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作业类型" prop="processType">
              <el-select
                v-model="formData.processType"
                placeholder="请选择作业类型"
                style="width: 100%"
              >
                <el-option
                  v-for="item in typeOptions"
                  :key="item.itemValue"
                  :label="item.label"
                  :value="item.itemValue"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图标" prop="icon">
          <el-popover placement="bottom" trigger="click" width="400">
            <template #reference>
              <el-input
                v-model="formData.icon"
                placeholder="点击选择图标"
                readonly
                style="cursor: pointer"
              >
                <template #prefix>
                  <SvgIcon v-if="formData.icon" :name="formData.icon" :size="20" />
                </template>
              </el-input>
            </template>
            <JJXIcon v-model="formData.icon" />
          </el-popover>
        </el-form-item>
        <el-form-item label="备注" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（工序说明）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'WorkProject',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import JJXIcon from '@/components/JJXIcon/index.vue'
import type {
  StandardProcessQueryParams,
  StandardProcessItem,
} from '@/types/product/standardProcess'

// 项目结构/作业类型选项（字典维护）
const { options: typeOptions } = useDict('process_type')
const { options: categoryOptions } = useDict('process_category')

function getTypeLabel(value: string): string {
  return typeOptions.value.find((i) => i.itemValue === value)?.label || value || '未知'
}

function getCategoryLabel(value: string): string {
  return categoryOptions.value.find((i) => i.itemValue === value)?.label || value || '未知'
}

// ==================== 查询参数 ====================
const queryParams = reactive<StandardProcessQueryParams>({
  pageNum: 1,
  pageSize: 10,
  processName: undefined,
  processType: undefined,
  processCategory: undefined,
  orderByColumn: 'displayOrder',
  isAsc: 'asc',
})

// ==================== 表格数据 ====================
const tableData = ref<StandardProcessItem[]>([])
const total = ref(0)
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const response = await standardProcessApi.pageQuery(queryParams)
    const result = response.data
    if (result) {
      tableData.value = result.records || []
      total.value = result.total || 0
    }
  } catch (error) {
    console.error('加载作业项目列表失败:', error)
    ElMessage.error('加载作业项目列表失败')
  } finally {
    loading.value = false
  }
}

// ==================== 搜索 ====================
const handleQuery = () => {
  queryParams.pageNum = 1
  loadData()
}

const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    processName: undefined,
    processType: undefined,
    processCategory: undefined,
    orderByColumn: 'displayOrder',
    isAsc: 'asc',
  })
  loadData()
}

// ==================== 分页 ====================
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  loadData()
}

const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  loadData()
}

// ==================== 新增/编辑 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  processId: undefined as number | undefined,
  processCode: '',
  processName: '',
  processType: '',
  processCategory: '',
  icon: '',
  description: '',
})

const formData = reactive(defaultForm())

const rules = reactive<FormRules>({
  processCode: [{ required: true, message: '项目编码不能为空', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  processType: [{ required: true, message: '请选择作业类型', trigger: 'change' }],
  processCategory: [{ required: true, message: '请选择项目结构', trigger: 'change' }],
})

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  dialogVisible.value = true
}

const handleEdit = (row: StandardProcessItem) => {
  isEdit.value = true
  Object.assign(formData, {
    processId: row.processId,
    processCode: row.processCode,
    processName: row.processName,
    processType: row.processType,
    processCategory: row.processCategory,
    icon: row.icon,
    description: row.description,
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitLoading.value = true
    const payload = {
      processCode: formData.processCode,
      processName: formData.processName,
      processType: formData.processType,
      processCategory: formData.processCategory,
      icon: formData.icon,
      description: formData.description,
      standardLaborHours: 0,
      standardMachineHours: 0,
      processParamTemplate: '',
      skillRequirement: '',
      equipmentType: '',
      qualityStandard: '',
      hasIndex: 0,
      isEnabled: 1,
      displayOrder: 0,
    }
    if (isEdit.value) {
      await standardProcessApi.update(formData.processId as number, payload)
      ElMessage.success('编辑成功')
    } else {
      await standardProcessApi.create(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    if (error === 'cancel') return
    console.error('提交作业项目失败:', error)
    ElMessage.error('提交失败，请重试')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 删除 ====================
const handleDelete = async (row: StandardProcessItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除作业项目 "${row.processName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await standardProcessApi.remove(row.processId)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error === 'cancel') return
    console.error('删除作业项目失败:', error)
  }
}

onMounted(loadData)
</script>

<style scoped>
.work-project-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 12px;
}

.operation-card {
  margin-bottom: 12px;
}
</style>
