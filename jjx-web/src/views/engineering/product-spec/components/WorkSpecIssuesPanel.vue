<template>
  <section v-if="issues.length" class="work-spec-issues" aria-label="作业规范问题清单">
    <strong>{{ title }}（{{ issues.length }}项）</strong>
    <article v-for="issue in issues" :key="issue.key" class="issue-row">
      <div class="issue-object">{{ issue.object }}</div>
      <div class="issue-reason">{{ issue.reason }}<span v-if="issue.count > 1">（重复 {{ issue.count }} 次）</span></div>
      <div class="issue-next">{{ issue.next }}</div>
      <el-button
        v-if="canView && ['structure', 'attachments', 'customer', 'materials', 'routing'].includes(issue.target)"
        link type="primary" :disabled="blocked || !contextReady" @click="inspect(issue)"
      >查看相关资料</el-button>
      <span v-else-if="!canView" class="issue-next">当前无资料查看权限，请联系资料负责人。</span>
      <span v-if="canView && !contextReady" class="issue-next">问题对象资料尚未成功读取，请核查读取错误后重试。</span>
    </article>
    <el-dialog v-model="visible" title="问题对象 · 已加载资料（只读）" width="760px" append-to-body destroy-on-close>
      <template v-if="selected">
        <p>{{ selected.object }}</p>
        <el-alert :title="selected.reason" type="warning" :closable="false" />
        <p>{{ selected.next }}</p>
        <p class="issue-next">以下为当前已加载资料；未加载、已失效或读取失败不代表资料为空，也不代表检查通过。</p>
        <el-table v-if="fileTarget" :data="context.files || []" size="small" border>
          <el-table-column label="文件名称" min-width="220"><template #default="{ row }">{{ row.displayName || row.fileName || '未取得文件名' }}</template></el-table-column>
          <el-table-column prop="version" label="文件版本" width="100" />
          <el-table-column label="附件ID" width="100"><template #default="{ row }">{{ row.attachmentId || row.id }}</template></el-table-column>
          <template #empty>未取得文件清单，请到对应资料入口核查；不能视为检查通过。</template>
        </el-table>
        <el-table v-else :data="sourceRows" size="small" border>
          <el-table-column label="项目 / 物料" min-width="200"><template #default="{ row }">{{ row.materialName || row.materialCode || row.processName || row.projectName || '未取得对象名称' }}</template></el-table-column>
          <el-table-column prop="specification" label="规格" min-width="140" />
          <template #empty>未取得来源明细，请核查原来源；不能视为检查通过。</template>
        </el-table>
        <el-button v-if="locatable" :disabled="blocked" @click="locate">定位到处理位置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { WorkSpecIssue, WorkSpecIssueContext, WorkSpecIssueTarget } from './workSpecIssues'
const props = withDefaults(defineProps<{
  issues: WorkSpecIssue[]
  context: WorkSpecIssueContext
  title?: string
  canView?: boolean
  contextReady?: boolean
  blocked?: boolean
  locatable?: boolean
}>(), { title: '资料检查问题', canView: false, contextReady: true, blocked: false, locatable: false })
const emit = defineEmits<{ locate: [target: WorkSpecIssueTarget] }>()
const visible = ref(false)
const selected = ref<WorkSpecIssue>()
const fileTarget = computed(() => ['structure', 'attachments', 'customer'].includes(selected.value?.target || ''))
const sourceRows = computed(() => (selected.value?.target === 'routing' ? props.context.routing : props.context.bom)?.items || [])
function inspect(issue: WorkSpecIssue) {
  if (!props.canView || !props.contextReady || props.blocked) return
  selected.value = issue
  visible.value = true
}
function locate() {
  if (!selected.value || !props.canView || props.blocked) return
  emit('locate', selected.value.target)
  visible.value = false
}
watch(() => props.context.productId, () => { visible.value = false; selected.value = undefined })
watch(() => props.canView, (allowed) => { if (!allowed) { visible.value = false; selected.value = undefined } })
</script>

<style scoped>
.work-spec-issues { margin: 12px 0; padding: 12px; border: 1px solid var(--el-color-warning-light-5); border-radius: 6px; }
.issue-row { margin-top: 12px; overflow-wrap: anywhere; }
.issue-object { font-weight: 500; }
.issue-reason { margin: 4px 0; white-space: pre-wrap; }
.issue-next { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
</style>
