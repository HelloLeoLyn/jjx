<template>
  <el-dialog
    v-model="visible"
    :title="`版本对比 · ${productName || ''}`"
    width="1000px"
    append-to-body
    destroy-on-close
  >
    <div v-loading="loading" style="min-height: 300px">
      <template v-if="versions.length >= 2">
        <!-- 版本选择 -->
        <div class="cmp-selectors">
          <div>
            <span class="cmp-label">旧版本：</span>
            <el-select v-model="oldVersionId" size="small" style="width: 220px" placeholder="选择旧版本">
              <el-option
                v-for="v in versions" :key="v.routingId"
                :label="`${v.version || v.routingVersion}（${v.approveStatusName || ''}${v.isCurrent === 1 ? '·当前' : ''}）`"
                :value="v.routingId"
              />
            </el-select>
          </div>
          <div>
            <span class="cmp-label">新版本：</span>
            <el-select v-model="newVersionId" size="small" style="width: 220px" placeholder="选择新版本">
              <el-option
                v-for="v in versions" :key="v.routingId"
                :label="`${v.version || v.routingVersion}（${v.approveStatusName || ''}${v.isCurrent === 1 ? '·当前' : ''}）`"
                :value="v.routingId"
              />
            </el-select>
          </div>
          <el-button type="primary" size="small" :disabled="!oldVersionId || !newVersionId || oldVersionId === newVersionId" @click="doCompare">
            对比
          </el-button>
        </div>

        <!-- 汇总信息 -->
        <div v-if="oldVersion && newVersion" class="cmp-summary">
          <el-tag size="small" type="info">{{ oldLabel }}</el-tag>
          <span class="cmp-arrow">→</span>
          <el-tag size="small" type="success">{{ newLabel }}</el-tag>
          <span class="cmp-stats">
            工序：{{ oldVersion.processCount || 0 }} → {{ newVersion.processCount || 0 }}（{{ diff.added.length }} 增 / {{ diff.removed.length }} 删 / {{ diff.changed.length }} 改）
          </span>
        </div>

        <!-- 差异明细 -->
        <div v-if="compared" class="cmp-body">
          <!-- 新增 -->
          <div v-if="diff.added.length" class="cmp-block">
            <div class="cmp-block-title added">➕ 新增工序（{{ diff.added.length }}）</div>
            <el-table :data="diff.added" size="small" border>
              <el-table-column label="顺序" prop="processOrder" width="60" align="center" />
              <el-table-column label="工序名称" prop="processName" min-width="140" />
              <el-table-column label="类别" prop="processCategory" width="100" />
              <el-table-column label="人工工时" width="90" align="right">
                <template #default="scope">{{ scope.row.customLaborHours || scope.row.standardLaborHours || 0 }}</template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 删除 -->
          <div v-if="diff.removed.length" class="cmp-block">
            <div class="cmp-block-title removed">➖ 删除工序（{{ diff.removed.length }}）</div>
            <el-table :data="diff.removed" size="small" border>
              <el-table-column label="顺序" prop="processOrder" width="60" align="center" />
              <el-table-column label="工序名称" prop="processName" min-width="140" />
              <el-table-column label="类别" prop="processCategory" width="100" />
              <el-table-column label="人工工时" width="90" align="right">
                <template #default="scope">{{ scope.row.customLaborHours || scope.row.standardLaborHours || 0 }}</template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 修改 -->
          <div v-if="diff.changed.length" class="cmp-block">
            <div class="cmp-block-title changed">✏️ 修改工序（{{ diff.changed.length }}）</div>
            <el-table :data="diff.changed" size="small" border>
              <el-table-column label="工序" prop="processName" min-width="130" />
              <el-table-column label="变更项" min-width="260">
                <template #default="scope">
                  <div v-for="(c, i) in scope.row.changes" :key="i" class="change-item">
                    <span class="change-field">{{ c.field }}：</span>
                    <span class="change-old">{{ c.old }}</span>
                    <span class="change-arrow">→</span>
                    <span class="change-new">{{ c.new }}</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-empty v-if="!diff.added.length && !diff.removed.length && !diff.changed.length" description="两个版本工序完全一致" :image-size="60" />
        </div>
      </template>
      <el-empty v-else-if="!loading" description="该产品不足两个版本，无法对比" :image-size="80" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { productRouteApi } from '@/api/product/routing'

/**
 * 工艺路线版本对比弹窗（DEV-768）
 * 选两个版本 → 按工序构成对比：新增/删除/修改（工时/类别/下标等）
 */
