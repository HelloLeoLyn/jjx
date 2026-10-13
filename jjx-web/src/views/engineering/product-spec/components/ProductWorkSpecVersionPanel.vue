<template>
  <el-card shadow="never" class="wsv-card">
    <template #header>
      <div class="wsv-head">
        <span class="wsv-title">作业规范发布版本</span>
        <div>
          <el-button
            v-hasPermi="['product:work-spec:publish']"
            type="primary"
            size="small"
            :loading="publishing"
            @click="publish"
          >发布新版本</el-button>
          <el-button size="small" :disabled="versions.length < 2" @click="openCompare">版本比较</el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="versions" size="small" border v-loading="loading">
      <el-table-column prop="versionNo" label="版本号" width="100" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" v-bind="WorkSpecVersionStatus.getTagProps(row.status)">{{ WorkSpecVersionStatus.getLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeSummary" label="修订说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="publishedBy" label="发布人" width="100" align="center" />
      <el-table-column prop="publishedAt" label="发布时间" width="160" align="center" />
      <el-table-column label="操作" width="230" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
          <el-button v-if="showFullPage" link type="primary" @click="emit('openVersion', row.id)">整页查看</el-button>
          <el-button
            v-if="row.status === WorkSpecVersionStatus.RETIRED.value"
            v-hasPermi="['product:work-spec:publish']"
            link
            type="primary"
            @click="restoreVersion(row)"
          >恢复</el-button>
          <el-button
            v-else
            v-hasPermi="['product:work-spec:publish']"
            link
            type="danger"
            @click="retireVersion(row)"
          >停用</el-button>
        </template>
      </el-table-column>
      <template #empty>尚未发布版本</template>
    </el-table>

    <el-dialog v-model="detailVisible" :title="`版本详情 ${detailVersion?.versionNo || ''}`" width="820px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="版本号">{{ detailVersion?.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailVersion?.status ? WorkSpecVersionStatus.getLabel(detailVersion.status) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布人">{{ detailVersion?.publishedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detailVersion?.publishedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修订说明" :span="2">{{ detailVersion?.changeSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="清单哈希" :span="2">{{ detailVersion?.manifestHash || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailItems" size="small" border style="margin-top: 10px">
        <el-table-column label="条目" width="140">
          <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="图种 / 类别" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operationRef || '-' }}</template>
        </el-table-column>
        <el-table-column label="内容 / 引用" min-width="320">
          <template #default="{ row }"><span class="wsv-snap">{{ row.snapshotJson }}</span></template>
        </el-table-column>
        <el-table-column label="哈希" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contentHash ? row.contentHash.slice(0, 12) + '…' : '-' }}</template>
        </el-table-column>
      </el-table>
      <el-alert
        :type="detailDiff.changed ? 'warning' : 'success'"
        :closable="false"
        style="margin-top: 10px"
        :title="detailDiff.changed ? '当前工作态与本次发布已有差异' : '当前工作态与本次发布一致'"
      />
      <el-table v-if="detailDiff.changed" :data="detailDiff.fields" size="small" border style="margin-top: 8px">
        <el-table-column prop="label" label="字段" width="190" show-overflow-tooltip />
        <el-table-column label="发布版本" min-width="180">
          <template #default="{ row }"><span class="wsv-snap">{{ row.before || '（空）' }}</span></template>
        </el-table-column>
        <el-table-column label="当前工作态" min-width="180">
          <template #default="{ row }"><span class="wsv-snap">{{ row.after || '（空）' }}</span></template>
        </el-table-column>
      </el-table>
      <div class="wsv-tip">发布后内容不可改；此处为只读快照。BOM/工艺路线为「引用已批准版本」，工程图为「引用文件版本 + 哈希」。</div>
      <div class="wsv-tip">未纳入本次发布：{{ detailDiff.notIncluded.join('、') }}。</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="showFullPage && detailVersion"
          type="primary"
          @click="emit('openVersion', detailVersion.id)"
        >整页查看</el-button>
        <el-button
          v-if="detailVersion && detailVersion.status === WorkSpecVersionStatus.RETIRED.value"
          v-hasPermi="['product:work-spec:publish']"
          @click="restoreVersion(detailVersion)"
        >恢复</el-button>
        <el-button
          v-else-if="detailVersion"
          v-hasPermi="['product:work-spec:publish']"
          type="danger"
          @click="retireVersion(detailVersion)"
        >停用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="compareVisible" title="版本比较（已发布版本）" width="860px" append-to-body destroy-on-close>
      <div class="wsv-compare-bar">
        <el-select v-model="compareA" placeholder="版本A" style="width: 160px">
          <el-option v-for="v in versions" :key="v.id" :label="v.versionNo" :value="v.id" />
        </el-select>
        <span class="wsv-vs">对比</span>
        <el-select v-model="compareB" placeholder="版本B" style="width: 160px">
          <el-option v-for="v in versions" :key="v.id" :label="v.versionNo" :value="v.id" />
        </el-select>
        <el-button
          type="primary"
          :loading="comparing"
          :disabled="!compareA || !compareB || compareA === compareB"
          @click="runCompare"
        >比较</el-button>
      </div>
      <template v-if="compareResult">
        <el-alert
          :type="compareResult.changed ? 'warning' : 'success'"
          :closable="false"
          style="margin-top: 10px"
          :title="compareResult.changed ? `发现 ${compareResult.fields.length} 处字段差异` : '两个版本内容一致'"
        />
        <el-table v-if="compareResult.changed" :data="compareResult.fields" size="small" border style="margin-top: 8px">
          <el-table-column prop="label" label="字段" width="190" show-overflow-tooltip />
          <el-table-column label="版本A" min-width="220">
            <template #default="{ row }"><span class="wsv-snap">{{ row.before || '（空）' }}</span></template>
          </el-table-column>
          <el-table-column label="版本B" min-width="220">
            <template #default="{ row }"><span class="wsv-snap">{{ row.after || '（空）' }}</span></template>
          </el-table-column>
        </el-table>
        <div class="wsv-tip">仅比较规范正文快照；未纳入：{{ (compareResult.notIncluded || []).join('、') }}。</div>
      </template>
      <div v-else class="wsv-tip">选择两个已发布版本后点「比较」。A 为基准（左），B 为对照（右）。</div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productWorkSpecApi } from '@/api/product/workSpec'
