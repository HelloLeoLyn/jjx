<template>
  <el-dialog :title="title" v-model="visible" width="1200px" append-to-body>
    <el-form ref="formRef" :model="localFormData" :rules="rules" label-width="100px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="客户名称" prop="customerName">
            <el-input
              v-model="localFormData.customerName"
              placeholder="请输入客户名称"
              maxlength="100"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="客户简称" prop="customerShortName">
            <el-input
              v-model="localFormData.customerShortName"
              placeholder="请输入客户简称"
              maxlength="50"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户类型" prop="customerType">
            <el-select v-model="localFormData.customerType" placeholder="请选择客户类型">
              <el-option
                v-for="dict in customerTypeOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="客户等级" prop="customerLevel">
            <el-select v-model="localFormData.customerLevel" placeholder="请选择客户等级">
              <el-option
                v-for="dict in customerLevelOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户状态" prop="customerStatus">
            <el-select v-model="localFormData.customerStatus" placeholder="请选择客户状态">
              <el-option
                v-for="dict in customerStatusOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="行业分类" prop="industryCategory">
            <el-input
              v-model="localFormData.industryCategory"
              placeholder="请输入行业分类"
              maxlength="100"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户来源" prop="customerSource">
            <el-select v-model="localFormData.customerSource" placeholder="请选择客户来源">
              <el-option
                v-for="dict in customerSourceOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="联系人" prop="contactPerson">
            <el-input
              v-model="localFormData.contactPerson"
              placeholder="请输入联系人姓名"
              maxlength="50"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input
              v-model="localFormData.contactPhone"
              placeholder="请输入联系电话"
              maxlength="20"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="联系邮箱" prop="contactEmail">
            <el-input
              v-model="localFormData.contactEmail"
              placeholder="请输入联系邮箱"
              maxlength="100"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="传真" prop="fax">
            <el-input v-model="localFormData.fax" placeholder="请输入传真号码" maxlength="20" />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="信用额度" prop="creditLimit">
            <el-input-number
              v-model="localFormData.creditLimit"
              :min="0"
              :precision="2"
              :step="1000"
              placeholder="请输入信用额度"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="付款方式" prop="paymentMethod">
            <el-select v-model="localFormData.paymentMethod" placeholder="请选择付款方式">
              <el-option
                v-for="dict in paymentMethodOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户评分" prop="customerScore">
            <el-rate v-model="localFormData.customerScore" :max="5" show-score />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否VIP" prop="vip">
            <el-switch v-model="localFormData.vip" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="localFormData.remark"
              type="textarea"
              placeholder="请输入备注"
              :rows="3"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">地址信息</el-divider>
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item label="国家/地区" prop="country">
            <el-input v-model="localFormData.country" placeholder="请输入国家/地区" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="省份/州" prop="province">
            <el-input v-model="localFormData.province" placeholder="请输入省份/州" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="城市" prop="city">
            <el-input v-model="localFormData.city" placeholder="请输入城市" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="邮编" prop="postalCode">
            <el-input v-model="localFormData.postalCode" placeholder="邮政编码" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="详细地址" prop="address">
            <el-input v-model="localFormData.address" placeholder="请输入街道门牌号/详细地址（含区/县）" maxlength="200" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { CustomerFormData } from '@/types/sales/customer'
import { Search } from '@element-plus/icons-vue'
import { customerApi } from '@/api/sales/customer'
import { useCustomerOptions } from '../composables/useCustomerOptions'

const {
  customerTypeOptions,
  customerLevelOptions,
  customerStatusOptions,
  customerSourceOptions,
  paymentMethodOptions,
} = useCustomerOptions()

// 定义组件属性
interface Props {
  visible: boolean
  title: string
  formData: CustomerFormData
}

