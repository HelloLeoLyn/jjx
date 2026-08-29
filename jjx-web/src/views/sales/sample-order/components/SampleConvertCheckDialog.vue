<template>
  <el-dialog
    v-model="visible"
    :title="`转量产就绪检查 · ${orderNo || ''}`"
    width="720px"
    append-to-body
    destroy-on-close
  >
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
      title="资料齐全，可以转量产"
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
            <el-button link type="warning" size="small" @click="goTransfer">去资料转移</el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!allPass" @click="submit">
        确认转量产
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    orderId?: number | null
    orderNo?: string
  }>(),
  {
    orderId: null,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  /** 去资料转移（关闭弹窗，父组件触发 handleTransfer） */
  goTransfer: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const items = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

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
    if (v && props.orderId) await load()
  }
)

async function load() {
  loading.value = true
  items.value = []
  try {
    const res: any = await sampleOrderApi.convertCheck(props.orderId as number)
    items.value = (res as any)?.data?.items || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

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

function goTransfer() {
  visible.value = false
  emit('goTransfer')
}

async function submit() {
  submitting.value = true
  try {
    const res: any = await sampleOrderApi.convertToProduction(props.orderId as number, [])
    if (res?.code === 200) {
      ElMessage.success('转量产成功，标准订单已生成')
      visible.value = false
      emit('success')
    } else {
      ElMessage.error(res?.msg || '转量产失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '转量产失败')
  } finally {
    submitting.value = false
  }
}
</script>
