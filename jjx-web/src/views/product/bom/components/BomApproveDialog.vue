<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="1250px" :before-close="handleClose">
    <BomBasicInfo :detail="detail" />

    <el-alert
      v-if="blockingIssues.length"
      type="warning"
      :closable="false"
      show-icon
      class="blocking-alert"
      :title="`暂不能批准：${blockingIssues.length} 项待处理`"
    >
      <div v-for="(issue, idx) in blockingIssues.slice(0, 5)" :key="idx" class="blocking-line">
        · {{ issueLocation(issue) }} {{ issue.message }}
      </div>
      <div v-if="blockingIssues.length > 5" class="blocking-line">
        … 共 {{ blockingIssues.length }} 项
      </div>
    </el-alert>

    <BomItemReviewTable
      :items="bomDetailList"
      :bom-id="detail.bomId"
      :can-review="detail.approveStatus === BomStatusEnum.REVIEWING.value"
      :busy-item-id="busyItemId"
      :batch-busy="batchBusy"
      :batch-failures="batchFailures"
      :original-values="originalValues"
      @batch-approve="handleBatchApprove"
      @review="handleRowReview"
      @link="handleLinkMaterial"
      @create="handleCreateMaterial"
    />

    <el-card class="approve-form-card" shadow="never">
      <template #header>审核意见</template>
      <ApprovalOpinionForm
        ref="approveFormRef"
        v-model="approveForm"
        :require-approve-remark="false"
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
          :disabled="
            batchBusy ||
            busyItemId != null ||
            (approveForm.result === ApprovalResultEnum.APPROVE &&
              (!approveCheckReady || blockingIssues.length > 0))
          "
          @click="handleSubmit"
        >
          {{ approveForm.result === ApprovalResultEnum.APPROVE ? '批准BOM' : '驳回BOM' }}
        </el-button>
      </span>
    </template>

    <MaterialFormDialog
      v-model="materialFormVisible"
      :preset-data="materialPreset"
      @success="handleMaterialCreated"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productBomApi } from '@/api/product/bom'
import { loadBomOriginalValues } from '@/api/product/bomOriginalValues'
import type { BomOriginalValues } from '@/types/product/bom'
import type {
  EngineeringBom,
  EngineeringBomItem,
  BomCheckIssue,
  BomItemBatchReviewResult,
} from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import { ProductActions, BomStatusEnum } from '@/enums/product'
import { ApprovalResultEnum } from '@/enums/common/ApprovalEnum'
import ApprovalOpinionForm from '@/components/Approval/ApprovalOpinionForm.vue'
import type { ApprovalOpinion } from '@/components/Approval/types'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import BomBasicInfo from './BomBasicInfo.vue'
import BomItemReviewTable from './BomItemReviewTable.vue'

type RowAction = 'APPROVE' | 'REJECT' | 'CONFIRM_REMOVE'

// Props
const props = defineProps({
  bomId: {
    type: Number,
    required: false,
    default: undefined,
  },
  modelValue: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: 'BOM审核',
  },
})

