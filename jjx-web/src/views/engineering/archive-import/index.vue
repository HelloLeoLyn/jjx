<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="header-row">
          <div>
            <div class="title">历史档案录入</div>
            <div class="hint">原图保留在本地，识别结果确认后生成产品、BOM和工艺路线草稿。</div>
            <el-tag size="small" :type="ocrAvailable ? 'success' : 'danger'">OCR：{{ ocrAvailable ? '已连接' : '未启动' }}</el-tag>
          </div>
          <el-upload :show-file-list="false" accept="image/jpeg,image/png" :http-request="uploadArchive">
            <el-button type="primary" :loading="uploading" v-hasPermi="['engineering:archive:import']">上传并识别</el-button>
          </el-upload>
        </div>
      </template>

      <el-alert class="upload-guide" type="info" :closable="false">
        <template #title>上传要求与图标识别说明</template>
        <div>支持 JPG/PNG；建议图片宽度 ≥ 1600px、高度 ≥ 2300px，宽高比约 0.678（允许 ±3%）。</div>
        <div>系统按固定模板比例识别：上部分默认 14 格；下部分为面板 14 格、下线 14 格、上线 6 格；工序格按比例定位，不要求固定像素。</div>
        <div>中间产品结构图按固定位置保留，不参与下方工序格切分；本功能重点识别面板、上线、下线图标对应的标准工序。</div>
        <div>图标区域按工序格宽度约 28% 裁切（自动限制 96～260px）。中间带“+”的图形会作为复合图标候选，先保留完整图标，再人工决定整体映射或拆分映射。</div>
      </el-alert>

      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="fileName" label="原始文件" min-width="230" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编号" width="160" />
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-bind="ArchiveRecognitionStatusEnum.getTagProps(row.recognizeStatus)">{{ ArchiveRecognitionStatusEnum.getLabel(row.recognizeStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recognizeMessage" label="识别信息" min-width="230" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReview(row)">查看/修正</el-button>
            <el-button v-if="row.recognizeStatus === ArchiveRecognitionStatusEnum.FAILED.value" link type="warning" @click="retry(row)">重试</el-button>
            <el-button v-if="row.recognizeStatus === ArchiveRecognitionStatusEnum.GENERATED.value" link type="warning" v-hasPermi="['engineering:archive:import']" @click="overwriteRetry(row)">覆盖重试</el-button>
            <el-button v-if="row.recognizeStatus === ArchiveRecognitionStatusEnum.REVIEW.value" link type="success" v-hasPermi="['engineering:archive:generate']" @click="generate(row)">生成草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="reviewVisible" title="识别结果确认" width="900px" destroy-on-close>
      <el-alert type="info" :closable="false" title="可直接修正 JSON。processId 留空的工序可先生成草稿，之后在工艺路线页面补选。" />
      <el-input v-model="resultText" type="textarea" :rows="20" class="json-editor" />
      <template #footer>
        <el-button @click="reviewVisible=false">关闭</el-button>
        <el-button type="primary" v-hasPermi="['engineering:archive:import']" @click="saveResult">保存修正</el-button>
        <el-button type="warning" @click="openSamples">图标映射</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sampleVisible" title="工序图标映射" width="850px">
      <el-table :data="samples">
        <el-table-column label="图标" width="100"><template #default="{ row }"><img v-if="row.previewBase64" :src="row.previewBase64" class="sample-icon" /></template></el-table-column>
        <el-table-column prop="workflowType" label="区域" width="110" />
        <el-table-column prop="stepNo" label="步骤" width="70" />
        <el-table-column label="匹配度" width="100"><template #default="{ row }">{{ row.matchScore == null ? '-' : `${Math.round(row.matchScore*100)}%` }}</template></el-table-column>
        <el-table-column label="标准工序" min-width="250">
          <template #default="{ row }">
            <el-select v-model="row.processId" filterable placeholder="选择工序" class="process-select">
              <template #label="{ label, value }">
                <span class="process-option"><SvgIcon v-if="processById(value)?.icon" :name="processById(value)!.icon!" :size="22" /><span>{{ label }}</span></span>
              </template>
              <el-option v-for="p in processes" :key="p.processId" :label="`${p.processName}（${p.processId}）`" :value="p.processId">
                <span class="process-option"><SvgIcon v-if="p.icon" :name="p.icon" :size="22" /><span>{{ p.processName }}（{{ p.processId }}）</span></span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column width="90"><template #default="{ row }"><el-button link type="primary" :disabled="!row.processId" v-hasPermi="['engineering:archive:icon-map']" @click="confirmSample(row)">确认</el-button></template></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { archiveImportApi, type ArchiveImportRecord, type IconSample } from '@/api/engineering/archiveImport'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import { ArchiveRecognitionStatusEnum } from '@/enums/engineering/archive'

const loading = ref(false), uploading = ref(false)
const rows = ref<ArchiveImportRecord[]>([]), total = ref(0), pageNum = ref(1), pageSize = ref(20)
const reviewVisible = ref(false), sampleVisible = ref(false), resultText = ref('')
const current = ref<ArchiveImportRecord>(), samples = ref<IconSample[]>([]), processes = ref<StandardProcessItem[]>([])
const ocrAvailable = ref(false)
function processById(id: number | string | undefined) { return processes.value.find(p => p.processId === Number(id)) }

function payload<T>(response: any): T { return (response?.data?.data ?? response?.data ?? response) as T }
async function load() {
  loading.value = true
  try { const page: any = payload(await archiveImportApi.page({ pageNum: pageNum.value, pageSize: pageSize.value })); rows.value = page.records || []; total.value = Number(page.total || 0) } finally { loading.value = false }
}
async function uploadArchive(options: UploadRequestOptions) {
  uploading.value = true
  try { await archiveImportApi.upload(options.file as File); ElMessage.success('档案已上传，识别结果请在列表确认'); await load() } finally { uploading.value = false }
}
function openReview(row: ArchiveImportRecord) { current.value = row; resultText.value = row.extractedJson ? JSON.stringify(JSON.parse(row.extractedJson), null, 2) : '{}'; reviewVisible.value = true }
async function saveResult() { if (!current.value) return; try { const parsed = JSON.parse(resultText.value); await archiveImportApi.updateResult(current.value.archiveId, parsed); ElMessage.success('识别结果已保存'); reviewVisible.value=false; await load() } catch (e: any) { ElMessage.error(e instanceof SyntaxError ? 'JSON格式不正确' : (e.message || '保存失败')) } }
async function retry(row: ArchiveImportRecord) { await archiveImportApi.retry(row.archiveId); ElMessage.success('已重新识别'); await load() }
async function overwriteRetry(row: ArchiveImportRecord) {
  await ElMessageBox.confirm('将重新识别原图，并删除该档案关联的产品、BOM和工艺路线草稿后重新生成。审批通过的数据不可覆盖，是否继续？', '覆盖重试', { type: 'warning' })
  await archiveImportApi.overwriteRetry(row.archiveId)
  ElMessage.success('已完成覆盖重试')
  await load()
}
async function generate(row: ArchiveImportRecord) { await ElMessageBox.confirm('将按当前识别结果创建产品、BOM和工艺路线草稿，是否继续？', '生成草稿'); await archiveImportApi.generate(row.archiveId); ElMessage.success('草稿已生成'); await load() }
async function openSamples() { if (!current.value) return; samples.value = payload(await archiveImportApi.samples(current.value.archiveId)); if (!processes.value.length) processes.value = payload(await standardProcessApi.getEnabledProcesses()); sampleVisible.value=true }
async function confirmSample(row: IconSample) { if (!row.processId) return; await archiveImportApi.confirmSample(row.sampleId, row.processId); ElMessage.success('图标样本已确认，可用于下次识别') }
onMounted(async () => { await load(); const health: any = payload(await archiveImportApi.ocrHealth()); ocrAvailable.value = health?.available === true })
</script>

<style scoped>
.header-row { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.title { font-size:18px; font-weight:600; }
.hint { margin-top:6px; color:var(--el-text-color-secondary); }
.upload-guide { margin-bottom:14px; line-height:1.7; }
.json-editor { margin-top:14px; font-family:monospace; }
.sample-icon { width:54px; height:54px; object-fit:contain; border:1px solid var(--el-border-color); }
.process-select { width: 100%; }
.process-option { display:inline-flex; align-items:center; gap:8px; min-height:28px; }
.el-pagination { margin-top:16px; justify-content:flex-end; }
</style>
