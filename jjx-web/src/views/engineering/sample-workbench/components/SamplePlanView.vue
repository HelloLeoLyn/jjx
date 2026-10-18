<template>
  <div class="sample-plan-view">
    <!-- 大类标签：冲型组装 / 印刷；切换只改展示，草稿仍由父级 workbench 持有 -->
    <el-tabs v-model="majorCategory" class="plan-major-tabs">
      <el-tab-pane label="🛠 冲型组装" name="ASSEMBLY">
        <div data-test="plan-slot-assembly"><slot name="assembly" /></div>
      </el-tab-pane>
      <el-tab-pane label="🖨️ 印刷" name="PRINT">
        <div data-test="plan-slot-print"><slot name="print" /></div>
      </el-tab-pane>
    </el-tabs>

    <!-- BOM 物料清单：默认折叠但入口常驻；v-show 保留子组件实例，折叠不丢草稿 -->
    <el-card class="plan-section materials-section" shadow="never">
      <template #header>
        <span style="font-weight: 600">🧾 BOM 物料清单</span>
        <span class="desc">与工序卡片材料共用同一份草稿；默认折叠，需要时展开核对</span>
        <el-button
          size="small"
          class="materials-toggle"
          style="float: right; margin-top: -2px"
          @click="materialsExpanded = !materialsExpanded"
        >
          {{ materialsExpanded ? '收起 BOM' : '展开核对 BOM' }}
        </el-button>
      </template>
      <div v-show="materialsExpanded" class="materials-body" data-test="plan-slot-materials">
        <slot name="materials" />
      </div>
    </el-card>

    <!-- 工程备注/工艺参数：正常显示 -->
    <div class="plan-section note-section" data-test="plan-slot-note">
      <slot name="note" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * 方案编制视图（dev-20261018-007）
 * 职责边界：只负责「大类切换 + 分区展示」，不复制任何业务状态。
 * - majorCategory: ASSEMBLY / PRINT，沿用父级同一份值
 * - 插槽 assembly / print / materials / note 由父页透传原组件与原事件
 * - BOM 默认折叠，展开/收起不卸载插槽内容（不丢草稿、不重建组件）
 * - 保存仍由父级 planEditor 控制器负责，本视图不发起请求
 */
const majorCategory = defineModel<string>('majorCategory', { default: 'ASSEMBLY' })
const materialsExpanded = ref(false)
</script>

<style scoped>
.sample-plan-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.plan-major-tabs {
  margin-bottom: 0;
}
.plan-section {
  margin-bottom: 0;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.materials-body {
  padding-top: 2px;
}
.note-section {
  min-height: 0;
}
</style>
