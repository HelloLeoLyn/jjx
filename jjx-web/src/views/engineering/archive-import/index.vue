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
        <el-table-column label="操作" width="220"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openWorkbench(row)">进入工作台</el-button
            ><el-button link type="warning" @click="retry(row)">重新识别</el-button></template
          ></el-table-column
        >
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
            ><el-button type="primary" :disabled="stage === 3" @click="nextStage"
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
          <template v-else-if="stage === 1"
            ><div v-for="w in draft.workflows" :key="w.workflowType" class="workflow">
              <b>{{ workflowName(w.workflowType) }} · {{ w.detection?.detectedStepCount }} 格</b
              ><el-tag :type="w.detection?.usedFallback ? 'danger' : 'success'">{{
                w.detection?.usedFallback ? '已兜底' : '黑边框'
              }}</el-tag>
              <div class="cells">
                <button
                  v-for="s in w.steps"
                  :key="s.stepNo"
                  :class="{ active: selectedStep === s }"
                  @click="selectStep(w, s)"
                >
                  <span>{{ s.stepNo }}</span>
                  <el-tag size="small" :type="s.classificationConfirmed ? 'success' : 'warning'">
                    {{ s.classificationConfirmed ? '已确认' : '待确认' }}
                  </el-tag>
                </button>
              </div>
            </div></template
          >
          <template v-else-if="stage === 2"
            ><button
              v-for="x in flatSteps"
              :key="x.key"
              class="card"
              @click="selectStep(x.workflow, x.step)"
            >
              <span>{{ workflowName(x.workflow.workflowType) }} · {{ x.step.stepNo }}</span
              ><el-tag
                size="small"
                :type="
                  x.step.contentType === ArchiveCellContentTypeEnum.EMPTY.value
                    ? 'info'
                    : stepConfirmed(x.step)
                      ? 'success'
                      : 'warning'
                "
              >
                {{
                  x.step.contentType === ArchiveCellContentTypeEnum.EMPTY.value
                    ? '空工序'
                    : stepConfirmed(x.step)
                      ? '已确认'
                      : '待确认'
                }} </el-tag
              ><el-tag
                v-if="x.step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value"
                type="warning"
              >
                复合 {{ x.step.components.length }} 子工序 </el-tag
              ><el-tag v-else v-bind="ArchiveCellContentTypeEnum.getTagProps(x.step.contentType)">
                {{ ArchiveCellContentTypeEnum.getLabel(x.step.contentType) }} </el-tag
              ><small>{{ x.step.editedText || '未识别' }}</small>
            </button></template
          >
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
        <section class="pane">
          <h4>检查与修正</h4>
          <ProcessOperation
            v-if="selectedStep"
            :items="archivePreviewItems"
            :remark="selectedStep.operationRemark"
          />
          <el-form v-if="selectedGroup" label-position="top"
            ><el-form-item label="分组"><el-input v-model="selectedGroup.label" /></el-form-item>
            <div class="bounds">
              <el-form-item v-for="f in boundFields" :key="f" :label="f"
                ><el-input-number
                  v-model="selectedGroup.bounds[f]"
                  :min="0"
                  :max="1"
                  :step="0.001"
                  :precision="3"
              /></el-form-item>
            </div>
            <el-button type="success" @click="selectedGroup.confirmed = true"
              >确认分组</el-button
            ></el-form
          >
          <el-form v-else-if="selectedStep" label-position="top"
            ><el-form-item label="识别原文"
              ><el-input :model-value="selectedStep.rawText" disabled /></el-form-item
            ><el-form-item label="修订文本"
              ><el-input v-model="selectedStep.editedText" type="textarea" /></el-form-item
            ><el-form-item label="内容形态"
              ><el-select v-model="selectedStep.contentType"
                ><el-option
                  v-for="o in ArchiveCellContentTypeEnum.items"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value" /></el-select></el-form-item
            ><el-form-item label="工序结构"
              ><el-select v-model="selectedStep.processStructure" @change="syncComposite"
                ><el-option
                  v-for="o in ArchiveProcessStructureEnum.items"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value" /></el-select
            ></el-form-item>
            <template
              v-if="selectedStep.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value"
              ><div class="subhead">
                <b>标准子工序</b><el-button link @click="addComponent">新增</el-button>
              </div>
              <div v-for="(c, i) in selectedStep.components" :key="i" class="component">
                <span>{{ i + 1 }}</span
                ><el-input v-model="c.text" placeholder="识别内容" /><el-select
                  v-model="c.processId"
                  clearable
                  filterable
                  placeholder="选择标准工序"
                  @change="c.confirmed = true"
                  ><el-option
                    v-for="p in processes"
                    :key="p.processId"
                    :label="p.processName"
                    :value="p.processId"
                    ><div class="process-option">
                      <SvgIcon v-if="p.icon" :name="p.icon" :size="22" /><span>{{
                        p.processName
                      }}</span>
                    </div></el-option
                  ></el-select
                ><el-input
                  v-if="processById(c.processId)?.hasWorkInstruction === 1 || c.workInstruction"
                  v-model="c.workInstruction"
                  clearable
                  placeholder="作业说明（可选）"
                /><el-button link type="danger" @click="removeComponent(i)">删除</el-button>
              </div></template
            >
            <el-form-item
              v-if="
                selectedStep.contentType !== ArchiveCellContentTypeEnum.EMPTY.value &&
                selectedStep.processStructure !== ArchiveProcessStructureEnum.COMPOSITE.value
              "
              label="标准工序"
            >
              <el-select
                v-model="selectedStep.processId"
                clearable
                filterable
                @change="selectedStep.processMappingConfirmed = true"
              >
                <template #prefix
                  ><IconStepBadge
                    v-if="selectedProcess?.icon"
                    :icon="selectedProcess.icon"
                    :size="22"
                    :work-instruction="selectedStep.workInstruction"
                /></template>
                <el-option
                  v-for="p in processes"
                  :key="p.processId"
                  :label="p.processName"
                  :value="p.processId"
                >
                  <div class="process-option">
                    <SvgIcon v-if="p.icon" :name="p.icon" :size="24" /><span
                      v-else
                      class="process-placeholder"
                      >—</span
                    ><span>{{ p.processName }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="
                selectedStep.processStructure !== ArchiveProcessStructureEnum.COMPOSITE.value &&
                selectedProcess?.hasWorkInstruction === 1
              "
              label="作业说明（显示在图标下标）"
              ><el-input
                v-model="selectedStep.workInstruction"
                clearable
                placeholder="如：线路外形、冲窗口灯孔"
            /></el-form-item>
            <template
              v-if="selectedStep.processStructure === ArchiveProcessStructureEnum.DEPENDENCY.value"
              ><el-form-item label="依赖结构"
                ><el-select v-model="selectedStep.precondition.workflowType"
                  ><el-option label="面板" value="PANEL" /><el-option
                    label="上线"
                    value="UP_LINE" /><el-option
                    label="下线"
                    value="DOWN_LINE" /></el-select></el-form-item
              ><el-form-item label="依赖工序格"
                ><el-input-number
                  v-model="selectedStep.precondition.stepNo"
                  :min="1" /></el-form-item
            ></template>
            <el-collapse v-if="selectedStep.contentType !== ArchiveCellContentTypeEnum.EMPTY.value"
              ><el-collapse-item title="更多：作业说明与工序备注（可选）"
                ><el-form-item
                  v-if="
                    selectedStep.processStructure !== ArchiveProcessStructureEnum.COMPOSITE.value &&
                    selectedProcess?.hasWorkInstruction !== 1
                  "
                  label="作业说明（显示在图标下标）"
                  ><el-input
                    v-model="selectedStep.workInstruction"
                    clearable
                    placeholder="如：线路外形、冲窗口灯孔" /></el-form-item
                ><el-form-item
                  :label="
                    selectedStep.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value
                      ? '整道复合工序备注'
                      : '工序备注'
                  "
                  ><el-input
                    v-model="selectedStep.operationRemark"
                    clearable
                    placeholder="如：一车一模" /></el-form-item></el-collapse-item
            ></el-collapse>
            <el-button type="success" @click="confirmSelectedStep">确认工序格</el-button> </el-form
          ><el-empty v-else description="从中间选择分组或工序格" />
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
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
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
  contentType?: string
  processId?: number
  workInstruction?: string
  confirmed?: boolean
}
type Step = {
  stepNo: number
  bounds: Bounds
  rawText: string
  editedText: string
  contentType: string
  processStructure: string
  classificationConfirmed: boolean
  isComposite: boolean
  components: Component[]
  processMappingConfirmed?: boolean
  processId?: number
  workInstruction?: string
  operationRemark?: string
  precondition: { workflowType?: string; stepNo?: number }
  cellImagePath?: string
}
type Workflow = Group & {
  workflowType: string
  gridConfirmed: boolean
  detection: { detectedStepCount: number; usedFallback: boolean }
  steps: Step[]
}
type Draft = { groups: Group[]; workflows: Workflow[]; [key: string]: unknown }
const stageNames = ['分组确认', '工序分格', '工序确认', '草稿检查'],
  boundFields: (keyof Bounds)[] = ['x1', 'y1', 'x2', 'y2']
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
  selectedGroup = ref<Group>(),
  selectedStep = ref<Step>(),
  originalUrl = ref(''),
  selectedUrl = ref(''),
  groupImageUrls = ref<Record<string, string>>({}),
  processes = ref<StandardProcessItem[]>([])
