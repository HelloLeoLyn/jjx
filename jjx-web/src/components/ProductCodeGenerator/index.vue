<template>
  <div class="product-code-generator">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="面板结构" required>
          <el-select
            v-model="state.panelType"
            placeholder="面板类型"
            style="width: 100%"
            :disabled="disabled"
          >
            <el-option
              v-for="o in PANEL_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="面板特征" required>
          <el-select
            v-model="state.panelFeature"
            placeholder="面板特征"
            style="width: 100%"
            :disabled="disabled"
          >
            <el-option
              v-for="o in PANEL_FEATURE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="线路类型" required>
          <el-select
            v-model="state.circuitType"
            placeholder="线路类型"
            style="width: 100%"
            :disabled="disabled"
          >
            <el-option
              v-for="o in CIRCUIT_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="线路特征" required>
          <el-select
            v-model="state.circuitFeature"
            placeholder="线路特征"
            style="width: 100%"
            :disabled="disabled"
          >
            <el-option
              v-for="o in CIRCUIT_FEATURE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <!-- 序号（2026-09-30 dev-20260930-011）：默认自动取号，可手填 1~4 位（超过 999 给 4 位） -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="序号">
          <el-input
            v-model="state.serialNo"
            placeholder="点「取号」自动取，或手填 1~4 位数字"
            maxlength="4"
            :disabled="disabled || !serialEditable"
            @input="onSerialInput"
            @blur="onSerialBlur"
          >
            <template v-if="!hideGenerate" #append>
              <el-button
                :icon="Refresh"
                :loading="generating"
                :disabled="disabled || !serialEditable"
                @click="handleGenerate"
                >取号</el-button
              >
            </template>
          </el-input>
          <div class="code-hint">不满 3 位自动补 0（9 → 009）；序号超过 999 直接给 4 位</div>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="产品编码">
          <el-input
            :model-value="preview?.productCode || ''"
            placeholder="选择编码要素后自动生成"
            readonly
            class="code-preview"
          />
          <div v-if="hint" class="code-hint">{{ hint }}</div>
        </el-form-item>
      </el-col>
    </el-row>
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
  normalizeSerial,
  type ProductCodeState,
  type ProductCodeResult,
} from '@/composables/useProductCode'

const props = withDefaults(
  defineProps<{
    /** 客户简称（页面提供，如选择客户后带出） */
    customerShort?: string
    /** 编码构成状态（v-model:state 双向绑定，编辑回显赋值） */
    state?: ProductCodeState
    /** 自定义取序号函数（默认统一接口 /product/code/next-serial） */
    fetchSerial?: (short: string) => Promise<string>
    /** 是否输出完整参数对象（v-model:params），默认 false 只输出编码 */
    emitParams?: boolean
    /** 是否隐藏客户简称显示（页面已有客户选择时用） */
    hideShortName?: boolean
    /** 是否隐藏“取号”按钮（2026-09-02：四选全选后自动生成场景用） */
    hideGenerate?: boolean
    /** 序号是否允许手填（默认 true；false = 只读，只能取号）（2026-09-30 dev-20260930-011） */
    serialEditable?: boolean
    /** 禁用 */
    disabled?: boolean
  }>(),
  {
    emitParams: false,
    hideShortName: false,
    hideGenerate: false,
    serialEditable: true,
    disabled: false,
  },
)

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
  { immediate: true }
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
    state.value.serialNo = normalizeSerial(no) || '001'
  } catch (e: any) {
    hint.value = e?.message || '序号获取失败'
  } finally {
    generating.value = false
  }
}

/** 序号输入：只留数字、最多 4 位（2026-09-30 dev-20260930-011） */
function onSerialInput(value: string) {
  const digits = String(value ?? '').replace(/\D/g, '').slice(0, 4)
  if (digits !== value) state.value.serialNo = digits
}

/** 序号失焦补零：不满 3 位补到 3 位（9 → 009），4 位原样 */
function onSerialBlur() {
  state.value.serialNo = normalizeSerial(state.value.serialNo)
}

defineExpose({
  /** 手动触发一次取号+拼接（选客户后自动调用） */
  generate: handleGenerate,
  /** 获取当前拼接结果（不触发回调） */
  getResult: () => composeProductCode(short.value, state.value),
})
</script>

<style scoped>
.code-preview :deep(.el-input__inner) {
  font-family: monospace;
  color: #67c23a;
}
.code-hint {
  width: 100%;
  margin-top: 4px;
  line-height: 1.5;
  font-size: 12px;
  color: #909399;
}
</style>
