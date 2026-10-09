<template>
  <div class="calculation-page">
    <header class="page-heading">
      <div>
        <el-button link @click="router.push('/production/order')">← 返回生产订单</el-button>
        <h1>工单用料计算 <el-tag type="warning" effect="plain">UI演示 · Mock数据</el-tag></h1>
        <p>保留工程需求，按本次库存安排实际用料。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="resetScenario">重置本场景</el-button>
        <el-button @click="historyVisible = true">引用历史组合</el-button>
      </div>
    </header>

    <el-alert type="warning" :closable="false" show-icon class="demo-notice">
      所有需求、库存和批次均为演示数据；保存只在当前页面内生效，不生成真实领料单、不扣库存。
      <span v-if="sourceOrderNo"
        >入口工单：{{ sourceOrderNo }}（仅作来源标识，尚未加载该工单的真实BOM）。</span
      >
    </el-alert>

    <div class="workspace">
      <aside class="scenario-panel">
        <div class="panel-caption">
          场景演示 <span>{{ scenarios.length }} 个场景</span>
        </div>
        <button
          v-for="(item, index) in scenarios"
          :key="item.id"
          class="scenario-button"
          :class="{ selected: selectedId === item.id }"
          :aria-pressed="selectedId === item.id"
          @click="selectScenario(item.id)"
        >
          <span class="scenario-number">{{ String(index + 1).padStart(2, '0') }}</span>
          <span
            ><strong>{{ item.title }}</strong
            ><small>{{ item.summary }}</small></span
          >
        </button>
        <div class="scenario-tip">场景切换会保留本页编辑；刷新后恢复初始演示数据。</div>
      </aside>

      <main class="calculation-main">
        <section class="order-card">
          <div class="order-topline">
            <strong>MO-DEMO-2960</strong><el-tag effect="plain">演示工单</el-tag
            ><span>方案 V{{ revision }}</span>
          </div>
          <div class="order-meta">
            <div><small>产品</small><b>透明防护面板 · DEMO-PET-01</b></div>
            <div>
              <small>演示工单数量</small><b>{{ workQuantity }} 件</b>
            </div>
            <div><small>原始工程依据</small><b>BOM-DEMO · V3</b></div>
            <div><small>计算人</small><b>演示计算员</b></div>
          </div>
          <div class="flow">
            <b>01 用料计算</b><span>→</span><span>02 确认方案</span><span>→</span
            ><span>03 领料单</span><span>→</span><span>04 仓库发料</span>
          </div>
        </section>

        <div class="scenario-intro">
          <h2>{{ current.title }}</h2>
          <p>{{ current.note }}</p>
        </div>

        <div class="summary-grid">
          <div>
            <span>原需求合计</span><strong>{{ fmt(summary.total) }}<small>件</small></strong>
          </div>
          <div>
            <span>已发净抵扣</span><strong>{{ fmt(summary.net) }}<small>件</small></strong>
          </div>
          <div>
            <span>待发占用</span><strong>{{ fmt(summary.pending) }}<small>件</small></strong>
          </div>
          <div class="accent">
            <span>本次分配</span><strong>{{ fmt(summary.planned) }}<small>件</small></strong>
          </div>
          <div :class="{ shortage: summary.gap > 0 }">
            <span>未安排缺口</span><strong>{{ fmt(summary.gap) }}<small>件</small></strong>
          </div>
        </div>

        <section v-for="d in current.demands" :key="d.id" class="demand-card">
          <div class="demand-heading">
            <div>
              <el-tag effect="plain">{{ d.id }}</el-tag>
              <h3>{{ d.process }}</h3>
            </div>
            <span>原始工程需求 · 只读</span>
          </div>
          <div class="original-spec">
            <b>A · PET原规格板材</b><span>{{ d.originalSpec }}</span
            ><span>基数 1 / 原模数 1 / 原损耗 0%</span>
          </div>
          <div class="demand-ledger">
            <span
              >总需求 <b>{{ d.total }}</b></span
            ><span
              >原发 <b>{{ d.issued }}</b></span
            ><span
              >退回 <b>{{ d.returned }}</b></span
            >
            <span
              >待发 <b>{{ d.pending }}</b></span
            ><span
              >可安排 <b>{{ remaining(d) }}</b> 件</span
            >
            <el-button v-if="d.pending > 0" link type="warning" @click="releasePending(d)"
              >演示取消待发</el-button
            >
          </div>
          <div class="allocation-title">
            <b>本次实际用料</b><span>先分配承担的需求，再换算实际需领量</span
            ><el-button type="primary" link @click="addAllocation(d)">＋ 添加材料</el-button>
          </div>
          <div class="table-scroll">
            <table class="allocation-table">
              <thead>
                <tr>
                  <th>实际材料 / 当前规格</th>
                  <th>可用库存</th>
                  <th>承担需求（件）</th>
                  <th>本次模数</th>
                  <th>损耗 %</th>
                  <th>实际需领</th>
                  <th></th>
                </tr>
              </thead>
              <tbody v-for="a in d.allocations" :key="a.id">
                <tr>
                  <td class="material-cell">
                    <el-select
                      v-model="a.materialId"
                      :aria-label="`${d.id}实际材料`"
                      @change="changeMaterial(a)"
                    >
                      <el-option
                        v-for="m in current.materials"
                        :key="m.id"
                        :label="m.name"
                        :value="m.id"
                      />
                    </el-select>
                    <small>{{ material(a).spec }}</small>
                    <el-tag v-if="a.materialId === 'D'" type="warning" size="small"
                      >厚度变化 · 请核对加工条件</el-tag
                    >
                  </td>
                  <td>{{ fmt(material(a).available) }} {{ material(a).unit }}</td>
                  <td>
                    <el-input-number
                      v-model="a.coverage"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      :aria-label="`${a.materialId}承担需求`"
                    />
                  </td>
                  <td>
                    <el-input-number
                      v-model="a.yield"
                      :min="0.01"
                      :precision="2"
                      :controls="false"
                      :aria-label="`${a.materialId}模数`"
                    /><small>件 / {{ material(a).unit }}</small>
                  </td>
                  <td>
                    <el-input-number
                      v-model="a.loss"
                      :min="0"
                      :max="100"
                      :precision="2"
                      :controls="false"
                      :aria-label="`${a.materialId}损耗率`"
                    />
                  </td>
                  <td class="quantity-cell">
                    <b>{{ fmt(quantity(a, material(a))) }}</b> {{ material(a).unit
                    }}<small v-if="quantity(a, material(a)) > rawQuantity(a) + 0.00001"
                      >含取整 {{ fmt(quantity(a, material(a)) - rawQuantity(a)) }}</small
                    >
                  </td>
                  <td>
                    <el-button
                      link
                      type="danger"
                      :aria-label="`移除${a.materialId}`"
                      @click="d.allocations = d.allocations.filter((item) => item.id !== a.id)"
                      >移除</el-button
                    >
                  </td>
                </tr>
                <tr class="basis-row">
                  <td colspan="7">
                    <div>
                      <span>{{ a.materialId === d.materialId ? '计算依据' : '换料依据 *' }}</span
                      ><el-input
                        v-model="a.reason"
                        placeholder="填写选料原因、排料方式或关键规格变化依据"
                        :aria-label="`${a.materialId}换料依据`"
                      /><small
                        >{{ fmt(a.coverage) }} ÷ {{ fmt(a.yield) }} × (1 + {{ fmt(a.loss) }}%) → 按
                        {{ material(a).step }} {{ material(a).unit }}取料</small
                      >
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <el-empty
              v-if="!d.allocations.length"
              description="添加一种或多种实际材料，开始分配需求"
              :image-size="60"
            />
          </div>
          <div class="demand-footer">
            <span
              >本次抵扣 <b>{{ fmt(planned(d)) }}</b> / 可安排 {{ fmt(remaining(d)) }} 件</span
            ><strong :class="{ 'warning-text': planned(d) !== remaining(d) }">{{
              planned(d) > remaining(d)
                ? `超出 ${fmt(planned(d) - remaining(d))} 件，请调整`
                : planned(d) < remaining(d)
                  ? `还缺 ${fmt(remaining(d) - planned(d))} 件，可分批领料`
                  : '本次已分配完整'
            }}</strong>
          </div>
        </section>

        <section class="stock-card">
          <div class="section-heading">
            <h3>本次库存校验</h3>
            <span>跨项目合并校验，同一材料不重复分配库存</span>
          </div>
          <div class="stock-grid">
            <div
              v-for="m in usedMaterials"
              :key="m.id"
              :class="{ 'stock-short': totals[m.id] > m.available }"
            >
              <b>{{ m.name }}</b
              ><span>需领 {{ fmt(totals[m.id]) }} / 可用 {{ fmt(m.available) }} {{ m.unit }}</span
              ><strong>{{
                totals[m.id] > m.available
                  ? `缺 ${fmt(totals[m.id] - m.available)} ${m.unit}`
                  : '库存足够'
              }}</strong>
            </div>
          </div>
          <p class="formula-note">
            演示公式：需领量 = 承担需求 ÷ 模数 × (1 +
            损耗率)，按材料取料步长向上取整。取整余量不额外抵扣需求；正式计算口径待确认。
          </p>
          <el-alert v-if="issues.length" type="error" :closable="false" show-icon
            ><ul class="issue-list">
              <li v-for="issue in issues" :key="issue">{{ issue }}</li>
            </ul></el-alert
          >
        </section>

        <section v-if="current.events.length" class="trace-card">
          <h3>历史发料 / 调整记录 · 演示</h3>
          <div v-for="(event, i) in current.events" :key="i" class="trace-event">
            <span class="trace-dot"></span>
            <div>
              <b>{{ event.title }}</b>
              <p>{{ event.detail }}</p>
            </div>
          </div>
        </section>

        <div class="action-bar">
          <div>
            <b>{{
              summary.gap > 0 ? `部分领料 · 保留${fmt(summary.gap)}件缺口` : '本次需求已安排'
            }}</b
            ><small>{{
              draftSaved ? '演示草稿已保存在本页' : '仅预览本次方案，不写入业务数据'
            }}</small>
          </div>
          <div>
            <el-button @click="saveDraft">保存演示草稿</el-button
            ><el-button type="primary" :disabled="issues.length > 0" @click="confirmPreview"
              >确认并预览领料单</el-button
            >
          </div>
        </div>
      </main>
    </div>

    <el-dialog v-model="historyVisible" title="引用历史组合 · 演示" width="640px">
      <el-alert
        type="info"
        :closable="false"
        title="仅引用材料组合与参数，按本次剩余需求重算，不复制历史领料量。"
      />
      <div class="history-card">
        <el-tag effect="plain">BOM-DEMO · V3</el-tag>
        <h3>同规格面板 · 大板与卷材混用</h3>
        <p>B承担60%，模数2件/张；C承担40%，模数1件/米；损耗0%。</p>
        <p>依据：大板裁切主体，卷材补足余量。演示历史工单 MO-DEMO-H01。</p>
        <el-button type="primary" @click="reuseHistory">引用组合并按本次重算</el-button>
      </div>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      title="领料单预览 · 演示，未创建真实单据"
      width="90%"
      class="pick-preview"
    >
      <template v-if="snapshot">
        <div class="preview-header">
          <h2>生产领料单 · DEMO</h2>
          <p>MO-DEMO-2960 / 方案 V{{ snapshot.revision }} / {{ snapshot.title }}</p>
          <p>待仓库审核发料 · 本预览不会预占或扣减库存</p>
        </div>
        <el-table :data="snapshot.rows" border>
          <el-table-column prop="demandId" label="原需求" width="110" />
          <el-table-column prop="originalSpec" label="原始工程规格" min-width="200" />
          <el-table-column prop="materialName" label="实际材料" min-width="160" />
          <el-table-column prop="actualSpec" label="当前选择规格" min-width="200" />
          <el-table-column prop="coverage" label="抵扣需求（件）" width="125" />
          <el-table-column prop="issue" label="需领量" width="105" />
          <el-table-column label="实发批次" min-width="150"
            ><template #default>仓库发料时确定</template></el-table-column
          >
        </el-table>
        <p>
          本次抵扣 {{ snapshot.coverage }} 件，未安排缺口
          {{ snapshot.gap }} 件。不同单位的实际领料量不直接合计。
        </p>
        <el-alert
          type="info"
          :closable="false"
          title="此处展示确认时的规格与计算快照。关闭后可继续调整并生成下一版演示预览。"
        />
      </template>
      <template #footer
        ><el-button @click="previewVisible = false">返回调整方案</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  allocation,
  materialTotals,
  planned,
  quantity,
  rawQuantity,
  remaining,
  scenarios,
  validationIssues,
  type Allocation,
  type Demand,
  type Scenario,
} from './model'

