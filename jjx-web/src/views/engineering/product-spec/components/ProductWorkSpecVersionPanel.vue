<template>
  <el-card shadow="never" class="wsv-card">
    <template #header>
      <div class="wsv-head">
        <span class="wsv-title">{{ draft.canWrite.value ? '作业规范定版' : '作业规范' }}</span>
        <div>
          <el-button
            v-if="draft.canWrite.value"
            v-hasPermi="['product:work-spec:publish']"
            type="primary"
            size="small"
            :loading="publishing"
            :disabled="blocked || draft.busy.value || !draft.ready.value || !draft.canWrite.value"
            :title="draft.isOwner.value ? '' : '只有当前制定负责人可以定版'"
            @click="publish"
            >定版</el-button
          >
          <el-button size="small" :disabled="versions.length < 2" @click="openCompare"
            >版本比较</el-button
          >
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="draft.readOnlyReason.value"
      :title="draft.readOnlyReason.value"
      type="info"
      :closable="false"
      show-icon
      class="wsv-tip"
    />
    <el-alert
      v-if="draft.conflict.value"
      :title="draft.error.value"
      type="warning"
      :closable="false"
    >
      <el-button :disabled="draft.busy.value || publishing" @click="recover(false)"
        >比较并重新应用</el-button
      >
      <el-button :disabled="draft.busy.value || publishing" @click="recover(true)"
        >放弃并重读</el-button
      >
    </el-alert>
    <el-table :data="versions" size="small" border v-loading="loading">
      <el-table-column prop="versionNo" label="版本号" width="100" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" v-bind="WorkSpecVersionStatus.getTagProps(row.status)">{{
            WorkSpecVersionStatus.getLabel(row.status)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="changeSummary"
        label="修订说明"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column prop="publishedBy" label="定版人" width="100" align="center" />
      <el-table-column prop="publishedAt" label="定版时间" width="160" align="center" />
      <el-table-column label="操作" width="330" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
          <el-button
            v-hasPermi="['product:edit']"
            link
            type="primary"
            :disabled="
              blocked || draft.busy.value || draft.dirty.value || publishing || !canStartUpgrade
            "
            :title="canStartUpgrade ? '' : '只有当前制定负责人或工程主管可以升级'"
            @click="upgrade(row)"
            >从此版升级</el-button
          >
          <el-button v-if="showFullPage" link type="primary" @click="emit('openVersion', row.id)"
            >整页查看</el-button
          >
          <el-button
            v-if="row.status === WorkSpecVersionStatus.RETIRED.value"
            v-hasPermi="['product:work-spec:publish']"
            link
            type="primary"
            @click="restoreVersion(row)"
            >恢复</el-button
          >
          <el-button
            v-else
            v-hasPermi="['product:work-spec:publish']"
            link
            type="danger"
            @click="retireVersion(row)"
            >停用</el-button
          >
        </template>
      </el-table-column>
      <template #empty>尚未定版</template>
    </el-table>

    <el-dialog
      v-model="detailVisible"
      :title="`版本详情 ${detailVersion?.versionNo || ''}`"
      width="820px"
      append-to-body
      destroy-on-close
    >
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="版本号">{{ detailVersion?.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          detailVersion?.status ? WorkSpecVersionStatus.getLabel(detailVersion.status) : '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="定版人">{{
          detailVersion?.publishedBy || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="定版时间">{{
          detailVersion?.publishedAt || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="修订说明" :span="2">{{
          detailVersion?.changeSummary || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="清单哈希" :span="2">{{
          detailVersion?.manifestHash || '-'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailItems" size="small" border style="margin-top: 10px">
        <el-table-column label="条目" width="140">
          <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="图种 / 类别" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operationRef || '-' }}</template>
        </el-table-column>
        <el-table-column label="内容 / 引用" min-width="320">
          <template #default="{ row }"
            ><span class="wsv-snap">{{ row.snapshotJson }}</span></template
          >
        </el-table-column>
        <el-table-column label="哈希" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{
            row.contentHash ? row.contentHash.slice(0, 12) + '…' : '-'
          }}</template>
        </el-table-column>
      </el-table>
      <el-alert
        :type="detailDiff.changed ? 'warning' : 'success'"
        :closable="false"
        style="margin-top: 10px"
        :title="detailDiff.changed ? '当前工作态与本次发布已有差异' : '当前工作态与本次发布一致'"
      />
      <el-table
        v-if="detailDiff.changed"
        :data="detailDiff.fields"
        size="small"
        border
        style="margin-top: 8px"
      >
        <el-table-column prop="label" label="字段" width="190" show-overflow-tooltip />
        <el-table-column label="发布版本" min-width="180">
          <template #default="{ row }"
            ><span class="wsv-snap">{{ row.before || '（空）' }}</span></template
          >
        </el-table-column>
        <el-table-column label="当前工作态" min-width="180">
          <template #default="{ row }"
            ><span class="wsv-snap">{{ row.after || '（空）' }}</span></template
          >
        </el-table-column>
      </el-table>
      <div class="wsv-tip">
        定版后内容不可改；完整快照包含客供资料、BOM、工艺路线与工程图。旧版仅保留当时已收录的正文和引用，缺项不会自动补入。
      </div>
      <div class="wsv-tip">未纳入本次发布：{{ detailDiff.notIncluded.join('、') }}。</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="showFullPage && detailVersion"
          type="primary"
          @click="emit('openVersion', detailVersion.id)"
          >整页查看</el-button
        >
        <el-button
          v-if="detailVersion && detailVersion.status === WorkSpecVersionStatus.RETIRED.value"
          v-hasPermi="['product:work-spec:publish']"
          @click="restoreVersion(detailVersion)"
          >恢复</el-button
        >
        <el-button
          v-else-if="detailVersion"
          v-hasPermi="['product:work-spec:publish']"
          type="danger"
          @click="retireVersion(detailVersion)"
          >停用</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="compareVisible"
      title="版本比较（已定版版本）"
      width="860px"
      append-to-body
      destroy-on-close
    >
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
          >比较</el-button
        >
      </div>
      <template v-if="compareResult">
        <el-alert
          :type="compareResult.changed ? 'warning' : 'success'"
          :closable="false"
          style="margin-top: 10px"
          :title="
            compareResult.changed
              ? `发现 ${compareResult.fields.length} 处字段差异`
              : '两个版本内容一致'
          "
        />
        <el-table
          v-if="compareResult.changed"
          :data="compareResult.fields"
          size="small"
          border
          style="margin-top: 8px"
        >
          <el-table-column prop="label" label="字段" width="190" show-overflow-tooltip />
          <el-table-column label="版本A" min-width="220">
            <template #default="{ row }"
              ><span class="wsv-snap">{{ row.before || '（空）' }}</span></template
            >
          </el-table-column>
          <el-table-column label="版本B" min-width="220">
            <template #default="{ row }"
              ><span class="wsv-snap">{{ row.after || '（空）' }}</span></template
            >
          </el-table-column>
        </el-table>
        <div class="wsv-tip">
          仅比较规范正文快照；未纳入：{{ (compareResult.notIncluded || []).join('、') }}。
        </div>
      </template>
      <div v-else class="wsv-tip">
        选择两个已定版版本后点「比较」。A 为基准（左），B 为对照（右）。
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, toRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productWorkSpecApi } from '@/api/product/workSpec'
import { WorkSpecVersionStatus } from '@/enums/product/workSpec'
import { useWorkSpecAttachmentSession } from './useWorkSpecAttachmentSession'
import { parseDocsetSnapshot, detectFileKind } from './versionDocset'
import { useUserStore } from '@/store/modules/user'
import { usePermission } from '@/composables/usePermission'

const props = withDefaults(
  defineProps<{ productId: number; showFullPage?: boolean; blocked?: boolean }>(),
  {
    showFullPage: false,
  }
)
// 整页查看（dev-20261011-029）：由父页打开作业规范抽屉并定位到该版本（只读）
const emit = defineEmits<{
  (e: 'openVersion', versionId: number): void
  (e: 'updated'): void
  (e: 'busy', value: boolean): void
}>()
const draft = useWorkSpecAttachmentSession(toRef(props, 'productId'), ref(''))
const user = useUserStore()
const { hasRole } = usePermission()
// 制定任务锁（dev-20261020-013）：定版仅负责人；升级由负责人或工程主管发起。
const canStartUpgrade = computed(
  () => draft.isOwner.value || (user.hasPermission('product:edit') && hasRole('engineering:all'))
)

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
  notIncluded: [],
})

// 版本比较（dev-20261011-028）：任意两个已定版版本
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
  if (props.blocked || publishing.value || draft.busy.value || !draft.ready.value) return
  if (!draft.canWrite.value) {
    ElMessage.warning(draft.readOnlyReason.value + '；只有当前制定负责人可以定版')
    return
  }
  publishing.value = true
  try {
    // 保存失败必须中断；所有附件和正文共用服务端返回的revision。
    if (
      !user.hasPermission('product:edit') &&
      (!draft.spec.value.draftManaged || draft.dirty.value)
    )
      throw new Error('请先由有编辑权限的人员保存规范草稿')
    const saved = user.hasPermission('product:edit')
      ? await draft.save()
      : { data: draft.spec.value }
    draft.confirming.value = true
    const final = saved.data
    const listed = await productWorkSpecApi.listVersions(props.productId)
    const baseId = final.basedOnVersionId || final.finalizedVersionId || listed.data?.[0]?.id
    let previous: { attachmentId: number; fileName: string; displayName?: string }[] = []
    if (baseId) {
      const response = await productWorkSpecApi.getVersion(baseId)
      const complete = response.data?.items?.find(
        (item: any) => item.resourceType === 'DOCSET_JSON'
      )
      if (!complete) throw new Error('基版缺少完整快照，无法确认附件差异')
      previous = parseDocsetSnapshot(complete.snapshotJson).files
    }
    const current = final.draftFiles || []
    const label = (file: { displayName?: string | null; fileName: string }) =>
      file.displayName || file.fileName
    const currentIds = new Set(current.map((file: any) => file.attachmentId))
    const oldIds = new Set(previous.map((file) => file.attachmentId))
    const added = current.filter((file: any) => !oldIds.has(file.attachmentId)).map(label)
    const removed = previous.filter((file) => !currentIds.has(file.attachmentId)).map(label)
    const selected = new Map(
      (final.draftAttachments || []).map((file: any) => [file.attachmentId, file.print !== false])
    )
    const printable = current.filter(
      (file: any) =>
        selected.get(file.attachmentId) && detectFileKind(file.fileName, file.fileType) !== 'other'
    )
    // managed定版服务端将PRINT_GUIDE固化到print，其余工程资料固化到atlas；栏目内保持manifest顺序。
    const printOrder = [
      ['印刷规范', printable.filter((file: any) => file.documentType === 'PRINT_GUIDE')],
      ['工程图集', printable.filter((file: any) => file.documentType !== 'PRINT_GUIDE')],
    ]
      .map(
        ([section, files]) =>
          `${section}：${(files as typeof current).map(label).join(' → ') || '无'}`
      )
      .join('；')
    const { value } = await ElMessageBox.prompt(
      `${baseId ? '相对基版' : '首次定版，全新清单'}：新增 ${added.length}（${added.join('、') || '无'}），移除 ${removed.length}（${removed.join('、') || '无'}）。栏目内打印顺序：${printOrder}。修订说明可留空。`,
      '规范定版',
      {
        inputPlaceholder: '如：冲型压力 80→85',
        inputValue: '',
        confirmButtonText: '定版',
        cancelButtonText: '取消',
      }
    )
    const res: any = await productWorkSpecApi.publishVersion(
      props.productId,
      value || undefined,
      saved.data.revision
    )
    if (res?.code === 200) {
      ElMessage.success(`已定版 ${res.data?.versionNo || ''}`)
      await load()
      draft.confirming.value = false
      await draft.load()
      emit('updated')
    }
  } catch (e: any) {
    if (/revision|冲突|其他入口修改|草稿已变化/i.test(e?.message || '')) {
      draft.conflict.value = true
      draft.error.value = '草稿已被其他入口修改，请比较后重新应用或放弃重读。'
    }
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '定版失败')
  } finally {
    draft.confirming.value = false
    publishing.value = false
  }
}

