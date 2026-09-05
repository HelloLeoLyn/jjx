<template>
  <el-dialog
    :model-value="modelValue"
    title="生产发料 · 领料单"
    width="920px"
    append-to-body
    class="pick-issue-dialog"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="issue-body">
      <div v-if="detail" class="document-preview">
        <div class="doc-title">领 料 单</div>

        <div class="doc-info">
          <div class="info-item"><span class="info-label">单据号</span>{{ detail.outboundNo }}</div>
          <div class="info-item"><span class="info-label">类型</span>生产领料</div>
          <div class="info-item"><span class="info-label">仓库</span>{{ detail.warehouseName || '-' }}</div>
          <div class="info-item"><span class="info-label">来源工单</span>{{ detail.sourceNo || '-' }}</div>
          <div class="info-item">
            <span class="info-label">单据状态</span>
            <el-tag :type="statusTag(detail.status)" size="small">{{ detail.statusName || '-' }}</el-tag>
          </div>
          <div class="info-item"><span class="info-label">总数量</span>{{ fmtNum(detail.totalQuantity) }}</div>
          <div class="info-item"><span class="info-label">创建人</span>{{ detail.createBy || '-' }}</div>
          <div class="info-item"><span class="info-label">创建时间</span>{{ detail.createTime || '-' }}</div>
          <div class="info-item info-item-full"><span class="info-label">备注</span>{{ detail.remark || '-' }}</div>
        </div>

        <table class="doc-items">
          <thead>
            <tr>
              <th style="width: 6%">序号</th>
              <th style="width: 14%">物料编码</th>
              <th>物料名称</th>
              <th style="width: 9%">单位</th>
              <th style="width: 13%">批次</th>
              <th style="width: 13%">库位</th>
              <th style="width: 12%">数量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in items" :key="item.itemId || index">
              <td class="col-center">{{ index + 1 }}</td>
              <td>{{ item.materialCode || '-' }}</td>
              <td>{{ item.materialName || '-' }}</td>
              <td class="col-center">{{ item.unit || '-' }}</td>
              <td>{{ item.batchNo || '-' }}</td>
              <td>{{ locationName(item) }}</td>
              <td class="col-right">{{ fmtNum(item.quantity) }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="7" class="col-center">无明细</td>
            </tr>
          </tbody>
        </table>

        <div class="doc-total-row">
          <span>物料种类：{{ items.length }} 项</span>
          <span>总数量：{{ fmtNum(detail.totalQuantity) }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="!detail" @click="handlePrint">打印领料单</el-button>
        <div>
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!detail" @click="handleConfirm">
            确认发料
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { outboundApi } from '@/api/inventory/outbound'
import { useUserStore } from '@/store/modules/user'
import type { OutboundItemVO, OutboundVO } from '@/types/inventory/outbound'

type TagType = 'success' | 'warning' | 'info' | 'danger' | undefined

const props = defineProps<{
  modelValue: boolean
  row: OutboundVO | null
  statusTag: (status: number) => TagType
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const loading = ref(false)
const submitting = ref(false)
const detail = ref<OutboundVO | null>(null)
const items = computed(() => detail.value?.items || [])

function fmtNum(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isNaN(number) ? String(value) : number.toLocaleString('zh-CN')
}

function locationName(item: OutboundItemVO): string {
  return (item as OutboundItemVO & { locationName?: string }).locationName || item.locationCode || '-'
}

async function loadDetail() {
  if (!props.row?.outboundId) return
  loading.value = true
  detail.value = null
  try {
    const response = await outboundApi.getById(String(props.row.outboundId))
    detail.value = response.data || null
  } catch (error: any) {
    ElMessage.error(error?.message || '获取领料单详情失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) loadDetail()
    else detail.value = null
  },
  { immediate: true },
)

function handlePrint() {
  if (detail.value?.outboundId) {
    window.open(`/print/outbound/${detail.value.outboundId}`, '_blank')
  }
}

async function handleConfirm() {
  if (!detail.value?.outboundId) return
  submitting.value = true
  try {
    const store = useUserStore()
    const user = { id: String(store.userId || 1), name: store.nickName || '当前用户' }
    await outboundApi.confirm(String(detail.value.outboundId), user.id, user.name)
    ElMessage.success('确认发料成功')
    emit('update:modelValue', false)
    emit('success')
  } catch (error: any) {
    ElMessage.error(error?.message || '确认发料失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.issue-body {
  min-height: 260px;
  max-height: 68vh;
  overflow-y: auto;
  padding: 4px;
}

.document-preview {
  padding: 24px 28px;
  color: #303133;
  background: #fff;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.doc-title {
  margin: 0 0 14px;
  padding-bottom: 8px;
  color: #000;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
  letter-spacing: 8px;
  border-bottom: 2px solid #2b5aa7;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px 24px;
  margin-bottom: 14px;
  font-size: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.info-item-full {
  grid-column: 1 / -1;
}

.info-label {
  width: 76px;
  flex-shrink: 0;
  color: #888;
}

.doc-items {
  width: 100%;
  margin-bottom: 10px;
  font-size: 12px;
  border-collapse: collapse;
}

.doc-items th {
  padding: 7px 4px;
  color: #fff;
  font-weight: 600;
  background: #2b5aa7;
  border: 1px solid #2b5aa7;
}

.doc-items td {
  padding: 6px 5px;
  border: 1px solid #dcdfe6;
}

.doc-items tr:nth-child(even) td {
  background: #f7f9fc;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

.doc-total-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
