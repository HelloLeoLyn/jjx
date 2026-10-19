<template>
  <component
    :is="embedded ? 'section' : ElDialog"
    v-model="visible"
    :title="`${check?.returnToSourceOrder ? '关联回原订单' : '转量产就绪检查'} · ${orderNo || ''}`"
    width="720px"
    append-to-body
    destroy-on-close
  >
    <h3 v-if="embedded">关联回原订单</h3>
    <p v-if="check?.returnToSourceOrder">
      原订单：<a
        v-if="check.sourceOrderId"
        :href="`/dashboard/order/detail/${check.sourceOrderId}`"
        target="_blank"
        rel="noopener"
        >{{ check.sourceOrderNo }}</a
      >
      <span v-else>{{ check.sourceOrderNo }}</span>
      · {{ check.productCode }} {{ check.productName }}
    </p>
    <el-alert v-if="loadFailed" type="error" title="就绪检查加载失败，请重试" :closable="false" />
    <el-button v-if="loadFailed" @click="load">重试</el-button>
    <el-alert
      v-if="!allPass"
      type="warning"
      show-icon
      :closable="false"
      :title="`以下资料未就绪，转量产已禁用：${missingNames}`"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-else
      type="success"
      show-icon
      :closable="false"
      :title="
        completed
          ? '已关联回原订单'
          : check?.returnToSourceOrder
            ? '资料齐全，可以关联回原订单'
            : '资料齐全，可以转量产'
      "
      style="margin-bottom: 12px"
    />

    <el-table :data="items" size="small" border v-loading="loading">
      <el-table-column label="校验项" width="110">
        <template #default="scope">
          <span :style="{ fontWeight: 600 }">{{ scope.row.name }}</span>
          <el-tag
            v-if="scope.row.level === 'suggest'"
            size="small"
            type="info"
            style="margin-left: 4px"
            >建议</el-tag
          >
          <el-tag
            v-if="scope.row.level === 'info'"
            size="small"
            type="info"
            style="margin-left: 4px"
            >信息</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row)" size="small">
            {{ statusText(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.message }}</template>
      </el-table-column>
      <el-table-column label="处置" width="140" align="center">
        <template #default="scope">
          <template v-if="scope.row.action === 'edit-product'">
            <el-button link type="primary" size="small" @click="goEditProduct(scope.row)"
              >编辑产品</el-button
            >
          </template>
          <template v-else-if="scope.row.action === 'list-product'">
            <el-button link type="primary" size="small" @click="goListProduct"
              >产品列表建档</el-button
            >
          </template>
          <template v-else-if="scope.row.action === 'transfer'">
            <el-button
              link
              type="warning"
              size="small"
              :loading="reminding"
              @click="remindTransfer(scope.row)"
              >提醒工程</el-button
            >
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="convert-actions">
      <template v-if="completed">
        <span>已关联回原订单 </span>
        <a :href="`/dashboard/order/detail/${check?.sourceOrderId}`" target="_blank" rel="noopener"
          >查看原订单 {{ check?.sourceOrderNo }}</a
        >
        <el-button type="primary" @click="finishReturn">完成</el-button>
      </template>
      <template v-else>
        <el-button v-if="!embedded" @click="visible = false">关闭</el-button>
        <el-button
          v-if="check?.returnToSourceOrder"
          type="primary"
          :disabled="!allPass || loading || loadFailed"
          :loading="submitting"
          @click="confirmReturn"
          >关联回原订单</el-button
        >
      </template>
    </div>
  </component>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import type { SampleConvertCheck } from '@/types/sales/sampleOrder'
import { sampleOrderApi } from '@/api/sales/sampleOrder'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    orderId?: number | null
    orderNo?: string
    embedded?: boolean
  }>(),
  {
    orderId: null,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const check = ref<SampleConvertCheck>()
const loadFailed = ref(false)
const submitting = ref(false)
const completed = ref(false)
const items = ref<any[]>([])
const loading = ref(false)
const reminding = ref(false)

const allPass = computed(
  () => items.value.length > 0 && items.value.every((i) => i.pass || i.level !== 'required')
)
const missingNames = computed(() =>
  items.value
    .filter((i) => i.level === 'required' && !i.pass)
    .map((i) => i.name)
    .join('、')
)

watch(
  () => props.modelValue,
  async (v) => {
    if (v && props.orderId) {
      completed.value = false
      await load()
    }
  },
  { immediate: true }
)

async function load() {
  loading.value = true
  items.value = []
  check.value = undefined
  loadFailed.value = false
  try {
    const res: any = await sampleOrderApi.convertCheck(props.orderId as number)
    if (!res?.data) throw new Error('缺少就绪检查结果')
    check.value = res.data
    items.value = res.data.items || []
  } catch {
    loadFailed.value = true
    items.value = []
  } finally {
    loading.value = false
  }
}

function finishReturn() {
  visible.value = false
  emit('success')
}

async function confirmReturn(): Promise<boolean> {
  if (completed.value) {
    finishReturn()
    return true
  }
  if (
    submitting.value ||
    loading.value ||
    loadFailed.value ||
    !allPass.value ||
    !check.value?.returnToSourceOrder ||
    !props.orderId
  )
    return false
  submitting.value = true
  try {
    const res: any = await sampleOrderApi.convertSample(props.orderId)
    if (res?.code !== 200) throw new Error(res?.msg || '关联原订单失败')
    completed.value = true
    ElMessage.success('已关联回原订单')
    return true
  } catch (e: any) {
    ElMessage.error(e?.message || '关联原订单失败，请刷新后重试')
    return false
  } finally {
    submitting.value = false
  }
}

defineExpose({ confirmReturn })

function statusText(row: any): string {
  if (row.pass) return '就绪'
  if (row.level === 'suggest') return '缺失'
  if (row.level === 'info') return row.status === 'done' ? '已执行' : '未执行'
  return '缺失'
}

function statusTagType(row: any): 'success' | 'danger' | 'warning' | 'info' {
  if (row.pass) return 'success'
  if (row.level === 'suggest') return 'warning'
  if (row.level === 'info') return 'info'
  return 'danger'
}

function goEditProduct(row: any) {
  if (row.productId) {
    window.open(`/product/list/edit/${row.productId}`, '_blank')
  }
}

function goListProduct() {
  window.open('/product/list', '_blank')
}

/** 提醒工程执行资料转移（DEV-1228：发布任务给工程，不再直接转移） */
async function remindTransfer(row: any) {
  if (!props.orderId) return
  reminding.value = true
  try {
    const res: any = await sampleOrderApi.transferRemind(props.orderId)
    if (res?.code === 200) {
      const data = res.data || {}
      if (data.duplicated) {
        ElMessage.info(data.message || '该样品单已提醒过工程')
      } else {
        ElMessage.success('已提醒工程处理资料转移')
      }
    } else {
      ElMessage.error(res?.msg || '提醒失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '提醒失败')
  } finally {
    reminding.value = false
  }
}
</script>
