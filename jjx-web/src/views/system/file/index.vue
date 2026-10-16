<template>
  <div class="app-container" v-loading="loading">
    <!-- 预警卡片 -->
    <el-alert
      v-if="stats.dailyAlert"
      title="文件增长异常预警：今日新增超过阈值（sys_config: file.alert.daily_size，默认500MB），疑似大文件批量上传，请及时清理"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="stats.totalAlert"
      title="存储空间预警：磁盘总占用超过阈值（sys_config: file.alert.total_percent，默认80%），请清理或迁移文件"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- 统计卡片 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-label">上传总大小</div>
            <div class="stat-value">{{ formatSize(stats.totalSize) }}</div>
            <div class="stat-sub">{{ stats.totalCount ?? 0 }} 个文件</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-label">今日新增</div>
            <div class="stat-value" :class="{ 'is-alert': stats.dailyAlert }">
              {{ formatSize(stats.todayAddedSize) }}
            </div>
            <div class="stat-sub">{{ stats.todayAddedCount ?? 0 }} 个文件</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-label">磁盘占用率</div>
            <div class="stat-value" :class="{ 'is-alert': stats.totalAlert }">
              {{ stats.usedPercent ?? 0 }}%
            </div>
            <el-progress
              :percentage="Math.min(100, Math.round(stats.usedPercent ?? 0))"
              :stroke-width="8"
              :color="(stats.totalAlert ? '#f56c6c' : '#409eff')"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-label">操作</div>
            <div style="margin-top: 6px">
              <el-button size="small" type="primary" plain :loading="backingDaily" @click="runDaily" v-hasPermi="['system:file:backup']">
                日备份
              </el-button>
              <el-button size="small" type="warning" plain :loading="backingWeekly" @click="runWeekly" v-hasPermi="['system:file:backup']">
                周全量
              </el-button>
              <el-button size="small" :loading="checking" @click="runCheck" v-hasPermi="['system:file:check']">预警检查</el-button>
            </div>
            <div style="margin-top: 8px; display: flex; gap: 6px">
              <el-input
                v-model="migratePath"
                size="small"
                placeholder="迁移源目录，如 D:/jjx"
                style="flex: 1"
              />
              <el-button size="small" type="success" plain :loading="migrating" @click="runMigrate" v-hasPermi="['system:file:migrate']">
                迁移产品文件
              </el-button>
            </div>
            <div class="stat-sub" style="margin-top: 6px">备份目录：{{ stats.backupPath }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 大文件 TopN + 类型统计 -->
    <el-row :gutter="12">
      <el-col :span="14">
        <el-card shadow="never" header="大文件 Top20">
          <el-table :data="stats.topFiles || []" size="small" border max-height="420">
            <el-table-column type="index" label="#" width="45" />
            <el-table-column label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="scope">
                <el-link type="primary" :href="downloadUrl(scope.row.id)" :underline="false" target="_blank">
                  {{ scope.row.fileName || '-' }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="类型" prop="bizType" width="100" />
            <el-table-column label="大小" width="90" align="right">
              <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="上传人" prop="createBy" width="90" />
            <el-table-column label="时间" width="140">
              <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="scope">
                <el-button link type="danger" :icon="Delete" @click="onDelete(scope.row)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" header="按类型统计" style="margin-bottom: 12px">
          <el-table :data="stats.bizTypeStats || []" size="small" border max-height="180">
            <el-table-column label="业务类型" prop="bizType" min-width="120" />
            <el-table-column label="数量" prop="count" width="70" align="right" />
            <el-table-column label="大小" width="100" align="right">
              <template #default="scope">{{ formatSize(scope.row.size) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" header="近30天每日新增">
          <el-table :data="stats.dailyStats || []" size="small" border max-height="210">
            <el-table-column label="日期" prop="date" width="110" />
            <el-table-column label="数量" prop="count" width="70" align="right" />
            <el-table-column label="大小" min-width="100" align="right">
              <template #default="scope">{{ formatSize(scope.row.size) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <!-- 待清理（到期且无有效引用） -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>待清理（到期且无有效引用；清理后进回收站，可恢复）</span>
          <span>
            <el-tag size="small" type="warning" style="margin-right: 8px">{{ cleanupCandidates.length }} 个</el-tag>
            <el-button size="small" type="danger" plain :loading="purging" @click="runPurgeFiles">仅删文件（留记录）</el-button>
            <el-button size="small" :loading="previewing" @click="loadCleanupCandidates">刷新预演</el-button>
          </span>
        </div>
      </template>
      <el-table :data="cleanupCandidates" size="small" border max-height="240">
        <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.fileName || `附件#${scope.row.attachmentId}` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" prop="uploadState" />
        <el-table-column label="大小" width="100" align="right">
          <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="保留期截止" width="160">
          <template #default="scope">{{ formatTime(scope.row.deadline) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!cleanupCandidates.length" description="没有到期且无有效引用的附件" :image-size="50" />
    </el-card>
    <!-- 过期草稿（未保存单据的待保存附件草稿行；>保留天数未落定即视为放弃） -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>过期草稿（未保存单据上传的待保存附件；超过 {{ DRAFT_RETENTION_DAYS }} 天仍未落定即视为放弃）</span>
          <el-button size="small" type="danger" plain :loading="cleaningDrafts" @click="runCleanupDrafts">清理过期草稿</el-button>
        </div>
      </template>
      <div class="stat-sub">
        清理会把过期草稿行软删，并把其临时文件一并标记到期（随后在「待清理」区释放）。清理后该草稿无法再恢复。
      </div>
    </el-card>
    <!-- 回收站 -->
    <el-card shadow="never" header="回收站（软删除附件，可恢复；物理删除已禁用）" style="margin-top: 12px">
      <div style="margin-bottom: 8px; display: flex; justify-content: flex-end">
        <el-button size="small" :loading="cleaning" @click="runCleanExpired">清理过期附件（移入回收站）</el-button>
      </div>
      <el-table :data="recycleList" size="small" border max-height="300">
        <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <el-link type="primary" :href="downloadUrl(scope.row.id)" :underline="false" target="_blank">
              {{ scope.row.fileName || '-' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="bizType" width="110" />
        <el-table-column label="类别" prop="category" width="90" />
        <el-table-column label="大小" width="90" align="right">
          <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="删除时间" width="150">
          <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="scope">
            <el-button link type="success" size="small" @click="onRestore(scope.row)">恢复</el-button>
            <el-tooltip content="当前策略：只支持移入回收站，物理删除已禁用" placement="top">
              <span><el-button link type="danger" size="small" disabled>彻底删除</el-button></span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!recycleList.length" description="回收站为空" :image-size="50" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { h, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { fileApi } from '@/api/system/file'
import { attachmentApi } from '@/api/system/attachment'
import { attachmentRefApi } from '@/api/system/attachmentRef'

const loading = ref(false)
const backingDaily = ref(false)
const backingWeekly = ref(false)
const checking = ref(false)
const stats = ref<any>({})
const migratePath = ref('')
const migrating = ref(false)
const recycleList = ref<any[]>([])
const cleaning = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const res: any = await fileApi.stats()
    stats.value = (res as any)?.data || {}
  } catch {
    stats.value = {}
  } finally {
    loading.value = false
  }
}

async function runDaily() {
  backingDaily.value = true
  try {
    const res: any = await fileApi.backupDaily()
    const d = (res as any)?.data
    ElMessage.success(`日备份完成：成功 ${d?.success}/${d?.total} 个，${formatSize(d?.totalBytes)}`)
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '日备份失败')
  } finally {
    backingDaily.value = false
  }
}

async function runWeekly() {
  backingWeekly.value = true
  try {
    const res: any = await fileApi.backupWeekly()
    ElMessage.success(`周全量备份完成：${(res as any)?.data?.success ?? 0} 个文件`)
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '周全量备份失败')
  } finally {
    backingWeekly.value = false
  }
}