defineOptions({ name: 'WorkOrderMaterialCalculationPreview' })
const router = useRouter()
const route = useRoute()
const sourceOrderNo = computed(() =>
  typeof route.query.orderNo === 'string' ? route.query.orderNo : ''
)
const copy = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const selectedId = ref(scenarios[0].id)
const edits = reactive<Record<string, Scenario>>({})
const revisions = reactive<Record<string, number>>({})
const savedDrafts = reactive<Record<string, string>>({})
const current = computed(() => edits[selectedId.value])
const revision = computed(() => (revisions[selectedId.value] || 0) + 1)
const historyVisible = ref(false)
const previewVisible = ref(false)
const snapshot = ref<{
  revision: number
  title: string
  coverage: number
  gap: number
  rows: {
    demandId: string
    originalSpec: string
    materialName: string
    actualSpec: string
    coverage: number
    issue: string
  }[]
} | null>(null)
function selectScenario(id: string) {
  if (!edits[id]) edits[id] = copy(scenarios.find((s) => s.id === id)!)
  selectedId.value = id
}
selectScenario(selectedId.value)
watch(
  () => route.query.orderId,
  () => {
    for (const key of Object.keys(edits)) delete edits[key]
    for (const key of Object.keys(revisions)) delete revisions[key]
    for (const key of Object.keys(savedDrafts)) delete savedDrafts[key]
    selectScenario(scenarios[0].id)
    snapshot.value = null
    previewVisible.value = false
  }
)
const workQuantity = computed(() => Math.max(...current.value.demands.map((d) => d.total)))
const totals = computed(() => materialTotals(current.value))
const usedMaterials = computed(() =>
  current.value.materials.filter((m) => totals.value[m.id] !== undefined)
)
const issues = computed(() => validationIssues(current.value))
const draftSaved = computed(() => savedDrafts[selectedId.value] === JSON.stringify(current.value))
const summary = computed(() =>
  current.value.demands.reduce(
    (sum, d) => ({
      total: sum.total + d.total,
      net: sum.net + d.issued - d.returned,
      pending: sum.pending + d.pending,
      planned: sum.planned + planned(d),
      gap: sum.gap + Math.max(0, remaining(d) - planned(d)),
    }),
    { total: 0, net: 0, pending: 0, planned: 0, gap: 0 }
  )
)
function fmt(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}
function material(a: Allocation) {
  return current.value.materials.find((m) => m.id === a.materialId)!
}
function changeMaterial(a: Allocation) {
  a.yield = material(a).yield
  a.reason = ''
}
function addAllocation(d: Demand) {
  const m =
    current.value.materials.find((item) => !d.allocations.some((a) => a.materialId === item.id)) ||
    current.value.materials[0]
  d.allocations.push(allocation(m.id, Math.max(0, remaining(d) - planned(d))))
}
function resetScenario() {
  edits[selectedId.value] = copy(scenarios.find((s) => s.id === selectedId.value)!)
  delete savedDrafts[selectedId.value]
  delete revisions[selectedId.value]
  ElMessage.info('当前场景已恢复初始演示数据')
}
function releasePending(d: Demand) {
  current.value.events.push({
    title: '演示取消待发占用',
    detail: `${d.id}：释放${d.pending}件需求，原记录保留；库存释放未接入，本页可用库存保持演示值。`,
  })
  d.pending = 0
}
function saveDraft() {
  savedDrafts[selectedId.value] = JSON.stringify(current.value)
  ElMessage.success('演示草稿已保存在本页，刷新后清除')
}
function reuseHistory() {
  for (const d of current.value.demands) {
    const rest = remaining(d)
    const b = Number((rest * 0.6).toFixed(2))
    d.allocations =
      rest > 0
        ? [
            allocation('B', b, '引用历史：大板裁切主体，按本次需求重算'),
            allocation(
              'C',
              Number((rest - b).toFixed(2)),
              '引用历史：卷材补足余量，按本次需求重算'
            ),
          ].filter((a) => a.coverage > 0)
        : []
  }
  historyVisible.value = false
  ElMessage.info('已按本次剩余需求重算，请检查库存与换料依据')
}
function confirmPreview() {
  if (issues.value.length) {
    ElMessage.warning('请先处理分配和库存问题')
    return
  }
  snapshot.value = {
    revision: revision.value,
    title: current.value.title,
    coverage: summary.value.planned,
    gap: summary.value.gap,
    rows: current.value.demands.flatMap((d) =>
      d.allocations.map((a) => ({
        demandId: d.id,
        originalSpec: d.originalSpec,
        materialName: material(a).name,
        actualSpec: material(a).spec,
        coverage: a.coverage,
        issue: `${fmt(quantity(a, material(a)))} ${material(a).unit}`,
      }))
    ),
  }
  revisions[selectedId.value] = revision.value
  previewVisible.value = true
}
</script>

