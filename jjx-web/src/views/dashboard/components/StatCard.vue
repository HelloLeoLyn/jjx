<template>
  <el-card
    shadow="never"
    class="stat-card"
    :class="{ clickable: Boolean(to) }"
    @click="handleClick"
  >
    <div class="stat-body">
      <div class="stat-icon" :class="`tone-${tone}`">
        <el-icon v-if="iconComponent" :size="24"><component :is="iconComponent" /></el-icon>
      </div>
      <div class="stat-info">
        <div class="stat-value">{{ value }}</div>
        <div class="stat-label">{{ label }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    icon?: string
    tone?: 'primary' | 'success' | 'warning' | 'danger'
    value: number | string
    label: string
    to?: string
  }>(),
  { tone: 'primary' }
)

const router = useRouter()
const iconComponent = computed(() =>
  props.icon ? (ElementPlusIconsVue as Record<string, unknown>)[props.icon] : undefined
)

function handleClick() {
  if (props.to) router.push(props.to)
}
</script>

<style scoped lang="scss">
.stat-card {
  height: 100%;
  border-radius: 12px;
  border: 1px solid #e8eaef;
  transition:
    transform 0.18s,
    box-shadow 0.18s;
  &.clickable {
    cursor: pointer;
  }
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 18px rgb(31 45 61 / 8%);
  }
}
.stat-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tone-primary {
  background: #ecf5ff;
  color: #409eff;
}
.tone-success {
  background: #f0f9eb;
  color: #67c23a;
}
.tone-warning {
  background: #fdf6ec;
  color: #e6a23c;
}
.tone-danger {
  background: #fef0f0;
  color: #f56c6c;
}
.stat-info {
  min-width: 0;
  flex: 1;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-label {
  margin-top: 2px;
  font-size: 13px;
  color: #909399;
}
</style>
