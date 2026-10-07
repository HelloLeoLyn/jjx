<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="1200px" :before-close="handleClose">
    <BomBasicInfo :detail="detail" />
    <BomDetailTable :items="bomDetailList" />
    <el-card class="approve-form-card" shadow="never">
      <template #header>审核意见</template>
      <ApprovalOpinionForm
        ref="approveFormRef"
        v-model="approveForm"
        :min-length="4"
        :max-length="500"
        :quick-opinions="['审核通过']"
        :disabled="submitting"
      />
    </el-card>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          v-hasPermi="['engineering:bom:approve']"
          :loading="submitting"
          @click="handleSubmit"
        >
          提交审核
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productBomApi } from '@/api/product/bom'
import type { EngineeringBom, EngineeringBomItem } from '@/types/product/bom'
import { ProductActions, BomStatusEnum } from '@/enums/product'
import { ApprovalResultEnum } from '@/enums/common/ApprovalEnum'
import ApprovalOpinionForm from '@/components/Approval/ApprovalOpinionForm.vue'
import type { ApprovalOpinion } from '@/components/Approval/types'
import BomBasicInfo from './BomBasicInfo.vue'
import BomDetailTable from './BomDetailTable.vue'

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
const createEmptyDetail = (): EngineeringBom => ({
  bomId: 0,
  bomCode: '',
  bomName: '',
  productId: 0,
  productCode: '',
  productName: '',
  bomVersion: '',
  approveStatus: BomStatusEnum.DRAFT.value,
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

const detail = reactive<EngineeringBom>(createEmptyDetail())

// BOM明细数据
const bomDetailList = ref<EngineeringBomItem[]>([])

// 审核表单
const approveForm = ref<ApprovalOpinion>({ result: ApprovalResultEnum.APPROVE, remark: '' })
const approveFormRef = ref<InstanceType<typeof ApprovalOpinionForm>>()

// 提交状态
const submitting = ref(false)

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
    approveForm.value.result = ApprovalResultEnum.APPROVE
    approveForm.value.remark = ''
  } catch (error) {
    console.error('加载BOM详情失败:', error)
    ElMessage.error('加载BOM详情失败')
  }
}

// 重置数据
const resetData = () => {
  Object.assign(detail, createEmptyDetail())
  bomDetailList.value = []
  approveForm.value.result = ApprovalResultEnum.APPROVE
  approveForm.value.remark = ''
  approveFormRef.value?.clearValidate()
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
    approveForm.value.result === ApprovalResultEnum.APPROVE
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
    if (approveForm.value.result === ApprovalResultEnum.APPROVE) {
      // 调用审核通过API
      await productBomApi.approveEngineeringBom(props.bomId, approveForm.value.remark)
    } else {
      // 调用审核驳回API
      await productBomApi.rejectEngineeringBom(props.bomId, approveForm.value.remark)
    }

    ElMessage.success(
      approveForm.value.result === ApprovalResultEnum.APPROVE ? '审核通过成功' : '审核驳回成功'
    )

    // 触发成功事件
    emit('success', {
      bomId: props.bomId,
      approveResult:
        approveForm.value.result === ApprovalResultEnum.APPROVE
          ? ProductActions.APPROVE
          : ProductActions.REJECT,
      approveRemark: approveForm.value.remark,
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
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
