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

    <!-- 派工弹层 -->
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
            <span>可派剩余：<b>{{ fmtQty(remain) }}</b></span>
          </div>
        </div>

        <div class="m-sheet-body">
          <input v-model="candKeyword" class="m-search-input m-sheet-search" placeholder="搜索姓名 / 车间 / 角色" />
          <div v-if="candidateLoading" class="m-loading">候选人加载中…</div>
          <div v-else-if="!filteredCandidates.length" class="m-loading">没有可派人员（与 PC 候选人树同源）</div>
          <div v-else class="m-cand-list">
            <div
              v-for="c in filteredCandidates"
              :key="c.userId"
              class="m-cand"
              :class="{ picked: pickedId === c.userId }"
              @click="pickCandidate(c)"
            >
              <span class="m-cand-name">{{ c.nickName || c.userName }}</span>
              <span class="m-cand-meta">{{ c.deptName || '' }}{{ c.roleName ? ' · ' + c.roleName : '' }}</span>
              <span class="m-cand-check">{{ pickedId === c.userId ? '✓' : '' }}</span>
            </div>
          </div>
        </div>

        <div class="m-sheet-form">
          <label class="m-form-label">数量（不超过 {{ fmtQty(remain) }}）</label>
          <div class="m-qty-input-row">
            <input v-model.number="quantity" type="number" min="0" :max="remain" class="m-num-input" />
            <button class="m-mini-btn" @click="quantity = remain">全部</button>
          </div>
          <label class="m-form-label">备注（可选）</label>
          <input v-model="remark" class="m-search-input" placeholder="派工说明" />
          <div v-if="quantity > remain" class="m-form-err">超出可派剩余，不能提交</div>
        </div>

        <div class="m-sheet-actions">
          <button class="m-act" @click="closeSheet">取消</button>
          <button class="m-act m-act-primary" :disabled="!canSubmit || submitting" @click="submit">
            {{ submitting ? '提交中…' : '确认派工' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { assignTask, getTaskCandidates, getTaskTreePage } from '@/api/production/task'
import type { TaskCandidate, TaskTreeRow } from '@/types/production/task'

/**
 * 移动端派工（简易版）——与 PC「派工管理」同源同权限，不另造规则：
 *  0) 入口：首页「全部功能」九宫格，不在底部 TabBar；可见性 = production:task:assign
 *     （= 后端 POST /production/tasks/{taskId}/assign 的 @SaCheckPermission 同键；
 *       操作工 32 只有 production:task:view，没有 assign → 看不到入口，直达 URL 也只看到无权限提示）
 *  1) 列表可见性 = production:task:view，与 GET /production/tasks/page 的 @SaCheckPermission 同键
 *  2) 列表范围由后端决定（生产全局=全部；普通用户=本人持有），前端不做角色/部门过滤
 *  3) 可派判定 = 后端投影 row.allowedActions 含 'ASSIGN' 且 row.remainingQuantity > 0
 *  4) 候选人 = GET /production/tasks/{taskId}/candidates 的责任树，只取 selectable=true
 *  5) 提交 = POST /production/tasks/{taskId}/assign，后端兜底鉴权
 * 第一版不做：收回 / 退回 / 任务链下钻 / 多人拆量（items 固定一条）
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

function fmtQty(v?: number | null): string {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? String(n) : '0'
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
const sheetOpen = ref(false)
const target = ref<TaskTreeRow | null>(null)
const candidates = ref<TaskCandidate[]>([])
const candidateLoading = ref(false)
const candKeyword = ref('')
const pickedId = ref<number | null>(null)
const quantity = ref(0)
const remark = ref('')
const submitting = ref(false)

const remain = computed(() => Number(target.value?.remainingQuantity || 0))
const canSubmit = computed(() => !!pickedId.value && quantity.value > 0 && quantity.value <= remain.value)
const picked = computed(() => candidates.value.find((c) => c.userId === pickedId.value) || null)

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

async function openSheet(row: TaskTreeRow): Promise<void> {
  target.value = row
  sheetOpen.value = true
  candKeyword.value = ''
  pickedId.value = null
  quantity.value = Number(row.remainingQuantity || 0)
  remark.value = ''
  candidateLoading.value = true
  try {
    const res: any = await getTaskCandidates(row.taskId)
    candidates.value = flattenSelectable((res?.data || []) as TaskCandidate[])
    if (candidates.value.length === 1) pickedId.value = candidates.value[0].userId
  } catch (e: any) {
    candidates.value = []
    ElMessage.error(e?.message || '候选人加载失败')
  } finally {
    candidateLoading.value = false
  }
}

function closeSheet(): void {
  sheetOpen.value = false
  target.value = null
  candidates.value = []
}

function pickCandidate(c: TaskCandidate): void {
  pickedId.value = c.userId
}

async function submit(): Promise<void> {
  const row = target.value
  const who = picked.value
  if (!row || !who || !canSubmit.value) return
  try {
    await ElMessageBox.confirm(
      `确认派工？${row.orderNo || ''} ${row.processName || ''}，派给 ${who.nickName || who.userName} ${fmtQty(
        quantity.value
      )} 件（可派剩余 ${fmtQty(remain.value)}）`,
      '派工确认',
      { type: 'warning', confirmButtonText: '确认派工', cancelButtonText: '取消' }
    )
  } catch {
    return // 取消
  }
  submitting.value = true
  try {
    await assignTask(row.taskId, {
      items: [{ assigneeId: who.userId, quantity: quantity.value }],
      remark: remark.value.trim() || undefined,
    })
    ElMessage.success('派工成功')
    closeSheet()
    await load(true)
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
.m-sheet-body {
  flex: 1;
  overflow-y: auto;
  min-height: 120px;
}
.m-sheet-search {
  width: 100%;
  box-sizing: border-box;
}
.m-cand-list {
  margin-top: 8px;
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
}
.m-cand-check {
  width: 18px;
  text-align: center;
  color: var(--doc-theme, #2b5aa7);
  font-weight: 700;
}
.m-sheet-form {
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
.m-form-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin: 8px 0 4px;
}
.m-qty-input-row {
  display: flex;
  gap: 8px;
}
.m-num-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 15px;
  outline: none;
}
.m-mini-btn {
  height: 38px;
  padding: 0 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
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
