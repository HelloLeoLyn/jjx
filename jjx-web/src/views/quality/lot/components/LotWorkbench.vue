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
              <!-- dev-20260922-011（G6）：INSPECTING 现在由「保存录入」推进，语义是"已录入、待判定" -->
              <el-option label="待判定（已录入）" value="INSPECTING" />
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
            <el-input
              v-model="query.businessNo"
              clearable
              placeholder="报工/工单/销售/入库/发货单号"
              style="width: 250px"
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
        <!-- dev-20260922-012（G5）：批号 + 复检版本信息（v2 / 已失效 / 复检源于哪批），解决多版本看不清新旧 -->
        <el-table-column label="检验批号" min-width="170">
          <template #default="{ row }">
            <span>{{ row.lotNo }}</span>
            <el-tag
              v-if="Number(row.version || 1) > 1"
              size="small"
              type="warning"
              effect="plain"
              class="lot-tag"
              >v{{ row.version }}</el-tag
            >
            <!-- dev-20260923-034：失效批一律只读，标签上说明为什么（不再给注定失败的按钮） -->
            <el-tooltip
              v-if="row.superseded"
              content="该批已被后续复检版本取代，不能再录入/判定/复检——请对最新版本操作；本批仅可打印查看"
              placement="top"
            >
              <el-tag size="small" type="info" effect="plain" class="lot-tag">已失效</el-tag>
            </el-tooltip>
            <div v-if="row.parentLotId" class="lot-sub">
              复检源于 {{ row.parentLotNo || '批 #' + row.parentLotId }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业务来源" min-width="250">
          <template #default="{ row }">
            <div>{{ sourceLabel(row) }}</div>
            <div v-if="sourceContext(row)" class="sub">{{ sourceContext(row) }}</div>
          </template>
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
        <!-- dev-20260922-011（G4）：可见"合格量进库了没"，解决"到底要不要点同步入库" -->
        <el-table-column label="已入库" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'stored-missing': needSync(row) }">{{ num(row.storedQuantity) }}</span>
          </template>
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
            <div v-if="row.reworkInspectionBlocked" class="rework-block-hint">
              返工工序尚未完工。请先到「生产管理 → 工序执行」完成返工工序，再回来判定复检批。
            </div>
            <el-button
              v-if="canInspect && can(row, 'LOT_INSPECT')"
              link
              type="primary"
              size="small"
              :disabled="busyLotId === row.lotId"
              @click="openItems(row)"
              >录入</el-button
            >
            <el-button
              v-if="canJudge && can(row, 'LOT_JUDGE')"
              link
              type="success"
              size="small"
              :disabled="busyLotId === row.lotId"
              @click="openJudge(row)"
              >判定</el-button
            >
            <el-button
              v-if="canJudge && can(row, 'LOT_REINSPECT')"
              link
              type="warning"
              size="small"
              :loading="busyLotId === row.lotId"
              @click="handleReinspect(row)"
              >复检</el-button
            >
            <!-- dev-20260922-030（用户拍板 A）：批在"入库+处置"双完成后自动 CLOSED，之后不能复检；
                 这里给一个显式「重开」（必须填原因，留痕），重开后回到已判定即可复检 -->
            <el-button
              v-if="canJudge && can(row, 'LOT_REOPEN')"
              link
              type="danger"
              size="small"
              @click="handleReopen(row)"
              >重开</el-button
            >
            <el-button link size="small" @click="printReport(row)">打印</el-button>
            <!-- dev-20260922-012（G7）：把"无操作权限"说清是缺哪个权限，别让人干瞪眼 -->
            <span v-if="!canJudge && !canInspect" class="no-action">
              无操作权限：需要「检验录入」（录入）或「检验判定」（判定/复检）
            </span>
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
      <!-- dev-20260924-017：流程显式化（录入 → 判定 → 不良处置 → 入库/结案） -->
      <InspectionStageBar
        :stages="['检验录入', '检验判定', '不良处置', '入库/结案']"
        :current="0"
        :hint="`${lotType === 'FQC' ? '按 JJX-QR-039 分组' : '检验项'} · 保存后到「判定」填数量（判定才是提交）`"
      />
      <!-- 本批结论（只读，与来料检验「本行结论」同一呈现口径） -->
      <div class="lot-verdict">
        <span class="lv-item">批量 <b>{{ num(current?.lotQuantity) }}</b></span>
        <span class="lv-item">已检 <b>{{ num(current?.inspectedQuantity) }}</b></span>
        <span class="lv-item">合格 <b>{{ num(current?.passQuantity) }}</b></span>
        <span class="lv-item">不良 <b>{{ num(current?.failQuantity) }}</b></span>
        <span class="lv-item">已入库 <b>{{ num(current?.storedQuantity) }}</b></span>
        <span class="lv-hint">逐项给结论即可；数量在「判定」环节确认</span>
      </div>
      <!-- dev-20260922-013：表体限高 + 表头固定，项目多时（FQC 按 JJX-QR-039 分组）不再把弹窗撑出屏幕 -->
      <el-table :data="itemRows" border size="small" max-height="56vh">
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
        <div class="dialog-footer">
          <span class="footer-tip">Tab 移动 · 保存后到「判定」填数量（判定才是提交）</span>
          <el-button @click="itemsVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveItems">保存录入</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 判定 -->
    <el-dialog v-model="judgeVisible" title="检验判定" width="480px" append-to-body>
      <InspectionStageBar
        :stages="['检验录入', '检验判定', '不良处置', '入库/结案']"
        :current="1"
      />
      <el-form label-width="100px">
        <el-form-item label="检验批">{{ current?.lotNo }}</el-form-item>
        <el-form-item label="批量">{{ num(current?.lotQuantity) }}</el-form-item>
        <!-- dev-20260923-021：链上有已报废/让步未确认时，界面先把口径说清（上界=批量−不可回收量） -->
        <el-alert
          v-if="guardInfo && guardInfo.needWarning"
          class="judge-guard-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`本批可判合格上限 ${num(guardInfo.upperBound)}`"
        >
          <span>
            链上有 {{ num(guardInfo.scrappedQuantity) }} 件已报废不可回收
            <template v-if="Number(guardInfo.concessionPendingQuantity || 0) > 0">
              、{{ num(guardInfo.concessionPendingQuantity) }} 件让步待客户确认
            </template>
            。已按上限预填；如需放行，请先登记<b>返工处置</b>并走返工复检，或按<b>让步接收</b>处理（需客户确认）；
            若原报废判定有误，可先「**撤销**」该报废处置再重判。
          </span>
        </el-alert>
        <el-form-item label="检验数量" required>
          <el-input-number
            v-model="judgeForm.inspectedQuantity"
            :min="1"
            :max="Number(current?.lotQuantity || 0)"
          />
        </el-form-item>
        <el-form-item :label="passLabel" required>
          <el-input-number v-model="judgeForm.passQuantity" :min="0" />
        </el-form-item>
        <el-form-item label="不良数量" required>
          <el-input-number v-model="judgeForm.failQuantity" :min="0" />
        </el-form-item>
        <!-- dev-20260924-004：不良件级发号预告 + N 与检验项目不合格合计 Σ 的联动提示 -->
        <el-alert
          v-if="Number(judgeForm.failQuantity || 0) > 0"
          :type="judgeDefectType"
          :closable="false"
          show-icon
          class="judge-defect-tip"
        >
          <template #title>
            按 <b>{{ Math.floor(Number(judgeForm.failQuantity || 0)) }}</b> 件发号（{{ current?.lotNo }}-D…，件级只做追溯、不动库存）
            <span v-if="itemDefectSum > 0">；检验项目不合格合计 Σ = <b>{{ num(itemDefectSum) }}</b></span>
          </template>
          <div v-if="judgeDefectType === 'error'">
            不良数量大于 Σ：还有 {{ num(Number(judgeForm.failQuantity || 0) - itemDefectSum) }} 件未归因 —— 请先到「检验录入」补录检验项目的不合格数（CR/MA/MI）
          </div>
          <div v-else-if="judgeDefectType === 'warning'">
            不良数量小于 Σ：如确属调整后结论，请勾选下方「确认调整」并填写说明（留痕）
          </div>
        </el-alert>
        <!-- dev-20260924-014：原因不再手打，结构化原因（不合格项目 + CR/MA/MI）由检验录入汇总；
             这里只收一句可选的补充说明 -->
        <el-form-item v-if="Number(judgeForm.failQuantity || 0) > 0" label="补充说明">
          <el-input
            v-model="judgeForm.defectReason"
            type="textarea"
            :rows="2"
            placeholder="可选：结构化原因已由检验项目自动汇总，此处仅补充说明"
          />
          <div class="judge-reason-tip">
            结构化原因 = 检验项目的「不合格项目 + CR/MA/MI」，见不良台账「主缺陷」列
          </div>
        </el-form-item>
        <el-form-item v-if="judgeDefectType === 'warning'" label="确认调整" required>
          <el-checkbox v-model="judgeForm.partialConfirmed">
            确认按 {{ num(judgeForm.failQuantity) }} 件判定（与检验项目不合格合计 Σ 不一致）
          </el-checkbox>
          <el-input
            v-model="judgeForm.partialReason"
            placeholder="说明（必填，留痕）"
            style="margin-top: 6px"
          />
        </el-form-item>
        <div class="judge-tip">
          合格 + 不良 必须等于检验数量；不良会自动进不良台账（成品按差额入库，复检只调差额）
          <template v-if="guardInfo && guardInfo.needWarning">
            <br />可判合格上限 <b>{{ num(guardInfo.upperBound) }}</b>（已扣减链上已报废/让步未确认量，不可回填良品）
          </template>
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
import { qualityLotApi, type QualityLot, type QualityLotItem, type JudgementGuardVO } from '@/api/quality/lot'
import { InspectionResult } from '@/enums/quality'
import { hasPermi } from '@/directives'
import InspectionStageBar from '@/components/InspectionStageBar.vue'

