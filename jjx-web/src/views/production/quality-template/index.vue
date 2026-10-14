<template>
  <div class="quality-template-page">
    <div class="page-header">
      <h1>质量记录模板注册表</h1>
      <el-button type="primary" @click="openCreate">新增</el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="编号"><el-input v-model="query.recordNo" clearable placeholder="JJX-QR-xxx" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="query.recordName" clearable /></el-form-item>
        <el-form-item label="部门"><el-input v-model="query.ownerDept" clearable /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="query.category" clearable style="width: 130px">
            <el-option v-for="item in QualityTemplateCategoryEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 110px">
            <el-option v-for="item in QualityTemplateStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="recordNo" label="编号" width="135" />
        <el-table-column prop="recordName" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="version" label="版次" width="70" />
        <el-table-column prop="ownerDept" label="主管部门" width="110" />
        <el-table-column label="保存期限" width="90"><template #default="{ row }">{{ row.retentionYears }}年</template></el-table-column>
        <el-table-column label="类别" width="100"><template #default="{ row }">{{ QualityTemplateCategoryEnum.getLabel(row.category) }}</template></el-table-column>
        <el-table-column prop="printComponent" label="前端打印实现组件/页面路径" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">{{ row.printComponent || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bizModule" label="相关业务归属" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizModule || '-' }}</template>
        </el-table-column>
        <el-table-column label="打印实现程度" width="120">
          <template #default="{ row }">{{ getPrintModeLabel(row.printMode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="QualityTemplateStatusEnum.getTagProps(row.status).type">{{ QualityTemplateStatusEnum.getLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="模板文件" width="100">
          <template #default="{ row }"><el-link v-if="row.fileRefId" type="primary" :href="attachmentRefApi.downloadUrlByRef(row.fileRefId)" target="_blank">下载</el-link><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openUpload(row, false)">上传模板</el-button>
            <el-button link type="warning" @click="openUpload(row, true)">换版</el-button>
            <el-button v-if="row.status !== QualityTemplateStatus.ACTIVE" link type="success" @click="changeStatus(row, QualityTemplateStatus.ACTIVE)">生效</el-button>
            <el-button v-if="row.status !== QualityTemplateStatus.DISABLED" link type="warning" @click="changeStatus(row, QualityTemplateStatus.DISABLED)">停用</el-button>
            <el-button v-if="row.status === QualityTemplateStatus.DRAFT" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" class="pagination" @change="load" />
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑模板' : '新增模板'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="记录编号" required><el-input v-model="form.recordNo" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="记录名称" required><el-input v-model="form.recordName" /></el-form-item>
        <el-form-item label="版次" required><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="主管部门"><el-input v-model="form.ownerDept" /></el-form-item>
        <el-form-item label="保存期限"><el-input-number v-model="form.retentionYears" :min="1" /> 年</el-form-item>
        <el-form-item label="类别"><el-select v-model="form.category"><el-option v-for="item in QualityTemplateCategoryEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-if="form.category === QualityTemplateCategory.DATA" label="业务类型"><el-input v-model="form.bizType" /></el-form-item>
        <el-form-item label="打印组件" label-width="110px"><el-input v-model="form.printComponent" placeholder="前端打印实现组件/页面路径" /></el-form-item>
        <el-form-item label="业务归属" label-width="110px"><el-input v-model="form.bizModule" placeholder="相关业务归属" /></el-form-item>
        <el-form-item label="打印程度" label-width="110px">
          <el-select v-model="form.printMode" clearable placeholder="请选择" style="width: 100%">
            <el-option v-for="item in printModeItems" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="uploadVisible" :title="isRevision ? '模板换版' : '上传模板文件'" width="720px" destroy-on-close>
      <el-form label-width="80px"><el-form-item v-if="isRevision" label="新版本"><el-input v-model="uploadVersion" /></el-form-item></el-form>
      <!-- 走公共附件组件（来源 quality_template）；模板文件=一条引用，file_id 已退役（dev-20261014-026） -->
      <AttachmentCenter
        v-if="uploadRow?.id"
        :key="`qt-upload-${uploadRow.id}`"
        source-type="quality_template"
        :source-id="uploadRow.id"
        mode="panel"
        title="模板文件"
        view-perm="production:quality-template:doc:view"
        edit-perm="production:quality-template:doc:add"
        :accept="['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.png', '.jpg', '.jpeg']"
        @bound="onTemplateBound"
      />
      <div class="upload-tip">模板文件以引用形式保存；解除引用即从模板移除（不删物理文件）。「换版」时上传后自动更新版次。</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { changeQualityTemplateStatus, createQualityTemplate, deleteQualityTemplate, getQualityTemplatePage, updateQualityTemplate, type QualityTemplate, type QualityTemplateQuery } from '@/api/production/qualityTemplate'