const props = defineProps<{
  modelValue: boolean
  productId?: number | null
  productName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const versions = ref<any[]>([])
const oldVersionId = ref<number | null>(null)
const newVersionId = ref<number | null>(null)
const compared = ref(false)
const oldVersion = ref<any>(null)
const newVersion = ref<any>(null)

const oldLabel = computed(() => oldVersion.value?.version || oldVersion.value?.routingVersion || '-')
const newLabel = computed(() => newVersion.value?.version || newVersion.value?.routingVersion || '-')

// 差异结果
const diff = ref<{ added: any[]; removed: any[]; changed: any[] }>({ added: [], removed: [], changed: [] })

// 打开时加载该产品所有版本
watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    loading.value = true
    compared.value = false
    oldVersion.value = null
    newVersion.value = null
    try {
      if (!props.productId) {
        versions.value = []
        return
      }
      const res = await productRouteApi.getProductRouteVersions(props.productId)
      versions.value = (res.data || []).slice().sort((a: any, b: any) =>
        (a.version || a.routingVersion || '').localeCompare(b.version || b.routingVersion || '', undefined, { numeric: true })
      )
      // 默认选最新两个
      if (versions.value.length >= 2) {
        newVersionId.value = versions.value[versions.value.length - 1].routingId
        oldVersionId.value = versions.value[versions.value.length - 2].routingId
        doCompare()
      } else {
        oldVersionId.value = null
        newVersionId.value = null
      }
    } catch {
      versions.value = []
    } finally {
      loading.value = false
    }
  }
)

// 对比
function doCompare() {
  if (!oldVersionId.value || !newVersionId.value || oldVersionId.value === newVersionId.value) {
    ElMessage.warning('请选择两个不同的版本')
    return
  }
  const oldV = versions.value.find((v) => v.routingId === oldVersionId.value)
  const newV = versions.value.find((v) => v.routingId === newVersionId.value)
  if (!oldV || !newV) return
  oldVersion.value = oldV
  newVersion.value = newV

  const oldItems = (oldV.items || []).map((it: any) => normalize(it))
  const newItems = (newV.items || []).map((it: any) => normalize(it))
  const oldMap = new Map(oldItems.map((it: any) => [keyOf(it), it]))
  const newMap = new Map(newItems.map((it: any) => [keyOf(it), it]))

  const added: any[] = []
  const removed: any[] = []
  const changed: any[] = []

  // 新增：在新不在旧
  newItems.forEach((it: any) => {
    if (!oldMap.has(keyOf(it))) added.push(it)
  })
  // 删除：在旧不在新
  oldItems.forEach((it: any) => {
    if (!newMap.has(keyOf(it))) removed.push(it)
  })
  // 修改：两边都有但字段不同
  newItems.forEach((it: any) => {
    const oldIt = oldMap.get(keyOf(it))
    if (!oldIt) return
    const changes: { field: string; old: string; new: string }[] = []
    const fields = ['processCategory', 'customLaborHours', 'standardLaborHours', 'indexNumber', 'description']
    fields.forEach((f: string) => {
      const ov = (oldIt as any)[f]
      const nv = (it as any)[f]
      if (String(ov ?? '') !== String(nv ?? '')) {
        changes.push({ field: fieldLabel(f), old: fmt(ov), new: fmt(nv) })
      }
    })
    if (changes.length) changed.push({ ...it, changes })
  })

  diff.value = { added, removed, changed }
  compared.value = true
}

function normalize(it: any) {
  return {
    processId: it.processId,
    stdProcessId: it.stdProcessId,
    processName: it.processName,
    processOrder: it.processOrder,
    processCategory: it.processCategory || '',
    customLaborHours: it.customLaborHours,
    standardLaborHours: it.standardLaborHours,
    indexNumber: it.indexNumber,
    description: it.description || '',
  }
}

function keyOf(it: any): string {
  return String(it.stdProcessId || it.processId || it.processName || '')
}

function fieldLabel(f: string): string {
  const map: Record<string, string> = {
    processCategory: '类别', customLaborHours: '人工工时', standardLaborHours: '标准工时',
    indexNumber: '下标', description: '说明',
  }
  return map[f] || f
}

function fmt(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  return String(v)
}
</script>

<style scoped>
.cmp-selectors {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cmp-label {
  font-size: 13px;
  color: #606266;
}
.cmp-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cmp-arrow {
  color: #909399;
}
.cmp-stats {
  font-size: 13px;
  color: #606266;
  margin-left: 12px;
}
.cmp-block {
  margin-bottom: 14px;
}
.cmp-block-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
}
.cmp-block-title.added {
  color: #67c23a;
}
.cmp-block-title.removed {
  color: #f56c6c;
}
.cmp-block-title.changed {
  color: #e6a23c;
}
.change-item {
  font-size: 12px;
  line-height: 1.8;
}
.change-field {
  color: #606266;
  font-weight: 600;
}
.change-old {
  color: #f56c6c;
  text-decoration: line-through;
  margin: 0 4px;
}
.change-new {
  color: #67c23a;
  font-weight: 600;
}
.change-arrow {
  color: #909399;
}
</style>