async function runCheck() {
  checking.value = true
  try {
    const res: any = await fileApi.alertCheck()
    const r = (res as any)?.data
    if (r?.dailyAlert || r?.totalAlert) {
      ElMessage.warning('存在预警，已发送站内通知（每日同类限一次）')
    } else {
      ElMessage.success('容量正常，未触发预警')
    }
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '预警检查失败')
  } finally {
    checking.value = false
  }
}

async function runMigrate() {
  if (!migratePath.value.trim()) {
    ElMessage.warning('请输入迁移源目录（如 D:/jjx，产品目录名=产品编码）')
    return
  }
  migrating.value = true
  try {
    const res: any = await fileApi.migrateProduct(migratePath.value.trim())
    const d = (res as any)?.data
    const skipped = (d?.skippedProducts || []).join('、')
    ElMessageBox.alert(
      `迁移完成：产品 ${d?.productCount} 个，文件 ${d?.fileCount} 个，成功 ${d?.successCount} 个，跳过 ${d?.skippedFiles} 个`
        + (skipped ? `\n\n⚠️ 未建档产品（先建产品再重跑）：${skipped}` : ''),
      '迁移结果',
      { confirmButtonText: '知道了' }
    )
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '迁移失败')
  } finally {
    migrating.value = false
  }
}

