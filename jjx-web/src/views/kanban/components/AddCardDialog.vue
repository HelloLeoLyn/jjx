<template>
  <el-dialog
    v-model="visibleModel"
    :title="`新建${templateName}卡片`"
    width="480px"
    @close="onClose"
  >
    <el-form :model="form" label-width="80px" size="small">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="请输入卡片标题" />
      </el-form-item>

      <template v-if="templateType === 'production'">
        <el-form-item label="工单号">
          <el-input v-model="form.workOrderNo" placeholder="自动生成" disabled />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="form.productName" placeholder="如：薄膜开关-MK12" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="0" :step="100" />
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="form.customer" placeholder="客户名称" />
        </el-form-item>
      </template>

      <template v-if="templateType === 'office'">
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option label="采购" value="采购" />
            <el-option label="销售" value="销售" />
            <el-option label="设计" value="设计" />
            <el-option label="跟单" value="跟单" />
            <el-option label="行政" value="行政" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.department" style="width: 100%">
            <el-option label="销售部" value="销售部" />
            <el-option label="采购部" value="采购部" />
            <el-option label="设计部" value="设计部" />
            <el-option label="品质部" value="品质部" />
            <el-option label="生产管理" value="生产管理" />
          </el-select>
        </el-form-item>
      </template>

      <template v-if="templateType === 'emergency'">
        <el-form-item label="紧急类型">
          <el-select v-model="form.urgencyType" style="width: 100%">
            <el-option label="返工" value="返工" />
            <el-option label="急单" value="急单" />
            <el-option label="插单" value="插单" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="form.sourceOrderNo" placeholder="关联单号" />
        </el-form-item>
        <el-form-item label="原因说明">
          <el-input v-model="form.reason" type="textarea" :rows="2" />
        </el-form-item>
      </template>

      <el-form-item label="负责人">
        <el-select v-model="form.assignee" style="width: 100%">
          <el-option label="张三" value="张三" />
          <el-option label="李四" value="李四" />
          <el-option label="王五" value="王五" />
          <el-option label="赵六" value="赵六" />
          <el-option label="陈七" value="陈七" />
        </el-select>
      </el-form-item>

      <el-form-item label="截止日期">
        <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>

      <el-form-item label="优先级">
        <el-select v-model="form.priority" style="width: 100%">
          <el-option label="🔥 紧急" value="urgent" />
          <el-option label="⏫ 高" value="high" />
          <el-option label="➖ 普通" value="normal" />
          <el-option label="⬇️ 低" value="low" />
        </el-select>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>

      <el-form-item label="截图">
        <div class="screenshot-upload" @paste="handlePaste">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            multiple
            :on-change="handleFileChange"
          >
            <div class="upload-tip">
              <el-icon style="font-size: 20px"><Picture /></el-icon>
              <span>点击选择 或 Ctrl+V 粘贴截图</span>
            </div>
          </el-upload>
          <div v-if="form.screenshots.length > 0" class="screenshot-preview">
            <div v-for="(img, idx) in form.screenshots" :key="idx" class="preview-item">
              <el-image :src="img.url" fit="cover" class="preview-thumb" preview-teleported :preview-src-list="form.screenshots.map(s => s.url)" :initial-index="idx" />
              <el-icon class="preview-remove" @click="form.screenshots.splice(idx, 1)"><Close /></el-icon>
            </div>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" @click="onSubmit" :loading="submitting">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Close } from '@element-plus/icons-vue'
import type { TemplateType, BoardCard, Priority } from '@/views/kanban/types/board'

const props = defineProps<{
  visible: boolean
  templateType: TemplateType
  targetColumnId: string
  targetColumnLabel: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  create: [card: Partial<BoardCard>, targetColumnId: string]
}>()

const submitting = ref(false)

const visibleModel = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

const templateName = computed(() => {
  const map: Record<string, string> = { production: '工单', office: '任务', emergency: '紧急任务' }
  return map[props.templateType] ?? '卡片'
})

const form = reactive({
  title: '',
  workOrderNo: '',
  productName: '',
  quantity: 0,
  customer: '',
  taskType: '',
  department: '',
  urgencyType: '',
  sourceOrderNo: '',
  reason: '',
  assignee: '',
  deadline: '',
  priority: 'normal' as Priority,
  remark: '',
  screenshots: [] as { file: File; url: string }[],
})

watch(() => props.visible, (val) => {
  if (!val) {
    // 关闭时不清空，下次打开再重置
  } else {
    // 自动生成工单号
    if (props.templateType === 'production' && !form.workOrderNo) {
      const seq = String(Date.now()).slice(-6)
      form.workOrderNo = `WO-202607-${seq}`
    }
  }
})

function resetForm() {
  form.title = ''
  form.workOrderNo = ''
  form.productName = ''
  form.quantity = 0
  form.customer = ''
  form.taskType = ''
  form.department = ''
  form.urgencyType = ''
  form.sourceOrderNo = ''
  form.reason = ''
  form.assignee = ''
  form.deadline = ''
  form.priority = 'normal'
  form.remark = ''
  form.screenshots = []
}

// 选择文件（含拖入）
function handleFileChange(uploadFile: any) {
  const file: File | undefined = uploadFile?.raw
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return
  }
  form.screenshots.push({ file, url: URL.createObjectURL(file) })
}

// 粘贴截图（Ctrl+V）
function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items || []
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        form.screenshots.push({ file, url: URL.createObjectURL(file) })
      }
    }
  }
  if (form.screenshots.length > 0) {
    ElMessage.success(`已添加 ${form.screenshots.length} 张截图`)
  }
}

function onClose() {
  visibleModel.value = false
}

async function onSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }

  submitting.value = true

  const today = new Date().toISOString().slice(0, 10)

  const newCard: Partial<BoardCard> = {
    id: `NEW-${Date.now()}`,
    title: form.title,
    templateType: props.templateType,
    priority: form.priority,
    status: 'pending',
    assignee: form.assignee || '未分配',
    deadline: form.deadline || today,
    remark: form.remark,
    createdAt: today,
    updatedAt: today,
    ...(props.templateType === 'production' && {
      workOrderNo: form.workOrderNo,
      productName: form.productName,
      quantity: form.quantity,
      customer: form.customer,
      currentProcess: props.targetColumnLabel,
    }),
    ...(props.templateType === 'office' && {
      taskType: form.taskType,
      department: form.department,
      status: 'pending' as const,
    }),
    ...(props.templateType === 'emergency' && {
      urgencyType: form.urgencyType,
      sourceOrderNo: form.sourceOrderNo,
      reason: form.reason,
      currentProcess: props.targetColumnLabel,
    }),
    screenshots: form.screenshots.map((s) => ({ file: s.file })),
  }

  emit('create', newCard, props.targetColumnId)
  resetForm()

  // 延迟关闭，避免闪烁
  setTimeout(() => {
    submitting.value = false
    visibleModel.value = false
  }, 200)
}
</script>

<style scoped>
.screenshot-upload {
  width: 100%;
}

.upload-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 14px 0;
  color: #909399;
  cursor: pointer;
  font-size: 13px;
}

.upload-tip:hover {
  border-color: #409eff;
  color: #409eff;
}

.screenshot-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.preview-item {
  position: relative;
}

.preview-thumb {
  width: 80px;
  height: 60px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  cursor: zoom-in;
}

.preview-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #f56c6c;
  color: #fff;
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
  font-size: 12px;
}
</style>
