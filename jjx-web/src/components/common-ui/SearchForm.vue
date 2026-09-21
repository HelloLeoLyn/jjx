<template>
  <el-card class="search-card" shadow="never">
    <el-form :model="formData" :inline="true">
      <template v-for="field in fields" :key="field.prop">
        <el-form-item :label="field.label" :style="field.block ? 'width:100%;margin-right:0' : undefined">
          <!-- 输入框 -->
          <template v-if="field.type === 'input'">
            <el-input
              v-model="formData[field.prop]"
              :placeholder="`请输入${field.label}`"
              clearable
              style="width: 180px"
              @input="handleInput"
              @keyup.enter="handleSearch"
            />
          </template>

          <!-- 下拉选择 -->
          <template v-else-if="field.type === 'select'">
            <el-select
              v-model="formData[field.prop]"
              :placeholder="`请选择${field.label}`"
              clearable
              style="width: 150px"
              @change="handleSelectChange(field.prop, $event)"
            >
              <el-option
                v-for="item in field.options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>

          <!-- 日期范围 -->
          <template v-else-if="field.type === 'daterange'">
            <el-date-picker
              @change="handleDateChange(field.prop, $event)"
              v-model="formData[field.prop]"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </template>

          <!-- 单个日期 -->
          <template v-else-if="field.type === 'date'">
            <el-date-picker
              @change="handleDateChange(field.prop, $event)"
              v-model="formData[field.prop]"
              type="date"
              :placeholder="`请选择${field.label}`"
              value-format="YYYY-MM-DD"
              style="width: 180px"
            />
          </template>

          <!-- 自定义插槽组件支持 -->
          <template v-else>
            <slot :name="field.prop"></slot>
          </template>
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import type { SearchOptions } from '@/components/common-ui/type'
interface Props {
  modelValue: Record<string, any>
  fields: SearchOptions[]
}

interface Emits {
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'search'): void
  (e: 'reset'): void
}

const props = withDefaults(defineProps<Props>(), {})

const emit = defineEmits<Emits>()

// 使用 reactive 来控制表单数据
const formData = reactive({ ...props.modelValue })

/**
 * 回写父级查询对象。
 * 2026-09-21 修复：父级普遍写成 const queryParams = reactive({...}) + v-model="queryParams"，
 * 而 v-model 的赋值只会改到本组件 setup state，不会改到父级那个闭包对象，
 * 导致父级 getList() 读到的还是旧值 —— 搜索条件静默失效（影响所有用 SearchForm 的列表页）。
 * 所以这里直接就地写进父级传入的对象。
 */
const syncToParent = () => {
  if (props.modelValue && typeof props.modelValue === 'object') {
    Object.assign(props.modelValue, formData)
  }
  emit('update:modelValue', { ...formData })
}

const handleSearch = () => {
  emit('search')
}

const handleInput = () => {
  syncToParent()
}
const handleDateChange = (field: string, value: any) => {
  formData[field] = value
  syncToParent()
}
const handleSelectChange = (field: string, value: any) => {
  // 当下拉框的值变化时，回写到父级查询对象
  formData[field] = value
  syncToParent()
}

const handleReset = () => {
  // 重置时，确保每个字段的默认值
  const resetData: Record<string, any> = {}
  props.fields.forEach((field) => {
    if (field.type === 'daterange') {
      resetData[field.prop] = [] // 日期范围字段重置为空数组
    } else if (field.type === 'select') {
      resetData[field.prop] = null // 选择框字段重置为null
    } else if (field.type === 'date') {
      resetData[field.prop] = '' // 日期字段重置为空字符串
    } else {
      resetData[field.prop] = '' // 输入框字段重置为空字符串
    }
  })

  // 更新父组件的 `modelValue`
  Object.assign(formData, resetData) // 同步数据到 formData
  if (props.modelValue && typeof props.modelValue === 'object') {
    Object.assign(props.modelValue, resetData) // 就地写回父级查询对象（见 syncToParent 注释）
  }
  emit('update:modelValue', resetData)
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
</style>
