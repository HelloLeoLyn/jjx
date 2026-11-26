<template>
  <div class="print-resources-page">
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="框号/型号/位置" clearable style="width: 220px" @keyup.enter="load(1)" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px" @change="load(1)">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="tip">网框与「网版管理」同一张表；这里管理已登记新模型的网框：绷网、制版、洗版、维护。</div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="frameNo" label="网框编号" width="150" />
        <el-table-column prop="frameType" label="型号" width="110" />
        <el-table-column prop="mesh" label="目数" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="当前版面" width="150" />
        <el-table-column prop="location" label="位置" min-width="130" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openStretch(row)">绷网</el-button>
            <el-button link type="success" size="small" @click="openPlate(row)">制版</el-button>
            <el-button link type="warning" size="small" @click="wash(row)">洗版</el-button>
            <el-dropdown @command="(cmd: string) => maintain(row, cmd)">
              <el-button link type="info" size="small">维护</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="maintain">送修</el-dropdown-item>
                  <el-dropdown-item command="resume">恢复使用</el-dropdown-item>
                  <el-dropdown-item command="scrap">报废</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :total="page.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="(p: number) => load(p)"
        @size-change="() => load(1)"
      />
    </el-card>

    <el-drawer v-model="detailVisible" size="760px" title="网框详情" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="框号">{{ detail.frameNo }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ detail.frameType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="位置">{{ detail.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="section-head">绷网记录</div>
          <el-table :data="detail.stretches || []" size="small" border>
            <el-table-column prop="mesh" label="目数" width="90" />
            <el-table-column prop="tensionNcm" label="张力(N/cm)" width="110" />
            <el-table-column prop="material" label="网纱" min-width="120" />
            <el-table-column label="合格" width="80">
              <template #default="{ row }">{{ row.qualified ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="stretchTime" label="绷网时间" width="160" />
            <el-table-column prop="endTime" label="结束时间" width="160" />
          </el-table>
          <div class="section-head">维护历史</div>
          <el-table :data="detail.history || []" size="small" border>
            <el-table-column prop="actionType" label="动作" width="110" />
            <el-table-column prop="beforeStatus" label="原状态" width="100" />
            <el-table-column prop="afterStatus" label="新状态" width="100" />
            <el-table-column prop="description" label="说明" min-width="160" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="operateTime" label="时间" width="160" />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="stretchVisible" title="绷网 / 重绷" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="目数"><el-input-number v-model="stretchForm.mesh" :min="1" :precision="0" /></el-form-item>
        <el-form-item label="张力(N/cm)"><el-input-number v-model="stretchForm.tensionNcm" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="网纱"><el-input v-model="stretchForm.material" maxlength="200" /></el-form-item>
        <el-form-item label="合格"><el-switch v-model="stretchForm.qualified" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stretchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStretch">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="plateVisible" title="制版" width="800px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="图案版本" required>
          <PrintResourceSelector :model-value="picked" size="small" placeholder="按图案/菲林号搜索版本" @select="onPickVersion" />
        </el-form-item>
        <el-form-item v-if="picked" label="已选版本">{{ picked.artworkNo }} {{ picked.name }} / {{ picked.versionNo }}</el-form-item>
        <el-form-item label="实体底片" required>
          <el-select v-model="plateForm.filmId" placeholder="选择该版本可用底片" style="width: 100%">
            <el-option v-for="f in filmOptions" :key="f.filmId" :label="f.filmNo + '（' + (f.location || '无位置') + '）'" :value="f.filmId" />
          </el-select>
        </el-form-item>
        <el-form-item label="版号" required><el-input v-model="plateForm.plateNo" maxlength="80" /></el-form-item>
        <el-form-item label="印刷位置" required><el-input v-model="plateForm.position" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="plateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPlate">确认制版</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/** 网版作业列表页（dev-20261126-001）：网框分页 + 绷网/制版/洗版/维护。 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { printingArtworkApi } from '@/api/product/film'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'
import type { PrintSearchRow } from '@/api/engineering/printingSearch'

defineOptions({ name: 'PrintResources' })

const statusOptions = [
  { value: 'READY', label: '可用' },
  { value: 'MAINTENANCE', label: '维护中' },
  { value: 'SCRAPPED', label: '已报废' },
]

const query = reactive({ keyword: '', status: '' })
const rows = ref<any[]>([])
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const submitting = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)

const stretchVisible = ref(false)
const stretchForm = reactive<any>({ mesh: undefined, tensionNcm: undefined, material: '', qualified: true })

const plateVisible = ref(false)
const plateForm = reactive<any>({ plateNo: '', position: '', filmId: undefined })
const picked = ref<PrintSearchRow | null>(null)
const filmOptions = ref<any[]>([])

let currentFrame: any = null

function statusLabel(status?: string) {
  if (status === 'READY') return '可用'
  if (status === 'MAINTENANCE') return '维护中'
  if (status === 'SCRAPPED') return '已报废'
  return status || '-'
}
function statusTag(status?: string) {
  if (status === 'READY') return 'success'
  if (status === 'MAINTENANCE') return 'warning'
  if (status === 'SCRAPPED') return 'danger'
  return 'info'
}
function newRequestId() {
  return 'm1-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
}

async function load(pageNo = page.pageNum) {
  page.pageNum = pageNo
  loading.value = true
  try {
    const res: any = await engineeringResourceApi.listPrintFrames({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
    })
    rows.value = res?.data?.records || []
    page.total = res?.data?.total || 0
  } catch {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.status = ''
  load(1)
}

async function loadFrame(row: any) {
  const res: any = await engineeringResourceApi.getPrintFrame(row.frameId)
  currentFrame = res?.data || null
  return currentFrame
}

async function openDetail(row: any) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res: any = await engineeringResourceApi.getPrintFrame(row.frameId)
    detail.value = res?.data || null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function openStretch(row: any) {
  try {
    await loadFrame(row)
  } catch {
    return
  }
  const stretch = (currentFrame?.stretches || [])[0]
  stretchForm.mesh = stretch?.mesh ?? (row.mesh ? Number(row.mesh) : undefined)
  stretchForm.tensionNcm = stretch?.tensionNcm ?? undefined
  stretchForm.material = stretch?.material || ''
  stretchForm.qualified = true
  stretchVisible.value = true
}

async function submitStretch() {
  if (!currentFrame) return
  if (!stretchForm.mesh || !stretchForm.tensionNcm) {
    ElMessage.warning('请填写目数与张力')
    return
  }
  submitting.value = true
  try {
    await engineeringResourceApi.stretchPrintFrame(Number(currentFrame.frameId), {
      requestId: newRequestId(),
      expectedRevision: Number(currentFrame.revision ?? 0),
      mesh: stretchForm.mesh,
      tensionNcm: stretchForm.tensionNcm,
      material: stretchForm.material || undefined,
      qualified: Boolean(stretchForm.qualified),
    })
    ElMessage.success('绷网已记录')
    stretchVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function openPlate(row: any) {
  try {
    await loadFrame(row)
  } catch {
    return
  }
  if (!currentFrame?.stretchId) {
    ElMessage.warning('该网框没有进行中的绷网，请先绷网')
    return
  }
  if (currentFrame?.plateId) {
    ElMessage.warning('该网框已有在用网版，请先洗版')
    return
  }
  picked.value = null
  filmOptions.value = []
  plateForm.filmId = undefined
  plateForm.plateNo = row.frameNo + '-' + new Date().toISOString().slice(5, 10).replace('-', '')
  plateForm.position = '居中'
  plateVisible.value = true
}

async function onPickVersion(item: PrintSearchRow) {
  if (!item?.versionId || !item?.artworkId) {
    ElMessage.warning('请选择图案版本候选（网框候选不能制版）')
    return
  }
  picked.value = item
  const res: any = await printingArtworkApi.detail(Number(item.artworkId))
  const films = (res?.data?.films || []).filter((f: any) => f.status === 'AVAILABLE')
  filmOptions.value = films
  plateForm.filmId = films.length === 1 ? films[0].filmId : undefined
}

async function submitPlate() {
  if (!currentFrame || !picked.value?.versionId) {
    ElMessage.warning('请先选择图案版本')
    return
  }
  if (!plateForm.filmId) {
    ElMessage.warning('请选择实体底片')
    return
  }
  if (!plateForm.plateNo || !plateForm.position) {
    ElMessage.warning('请填写版号与印刷位置')
    return
  }
  submitting.value = true
  try {
    await engineeringResourceApi.createPrintPlate({
      requestId: newRequestId(),
      expectedRevision: Number(currentFrame.revision ?? 0),
      frameId: Number(currentFrame.frameId),
      stretchId: Number(currentFrame.stretchId),
      versionId: Number(picked.value.versionId),
      filmId: Number(plateForm.filmId),
      plateNo: plateForm.plateNo,
      position: plateForm.position,
    })
    ElMessage.success('制版完成')
    plateVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function wash(row: any) {
  try {
    await loadFrame(row)
  } catch {
    return
  }
  if (!currentFrame?.plateId) {
    ElMessage.warning('该网框当前没有在用网版')
    return
  }
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写洗版原因', '洗版', {
      inputPlaceholder: '洗版原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
    })
    reason = value.trim()
  } catch {
    return
  }
  try {
    const plateRes: any = await engineeringResourceApi.getPrintPlate(Number(currentFrame.plateId))
    const plate = plateRes?.data || {}
    await engineeringResourceApi.washPrintPlate(Number(plate.plateId), {
      requestId: newRequestId(),
      expectedRevision: Number(plate.revision ?? 0),
      reason,
    })
    ElMessage.success('已洗版，网框释放')
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function maintain(row: any, action: string) {
  const label = action === 'maintain' ? '送修' : action === 'resume' ? '恢复使用' : '报废'
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写' + label + '原因', label, {
      inputPlaceholder: '原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
    })
    reason = value.trim()
  } catch {
    return
  }
  try {
    await loadFrame(row)
    const command = {
      requestId: newRequestId(),
      expectedRevision: Number(currentFrame?.revision ?? 0),
      reason,
    }
    if (action === 'maintain') await engineeringResourceApi.maintainPrintFrame(Number(row.frameId), command)
    else if (action === 'resume') await engineeringResourceApi.resumePrintFrame(Number(row.frameId), command)
    else await engineeringResourceApi.scrapPrintFrame(Number(row.frameId), command)
    ElMessage.success(label + '成功')
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.tip { color: #909399; font-size: 12px; }
.section-head { margin: 16px 0 8px; font-weight: 600; }
</style>