const router = useRouter()

// 2026-09-21（dev-20260921-030）：操作栏改为「按权限 + 按状态」渲染，并加行级忙碌锁防连点。
// 原实现 5 个按钮无条件可点（判定后仍能改录入、待检批也能复检）。
const canInspect = computed(() => hasPermi('quality:lot:inspect'))
const canJudge = computed(() => hasPermi('quality:lot:judge'))
const busyLotId = ref<number | null>(null)
// dev-20260923-034：已被后继复检版本取代的批（列表里带「已失效」标签）一律只读 ——
// 原来按钮只看状态，失效批仍显示「复检」，点下去必被后端拒（该批已有复检新版本），界面给了注定失败的动作。
// dev-20260923-039（第二片）：改为**只按后端下发的 allowedActions 渲染**（唯一出处 = AllowedActionResolver），
// 前端不再写任何状态条件（isEditable / isJudged / isClosed 已删除）；权限点仍由 hasPermi 卡一道。
const can = (row: QualityLot, code: string) =>
  Array.isArray(row?.allowedActions) && row.allowedActions.includes(code)
const props = withDefaults(defineProps<{ lotType?: string }>(), { lotType: 'FQC' })
const title = props.lotType === 'IQC' ? '来料检验' : props.lotType === 'OQC' ? '出货检验' : '成品检验'
const lotType = props.lotType
const needSync = (row: QualityLot) =>
  lotType === 'FQC' && Number(row.storedQuantity || 0) < Number(row.passQuantity || 0)
