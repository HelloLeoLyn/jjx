<template>
  <el-dialog
    :model-value="visible"
    :title="'✅ 操作结果 · ' + (data?.actionName || '操作成功')"
    width="560px"
    append-to-body
    @update:model-value="onClose"
  >
    <div v-if="data">
      <!-- 视图切换 -->
      <div style="display:flex;gap:8px;margin-bottom:14px">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="card">标准卡</el-radio-button>
          <el-radio-button value="doc" v-if="data.docType === 'express' || data.docType === 'audit'">单据</el-radio-button>
          <el-radio-button value="chat">聊天通知</el-radio-button>
          <el-radio-button value="markdown">文档登记</el-radio-button>
        </el-radio-group>
      </div>

      <!-- ① 标准结果卡 -->
      <div v-if="viewMode === 'card'" style="border:1px solid #e4e7ed;border-radius:8px;padding:16px">
        <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
          <el-icon :size="28" color="#67c23a"><CircleCheckFilled /></el-icon>
          <span style="font-size:16px;font-weight:600">{{ data.actionName }}</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="单据号" :span="2">{{ data.docNo }}</el-descriptions-item>
          <el-descriptions-item label="操作前">
            <el-tag size="small" type="info">{{ data.fromStatus }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作后">
            <el-tag size="small" type="success">{{ data.toStatus }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ data.operator }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ data.time }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2" v-if="data.remark">{{ data.remark }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="data.nextSteps && data.nextSteps.length" style="margin-top:12px;background:#f5f7fa;border-radius:6px;padding:10px 12px">
          <div style="font-size:12px;color:#909399;margin-bottom:6px">下一步</div>
          <div v-for="(s, i) in data.nextSteps" :key="i" style="font-size:13px;color:#606266;line-height:1.9">
            {{ i + 1 }}. {{ s }}
          </div>
        </div>
      </div>

      <!-- ② 单据视图（审核单/快递单样式） -->
      <div v-else-if="viewMode === 'doc'" style="border:2px solid #333;border-radius:4px;padding:20px;background:#fff">
        <template v-if="data.docType === 'express'">
          <div style="text-align:center;font-size:15px;font-weight:700;letter-spacing:4px;margin-bottom:14px">快 递 单</div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 20px;font-size:13px">
            <div><span style="color:#909399">快递单号：</span>{{ data.express?.trackingNo || '-' }}</div>
            <div><span style="color:#909399">收件人：</span>{{ data.express?.receiver || data.customerName || '-' }}</div>
            <div><span style="color:#909399">联系电话：</span>{{ data.express?.phone || data.contactPhone || '-' }}</div>
            <div><span style="color:#909399">寄件日期：</span>{{ data.time }}</div>
            <div style="grid-column:1/3"><span style="color:#909399">收件地址：</span>{{ data.express?.address || '-' }}</div>
            <div style="grid-column:1/3"><span style="color:#909399">内件说明：</span>{{ data.docNo }} 样品（{{ data.express?.qty || data.sampleQty || '' }}件）</div>
          </div>
          <div style="margin-top:16px;text-align:center;color:#999;font-size:12px">样品送样登记单</div>
        </template>
        <template v-else-if="data.docType === 'audit'">
          <div style="text-align:center;font-size:15px;font-weight:700;letter-spacing:4px;margin-bottom:14px">审 核 单</div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 20px;font-size:13px">
            <div><span style="color:#909399">单据号：</span>{{ data.docNo }}</div>
            <div><span style="color:#909399">审核结果：</span><span style="color:#67c23a;font-weight:600">{{ data.actionName }}</span></div>
            <div><span style="color:#909399">审核人：</span>{{ data.operator }}</div>
            <div><span style="color:#909399">审核时间：</span>{{ data.time }}</div>
            <div><span style="color:#909399">状态变化：</span>{{ data.fromStatus }} → {{ data.toStatus }}</div>
            <div v-if="data.remark" style="grid-column:1/3"><span style="color:#909399">审核意见：</span>{{ data.remark }}</div>
          </div>
          <div style="margin-top:16px;text-align:center;color:#999;font-size:12px">JJX ERP 审核凭证</div>
        </template>
      </div>

      <!-- ③ 聊天通知视图 -->
      <div v-else-if="viewMode === 'chat'" style="background:#f5f7fa;border-radius:8px;padding:14px">
        <div style="display:flex;gap:10px;align-items:flex-start">
          <div style="width:36px;height:36px;border-radius:50%;background:#409eff;color:#fff;display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0">系</div>
          <div>
            <div style="font-size:12px;color:#909399;margin-bottom:4px">系统通知 · {{ data.time }}</div>
            <div style="background:#fff;border-radius:0 8px 8px 8px;padding:10px 12px;font-size:13px;color:#303133;line-height:1.8;box-shadow:0 1px 3px rgba(0,0,0,0.08)">
              <b>【{{ data.actionName }}】</b>{{ data.chatText || `${data.docNo} 状态由「${data.fromStatus}」变为「${data.toStatus}」` }}
              <div v-if="data.operator" style="margin-top:4px;color:#909399;font-size:12px">操作人：{{ data.operator }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ④ 文档登记视图 -->
      <div v-else-if="viewMode === 'markdown'">
        <div style="background:#282c34;border-radius:6px;padding:14px;color:#abb2bf;font-size:12px;line-height:1.9;white-space:pre-wrap;max-height:260px;overflow-y:auto;font-family:monospace">{{ markdownText }}</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="copyView" type="primary" plain>📋 复制当前视图</el-button>
      <el-button @click="onClose(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'

export interface OperationResultData {
  actionName: string      // 操作名，如"审核通过"
  docNo: string           // 单据号
  fromStatus: string      // 操作前状态
  toStatus: string        // 操作后状态
  operator?: string       // 操作人
  time?: string           // 操作时间
  remark?: string         // 备注/意见
  nextSteps?: string[]    // 下一步指引
  docType?: 'audit' | 'express' | 'normal'  // 单据视图类型
  express?: { trackingNo?: string; receiver?: string; phone?: string; address?: string; qty?: number }
  customerName?: string
  contactPhone?: string
  sampleQty?: number
  chatText?: string       // 聊天视图文案（可选）
}

const props = defineProps<{
  visible: boolean
  data: OperationResultData | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const viewMode = ref<'card' | 'doc' | 'chat' | 'markdown'>('card')

watch(() => props.visible, (v) => {
  if (v) viewMode.value = 'card'
})

function onClose(val: boolean) {
  if (!val) emit('update:visible', false)
}

const markdownText = computed(() => {
  const d = props.data
  if (!d) return ''
  return [
    `### ${d.actionName} (${d.time || ''})`,
    ``,
    `- 单据号: ${d.docNo}`,
    `- 状态: ${d.fromStatus} → ${d.toStatus}`,
    `- 操作人: ${d.operator || '-'}`,
    d.remark ? `- 备注: ${d.remark}` : '',
    d.nextSteps?.length ? `- 下一步: ${d.nextSteps.join(' → ')}` : '',
    d.docType === 'express' && d.express ? `- 快递单号: ${d.express.trackingNo || '-'}` : '',
  ].filter(Boolean).join('\n')
})

function copyView() {
  const d = props.data
  if (!d) return
  let text = ''
  if (viewMode.value === 'markdown') {
    text = markdownText.value
  } else if (viewMode.value === 'card') {
    text = [
      `✅ ${d.actionName}`,
      `单据号: ${d.docNo}`,
      `状态: ${d.fromStatus} → ${d.toStatus}`,
      `操作人: ${d.operator || '-'}  时间: ${d.time || ''}`,
      d.remark ? `备注: ${d.remark}` : '',
      d.nextSteps?.length ? `下一步: ${d.nextSteps.join(' → ')}` : '',
    ].filter(Boolean).join('\n')
  } else if (viewMode.value === 'doc') {
    text = d.docType === 'express'
      ? `【快递单】单号: ${d.express?.trackingNo || '-'} | 收件人: ${d.express?.receiver || d.customerName || '-'} | 日期: ${d.time}`
      : `【审核单】${d.actionName} | ${d.docNo} | ${d.fromStatus}→${d.toStatus} | ${d.operator} ${d.time}${d.remark ? ' | ' + d.remark : ''}`
  } else {
    text = `【系统通知】${d.chatText || `${d.docNo} ${d.fromStatus} → ${d.toStatus}`}（${d.time}）`
  }
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制')
  }).catch(() => {
    ElMessage.warning('复制失败，请手动选择')
  })
}
</script>
