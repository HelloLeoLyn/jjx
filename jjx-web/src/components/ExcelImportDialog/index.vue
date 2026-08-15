<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="560px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
    @closed="reset"
  >
    <!-- 说明 + 模板下载 -->
    <el-alert :title="tip" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
    <div style="margin-bottom: 10px">
      <el-button type="primary" plain :loading="templateLoading" @click="handleDownloadTemplate">
        下载导入模板
      </el-button>
    </div>

    <!-- 文件选择 -->
    <el-upload
      drag
      :auto-upload="false"
      :limit="1"
      :accept="accept"
      :on-change="onFileChange"
      :on-exceed="onExceed"
      :on-remove="onFileRemove"
      style="width: 100%"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 {{ accept }}，单次一个文件</div>
      </template>
    </el-upload>

    <!-- 导入结果 -->
    <div v-if="result" class="imp-result" :class="result.failCount > 0 ? 'imp-result-fail' : 'imp-result-ok'">
      <div class="imp-result-summary">
        <el-tag type="success" size="small">成功 {{ result.successCount }}</el-tag>
        <el-tag v-if="result.skipCount" type="info" size="small">跳过 {{ result.skipCount }}</el-tag>
        <el-tag v-if="result.failCount" type="danger" size="small">失败 {{ result.failCount }}</el-tag>
        <span v-if="result.message" style="margin-left: 8px; color: #606266">{{ result.message }}</span>
      </div>
      <template v-if="result.failDetails.length">
        <el-table :data="result.failDetails" size="small" max-height="200" style="margin-top: 8px">
          <el-table-column label="行号" width="70" align="center">
            <template #default="{ row }">{{ row.rowIndex ?? row.row ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="名称/编码" min-width="140">
            <template #default="{ row }">{{ row.materialName ?? row.name ?? row.code ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="200">
            <template #default="{ row }">{{ row.reason ?? '-' }}</template>
          </el-table-column>
        </el-table>
        <el-button link type="danger" style="margin-top: 6px" @click="downloadFailDetail">
          下载失败明细
        </el-button>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selectedFile"
        :loading="importing"
        @click="handleImport"
      >
        开始导入{{ selectedFile ? `（${selectedFile.name}）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { download } from '@/utils/format'

interface FailRow {
  rowIndex?: number
  row?: number
  materialName?: string
  name?: string
  code?: string
  reason?: string
}

interface ImportResult {
  successCount: number
  skipCount: number
  failCount: number
  failDetails: FailRow[]
  message?: string
}

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    tip?: string
    accept?: string
    /** 导入接口：接收 File，返回 Promise（后端 Result 结构） */
    importApi: (file: File) => Promise<any>
    /** 模板下载接口：返回 blob Promise */
    templateApi?: () => Promise<any>
    /** 模板下载文件名 */
    templateName?: string
  }>(),
  {
    title: '导入',
    tip: '请先下载模板按格式填写，再选择 Excel 文件导入。',
    accept: '.xlsx,.xls',
    templateName: '导入模板.xlsx',
  },
)

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success'): void
}>()

const selectedFile = ref<File | null>(null)
const importing = ref(false)
const templateLoading = ref(false)
const result = ref<ImportResult | null>(null)

const onFileChange = (file: any) => {
  selectedFile.value = file.raw || null
  result.value = null
}
const onExceed = (files: any[]) => {
  selectedFile.value = files[0]?.raw || null
  result.value = null
}
const onFileRemove = () => {
  selectedFile.value = null
  result.value = null
}

const handleDownloadTemplate = async () => {
  if (!props.templateApi) return
  templateLoading.value = true
  try {
    const res: any = await props.templateApi()
    download(res, props.templateName)
  } catch {
    ElMessage.error('模板下载失败')
  } finally {
    templateLoading.value = false
  }
}

// 兼容多种返回结构：string 消息 / MaterialImportResultVO 等结构化结果
function parseResult(data: any): ImportResult {
  if (data === null || data === undefined) return { successCount: 0, skipCount: 0, failCount: 0, failDetails: [] }
  if (typeof data === 'string') return { successCount: 0, skipCount: 0, failCount: 0, failDetails: [], message: data }
  const d = data
  return {
    successCount: d.successCount ?? d.successNum ?? d.success ?? 0,
    skipCount: d.skipCount ?? d.skipNum ?? 0,
    failCount: d.failCount ?? d.failNum ?? d.fail ?? 0,
    failDetails: (d.failDetails ?? d.failList ?? d.errors ?? []).map((f: any) => ({
      rowIndex: f.rowIndex ?? f.row,
      materialName: f.materialName ?? f.name ?? f.code,
      reason: f.reason,
    })),
    message: d.message ?? d.msg,
  }
}

const handleImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的 Excel 文件')
    return
  }
  importing.value = true
  try {
    const res: any = await props.importApi(selectedFile.value)
    result.value = parseResult(res?.data)
    if (!result.value.failCount) {
      ElMessage.success(result.value.message || '导入成功')
    } else {
      ElMessage.warning(`导入完成：成功 ${result.value.successCount} 条，失败 ${result.value.failCount} 条`)
    }
    emit('success')
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const downloadFailDetail = () => {
  if (!result.value) return
  const lines = result.value.failDetails.map(
    (f) => `第${f.rowIndex ?? f.row ?? '-'}行\t${f.materialName ?? f.name ?? f.code ?? '-'}\t${f.reason ?? ''}`,
  )
  const blob = new Blob(['行号\t名称/编码\t失败原因\n' + lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  download(blob, '导入失败明细.txt')
}

const reset = () => {
  selectedFile.value = null
  result.value = null
  importing.value = false
}
</script>

<style scoped>
.imp-result {
  margin-top: 12px;
  padding: 10px;
  border-radius: 6px;
  background: #fafafa;
}
.imp-result-ok {
  border: 1px solid #e1f3d8;
  background: #f0f9eb;
}
.imp-result-fail {
  border: 1px solid #fde2e2;
  background: #fef0f0;
}
.imp-result-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
