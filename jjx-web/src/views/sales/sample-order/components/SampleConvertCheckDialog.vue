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

    <el-form :model="extrasForm" label-width="90px" style="margin-top: 16px">
      <el-divider content-position="left">付款条件</el-divider>
      <el-form-item label="付款条件">
        <el-select
          v-model="extrasForm.paymentTerms"
          placeholder="请选择付款条件"
          style="width: 100%"
        >
          <el-option
            v-for="dict in paymentTermsOptions"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">收货信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="收货人">
            <el-input v-model="extrasForm.contactPerson" placeholder="请输入联系人" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="收货电话">
            <el-input v-model="extrasForm.contactPhone" placeholder="请输入联系电话" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <InternationalAddressEditor v-model="extrasForm.deliveryAddress" prop-path="address" />
        </el-col>
        <el-col :span="24">
          <el-form-item label="收货条款">
            <el-input v-model="extrasForm.deliveryTerms" placeholder="请输入收货条款" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

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
import InternationalAddressEditor from '@/components/InternationalAddressEditor.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    orderId?: number | null
    orderNo?: string
    sampleContact?: {
      contactPerson?: string
      contactPhone?: string
    }
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

const items = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)
const reminding = ref(false)
const paymentTermsOptions = [
  { value: 'prepaid', label: '预付' },
  { value: 'cod', label: '货到付款' },
  { value: 'net30', label: '月结30天' },
  { value: 'net60', label: '月结60天' },
]
const extrasForm = ref({
  paymentTerms: '',
  deliveryTerms: '',
  deliveryAddress: '',
  contactPerson: '',
  contactPhone: '',
})

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
      extrasForm.value = {
        paymentTerms: '',
        deliveryTerms: '',
        deliveryAddress: '',
        contactPerson: props.sampleContact?.contactPerson || '',
        contactPhone: props.sampleContact?.contactPhone || '',
      }
      await load()
    }
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

async function submit() {
  submitting.value = true
  try {
    const res: any = await sampleOrderApi.convertToProduction(
      props.orderId as number,
      [],
      extrasForm.value
    )
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
