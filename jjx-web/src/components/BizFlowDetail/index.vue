<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="860px"
    append-to-body
    destroy-on-close
    @update:model-value="handleUpdateVisible"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <!-- 顶部：状态流转 -->
    <StatusFlowBar :steps="flowSteps" :current="currentStatus" />

    <!-- 操作区：备注 + 确认按钮 -->
    <div class="biz-actions">
      <el-input
        v-model="remark"
        type="textarea"
        :rows="2"
        placeholder="添加备注..."
        class="biz-remark-input"
      />
      <div class="biz-actions-right">
        <slot name="actions" :confirm="handleConfirm">
          <el-button type="primary" :loading="confirmLoading" @click="handleConfirm">
            {{ confirmText }}
          </el-button>
        </slot>
      </div>
    </div>

    <!-- 标签页 -->
    <el-tabs v-model="activeTab" class="biz-tabs">
      <!-- Tab1 单据详情（默认，调用方插槽） -->
      <el-tab-pane label="单据详情" name="detail">
        <slot name="detail" />
      </el-tab-pane>

      <!-- Tab2 文档流水 -->
      <el-tab-pane label="文档流水" name="docs">
        <AttachmentPanel
          v-if="bizId"
          :biz-type="bizType"
          :biz-id="bizId"
          :trace-id="traceId || undefined"
        />
        <el-empty v-else description="暂无单据ID" :image-size="60" />
      </el-tab-pane>

      <!-- Tab3 操作流水 -->
      <el-tab-pane label="操作流水" name="ops">
        <OperationLogPanel
          v-if="bizId"
          :biz-type="bizType"
          :biz-id="bizId"
          :trace-id="traceId || undefined"
        />
      </el-tab-pane>

      <!-- Tab4 事件 -->
      <el-tab-pane label="事件" name="events">
        <EventPanel v-if="bizId" :biz-type="bizType" :biz-id="bizId" />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <slot name="footer">
        <el-button @click="handleUpdateVisible(false)">关 闭</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StatusFlowBar, { type FlowStep } from './StatusFlowBar.vue'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import OperationLogPanel from './OperationLogPanel.vue'
import EventPanel from './EventPanel.vue'
import request from '@/utils/request'

const props = defineProps<{
  modelValue: boolean
  /** 业务类型（如 inquiry/order/purchase） */
  bizType: string
  /** 业务单据ID */
  bizId: number | null | undefined
  /** 弹窗标题 */
  title?: string
  /** 状态流转步骤配置（调用方传入） */
  statusSteps?: FlowStep[]
  /** 当前状态 key */
  currentStatus?: string | number
  /** 确认按钮文案 */
  confirmText?: string
  /** 可选：链路追踪ID（直达操作日志） */
  traceId?: string
  /** 确认接口配置：POST/GET + url + 参数映射 */
  confirmApi?: {
    url: string
    method?: 'post' | 'put' | 'get'
    /** 从 bizId 构造请求体/参数，默认 { bizId } */
    buildParams?: (bizId: number) => Record<string, unknown>
  }
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'confirm-success', data?: unknown): void
  (e: 'remark-save', remark: string): void
}>()

const activeTab = ref('detail')
const remark = ref('')
const confirmLoading = ref(false)
const flowSteps = ref<FlowStep[]>(props.statusSteps || [])

function handleUpdateVisible(val: boolean) {
  emit('update:modelValue', val)
}

function handleOpen() {
  activeTab.value = 'detail'
  remark.value = ''
}

function handleClosed() {
  remark.value = ''
}

// 通用确认：按 confirmApi 配置调用后端；未配置则直接发确认事件
async function handleConfirm() {
  confirmLoading.value = true
  try {
    if (props.confirmApi?.url && props.bizId != null) {
      const build = props.confirmApi.buildParams || ((id: number) => ({ bizId: id }))
      const params = build(props.bizId)
      const res: any = await request({
        url: props.confirmApi.url,
        method: props.confirmApi.method || 'post',
        data: params,
        params: props.confirmApi.method === 'get' ? params : undefined,
      })
      if (res?.code === 200) {
        ElMessage.success('操作成功')
        // 备注一并保存（可选）
        if (remark.value.trim()) {
          emit('remark-save', remark.value.trim())
        }
        emit('confirm-success', res.data)
      } else {
        ElMessage.error(res?.msg || '操作失败')
      }
    } else {
      // 无后端配置：交回调用方处理
      emit('confirm-success', undefined)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    confirmLoading.value = false
  }
}

watch(() => props.modelValue, (v) => {
  if (!v) {
    remark.value = ''
  }
})

defineExpose({ refresh: handleOpen })
</script>

<style scoped>
.biz-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 4px;
}
.biz-remark-input {
  flex: 1;
}
.biz-actions-right {
  flex-shrink: 0;
  padding-top: 4px;
}
.biz-tabs {
  margin-top: 8px;
}
</style>
