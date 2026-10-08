<template>
  <el-dialog
    v-model="visible"
    title="收货地址簿"
    width="820px"
    append-to-body
    destroy-on-close
  >
    <div class="addr-toolbar">
      <span class="addr-customer">{{ customerName || `客户#${customerId ?? '-'}` }}</span>
      <el-button type="primary" icon="Plus" :disabled="!customerId" @click="openEdit()">
        新增地址
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border size="small">
      <el-table-column label="标签" prop="label" width="120" />
      <el-table-column label="联系人" prop="contactPerson" width="100" />
      <el-table-column label="电话" prop="contactPhone" width="130" />
      <el-table-column label="地址" min-width="220">
        <template #default="scope">
          <span>{{ displayAddr(scope.row) }}</span>
          <el-tag v-if="scope.row.isDefault === 1" type="success" size="small" style="margin-left: 6px">
            默认
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center">
        <template #default="scope">
          <el-button
            v-if="selectable"
            link
            type="primary"
            @click="emit('select', scope.row)"
          >
            选为本单地址
          </el-button>
          <el-button
            v-if="scope.row.isDefault !== 1"
            link
            type="success"
            @click="setDefault(scope.row)"
          >
            设为默认
          </el-button>
          <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
          <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span>{{ customerId ? '暂无收货地址，可点右上角「新增地址」' : '请先选择客户' }}</span>
      </template>
    </el-table>

    <!-- 新增/编辑地址 -->
    <el-dialog
      v-model="editVisible"
      :title="form.addressId ? '编辑收货地址' : '新增收货地址'"
      width="640px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="地址标签" prop="label">
          <el-input v-model="form.label" placeholder="如 上海总部 / 东莞仓（可空）" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="收货联系人" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="收货联系电话" maxlength="50" />
        </el-form-item>
        <el-form-item label="国家/地区" prop="country">
          <el-select v-model="form.country" filterable clearable placeholder="请选择" style="width: 100%">
            <el-option
              v-for="c in COMMON_COUNTRIES"
              :key="c.value"
              :label="c.label"
              :value="c.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="省份/州" prop="province">
          <el-input v-model="form.province" maxlength="50" />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" maxlength="50" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="邮政编码" prop="postalCode">
          <el-input v-model="form.postalCode" maxlength="20" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" maxlength="255" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { customerApi } from '@/api/sales/customer'
import type { SalesCustomerAddress } from '@/types/sales/customer'
import { COMMON_COUNTRIES } from '@/types/sales/address'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    customerId?: number
    customerName?: string
    /** 是否展示「选为本单地址」按钮（下单页复用） */
    selectable?: boolean
  }>(),
  { customerId: undefined, customerName: '', selectable: false }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [address: SalesCustomerAddress]
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const saving = ref(false)
const list = ref<SalesCustomerAddress[]>([])

const emptyForm = (): SalesCustomerAddress => ({
  addressId: undefined,
  label: '',
  contactPerson: '',
  contactPhone: '',
  country: 'CN',
  province: '',
  city: '',
  address: '',
  postalCode: '',
  isDefault: 0,
  remark: '',
})

const editVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SalesCustomerAddress>(emptyForm())

const rules: FormRules = {
  address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
}

const displayAddr = (a: SalesCustomerAddress) =>
  [a.country, a.province, a.city, a.address].filter(Boolean).join(' ') +
  (a.postalCode ? ` ${a.postalCode}` : '')

const load = async () => {
  if (!props.customerId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const res = await customerApi.getCustomerAddresses(props.customerId)
    list.value = res?.data || []
  } catch (e) {
    console.error('加载收货地址失败', e)
  } finally {
    loading.value = false
  }
}

const openEdit = (row?: SalesCustomerAddress) => {
  if (!props.customerId) {
    ElMessage.warning('请先选择客户')
    return
  }
  Object.assign(form, emptyForm(), row ? { ...row } : {})
  editVisible.value = true
  formRef.value?.clearValidate()
}

const save = async () => {
  if (!props.customerId || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  const cid = props.customerId
  try {
    if (form.addressId) {
      await customerApi.updateCustomerAddress(cid as number, form.addressId, { ...form })
    } else {
      const res = await customerApi.addCustomerAddress(cid as number, { ...form })
      if (res?.data != null) form.addressId = res.data
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    console.error('保存收货地址失败', e)
  } finally {
    saving.value = false
  }
}

const remove = async (row: SalesCustomerAddress) => {
  if (!props.customerId || !row.addressId) return
  try {
    await ElMessageBox.confirm('确定删除该收货地址吗？', '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await customerApi.deleteCustomerAddress(props.customerId, row.addressId)
    ElMessage.success('已删除')
    await load()
    emit('changed')
  } catch (e) {
    console.error('删除收货地址失败', e)
  }
}

const setDefault = async (row: SalesCustomerAddress) => {
  if (!props.customerId || !row.addressId) return
  try {
    await customerApi.setDefaultCustomerAddress(props.customerId, row.addressId)
    ElMessage.success('已设为默认')
    await load()
    emit('changed')
  } catch (e) {
    console.error('设为默认失败', e)
  }
}

watch(
  () => [props.modelValue, props.customerId] as const,
  ([v]) => {
    if (v) load()
  },
  { immediate: true }
)

defineExpose({ reload: load })
</script>

<style scoped>
.addr-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.addr-customer {
  color: #606266;
}
</style>
