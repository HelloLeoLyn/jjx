<template>
  <div class="biz-detail-panel">
    <el-descriptions
      :column="column"
      border
      size="small"
      v-if="items.length"
      :direction="direction"
    >
      <el-descriptions-item
        v-for="(item, idx) in items"
        :key="idx"
        :label="item.label"
        :span="item.span || 1"
      >
        <template v-if="item.type === 'tag'">
          <el-tag :type="item.tagType || 'info'" size="small" effect="plain">
            {{ formatValue(item) }}
          </el-tag>
        </template>
        <template v-else-if="item.type === 'slot' && $slots[item.slotName || item.key]">
          <slot :name="item.slotName || item.key" :data="data" />
        </template>
        <template v-else>
          {{ formatValue(item) }}
        </template>
      </el-descriptions-item>
    </el-descriptions>
    <el-empty v-else-if="!data" description="暂无数据" :image-size="50" />
  </div>
</template>

<script setup lang="ts">
/**
 * 通用详情组件：传字段配置 items + 数据 data，渲染描述列表
 * 支持：普通文本 / tag 标签 / 插槽（slotName）
 */
export interface DetailItem {
  /** 字段 key（从 data 取值） */
  key: string
  /** 显示名称 */
  label: string
  /** 渲染类型：text | tag | slot（默认 text） */
  type?: 'text' | 'tag' | 'slot'
  /** tag 类型时的颜色 */
  tagType?: 'info' | 'warning' | 'success' | 'danger' | 'primary'
  /** 占几列（默认1，最大2） */
  span?: 1 | 2
  /** 取值格式化函数（可选） */
  format?: (value: any, data: any) => string
  /** slot 模式下的插槽名（默认用 key） */
  slotName?: string
}

const props = defineProps<{
  data: Record<string, any> | null | undefined
  items: DetailItem[]
  column?: number
  direction?: 'horizontal' | 'vertical'
}>()

function formatValue(item: DetailItem): string {
  const raw = props.data ? props.data[item.key] : undefined
  if (item.format) {
    return item.format(raw, props.data)
  }
  if (raw == null || raw === '') return '-'
  return String(raw)
}
</script>
