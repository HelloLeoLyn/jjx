<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="1200px"
    append-to-body
    destroy-on-close
    @update:model-value="handleUpdateVisible"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <!-- 顶部：操作信息 + 状态流转 -->
    <div class="biz-flow-head">
      <div class="biz-flow-op">
        <el-tag size="small" type="primary" effect="dark">{{ operationName }}</el-tag>
        <span class="biz-flow-no" v-if="bizNo">{{ bizNo }}</span>
      </div>
      <div v-if="fromStatus != null || toStatus != null" class="biz-flow-status">
        <template v-if="fromStatus != null">
          <el-tag size="small" type="info" effect="plain">{{ fromStatusLabel }}</el-tag>
          <el-icon class="flow-arrow"><Right /></el-icon>
        </template>
        <el-tag size="small" :type="toStatusType || 'success'" effect="dark">
          {{ toStatusLabel }}
        </el-tag>
      </div>
    </div>

    <!-- 备注输入 -->
    <div class="biz-remark">
      <el-input v-model="remark" type="textarea" :rows="2" placeholder="操作备注（选填）..." />
    </div>

    <!-- 标签页 -->
    <el-tabs v-model="activeTab" class="biz-tabs">
      <!-- Tab1 单据详情 -->
      <el-tab-pane label="单据详情" name="detail">
        <slot name="detail" :data="data">
          <BizDetailPanel
            v-if="data"
            :data="data"
            :items="detailItems"
            :column="2"
            :direction="direction"
          />
          <el-empty v-else description="暂无单据数据" :image-size="50" />
        </slot>
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

    </el-tabs>

    <template #footer>
      <slot name="footer" :confirm="handleConfirm">
        <el-button @click="handleUpdateVisible(false)">取 消</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="handleConfirm">
          {{ confirmText }}
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Right } from '@element-plus/icons-vue'
import BizDetailPanel, { type DetailItem } from './BizDetailPanel.vue'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import request from '@/utils/request'

const props = defineProps<{
  modelValue: boolean
  /** 业务类型（如 inquiry/order/purchase） */
  bizType: string
  /** 业务单据ID */
  bizId: number | null | undefined
  /** 弹窗标题 */
  title?: string
  /** 操作名称（如：审核通过/转报价） */
  operationName?: string
  /** 单据号显示 */
  bizNo?: string
  /** 操作前状态 label（可空，如"草稿"） */
  fromStatus?: string | number | null
  fromStatusLabel?: string
  /** 操作后状态 label */
  toStatus?: string | number | null
  toStatusLabel?: string
  toStatusType?: 'info' | 'warning' | 'success' | 'danger' | 'primary'
  /** 确认按钮文案 */
  confirmText?: string
  /** 可选：链路追踪ID */
  traceId?: string
  /** 单据详情数据（传给通用详情组件/插槽） */
  data?: Record<string, any> | null
  /** 通用详情字段配置 */
  detailItems?: DetailItem[]
  /** 通用详情布局方向 */
  direction?: 'horizontal' | 'vertical'

  /** 确认接口配置 */
  confirmApi?: {
    url: string
    method?: 'post' | 'put' | 'get'
    buildParams?: (bizId: number, remark: string) => Record<string, unknown>
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
const detailItems = ref<DetailItem[]>(props.detailItems || [])

function handleUpdateVisible(val: boolean) {
  emit('update:modelValue', val)
}

function handleOpen() {
  activeTab.value = 'detail'
}

function handleClosed() {
  remark.value = ''
}

async function handleConfirm() {
  confirmLoading.value = true
  try {
    if (props.confirmApi?.url && props.bizId != null) {
      const build = props.confirmApi.buildParams || ((id: number) => ({ bizId: id }))
      const params = build(props.bizId, remark.value.trim())
      const res: any = await request({
        url: props.confirmApi.url,
        method: props.confirmApi.method || 'post',
        data: props.confirmApi.method === 'get' ? undefined : params,
        params: props.confirmApi.method === 'get' ? params : undefined,
      })
      if (res?.code === 200) {
        ElMessage.success('操作成功')
        if (remark.value.trim()) {
          emit('remark-save', remark.value.trim())
        }
        emit('confirm-success', res.data)
        handleUpdateVisible(false)
      } else {
        ElMessage.error(res?.msg || '操作失败')
      }
    } else {
      // 无后端配置：交回调用方
      emit('confirm-success', undefined)
      handleUpdateVisible(false)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    confirmLoading.value = false
  }
}

defineExpose({ refresh: handleOpen })
</script>

<style scoped>
.biz-flow-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 12px;
}
.biz-flow-op {
  display: flex;
  align-items: center;
  gap: 8px;
}
.biz-flow-no {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.biz-flow-status {
  display: flex;
  align-items: center;
  gap: 6px;
}
.flow-arrow {
  color: #909399;
}
.biz-remark {
  margin-bottom: 4px;
}
.biz-tabs {
  margin-top: 8px;
}
</style>
