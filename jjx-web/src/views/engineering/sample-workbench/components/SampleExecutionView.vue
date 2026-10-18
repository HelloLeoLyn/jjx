<template>
  <div class="exec-view">
    <!-- 执行操作条：只发事件，不直接调 API；图纸入口由公共信息区持有（dev-20261018-010 契约 E/J） -->
    <el-card class="exec-bar" shadow="never">
      <div class="exec-bar-row">
        <div class="exec-bar-info">
          <span class="title">打样执行</span>
          <span class="sub">
            Round {{ roundNo }} · 按计划开始 / 完成工序，未保存或保存中的工序不可推进
          </span>
        </div>
        <div class="exec-bar-ops">
          <el-button size="small" @click="emit('view-files')">📎 查看图纸 / 资料</el-button>
          <el-button
            v-if="!readonly"
            type="success"
            size="small"
            :loading="saving"
            @click="emit('mark-ready')"
            >🎯 标记样品完成（送样）</el-button
          >
        </div>
      </div>
    </el-card>

    <!-- 工序执行时间线：组装卡片与印刷行合并展示，开始/完成仍走父级原回调 -->
    <div class="exec-timeline">
      <ExecutionTimeline
        :plan-list="planList"
        :print-list="printList"
        :round="roundNo"
        :format-time="formatTime"
        :parse-materials="parseMaterials"
        :advance-print="advancePrint"
        :advance-plan="advancePlan"
        :exec-block-reason="execBlockReason"
        :print-block-reason="printBlockReason"
        :readonly="readonly"
      />
    </div>

    <!-- 不良原因及改善（样品单级，订单/轮次归属不变；compact 仅折叠历史记录默认展开状态） -->
    <DefectRecordPanel compact :readonly="readonly" :order-id="orderId" :round-no="roundNo" />
  </div>
</template>

<script setup lang="ts">
/**
 * 打样执行视图（dev-20261018-008，公共契约 E/F/J）
 *
 * 1）本组件只做执行侧组织：当前工序时间线 + 图纸资料入口 + 样品完成入口 + 不良改善记录。
 * 2）计划、印刷、BOM、备注、轮次与执行状态仍由 workbench.vue / useSampleWorkbench 持有；
 *    这里不复制业务状态、不新建请求、不保存草稿。
 * 3）mark-ready / view-files 只向外发事件：标记完成的判断、确认与资料入口归属都在父级。
 * 4）readonly 时不出现工序推进、不良新增/删除和标记样品完成能力。
 */
import ExecutionTimeline from './ExecutionTimeline.vue'
import DefectRecordPanel from './DefectRecordPanel.vue'

defineProps<{
  readonly: boolean
  orderId: number
  roundNo: number
  planList: any[]
  printList: any[]
  formatTime: (t?: string) => string
  parseMaterials: (json?: string | null) => any[]
  saving: boolean
  advancePrint?: (row: any) => void
  advancePlan?: (pc: any) => void
  execBlockReason?: (pc: any) => string | null
  printBlockReason?: (row: any) => string | null
}>()

const emit = defineEmits<{
  (e: 'mark-ready'): void
  (e: 'view-files'): void
}>()
</script>

<style scoped>
.exec-bar {
  margin-bottom: 12px;
}

.exec-bar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.exec-bar-info {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.exec-bar-info .title {
  font-weight: 600;
  font-size: 14px;
}

.exec-bar-info .sub {
  font-size: 12px;
  color: #909399;
}

.exec-bar-ops {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exec-timeline {
  margin-bottom: 12px;
}
</style>