const loading = ref(false)
const rows = ref<QualityLot[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, lotType, status: '', lotNo: '', businessNo: '' })
const current = ref<QualityLot | null>(null)

/**
 * 功能说明（2026-09-21 dev-20260921-032 立；2026-09-22 dev-20260922-011 改为「谁做什么」口径）：
 * 上一版是"系统机制说明"，业务看不明"我现在点哪个"；现在按角色 + 顺序写，机制细节收进括号。
 */
const helpLines = computed<string[]>(() =>
  lotType === 'IQC'
    ? [
        '检验员：点「录入」填检验项目与实测值 → 保存（保存只是存清单，可反复改）。',
        '品质主管：点「判定」填 检验/合格/不良 数量 —— 这才是提交；不良会生成不良台账。',
        '要改已判定的结果：点「复检」新建一版（原批号不变、旧版自动失效）。',
        '看不到按钮 = 缺权限（录入=检验录入；判定/复检=检验判定）。来料的收货入库与隔离处置在「质量管理 → 来料检验 / 来料不合格处置」，不在这页。',
      ]
    : [
        '检验员：点「录入」逐项填实测值 → 保存（保存只是存清单，可反复改，不推进状态）。',
        '品质主管：点「判定」填 检验/合格/不良 数量 —— 这才是提交：合格会自动回写工单完工并同步成品入库，不良会生成不良台账（去「质量管理 → 产品不良台账」处置）。',
        '要改已判定的结果：点「复检」新建一版（原批号不变、旧版自动失效）。',
        '看不到按钮 = 缺权限（录入=检验录入；判定/复检=检验判定）。判定后系统按检验批自动生成待仓库确认的成品入库单。',
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
    INBOUND: '入库单',
    INBOUND_ITEM: '入库单',
    EXECUTION: '工序',
    SALES_DELIVERY: '发货单',
  }
  const key = row.sourceType || ''
  if (row.sourceNo) return `${map[key] || key} ${row.sourceNo}`
  return map[key] || key || '来源未登记'
}
const sourceContext = (row: QualityLot) => {
  if (row.lotType === 'IQC') {
    return row.upstreamSourceNo ? `采购来源 ${row.upstreamSourceNo}` : '采购来源未登记'
  }
  if (row.lotType === 'OQC') {
    return row.salesOrderNo ? `销售单 ${row.salesOrderNo}` : '销售来源未登记'
  }
  const parts = [row.orderNo ? `工单 ${row.orderNo}` : '生产工单未登记']
  if (row.processName) parts.push(`工序 ${row.processName}`)
  parts.push(row.stockProduction ? '备库生产（无销售订单）' : row.salesOrderNo ? `销售单 ${row.salesOrderNo}` : '销售来源未登记')
  return parts.join(' · ')
}
const statusLabel = (status?: string) =>
  // dev-20260922-011（G6）：INSPECTING 由「保存录入」推进 → 语义是"已录入、待判定"
  ({ PENDING: '待检', INSPECTING: '待判定', JUDGED: '已判定', CLOSED: '已关闭' })[status || ''] ||
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
    itemsVisible.value = false
    // dev-20260922-011（G1/G2）：保存只存清单、不推进状态，真正"提交"是「判定」。
    // 这里直接把下一步接上，并用录入数据预填判定数量（不良 = 不合格项的 CR+MA+MI 合计）。
    const savedRow = current.value
    const failItems = itemRows.value.filter((i) => i.result === InspectionResult.FAIL)
    const defectQty = failItems.reduce(
      (sum, i) =>
        sum + Number(i.crQuantity || 0) + Number(i.maQuantity || 0) + Number(i.miQuantity || 0),
      0
    )
    const tip =
      failItems.length > 0
        ? `已保存 ${itemRows.value.length} 项，其中 ${failItems.length} 项不合格（缺陷数合计 ${defectQty}）。`
        : `已保存 ${itemRows.value.length} 项，全部合格。`
    try {
      await ElMessageBox.confirm(
        `${tip}\n是否现在提交判定？（判定后合格量自动入库、不良生成不良台账）`,
        '下一步：提交判定',
        {
          confirmButtonText: '现在判定',
          cancelButtonText: '稍后再说',
          type: failItems.length > 0 ? 'warning' : 'success',
        }
      )
    } catch {
      ElMessage.success('录入已保存（只存清单）；稍后点该行「判定」才算提交')
      return
    }
    openJudge(savedRow, { failQuantity: defectQty })
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
  /** dev-20260924-004：N < Σ 时的确认与说明（留痕） */
  partialConfirmed: false,
  partialReason: '',
})
/**
 * dev-20260923-021 一期：判定护栏（可判合格上界）
 * 打开判定弹窗时取上界：链上有已报废/让步未确认时，默认把「合格」预填为上界、「不良」预填为差额，
 * 避免把已报废量重判回良品（业内数量守恒）；接口异常/无权限时静默降级，保持原行为。
 */
