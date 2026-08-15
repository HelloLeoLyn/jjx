<template>
  <span class="op-chain" :class="{ clickable }" @click="clickable && open()">
    <!-- 紧凑模式：名字 + 箭头，末级加粗绿色 -->
    <template v-if="chain.length">
      <!-- 只显示第一级负责人（列表列用，其余级别弹窗内看） -->
      <template v-if="firstOnly">
        <span class="op-name op-last">{{ chain[0].userName }}</span>
        <el-tooltip v-if="chain.length > 1" :content="`共 ${chain.length} 级执行人（第2级起），点击查看完整链`" placement="top">
          <span class="op-level">＋{{ chain.length - 1 }}级</span>
        </el-tooltip>
      </template>
      <template v-else>
        <template v-for="(o, i) in chain" :key="i">
          <span v-if="i > 0" class="op-arrow"> ＞ </span>
          <span class="op-name" :class="{ 'op-last': i === chain.length - 1 }">{{ o.userName }}</span>
        </template>
        <el-tooltip v-if="chain.length > 1" :content="`共 ${chain.length} 级执行人，点击查看完整链`" placement="top">
          <span class="op-level">({{ chain.length }}级)</span>
        </el-tooltip>
      </template>
    </template>
    <span v-else class="op-empty">未指定</span>
  </span>

  <!-- 弹窗模式：完整链 -->
  <el-dialog v-model="visible" :title="`执行人链 - ${processName || ''}`" width="480px" append-to-body>
    <div v-if="orderNo || teamName || equipmentName" class="op-ctx">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item v-if="orderNo" label="工单">{{ orderNo }}</el-descriptions-item>
        <el-descriptions-item v-if="teamName" label="班组">
          <el-tag size="small" type="primary" effect="plain">{{ teamName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="equipmentName" label="设备">{{ equipmentName }}</el-descriptions-item>
        <el-descriptions-item v-else label="设备">不限</el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="chain.length" class="op-full">
      <div v-for="(o, i) in chain" :key="i" class="op-node">
        <div class="op-card" :class="{ 'op-card-last': i === chain.length - 1 }">
          <el-tag size="small" :type="i === chain.length - 1 ? 'success' : 'info'" effect="plain" class="op-level-tag">
            第{{ o.level ?? i + 1 }}级
          </el-tag>
          <span class="op-user">{{ o.userName }}</span>
          <span v-if="i === chain.length - 1" class="op-star">★ 实际干活（报工挂此级）</span>
        </div>
        <div v-if="i < chain.length - 1" class="op-link">↓</div>
      </div>
    </div>
    <el-empty v-else description="未指定执行人" :image-size="60" />

    <template #footer>
      <el-button v-if="dispatchId" type="primary" plain @click="goLogs">查看流水</el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface OpItem {
  userId: number
  userName: string
  level?: number
}

const props = withDefaults(
  defineProps<{
    /** 执行人链 JSON 字符串 */
    operators?: string | null
    /** 上下文（弹窗内展示） */
    processName?: string
    orderNo?: string
    teamName?: string
    equipmentName?: string
    /** 派工单 ID（有则弹窗底部显示"查看流水"） */
    dispatchId?: number | null
    /** 是否可点击展开弹窗 */
    clickable?: boolean
    /** 只显示第一级负责人（列表列用；完整链在弹窗内看） */
    firstOnly?: boolean
  }>(),
  { clickable: true, firstOnly: false },
)

const emit = defineEmits<{ (e: 'logs', dispatchId: number): void }>()

const visible = ref(false)

const chain = computed<OpItem[]>(() => {
  if (!props.operators) return []
  try {
    const arr = JSON.parse(props.operators) as OpItem[]
    return arr.sort((a, b) => (a.level ?? 1) - (b.level ?? 1))
  } catch {
    return []
  }
})

const open = () => {
  visible.value = true
}

const goLogs = () => {
  if (!props.dispatchId) return
  visible.value = false
  emit('logs', props.dispatchId)
}
</script>

<style scoped>
.op-chain {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
.op-chain.clickable {
  cursor: pointer;
}
.op-name {
  font-size: 12px;
  color: #606266;
}
.op-name.op-last {
  font-weight: 600;
  color: #67c23a;
}
.op-arrow {
  color: #c0c4cc;
  font-size: 12px;
}
.op-level {
  font-size: 11px;
  color: #909399;
  margin-left: 2px;
}
.op-empty {
  color: #c0c4cc;
  font-size: 12px;
}
.op-ctx {
  margin-bottom: 12px;
}
.op-full {
  display: flex;
  flex-direction: column;
}
.op-node {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.op-card {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fafafa;
}
.op-card-last {
  border-color: #b3e19d;
  background: #f0f9eb;
}
.op-user {
  font-size: 13px;
  font-weight: 500;
}
.op-star {
  margin-left: auto;
  font-size: 12px;
  color: #67c23a;
}
.op-link {
  color: #c0c4cc;
  font-size: 14px;
  line-height: 20px;
  padding: 2px 0;
}
</style>
