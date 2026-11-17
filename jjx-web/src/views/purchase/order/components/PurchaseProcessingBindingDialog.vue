<template>
  <el-dialog
    v-model="bindingVisible"
    title="指定终端交货物料"
    width="600px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!busy"
  >
    <p v-if="active">
      {{ activeLabel || active.role }}：{{ processingTreeSpec(active) }}，{{ active.quantity
      }}{{ active.unit === 'ROLL' ? '卷' : '片' }}
    </p>
    <el-select
      v-if="!bindingScrap"
      v-model="bindingId"
      remote
      filterable
      clearable
      :remote-method="(keyword) => emit('search', keyword)"
      :loading="searching"
      @visible-change="materialSelectOpen = $event"
      :disabled="busy || disabled"
      placeholder="选择对应规格物料"
      style="width: 100%"
      ><el-option
        v-for="m in materialOptions"
        :key="m.materialId"
        :value="m.materialId"
        :label="`${m.materialCode} · ${m.materialName} · ${m.specification || ''}`"
      />
      <template #empty>
        <div class="empty-materials">
          <el-button
            v-if="canCreateFromSearch"
            link
            type="primary"
            :disabled="busy || disabled"
            @click="emit('createMaterial')"
            >未找到对应规格？新建此规格档案</el-button
          >
          <span v-else>{{ searching ? '正在搜索…' : '暂无匹配物料' }}</span>
        </div>
      </template>
    </el-select>
    <el-button
      v-if="canCreateFromSearch && !materialSelectOpen"
      link
      type="primary"
      :disabled="busy || disabled"
      @click="emit('createMaterial')"
    >
      未找到对应规格？新建此规格档案
    </el-button>
    <p v-if="!bindingScrap" class="hint">
      仅搜索与当前终端规格匹配的启用物料；没有对应档案时可在搜索后新建。
    </p>
    <el-checkbox
      v-model="bindingScrap"
      :disabled="busy || disabled"
      @change="emit('changeDelivery')"
      >报废不入库</el-checkbox
    >
    <el-input
      v-if="bindingScrap"
      v-model="disposalReason"
      type="textarea"
      :disabled="busy || disabled"
      maxlength="200"
      show-word-limit
      placeholder="填写报废原因；实际报废数量在收货时确认，不生成该节点的库存"
      aria-label="报废原因"
    />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
    <template #footer
      ><el-button :disabled="busy" @click="bindingVisible = false">取消</el-button
      ><el-button
        type="primary"
        :disabled="disabled || (bindingScrap ? !disposalReason.trim() : !bindingId)"
        :loading="busy"
        @click="emit('apply')"
        >{{ bindingScrap ? '确定报废处理' : '确定物料' }}</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import type { ProcessingTreeNode } from '@/api/purchase/orderEntry'
import type { InventoryMaterial } from '@/types/inventory/material'
import { processingTreeSpec } from './processingTreeDisplay'
const bindingVisible = defineModel<boolean>({ default: false })
const bindingId = defineModel<number>('bindingId')
const bindingScrap = defineModel<boolean>('bindingScrap', { required: true })
const disposalReason = defineModel<string>('disposalReason', { required: true })
const materialSelectOpen = defineModel<boolean>('materialSelectOpen', { required: true })
defineProps<{
  active?: ProcessingTreeNode
  activeLabel: string
  busy: boolean
  disabled?: boolean
  searching: boolean
  materialOptions: InventoryMaterial[]
  canCreateFromSearch: boolean
  error: string
}>()
const emit = defineEmits<{
  (e: 'search', keyword: string): void
  (e: 'createMaterial'): void
  (e: 'changeDelivery'): void
  (e: 'apply'): void
}>()
</script>
<style scoped>
.hint {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
.notice {
  margin-top: 12px;
}
.empty-materials {
  padding: 12px;
  text-align: center;
}
</style>