async function loadRecycle() {
  try {
    const res: any = await attachmentApi.recycleList()
    recycleList.value = (res as any)?.data || []
  } catch {
    recycleList.value = []
  }
}

async function onRestore(row: any) {
  try {
    await attachmentApi.restore(row.id)
    ElMessage.success('已恢复')
    loadRecycle()
  } catch (e: any) {
    ElMessage.error(e?.message || '恢复失败')
  }
}

/** A 口径清理：预演 → 确认 → 标记删除（进回收站，可恢复）。游标分页推进，最多 5 页。 */
const CLEANUP_MAX_PAGES = 5
const cleanupCandidates = ref<any[]>([])
const previewing = ref(false)
const purging = ref(false)
/** 过期草稿清理（dev-20261016-043）：与 TEMP 附件到期口径一致，默认 7 天。 */
const DRAFT_RETENTION_DAYS = 7
const cleaningDrafts = ref(false)

/**
 * 清理过期草稿行（未保存单据上传的待保存附件）：>N 天未落定即软删，
 * 并把其 TEMP 文件一并标记到期（随后在「待清理」区释放）。不可恢复，故先二次确认。
 */
async function runCleanupDrafts() {
  try {
    await ElMessageBox.confirm(
      `将清理超过 ${DRAFT_RETENTION_DAYS} 天仍未落定的草稿行（未保存单据上传的待保存附件），`
        + '并把其临时文件一并标记到期、随后在「待清理」区释放。清理后这些草稿无法再恢复。',
      '清理过期草稿',
      { confirmButtonText: '确认清理', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  cleaningDrafts.value = true
  try {
    const res: any = await attachmentRefApi.draftsCleanup(DRAFT_RETENTION_DAYS)
    const count = Number(res?.data ?? 0)
    ElMessage.success(count ? `已清理 ${count} 条过期草稿行` : '没有超过保留期的草稿行')
    loadCleanupCandidates()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '清理过期草稿失败')
  } finally {
    cleaningDrafts.value = false
  }
}

/** 预演只读：返回「到期且无有效引用」的行（含 fileName/size/deadline），用于页面待清理区。 */
async function fetchEligibleRows(): Promise<any[]> {
  const rows: any[] = []
  let afterId = 0
  for (let page = 0; page < CLEANUP_MAX_PAGES; page++) {
    const res: any = await attachmentRefApi.cleanupPreview(afterId, 100)
    const data: any[] = (res as any)?.data || []
    if (!data.length) break
    data.filter((r) => r.eligible).forEach((r) => rows.push(r))
    if (data.length < 100) break
    afterId = Number(data[data.length - 1].attachmentId) || afterId
  }
  return rows
}

async function loadCleanupCandidates() {
  previewing.value = true
  try {
    cleanupCandidates.value = await fetchEligibleRows()
  } catch {
    cleanupCandidates.value = []
  } finally {
    previewing.value = false
  }
}

/** 留痕式释放：删磁盘文件、保留数据库记录（状态 PURGED；不可恢复、不可下载）。 */
async function runPurgeFiles() {
  purging.value = true
  try {
    const candidates = await fetchEligibleRows()
    if (!candidates.length) {
      cleanupCandidates.value = []
      ElMessage.info('没有可释放的附件')
      return
    }
    const text =
      `将删除以下 ${candidates.length} 个文件的磁盘文件；数据库记录、上传人与 sha256 保留（不可恢复、不可下载）：\n` +
      candidates.map((r) => `・${r.fileName || `附件#${r.attachmentId}`}（${formatSize(r.fileSize)}）`).join('\n')
    await ElMessageBox.confirm(
      h('div', { style: 'white-space: pre-line; max-height: 320px; overflow: auto' }, text),
      '仅删文件（留记录）',
      { confirmButtonText: '确认删除文件', cancelButtonText: '取消', type: 'warning' }
    )
    let afterId = 0
    let purged = 0
    for (let page = 0; page < CLEANUP_MAX_PAGES; page++) {
      const res: any = await attachmentRefApi.purgeApply(afterId, 100)
      const d: any = (res as any)?.data || {}
      purged += Number(d.purged ?? 0)
      const next = Number(d.nextAfterId ?? afterId)
      if (!Number(d.scanned ?? 0) || next === afterId) break
      afterId = next
    }
    ElMessage.success(`已释放 ${purged} 个文件（记录保留）`)
    loadCleanupCandidates()
    loadStats()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '释放失败')
  } finally {
    purging.value = false
  }
}

async function runCleanExpired() {
  cleaning.value = true
  try {
    const candidates = await fetchEligibleRows()
    if (!candidates.length) {
      cleanupCandidates.value = []
      ElMessage.info('没有到期且无有效引用的附件需要清理')
      return
    }
    await ElMessageBox.confirm(
      `将把 ${candidates.length} 个到期且无有效引用的附件移入回收站（可恢复，磁盘文件保留）。继续？`,
      '清理过期附件',
      { confirmButtonText: '移入回收站', cancelButtonText: '取消', type: 'warning' }
    )
    let afterId = 0
    let recycled = 0
    for (let page = 0; page < CLEANUP_MAX_PAGES; page++) {
      const res: any = await attachmentRefApi.cleanupApply(afterId, 100)
      const d: any = (res as any)?.data || {}
      recycled += Number(d.recycled ?? 0)
      const next = Number(d.nextAfterId ?? afterId)
      if (!Number(d.scanned ?? 0) || next === afterId) break
      afterId = next
    }
    ElMessage.success(`已移入回收站 ${recycled} 个附件（可在列表中恢复）`)
    loadRecycle()
    loadCleanupCandidates()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '清理失败')
  } finally {
    cleaning.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除文件「${row.fileName || '-'}」？文件将同时从磁盘删除。`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await attachmentApi.remove(row.id)
    ElMessage.success('删除成功')
    loadStats()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

function formatSize(size: number | null | undefined): string {
  if (!size) return '0B'
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)}MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)}GB`
}

function formatTime(t: string | null | undefined): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  loadStats()
  loadRecycle()
  loadCleanupCandidates()
})
</script>

<style scoped>
.stat-card {
  padding: 4px 0;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.stat-value.is-alert {
  color: #f56c6c;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