import { QualityTemplateCategory, QualityTemplateCategoryEnum, QualityTemplateStatus, QualityTemplateStatusEnum } from '@/enums/production/QualityTemplateEnum'

enum PrintMode {
  BLANK = 'blank',
  SYSTEM = 'system',
  DUAL = 'dual'
}

interface QualityTemplateView extends QualityTemplate {
  printComponent?: string
  bizModule?: string
  printMode?: PrintMode
}

const printModeItems = [
  { value: PrintMode.BLANK, label: '未实现' },
  { value: PrintMode.SYSTEM, label: '系统版' },
  { value: PrintMode.DUAL, label: '双版式' }
]

const loading = ref(false), rows = ref<QualityTemplateView[]>([]), total = ref(0), formVisible = ref(false), uploadVisible = ref(false), isRevision = ref(false)
const query = reactive<QualityTemplateQuery>({ pageNum: 1, pageSize: 20 })
const emptyForm = (): QualityTemplateView => ({ recordNo: '', recordName: '', version: 'A', ownerDept: '', retentionYears: 2, category: QualityTemplateCategory.BLANK, bizType: '', printComponent: '', bizModule: '', printMode: undefined, remark: '' })
const form = reactive<QualityTemplateView>(emptyForm())
const uploadRow = ref<QualityTemplateView | null>(null), uploadVersion = ref('A')

function getPrintModeLabel(value?: PrintMode) { return printModeItems.find(item => item.value === value)?.label || '-' }

async function load() { loading.value = true; try { const res: any = await getQualityTemplatePage(query); rows.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: query.pageSize, recordNo: undefined, recordName: undefined, ownerDept: undefined, category: undefined, status: undefined }); load() }
function openCreate() { Object.assign(form, emptyForm()); formVisible.value = true }
function openEdit(row: QualityTemplateView) { Object.assign(form, emptyForm(), row); formVisible.value = true }
async function save() { if (!form.recordNo.trim() || !form.recordName.trim() || !form.version.trim()) return ElMessage.warning('请填写编号、名称和版次'); form.id ? await updateQualityTemplate(form) : await createQualityTemplate(form); ElMessage.success('保存成功'); formVisible.value = false; load() }
function nextVersion(version: string) { const value = version.trim().toUpperCase(); return /^[A-Z]$/.test(value) ? String.fromCharCode(value.charCodeAt(0) + 1) : `${version}-1` }
function openUpload(row: QualityTemplateView, revision: boolean) { uploadRow.value = row; isRevision.value = revision; uploadVersion.value = revision ? nextVersion(row.version) : row.version; uploadVisible.value = true }
async function onTemplateBound() {
  // 上传并绑定成功后刷新列表（模板文件列/hasFile 均以引用为准）；换版时同步更新版次，不关闭弹窗便于继续查看/补充。
  if (isRevision.value && uploadRow.value?.id) {
    await updateQualityTemplate({ ...uploadRow.value, version: uploadVersion.value })
  }
  await load()
}
async function changeStatus(row: QualityTemplateView, status: number) { await changeQualityTemplateStatus(row.id!, status); ElMessage.success('状态已更新'); load() }
async function remove(row: QualityTemplateView) { await ElMessageBox.confirm(`确认删除 ${row.recordNo}？`, '提示', { type: 'warning' }); await deleteQualityTemplate(row.id!); ElMessage.success('删除成功'); load() }
onMounted(load)
</script>

<style scoped>
.quality-template-page { padding: 20px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h1 { margin: 0; font-size: 22px; }
.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-form-item) { margin-bottom: 0; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.upload-tip { margin-top: 12px; color: #909399; font-size: 13px; }
</style>