const cache = new Map<string, string>()
const payload = <T,>(r: any): T => (r?.data?.data ?? r?.data ?? r) as T
const workflowName = (t: string) =>
  (({ PANEL: '面板', UP_LINE: '上线', DOWN_LINE: '下线' }) as Record<string, string>)[t] || t
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
const nonEmpty = computed(() =>
  flatSteps.value.filter((x) => x.step.contentType !== ArchiveCellContentTypeEnum.EMPTY.value)
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
const selectedProcess = computed(() =>
  processes.value.find((p) => p.processId === selectedStep.value?.processId)
)
const archivePreviewItems = computed<ProcessOperationItem[]>(() => {
  const s = selectedStep.value
  if (!s) return []
  if (s.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value)
    return s.components.map((c, i) => {
      const p = processById(c.processId)
      return {
        key: i,
        icon: p?.icon,
        processName: p?.processName || c.text || `子工序${i + 1}`,
        hasWorkInstruction: p?.hasWorkInstruction,
        workInstruction: c.workInstruction,
      }
    })
  const p = selectedProcess.value
  return [
    {
      key: s.stepNo,
      icon: p?.icon,
      processName: p?.processName || s.editedText || s.rawText || `工序${s.stepNo}`,
      hasWorkInstruction: p?.hasWorkInstruction,
      workInstruction: s.workInstruction,
    },
  ]
})
const generationReady = computed(
  () =>
    groups.value.every((g) => g.confirmed) &&
    flatSteps.value.every((x) => {
      if (x.step.contentType === ArchiveCellContentTypeEnum.EMPTY.value) return true
      if (x.step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value)
        return x.step.components.length > 0 && x.step.components.every((c) => c.processId)
      return Boolean(x.step.processId)
    })
)
const processById = (id?: number) => processes.value.find((p) => p.processId === id)
const confirmSelectedStep = () => {
  if (!selectedStep.value) return
  selectedStep.value.classificationConfirmed = true
  selectedStep.value.processMappingConfirmed = true
}
const stepConfirmed = (step: Step) => {
  if (step.contentType === ArchiveCellContentTypeEnum.EMPTY.value)
    return Boolean(step.classificationConfirmed)
  if (!step.classificationConfirmed) return false
  if (step.processStructure === ArchiveProcessStructureEnum.COMPOSITE.value)
    return step.components.length > 0 && step.components.every((c) => Boolean(c.processId))
  return Boolean(step.processId)
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
  draft.value.workflows.forEach((w) =>
    w.steps.forEach((s) => {
      s.precondition ||= {}
      s.workInstruction ||= ''
      s.operationRemark ||= ''
      s.components.forEach((c) => (c.workInstruction ||= ''))
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
  if (!processes.value.length)
    processes.value = payload(await standardProcessApi.getEnabledProcesses())
}
function closeWorkbench() {
  for (const u of cache.values()) URL.revokeObjectURL(u)
  cache.clear()
  originalUrl.value = ''
  selectedUrl.value = ''
  groupImageUrls.value = {}
  selectedGroup.value = undefined
  selectedStep.value = undefined
}
async function selectGroup(g: Group) {
  key.value = g.key || ''
  selectedStep.value = undefined
  const w = draft.value.workflows.find((x) => g.key === `w-${x.workflowType}`)
  selectedGroup.value = w ? (w as unknown as Group) : draft.value.groups[Number(g.key?.slice(2))]
  selectedUrl.value = await url(g.groupImagePath)
}
async function selectStep(w: Workflow, s: Step) {
  key.value = `${w.workflowType}-${s.stepNo}`
  selectedGroup.value = undefined
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
  goStage(Math.min(3, stage.value + 1))
}
function syncComposite(v: string) {
  if (!selectedStep.value) return
  selectedStep.value.isComposite = v === ArchiveProcessStructureEnum.COMPOSITE.value
  if (selectedStep.value.isComposite && !selectedStep.value.components.length) addComponent()
  if (!selectedStep.value.isComposite) selectedStep.value.components = []
}
function addComponent() {
  selectedStep.value?.components.push({
    order: selectedStep.value.components.length + 1,
    text: null,
    contentType: ArchiveCellContentTypeEnum.UNKNOWN.value,
    workInstruction: '',
    confirmed: false,
  })
}
function removeComponent(i: number) {
  selectedStep.value?.components.splice(i, 1)
  selectedStep.value?.components.forEach((c, n) => (c.order = n + 1))
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
  grid-template-columns: 1.1fr 1fr 0.9fr;
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