const guardInfo = ref<JudgementGuardVO | null>(null)
const guardLoading = ref(false)
const passLabel = computed(() =>
  guardInfo.value && guardInfo.value.needWarning
    ? `合格数量（上限 ${num(guardInfo.value.upperBound)}）`
    : '合格数量'
)

const loadGuard = async (lotId: number) => {
  guardInfo.value = null
  guardLoading.value = true
  try {
    const res: any = await qualityLotApi.judgementGuard(lotId)
    const g = res?.data as JudgementGuardVO | undefined
    if (g && g.guardAvailable) guardInfo.value = g
  } catch {
    guardInfo.value = null // 降级：不阻塞判定
  } finally {
    guardLoading.value = false
  }
}

/** dev-20260922-011（G2）：支持从「录入」带过来的数量预填（检验=批量、不良=录入里不合格项的 CR+MA+MI 合计、合格=差额） */
const openJudge = async (row: QualityLot, prefill?: { failQuantity?: number }) => {
  current.value = row
  const inspected = Number(row.lotQuantity || 0)
  const fail = Math.max(0, Number(prefill?.failQuantity || 0))
  judgeForm.inspectedQuantity = inspected
  judgeForm.failQuantity = fail
  judgeForm.passQuantity = Math.max(0, inspected - fail)
  judgeForm.defectReason = ''
  judgeForm.partialConfirmed = false
  judgeForm.partialReason = ''
  judgeVisible.value = true
  // dev-20260924-004：先取检验项目不合格合计 Σ（与不良数量 N 联动校验的基准）
  await loadItemDefectSum(Number(row.lotId))
  // 护栏：链上有不可回收量时按上限预填（优先级高于录入预填）
  await loadGuard(Number(row.lotId))
  if (guardInfo.value && guardInfo.value.needWarning) {
    judgeForm.inspectedQuantity = inspected
    judgeForm.passQuantity = Number(guardInfo.value.suggestedPass || 0)
    judgeForm.failQuantity = Number(guardInfo.value.suggestedFail || 0)
  }
}
/** dev-20260924-004：检验项目不合格合计 Σ（= 各项目 CR+MA+MI 之和），判定 N 联动校验的基准 */
const itemDefectSum = ref(0)
const loadItemDefectSum = async (lotId: number) => {
  try {
    const res: any = await qualityLotApi.items(lotId)
    const rows = (res?.data || []) as any[]
    itemDefectSum.value = rows.reduce(
      (sum, r) =>
        sum + Number(r.crQuantity || 0) + Number(r.maQuantity || 0) + Number(r.miQuantity || 0),
      0
    )
  } catch {
    itemDefectSum.value = 0
  }
}

