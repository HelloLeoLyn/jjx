<template>
  <!--
    A4Canvas：A4 单据画布（打印方案 B）
    设计目标：
    - 屏幕显示：按 A4 比例（210:297）渲染，带纸张阴影，可视化编辑内容
    - 打印时：@media print 用 mm 精确控制纸张（210mm×297mm），所见即所得
    - 用法：内容放默认插槽，画布负责纸张/边距/打印控制
    示例：
      <A4Canvas :padding-mm="15" :scale="0.9">
        <div class="doc-header">公司抬头</div>
        <table>明细...</table>
      </A4Canvas>
  -->
  <div class="a4-canvas-wrap" :style="{ transform: `scale(${scale})` }">
    <div
      class="a4-canvas"
      :style="{
        padding: paddingMm + 'mm',
        '--a4-padding': paddingMm + 'mm',
      }"
    >
      <slot></slot>
    </div>
    <!-- 页脚（打印时也显示） -->
    <div v-if="showFooter" class="a4-footer">
      <slot name="footer">{{ footerText }}</slot>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * A4Canvas 公共组件（DEV：打印方案 B）
 * 规格：
 * - A4 纸 = 210mm × 297mm，比例固定 1:1.414
 * - 屏幕：宽 794px（96dpi 换算），高自动按比例；可 scale 缩放
 * - 打印：@media print 强制 210mm 宽，浏览器按真实 A4 输出
 */
defineProps<{
  /** 内容区边距（mm，默认 15） */
  paddingMm?: number
  /** 屏幕显示缩放（默认 1，内容多时可调小） */
  scale?: number
  /** 是否显示页脚（默认 false） */
  showFooter?: boolean
  /** 页脚文本（默认空） */
  footerText?: string
}>()
</script>

<style scoped>
.a4-canvas-wrap {
  /* 屏幕显示：A4 宽 794px @96dpi（210mm），transform-origin 左上 */
  transform-origin: top left;
  width: 794px;
  margin: 0 auto;
}

.a4-canvas {
  width: 794px;
  min-height: 1123px; /* 297mm @96dpi，内容超出会自然撑高（打印自动分页） */
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  border: 1px solid #e4e7ed;
  box-sizing: border-box;
  color: #333;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  font-size: 12px;
  line-height: 1.5;
}

.a4-footer {
  width: 794px;
  text-align: center;
  color: #999;
  font-size: 10px;
  padding: 8px 0 4px;
}

/* 打印：精确 A4 纸张 */
@media print {
  .a4-canvas-wrap {
    width: 210mm;
    margin: 0;
    transform: none !important;
  }

  .a4-canvas {
    width: 210mm;
    min-height: 0;
    box-shadow: none;
    border: none;
  }

  .a4-footer {
    width: 210mm;
  }
}
</style>
