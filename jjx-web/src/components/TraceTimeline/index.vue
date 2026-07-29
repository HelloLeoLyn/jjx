<template>
  <el-drawer
    v-model="visible"
    title="🔗 链路追踪"
    size="600px"
    @open="loadTrace"
    @close="handleClose"
  >
    <!-- 链路上方信息 -->
    <div v-if="traceId" class="trace-header">
      <el-tag type="primary" effect="dark">traceId: {{ traceId }}</el-tag>
      <el-tag v-if="module" type="success" closable @close="module = ''" style="margin-left:8px">
        {{ module }}
      </el-tag>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" style="text-align:center;padding:60px">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <div style="margin-top:10px;color:var(--el-text-color-secondary)">加载中...</div>
    </div>

    <!-- 空结果 -->
    <el-empty v-else-if="nodes.length === 0" description="暂无操作日志" />

    <!-- 时间线 -->
    <div v-else class="tl-container">
      <div v-for="(node, ni) in nodes" :key="ni" class="tl-node">
        <!-- 连接线 -->
        <div class="tl-connector">
          <div class="tl-dot" :class="node.status === 'success' ? 'dot-ok' : 'dot-warn'"></div>
          <div v-if="ni < nodes.length - 1" class="tl-line"></div>
        </div>

        <!-- 节点卡片 -->
        <el-card class="tl-card" shadow="hover" @click="toggleExpand(ni)">
          <div class="tl-card-header">
            <span class="tl-icon">{{ moduleIcon(node.module) }}</span>
            <span class="tl-title">{{ node.module }}</span>
            <el-tag size="small" :type="node.status === 'success' ? 'success' : 'warning'">
              {{ node.totalOps }} 次
            </el-tag>
            <span class="tl-time">{{ node.startTime }}</span>
            <el-icon class="tl-arrow" :class="{ expanded: node._expanded }"><ArrowDown /></el-icon>
          </div>

          <!-- 展开操作日志表格 -->
          <div v-if="node._expanded" class="tl-detail">
            <el-table :data="node.operations" size="small" stripe border>
              <el-table-column prop="time" label="时间" width="160" />
              <el-table-column prop="action" label="操作" min-width="140" />
              <el-table-column prop="operator" label="操作人" width="80" />
              <el-table-column label="结果" width="60">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 0 ? 'success' : 'danger'" size="small">
                    {{ scope.row.status === 0 ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'

const props = defineProps<{
  traceId: string
  module?: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(false)
const loading = ref(false)
const nodes = ref<any[]>([])
const filterModule = ref(props.module || '')

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.traceId, () => { if (props.modelValue) loadTrace() })

function handleClose() {
  emit('update:modelValue', false)
}

function moduleIcon(mod: string): string {
  const m: Record<string, string> = {
    '询价单管理': '📩', '报价单管理': '📋', '销售订单管理': '📑',
    '订单审核管理': '🔍', '产品管理': '🔧', '采购订单管理': '📦',
    '生产工单管理': '🏭', '入库管理': '📥', '出库管理': '📤', '质检管理': '🔬',
  }
  return m[mod] || '📌'
}

function toggleExpand(ni: number) {
  nodes.value[ni]._expanded = !nodes.value[ni]._expanded
}

async function loadTrace() {
  if (!props.traceId) return
  loading.value = true
  try {
    const res = await request.get(`/trace/${props.traceId}`)
    let data = (res as any).data || []
    // 如果指定了模块，过滤并固定
    if (filterModule.value) {
      data = data.filter((n: any) => n.module === filterModule.value)
    }
    data.forEach((n: any) => { n._expanded = false })
    nodes.value = data
  } catch {
    nodes.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.trace-header { margin-bottom: 12px; }
.tl-container { display:flex; flex-direction:column; gap:0; }
.tl-node { display:flex; align-items:stretch; gap:0; }
.tl-connector {
  display:flex; flex-direction:column; align-items:center;
  width:24px; flex-shrink:0; padding-top:18px;
}
.tl-dot { width:12px; height:12px; border-radius:50%; z-index:1; }
.dot-ok { background:var(--el-color-success); }
.dot-warn { background:var(--el-color-warning); }
.tl-line {
  width:2px; flex:1; background:var(--el-border-color-light);
  min-height:16px;
}
.tl-card { flex:1; margin-left:6px; margin-bottom:6px; }
.tl-card :deep(.el-card__body) { padding:10px 14px; }
.tl-card-header {
  display:flex; align-items:center; gap:6px; cursor:pointer;
}
.tl-icon { font-size:16px; }
.tl-title { font-weight:600; font-size:13px; flex:1; }
.tl-time { font-size:11px; color:var(--el-text-color-secondary); }
.tl-arrow { transition:transform .2s; font-size:12px; }
.tl-arrow.expanded { transform:rotate(180deg); }
.tl-detail { margin-top:8px; }
</style>
