<template>
  <div class="trace-attachment-panel">
    <div class="panel-header">
      <span class="panel-title">
        <el-icon><FolderOpened /></el-icon>
        链路附件
      </span>
      <el-tag v-if="list.length" size="small" type="info">{{ list.length }} 个</el-tag>
    </div>

    <!-- 空态 -->
    <el-empty v-if="!loading && !list.length" description="暂无附件" :image-size="60" />
    <div v-else-if="loading" v-loading="true" class="panel-loading"></div>

    <!-- 按来源单据分组 -->
    <div v-else class="att-groups">
      <div
        v-for="(group, gi) in groups"
        :key="gi"
        class="att-group"
      >
        <!-- 分组头：来源单据 -->
        <div class="group-head">
          <span class="group-badge">{{ group.bizTypeName || '附件' }}</span>
          <span class="group-no" v-if="group.sourceNo">{{ group.sourceNo }}</span>
          <span class="group-count">{{ group.items.length }} 个</span>
        </div>

        <!-- 组内附件 -->
        <div
          v-for="att in group.items"
          :key="att.id"
          class="att-item"
        >
          <el-icon class="att-icon"><Document /></el-icon>
          <div class="att-info">
            <div class="att-name-row">
              <el-link
                type="primary"
                :href="downloadUrl(att.id)"
                :underline="false"
                target="_blank"
                class="att-name"
              >
                {{ att.fileName || '-' }}
              </el-link>
              <!-- 类型标签（remark）：图纸/单据/凭证… -->
              <el-tag
                v-if="att.remark"
                size="small"
                effect="plain"
                type="warning"
                class="att-tag"
              >
                {{ att.remark }}
              </el-tag>
              <el-tag v-else-if="att.category" size="small" effect="plain" class="att-tag">
                {{ att.category }}
              </el-tag>
            </div>
            <div class="att-sub">
              <span>{{ formatSize(att.fileSize) }}</span>
              <span v-if="att.createBy" class="att-meta">· {{ att.createBy }}</span>
              <span v-if="att.createTime" class="att-meta">· {{ formatTime(att.createTime) }}</span>
            </div>
          </div>
          <div class="att-actions">
            <el-tooltip content="下载" placement="top">
              <el-button link type="primary" :icon="Download" @click="onDownload(att)"></el-button>
            </el-tooltip>
            <el-tooltip content="预览" placement="top">
              <el-button link type="primary" :icon="View" @click="onPreview(att)"></el-button>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { FolderOpened, Document, Download, View } from '@element-plus/icons-vue'
import { attachmentApi } from '@/api/system/attachment'

/**
 * 链路附件面板（2026-08-11）
 * 按 traceId 聚合链路内所有附件（含来源单据文档），按来源单据分组展示
 * 每个附件显示：来源单据类型名 + 单号 + 类型标签（remark：图纸/单据/凭证…）
 *
 * Props:
 * - traceId: 链路追踪ID（必填）
 * - bizType/bizId: 可选降级，无 traceId 时按单据查附件
 */
const props = withDefaults(defineProps<{
  traceId?: string
  bizType?: string
  bizId?: number | null
}>(), {
  traceId: '',
  bizType: '',
  bizId: null,
})

const loading = ref(false)
const list = ref<any[]>([])

/** 按来源单据分组（bizTypeName + sourceNo 相同的合并） */
const groups = computed(() => {
  const map = new Map<string, any>()
  for (const att of list.value) {
    const key = `${att.bizTypeName || att.bizType || '附件'}|${att.sourceNo || ''}`
    if (!map.has(key)) {
      map.set(key, {
        bizTypeName: att.bizTypeName || '附件',
        sourceNo: att.sourceNo || '',
        items: [],
      })
    }
    map.get(key).items.push(att)
  }
  return Array.from(map.values())
})

watch(
  () => [props.traceId, props.bizId],
  () => load(),
  { immediate: true },
)

async function load() {
  loading.value = true
  try {
    if (props.traceId) {
      const res: any = await attachmentApi.listByTrace(props.traceId)
      list.value = res?.data || []
    } else if (props.bizType && props.bizId) {
      const res: any = await attachmentApi.list(props.bizType, Number(props.bizId))
      list.value = res?.data || []
    } else {
      list.value = []
    }
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

function onDownload(att: any) {
  window.open(downloadUrl(att.id), '_blank')
}

function onPreview(att: any) {
  window.open(downloadUrl(att.id), '_blank')
}

function formatSize(bytes?: number): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function formatTime(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

defineExpose({ load, list })
</script>

<style scoped>
.trace-attachment-panel {
  min-height: 120px;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.panel-loading {
  min-height: 100px;
}

.att-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.att-group {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
}

.group-badge {
  font-weight: 600;
  color: #2b5aa7;
}

.group-no {
  color: #606266;
}

.group-count {
  color: #909399;
  margin-left: auto;
}

.att-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid #f2f3f5;
}

.att-item:last-child {
  border-bottom: none;
}

.att-icon {
  color: #909399;
  font-size: 18px;
  flex-shrink: 0;
}

.att-info {
  flex: 1;
  min-width: 0;
}

.att-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.att-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.att-tag {
  flex-shrink: 0;
}

.att-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.att-meta {
  margin-left: 2px;
}

.att-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
</style>
