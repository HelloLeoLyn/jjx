<template>
  <div class="product-code-generator">
    <el-row :gutter="16">
      <el-col v-if="!hideShortName" :span="12">
        <el-form-item label="客户简称">
          <el-input :model-value="customerShort" readonly placeholder="选择客户后自动带出" />
        </el-form-item>
      </el-col>
      <el-col :span="hideShortName ? 24 : 12">
        <el-form-item label="流水号">
          <el-input
            v-model="state.serialNo"
            maxlength="3"
            placeholder="3位，点生成编码自动取号可改"
            :disabled="disabled"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="面板结构" required>
          <el-select v-model="state.panelType" placeholder="面板类型" style="width: 100%" :disabled="disabled">
            <el-option v-for="o in PANEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="面板特征" required>
          <el-select v-model="state.panelFeature" placeholder="面板特征" style="width: 100%" :disabled="disabled">
            <el-option v-for="o in PANEL_FEATURE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="线路类型" required>
          <el-select v-model="state.circuitType" placeholder="线路类型" style="width: 100%" :disabled="disabled">
            <el-option v-for="o in CIRCUIT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="线路特征" required>
          <el-select v-model="state.circuitFeature" placeholder="线路特征" style="width: 100%" :disabled="disabled">
            <el-option v-for="o in CIRCUIT_FEATURE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <div class="gen-row" style="margin-bottom: 12px">
      <el-button type="primary" size="small" :loading="generating" :disabled="disabled" @click="handleGenerate">
        <el-icon style="margin-right: 4px"><Refresh /></el-icon>生成编码
      </el-button>
      <span v-if="preview" class="code-preview">编码：<b>{{ preview.productCode }}</b></span>
      <span v-else class="code-hint">{{ hint }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  PANEL_TYPE_OPTIONS,
  PANEL_FEATURE_OPTIONS,
  CIRCUIT_TYPE_OPTIONS,
  CIRCUIT_FEATURE_OPTIONS,
  composeProductCode,
  missingHint,
  defaultFetchSerial,
  type ProductCodeState,
  type ProductCodeResult,
} from '@/composables/useProductCode'

const props = defineProps<{
  /** 客户简称（页面提供，如选择客户后带出） */
  customerShort?: string
  /** 编码构成状态（v-model:state 双向绑定，编辑回显赋值） */
  state?: ProductCodeState
  /** 自定义取流水号函数（默认统一接口 /product/code/next-serial） */
  fetchSerial?: (short: string) => Promise<string>
  /** 是否输出完整参数对象（v-model:params），默认 false 只输出编码 */
  emitParams?: boolean
  /** 是否隐藏客户简称显示（页面已有客户选择时用） */
  hideShortName?: boolean
  /** 禁用 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:state', v: ProductCodeState): void
  (e: 'update:code', v: string): void
  (e: 'update:params', v: ProductCodeResult | null): void
  (e: 'change', data: string | ProductCodeResult): void
}>()

const generating = ref(false)
const internal = reactive<ProductCodeState>({
  serialNo: '',
  panelType: '',
  panelFeature: '',
  circuitType: '',
  circuitFeature: '',
})

// 外部传入 state 则用外部，否则用内部
const state = computed<ProductCodeState>({
  get: () => props.state ?? internal,
  set: (v) => {
    if (props.state) {
      emit('update:state', v)
    } else {
      Object.assign(internal, v)
    }
  },
})

const short = computed(() => (props.customerShort || '').trim())
const preview = ref<ProductCodeResult | null>(null)
const hint = ref('')

// 任一构成变化 → 重新拼接并输出
watch(
  () => [
    short.value,
    state.value.serialNo,
    state.value.panelType,
    state.value.panelFeature,
    state.value.circuitType,
    state.value.circuitFeature,
  ],
  () => emitResult(),
  { immediate: true },
)

function emitResult() {
  const result = composeProductCode(short.value, state.value)
  preview.value = result
  hint.value = result ? '' : missingHint(short.value, state.value)
  if (!result) {
    emit('update:code', '')
    emit('update:params', null)
    return
  }
  emit('update:code', result.productCode)
  if (props.emitParams) {
    emit('update:params', result)
    emit('change', result)
  } else {
    emit('change', result.productCode)
  }
}

async function handleGenerate() {
  if (short.value.length < 1 || short.value.length > 3) {
    hint.value = `客户简称需为1~3位（当前：${short.value || '未选择客户'}）`
    return
  }
  generating.value = true
  try {
    const fetchSerial = props.fetchSerial ?? defaultFetchSerial
    const no = await fetchSerial(short.value)
    state.value.serialNo = String(no || '001').padStart(3, '0').slice(0, 3)
  } catch (e: any) {
    hint.value = e?.message || '流水号获取失败'
  } finally {
    generating.value = false
  }
}

defineExpose({
  /** 手动触发一次取号+拼接（选客户后自动调用） */
  generate: handleGenerate,
  /** 获取当前拼接结果（不触发回调） */
  getResult: () => composeProductCode(short.value, state.value),
})
</script>

<style scoped>
.code-preview {
  margin-left: 12px;
  font-size: 13px;
  color: #67c23a;
}
.code-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
