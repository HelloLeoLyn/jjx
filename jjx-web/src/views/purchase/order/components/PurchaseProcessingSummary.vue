<template>
  <div v-if="modelValue" class="processing-summary">
    <template v-if="plan">
      <PurchaseProcessingTreeRows v-if="plan.tree" :tree="plan.tree" />
      <template v-else>
      <div v-for="group in plan.groups" :key="group.groupId" class="processing-group">
        <strong>{{ group.name }} · 母卷 {{ group.motherRolls }} 卷</strong>
        <div>分切：{{ group.slits.map(s => `${s.widthMm}mm × ${s.rolls}卷`).join(' / ') }}</div>
        <div v-for="output in group.outputs" :key="output.outputId">
          {{ output.unit === 'ROLL' ? '余卷交回' : '切片' }}：{{ output.materialCode }} · {{ output.materialName }} ·
          {{ output.widthMm }}mm × {{ output.unit === 'ROLL' ? `${output.rollLengthM}m` : `${output.lengthMm}mm` }} × {{ output.quantity }}{{ output.unit === 'ROLL' ? '卷' : '片' }}
          <span v-if="output.remark">；{{ output.remark }}</span>
        </div>
      </div>
      </template>
      <small>加工要求属于本采购行；不重复计材料金额，实际交货另行登记。</small>
    </template>
    <span v-else class="invalid-plan">加工方案格式无效，请核对原始采购单</span>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import PurchaseProcessingTreeRows from './PurchaseProcessingTreeRows.vue'
import { readProcessingPlan } from '@/api/purchase/orderEntry'
const props=defineProps<{modelValue?:string|null}>()
const plan=computed(()=>readProcessingPlan(props.modelValue))
</script>
<style scoped>
.processing-summary{text-align:left;white-space:normal;overflow-wrap:anywhere;line-height:1.8}.processing-group{margin-bottom:8px;break-inside:avoid}.processing-summary small{color:#606266}.invalid-plan{color:#c45656}
</style>
