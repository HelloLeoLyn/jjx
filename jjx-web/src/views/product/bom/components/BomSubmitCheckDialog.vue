<template>
  <el-dialog v-model="visible" title="暂不能提交审核" width="720px" append-to-body>
    <div class="submit-check">
      <p class="submit-check__intro">
        当前 BOM<span v-if="targetName">「{{ targetName }}」</span>还有下列问题，处理后才能提交审核。
      </p>

      <el-alert
        v-if="fatalError"
        :title="fatalError"
        type="error"
        :closable="false"
        show-icon
      />

      <el-alert v-if="loading" title="正在读取相关制造方案信息…" type="info" :closable="false" />

      <div
        v-for="item in schemeItems"
        :key="item.key"
        class="submit-check__item"
        :data-issue-code="item.code"
      >
        <div class="submit-check__head">
          <span class="submit-check__title">{{ item.title }}</span>
          <el-tag v-if="item.statusLabel" size="small" type="info">{{ item.statusLabel }}</el-tag>
        </div>
        <p class="submit-check__message">{{ item.message }}</p>
        <div class="submit-check__actions">
          <el-button v-if="item.canNavigate" type="primary" link @click="navigate(item)">
            查看制造方案
          </el-button>
          <el-button v-if="item.error" link @click="reload">重新读取</el-button>
        </div>
      </div>

      <div v-if="otherItems.length" class="submit-check__others">
        <p class="submit-check__others-title">其他需要处理的问题</p>
        <ul class="submit-check__list">
          <li v-for="item in otherItems" :key="item.key">
            <span v-if="item.location" class="submit-check__location">{{ item.location }}：</span>
            <span>{{ item.message }}</span>
          </li>
        </ul>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { productBomApi } from '@/api/product/bom'
import type { BomCheckIssue } from '@/types/product/bom'
import {
  buildSubmitCheckView,
  loadSchemeDetails,
  mergeSubmitIssues,
} from './bomSubmitCheck'
import type {
  MergedSubBomIssue,
  OtherBlock,
  SchemeBlock,
  SchemeDetailFetcher,
  SchemeNavigateTarget,
} from './bomSubmitCheck'

const props = defineProps<{
  modelValue: boolean
  issues: BomCheckIssue[]
  /** 当前被拦截的 BOM 版本（只用于说明上下文，不参与任何写操作）。 */
  targetName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  navigate: [target: SchemeNavigateTarget]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const fatalError = ref('')
const schemeItems = ref<SchemeBlock[]>([])
const otherItems = ref<OtherBlock[]>([])
let buildRequest = 0

/** 只读详情：读取失败必须抛出，让阻断项显示具体原因，不能当通过。 */
const fetchDetail: SchemeDetailFetcher = async (bomId: number) => {
  const response = await productBomApi.getEngineeringBomInfo(bomId)
  const data = (response as { data?: Record<string, any> } | null | undefined)?.data
  if (!data) throw new Error('方案不存在或无权查看')
  return data
}

const build = async () => {
  const requestId = ++buildRequest
  const merged = mergeSubmitIssues(props.issues)
  const bomIds = merged
    .filter((item): item is MergedSubBomIssue => item.kind === 'sub-bom')
    .map((item) => item.bomId)
  fatalError.value = ''
  loading.value = true
  try {
    const details = await loadSchemeDetails(bomIds, fetchDetail)
    if (requestId !== buildRequest) return
    const view = buildSubmitCheckView(merged, details)
    schemeItems.value = view.schemeItems
    otherItems.value = view.otherItems
  } catch (error: any) {
    if (requestId !== buildRequest) return
    fatalError.value = error?.message || '提交预检问题读取失败，请刷新后重试'
    schemeItems.value = []
    otherItems.value = []
  } finally {
    if (requestId === buildRequest) loading.value = false
  }
}

const reload = () => {
  void build()
}

const navigate = (item: SchemeBlock) => {
  if (!item.target) return
  emit('navigate', item.target)
}

watch(
  () => [props.modelValue, props.issues] as const,
  ([open]) => {
    if (open) void build()
  },
  { immediate: true }
)
</script>

<style scoped>
.submit-check__intro {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
}
.submit-check__item {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.submit-check__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.submit-check__title {
  font-weight: 600;
}
.submit-check__message {
  margin: 0;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
.submit-check__actions {
  margin-top: 6px;
}
.submit-check__others {
  margin-top: 16px;
}
.submit-check__others-title {
  margin: 0 0 6px;
  font-weight: 600;
}
.submit-check__list {
  margin: 0;
  padding-left: 20px;
}
.submit-check__list li {
  line-height: 1.7;
}
.submit-check__location {
  color: var(--el-text-color-secondary);
}
</style>
