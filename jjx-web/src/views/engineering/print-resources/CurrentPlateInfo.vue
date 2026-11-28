<template>
  <div class="current-plate-info">
    <template v-if="compact">
      <span v-if="sourcesError" class="error">{{ sourcesError }}</span>
      <span v-else-if="!sources.length" class="muted">未记录菲林样式</span>
      <div v-for="source in sources" :key="source.filmId" class="source-summary">
        <strong>{{ source.filmNo || '片号未记录' }}</strong>
        <div>{{ source.styles.map(style => style.name || style.styleNo).join(' / ') || '未记录样式' }}</div>
      </div>
    </template>
    <template v-else>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="网版编号">{{ plate.plateNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="网版状态"><el-tag :type="ScreenPlateStatusEnum.getTagProps(plate.status).type">{{ ScreenPlateStatusEnum.getLabel(plate.status) || plate.status || '未知' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="印刷位置">{{ plate.position || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制版人">{{ plate.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="放行人">{{ plate.releasedBy || (plate.status === ScreenPlateStatusEnum.RELEASED.value ? '未记录' : '尚未放行') }}</el-descriptions-item>
        <el-descriptions-item label="放行时间">{{ plate.releasedTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="plate.status === ScreenPlateStatusEnum.PENDING.value" class="notice">该网版待检验，暂不可用于工艺路线；框体正常不代表网版已放行。</p>
      <p v-else-if="plate.status === ScreenPlateStatusEnum.FAILED.value" class="error">该网版检验不合格，暂不可用于工艺路线。</p>
      <div class="section-head">菲林与印刷样式</div>
      <el-alert v-if="sourcesError" :title="sourcesError" type="error" :closable="false" />
      <el-empty v-else-if="!sources.length" description="该网版未记录菲林样式" :image-size="48" />
      <div v-for="source in sources" :key="source.filmId" class="source-card">
        <strong>{{ source.filmNo || '片号未记录' }}</strong>
        <span class="source-version">{{ [source.artworkNo, source.versionNo].filter(Boolean).join(' / ') }}</span>
        <div v-for="style in source.styles" :key="style.styleNo" class="style-row">
          <span>{{ style.name || style.styleNo }}</span><span class="muted">色号：{{ style.inkColor || '未记录' }}</span>
        </div>
      </div>
      <div class="section-head">网版作业 / 检验记录</div>
      <el-table :data="plate.history || []" size="small" border>
        <el-table-column label="动作" width="110"><template #default="{ row }">{{ actionLabel(row.actionType) }}</template></el-table-column>
        <el-table-column label="结果" width="110"><template #default="{ row }">{{ ScreenPlateStatusEnum.getLabel(row.afterStatus) || row.afterStatus || '-' }}</template></el-table-column>
        <el-table-column prop="description" label="记录" min-width="180" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="operateTime" label="时间" width="165" />
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ScreenPlateStatusEnum } from '@/enums/engineering/resource'
import { readSources } from '@/components/printing/printStyles'

const props = defineProps<{ plate: any; compact?: boolean }>()
const parsed = computed(() => {
  try {
    const sources = readSources(props.plate.sourcesJson)
    if (sources.some(source => !source || !Array.isArray(source.styles) || source.styles.some(style => !style || typeof style !== 'object'))) throw new Error('缺少样式')
    return { sources, error: '' }
  } catch { return { sources: [], error: '菲林样式资料格式异常' } }
})
const sources = computed(() => parsed.value.sources)
const sourcesError = computed(() => parsed.value.error)
function actionLabel(action: string) {
  return ({ create: '制版', release: '检验放行', fail: '检验不合格', wash: '洗版', scrap: '报废', clean: '清洁' } as Record<string, string>)[action] || action
}
</script>

<style scoped>
.muted, .source-version { color: var(--el-text-color-secondary); }
.source-version { margin-left: 12px; font-size: 12px; }
.error { color: var(--el-color-danger); }
.notice { color: var(--el-color-warning-dark-2); }
.section-head { font-weight: 600; margin: 16px 0 8px; }
.source-card { border: 1px solid var(--el-border-color-light); border-radius: 6px; padding: 12px; margin-bottom: 8px; }
.style-row { display: flex; gap: 24px; margin-top: 8px; }
.source-summary + .source-summary { margin-top: 6px; }
</style>
