<template>
  <div ref="anchorRef" class="ess-anchor">
    <el-input
      ref="inputRef"
      :model-value="text"
      :placeholder="placeholder"
      :size="size"
      :disabled="disabled"
      :clearable="clearable"
      :prefix-icon="prefixIcon"
      @input="onInput"
      @compositionstart="onCompositionStart"
      @compositionend="onCompositionEnd"
      @focus="onFocus"
      @blur="onBlur"
      @clear="onClear"
      @keydown.esc.stop="close"
      @keydown.enter="onEnter"
    >
      <template v-if="$slots.prefix" #prefix>
        <slot name="prefix" />
      </template>
    </el-input>

    <el-popover
      :visible="open"
      :virtual-ref="anchorRef || undefined"
      virtual-triggering
      :width="popperWidth"
      placement="bottom-start"
      :show-arrow="false"
      :teleported="true"
      popper-class="ess-popover"
    >
      <div class="ess-panel" @mousedown.prevent>
        <div v-if="loading" class="ess-state">加载中…</div>
        <template v-else>
          <template v-if="groups && groups.length">
            <div v-for="g in groups" :key="g.label" class="ess-group">
              <div class="ess-group-label">{{ g.label }}</div>
              <div
                v-for="item in g.items"
                :key="keyOf(item)"
                class="ess-option"
                :class="{ 'is-active': keyOf(item) === modelValue }"
                @click="pick(item)"
              >
                <slot name="option" :item="item">{{ labelOf(item) }}</slot>
              </div>
            </div>
          </template>
          <template v-else>
            <div
              v-for="item in options"
              :key="keyOf(item)"
              class="ess-option"
              :class="{ 'is-active': keyOf(item) === modelValue }"
              @click="pick(item)"
            >
              <slot name="option" :item="item">{{ labelOf(item) }}</slot>
            </div>
          </template>
          <div v-if="!(groups && groups.length) && !options.length && !failed" class="ess-state">
            无匹配项
          </div>
        </template>

        <slot name="footer" />
        <div v-if="loadingMore" class="ess-state">加载更多…</div>
        <div
          v-else-if="!(groups && groups.length) && total > options.length"
          class="ess-more"
          @click="emit('load-more')"
        >
          加载更多（还有 {{ total - options.length }} 条）
        </div>
        <div v-if="showCreate && text.trim()" class="ess-create" @click="create">
          {{ createText }}
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
/**
 * 可编辑搜索选择器（combobox）—— 公共组件（dev-20261018-011）
 * 问题：el-select 选中后把标签渲染成 placeholder 形态，输入框文字不可直接编辑，选错只能整段重打。
 * 方案：el-input + 下拉面板；选中后输入框内是已选名称/关键字，聚焦可删改，下拉按当前文本续搜。
 * 约束：输入文本变化不直接改业务值，只有选中新项或显式清空才 emit。
 */
import { computed, nextTick, ref, watch } from 'vue'

interface Props {
  modelValue?: number | string | null
  displayValue?: string | null
  /** 查询失败时不再显示「无匹配项」，由调用方渲染失败与重试（dev-20261024-018 复核修复） */
  failed?: boolean
  placeholder?: string
  size?: 'large' | 'default' | 'small'
  disabled?: boolean
  clearable?: boolean
  prefixIcon?: any
  options?: any[]
  groups?: { label: string; items: any[] }[]
  optionKey?: string
  optionLabel?: string
  labelOf?: (item: any) => string
  loading?: boolean
  loadingMore?: boolean
  total?: number
  showCreate?: boolean
  createLabel?: string
  /** 失焦时不回滚已输入文本（仅当使用方显式开启；默认保持原回滚行为） */
  keepTextOnBlur?: boolean
  /** 显式启用搜索草稿通知，默认不改变现有调用方行为。 */
  trackInput?: boolean
  /** 弹层最小宽度；默认 200 保持其它选择器现状。 */
  minWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  displayValue: '',
  placeholder: '输入关键字搜索',
  size: 'small',
  disabled: false,
  clearable: true,
  prefixIcon: undefined,
  options: () => [],
  groups: undefined,
  optionKey: 'id',
  optionLabel: 'name',
  labelOf: undefined,
  loading: false,
  loadingMore: false,
  total: 0,
  showCreate: false,
  createLabel: '',
  keepTextOnBlur: false,
  trackInput: false,
  minWidth: 200,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | string | undefined): void
  (e: 'select', item: any): void
  (e: 'clear'): void
  (e: 'search', keyword: string): void
  (e: 'load-more'): void
  (e: 'create', keyword: string): void
  (e: 'draft', value: { text: string; composing: boolean }): void
}>()

