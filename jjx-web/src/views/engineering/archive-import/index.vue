<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header
        ><div class="header">
          <div>
            <h3>历史档案识别工作台</h3>
            <div class="hint">逐阶段确认，只保存识别草稿，不生成正式业务数据。</div>
            <el-tag :type="ocrAvailable ? 'success' : 'danger'"
              >OCR：{{ ocrAvailable ? '已连接' : '未启动' }}</el-tag
            >
          </div>
          <el-upload :show-file-list="false" accept="image/jpeg,image/png" :http-request="upload"
            ><el-button type="primary" :loading="uploading">上传并识别</el-button></el-upload
          >
        </div></template
      >
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="fileName" label="原始文件" min-width="240" />
        <el-table-column prop="productCode" label="产品编号" width="170" /><el-table-column
          prop="productName"
          label="产品名称"
          min-width="180"
        />
        <el-table-column label="状态" width="110"
          ><template #default="{ row }"
            ><el-tag v-bind="ArchiveRecognitionStatusEnum.getTagProps(row.recognizeStatus)">{{
              ArchiveRecognitionStatusEnum.getLabel(row.recognizeStatus)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="recognizeMessage" label="识别信息" min-width="220" />
        <el-table-column prop="recognizeTime" label="识别时间" width="180" />
        <TableActionColumn
          :actions="archiveActions"
          width="220"
          display="text"
          @action="handleArchiveAction"
        />
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total,prev,pager,next"
        @change="load"
      />
    </el-card>

    <el-dialog v-model="visible" fullscreen destroy-on-close @closed="closeWorkbench">
      <template #header
        ><div class="header">
          <strong>{{ current?.fileName }}</strong>
          <div>
            <span class="progress">已确认 {{ confirmed }}/{{ confirmable }}</span
            ><el-button :loading="saving" @click="save">保存草稿</el-button
            ><el-button type="primary" :disabled="stage === 2" @click="nextStage"
              >下一阶段</el-button
            >
          </div>
        </div></template
      >
      <el-steps :active="stage" align-center class="stages"
        ><el-step
          v-for="(name, index) in stageNames"
          :key="name"
          :title="name"
          @click="goStage(index)"
      /></el-steps>
      <div class="workspace">
        <section class="pane">
          <h4>原图与切片</h4>
          <div v-if="stage === 0" class="group-collage">
            <img v-if="originalUrl" class="collage-background" :src="originalUrl" />
            <button
              v-for="g in groups"
              :key="`collage-${g.key}`"
              class="collage-tile"
              :class="{ active: key === g.key }"
              :style="groupTileStyle(g)"
              @click="selectGroup(g)"
            >
              <img v-if="groupImageUrls[g.key || '']" :src="groupImageUrls[g.key || '']" />
              <span>{{ g.label }}</span>
            </button>
          </div>
          <div v-else class="image"><img v-if="originalUrl" :src="originalUrl" /></div>
          <div v-if="selectedUrl">
            <h4>当前选择</h4>
            <div class="image crop"><img :src="selectedUrl" /></div>
          </div>
        </section>
        <section class="pane">
          <h4>{{ stageNames[stage] }}</h4>
          <template v-if="stage === 0"
            ><div class="group-toolbar">
              <span>已切割 {{ groups.length }} 个分组，可直接总览并确认</span>
              <el-button type="success" size="small" @click="confirmAllGroups"
                >确认全部分组</el-button
              >
            </div>
            <button
              v-for="g in groups"
              :key="g.key"
              class="card"
              :class="{ active: key === g.key }"
              @click="selectGroup(g)"
            >
              <span class="group-card-content"
                ><img
                  v-if="groupImageUrls[g.key || '']"
                  :src="groupImageUrls[g.key || '']"
                /><span>{{ g.label }}</span></span
              ><el-tag :type="g.confirmed ? 'success' : 'warning'">{{
                g.confirmed ? '已确认' : '待确认'
              }}</el-tag>
            </button></template
          >
          <template v-else-if="stage === 1">
            <div v-for="w in draft.workflows" :key="w.workflowType" class="workflow">
              <div class="workflow-title">
                <b>{{ workflowName(w.workflowType) }} · {{ w.detection?.detectedStepCount }} 格</b>
                <el-tag :type="w.detection?.usedFallback ? 'danger' : 'success'">
                  {{ w.detection?.usedFallback ? '已兜底' : '黑边框' }}
                </el-tag>
              </div>
              <el-table
                :data="w.steps"
                row-key="stepNo"
                border
                highlight-current-row
                @row-click="(row: Step) => selectStep(w, row)"
              >
                <el-table-column type="expand" width="46">
                  <template #default="{ row }">
                    <el-table
                      v-if="row.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value"
                      :data="row.components"
                      border
                      size="small"
                    >
                      <el-table-column type="index" label="子序号" width="80" />
                      <el-table-column label="识别原文" min-width="160">
                        <template #default="{ row: component }">
                          <span>{{ component.text || '图标' }}</span>
                          <el-tag v-if="component.jumpCategory" size="small" type="warning"
                            >{{ jumpCategoryLabel(component.jumpCategory) }}跳</el-tag
                          >
                        </template>
                      </el-table-column>
                      <el-table-column label="标准工序" min-width="220">
                        <template #default="{ row: component }">
                          <el-select
                            v-model="component.processId"
                            filterable
                            clearable
                            placeholder="选择标准工序"
                            @click.stop
                            @change="component.confirmed = false"
                          >
                            <el-option-group
                              v-for="group in processGroups"
                              :key="group.label"
                              :label="group.label"
                            >
                              <el-option
                                v-for="p in group.options"
                                :key="p.processId"
                                :label="p.processName"
                                :value="p.processId"
                              >
                                <span class="process-option-label">
                                  <span class="process-option-name"
                                    ><SvgIcon v-if="p.icon" :name="p.icon" :size="18" />{{
                                      p.processName
                                    }}</span
                                  >
                                  <small>{{ p.processCode }}</small>
                                </span>
                              </el-option>
                            </el-option-group>
                          </el-select>
                        </template>
                      </el-table-column>
                      <el-table-column label="跳类型" width="100">
                        <template #default="{ row: component }">
                          <el-tag v-if="component.jumpCategory" type="warning" size="small">{{ jumpCategoryLabel(component.jumpCategory) }}</el-tag>
                          <span v-else>-</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="作业说明" min-width="160">
                        <template #default="{ row: component }"
                          ><el-input
                            v-model="component.workInstruction"
                            size="small"
                            clearable
                            @click.stop
                        /></template>
                      </el-table-column>
                      <el-table-column label="状态" width="90"
                        ><template #default="{ row: component }"
                          ><el-tag :type="component.confirmed ? 'success' : 'warning'">{{
                            component.confirmed ? '已确认' : '待确认'
                          }}</el-tag></template
                        ></el-table-column
                      >
                      <el-table-column label="操作" width="110"
                        ><template #default="{ row: component }"
                          ><el-button
                            size="small"
                            type="success"
                            plain
                            :disabled="!component.processId"
                            @click.stop="confirmComponent(row, component)"
                            >{{ component.confirmed ? '修改后确认' : '确认' }}</el-button
                          ></template
                        ></el-table-column
                      >
                    </el-table>
                    <el-empty v-else description="该工序不是复合工序" :image-size="50" />
                  </template>
                </el-table-column>
                <el-table-column prop="stepNo" label="序号" width="65" />
                <el-table-column label="标准工序" min-width="220">
                  <template #default="{ row }">
                    <template
                      v-if="row.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value"
                    >
                      <el-tag type="info">复合工序（子项确认）</el-tag>
                    </template>
                    <el-select
                      v-else-if="row.contentType !== ArchiveCellContentTypeEnum.EMPTY.value"
                      v-model="row.processId"
                      filterable
                      clearable
                      placeholder="选择标准工序"
                      @click.stop
                      @change="row.processMappingConfirmed = false"
                    >
                      <el-option-group
                        v-for="group in processGroups"
                        :key="group.label"
                        :label="group.label"
                      >
                        <el-option
                          v-for="p in group.options"
                          :key="p.processId"
                          :label="p.processName"
                          :value="p.processId"
                        >
                          <span class="process-option-label">
                            <span class="process-option-name"
                              ><SvgIcon v-if="p.icon" :name="p.icon" :size="18" />{{
                                p.processName
                              }}</span
                            >
                            <small>{{ p.processCode }}</small>
                          </span>
                        </el-option>
                      </el-option-group>
                    </el-select>
                    <el-tag v-else type="info">空工序</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="跳类型" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.jumpCategory" type="warning" size="small">{{ jumpCategoryLabel(row.jumpCategory) }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90"
                  ><template #default="{ row }"
                    ><el-tooltip
                      v-if="!stepConfirmed(row)"
                      :content="stepIssue(row)"
                      placement="top"
                    >
                      <el-tag type="warning">待确认</el-tag>
                    </el-tooltip>
                    <el-tag v-else type="success">已确认</el-tag></template
                  ></el-table-column
                >
                <el-table-column
                  prop="rawText"
                  label="识别原文"
                  min-width="160"
                  show-overflow-tooltip
                />
                <el-table-column label="内容形态" width="100"
                  ><template #default="{ row }">{{
                    ArchiveCellContentTypeEnum.getLabel(row.contentType)
                  }}</template></el-table-column
                >
                <el-table-column label="工序结构" width="110"
                  ><template #default="{ row }">{{
                    ArchiveProcessStructureEnum.getLabel(row.processStructure)
                  }}</template></el-table-column
                >
                <el-table-column label="作业说明" min-width="160"
                  ><template #default="{ row }"
                    ><el-input
                      v-model="row.workInstruction"
                      size="small"
                      clearable
                      placeholder="作业说明"
                      @click.stop /></template
                ></el-table-column>
                <el-table-column label="备注" min-width="160"
                  ><template #default="{ row }"
                    ><el-input
                      v-model="row.operationRemark"
                      size="small"
                      clearable
                      placeholder="备注"
                      @click.stop /></template
                ></el-table-column>
                <el-table-column label="操作" width="120" fixed="right"
                  ><template #default="{ row }"
                    ><el-button
                      size="small"
                      type="success"
                      plain
                      :disabled="
                        row.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value ||
                        (row.contentType !== ArchiveCellContentTypeEnum.EMPTY.value &&
                          !row.processId)
                      "
                      @click.stop="confirmStep(row)"
                      >{{ stepConfirmed(row) ? '修改后确认' : '确认' }}</el-button
                    ></template
                  ></el-table-column
                >
              </el-table>
            </div>
          </template>
          <template v-else
            ><div class="summary">
              <el-statistic title="分组" :value="groups.length" /><el-statistic
                title="工序格"
                :value="flatSteps.length"
              /><el-statistic title="空白" :value="empty.length" /><el-statistic
                title="复合"
                :value="composites.length"
              />
            </div>
            <el-alert
              :type="confirmed < confirmable ? 'warning' : 'success'"
              :title="
                confirmed < confirmable
                  ? `仍有 ${confirmable - confirmed} 项待确认`
                  : '草稿已全部确认'
              "
              :closable="false"
            />
            <el-button
              type="primary"
              :loading="generating"
              :disabled="
                !generationReady ||
                current?.recognizeStatus === ArchiveRecognitionStatusEnum.GENERATED.value
              "
              @click="generateDrafts"
            >
              {{
                current?.recognizeStatus === ArchiveRecognitionStatusEnum.GENERATED.value
                  ? '已生成正式草稿'
                  : '生成产品、BOM和工艺路线草稿'
              }}
            </el-button>
            <el-alert
              v-if="current?.recognizeStatus === ArchiveRecognitionStatusEnum.GENERATED.value"
              type="success"
              title="正式业务草稿已生成，可到产品、BOM和工艺路线页面继续维护。"
              :closable="false"
            />
          </template>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { archiveImportApi, type ArchiveImportRecord } from '@/api/engineering/archiveImport'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import {
  ArchiveCellContentTypeEnum,
  ArchiveProcessStructureEnum,
  ArchiveRecognitionStatusEnum,
} from '@/enums/engineering/archive'
import SvgIcon from '@/components/SvgIcon/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
type Bounds = { x1: number; y1: number; x2: number; y2: number }
type Group = {
  key?: string
  groupType: string
  label: string
  confirmed: boolean
  bounds: Bounds
  groupImagePath?: string
}
type Component = {
  order: number
  text: string | null
  processId?: number
  indexNumber?: number
  jumpCategory?: 'PANEL' | 'UP_LINE' | 'DOWN_LINE'
  workInstruction?: string
  confirmed?: boolean
}
type Step = {
  stepNo: number
  bounds: Bounds
  rawText: string
  contentType: string
  processStructure: string
  classificationConfirmed: boolean
  components: Component[]
  processMappingConfirmed?: boolean
  processId?: number
  indexNumber?: number
  jumpCategory?: 'PANEL' | 'UP_LINE' | 'DOWN_LINE'
  workInstruction?: string
  operationRemark?: string
  cellImagePath?: string
}
type Workflow = Group & {
  workflowType: string
  detection: { detectedStepCount: number; usedFallback: boolean }
  steps: Step[]
}
type Draft = { groups: Group[]; workflows: Workflow[]; [key: string]: unknown }
const stageNames = ['分组确认', '工序分格与标准工序确认', '草稿检查']
const loading = ref(false),
  uploading = ref(false),
  saving = ref(false),
  generating = ref(false),
  visible = ref(false),
  ocrAvailable = ref(false),
  stage = ref(0),
  key = ref('')
