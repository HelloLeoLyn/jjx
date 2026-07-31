<template>
  <el-drawer
    v-model="visible"
    title="🔗 链路追踪"
    size="700px"
    @open="loadTrace"
    @close="handleClose"
  >
    <!-- 链路上方信息 -->
    <div v-if="traceId" class="trace-header">
      <el-tag type="primary" effect="dark">traceId: {{ traceId }}</el-tag>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" style="text-align:center;padding:60px">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <div style="margin-top:10px;color:var(--el-text-color-secondary)">加载中...</div>
    </div>

    <!-- 空结果 -->
    <el-empty v-else-if="flatOps.length === 0" description="暂无操作日志" />

    <!-- 平铺表格 -->
    <el-table v-else :data="flatOps" size="small" stripe border>
      <el-table-column prop="time" label="时间" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          {{ formatBizStatus(scope.row.bizStatus, scope.row.bizType) }}-{{ scope.row.bizStatus }}
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column label="操作" min-width="180">
        <template #default="scope">
          {{  formatBusinessType(scope.row.businessType) }}-{{ scope.row.businessType }}
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="80" />
      <el-table-column label="结果" width="70" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
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

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.traceId, () => { if (props.modelValue) loadTrace() })

function handleClose() {
  emit('update:modelValue', false)
}

// 把所有模块的操作日志平铺成一个列表，按时间排序
const flatOps = computed(() => {
  const list: any[] = []
  for (const node of nodes.value) {
    for (const op of (node.operations || [])) {
      list.push({
        ...op,
        module: node.module,
      })
    }
  }
  // 按时间正序
  list.sort((a, b) => (a.time || '').localeCompare(b.time || ''))
  return list
})

function formatBusinessType(code: number): string {
  const map: Record<number, string> = {
    1: '新增', 2: '修改', 3: '删除', 4: '导出', 5: '导入',
    6: '审批', 7: '登录', 8: '登出', 9: '其他', 10: '重置密码', 11: '转换',
  }
  return map[code] ?? ''
}

/** 按模块显示业务状态名 */
function formatBizStatus(code: number, module: string): string {
  if (code == null) return ''
  const mod = module || ''
  if (mod.includes('inquiry')) {
    const m: Record<number, string> = { 0:'草稿', 1:'待处理', 2:'已发送', 3:'已转报价', 4:'已确认', 5:'已拒绝', 6:'已过期' }
    return m[code] ?? String(code)
  }
  if (mod.includes('quotation')) {
    const m: Record<number, string> = { 0:'草稿', 1:'待处理', 2:'已发送', 3:'已报价', 4:'已确认', 5:'已拒绝', 6:'已过期' }
    return m[code] ?? String(code)
  }
  if (mod.includes('order') || mod.includes('销售')) {
    const m: Record<number, string> = { 0:'待确认', 1:'已确认', 2:'生产中', 3:'已完成', 4:'已取消' }
    return m[code] ?? String(code)
  }
  return String(code)
}

async function loadTrace() {
  if (!props.traceId) return
  loading.value = true
  try {
    const res = await request.get(`/api/trace/${props.traceId}`)
    nodes.value = (res as any).data || []
  } catch {
    nodes.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.trace-header { margin-bottom: 12px; }
</style>
