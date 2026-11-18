<template>
  <div class="measurement-policy-editor">
    <template v-if="modelValue">
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="实物单位" required>
          <el-select :model-value="modelValue.physicalUnit" filterable @update:model-value="patch({ physicalUnit: $event })">
            <el-option v-for="o in units" :key="o.code" :label="`${o.label}/${o.code}`" :value="o.code" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="采购（计价）单位" required>
          <el-select :model-value="modelValue.priceUnit" filterable @update:model-value="patch({ priceUnit: $event })">
            <el-option v-for="o in units" :key="o.code" :label="`${o.label}/${o.code}`" :value="o.code" />
          </el-select>
        </el-form-item></el-col>
      </el-row>
      <el-form-item label="换算方式" required>
        <el-select :model-value="modelValue.mode" style="width: 100%" @update:model-value="patch({ mode: $event })">
          <el-option v-for="o in MeasurementModeEnum.items" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="modelValue.mode === MeasurementModeEnum.PIECE_WEIGHT.value" label="默认单重(g)">
        <el-input-number :model-value="modelValue.unitWeightG" :min="0" :precision="8" @update:model-value="patch({ unitWeightG: $event })" />
      </el-form-item>
      <div class="measurement-hint">宽、长、克重取档案尺寸。收货、余卷退料按当次实际尺寸或净重换算。</div>
    </template>
    <el-alert v-else title="双单位未配置，不按规格猜测卷数或片数" type="info" :closable="false" />
    <el-button v-if="!modelValue" link type="primary" @click="enable">配置双单位</el-button>
  </div>
</template>
<script setup lang="ts">
import { MeasurementModeEnum } from '@/enums/inventory/MeasurementEnum'
import type { MeasurementPolicy } from '@/types/inventory/measurement'
import type { DictionaryOption } from '@/types/inventory/materialMaster'
const props = defineProps<{ modelValue?: MeasurementPolicy | null; units: DictionaryOption[]; defaultUnit?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', policy: MeasurementPolicy): void }>()
function patch(value: Partial<MeasurementPolicy>) { if (props.modelValue) emit('update:modelValue', { ...props.modelValue, ...value }) }
function enable() { const unit = props.defaultUnit || 'PCS'; emit('update:modelValue', { physicalUnit: unit, priceUnit: unit, mode: MeasurementModeEnum.SAME.value }) }
</script>
<style scoped>
.measurement-hint { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 12px; }
</style>
