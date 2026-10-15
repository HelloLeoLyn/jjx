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
      {{ row?.createBy || '-' }} / {{ row?.createTime || '-' }}
    </el-descriptions-item>
    <el-descriptions-item v-if="statusLabel" label="状态">{{ statusLabel }}</el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import {
  AttachmentDocumentStatusEnum,
  AttachmentSourceTypeEnum,
  canonicalAttachmentSource,
} from '@/enums/system/AttachmentEnum'
import { formatSize } from './attachmentFormat'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'

/**
 * 附件详情信息块（只读）——可写表与只读表的唯一实现。
 * 行数据只需带上 fileName/displayName/sourceType/sourceNo/productCode/documentType/fileSize/createBy/createTime/status。
 */
const props = defineProps<{
  row: Record<string, any> | null
  /** 资料类型字典（父组件已加载时传入，避免重复请求）；不传则显示类型编码 */
  documentTypes?: AttachmentDocumentTypeItem[]
  sourceType?: string
}>()

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