async function recover(discard: boolean) {
  try {
    await draft.recover(discard)
    emit('updated')
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close')
      ElMessage.error(cause instanceof Error ? cause.message : '冲突恢复失败，改动已保留')
  }
}
watch(publishing, (value) => emit('busy', value), { flush: 'sync' })

async function upgrade(row: any) {
  if (props.blocked || publishing.value || draft.busy.value || draft.dirty.value) return
  try {
    await ElMessageBox.confirm(
      `从 ${row.versionNo} 复制正文、附件和负责人为新草稿？`,
      '升级作业规范'
    )
    await draft.upgrade(row.id)
    ElMessage.success('已生成可编辑草稿')
    emit('updated')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      ElMessage.error(error instanceof Error ? error.message : '升级失败')
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
    const complete = detailItems.value.some((item) => item.resourceType === 'DOCSET_JSON')
    detailDiff.value = {
      changed: false,
      fields: [],
      notIncluded: [...(complete ? [] : ['客供资料']), '领料单', '油墨', '实际检测记录'],
    }
    detailVisible.value = true
  }
  try {
    const dres: any = await productWorkSpecApi.versionDiff(row.id)
    if (dres?.code === 200 && dres.data) {
      detailDiff.value = {
        changed: !!dres.data.changed,
        fields: dres.data.fields || [],
        notIncluded: detailDiff.value.notIncluded,
      }
    }
  } catch {
    /* 差异预览失败不阻塞版本详情 */
  }
}

/** 版本发布/定版进行中为 busy（dev-20261021-025）；发布没有本地草稿态。 */
const guardDetails = computed(() => ({
  busy: publishing.value,
  dirty: false,
  reasons: publishing.value ? ['版本正在发布/定版'] : [],
}))
defineExpose({
  guardState: () => guardDetails.value,
  saveIfDirty: async () => !publishing.value,
  discardIfDirty: () => {},
})

onMounted(load)
watch(() => props.productId, load)
</script>

<style scoped>
.wsv-card {
  margin-bottom: 12px;
}
.wsv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wsv-title {
  font-weight: 600;
}
.wsv-snap {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  color: #555;
}
.wsv-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
.wsv-compare-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.wsv-vs {
  color: #909399;
  font-size: 13px;
}
</style>