const anchorRef = ref<HTMLElement | null>(null)
const inputRef = ref()
const text = ref(props.displayValue || '')
const open = ref(false)
const popperWidth = ref(260)
const focused = ref(false)
let composing = false
let blurTimer: ReturnType<typeof setTimeout> | undefined

const keyOf = (item: any) => (item == null ? '' : item[props.optionKey])
const labelOf = (item: any) =>
  props.labelOf ? props.labelOf(item) : String(item && item[props.optionLabel] != null ? item[props.optionLabel] : '')
const createText = computed(() => props.createLabel || '新建「' + text.value.trim() + '」')

watch(
  () => props.displayValue,
  (v) => {
    if (!focused.value) text.value = v || ''
  }
)

function measure() {
  const el = anchorRef.value
  if (el)
    popperWidth.value = Math.max(
      props.minWidth,
      Math.round(el.getBoundingClientRect().width)
    )
}

function onFocus() {
  if (props.disabled) return
  focused.value = true
  measure()
  open.value = true
}

function onInput(value: string) {
  text.value = value
  open.value = true
  if (props.trackInput) emit('draft', { text: value, composing })
  if (composing) return
  emit('search', value)
}

function onCompositionStart() {
  composing = true
  if (props.trackInput) emit('draft', { text: text.value, composing: true })
}

async function onCompositionEnd(event: CompositionEvent) {
  const value = (event.target as HTMLInputElement).value
  // Element Plus 会在 nextTick 补发 input；先收齐该事件，再补发一次最终搜索。
  await nextTick()
  await nextTick()
  composing = false
  onInput(value)
}

function onBlur() {
  focused.value = false
  clearTimeout(blurTimer)
  blurTimer = setTimeout(() => {
    open.value = false
    if (!props.keepTextOnBlur && text.value !== (props.displayValue || '')) {
      text.value = props.displayValue || ''
    }
  }, 150)
}

function pick(item: any) {
  clearTimeout(blurTimer)
  text.value = labelOf(item)
  open.value = false
  focused.value = false
  emit('update:modelValue', keyOf(item))
  emit('select', item)
}

function onClear() {
  text.value = ''
  open.value = false
  emit('update:modelValue', undefined)
  emit('clear')
}

function close() {
  open.value = false
}

function onEnter(event?: Event) {
  if (composing) return
  event?.preventDefault()
  if (!open.value) return
  const first = props.groups && props.groups.length ? props.groups[0] && props.groups[0].items[0] : (props.options || [])[0]
  if (first) pick(first)
}

function create() {
  const kw = text.value.trim()
  if (!kw) return
  open.value = false
  emit('create', kw)
}

watch(
  () => props.modelValue,
  () =>
    nextTick(() => {
      if (!focused.value) text.value = props.displayValue || ''
    })
)
</script>

<style scoped>
.ess-anchor {
  width: 100%;
}
</style>

<style>
.ess-popover {
  padding: 4px !important;
}
.ess-panel {
  max-height: 280px;
  overflow-y: auto;
}
.ess-option {
  padding: 6px 10px;
  font-size: 13px;
  line-height: 1.4;
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.ess-option:hover,
.ess-option.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.ess-group-label {
  padding: 6px 10px 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ess-state {
  padding: 8px 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ess-more,
.ess-create {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  border-top: 1px solid var(--el-border-color-lighter);
}
.ess-create {
  margin-top: 2px;
}
</style>