/** 判定提示类型：N>Σ 错误（拦提交）、N<Σ 警告（需确认）、相等成功、Σ=0 普通（兜底「其他」） */
const judgeDefectType = computed(() => {
  const fail = Number(judgeForm.failQuantity || 0)
  if (fail <= 0 || itemDefectSum.value <= 0) return 'info'
  if (fail > itemDefectSum.value) return 'error'
  if (fail < itemDefectSum.value) return 'warning'
  return 'success'
})

const submitJudge = async () => {
  if (!current.value) return
  const inspected = Number(judgeForm.inspectedQuantity || 0)
  const pass = Number(judgeForm.passQuantity || 0)
  const fail = Number(judgeForm.failQuantity || 0)
  if (inspected <= 0) return ElMessage.warning('检验数量必须大于 0')
  if (pass + fail !== inspected) return ElMessage.warning('合格数量 + 不良数量必须等于检验数量')
  // dev-20260924-014：不再强制手写原因；仅当 Σ=0（检验项无任何不合格数）时要求补充说明（与后端同口径=方案 A）
  if (fail > 0 && itemDefectSum.value === 0 && !judgeForm.defectReason.trim())
    return ElMessage.warning('检验项目未录任何不合格数（CR/MA/MI）：请先在「检验录入」补录，或填写补充说明')
  // dev-20260923-021：提交前先拦一道（后端同样校验，双保险）
  // dev-20260924-004：N 与「检验项目不合格合计 Σ」联动（后端同样校验，双保险）
  if (fail > 0 && itemDefectSum.value > 0) {
    if (fail > itemDefectSum.value) {
      return ElMessage.warning(
        `不良数量大于检验项目不合格合计 ${num(itemDefectSum.value)}：还有 ${num(
          fail - itemDefectSum.value
        )} 件未归因，请先在「检验录入」补录检验项目的不合格数`
      )
    }
    if (fail < itemDefectSum.value && (!judgeForm.partialConfirmed || !judgeForm.partialReason.trim())) {
      return ElMessage.warning(
        `不良数量小于检验项目不合格合计 ${num(itemDefectSum.value)}：请勾选「确认调整」并填写说明`
      )
    }
  }
  if (guardInfo.value && guardInfo.value.needWarning && pass > Number(guardInfo.value.upperBound || 0)) {
    return ElMessage.warning(
      `本批可判合格上限 ${num(guardInfo.value.upperBound)}（已扣减已报废 ${num(
        guardInfo.value.scrappedQuantity
      )} 件）；请先登记返工处置并走返工复检，或按让步接收处理`
    )
  }
  judging.value = true
  try {
    await qualityLotApi.judge(current.value.lotId, {
      ...judgeForm,
      result: fail > 0 ? 'fail' : 'pass',
    })
    judgeVisible.value = false
    load()
    // dev-20260922-012（G3）：判定完把"接下来去哪"接上——合格已自动入库；有不良就引导去处置台账
    const judgedRow = current.value
    if (fail > 0) {
      const goText = lotType === 'IQC' ? '去「来料不合格处置」' : '去「产品不良台账」处置'
      const target = lotType === 'IQC' ? '/inventory/iqc-quarantine' : '/quality/ncr'
      try {
        await ElMessageBox.confirm(
          `判定完成：合格 ${num(pass)} 已自动入库，不良 ${num(fail)} 已生成${lotType === 'IQC' ? '隔离与不良台账' : '不良台账'}（待处置 ${num(fail)}）。\n是否现在去处置？`,
          '下一步：处置不良',
          { confirmButtonText: goText, cancelButtonText: '稍后再说', type: 'warning' }
        )
      } catch {
        ElMessage.success('判定完成，不良已进台账（稍后去「质量管理 → 产品不良台账」处置）')
        return
      }
      router.push(
        lotType === 'IQC'
          ? target
          : { path: target, query: { materialCode: judgedRow?.materialCode || undefined } }
      )
    } else {
      ElMessage.success(`判定完成：合格 ${num(pass)} 已自动入库`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '判定失败')
  } finally {
    judging.value = false
  }
}

// ============ 重开（dev-20260922-030） ============
/** 已关闭的批不能复检；这里显式重开（必须填原因留痕），重开后回到"已判定"即可复检 */
const handleReopen = async (row: QualityLot) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `重开 ${row.lotNo}？（该批已入库+处置完成，重开后回到"已判定"，可再复检）\n请填写重开原因，用于留痕。`,
      '重开检验批',
      {
        inputPlaceholder: '如：客户反馈外观问题，需复检',
        confirmButtonText: '重开',
        cancelButtonText: '取消',
        inputValidator: (val: string) => (val && val.trim() ? true : '必须填写重开原因'),
      }
    )
    reason = String(value || '').trim()
  } catch {
    return
  }
  try {
    await qualityLotApi.reopen(Number(row.lotId), reason)
    ElMessage.success('已重开，可对该批执行「复检」')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '重开失败')
  }
}

// ============ 复检 ============
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
/** 打印检验报告（QR-037 进料 / QR-039 成品，报告数据来自检验批） */
const printReport = (row: QualityLot) => {
  router.push({ path: '/quality/print/lot-report', query: { lotId: row.lotId } })
}

onMounted(() => load(1))
</script>

<style scoped>
.judge-reason-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
/* dev-20260924-017：本批结论条（只读） + 弹窗页脚 */
.lot-verdict {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.lv-hint {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.footer-tip {
  flex: 1;
  text-align: left;
  color: #909399;
  font-size: 12px;
}
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
/* dev-20260922-011（G4）：合格量还没全进库 → 已入库数字提醒一下 */
.stored-missing {
  color: #e6a23c;
  font-weight: 600;
}
/* dev-20260922-012（G5）：批号列的复检版本标记与来源批 */
.lot-tag {
  margin-left: 4px;
}
.lot-sub {
  color: #909399;
  font-size: 11px;
  line-height: 1.4;
  margin-top: 2px;
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
.rework-block-hint {
  max-width: 275px;
  margin-bottom: 4px;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.5;
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

.judge-guard-alert {
  margin: 0 0 12px;
}
</style>
