<template>
  <el-dialog
    v-model="operationVisible"
    :title="operation.type === PurchaseProcessingOperationEnum.SLIT.value ? '分切' : '切片'"
    width="650px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <el-form label-width="145px" :disabled="busy">
      <p v-if="activeLabel">所属：{{ activeLabel }}</p>
      <p v-if="active">
        来源：{{ active.role }} · {{ processingTreeSpec(active) }}，{{ active.quantity
        }}{{ active.unit === 'ROLL' ? '卷' : '块' }}
      </p>
      <el-form-item v-if="slitSteps.length > 1" label="分切步骤">
        <el-select
          :model-value="active?.nodeId"
          aria-label="选择分切步骤"
          @change="emit('selectSlitStep', $event)"
        >
          <el-option
            v-for="(step, i) in slitSteps"
            :key="step.nodeId"
            :value="step.nodeId"
            :label="`${i ? '剩余宽度' : '来源原料'} ${step.widthMm}mm → ${step.operation?.slits?.map((s) => `${s.widthMm}mm×${s.rolls}份`).join('、')}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="active?.unit === 'ROLL' ? '本次使用卷数' : '本次使用块数'"
        ><el-input-number
          v-model="operation.useQuantity"
          :min="1"
          :max="active?.quantity"
          :precision="0"
          :controls="false"
          aria-label="本次使用数量"
      /></el-form-item>
      <template v-if="operation.type === PurchaseProcessingOperationEnum.SLIT.value">
        <div v-for="(slit, i) in operation.slits" :key="i" class="slit-row">
          <span>每卷 / 块分切</span
          ><el-input-number
            v-model="slit.widthMm"
            :min="0"
            :precision="2"
            :controls="false"
            aria-label="分切宽度"
          />mm ×
          <el-input-number
            v-model="slit.rolls"
            :min="1"
            :precision="0"
            :controls="false"
            aria-label="每卷分切份数"
          />份<el-button link type="danger" @click="operation.slits?.splice(i, 1)">移除</el-button>
        </div>
        <el-button plain @click="operation.slits?.push({ widthMm: 0, rolls: 1 })"
          >添加分切规格</el-button
        >
      </template>
      <template v-else>
        <el-form-item :label="active?.unit === 'ROLL' ? '卷宽作为' : '来源宽度作为'">
          <el-select
            :model-value="!!operation.rotated"
            aria-label="卷宽作为"
            @change="emit('changeCutAxis', $event)"
          >
            <el-option :value="false" label="成品宽 W" />
            <el-option :value="true" label="成品长 L" />
          </el-select>
        </el-form-item>
        <el-form-item :label="operation.rotated ? '成品长 L(mm)' : '成品宽 W(mm)'">
          <el-input-number
            v-model="operation[operation.rotated ? 'pieceLengthMm' : 'pieceWidthMm']"
            :min="0"
            :precision="2"
            :controls="false"
            :aria-label="operation.rotated ? '成品长L' : '成品宽W'"
          />
          <span class="hint">从来源宽度带出，可修改</span>
        </el-form-item>
        <el-form-item :label="operation.rotated ? '成品宽 W(mm)' : '成品长 L(mm)'">
          <el-input-number
            v-model="operation[operation.rotated ? 'pieceWidthMm' : 'pieceLengthMm']"
            :min="0"
            :precision="2"
            :controls="false"
            :aria-label="operation.rotated ? '成品宽W' : '成品长L'"
            placeholder="填写切片尺寸"
          />
        </el-form-item>
        <p class="hint">
          成品规格：{{ processingPieceSpec(operation) }}。
          {{
            operation.rotated
              ? '成品长L沿来源宽度，按成品宽W切片。'
              : '成品宽W沿来源宽度，按成品长L切片。'
          }}
        </p>
        <el-form-item label="每卷 / 块切片数"
          ><el-input-number
            v-model="operation.piecesPerUnit"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="留空按可切满"
            aria-label="每卷切片数"
            clearable
        /></el-form-item>
        <p class="hint">
          片数留空时由后台按尺寸算可切满数量；填写时按每个来源卷/块解释，剩余部分自动生成余料。
        </p>
        <div class="hint" aria-live="polite">
          <p v-if="cutPreviewBusy">正在计算片数和余料…</p>
          <el-alert
            v-else-if="cutPreviewError"
            :title="cutPreviewError"
            type="error"
            :closable="false"
          />
          <template v-else-if="cutPreviewPiece">
            <p>
              每卷 / 块切 {{ cutPreviewPiece.quantity / operation.useQuantity }} 片，本次合计
              {{ cutPreviewPiece.quantity }} 片。
            </p>
            <p v-for="rest in cutPreviewRemainders" :key="rest.nodeId">
              {{ rest.role }}：{{ processingTreeSpec(rest) }} × {{ rest.quantity
              }}{{ rest.unit === 'ROLL' ? '卷' : '片' }}
            </p>
            <p v-if="!cutPreviewRemainders.length">无剩余料</p>
          </template>
          <p v-else>填写完整尺寸后自动计算片数和余料。</p>
        </div>
      </template>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer
      ><el-button
        v-if="active?.operation"
        type="danger"
        link
        :disabled="busy"
        @click="emit('clear')"
        >清除本步及下级加工</el-button
      ><el-button :disabled="busy" @click="operationVisible = false">取消</el-button
      ><el-button type="primary" :loading="busy" @click="emit('apply')"
        >确定并生成余料</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import type { ProcessingTreeNode, ProcessingTreeOperation } from '@/api/purchase/orderEntry'
import { PurchaseProcessingOperationEnum } from '@/enums/purchase/processing'
import { processingTreeSpec, processingPieceSpec } from './processingTreeDisplay'
const operationVisible = defineModel<boolean>({ default: false })
const operation = defineModel<ProcessingTreeOperation>('operation', { required: true })
defineProps<{
  active?: ProcessingTreeNode
  activeLabel: string
  slitSteps: ProcessingTreeNode[]
  busy: boolean
  error: string
  cutPreviewBusy: boolean
  cutPreviewError: string
  cutPreviewPiece?: ProcessingTreeNode
  cutPreviewRemainders: ProcessingTreeNode[]
}>()
const emit = defineEmits<{
  (e: 'selectSlitStep', id: string): void
  (e: 'changeCutAxis', rotated: boolean): void
  (e: 'clear'): void
  (e: 'apply'): void
}>()
</script>
<style scoped>
.hint {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
.slit-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
</style>
