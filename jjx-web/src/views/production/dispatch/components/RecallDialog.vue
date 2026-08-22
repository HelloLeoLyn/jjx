<template>
  <el-dialog
    :model-value="visible"
    title="收回任务"
    width="520px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="child">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="人员">{{ child.assigneeName || '未知人员' }}</el-descriptions-item>
        <el-descriptions-item label="原任务数量">{{ fmt(child.taskQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ fmt(child.selfReported) }}</el-descriptions-item>
        <el-descriptions-item label="剩余未完成">{{ fmt(child.remainingQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="当前可收回范围">
          <span :style="{ color: maxQty > 0 ? '#f56c6c' : '#909399' }">0 &lt; x ≤ {{ fmt(maxQty) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="90px" class="recall-form">
        <el-form-item label="收回数量" required>
          <el-input-number
            v-model="quantity"
            :min="0"
            :max="maxQty"
            :precision="4"
            :step="1"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="收回原因（选填）" maxlength="200" />
        </el-form-item>
      </el-form>
      <div v-if="quantity > maxQty" class="over-tip">收回数量不能超过可收回范围 {{ fmt(maxQty) }}</div>
      <div class="hint">收回后父节点可分配容量立即恢复，可马上重新分配给其他人员。</div>
    </div>
    <el-empty v-else description="节点不存在" :image-size="50" />

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="warning" :loading="submitting" :disabled="!canSubmit" @click="handleConfirm">确认收回</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { TaskNodeVO } from '@/types/production/taskNode'

const props = defineProps<{
  visible: boolean
  child?: TaskNodeVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'confirm', payload: { quantity: number; remark: string }): void
}>()

const quantity = ref(0)
const remark = ref('')
const submitting = ref(false)

const maxQty = computed(() => num(props.child?.remainingQuantity))

const canSubmit = computed(() => {
  const q = Number(quantity.value || 0)
  return q > 0 && q <= maxQty.value && !submitting.value
})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      quantity.value = 0
      remark.value = ''
      submitting.value = false
    }
  },
)

const handleConfirm = async () => {
  const q = Number(quantity.value || 0)
  if (q <= 0 || q > maxQty.value) {
    ElMessage.warning(`收回数量必须在 0 < x <= ${fmt(maxQty.value)} 之间`)
    return
  }
  submitting.value = true
  emit('confirm', { quantity: q, remark: remark.value || '' })
}

function num(v?: number | null): number {
  return Number(v || 0)
}
function fmt(v?: number | null): string {
  const n = num(v)
  return Number.isInteger(n) ? String(n) : String(n)
}
</script>

<style scoped>
.recall-form {
  margin-top: 14px;
}
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.over-tip {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
}
</style>
