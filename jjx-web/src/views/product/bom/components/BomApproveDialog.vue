<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="70%" :before-close="handleClose">
    <!-- BOM基本信息 -->
    <el-card class="bom-info-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>BOM基本信息</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="BOM编码">{{ detail.bomCode }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="BOM版本">{{ detail.bomVersion }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="BomStatusEnum.getTagProps(detail.approveStatus)?.type">
            {{ BomStatusEnum.getLabel(detail.approveStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生效日期">
          {{ parseDate(detail.effectiveDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="失效日期">
          {{ parseDate(detail.expiryDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(detail.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批注" :span="2">{{
          detail.approveRemark || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detail.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- BOM明细 -->
    <el-card class="bom-detail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>BOM明细</span>
          <span class="total-count">共 {{ bomDetailList.length }} 项</span>
        </div>
      </template>
      <el-table :data="bomDetailList" border style="width: 100%" height="300">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="180" />
        <el-table-column label="规格型号" prop="specification" width="120" />
        <el-table-column label="单位" prop="unit" width="80" />
        <el-table-column label="数量" prop="quantity" width="80" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column label="损耗率(%)" prop="lossRate" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.lossRate) }}
          </template>
        </el-table-column>
        <el-table-column label="来源类型" prop="sourceType" width="100">
          <template #default="scope">
            <el-tag :type="SourceTypeEnum.getTagProps(scope.row.sourceType)?.type" size="small">
              {{ SourceTypeEnum.getLabel(scope.row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="备注" prop="remark" />
      </el-table>
    </el-card>

    <!-- 审核表单 -->
    <el-card class="approve-form-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>审核意见</span>
        </div>
      </template>
      <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
        <el-form-item label="审核结果" prop="approveResult">
          <el-radio-group v-model="approveForm.approveResult">
            <el-radio :label="ProductActions.APPROVE" border>
              <el-icon><CircleCheck /></el-icon>
              通过
            </el-radio>
            <el-radio :label="ProductActions.REJECT" border>
              <el-icon><CircleClose /></el-icon>
              驳回
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="审核意见模板"
          v-if="approveForm.approveResult === ProductActions.APPROVE"
          ><el-button type="primary" plain link @click="approveForm.approveRemark = '审核通过'"
            >审核通过</el-button
          >
        </el-form-item>
        <el-form-item label="审核意见" prop="approveRemark" :closable="false">
          <el-input
            v-model="approveForm.approveRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入审核意见"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </el-card>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit"> 提交审核 </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { productBomApi } from '@/api/product/bom'
import { parseDate, parseTime } from '@/utils/format'
import type { EngineeringBom, EngineeringBomItem } from '@/types/product/bom'
import { ProductEnum, ProductActions, BomStatusEnum, SourceTypeEnum } from '@/enums/product'

// Props
const props = defineProps({
  // BOM ID
  bomId: {
    type: Number,
    required: false,
    default: undefined,
  },
  // 对话框显示控制
  modelValue: {
    type: Boolean,
    default: false,
  },
  // 对话框标题（可选）
  title: {
    type: String,
    default: 'BOM审核',
  },
})

// Emits
const emit = defineEmits(['update:modelValue', 'success', 'close'])

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const dialogTitle = computed(() => {
  return `${props.title} - ${detail.bomCode || ''}`
})

// BOM详情数据
const detail = reactive<EngineeringBom>({
  bomId: 0,
  bomCode: '',
  bomName: '',
  productId: 0,
  productCode: '',
  productName: '',
  bomVersion: '',
  approveStatus: 0,
  isCurrent: false,
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  approveRemark: '',
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

// BOM明细数据
const bomDetailList = ref<EngineeringBomItem[]>([])

// 审核表单
const approveForm = reactive({
  approveResult: ProductActions.APPROVE, // 默认通过
  approveRemark: '',
})

// 表单验证规则
const approveRules: FormRules = {
  approveResult: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  approveRemark: [
    { required: true, message: '请输入审核意见', trigger: 'blur' },
    { min: 4, message: '审核意见至少4个字符', trigger: 'blur' },
  ],
}

// 表单引用
const approveFormRef = ref<FormInstance>()

// 提交状态
const submitting = ref(false)

// 数字格式化
const formatNumber = (value: number | string) => {
  if (value === undefined || value === null) return '0'
  const num = typeof value === 'string' ? parseFloat(value) : value
  return Number.isNaN(num) ? '0' : num.toFixed(2)
}

// 加载BOM详情数据
const loadBomDetail = async () => {
  if (!props.bomId) {
    resetData()
    return
  }

  try {
    const response = await productBomApi.getEngineeringBomInfo(props.bomId)
    Object.assign(detail, response.data)
    bomDetailList.value = response.data.items || []

    // 重置审核表单
    approveForm.approveResult = ProductActions.APPROVE
    approveForm.approveRemark = ''
  } catch (error) {
    console.error('加载BOM详情失败:', error)
    ElMessage.error('加载BOM详情失败')
  }
}

// 重置数据
const resetData = () => {
  Object.assign(detail, {
    bomId: 0,
    bomCode: '',
    bomName: '',
    productId: 0,
    productCode: '',
    productName: '',
    bomVersion: '',
    bomStatus: 0,
    approveStatus: '',
    isCurrent: false,
    effectiveDate: '',
    expiryDate: '',
    remark: '',
    createTime: '',
    updateTime: '',
    createBy: '',
    updateBy: '',
  })
  bomDetailList.value = []
  approveForm.approveResult = ProductActions.APPROVE
  approveForm.approveRemark = ''
}

// 处理对话框关闭
const handleClose = () => {
  if (submitting.value) {
    ElMessage.warning('正在提交，请稍候...')
    return
  }

  ElMessageBox.confirm('确定要关闭审核窗口吗？未保存的审核意见将会丢失。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      visible.value = false
      emit('close')
    })
    .catch(() => {})
}

// 提交审核
const handleSubmit = async () => {
  if (!approveFormRef.value) return

  // 表单验证
  try {
    await approveFormRef.value.validate()
  } catch (error) {
    ElMessage.warning('请完善审核信息')
    return
  }

  // 确认提交
  const confirmMessage =
    approveForm.approveResult === ProductActions.APPROVE
      ? '确定要通过此BOM审核吗？'
      : '确定要驳回此BOM吗？'

  try {
    await ElMessageBox.confirm(confirmMessage, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }

  submitting.value = true
  try {
    if (!props.bomId) {
      throw new Error('BOM ID不能为空')
    }

    // 根据审核结果调用不同的API
    if (approveForm.approveResult === ProductActions.APPROVE) {
      // 调用审核通过API
      await productBomApi.approveEngineeringBom(props.bomId, approveForm.approveRemark)
    } else {
      // 调用审核驳回API
      await productBomApi.rejectEngineeringBom(props.bomId, approveForm.approveRemark)
    }

    ElMessage.success(
      approveForm.approveResult === ProductActions.APPROVE ? '审核通过成功' : '审核驳回成功'
    )

    // 触发成功事件
    emit('success', {
      bomId: props.bomId,
      approveResult: approveForm.approveResult,
      approveRemark: approveForm.approveRemark,
    })

    // 关闭对话框
    visible.value = false
  } catch (error) {
    console.error('提交审核失败:', error)
    ElMessage.error('提交审核失败')
  } finally {
    submitting.value = false
  }
}

// 监听bomId和visible变化
watch(
  [() => props.bomId, () => visible.value],
  ([newBomId, newVisible], [oldBomId, oldVisible]) => {
    // 只有当对话框打开且有有效的bomId时才加载数据
    if (newVisible && newBomId) {
      // 避免重复加载：只有当bomId变化或对话框从关闭变为打开时才加载
      if (newBomId !== oldBomId || !oldVisible) {
        loadBomDetail()
      }
    } else if (!newVisible) {
      // 对话框关闭时重置数据
      resetData()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.bom-info-card,
.bom-detail-card,
.approve-form-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-count {
  font-size: 14px;
  color: #909399;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-radio.is-bordered) {
  padding: 10px 20px;
}

:deep(.el-radio.is-bordered .el-icon) {
  margin-right: 5px;
}
</style>
