<template>
  <el-card class="col-timeline" shadow="never">
    <template #header>
      <span style="font-weight: 600">执行时间线</span>
      <span class="desc">Round {{ round }} · 按计划流转（在此开始/完成工序）</span>
    </template>
    <div v-if="merged.length" class="timeline">
      <div
        v-for="pc in merged"
        :key="pc.uid"
        class="tl-item"
        :class="pc.status === 2 ? 'done' : pc.status === 1 ? 'doing' : ''"
      >
        <div class="t">
          {{ pc.items.map((i: any) => i.processName).join(' + ') || '未命名工序' }}
          <el-tag v-if="pc.isPrint" size="small" type="warning" effect="plain">印刷</el-tag>
          <el-tag v-if="pc.status === 2" size="small" type="success">完成</el-tag>
          <el-tag v-else-if="pc.status === 1" size="small" type="warning">进行中</el-tag>
          <el-tag v-else size="small" type="info">待做</el-tag>
        </div>
        <div class="s">
          <template v-if="pc.operator">{{ pc.operator }} · </template>
          <template v-if="pc.startTime">{{ formatTime(pc.startTime) }}</template>
          <template v-if="pc.endTime"> - {{ formatTime(pc.endTime) }}</template>
          <template v-if="pc.durationMinutes"> · {{ pc.durationMinutes }}分钟</template>
          <template v-if="!pc.startTime && pc.status === 0">—</template>
        </div>
        <div v-if="pc.processNote" class="n">🔧 {{ pc.processNote }}</div>
        <div v-if="parseMaterials(pc.materials).length" class="n" style="margin-top:2px">
          <el-tag
            v-for="(m, mi) in parseMaterials(pc.materials)"
            :key="mi"
            size="small"
            type="info"
            style="margin-right:4px"
            >{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag
          >
        </div>
        <!-- 开始/完成操作（2026-09-04：从表格行统一收口到执行时间线） -->
        <div v-if="!readonly && pc.status !== 2" class="ops">
          <el-button
            size="small"
            type="primary"
            :loading="pc.advancing"
            @click="onAdvance(pc)"
            >{{ pc.status === 1 ? '✓ 完成' : '▶ 开始' }}</el-button
          >
        </div>
      </div>
    </div>
    <div v-else style="color:#999;font-size:13px">暂无工序计划</div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 执行时间线（dev-20260811-008 组件化）
 * 展示工序计划的执行状态/时间/材料
 * 2026-08-12：合并印刷工序（printList）按 processOrder 统一排序展示
 * 2026-09-04：操作收口——时间线内开始/完成（组装卡片=advancePlan 聚合推进，印刷行=advancePrint 单行）
 */
const props = defineProps<{
  planList: any[]
  printList?: any[]
  round: number
  formatTime: (t?: string) => string
  parseMaterials: (json?: string | null) => any[]
  advancePrint?: (row: any) => void
  advancePlan?: (pc: any) => void
  readonly?: boolean
}>()

// 印刷行 → 时间线条目
function toTimelineItem(r: any) {
  const colorText = r.colorNoLabel
    ? `${r.colorNoLabel}${r.colorNo && r.colorNo !== r.colorNoLabel ? `（${r.colorNo}）` : ''}`
    : r.colorNo
  const noteParts = [
    colorText ? `色号：${colorText}` : '',
    r.inkNo ? `油墨：${r.inkNo}` : '',
    r.screenNo ? `网框：${r.screenNo}` : '',
  ].filter(Boolean)
  return {
    uid: `print-${r.uid || r.processId || Math.random()}`,
    items: [{ processName: r.printName || '未命名印刷' }],
    status: r.status ?? 0,
    operator: r.operator || '',
    startTime: r.startTime || null,
    endTime: r.endTime || null,
    durationMinutes: r.durationMinutes ?? null,
    processNote: noteParts.join(' · '),
    materials: r.materials || null,
    processOrder: r.processOrder ?? 999,
    isPrint: true,
    advancing: r.advancing || false,
    raw: r,
  }
}

// 组装卡片 + 印刷行合并，按 processOrder 排序
const merged = computed(() => {
  // 印刷面板会自动补一条未保存的空白录入行；只有已落库且名称有效的工序才进入执行时间线。
  const prints = (props.printList || [])
    .filter((row) => row.processId != null && String(row.printName || '').trim())
    .map(toTimelineItem)
  return [...props.planList, ...prints].sort(
    (a, b) => (a.processOrder ?? 999) - (b.processOrder ?? 999),
  )
})

// 统一推进：印刷行走原行（advancePrint 单行），组装卡片走原卡片（advancePlan 聚合）
function onAdvance(pc: any) {
  if (props.readonly) return
  if (pc.isPrint) {
    props.advancePrint?.(pc.raw)
  } else {
    props.advancePlan?.(pc)
  }
}
</script>

<style scoped>
.col-timeline {
  flex: 1.2;
  min-width: 0;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.timeline {
  position: relative;
  padding-left: 20px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 4px;
  bottom: 4px;
  width: 2px;
  background: #e4e7ed;
}
.tl-item {
  position: relative;
  padding-bottom: 16px;
}
.tl-item::before {
  content: '';
  position: absolute;
  left: -17px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.tl-item.done::before {
  background: #67c23a;
}
.tl-item.doing::before {
  background: #409eff;
  box-shadow: 0 0 0 3px #ecf5ff;
}
.tl-item .t {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tl-item .s {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.tl-item .n {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
}
.tl-item .ops {
  margin-top: 6px;
}
</style>