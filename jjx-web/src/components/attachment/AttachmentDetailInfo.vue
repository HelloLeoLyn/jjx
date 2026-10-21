<template>
  <el-descriptions :column="1" border size="small" style="margin-bottom: 12px">
    <el-descriptions-item label="原文件名">
      {{ row?.fileName || `附件 #${row?.attachmentId ?? ''}` }}
    </el-descriptions-item>
    <el-descriptions-item label="展示文件名">
      {{ row?.displayName || '（未设置，用原文件名）' }}
    </el-descriptions-item>
    <el-descriptions-item label="来源类型">{{ sourceTypeLabel }}</el-descriptions-item>
    <el-descriptions-item label="来源单号">{{ row?.sourceNo || '-' }}</el-descriptions-item>
    <el-descriptions-item label="产品编码">{{ row?.productCode || '-' }}</el-descriptions-item>
    <el-descriptions-item label="资料类型">
      {{ categoryLabel }}<span v-if="row?.documentType"> / {{ secondLabel }}</span>
    </el-descriptions-item>
    <el-descriptions-item label="大小">{{ formatSize(row?.fileSize) }}</el-descriptions-item>
    <el-descriptions-item label="上传人 / 时间">
      {{ uploadBy || '未记录' }} / {{ formatTime(uploadTime) }}
    </el-descriptions-item>
    <template v-if="referenceId">
      <el-descriptions-item label="加入本单">
        <span v-if="loading">正在查询…</span>
        <span v-else-if="error">暂未加载</span>
        <span v-else-if="actionDetail?.joinedAt">
          {{ actionDetail.joinedBy || '操作人未记录' }} / {{ formatTime(actionDetail.joinedAt) }}
        </span>
        <span v-else>未查到正式加入记录</span>
      </el-descriptions-item>
      <el-descriptions-item label="关联业务动作">
        <span v-if="loading">正在查询…</span>
        <div v-else-if="error" role="alert">
          {{ error }} <button type="button" class="action-retry" @click="loadActions">重试</button>
        </div>
        <template v-else>
          <ul v-if="actionDetail?.businessActions.length" class="action-list">
            <li v-for="action in actionDetail.businessActions" :key="action.eventKey">
              <strong>{{ action.actionName }}</strong>
              <div>{{ formatTime(action.actionTime) }} · {{ action.operatorName || '操作人未记录' }}</div>
            </li>
          </ul>
          <span v-else>未查到关联业务动作记录</span>
          <details v-if="actionDetail?.referenceEvents.length" class="reference-events">
            <summary>查看附件变更流水</summary>
            <ul class="action-list">
              <li v-for="action in actionDetail.referenceEvents" :key="action.eventKey">
                {{ action.actionName }} · {{ formatTime(action.actionTime) }} · {{ action.operatorName || '操作人未记录' }}
              </li>
            </ul>
          </details>
        </template>
      </el-descriptions-item>
    </template>
    <el-descriptions-item v-if="statusLabel" label="状态">{{ statusLabel }}</el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { attachmentRefApi, type AttachmentDocumentTypeItem, type AttachmentRefActions } from '@/api/system/attachmentRef'
import {
  AttachmentDocumentStatusEnum,
  AttachmentSourceTypeEnum,
  canonicalAttachmentSource,
} from '@/enums/system/AttachmentEnum'
import { formatSize, formatTime } from './attachmentFormat'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'

/**
 * 附件详情信息块（只读）——可写表与只读表的唯一实现。
 * 行数据只需带上 fileName/displayName/sourceType/sourceNo/productCode/documentType/fileSize/createBy/createTime/status。
 */
const props = withDefaults(defineProps<{
  row: Record<string, any> | null
  /** 资料类型字典（父组件已加载时传入，避免重复请求）；不传则显示类型编码 */
  documentTypes?: AttachmentDocumentTypeItem[]
  sourceType?: string
  /** 调用方持有原始引用行时显式提供；永远不从通用 row.id 推断。 */
  refId?: number | null
  visible?: boolean
}>(), { visible: true })

const referenceId = computed(() => {
  const id = props.refId ?? props.row?.refId
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : null
})
const actionDetail = ref<AttachmentRefActions | null>(null)
const loading = ref(false)
const error = ref('')
let requestId = 0
let disposed = false
// 引用行的 createBy/createTime 是引用创建者/时间，不能拿来冒充原文件上传信息。
const uploadBy = computed(() => referenceId.value ? actionDetail.value?.uploadedBy : props.row?.createBy)
const uploadTime = computed(() => referenceId.value ? actionDetail.value?.uploadedAt : props.row?.createTime)

async function loadActions() {
  const current = ++requestId
  const id = referenceId.value
  actionDetail.value = null
  error.value = ''
  loading.value = false
  if (!props.visible || !id || disposed) return
  loading.value = true
  try {
    const response = await attachmentRefApi.actions(id)
    if (disposed || current !== requestId) return
    if (response.data?.refId !== id) throw new Error('附件引用返回不匹配')
    actionDetail.value = response.data
  } catch {
    if (!disposed && current === requestId) error.value = '附件动作加载失败或当前无查看权限。'
  } finally {
    if (!disposed && current === requestId) loading.value = false
  }
}

watch(() => [props.visible, referenceId.value, props.row?.attachmentId] as const, loadActions, { immediate: true })
onBeforeUnmount(() => { disposed = true; requestId++ })

const { categoryLabelOf, secondLabelOf } = useAttachmentDocTypes(
  computed(() => props.documentTypes ?? []),
  () => props.sourceType ?? ''
)
const sourceTypeLabel = computed(() =>
  props.row?.sourceType
    ? AttachmentSourceTypeEnum.getLabel(canonicalAttachmentSource(props.row.sourceType))
    : '-'
)
const categoryLabel = computed(() =>
  props.row?.documentType ? categoryLabelOf(props.row.documentType) : '-'
)
const secondLabel = computed(() =>
  props.row?.documentType ? secondLabelOf(props.row.documentType) : '-'
)
const statusLabel = computed(() =>
  props.row?.status ? AttachmentDocumentStatusEnum.getLabel(props.row.status) : ''
)
</script>

<style scoped>
.action-list { margin: 0; padding-left: 18px; }
.action-list li + li { margin-top: 8px; }
.reference-events { margin-top: 8px; }
.reference-events summary { cursor: pointer; color: var(--el-color-primary); }
.action-retry { border: 0; background: none; color: var(--el-color-primary); cursor: pointer; }
</style>
