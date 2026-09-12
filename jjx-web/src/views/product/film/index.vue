<template>
  <div class="app-container">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="产品">
          <div style="width: 260px">
            <ProductSelector
              v-model="queryProductId"
              :options="productOptions"
              value-type="productId"
              placeholder="不选=全部产品"
              @change="onProductChange"
            />
          </div>
        </el-form-item>
        <el-form-item label="菲林类型">
          <el-select v-model="queryFilmType" placeholder="全部" clearable style="width: 150px" @change="loadFilms">
            <el-option
              v-for="t in FilmTypeCodeEnum.items"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryApproveStatus" placeholder="全部" clearable style="width: 130px" @change="loadFilms">
            <el-option
              v-for="s in FilmApproveStatusEnum.items"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="queryKeyword"
            placeholder="菲林编码/名称/产品"
            clearable
            style="width: 190px"
            @keyup.enter="loadFilms"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadFilms">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button
          type="primary"
          :icon="Plus"
          v-hasPermi="['engineering:film:edit']"
          @click="handleAdd"
        >
          新增菲林
        </el-button>
        <span class="toolbar-tip">不选产品时展示全部菲林；新增时在表单内选择产品</span>
      </div>

      <el-table :data="filmList" v-loading="loading" border stripe>
        <el-table-column label="菲林编码" prop="filmCode" width="170" show-overflow-tooltip />
        <el-table-column label="产品" min-width="140" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.productCode }} {{ scope.row.productName }}
          </template>
        </el-table-column>
        <el-table-column label="菲林名称" prop="filmName" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="scope">
            {{ scope.row.filmTypeName || filmTypeLabel(scope.row.filmType) }}
          </template>
        </el-table-column>
        <el-table-column label="版本" prop="version" width="110" align="center">
          <template #default="scope">
            <span>{{ scope.row.version }}</span>
            <el-tag
              v-if="scope.row.isCurrent === FilmCurrentFlagEnum.CURRENT.value"
              size="small"
              type="success"
              style="margin-left: 4px"
            >
              当前
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尺寸" prop="filmSize" width="100" show-overflow-tooltip />
        <el-table-column label="厚度" width="80" align="center">
          <template #default="scope">{{ scope.row.filmThickness ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="图纸" width="90" align="center">
          <template #default="scope">
            <el-link
              v-if="scope.row.fileId"
              type="primary"
              :href="downloadUrl(scope.row.fileId)"
              target="_blank"
              underline="never"
            >
              下载
            </el-link>
            <span v-else class="muted">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="FilmApproveStatusEnum.getTagProps(scope.row.approveStatus).type" size="small">
              {{ FilmApproveStatusEnum.getLabel(scope.row.approveStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下发生产" width="90" align="center">
          <template #default="scope">
            <el-tag :type="FilmReleaseStatusEnum.getTagProps(scope.row.isReleased ?? 0).type" size="small">
              {{ FilmReleaseStatusEnum.getLabel(scope.row.isReleased ?? 0) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设计师" prop="designerName" width="90" />
        <el-table-column label="创建时间" width="150">
          <template #default="scope">{{ scope.row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="scope">
            <template v-if="isEditable(scope.row)">
              <el-button link type="primary" size="small" v-hasPermi="['engineering:film:edit']" @click="handleEdit(scope.row)">
                编辑
              </el-button>
              <el-button link type="warning" size="small" v-hasPermi="['engineering:film:submit']" @click="handleSubmit(scope.row)">
                提交审批
              </el-button>
            </template>
            <template v-else-if="scope.row.approveStatus === FilmApproveStatusEnum.PENDING.value">
              <el-button link type="success" size="small" v-hasPermi="['engineering:film:approve']" @click="handleApprove(scope.row)">
                通过
              </el-button>
              <el-button link type="danger" size="small" v-hasPermi="['engineering:film:reject']" @click="handleReject(scope.row)">
                驳回
              </el-button>
            </template>
            <template v-else-if="scope.row.approveStatus === FilmApproveStatusEnum.APPROVED.value">
              <el-button link type="primary" size="small" v-hasPermi="['engineering:film:edit']" @click="handleSetCurrent(scope.row)">
                设当前
              </el-button>
              <el-button
                v-if="scope.row.isReleased !== FilmReleaseStatusEnum.RELEASED.value"
                link
                type="success"
                size="small"
                v-hasPermi="['engineering:film:release']"
                @click="handleRelease(scope.row)"
              >
                下发生产
              </el-button>
            </template>
            <el-button link type="primary" size="small" v-hasPermi="['engineering:film:edit']" @click="handleNewVersion(scope.row)">
              新版本
            </el-button>
            <el-button
              v-if="isEditable(scope.row)"
              link
              type="danger"
              size="small"
              v-hasPermi="['engineering:film:delete']"
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filmList.length" description="暂无菲林数据" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.filmId ? '编辑菲林' : '新增菲林'" v-model="dialogVisible" width="680px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="关联产品" prop="productId">
          <ProductSelector
            :model-value="form.productId ?? null"
            @update:model-value="form.productId = Number($event) || undefined"
            :options="productOptions"
            value-type="productId"
            :disabled="Boolean(form.filmId)"
            placeholder="请选择产品"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菲林名称" prop="filmName">
              <el-input v-model="form.filmName" placeholder="如：面板菲林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="filmType">
              <el-select v-model="form.filmType" placeholder="选择类型" style="width: 100%">
                <el-option
                  v-for="t in FilmTypeCodeEnum.items"
                  :key="t.value"
                  :label="t.label"
                  :value="t.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="尺寸">
              <el-input v-model="form.filmSize" placeholder="如：50x30mm" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="厚度">
              <el-input-number
                v-model="form.filmThickness"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%"
                placeholder="mm"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="材料">
              <el-input v-model="form.filmMaterial" placeholder="如：PET" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="颜色">
          <el-input v-model="form.color" placeholder="如：透明白" />
        </el-form-item>
        <el-form-item label="菲林图纸">
          <div class="upload-row">
            <el-upload
              :show-file-list="false"
              :before-upload="beforeUpload"
              :http-request="doUpload"
              :disabled="uploading"
            >
              <el-button :loading="uploading" :icon="Upload">
                {{ form.fileId ? '重新上传' : '上传图纸' }}
              </el-button>
            </el-upload>
            <span v-if="form.fileName" class="file-tip">
              <el-link type="primary" :href="downloadUrl(form.fileId)" target="_blank" underline="never">
                {{ form.fileName }}
              </el-link>
              <el-button link type="danger" size="small" @click="clearFile">移除</el-button>
            </span>
            <span v-else class="muted">未上传</span>
          </div>
          <div class="form-tip">支持 PDF/DWG/DXF/AI/CDR/图片（≤50MB）；上传后归档到产品文件库「菲林」类别</div>
        </el-form-item>
        <el-form-item label="技术规格">
          <el-input v-model="form.technicalSpec" type="textarea" :rows="2" placeholder="技术规格说明" />
        </el-form-item>
        <el-form-item label="设计说明">
          <el-input v-model="form.designNotes" type="textarea" :rows="2" placeholder="设计备注" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitForm">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Plus, Upload } from '@element-plus/icons-vue'
import { filmApi, type EngineeringFilm } from '@/api/product/film'
import { listProductPage } from '@/api/product'
import { attachmentApi } from '@/api/system/attachment'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import {
  FilmApproveStatusEnum,
  FilmTypeCodeEnum,
  FilmReleaseStatusEnum,
  FilmCurrentFlagEnum,
} from '@/enums/product/film'

defineOptions({ name: 'EngineeringFilm' })

const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const filmList = ref<EngineeringFilm[]>([])
const productOptions = ref<any[]>([])
const queryProductId = ref<number | null>(null)
const queryFilmType = ref<string>('')
const queryApproveStatus = ref<number | null>(null)
const queryKeyword = ref('')
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<Partial<EngineeringFilm>>({
  filmId: undefined,
  filmName: '',
  filmType: '',
  filmSize: '',
  filmThickness: undefined,
  filmMaterial: '',
  color: '',
  technicalSpec: '',
  designNotes: '',
  fileId: undefined,
  fileName: '',
  filePath: '',
  remark: '',
})

const rules = {
  productId: [{ required: true, message: '请选择关联产品', trigger: 'change' }],
  filmName: [{ required: true, message: '菲林名称不能为空', trigger: 'blur' }],
  filmType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

/** 草稿或已驳回可编辑/提交 */
function isEditable(row: EngineeringFilm): boolean {
  const editable: number[] = [FilmApproveStatusEnum.DRAFT.value, FilmApproveStatusEnum.REJECTED.value]
  return editable.includes(Number(row.approveStatus ?? FilmApproveStatusEnum.DRAFT.value))
}

function filmTypeLabel(code?: string): string {
  return code ? FilmTypeCodeEnum.getLabel(code) : '-'
}

function downloadUrl(fileId?: number | string | null): string {
  return fileId ? attachmentApi.downloadUrl(Number(fileId)) : ''
}

// 加载产品下拉
async function loadProducts() {
  try {
    const res: any = await listProductPage({ pageNum: 1, pageSize: 200 })
    productOptions.value = res?.data?.records || res?.data?.rows || res?.data || []
  } catch {
    productOptions.value = []
  }
}

// 加载菲林列表（不选产品时=全部）
async function loadFilms() {
  loading.value = true
  try {
    const res: any = await filmApi.list({
      productId: queryProductId.value || undefined,
      filmType: queryFilmType.value || undefined,
      approveStatus: queryApproveStatus.value ?? undefined,
      keyword: queryKeyword.value || undefined,
    })
    filmList.value = (res as any)?.data || []
  } catch {
    filmList.value = []
  } finally {
    loading.value = false
  }
}

function onProductChange() {
  loadFilms()
}

function resetQuery() {
  queryProductId.value = null
  queryFilmType.value = ''
  queryApproveStatus.value = null
  queryKeyword.value = ''
  loadFilms()
}

function handleAdd() {
  Object.assign(form, {
    filmId: undefined,
    productId: queryProductId.value ?? undefined,
    filmName: '',
    filmType: '',
    filmSize: '',
    filmThickness: undefined,
    filmMaterial: '',
    color: '',
    technicalSpec: '',
    designNotes: '',
    fileId: undefined,
    fileName: '',
    filePath: '',
    remark: '',
  })
  dialogVisible.value = true
}

async function handleEdit(row: EngineeringFilm) {
  try {
    const res: any = await filmApi.getById(row.filmId!)
    Object.assign(form, res?.data || row)
  } catch {
    Object.assign(form, row)
  }
  dialogVisible.value = true
}

function beforeUpload(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过50MB')
    return false
  }
  return true
}

/** 图纸上传：先落产品文件库（菲林类别），把附件 id 回填到菲林档案 */
async function doUpload(options: any) {
  const productId = form.productId || queryProductId.value
  const productCode = productId
    ? productOptions.value.find((p: any) => p.productId === productId)?.productCode
    : undefined
  if (!productCode) {
    ElMessage.warning('请先选择产品再上传图纸')
    options.onError(new Error('no product'))
    return
  }
  uploading.value = true
  try {
    const res: any = await attachmentApi.uploadProductFile(options.file, productCode, '菲林')
    if (res?.code === 200) {
      form.fileId = Number(res.data)
      form.fileName = options.file.name
      ElMessage.success('图纸上传成功')
      options.onSuccess(res.data)
    } else {
      ElMessage.error(res?.msg || '上传失败')
      options.onError(new Error(res?.msg || '上传失败'))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
    options.onError(e)
  } finally {
    uploading.value = false
  }
}

function clearFile() {
  form.fileId = undefined
  form.fileName = ''
  form.filePath = ''
}

async function handleSubmitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form, productId: form.productId }
    if (form.filmId) {
      await filmApi.update(form.filmId, payload)
      ElMessage.success('修改成功')
    } else {
      await filmApi.create(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadFilms()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmit(row: EngineeringFilm) {
  await filmApi.submitApprove(row.filmId!)
  ElMessage.success('已提交审批')
  loadFilms()
}

async function handleApprove(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认通过菲林「${row.filmName}」？`, '审批通过')
  await filmApi.approve(row.filmId!)
  ElMessage.success('已通过')
  loadFilms()
}

async function handleReject(row: EngineeringFilm) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '审批驳回', {
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (val: string) => (val && val.trim() ? true : '驳回原因不能为空'),
    })
    await filmApi.reject(row.filmId!, value)
    ElMessage.success('已驳回')
    loadFilms()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '驳回失败')
  }
}

async function handleNewVersion(row: EngineeringFilm) {
  try {
    const { value } = await ElMessageBox.prompt(
      `为「${row.filmName}」创建新版本（留空自动 +0.1）`,
      '新版本',
      { inputPlaceholder: '如 v1.1', inputValue: '' }
    )
    await filmApi.newVersion(row.filmId!, value || undefined, undefined)
    ElMessage.success('新版本已创建')
    loadFilms()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '创建新版本失败')
  }
}

async function handleSetCurrent(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认将版本 ${row.version} 设为当前？`, '设为当前')
  await filmApi.setCurrent(row.filmId!)
  ElMessage.success('已设为当前版本')
  loadFilms()
}

async function handleRelease(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认将「${row.filmName}」下发生产？`, '下发生产', { type: 'warning' })
  await filmApi.release(row.filmId!)
  ElMessage.success('已下发生产')
  loadFilms()
}

async function handleDelete(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认删除菲林「${row.filmName}」？`, '删除确认', { type: 'warning' })
  await filmApi.remove(row.filmId!)
  ElMessage.success('已删除')
  loadFilms()
}

onMounted(async () => {
  await loadProducts()
  loadFilms()
})
</script>

<style scoped>
.search-card,
.table-card {
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-tip {
  color: #909399;
  font-size: 12px;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.file-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
.muted {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