import { WorkSpecVersionStatus } from '@/enums/product/workSpec'

const props = withDefaults(defineProps<{ productId: number; showFullPage?: boolean }>(), {
  showFullPage: false,
})
// 整页查看（dev-20261011-029）：由父页打开作业规范抽屉并定位到该版本（只读）
const emit = defineEmits<{ (e: 'openVersion', versionId: number): void }>()

const RESOURCE_LABELS: Record<string, string> = {
  SPEC_JSON: '规范正文快照',
  BOM: 'BOM',
  ROUTING: '工艺路线',
  DRAWING: '工程图/结构图',
  DOCSET_JSON: '完整快照',
  DOCSET_FILE: '快照文件保留',
}

const versions = ref<any[]>([])
const loading = ref(false)
const publishing = ref(false)
const detailVisible = ref(false)
const detailVersion = ref<any>(null)
const detailItems = ref<any[]>([])
const detailDiff = ref<{ changed: boolean; fields: any[]; notIncluded: string[] }>({
  changed: false,
  fields: [],
  notIncluded: ['客供资料', '领料单', '油墨', '实际检测记录'],
})

// 版本比较（dev-20261011-028）：任意两个已发布版本
const compareVisible = ref(false)
const compareA = ref<number | null>(null)
const compareB = ref<number | null>(null)
const comparing = ref(false)
const compareResult = ref<any>(null)

function openCompare() {
  compareA.value = versions.value[0]?.id ?? null
  compareB.value = versions.value[1]?.id ?? null
  compareResult.value = null
  compareVisible.value = true
}

async function runCompare() {
  if (!compareA.value || !compareB.value) return
  comparing.value = true
  try {
    const res: any = await productWorkSpecApi.compareVersions(compareA.value, compareB.value)
    if (res?.code === 200) compareResult.value = res.data
  } catch (e: any) {
    ElMessage.error(e?.message || '版本比较失败')
  } finally {
    comparing.value = false
  }
}

function resourceLabel(code: string) {
  return RESOURCE_LABELS[code] || code
}

async function load() {
  if (!props.productId) return
  loading.value = true
  try {
    const res: any = await productWorkSpecApi.listVersions(props.productId)
    versions.value = res?.data || []
  } catch {
    versions.value = []
  } finally {
    loading.value = false
  }
}

async function publish() {
  try {
    const { value } = await ElMessageBox.prompt('填写本次修订说明（可空）', '发布新版本', {
      inputPlaceholder: '如：冲型压力 80→85',
      inputValue: '',
      confirmButtonText: '发布',
      cancelButtonText: '取消',
    })
    publishing.value = true
    const res: any = await productWorkSpecApi.publishVersion(props.productId, value || undefined)
    if (res?.code === 200) {
      ElMessage.success(`已发布 ${res.data?.versionNo || ''}`)
      await load()
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

// 停用 / 恢复（dev-20261011-033）：只改 status、不硬删；停用后新工单/订单不能再绑该版，已绑工单不受影响
async function retireVersion(row: any) {
  try {
    await ElMessageBox.confirm(
      `停用版本 ${row.versionNo}？停用后新工单/新订单不能再绑定该版本；已绑定工单仍按原版本读取。`,
      '停用版本',
      { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const res: any = await productWorkSpecApi.retireVersion(row.id)
    if (res?.code === 200) {
      ElMessage.success('已停用')
      detailVisible.value = false
      await load()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '停用失败')
  }
}

async function restoreVersion(row: any) {
  try {
    const res: any = await productWorkSpecApi.restoreVersion(row.id)
    if (res?.code === 200) {
      ElMessage.success('已恢复')
      detailVisible.value = false
      await load()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '恢复失败')
  }
}

async function openDetail(row: any) {
  const res: any = await productWorkSpecApi.getVersion(row.id)
  if (res?.code === 200) {
    detailVersion.value = res.data?.version
    detailItems.value = res.data?.items || []
    detailVisible.value = true
  }
  try {
    const dres: any = await productWorkSpecApi.versionDiff(row.id)
    if (dres?.code === 200 && dres.data) {
      detailDiff.value = {
        changed: !!dres.data.changed,
        fields: dres.data.fields || [],
        notIncluded: dres.data.notIncluded || detailDiff.value.notIncluded,
      }
    }
  } catch {
    /* 差异预览失败不阻塞版本详情 */
  }
}

onMounted(load)
</script>

<style scoped>
.wsv-card { margin-bottom: 12px; }
.wsv-head { display: flex; align-items: center; justify-content: space-between; }
.wsv-title { font-weight: 600; }
.wsv-snap { white-space: pre-wrap; word-break: break-all; font-size: 12px; color: #555; }
.wsv-tip { margin-top: 10px; font-size: 12px; color: #909399; }
.wsv-compare-bar { display: flex; align-items: center; gap: 10px; }
.wsv-vs { color: #909399; font-size: 13px; }
</style>
