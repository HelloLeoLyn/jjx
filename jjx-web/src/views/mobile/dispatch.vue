<template>
  <div class="m-dispatch">
    <!-- 搜索 -->
    <div class="m-search-row">
      <input
        v-model="keyword"
        class="m-search-input"
        placeholder="工单号 / 任务号 / 工序"
        enterkeyhint="search"
        @keyup.enter="handleSearch"
      />
      <button class="m-search-btn" :disabled="loading" @click="handleSearch">查询</button>
    </div>

    <!-- 无权限（与后端 production:task:view 同键） -->
    <div v-if="!canView" class="m-empty">
      <div class="m-empty-icon">🔒</div>
      <div class="m-empty-text">当前账号没有派工权限</div>
      <div class="m-empty-sub">派工列表与 PC「派工管理」共用生产任务权限，请联系管理员授权</div>
    </div>

    <template v-else>
      <!-- 待派任务列表（后端按登录人 scope 返回：生产全局=全部，普通用户=本人持有） -->
      <div v-for="row in rows" :key="row.taskId" class="m-card">
        <div class="m-card-head">
          <span class="m-order-no">{{ row.orderNo || row.taskNo || '-' }}</span>
          <span class="m-status">{{ row.statusLabel || '' }}</span>
        </div>
        <div class="m-process">{{ row.processName || '-' }}</div>
        <div class="m-qty-row">
          <span class="m-qty-item">
            <b :class="{ 'is-zero': Number(row.remainingQuantity || 0) <= 0 }">{{ fmtQty(row.remainingQuantity) }}</b>
            <small>我的剩余</small>
          </span>
          <span class="m-qty-item">
            <b>{{ fmtQty(row.assignedQuantity) }}</b>
            <small>已分下级</small>
          </span>
          <span class="m-qty-item">
            <b>{{ fmtQty(row.taskQuantity) }}</b>
            <small>任务量</small>
          </span>
        </div>
        <div class="m-card-foot">
          <span class="m-holder">持有：{{ row.assigneeName || '未分配' }}</span>
          <button v-if="canAssign(row)" class="m-act m-act-primary" @click="openSheet(row)">派工</button>
          <span v-else-if="Number(row.remainingQuantity || 0) <= 0" class="m-act-hint">已派完</span>
        </div>
      </div>

      <div v-if="loading" class="m-loading">加载中…</div>
      <div v-else-if="!rows.length" class="m-empty">
        <div class="m-empty-icon">📭</div>
        <div class="m-empty-text">当前没有可派工的任务</div>
        <div class="m-empty-sub">这里只列你名下有剩余额度的任务（与 PC 派工管理同一口径）</div>
      </div>
      <button v-else-if="hasMore" class="m-more" @click="loadMore">加载更多</button>
    </template>

    <!-- 派工弹层（多选 + 逐人拆量 + 连续派工） -->
    <div v-if="sheetOpen" class="m-mask" @click.self="closeSheet">
      <div class="m-sheet">
        <div class="m-sheet-head">
          <span class="m-sheet-title">派工</span>
          <span class="m-sheet-close" @click="closeSheet">✕</span>
        </div>

        <div v-if="target" class="m-sheet-info">
          <div class="m-sheet-info-row">
            <span>{{ target.orderNo || '-' }}</span>
            <span>{{ target.processName || '-' }}</span>
          </div>
          <div class="m-sheet-info-row m-sheet-info-sub">
            <span>{{ target.taskNo || '' }}</span>
            <span>可派剩余：<b :class="{ 'is-zero': localRemain <= 0 }">{{ fmtQty(localRemain) }}</b></span>
          </div>
        </div>

        <!-- 本次已派（连续派工留痕，不关弹层即可继续派下一批） -->
        <div v-if="dispatched.length" class="m-dispatched">
          <div class="m-dispatched-head">
            <span>本次已派 <b>{{ dispatched.length }}</b> 人 / <b>{{ fmtQty(dispatchedTotal) }}</b></span>
            <span class="m-link" @click="closeSheet">完成</span>
          </div>
          <div class="m-chips">
            <span v-for="d in dispatched" :key="d.userId" class="m-chip">
              {{ d.name }} <b>{{ fmtQty(d.quantity) }}</b>
            </span>
          </div>
        </div>

        <div class="m-sheet-body">
          <input v-model="candKeyword" class="m-search-input m-sheet-search" placeholder="搜索姓名 / 车间 / 角色" />
          <div v-if="candidateLoading" class="m-loading">候选人加载中…</div>
          <div v-else-if="!filteredCandidates.length" class="m-loading">没有可派人员（与 PC 候选人树同源）</div>

          <template v-else>
            <!-- 本次分配：已选人员逐人拆量 -->
            <template v-if="selected.length">
              <div class="m-block-title">
                本次分配（{{ selected.length }} 人）
                <span class="m-block-hint">合计 {{ fmtQty(pickTotal) }} / 剩余 {{ fmtQty(localRemain) }}</span>
              </div>
              <div v-for="c in selected" :key="c.userId" class="m-pick-row">
                <div class="m-pick-name">
                  <span>{{ c.nickName || c.userName }}</span>
                  <small>{{ c.deptName || '未设部门' }}</small>
                </div>
                <div class="m-qty-stepper">
                  <button type="button" class="m-step" @click="bump(c.userId, -1)">−</button>
                  <input
                    v-model.number="qtyMap[c.userId]"
                    class="m-step-input"
                    type="number"
                    inputmode="decimal"
                    min="0"
                    :max="localRemain"
                  />
                  <button type="button" class="m-step" @click="bump(c.userId, 1)">＋</button>
                </div>
                <button type="button" class="m-pick-del" @click="toggle(c)">✕</button>
              </div>
              <div class="m-quick-row">
                <button type="button" class="m-mini-btn" @click="splitEven">平均分配</button>
                <button type="button" class="m-mini-btn" @click="fillRest">差额补齐</button>
                <button type="button" class="m-mini-btn" @click="clearPicks">清空</button>
              </div>
            </template>

            <!-- 候选人（点整行即勾选/取消，勾选后在上方填量） -->
            <div class="m-block-title">候选人（{{ filteredCandidates.length }}）</div>
            <div class="m-cand-list">
              <div
                v-for="c in filteredCandidates"
                :key="c.userId"
                class="m-cand"
                :class="{ picked: isPicked(c.userId) }"
                @click="toggle(c)"
              >
                <span class="m-cand-name">{{ c.nickName || c.userName }}</span>
                <span class="m-cand-meta">{{ c.deptName || '' }}{{ c.roleName ? ' · ' + c.roleName : '' }}</span>
                <span class="m-cand-check">{{ isPicked(c.userId) ? '✓' : '＋' }}</span>
              </div>
            </div>
          </template>
        </div>

        <div class="m-sheet-form">
          <input v-model="remark" class="m-search-input" placeholder="备注（可选，随本次提交）" />
          <div v-if="selected.length" class="m-summary">
            <span>可分配 <b>{{ fmtQty(localRemain) }}</b></span>
            <span>本次分配 <b>{{ fmtQty(pickTotal) }}</b></span>
            <span>分配后剩余 <b :class="{ 'is-err': afterAssign < 0 }">{{ fmtQty(afterAssign) }}</b></span>
          </div>
          <div v-if="hasZeroQty" class="m-form-err">有人员数量为 0，请填写或移除</div>
          <div v-else-if="afterAssign < 0" class="m-form-err">本次分配超出可派剩余 {{ fmtQty(-afterAssign) }}</div>
        </div>

        <div class="m-sheet-actions">
          <button class="m-act" @click="closeSheet">{{ dispatched.length ? '完成' : '取消' }}</button>
          <button class="m-act m-act-primary" :disabled="!canSubmit || submitting" @click="submit">
            {{ submitLabel }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { assignTask, getTaskCandidates, getTaskTreePage } from '@/api/production/task'
import type { TaskCandidate, TaskTreeRow } from '@/types/production/task'

/**
 * 移动端派工（多选 + 逐人拆量 + 连续派工）——与 PC「派工管理」同源同权限，不另造规则：
 *  0) 入口：首页「全部功能」九宫格，不在底部 TabBar；可见性 = production:task:assign
 *     （= 后端 POST /production/tasks/{taskId}/assign 的 @SaCheckPermission 同键；
 *       操作工 32 只有 production:task:view，没有 assign → 看不到入口，直达 URL 也只看到无权限提示）
 *  1) 列表可见性 = production:task:view，与 GET /production/tasks/page 的 @SaCheckPermission 同键
 *  2) 列表范围由后端决定（生产全局=全部；普通用户=本人持有），前端不做角色/部门过滤
 *  3) 可派判定 = 后端投影 row.allowedActions 含 'ASSIGN' 且 row.remainingQuantity > 0
 *  4) 候选人 = GET /production/tasks/{taskId}/candidates 的责任树，只取 selectable=true 后摊平
 *  5) 提交 = POST /production/tasks/{taskId}/assign，items 支持多条（后端逐条校验、合计不超剩余、整单事务）
 *
 * 移动端交互口径（2026-09-23 定，A+C 混合方案）：
 *  - A：候选人扁平列表点行勾选 → 「本次分配」区逐人填量（数字键盘，支持小数）→「平均分配 / 差额补齐」快捷
 *       + 吸底实时汇总（可分配 / 本次分配 / 分配后剩余，超限标红禁用）
 *  - C：提交成功后**不关闭弹层**，本地扣减剩余、「本次已派」留痕，可继续派下一批；关闭时才回读列表
 *  - 数量精度 2 位（后端 BigDecimal，与 PC el-input-number precision=2 同口径）
 */
const userStore = useUserStore()

const canView = computed(() => userStore.hasPermission('production:task:assign'))

const rows = ref<TaskTreeRow[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 20
const hasMore = computed(() => rows.value.length < total.value)

function num(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
function round2(v: number): number {
  return Math.round(v * 100) / 100
}
function fmtQty(v?: number | null): string {
  return String(round2(num(v)))
}

async function load(reset = false): Promise<void> {
  if (!canView.value) return
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const res: any = await getTaskTreePage({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    const page = res?.data || {}
    const list: TaskTreeRow[] = page.records || []
    rows.value = reset ? list : rows.value.concat(list)
    total.value = Number(page.total || 0)
  } catch (e: any) {
    if (reset) rows.value = []
    ElMessage.error(e?.message || '派工列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  void load(true)
}

function loadMore(): void {
  if (loading.value || !hasMore.value) return
  pageNum.value += 1
  void load()
}

/** 后端投影说了算：allowedActions 含 ASSIGN 且仍有剩余额度 */
function canAssign(row: TaskTreeRow): boolean {
  return (row.allowedActions || []).includes('ASSIGN') && Number(row.remainingQuantity || 0) > 0
}

// ==================== 派工弹层 ====================
interface DispatchedRow {
  userId: number
  name: string
  quantity: number
}

const sheetOpen = ref(false)
const target = ref<TaskTreeRow | null>(null)
const candidates = ref<TaskCandidate[]>([])
const candidateLoading = ref(false)
const candKeyword = ref('')
/** 已勾选人员（有序：勾选顺序即分配表顺序） */
const pickedIds = ref<number[]>([])
/** userId → 分配数量 */
const qtyMap = ref<Record<number, number>>({})
const remark = ref('')
const submitting = ref(false)
/** 弹层内的可派剩余（提交后本地扣减，关闭弹层时回读列表校准） */
const localRemain = ref(0)
/** 本次会话已派留痕（连续派工） */
const dispatched = ref<DispatchedRow[]>([])

const selected = computed(() =>
  pickedIds.value
    .map((id) => candidates.value.find((c) => c.userId === id))
    .filter((c): c is TaskCandidate => !!c)
)
const pickTotal = computed(() => pickedIds.value.reduce((s, id) => s + num(qtyMap.value[id]), 0))
const dispatchedTotal = computed(() => dispatched.value.reduce((s, d) => s + num(d.quantity), 0))
const afterAssign = computed(() => round2(localRemain.value - pickTotal.value))
const hasZeroQty = computed(() => selected.value.some((c) => !(num(qtyMap.value[c.userId]) > 0)))
const canSubmit = computed(
  () =>
    selected.value.length > 0 &&
    !hasZeroQty.value &&
    pickTotal.value > 0 &&
    pickTotal.value <= localRemain.value
)
const submitLabel = computed(() => {
  if (submitting.value) return '提交中…'
  if (!selected.value.length) return '确认派工'
  const word = dispatched.value.length ? '继续派工' : '确认派工'
  return `${word} ${fmtQty(pickTotal.value)}`
})

const filteredCandidates = computed(() => {
  const k = candKeyword.value.trim().toLowerCase()
  if (!k) return candidates.value
  return candidates.value.filter((c) =>
    `${c.nickName || ''}${c.userName || ''}${c.deptName || ''}${c.roleName || ''}`.toLowerCase().includes(k)
  )
})

/** 责任树摊平：只保留后端标记 selectable=true 的节点（根节点=自己，不可派） */
function flattenSelectable(nodes: TaskCandidate[], out: TaskCandidate[] = []): TaskCandidate[] {
  for (const n of nodes || []) {
    if (n.selectable) out.push(n)
    if (n.children?.length) flattenSelectable(n.children, out)
  }
  return out
}

function isPicked(userId: number): boolean {
  return pickedIds.value.includes(userId)
}

/** 点候选人行：勾选（首个默认填满剩余，其余默认填未分配额度）/ 取消 */
function toggle(c: TaskCandidate): void {
  const i = pickedIds.value.indexOf(c.userId)
  if (i >= 0) {
    pickedIds.value.splice(i, 1)
    delete qtyMap.value[c.userId]
    return
  }
  const unalloc = round2(Math.max(0, localRemain.value - pickTotal.value))
  pickedIds.value.push(c.userId)
  qtyMap.value[c.userId] = pickedIds.value.length === 1 ? localRemain.value : unalloc
}

function bump(userId: number, delta: number): void {
  qtyMap.value[userId] = round2(Math.max(0, num(qtyMap.value[userId]) + delta))
}

/** 平均分配：余数落在最后一人，保证合计 == 可派剩余 */
function splitEven(): void {
  const n = pickedIds.value.length
  if (!n || localRemain.value <= 0) return
  const base = Math.floor((localRemain.value / n) * 100) / 100
  let acc = 0
  pickedIds.value.forEach((id, idx) => {
    const q = idx === n - 1 ? round2(localRemain.value - acc) : base
    qtyMap.value[id] = q
    acc = round2(acc + q)
  })
}

/** 差额补齐：把剩余额度补到最后一个人身上（前面的人保持不动） */
function fillRest(): void {
  const n = pickedIds.value.length
  if (!n) return
  const lastId = pickedIds.value[n - 1]
  const others = pickedIds.value.slice(0, n - 1).reduce((s, id) => s + num(qtyMap.value[id]), 0)
  const rest = round2(localRemain.value - others)
  if (rest <= 0) {
    ElMessage.warning('其余人员已占满可派剩余')
    return
  }
  qtyMap.value[lastId] = rest
}

function clearPicks(): void {
  pickedIds.value = []
  qtyMap.value = {}
}

async function openSheet(row: TaskTreeRow): Promise<void> {
  target.value = row
  sheetOpen.value = true
  candKeyword.value = ''
  clearPicks()
  dispatched.value = []
  remark.value = ''
  localRemain.value = num(row.remainingQuantity)
  candidateLoading.value = true
  try {
    const res: any = await getTaskCandidates(row.taskId)
    candidates.value = flattenSelectable((res?.data || []) as TaskCandidate[])
    // 只有一个候选人时直接勾选并填满剩余，少两次点击
    if (candidates.value.length === 1) {
      const only = candidates.value[0]
      pickedIds.value = [only.userId]
      qtyMap.value[only.userId] = localRemain.value
    }
  } catch (e: any) {
    candidates.value = []
    ElMessage.error(e?.message || '候选人加载失败')
  } finally {
    candidateLoading.value = false
  }
}

function closeSheet(): void {
  const dispatchedAny = dispatched.value.length > 0
  sheetOpen.value = false
  target.value = null
  candidates.value = []
  clearPicks()
  dispatched.value = []
  remark.value = ''
  // 派过才回读，避免无谓请求；回读同时校准剩余与列表状态
  if (dispatchedAny) void load(true)
}

async function submit(): Promise<void> {
  const row = target.value
  if (!row || !canSubmit.value) return
  const items = selected.value.map((c) => ({
    assigneeId: c.userId,
    quantity: num(qtyMap.value[c.userId]),
  }))
  const batchTotal = pickTotal.value
  submitting.value = true
  try {
    await assignTask(row.taskId, { items, remark: remark.value.trim() || undefined })
    // 连续派工：留痕 + 本地扣减，不关闭弹层
    selected.value.forEach((c) => {
      const q = num(qtyMap.value[c.userId])
      const found = dispatched.value.find((d) => d.userId === c.userId)
      if (found) found.quantity = round2(found.quantity + q)
      else dispatched.value.push({ userId: c.userId, name: c.nickName || c.userName, quantity: q })
    })
    localRemain.value = round2(Math.max(0, localRemain.value - batchTotal))
    clearPicks()
    remark.value = ''
    if (localRemain.value > 0) {
      ElMessage.success(`派工成功（合计 ${fmtQty(batchTotal)}），可继续派工`)
    } else {
      ElMessage.success('派工成功，已派完')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '派工失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void load(true)
})

// 硬刷新/收藏直达时权限快照是空的（由 MobileLayout 用 /sessions/current 恢复），
// 恢复后 canView 翻真再取一次列表，避免首屏误显示「没有派工权限」
watch(canView, (v) => {
  if (v && !rows.value.length) void load(true)
})
</script>

<style scoped>
.m-dispatch {
  padding: 10px 12px 16px;
}
.m-search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.m-search-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
  outline: none;
}
.m-search-btn {
  height: 38px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: var(--doc-theme, #2b5aa7);
  color: #fff;
  font-size: 14px;
}
.m-card {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.m-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.m-order-no {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.m-status {
  font-size: 12px;
  color: var(--doc-theme, #2b5aa7);
  background: rgba(43, 90, 167, 0.1);
  border-radius: 10px;
  padding: 2px 8px;
}
.m-process {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
}
.m-qty-row {
  display: flex;
  gap: 20px;
  margin-top: 10px;
}
.m-qty-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.m-qty-item b {
  font-size: 17px;
  color: #303133;
}
.m-qty-item b.is-zero {
  color: #c0c4cc;
}
.m-qty-item small {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.m-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
.m-holder {
  font-size: 12px;
  color: #909399;
}
.m-act-hint {
  font-size: 12px;
  color: #c0c4cc;
}
.m-act {
  height: 34px;
  padding: 0 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  font-size: 14px;
}
.m-act-primary {
  background: var(--doc-theme, #2b5aa7);
  border-color: var(--doc-theme, #2b5aa7);
  color: #fff;
}
.m-act:disabled {
  opacity: 0.5;
}
.m-loading {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 14px 0;
}
.m-empty {
  text-align: center;
  padding: 48px 16px;
  color: #909399;
}
.m-empty-icon {
  font-size: 34px;
}
.m-empty-text {
  margin-top: 10px;
  font-size: 15px;
  color: #606266;
}
.m-empty-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #a8abb2;
}
.m-more {
  width: 100%;
  height: 38px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  font-size: 14px;
}
/* ===== 弹层 ===== */
.m-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 2000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.m-sheet {
  width: 100%;
  max-width: 560px;
  max-height: 88vh;
  background: #fff;
  border-radius: 14px 14px 0 0;
  display: flex;
  flex-direction: column;
  padding: 12px 14px calc(14px + env(safe-area-inset-bottom));
}
.m-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.m-sheet-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.m-sheet-close {
  font-size: 16px;
  color: #909399;
  padding: 4px 8px;
}
.m-sheet-info {
  margin: 8px 0;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 8px;
}
.m-sheet-info-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #303133;
}
.m-sheet-info-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.m-sheet-info-sub b.is-zero {
  color: #c0c4cc;
}
/* 本次已派（连续派工留痕） */
.m-dispatched {
  background: rgba(43, 90, 167, 0.06);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.m-dispatched-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #303133;
}
.m-link {
  color: var(--doc-theme, #2b5aa7);
  font-size: 13px;
  padding: 2px 4px;
}
.m-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.m-chip {
  font-size: 12px;
  color: #303133;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 2px 10px;
}
.m-sheet-body {
  flex: 1;
  overflow-y: auto;
  min-height: 120px;
}
.m-sheet-search {
  width: 100%;
  box-sizing: border-box;
}
.m-block-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.m-block-hint {
  font-size: 11px;
  font-weight: 400;
  color: #909399;
}
/* 本次分配：逐人拆量 */
.m-pick-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f7f9fc;
  border-radius: 10px;
  margin-bottom: 6px;
}
.m-pick-name {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.m-pick-name span {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-pick-name small {
  font-size: 11px;
  color: #909399;
}
.m-qty-stepper {
  display: flex;
  align-items: center;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.m-step {
  width: 34px;
  height: 34px;
  border: none;
  background: #fff;
  color: #606266;
  font-size: 16px;
  line-height: 1;
}
.m-step:active {
  background: #f2f3f5;
}
.m-step-input {
  width: 58px;
  height: 34px;
  border: none;
  border-left: 1px solid #ebeef5;
  border-right: 1px solid #ebeef5;
  text-align: center;
  font-size: 15px;
  color: #303133;
  outline: none;
}
.m-pick-del {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  color: #c0c4cc;
  font-size: 14px;
}
.m-quick-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}
.m-mini-btn {
  flex: 1;
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  font-size: 12px;
}
/* 候选人 */
.m-cand-list {
  margin-top: 2px;
}
.m-cand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 8px;
  border-bottom: 1px solid #f5f7fa;
}
.m-cand.picked {
  background: rgba(43, 90, 167, 0.08);
  border-radius: 8px;
}
.m-cand-name {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}
.m-cand-meta {
  flex: 1;
  font-size: 12px;
  color: #909399;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-cand-check {
  width: 20px;
  text-align: center;
  color: var(--doc-theme, #2b5aa7);
  font-size: 15px;
  font-weight: 700;
}
/* 底部表单 + 汇总 */
.m-sheet-form {
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
.m-summary {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.m-summary b {
  color: #303133;
}
.m-summary b.is-err {
  color: #f56c6c;
}
.m-form-err {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}
.m-sheet-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.m-sheet-actions .m-act {
  flex: 1;
  height: 42px;
}
</style>
