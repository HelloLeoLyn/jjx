<template>
  <div class="production-tooling">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">工装模具档案</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" v-hasPermi="['production:tooling:add']" @click="handleCreate">新增</el-button>
        <el-button icon="Upload" v-hasPermi="['production:tooling:import']" @click="importDialogVisible = true">导入</el-button>
        <el-button icon="Download" v-hasPermi="['production:tooling:export']" @click="handleExport">导出</el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-tabs v-model="query.type" @tab-change="handleSearch">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="网框" name="SCREEN" />
        <el-tab-pane label="刀模" name="DIE" />
      </el-tabs>
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索编号/名称"
          style="width: 220px"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
          <el-option
            v-for="s in ToolingStatusEnum.items"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="实物照片" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.photoId"
              :src="attachmentApi.downloadUrl(row.photoId)"
              :preview-src-list="[attachmentApi.downloadUrl(row.photoId)]"
              preview-teleported
              fit="cover"
              style="width: 44px; height: 44px; border-radius: 4px"
            />
            <span v-else class="no-photo">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="toolingNo" label="编号" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="ToolingTypeEnum.getTagProps(row.toolingType).type">
              {{ row.typeLabel || row.toolingType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="参数" min-width="180" show-overflow-tooltip />
        <el-table-column label="设计寿命(次)" width="110">
          <template #default="{ row }">
            {{ row.lifeLimit ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="ToolingStatusEnum.getTagProps(row.status).type">
              {{ row.statusLabel || ToolingStatusEnum.getLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="存放位置" width="120" show-overflow-tooltip />
        <el-table-column prop="customer" label="客户" width="110" show-overflow-tooltip />
        <el-table-column prop="responsible" label="责任人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-dropdown v-if="row.status !== 4" @command="(cmd) => handleStatus(row, cmd)">
              <el-button link type="warning">状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="s in statusOptions(row)" :key="s.value" :command="s.value">
                    {{ s.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link type="danger" v-hasPermi="['production:tooling:remove']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <ToolingFormDialog
      v-model="dialogVisible"
      :form-data="currentForm"
      :photo-id="currentPhotoId"
      @submit="handleSubmit"
    />
    <!-- 通用导入弹窗（2026-08-13） -->
    <ExcelImportDialog
      :visible="importDialogVisible"
      @update:visible="importDialogVisible = $event"
      title="导入工装模具"
      :import-api="importToolingFile"
      :template-api="downloadToolingTemplate"
      template-name="工装模具导入模板.xlsx"
      @success="loadList"
    />
  </div>

</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
  getToolingPage,
  createTooling,
  updateTooling,
  deleteTooling,
  changeToolingStatus,
  importTooling,
  downloadToolingTemplate,
  exportTooling,
  type ToolingQuery,
  type ToolingVO,
} from '@/api/production/tooling'
import { attachmentApi } from '@/api/system/attachment'
import { ToolingTypeEnum, ToolingStatusEnum } from '@/enums/production/ToolingEnum'
import { download } from '@/utils/format'
import ToolingFormDialog from './components/ToolingFormDialog.vue'

const loading = ref(false)
const list = ref<ToolingVO[]>([])
const total = ref(0)
const query = reactive<ToolingQuery>({
  pageNum: 1,
  pageSize: 10,
  type: '',
  keyword: '',
  status: undefined,
})

const dialogVisible = ref(false)
const currentForm = ref<ToolingVO | null>(null)
const currentPhotoId = ref<number | undefined>(undefined)

async function loadList() {
  loading.value = true
  try {
    const res: any = await getToolingPage(query)
    const data = res?.data || res
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadList()
}

const handleReset = () => {
  query.keyword = ''
  query.status = undefined
  query.type = ''
  query.pageNum = 1
  loadList()
}

const handleCreate = () => {
  currentForm.value = null
  currentPhotoId.value = undefined
  dialogVisible.value = true
}

const handleEdit = (row: ToolingVO) => {
  currentForm.value = row
  currentPhotoId.value = row.photoId
  dialogVisible.value = true
}

async function handleSubmit(data: { form: any; photoFile: File | null; removeOldPhoto: boolean }) {
  try {
    let id: number
    if (data.form.toolingId) {
      await updateTooling(data.form)
      id = data.form.toolingId
    } else {
      const res: any = await createTooling(data.form)
      id = res?.data
    }
    // 照片处理：传了新照片 → 先删旧的再传新的
    if (data.removeOldPhoto && currentPhotoId.value) {
      await attachmentApi.remove(currentPhotoId.value).catch(() => {})
    }
    if (data.photoFile) {
      await attachmentApi.upload(data.photoFile, 'tooling', id).catch(() => {
        ElMessage.warning('保存成功，但照片上传失败')
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

function statusOptions(row: ToolingVO) {
  // 已报废(4)不可再改状态；其余状态下拉全部可选（简单场景）
  return ToolingStatusEnum.items.filter((s) => s.value !== 4 && s.value !== row.status)
}

async function handleStatus(row: ToolingVO, status: number) {
  const label = ToolingStatusEnum.getLabel(status)
  if (status === 4) {
    await ElMessageBox.confirm(`确定将「${row.toolingName}」报废吗？报废后不可恢复。`, '报废确认', {
      type: 'warning',
      confirmButtonText: '确定报废',
    }).catch(() => Promise.reject())
  }
  try {
    await changeToolingStatus(row.toolingId, status)
    ElMessage.success(`已变更为「${label}」`)
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '状态变更失败')
  }
}

async function handleDelete(row: ToolingVO) {
  await ElMessageBox.confirm(`确定删除「${row.toolingName}」吗？`, '删除确认', {
    type: 'warning',
  }).catch(() => Promise.reject())
  try {
    await deleteTooling(row.toolingId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ===== 导入（2026-08-13 通用 ExcelImportDialog 组件） =====
const importDialogVisible = ref(false)
const importToolingFile = (file: File) => importTooling(file)

const handleExport = () => {
  ElMessageBox.confirm('是否确认导出当前筛选条件下的工装模具数据？', '导出确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      const loading = ElLoading.service({ text: '导出中...', lock: true })
      return exportTooling(query)
        .then((response: any) => {
          download(response, '工装模具档案.xlsx')
        })
        .finally(() => loading.close())
    })
    .catch(() => {})
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.production-tooling {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 8px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.no-photo {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
