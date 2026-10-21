<!--
  标准工序图标统一渲染（dev-20261021-027）

  - 有 icon 且对应 svg 存在 → SvgIcon
  - 否则 → 文字图标：名称前两字（尺寸 ≤16px 时只取首字）+ 类别配色圆角块
  说明：icon 存的是中文名（如「面板」），对应 src/icons/jjx/*.svg 的中文文件名；
        这里用 import.meta.glob 生成名字集合，避免 icon 有值但文件被改名时渲染空白。
-->
<template>
  <span v-if="hasIcon" class="process-icon" :style="boxStyle">
    <SvgIcon :name="icon!" :size="size" />
  </span>
  <span
    v-else-if="label"
    class="process-icon process-icon--text"
    :style="textStyle"
    :title="name || ''"
    >{{ label }}</span
  >
  <span v-else class="process-icon process-icon--empty" :style="boxStyle" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

const props = withDefaults(
  defineProps<{
    /** 图标名（中文名，对应 src/icons/jjx/*.svg） */
    icon?: string | null
    /** 工序名称，用于文字回退 */
    name?: string | null
    /** 工序类别：PANEL / UP_LINE / DOWN_LINE / OTHER */
    category?: string | null
    /** 像素尺寸（图标与文字块一致） */
    size?: number
  }>(),
  { icon: '', name: '', category: '', size: 16 }
)

/** 图标文件清单（构建期收集，运行期只读） */
const iconFiles = import.meta.glob('@/icons/jjx/*.svg')
const iconNames = new Set(
  Object.keys(iconFiles).map((path) => path.split('/').pop()?.replace(/\.svg$/, '') || '')
)

const hasIcon = computed(() => !!props.icon && iconNames.has(props.icon))

/** 类别底色：面板/上线/下线/其他 */
const CATEGORY_COLORS: Record<string, string> = {
  PANEL: '#409eff',
  UP_LINE: '#67c23a',
  DOWN_LINE: '#e6a23c',
  OTHER: '#909399',
}
const bgColor = computed(() => CATEGORY_COLORS[props.category || ''] || '#b1b3b8')

/** 文字回退：≤16px 取首字，否则取前两字 */
const label = computed(() => {
  const text = (props.name || '').trim()
  if (!text) return ''
  return props.size <= 16 ? text.slice(0, 1) : text.slice(0, 2)
})

const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))

const textStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: bgColor.value,
  fontSize: `${Math.max(10, Math.round(props.size * 0.55))}px`,
  borderRadius: props.size <= 16 ? '3px' : '4px',
}))
</script>

<style scoped>
.process-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  line-height: 1;
  overflow: hidden;
}
.process-icon--text {
  color: #fff;
  font-weight: 600;
  letter-spacing: 0;
  text-align: center;
  white-space: nowrap;
  user-select: none;
}
.process-icon--empty {
  display: inline-block;
}
</style>
