<template>
  <el-dialog v-model="visible" title="客户联系人" width="720px" append-to-body destroy-on-close>
    <div class="contact-toolbar">
      <span>{{ customerName }}</span>
      <el-button type="primary" :disabled="!customerId" @click="openEdit()">新增联系人</el-button>
    </div>
    <el-alert
      title="修改联系人不会自动更改已有地址和单据中的姓名、电话。"
      type="info"
      :closable="false"
    />
    <el-table v-loading="loading" :data="list" border style="margin-top: 12px">
      <el-table-column label="姓名" prop="contactPerson" width="120" />
      <el-table-column label="联系电话" prop="contactPhone" min-width="160" />
      <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="130">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
          <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog
      v-model="editVisible"
      :title="form.contactId ? '编辑联系人' : '新增联系人'"
      width="480px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="contactPerson">
          <el-input v-model="form.contactPerson" maxlength="50" placeholder="联系人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="50" placeholder="可填写国际号码或分机" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" maxlength="255" type="textarea" :rows="2" />
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
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { customerApi } from '@/api/sales/customer'
import type { SalesCustomerContact } from '@/types/sales/customer'
import { validateContactPhone } from '@/utils/contactPhone'

const props = defineProps<{ modelValue: boolean; customerId?: number; customerName?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const list = ref<SalesCustomerContact[]>([])
const formRef = ref<FormInstance>()
const emptyForm = (): SalesCustomerContact => ({ contactPerson: '', contactPhone: '', remark: '' })
const form = reactive<SalesCustomerContact>(emptyForm())
const rules: FormRules = {
  contactPerson: [
    { required: true, whitespace: true, message: '请输入联系人姓名', trigger: 'blur' },
  ],
  contactPhone: [{ validator: validateContactPhone, trigger: 'blur' }],
}
let loadVersion = 0

const load = async () => {
  const cid = props.customerId
  const version = ++loadVersion
  list.value = []
  if (!cid) return
  loading.value = true
  try {
    const res = await customerApi.getCustomerContacts(cid)
    if (version === loadVersion && cid === props.customerId) list.value = res?.data || []
  } catch (error) {
    console.error('加载客户联系人失败', error)
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

const openEdit = async (row?: SalesCustomerContact) => {
  Object.assign(form, { contactId: undefined }, emptyForm(), row || {})
  editVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

const save = async () => {
  const cid = props.customerId
  if (!cid || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (form.contactId) await customerApi.updateCustomerContact(cid, form.contactId, { ...form })
    else await customerApi.addCustomerContact(cid, { ...form })
    if (cid !== props.customerId) return
    ElMessage.success('联系人已保存')
    editVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    console.error('保存客户联系人失败', error)
  } finally {
    saving.value = false
  }
}

const remove = async (row: SalesCustomerContact) => {
  const cid = props.customerId
  if (!cid || !row.contactId) return
  try {
    await ElMessageBox.confirm(
      '确认删除该联系人？仍被地址使用的联系人需要先解除关联。',
      '删除联系人',
      { type: 'warning' }
    )
  } catch {
    return
  }
  if (cid !== props.customerId) return
  try {
    await customerApi.deleteCustomerContact(cid, row.contactId)
    if (cid !== props.customerId) return
    ElMessage.success('联系人已删除')
    await load()
    emit('changed')
  } catch (error) {
    console.error('删除客户联系人失败', error)
  }
}

watch(
  () => [props.modelValue, props.customerId] as const,
  ([open]) => {
    editVisible.value = false
    ++loadVersion
    if (open) load()
  },
  { immediate: true }
)
</script>

<style scoped>
.contact-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>