const rows = ref<ArchiveImportRecord[]>([]),
  total = ref(0),
  pageNum = ref(1),
  pageSize = ref(20),
  current = ref<ArchiveImportRecord>()
const draft = ref<Draft>({ groups: [], workflows: [] }),
  selectedStep = ref<Step>(),
  originalUrl = ref(''),
  selectedUrl = ref(''),
  groupImageUrls = ref<Record<string, string>>({}),
  processes = ref<StandardProcessItem[]>([])
const cache = new Map<string, string>()
const archiveActions: TableAction<ArchiveImportRecord>[] = [
  { key: 'workbench', label: '进入工作台' },
  { key: 'retry', label: '重新识别', type: 'warning' },
]
const payload = <T,>(r: any): T => (r?.data?.data ?? r?.data ?? r) as T
const workflowName = (t: string) =>
  (({ PANEL: '面板', UP_LINE: '上线', DOWN_LINE: '下线' }) as Record<string, string>)[t] || t

const jumpCategoryLabel = (category?: string) =>
  ({ PANEL: '面板', UP_LINE: '上线', DOWN_LINE: '下线' })[category || ''] || category || ''

/** 档案图标中的三种“跳”只按外形识别，不把尾随数字当作工序下标。 */
const detectJumpCategory = (value?: string): Component['jumpCategory'] => {
  const text = (value || '').replace(/\s/g, '')
  if (/□|▭|▱|长方形|矩形/.test(text)) return 'PANEL'
  if (/△|▲|上三角/.test(text)) return 'UP_LINE'
  if (/▽|▼|下三角/.test(text)) return 'DOWN_LINE'
  return undefined
}
const groups = computed(() => [
  ...draft.value.groups.map((g, i) => ({ ...g, key: `g-${i}` })),
  ...draft.value.workflows.map((w) => ({ ...w, key: `w-${w.workflowType}` })),
])
const flatSteps = computed(() =>
  draft.value.workflows.flatMap((workflow) =>
    workflow.steps.map((step) => ({
      key: `${workflow.workflowType}-${step.stepNo}`,
      workflow,
      step,
    }))
  )
)
const empty = computed(() =>
  flatSteps.value.filter((x) => x.step.contentType === ArchiveCellContentTypeEnum.EMPTY.value)
)
const composites = computed(() =>
  flatSteps.value.filter(
    (x) => x.step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value
  )
)
const confirmable = computed(() => groups.value.length + flatSteps.value.length)
const confirmed = computed(
  () =>
    groups.value.filter((g) => g.confirmed).length +
    flatSteps.value.filter((x) => stepConfirmed(x.step)).length
)
const generationReady = computed(
  () =>
    groups.value.every((g) => g.confirmed) &&
    flatSteps.value.every((x) => {
      if (x.step.contentType === ArchiveCellContentTypeEnum.EMPTY.value) return true
      return stepConfirmed(x.step)
    })
)
const processById = (id?: number) => processes.value.find((p) => p.processId === id)
const autoMapJumpProcess = (component: Component) => {
  if (!component.jumpCategory) return
  const candidates = processes.value.filter(
    (p) => p.processCategory === component.jumpCategory && /跳/.test(p.processName || '')
  )
  if (!component.processId && candidates.length === 1) component.processId = candidates[0].processId
}
const normalizeComponentNumbers = (step: Step) => {
  if (step.processStructure !== ArchiveProcessStructureEnum.COMPOSITE.value) return
  const components = step.components || []
  for (let i = components.length - 1; i >= 0; i--) {
    const component = components[i]
    const text = String(component.text || '').trim()
    if (/^\d+$/.test(text) && i > 0) {
      const previous = components[i - 1]
      previous.workInstruction = previous.workInstruction
        ? `${previous.workInstruction} ${text}`
        : text
      components.splice(i, 1)
      continue
    }
    const match = text.match(/^(.*?)(\d+)$/)
    if (match && match[1].trim()) {
      component.text = match[1].trim()
      component.workInstruction = component.workInstruction
        ? `${component.workInstruction} ${match[2]}`
        : match[2]
    }
    component.jumpCategory = detectJumpCategory(component.text || undefined)
    autoMapJumpProcess(component)
  }
}
const normalizeWorkflowStepNumbers = () => {
  draft.value.workflows.forEach((workflow) => {
    let sequence = 1
    workflow.steps.forEach((step) => {
      if (step.contentType === ArchiveCellContentTypeEnum.EMPTY.value) return
      step.stepNo = sequence++
    })
  })
}
const stepIssue = (step: Step) => {
  if (step.contentType === ArchiveCellContentTypeEnum.EMPTY.value) return '空工序无需匹配标准工序'
  if (step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value) {
    if (!step.components?.length) return '复合工序没有识别到子项'
    const missing = step.components.findIndex((c) => !c.processId)
    if (missing >= 0) return `子项${missing + 1}未选择标准工序`
    const unconfirmed = step.components.findIndex((c) => !c.confirmed)
    if (unconfirmed >= 0) return `子项${unconfirmed + 1}尚未确认`
    return '复合工序待确认'
  }
  if (!step.processId) return '未选择标准工序'
  if (!step.processMappingConfirmed) return '标准工序尚未确认'
  return '待确认'
}
const processGroups = computed(() => {
  const grouped = new Map<string, StandardProcessItem[]>()
  processes.value.forEach((process) => {
    const label = process.processCategoryName || process.processCategory || '未分类'
    const items = grouped.get(label) || []
    items.push(process)
    grouped.set(label, items)
  })
  return Array.from(grouped, ([label, options]) => ({ label, options }))
})
const confirmStep = (step: Step) => {
  if (step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value) {
    normalizeComponentNumbers(step)
    if (!step.components.length || step.components.some((c) => !c.processId || !c.confirmed)) return
  } else if (step.contentType !== ArchiveCellContentTypeEnum.EMPTY.value && !step.processId) {
    return
  }
  step.classificationConfirmed = true
  step.processMappingConfirmed = true
}
const confirmComponent = (step: Step, component: Component) => {
  normalizeComponentNumbers(step)
  if (!component.processId) return
  const process = processById(component.processId)
  component.jumpCategory ||= detectJumpCategory(component.text || undefined)
  component.confirmed = true
  if (step.components.length && step.components.every((c) => c.processId && c.confirmed)) {
    step.classificationConfirmed = true
    step.processMappingConfirmed = true
  } else {
    step.classificationConfirmed = false
    step.processMappingConfirmed = false
  }
}
const stepConfirmed = (step: Step) => {
  if (step.contentType === ArchiveCellContentTypeEnum.EMPTY.value)
    return Boolean(step.classificationConfirmed)
  if (!step.classificationConfirmed) return false
  if (step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value) {
    return (
      step.components.length > 0 &&
      step.components.every((c) => {
        const process = processById(c.processId)
        return (
          Boolean(c.processId) &&
          Boolean(c.confirmed) &&
          Boolean(process)
        )
      })
    )
  }
  const process = processById(step.processId)
  return (
    Boolean(step.processId) &&
    Boolean(step.processMappingConfirmed) &&
    Boolean(process)
  )
}
async function load() {
  loading.value = true
  try {
    const p: any = payload(
      await archiveImportApi.page({ pageNum: pageNum.value, pageSize: pageSize.value })
    )
    rows.value = p.records || []
    total.value = Number(p.total || 0)
  } finally {
    loading.value = false
  }
}
async function upload(o: UploadRequestOptions) {
  uploading.value = true
  try {
    await archiveImportApi.upload(o.file as File)
    ElMessage.success('识别完成，请进入工作台')
    await load()
  } finally {
    uploading.value = false
  }
}
async function retry(r: ArchiveImportRecord) {
  try {
    await ElMessageBox.confirm(
      '重新识别会刷新当前档案的识别结果，当前未保存的修改可能丢失，是否继续？',
      '确认重新识别',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await archiveImportApi.retry(r.archiveId)
  ElMessage.success('已重新识别')
  await load()
}
function handleArchiveAction(key: string, row: ArchiveImportRecord) {
  if (key === 'workbench') void openWorkbench(row)
  if (key === 'retry') void retry(row)
}
async function url(path?: string) {
  if (!path) return ''
  if (cache.has(path)) return cache.get(path)!
  const blob = (await archiveImportApi.image(path)) as Blob,
    u = URL.createObjectURL(blob)
  cache.set(path, u)
  return u
}
async function openWorkbench(r: ArchiveImportRecord) {
  const d = payload<ArchiveImportRecord>(await archiveImportApi.detail(r.archiveId))
  current.value = d
  draft.value = d.extractedJson ? JSON.parse(d.extractedJson) : { groups: [], workflows: [] }
  draft.value.groups ||= []
  draft.value.workflows ||= []
  if (!processes.value.length)
    processes.value = payload(await standardProcessApi.getEnabledProcesses())
  normalizeWorkflowStepNumbers()
  draft.value.workflows.forEach((w) =>
    w.steps.forEach((s) => {
      s.workInstruction ||= ''
      s.operationRemark ||= ''
      normalizeComponentNumbers(s)
      s.components.forEach((c) => {
        if (!c.workInstruction && c.indexNumber != null) c.workInstruction = String(c.indexNumber)
        c.workInstruction ||= ''
        c.jumpCategory ||= detectJumpCategory(c.text || undefined)
        autoMapJumpProcess(c)
      })
    })
  )
  visible.value = true
  stage.value = 0
  originalUrl.value = await url(d.filePath)
  groupImageUrls.value = {}
  for (const g of groups.value) {
    const imageUrl = await url(g.groupImagePath)
    if (imageUrl) groupImageUrls.value[g.key || ''] = imageUrl
  }
}
function closeWorkbench() {
  for (const u of cache.values()) URL.revokeObjectURL(u)
  cache.clear()
  originalUrl.value = ''
  selectedUrl.value = ''
  groupImageUrls.value = {}
  selectedStep.value = undefined
}
async function selectGroup(g: Group) {
  key.value = g.key || ''
  selectedStep.value = undefined
  const w = draft.value.workflows.find((x) => g.key === `w-${x.workflowType}`)
  selectedUrl.value = await url(g.groupImagePath)
}
async function selectStep(w: Workflow, s: Step) {
  key.value = `${w.workflowType}-${s.stepNo}`
  selectedStep.value = s
  selectedUrl.value = await url(s.cellImagePath)
}
function groupTileStyle(g: Group) {
  const b = g.bounds
  return {
    left: `${b.x1 * 100}%`,
    top: `${b.y1 * 100}%`,
    width: `${Math.max(1, (b.x2 - b.x1) * 100)}%`,
    height: `${Math.max(1, (b.y2 - b.y1) * 100)}%`,
  }
}
function confirmAllGroups() {
  draft.value.groups.forEach((g) => (g.confirmed = true))
  draft.value.workflows.forEach((w) => (w.confirmed = true))
  ElMessage.success('已确认全部分组')
}
function stageBlocked(target: number): string | undefined {
  if (target <= stage.value) return
  if (stage.value === 0 && groups.value.some((g) => !g.confirmed)) return '请先确认全部分组'
  if (stage.value === 1 && draft.value.workflows.some((w) => !w.steps?.length))
    return '请先完成工序分格'
  if (stage.value === 2 && flatSteps.value.some((x) => !stepConfirmed(x.step)))
    return '请先完成全部工序的分类、拆分和标准工序匹配'
}
function goStage(target: number) {
  if (target <= stage.value) {
    stage.value = target
    return
  }
  if (target > stage.value + 1) {
    ElMessage.warning('请按顺序完成当前阶段')
    return
  }
  const reason = stageBlocked(target)
  if (reason) {
    ElMessage.warning(reason)
    return
  }
  stage.value = target
}
function nextStage() {
  goStage(Math.min(2, stage.value + 1))
}
async function save() {
  if (!current.value) return
  saving.value = true
  try {
    await archiveImportApi.updateResult(current.value.archiveId, draft.value)
    ElMessage.success('草稿已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function generateDrafts() {
  if (!current.value || !generationReady.value) return
  generating.value = true
  try {
    // 生成前先落库当前页面编辑内容，确保人工选择的标准工序不会被旧草稿覆盖。
    await archiveImportApi.updateResult(current.value.archiveId, draft.value)
    current.value = payload(await archiveImportApi.generateDrafts(current.value.archiveId))
    await load()
    ElMessage.success('产品、BOM和工艺路线草稿已生成')
  } finally {
    generating.value = false
  }
}
onMounted(async () => {
  await load()
  const h: any = payload(await archiveImportApi.ocrHealth())
  ocrAvailable.value = h?.available === true
})
</script>

<style scoped>
.header,
.subhead,
.workflow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
h3 {
  margin: 0 0 6px;
}
.hint,
.progress {
  color: var(--el-text-color-secondary);
  margin-right: 14px;
}
.el-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}
.stages {
  margin: 0 20px 20px;
  cursor: pointer;
}
.workspace {
  display: grid;
  grid-template-columns: 0.85fr 2.15fr;
  gap: 12px;
  height: calc(100vh - 155px);
}
.pane {
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.pane h4 {
  margin: 0 0 12px;
}
.image {
  display: flex;
  justify-content: center;
  max-height: 60vh;
  overflow: auto;
  background: var(--el-fill-color-light);
}
.image img {
  max-width: 100%;
  height: auto;
}
.group-collage {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--el-fill-color-light);
  aspect-ratio: 4 / 3;
}
.collage-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
  opacity: 0.16;
}
.collage-tile {
  position: absolute;
  display: flex;
  min-width: 0;
  min-height: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 2px;
  border: 2px solid var(--el-color-warning);
  background: var(--el-bg-color-overlay);
  color: var(--el-text-color-primary);
  cursor: pointer;
}
.collage-tile.active {
  z-index: 2;
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}
.collage-tile img {
  width: 100%;
  height: 100%;
  object-fit: fill;
}
.collage-tile span {
  position: absolute;
  max-width: 90%;
  overflow: hidden;
  padding: 2px 4px;
  border-radius: 3px;
  background: rgb(255 255 255 / 85%);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.group-card-content {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.group-card-content img {
  width: 44px;
  height: 32px;
  object-fit: fill;
  border: 1px solid var(--el-border-color-lighter);
}
.crop {
  max-height: 25vh;
}
.card {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  margin-bottom: 7px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: transparent;
  color: inherit;
}
.card.active,
.card:hover,
.cells button.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.process-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-top: 10px;
}
.process-row {
  padding: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.process-row.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.process-row-head,
.process-match-row,
.process-child-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.process-table-header,
.process-row-head {
  display: grid;
  grid-template-columns: 48px 1.2fr 80px 1fr 80px 140px 140px 56px;
  align-items: center;
  gap: 8px;
}
.process-table-header {
  margin-top: 10px;
  padding: 6px 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  background: var(--el-fill-color-light);
}
.process-row-head {
  cursor: pointer;
}
.process-step-no,
.child-label {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.process-text {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.process-match-row {
  margin-top: 8px;
}
.process-match-row :deep(.el-select) {
  flex: 1;
}
.process-children {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
  padding-left: 24px;
  border-left: 2px solid var(--el-border-color-lighter);
}
.process-child-row :deep(.el-select) {
  width: 220px;
}
.workflow {
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.cells {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 5px;
}
.cells button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  min-height: 34px;
  border: 1px solid var(--el-border-color);
  background: transparent;
  border-radius: 5px;
}
.bounds,
.summary {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.bounds :deep(.el-input-number),
.pane :deep(.el-select) {
  width: 100%;
}
.component {
  display: grid;
  grid-template-columns: 24px 1fr 44px;
  align-items: center;
  gap: 6px;
  margin: 8px 0;
}
.process-option {
  display: flex;
  align-items: center;
  gap: 10px;
}
.process-option-label {
  display: inline-flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.process-option-label small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.process-option-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.process-placeholder {
  display: inline-flex;
  width: 24px;
  justify-content: center;
  color: var(--el-text-color-placeholder);
}
@media (max-width: 1100px) {
  .workspace {
    grid-template-columns: 1fr 1fr;
    height: auto;
  }
  .pane:last-child {
    grid-column: 1/-1;
  }
}
</style>