// 定义组件事件
interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'update:formData', value: CustomerFormData): void
  (e: 'success', data: CustomerFormData): void
  (e: 'cancel'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 表单引用
const formRef = ref<FormInstance>()

// 本地表单数据副本
const localFormData = reactive<CustomerFormData>({
  customerId: undefined,
  customerCode: '',
  customerName: '',
  customerShortName: '',
  customerType: undefined,
  customerLevel: undefined,
  customerStatus: undefined,
  industryCategory: '',
  customerSource: undefined,
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  fax: '',
  country: '',
  province: '',
  city: '',
  address: '',
  postalCode: '',
  creditLimit: 0,
  usedCreditLimit: 0,
  customerScore: 3,
  paymentMethod: undefined,
  vip: false,
  remark: '',
})

// 监听props变化，更新本地数据
watch(
  () => props.formData,
  (newValue) => {
    Object.assign(localFormData, newValue)
  },
  { immediate: true, deep: true }
)

// 生成客户编码
const generateCode = async () => {
  try {
    const response = await customerApi.generateCode()
    localFormData.customerCode = response.data || ''
  } catch (error) {
    ElMessage.error('生成客户编码失败，请稍后再试')
  }
}

// 验证手机号码格式
const validatePhone = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback()
    return
  }

  // 手机号码正则：11位数字，1开头
  const phoneRegex = /^1[3-9]\d{9}$/
  // 固定电话正则：区号-号码 或 区号号码
  const telRegex = /^(0\d{2,3}-?)?\d{7,8}$/

  if (phoneRegex.test(value) || telRegex.test(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的手机号码或固定电话'))
  }
}

// 验证邮箱格式
const validateEmail = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback()
    return
  }

  const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  if (emailRegex.test(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的邮箱地址'))
  }
}

// 验证传真格式
const validateFax = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback()
    return
  }

  // 传真格式：区号-号码 或 区号号码
  const faxRegex = /^(0\d{2,3}-?)?\d{7,8}$/
  if (faxRegex.test(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的传真号码'))
  }
}

// 表单验证规则
const rules = reactive<FormRules>({
  customerName: [
    { required: true, message: '客户名称不能为空', trigger: 'blur' },
    { max: 100, message: '客户名称长度不能超过100个字符', trigger: 'blur' },
  ],
  customerShortName: [
    { max: 50, message: '客户简称长度不能超过50个字符', trigger: 'blur', required: true },
  ],
  contactPerson: [
    { max: 50, message: '联系人姓名长度不能超过50个字符', trigger: 'blur', required: true },
  ],
  contactPhone: [
    { validator: validatePhone, trigger: 'blur' },
    { max: 20, message: '联系电话长度不能超过20个字符', trigger: 'blur', required: true },
  ],
  contactEmail: [
    { validator: validateEmail, trigger: 'blur' },
    { max: 100, message: '联系邮箱长度不能超过100个字符', trigger: 'blur', required: true },
  ],
  fax: [
    { validator: validateFax, trigger: 'blur' },
    { max: 20, message: '传真号码长度不能超过20个字符', trigger: 'blur' },
  ],
  address: [{ max: 200, message: '详细地址长度不能超过200个字符', trigger: 'blur' }],
  industryCategory: [{ max: 100, message: '行业分类长度不能超过100个字符', trigger: 'blur' }],
  creditLimit: [{ type: 'number', min: 0, message: '信用额度不能为负数', trigger: 'blur' }],
  customerScore: [
    {
      type: 'number',
      min: 1,
      max: 5,
      message: '客户评分必须在1-5分之间',
      trigger: 'blur',
    },
  ],
  remark: [{ max: 500, message: '备注长度不能超过500个字符', trigger: 'blur' }],
})

// 处理提交
const handleSubmit = async () => {
  if (!formRef.value) return

  formRef.value.validate(async (valid) => {
    if (valid) {
      const submitData = { ...localFormData }
      if (!submitData.customerId) {
        submitData.customerCode = ''
      }

      try {
        if (submitData.customerId !== undefined) {
          // 修改客户
          await customerApi.updateCustomer(submitData.customerId, submitData)
          ElMessage.success('修改成功')
        } else {
          // 新增客户
          await customerApi.addCustomer(submitData)
          ElMessage.success('新增成功')
        }
        emit('success', submitData)
        emit('update:visible', false)
      } catch (error) {
        console.error('提交失败:', error)
      }
    } else {
      ElMessage.warning('请检查表单填写是否正确')
    }
  })
}

// 处理取消
const handleCancel = () => {
  emit('cancel')
}

// 计算visible属性，用于双向绑定
const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
