<template>
  <div class="international-address-editor">
    <el-row :gutter="8">
      <!-- 国家 -->
      <el-col :span="12">
        <el-form-item label="国家/地区" :prop="propPath + '.country'">
          <el-select
            v-model="addressData.country"
            placeholder="请选择国家或地区"
            filterable
            clearable
            @change="emitChange"
          >
            <el-option
              v-for="country in countries"
              :key="country.value"
              :label="country.label"
              :value="country.value"
            >
              <span>{{ country.label }}</span>
              <span class="country-en">({{ country.labelEn }})</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-col>

      <!-- 省份/州 -->
      <el-col :span="12">
        <el-form-item label="省份/州" :prop="propPath + '.province'">
          <el-input
            v-model="addressData.province"
            placeholder="请输入省份/州"
            maxlength="50"
            @input="emitChange"
          />
        </el-form-item>
      </el-col>

      <!-- 城市 -->
      <el-col :span="12">
        <el-form-item label="城市" :prop="propPath + '.city'">
          <el-input
            v-model="addressData.city"
            placeholder="请输入城市"
            maxlength="50"
            @input="emitChange"
          />
        </el-form-item>
      </el-col>

      <!-- 区/县 -->
      <el-col :span="12">
        <el-form-item label="区/县" :prop="propPath + '.district'">
          <el-input
            v-model="addressData.district"
            placeholder="请输入区/县"
            maxlength="50"
            @input="emitChange"
          />
        </el-form-item>
      </el-col>

      <!-- 街道/详细地址 -->
      <el-col :span="12">
        <el-form-item label="详细地址" :prop="propPath + '.street'">
          <el-input
            v-model="addressData.street"
            placeholder="请输入街道门牌号/详细地址"
            maxlength="200"
            @input="emitChange"
          />
        </el-form-item>
      </el-col>

      <!-- 邮政编码 -->
      <el-col :span="12">
        <el-form-item label="邮编" :prop="propPath + '.zipCode'">
          <el-input
            v-model="addressData.zipCode"
            placeholder="邮政编码"
            maxlength="20"
            @input="emitChange"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 地址预览 -->
    <el-row v-if="hasAddress" style="margin-top: 4px">
      <el-col :span="24">
        <div class="address-preview">
          <el-tag size="small" type="info" effect="plain">
            {{ displayText }}
          </el-tag>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import {
  type InternationalAddress,
  COMMON_COUNTRIES,
  deserializeAddress,
  serializeAddress,
  getAddressDisplayText,
} from '@/types/sales/address'

const props = withDefaults(
  defineProps<{
    /** 当前地址值（JSON 字符串） */
    modelValue: string
    /** 表单校验的 prop 路径前缀 */
    propPath?: string
  }>(),
  {
    propPath: 'address',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const countries = COMMON_COUNTRIES

// 本地地址数据
const addressData = reactive<InternationalAddress>({
  country: '',
  province: '',
  city: '',
  district: '',
  street: '',
  zipCode: '',
})

// 从 props 初始化
watch(
  () => props.modelValue,
  (newVal) => {
    const parsed = deserializeAddress(newVal || '')
    Object.assign(addressData, parsed)
  },
  { immediate: true }
)

// 是否有地址内容
const hasAddress = computed(() => {
  return Object.values(addressData).some((v) => v && v.trim() !== '')
})

// 地址显示文本
const displayText = computed(() => {
  return getAddressDisplayText(addressData)
})

// 触发更新
const emitChange = () => {
  emit('update:modelValue', serializeAddress({ ...addressData }))
}
</script>

<style scoped>
.international-address-editor {
  width: 100%;
}

.country-en {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.address-preview {
  padding: 0 12px;
}
</style>