// Emits
const emit = defineEmits(['update:modelValue', 'success', 'close'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const dialogTitle = computed(() => `${props.title} - ${detail.bomCode || ''}`)

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
const bomDetailList = ref<EngineeringBomItem[]>([])
const blockingIssues = ref<BomCheckIssue[]>([])
const batchBusy = ref(false)
const batchFailures = ref<BomItemBatchReviewResult['failed']>([])
const approveCheckReady = ref(false)
const busyItemId = ref<number | undefined>(undefined)

/**
 * 最近一次保存的「改动前值」（dev-20261022-021）。
 * 来源：该BOM最近成功保存日志的结构化itemChanges，按稳定itemId对应。
 * 只用于审核页对比展示，不落库。
 */
const originalValues = ref<BomOriginalValues>({})
let originalValuesRequest = 0

const loadOriginalValues = async () => {
  originalValues.value = {}
  const bomId = props.bomId
  const version = ++originalValuesRequest
  if (!bomId) return
  try {
    const values = await loadBomOriginalValues(bomId)
    if (version === originalValuesRequest && bomId === props.bomId) originalValues.value = values
  } catch (error) {
    console.warn('读取BOM变更流水失败（不影响审核）:', error)
  }
}

// 审核表单
const approveForm = ref<ApprovalOpinion>({ result: ApprovalResultEnum.APPROVE, remark: '' })
const approveFormRef = ref<InstanceType<typeof ApprovalOpinionForm>>()
const submitting = ref(false)

// 物料建档（审核员补档后立即关联当前行）
const materialFormVisible = ref(false)
const materialPreset = ref({ materialName: '', specification: '', unit: 'PCS' })
let linkingItemId: number | undefined

const issueLocation = (issue: BomCheckIssue) =>
  issue.materialCode || (issue.itemId != null ? `行#${issue.itemId}` : '主表')

// 加载BOM详情 + 批准条件体检
const loadBomDetail = async () => {
  if (!props.bomId) {
    resetData()
    return
  }
  try {
    const response = await productBomApi.getEngineeringBomInfo(props.bomId)
    Object.assign(detail, response.data)
    bomDetailList.value = response.data.items || []
  } catch (error) {
    console.error('加载BOM详情失败:', error)
    ElMessage.error('加载BOM详情失败')
    return
  }
  await refreshApproveCheck()
  await loadOriginalValues()
  approveForm.value.result = ApprovalResultEnum.APPROVE
  approveForm.value.remark = ''
}

const refreshApproveCheck = async () => {
  if (!props.bomId) return
  approveCheckReady.value = false
  try {
    const res = await productBomApi.checkBomForApprove(props.bomId)
    blockingIssues.value = res.data || []
    approveCheckReady.value = true
  } catch (error) {
    console.error('BOM批准条件体检失败:', error)
    blockingIssues.value = []
  }
}

const refreshAfterAction = async () => {
  if (!props.bomId) return
  const res = await productBomApi.getEngineeringBomInfo(props.bomId)
  Object.assign(detail, res.data)
  bomDetailList.value = res.data.items || []
  await refreshApproveCheck()
}

const resetData = () => {
  originalValuesRequest++
  originalValues.value = {}
  Object.assign(detail, createEmptyDetail())
  bomDetailList.value = []
  blockingIssues.value = []
  busyItemId.value = undefined
  batchFailures.value = []
  approveCheckReady.value = false
  approveForm.value.result = ApprovalResultEnum.APPROVE
  approveForm.value.remark = ''
  approveFormRef.value?.clearValidate()
}

const handleClose = () => {
  if (submitting.value || batchBusy.value) {
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

const handleBatchApprove = async (itemIds: number[]) => {
  if (!props.bomId || batchBusy.value || busyItemId.value != null || !itemIds.length) return
  batchBusy.value = true
  approveCheckReady.value = false
  batchFailures.value = []
  try {
    const res = await productBomApi.reviewBomItemsBatch({
      bomId: props.bomId,
      itemIds,
      action: 'APPROVE',
    })
    if (!res.data) throw new Error('批量审核未返回结果')
    batchFailures.value = res.data.failed
    ElMessage[res.data.failed.length ? 'warning' : 'success'](
      `批量通过 ${res.data.successIds.length} 行，失败 ${res.data.failed.length} 行`
    )
    await refreshAfterAction()
  } catch (error: any) {
    ElMessage.error(error?.message || '批量审核或刷新失败，请重新加载BOM审核弹窗')
  } finally {
    batchBusy.value = false
  }
}

// 行级审核：通过 / 驳回 / 确认移除
const handleRowReview = async (row: EngineeringBomItem, action: RowAction) => {
  if (!row.itemId) {
    ElMessage.warning('该行尚未保存，不能行级审核')
    return
  }
  let remark = ''
  try {
    if (action === 'REJECT') {
      const { value } = await ElMessageBox.prompt('请输入行驳回原因', '行审核驳回', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
      })
      remark = value.trim()
    } else if (action === 'CONFIRM_REMOVE') {
      await ElMessageBox.confirm(
        `确认移除材料「${row.materialName || row.materialCode || ''}」？批准 BOM 时将物理删除该行。`,
        '确认移除',
        { confirmButtonText: '确认移除', cancelButtonText: '取消', type: 'warning' }
      )
    } else if (!row.materialId) {
      ElMessage.warning('请先为该行关联物料，再审核通过')
      return
    }
  } catch {
    return
  }

  busyItemId.value = row.itemId
  try {
    await productBomApi.reviewBomItem({ itemId: row.itemId, action, remark })
    ElMessage.success(
      action === 'APPROVE'
        ? '该行已审核通过'
        : action === 'REJECT'
          ? '该行已驳回，BOM已退回工程修改'
          : '已确认移除'
    )
    await refreshAfterAction()
    if (action === 'REJECT') {
      // 行驳回会整单退回工程修改，关闭审核窗并刷新列表
      emit('success', {
        bomId: props.bomId,
        approveResult: ProductActions.REJECT,
        approveRemark: remark,
      })
      visible.value = false
    }
  } catch (error: any) {
    console.error('行级审核失败:', error)
    ElMessage.error(error?.message || '行级审核失败')
  } finally {
    busyItemId.value = undefined
  }
}

// 关联已有物料
const handleLinkMaterial = async (row: EngineeringBomItem, material: InventoryMaterial) => {
  if (!row.itemId || !material?.materialId) return
  busyItemId.value = row.itemId
  try {
    await productBomApi.reviewBomItem({
      itemId: row.itemId,
      action: 'LINK_MATERIAL',
      materialId: material.materialId,
    })
    ElMessage.success('已关联物料，请继续审核该行')
    await refreshAfterAction()
  } catch (error: any) {
    console.error('关联物料失败:', error)
    ElMessage.error(error?.message || '关联物料失败')
  } finally {
    busyItemId.value = undefined
  }
}

// 新建物料并关联（审核员需 inventory:material:add）
const handleCreateMaterial = (row: EngineeringBomItem, keyword: string) => {
  linkingItemId = row.itemId
  materialPreset.value = {
    materialName: keyword || row.materialName || '',
    specification: row.specification || '',
    unit: row.unit || 'PCS',
  }
  materialFormVisible.value = true
}

const handleMaterialCreated = async (material: InventoryMaterial) => {
  if (!material?.materialId || !linkingItemId) {
    ElMessage.warning('物料已建档，请在行内搜索后关联')
    return
  }
  const row = bomDetailList.value.find((it) => it.itemId === linkingItemId)
  if (row) await handleLinkMaterial(row, material)
  linkingItemId = undefined
}

// 整单审核提交
const handleSubmit = async () => {
  if (!approveFormRef.value) return

  try {
    await approveFormRef.value.validate()
  } catch {
    ElMessage.warning('请完善审核信息')
    return
  }

  if (approveForm.value.result === ApprovalResultEnum.APPROVE) {
    await refreshApproveCheck()
    if (!approveCheckReady.value) {
      ElMessage.error('批准条件体检失败，请重新加载审核弹窗后重试')
      return
    }
    if (blockingIssues.value.length) {
      ElMessageBox.alert(
        `仍有 ${blockingIssues.value.length} 项未满足批准条件：请先逐行审核通过、关联物料并确认待移除行。`,
        '暂不能批准',
        { confirmButtonText: '知道了', type: 'warning' }
      ).catch(() => {})
      return
    }
  }

  const confirmMessage =
    approveForm.value.result === ApprovalResultEnum.APPROVE
      ? '确定要批准此BOM吗？'
      : '确定要驳回此BOM吗？'

  try {
    await ElMessageBox.confirm(confirmMessage, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  submitting.value = true
  try {
    if (!props.bomId) {
      throw new Error('BOM ID不能为空')
    }

    if (approveForm.value.result === ApprovalResultEnum.APPROVE) {
      await productBomApi.approveEngineeringBom(props.bomId, approveForm.value.remark)
    } else {
      await productBomApi.rejectEngineeringBom(props.bomId, approveForm.value.remark)
    }

    ElMessage.success(
      approveForm.value.result === ApprovalResultEnum.APPROVE ? '审核通过成功' : '审核驳回成功'
    )

    emit('success', {
      bomId: props.bomId,
      approveResult:
        approveForm.value.result === ApprovalResultEnum.APPROVE
          ? ProductActions.APPROVE
          : ProductActions.REJECT,
      approveRemark: approveForm.value.remark,
    })

    visible.value = false
  } catch (error: any) {
    console.error('提交审核失败:', error)
    ElMessage.error(error?.message || '提交审核失败')
  } finally {
    submitting.value = false
  }
}

// 监听bomId和visible变化
watch(
  [() => props.bomId, () => visible.value],
  ([newBomId, newVisible], [oldBomId, oldVisible]) => {
    if (newVisible && newBomId) {
      if (newBomId !== oldBomId || !oldVisible) {
        loadBomDetail()
      }
    } else if (!newVisible) {
      resetData()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.approve-form-card {
  margin-top: 16px;
}
.blocking-alert {
  margin-bottom: 12px;
}
.blocking-line {
  font-size: 12px;
  line-height: 20px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
