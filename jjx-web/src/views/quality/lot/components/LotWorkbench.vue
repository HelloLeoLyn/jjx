<template>
  <div class="lot-workbench">
    <el-card>
      <template #header>
        <div class="header">
          <span>{{ title }}</span>
          <div>
            <el-select
              v-model="query.status"
              clearable
              placeholder="状态"
              style="width: 130px"
              @change="load(1)"
            >
              <el-option label="待检" value="PENDING" />
              <el-option label="检验中" value="INSPECTING" />
              <el-option label="已判定" value="JUDGED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
            <el-input
              v-model="query.lotNo"
              clearable
              placeholder="批号"
              style="width: 170px"
              @keyup.enter="load(1)"
            />
            <el-button type="primary" @click="load(1)">查询</el-button>
            <el-button @click="load()">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 功能说明：这页每个动作是什么意思（FQC/IQC 文案不同） -->
      <el-alert type="info" :closable="false" class="page-help">
        <template #title>
          <span class="help-title">功能说明：{{ title }}（检验批）</span>
        </template>
        <div v-for="(line, idx) in helpLines" :key="idx" class="help-line">{{ line }}</div>
      </el-alert>

      <el-table v-loading="loading" :data="rows" border size="small">
        <template #empty><el-empty description="暂无检验批" /></template>
        <el-table-column prop="lotNo" label="检验批号" min-width="150" />
        <el-table-column label="来源" min-width="150">
          <template #default="{ row }">{{ sourceLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="物料/产品" min-width="170">
          <template #default="{ row }">
            {{ row.materialCode || row.productCode || '-' }}
            <span class="sub">{{ row.materialName || row.productName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="130" />
        <el-table-column label="批量" width="90" align="right">
          <template #default="{ row }">{{ num(row.lotQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已检" width="90" align="right">
          <template #default="{ row }">{{ num(row.inspectedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待检" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ warn: remaining(row) > 0 }">{{ num(remaining(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="合格" width="80" align="right">
          <template #default="{ row }">{{ num(row.passQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="80" align="right">
          <template #default="{ row }">{{ num(row.failQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待处置" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="pendingDefect(row) > 0" type="danger" size="small">{{
              num(pendingDefect(row))
            }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{
              statusLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="90">
          <template #default="{ row }">{{ resultLabel(row.result) }}</template>
        </el-table-column>
        <el-table-column prop="inspector" label="检验员" width="100" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canInspect && isEditable(row)"
              link
              type="primary"
              size="small"
              :disabled="busyLotId === row.lotId"
              @click="openItems(row)"
              >录入</el-button
            >
            <el-button
              v-if="canJudge && isEditable(row)"
              link
              type="success"
              size="small"
              :disabled="busyLotId === row.lotId"
              @click="openJudge(row)"
              >判定</el-button
            >
            <el-button
              v-if="canJudge && isJudged(row)"
              link
              type="warning"
              size="small"
              :loading="busyLotId === row.lotId"
              @click="handleReinspect(row)"
              >复检</el-button
            >
            <el-button link size="small" @click="printReport(row)">打印</el-button>
            <el-tooltip
              content="把工单完工入库数量对齐到「本工单成品检验合格累计」，只补差额（合格累计 − 已入数量）；提示 0 = 没有新增合格量，属正常，可重复点"
              placement="top"
            >
              <el-button
                v-if="canJudge && lotType === 'FQC' && row.orderId && isJudged(row)"
                link
                size="small"
                :loading="busyLotId === row.lotId"
                @click="handleSyncFinish(row)"
                >同步入库</el-button
              >
            </el-tooltip>
            <span v-if="!canJudge && !canInspect" class="no-action">无操作权限</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load()"
          @size-change="load(1)"
        />
      </div>
    </el-card>

    <!-- 录入（检验项） -->
    <el-dialog v-model="itemsVisible" title="检验录入" width="1100px" append-to-body>
      <el-table :data="itemRows" border size="small">
        <el-table-column v-if="lotType === 'FQC'" label="类别" width="75">
          <template #default="{ row }">{{ row.category || '其他' }}</template>
        </el-table-column>
        <el-table-column label="检验项目" width="130">
          <template #default="{ row }"
            ><el-input v-model="row.checkItem" :disabled="lotType === 'FQC'"
          /></template>
        </el-table-column>
        <el-table-column label="检验规范" min-width="170">
          <template #default="{ row }"><el-input v-model="row.standard" /></template>
        </el-table-column>
        <el-table-column label="方法" width="110">
          <template #default="{ row }"><el-input v-model="row.inspectionMethod" /></template>
        </el-table-column>
        <el-table-column label="设备" width="110">
          <template #default="{ row }"><el-input v-model="row.equipment" /></template>
        </el-table-column>
        <el-table-column label="逐件实测（| 分隔）" min-width="150">
          <template #default="{ row }"><el-input v-model="row.sampleValues" /></template>
        </el-table-column>
        <el-table-column label="CR" width="80">
          <template #default="{ row }"
            ><el-input-number v-model="row.crQuantity" :min="0" size="small"
          /></template>
        </el-table-column>
        <el-table-column label="MA" width="80">
          <template #default="{ row }"
            ><el-input-number v-model="row.maQuantity" :min="0" size="small"
          /></template>
        </el-table-column>
        <el-table-column label="MI" width="80">
          <template #default="{ row }"
            ><el-input-number v-model="row.miQuantity" :min="0" size="small"
          /></template>
        </el-table-column>
        <el-table-column label="结论" width="110">
          <template #default="{ row }">
            <el-select v-model="row.result" size="small">
              <el-option label="合格" :value="InspectionResult.PASS" />
              <el-option label="不合格" :value="InspectionResult.FAIL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }"><el-input v-model="row.remark" /></template>
        </el-table-column>
      </el-table>
      <div class="dialog-actions">
        <el-button v-if="lotType !== 'FQC'" @click="addItemRow">新增检验项</el-button>
        <span v-else class="entry-tip">FQC 项目已按 JJX-QR-039 固定分组，逐项填写后保存</span>
      </div>
      <template #footer>
        <el-button @click="itemsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveItems">保存录入</el-button>
      </template>
    </el-dialog>

    <!-- 判定 -->
    <el-dialog v-model="judgeVisible" title="检验判定" width="480px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="检验批">{{ current?.lotNo }}</el-form-item>
        <el-form-item label="批量">{{ num(current?.lotQuantity) }}</el-form-item>
        <el-form-item label="检验数量" required>
          <el-input-number
            v-model="judgeForm.inspectedQuantity"
            :min="1"
            :max="Number(current?.lotQuantity || 0)"
          />
        </el-form-item>
        <el-form-item label="合格数量" required>
          <el-input-number v-model="judgeForm.passQuantity" :min="0" />
        </el-form-item>
        <el-form-item label="不良数量" required>
          <el-input-number v-model="judgeForm.failQuantity" :min="0" />
        </el-form-item>
        <el-form-item v-if="Number(judgeForm.failQuantity || 0) > 0" label="不良原因">
          <el-input
            v-model="judgeForm.defectReason"
            type="textarea"
            :rows="2"
            placeholder="不合格原因"
          />
        </el-form-item>
        <div class="judge-tip">
          合格 + 不良 必须等于检验数量；不良会自动进不良台账（成品按差额入库，复检只调差额）
        </div>
      </el-form>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" :loading="judging" @click="submitJudge">提交判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { qualityLotApi, type QualityLot, type QualityLotItem } from '@/api/quality/lot'
import { InspectionResult } from '@/enums/quality'
import { hasPermi } from '@/directives'

const router = useRouter()

// 2026-09-21（dev-20260921-030）：操作栏改为「按权限 + 按状态」渲染，并加行级忙碌锁防连点。
// 原实现 5 个按钮无条件可点（判定后仍能改录入、待检批也能复检）。
const canInspect = computed(() => hasPermi('quality:lot:inspect'))
const canJudge = computed(() => hasPermi('quality:lot:judge'))
const busyLotId = ref<number | null>(null)
/** 待检/检验中 = 可录入、可判定 */
const isEditable = (row: QualityLot) => ['PENDING', 'INSPECTING'].includes(String(row.status))
/** 已判定 = 可复检、可手工同步入库 */
const isJudged = (row: QualityLot) => row.status === 'JUDGED'

const props = withDefaults(defineProps<{ lotType?: string }>(), { lotType: 'FQC' })
const title = props.lotType === 'IQC' ? '来料检验' : props.lotType === 'OQC' ? '出货检验' : '成品检验'
const lotType = props.lotType

const loading = ref(false)
const rows = ref<QualityLot[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, lotType, status: '', lotNo: '' })
const current = ref<QualityLot | null>(null)

/**
 * 功能说明（2026-09-21 dev-20260921-032）：这页每个动作的含义。
 * FQC 多一条「同步入库」；IQC 的入库与隔离走「库存管理 → 来料检验 / 不合格品处置」。
 */
const helpLines = computed<string[]>(() =>
  lotType === 'IQC'
    ? [
        '① 录入：填检验项目与实测值，保存后进入待判定。',
        '② 判定：填检验数量 / 合格 / 不良。判不合格会同步生成不良台账（去「质量管理 → 不良台账」处置）。',
        '③ 复检：已判定的批可复检，会新建一个版本（旧版自动失效），原批号不变。',
        '④ 来料的收货入库与隔离处置在「库存管理 → 来料检验 / 不合格品处置」，不在这页。',
      ]
    : [
        '① 录入：检验项目按 JJX-QR-039 固定分组，逐项填实测值，保存后进入待判定。',
        '② 判定：填检验数量 / 合格 / 不良。合格累计会回写工单完工数量；不良会同步生成不良台账。',
        '③ 复检：已判定的批可复检，会新建一个版本（旧版自动失效），原批号不变。',
        '④ 同步入库：把工单的完工入库数量对齐到「本工单成品检验合格累计」，只补差额（合格累计 − 已入数量）；提示「0」表示没有新增合格量，属正常，可重复点。',
      ]
)

const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const remaining = (row: QualityLot) =>
  Number(row.lotQuantity || 0) - Number(row.inspectedQuantity || 0)
const pendingDefect = (row: QualityLot) =>
  Number(row.failQuantity || 0) - Number(row.disposedQuantity || 0)
const sourceLabel = (row: QualityLot) => {
  const map: Record<string, string> = {
    WORK_REPORT: '报工批',
    INBOUND_ITEM: '收货行',
    EXECUTION: '工序',
    SALES_DELIVERY: '发货单',
  }
  const key = row.sourceType || ''
  return `${map[key] || key}${row.sourceId ? ' #' + row.sourceId : ''}`
}
const statusLabel = (status?: string) =>
  ({ PENDING: '待检', INSPECTING: '检验中', JUDGED: '已判定', CLOSED: '已关闭' })[status || ''] ||
  status ||
  '-'
const statusTag = (status?: string) =>
  (({ PENDING: 'info', INSPECTING: 'warning', JUDGED: 'success', CLOSED: '' })[status || ''] ||
    'info') as never
const resultLabel = (result?: string) =>
  ({ pass: '合格', fail: '不合格', concession: '特采', pending: '待判' })[result || ''] || '-'

const load = async (page?: number) => {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const res: any = await qualityLotApi.page({ ...query })
    const data = res?.data
    rows.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : Number(data?.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载检验批失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

// ============ 录入 ============
const itemsVisible = ref(false)
const itemRows = ref<QualityLotItem[]>([])
const saving = ref(false)
const fqcLayout = [
  { category: '材 质', items: ['面板', '上线', '下线', '背胶'] },
  { category: '尺 寸', items: ['长度', '宽度', '厚度', 'Key高度', '线头', '视窗'] },
  { category: '功能', items: ['电阻', '防水', '寿命'] },
  { category: '印 刷', items: ['图文', '灯孔', '网点', '线头'] },
  { category: '组合', items: ['LED', 'PIN', '弹片', '背胶'] },
  { category: '颜 色', items: ['Color', 'Color', 'Color', 'Color', 'Color', 'Color', 'Color'] },
]
const createItem = (
  checkItem: string,
  category?: string
): QualityLotItem & { category?: string } => ({
  checkItem,
  category,
  crQuantity: 0,
  maQuantity: 0,
  miQuantity: 0,
  result: InspectionResult.PASS,
})
const alignFqcItems = (items: QualityLotItem[]) => {
  const queues = new Map<string, QualityLotItem[]>()
  for (const item of items) {
    const key = String(item.checkItem || '')
      .trim()
      .toLowerCase()
    queues.set(key, [...(queues.get(key) || []), item])
  }
  const aligned: (QualityLotItem & { category?: string })[] = []
  for (const group of fqcLayout) {
    for (const name of group.items) {
      const item = queues.get(name.toLowerCase())?.shift()
      aligned.push({ ...(item || createItem(name)), checkItem: name, category: group.category })
    }
  }
  // 保留历史上不在 QR-039 固定清单内的项目，避免打开并保存时丢失数据。
  for (const leftovers of queues.values()) {
    aligned.push(...leftovers.map((item) => ({ ...item, category: '其他' })))
  }
  return aligned
}
const openItems = async (row: QualityLot) => {
  current.value = row
  let loaded: QualityLotItem[] = []
  try {
    const res: any = await qualityLotApi.items(row.lotId)
    loaded = (res?.data || []).map((item: QualityLotItem) => ({ ...item }))
  } catch {
    loaded = []
  }
  itemRows.value = lotType === 'FQC' ? alignFqcItems(loaded) : loaded
  if (!itemRows.value.length) itemRows.value = [createItem('')]
  itemsVisible.value = true
}
const addItemRow = () => itemRows.value.push(createItem(''))
const saveItems = async () => {
  if (!current.value) return
  const invalid = itemRows.value.find((item) => !String(item.checkItem || '').trim())
  if (invalid) {
    ElMessage.warning('检验项目名称不能为空')
    return
  }
  saving.value = true
  try {
    // 2026-09-21：只提交后端 DTO 字段 —— category 是前端用于分组展示的字段（本地 ITEM_GROUPS），
    // 后端 QualityLotItemDTO/表里都没有它，原样提交会触发 JSON parse error: Unrecognized field "category"。
    const payload = itemRows.value.map((item) => {
      const dto = { ...(item as QualityLotItem & { category?: string }) }
      delete dto.category
      return dto
    })
    await qualityLotApi.saveItems(current.value.lotId, payload)
    ElMessage.success('录入已保存')
    itemsVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ============ 判定 ============
const judgeVisible = ref(false)
const judging = ref(false)
const judgeForm = reactive({
  inspectedQuantity: 0,
  passQuantity: 0,
  failQuantity: 0,
  defectReason: '',
})
const openJudge = (row: QualityLot) => {
  current.value = row
  judgeForm.inspectedQuantity = Number(row.lotQuantity || 0)
  judgeForm.passQuantity = Number(row.lotQuantity || 0)
  judgeForm.failQuantity = 0
  judgeForm.defectReason = ''
  judgeVisible.value = true
}
const submitJudge = async () => {
  if (!current.value) return
  const inspected = Number(judgeForm.inspectedQuantity || 0)
  const pass = Number(judgeForm.passQuantity || 0)
  const fail = Number(judgeForm.failQuantity || 0)
  if (inspected <= 0) return ElMessage.warning('检验数量必须大于 0')
  if (pass + fail !== inspected) return ElMessage.warning('合格数量 + 不良数量必须等于检验数量')
  if (fail > 0 && !judgeForm.defectReason.trim())
    return ElMessage.warning('有不良时必须填写不良原因')
  judging.value = true
  try {
    await qualityLotApi.judge(current.value.lotId, {
      ...judgeForm,
      result: fail > 0 ? 'fail' : 'pass',
    })
    ElMessage.success('判定完成' + (fail > 0 ? '，不良已进台账' : ''))
    judgeVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '判定失败')
  } finally {
    judging.value = false
  }
}

// ============ 复检 / 同步入库 ============
const handleReinspect = async (row: QualityLot) => {
  if (busyLotId.value === row.lotId) return
  try {
    await ElMessageBox.confirm(
      `将对 ${row.lotNo} 建一个复检新版本（原判定保留但不再计账，库存只调差额），是否继续？`,
      '复检确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  busyLotId.value = row.lotId
  try {
    const res: any = await qualityLotApi.reinspect(row.lotId)
    ElMessage.success(`已生成复检批 ${res?.data?.lotNo || ''}`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '复检失败')
  } finally {
    busyLotId.value = null
  }
}
const handleSyncFinish = async (row: QualityLot) => {
  if (!row.orderId || busyLotId.value === row.lotId) return
  busyLotId.value = row.lotId
  try {
    const res: any = await qualityLotApi.syncFinish(row.orderId, `手工同步（批 ${row.lotNo}）`)
    ElMessage.success(`已按差额同步入库：${res?.data ?? 0}`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    busyLotId.value = null
  }
}

/** 打印检验报告（QR-037 进料 / QR-039 成品，报告数据来自检验批） */
const printReport = (row: QualityLot) => {
  router.push({ path: '/quality/print/lot-report', query: { lotId: row.lotId } })
}

onMounted(() => load(1))
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.sub {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
.warn {
  color: #e6a23c;
  font-weight: 600;
}
.dialog-actions {
  margin-top: 8px;
}
.entry-tip {
  color: #909399;
  font-size: 12px;
}
.judge-tip {
  color: #909399;
  font-size: 12px;
  padding-left: 100px;
}
.no-action {
  color: #c0c4cc;
  font-size: 12px;
}
.page-help {
  margin-bottom: 12px;
}
.page-help .help-title {
  font-weight: 600;
}
.page-help .help-line {
  font-size: 12px;
  line-height: 1.8;
  color: #606266;
}
</style>
