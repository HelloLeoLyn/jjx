<template>
  <el-button link type="primary" size="small" @click="dialogVisible = true">
    {{ hasValue ? '查看参数' : '设置参数' }}
  </el-button>

  <el-dialog
    v-model="dialogVisible"
    title="工艺参数"
    width="500px"
    :close-on-click-modal="false"
    append-to-body
    @close="handleClose"
  >
    <div class="process-params-dialog">
      <div class="params-toolbar">
        <el-button type="primary" size="small" icon="Plus" @click="addParam"> 添加参数 </el-button>
      </div>

      <div v-if="params.length === 0" class="empty-tip">
        <el-empty description="暂无参数，请点击上方按钮添加" :image-size="60" />
      </div>

      <div v-for="(param, index) in params" :key="index" class="param-row">
        <el-row :gutter="8">
          <el-col :span="10">
            <el-input
              v-model="param.key"
              placeholder="参数名称"
              size="small"
              @input="handleChange"
            />
          </el-col>
          <el-col :span="12">
            <el-input
              v-model="param.value"
              placeholder="参数值"
              size="small"
              @input="handleChange"
            />
          </el-col>
          <el-col :span="2" class="param-action">
            <el-button type="danger" size="small" link icon="Delete" @click="removeParam(index)" />
          </el-col>
        </el-row>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface ParamItem {
  key: string
  value: string
}

const props = defineProps<{
  modelValue?: string
  template?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const dialogVisible = ref(false)

// 参数列表
const params = ref<ParamItem[]>([])

// 解析已有值
const parseValue = (): ParamItem[] => {
  if (!props.modelValue) return []
  try {
    const obj = JSON.parse(props.modelValue)
    // 如果是数组格式（旧格式兼容），转为 key-value
    if (Array.isArray(obj)) {
      const items: ParamItem[] = []
      obj.forEach((record: Record<string, any>) => {
        Object.entries(record).forEach(([key, value]) => {
          items.push({ key, value: String(value ?? '') })
        })
      })
      return items
    }
    // 如果是对象格式
    return Object.entries(obj).map(([key, value]) => ({
      key,
      value: String(value ?? ''),
    }))
  } catch {
    return []
  }
}

// 是否有值
const hasValue = computed(() => {
  return params.value.some((p) => p.key || p.value)
})

// 监听外部值变化
watch(
  () => props.modelValue,
  () => {
    params.value = parseValue()
  },
  { immediate: true }
)

// 添加参数
const addParam = () => {
  params.value.push({ key: '', value: '' })
}

// 删除参数
const removeParam = (index: number) => {
  params.value.splice(index, 1)
  handleChange()
}

// 值变化时同步到父组件
const handleChange = () => {
  const obj: Record<string, string> = {}
  params.value.forEach((p) => {
    if (p.key) {
      obj[p.key] = p.value
    }
  })
  emit('update:modelValue', JSON.stringify(obj))
}

// 关闭弹窗时同步
const handleClose = () => {
  handleChange()
}
</script>

<style scoped>
.process-params-dialog {
  max-height: 500px;
  overflow-y: auto;
}

.params-toolbar {
  margin-bottom: 16px;
}

.empty-tip {
  padding: 20px 0;
}

.param-row {
  margin-bottom: 8px;
  padding: 4px 0;
}

.param-action {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