<style scoped lang="scss">
.calculation-page {
  padding: 24px;
  background: #f4f6f9;
  color: #243247;
  min-height: 100%;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
h1 {
  font-size: 25px;
  margin: 10px 0 8px;
  display: flex;
  align-items: center;
  gap: 14px;
}
h2 {
  font-size: 18px;
  margin: 0 0 8px;
}
h3 {
  font-size: 15px;
  margin: 0;
}
p {
  line-height: 1.7;
}
.page-heading p {
  color: #718096;
  margin: 0;
}
.demo-notice {
  margin-bottom: 20px;
}
.workspace {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}
.scenario-panel {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  overflow: hidden;
  position: sticky;
  top: 16px;
}
.panel-caption {
  padding: 19px 16px;
  font-weight: 700;
  border-bottom: 1px solid #edf0f5;
  display: flex;
  justify-content: space-between;
}
.panel-caption span {
  font-size: 12px;
  color: #8491a3;
  font-weight: 400;
}
.scenario-button {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: start;
  border: 0;
  border-left: 3px solid transparent;
  background: #fff;
  text-align: left;
  padding: 15px 12px;
  cursor: pointer;
  color: #4a5667;
  font: inherit;
}
.scenario-button:hover {
  background: #f5f8fc;
}
.scenario-button.selected {
  background: #edf5ff;
  border-left-color: #2878da;
  color: #175db3;
}
.scenario-button strong {
  display: block;
  font-size: 13px;
  margin-bottom: 5px;
}
.scenario-button small {
  font-size: 11px;
  color: #7d899b;
}
.scenario-number {
  font-size: 11px;
  padding-top: 2px;
  color: #94a1b3;
}
.scenario-tip {
  margin: 8px 15px 18px;
  font-size: 12px;
  line-height: 1.7;
  color: #8a94a3;
}
.order-card,
.demand-card,
.stock-card,
.trace-card {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  margin-bottom: 18px;
  overflow: hidden;
}
.order-card {
  padding: 20px 22px;
}
.order-topline {
  display: flex;
  align-items: center;
  gap: 12px;
}
.order-topline > strong {
  font-size: 17px;
}
.order-topline > span:last-child {
  margin-left: auto;
  color: #6c7a8b;
  font-size: 13px;
}
.order-meta {
  display: grid;
  grid-template-columns: 2fr 1fr 1.3fr 1fr;
  gap: 14px;
  padding: 20px 0;
}
.order-meta small {
  display: block;
  color: #8390a2;
  margin-bottom: 8px;
}
.order-meta b {
  font-size: 13px;
  font-weight: 500;
}
.flow {
  display: flex;
  gap: 22px;
  padding-top: 16px;
  border-top: 1px solid #eef1f5;
  font-size: 12px;
  color: #929cac;
}
.flow b {
  color: #2878da;
}
.scenario-intro {
  padding: 2px 0 12px;
}
.scenario-intro p {
  color: #768195;
  font-size: 13px;
  margin: 0;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.summary-grid > div {
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
}
.summary-grid span {
  color: #7e899a;
  font-size: 12px;
}
.summary-grid strong {
  display: block;
  font-size: 26px;
  margin-top: 10px;
  font-variant-numeric: tabular-nums;
}
.summary-grid small {
  font-size: 11px;
  font-weight: 400;
  margin-left: 7px;
  color: #8b97a7;
}
.summary-grid .accent {
  background: #eff6ff;
  border-color: #cde1fb;
  color: #2268ba;
}
.summary-grid .shortage {
  background: #fff8ec;
  border-color: #f5dfb6;
  color: #b87316;
}
.demand-heading {
  padding: 18px 20px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.demand-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.demand-heading > span {
  color: #8a95a4;
  font-size: 12px;
}
.original-spec {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin: 0 20px;
  padding: 14px 16px;
  background: #f6f8fb;
  border-radius: 6px;
  font-size: 12px;
  color: #7a8697;
}
.original-spec b {
  color: #48556a;
}
.demand-ledger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 22px;
  padding: 14px 20px;
  color: #7c8796;
  font-size: 12px;
}
.demand-ledger b {
  color: #33445d;
  margin-left: 5px;
}
.allocation-title {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  border-top: 1px solid #edf0f4;
  font-size: 13px;
}
.allocation-title > span {
  color: #8a95a3;
  font-size: 12px;
}
.allocation-title .el-button {
  margin-left: auto;
}
.table-scroll {
  overflow-x: auto;
}
.allocation-table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
  font-size: 12px;
}
.allocation-table th {
  background: #fafbfd;
  padding: 12px 10px;
  text-align: left;
  color: #7e8897;
  font-weight: 500;
  white-space: nowrap;
}
.allocation-table td {
  padding: 13px 10px 7px;
  vertical-align: top;
}
.allocation-table th:first-child,
.allocation-table td:first-child {
  padding-left: 20px;
}
.allocation-table .material-cell {
  min-width: 235px;
}
.material-cell .el-select {
  width: 100%;
}
.allocation-table small {
  display: block;
  color: #8b95a5;
  margin-top: 7px;
  font-size: 11px;
}
.allocation-table .el-input-number {
  width: 94px;
}
.quantity-cell {
  white-space: nowrap;
}
.quantity-cell b {
  font-size: 20px;
  color: #246bbf;
}
.basis-row td {
  padding-bottom: 16px;
  border-bottom: 1px solid #edf0f4;
}
.basis-row td > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.basis-row span {
  white-space: nowrap;
  color: #8590a0;
}
.basis-row .el-input {
  max-width: 440px;
}
.basis-row small {
  margin: 0;
}
.demand-footer {
  display: flex;
  justify-content: space-between;
  padding: 15px 20px;
  font-size: 12px;
  color: #7c8796;
}
.demand-footer strong {
  color: #27816a;
}
.demand-footer b {
  color: #2878da;
}
.demand-footer .warning-text {
  color: #be7a1e;
}
.stock-card,
.trace-card {
  padding: 20px;
}
.section-heading {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}
.section-heading span {
  font-size: 12px;
  color: #8893a3;
}
.stock-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.stock-grid > div {
  border: 1px solid #e7edf3;
  background: #fafcfe;
  padding: 14px;
  border-radius: 6px;
  font-size: 12px;
}
.stock-grid span {
  display: block;
  color: #8894a2;
  margin: 8px 0;
}
.stock-grid strong {
  color: #26816a;
  font-weight: 500;
}
.stock-grid .stock-short {
  background: #fff6f3;
  border-color: #f5cdbf;
}
.stock-short strong {
  color: #ce6247;
}
.formula-note {
  font-size: 12px;
  color: #8994a4;
  margin: 14px 0 0;
}
.issue-list {
  padding-left: 18px;
  margin: 5px 0;
}
.stock-card .el-alert {
  margin-top: 12px;
}
.trace-event {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  font-size: 12px;
}
.trace-dot {
  height: 8px;
  width: 8px;
  border-radius: 50%;
  background: #98b8da;
  margin-top: 5px;
  flex-shrink: 0;
}
.trace-event p {
  color: #8a95a3;
  margin: 6px 0 0;
}
.action-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  background: #fff;
  padding: 16px 20px;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  box-shadow: 0 -5px 20px #283b5010;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.action-bar b {
  font-size: 13px;
}
.action-bar small {
  display: block;
  margin-top: 6px;
  color: #8a95a3;
  font-size: 11px;
}
.history-card {
  padding: 22px 0 4px;
}
.history-card h3 {
  margin-top: 14px;
}
.history-card p {
  color: #778394;
  font-size: 13px;
}
.preview-header {
  text-align: center;
  margin-bottom: 20px;
}
.preview-header p {
  color: #8691a1;
  font-size: 13px;
}
@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 195px minmax(0, 1fr);
    gap: 14px;
  }
  .calculation-page {
    padding: 16px;
  }
  .summary-grid > div {
    padding: 12px;
  }
  .order-meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 800px) {
  .page-heading {
    align-items: start;
    flex-direction: column;
  }
  h1 {
    font-size: 21px;
    flex-wrap: wrap;
  }
  .workspace {
    display: block;
  }
  .scenario-panel {
    position: static;
    display: flex;
    overflow-x: auto;
    margin-bottom: 16px;
  }
  .panel-caption,
  .scenario-tip {
    display: none;
  }
  .scenario-button {
    min-width: 210px;
  }
  .summary-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .flow {
    gap: 10px;
    flex-wrap: wrap;
  }
  .action-bar {
    position: static;
    align-items: start;
    flex-direction: column;
  }
  .allocation-title > span,
  .demand-heading > span {
    display: none;
  }
  .section-heading {
    align-items: start;
    flex-direction: column;
  }
}
</style>
