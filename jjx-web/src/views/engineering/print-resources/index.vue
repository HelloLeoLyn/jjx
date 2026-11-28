<template>
  <div class="print-resources-page">
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="框号/型号/位置"
            clearable
            style="width: 220px"
            @keyup.enter="load(1)"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部"
            style="width: 140px"
            @change="load(1)"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
          <el-button v-hasPermi="['engineering:resource:edit']" type="success" @click="openFrameCreate">新增网框</el-button>
        </el-form-item>
      </el-form>
      <div class="tip">
        先新增网框，再登记绷网、制版并检验放行。网框可以洗版后重复使用。
      </div>
      <el-alert v-if="carriedVersion" :title="`当前菲林：${carriedVersion.artworkNo} / ${carriedVersion.versionNo}，打开制版后可直接勾选实体片`" type="info" :closable="false" style="margin-top:12px" />
      <el-alert v-if="contextError" :title="contextError" type="error" :closable="false" style="margin-top:12px" />
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="frameNo" label="网框编号" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.frameNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="frameType" label="型号" width="110" />
        <el-table-column prop="mesh" label="目数" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{
              statusLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="当前版面" width="150" />
        <el-table-column prop="location" label="位置" min-width="130" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <TableActionColumn
          :actions="frameActions"
          :max-visible="4"
          width="350"
          fixed="right"
          align="center"
          display="text"
          @action="handleFrameAction"
        />
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

    <el-dialog v-model="frameCreateVisible" title="新增网框" width="520px" destroy-on-close :show-close="!submitting" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting">
      <el-alert v-if="frameCreateError" :title="frameCreateError" type="error" :closable="false" />
      <el-form label-width="90px" :disabled="submitting" @submit.prevent="submitFrameCreate">
        <el-form-item label="网框编号" required><el-input v-model="frameForm.frameNo" maxlength="50" aria-label="网框编号" placeholder="填写实物网框编号" /></el-form-item>
        <el-form-item label="编码系列"><el-input v-model="frameForm.frameSeries" maxlength="64" placeholder="如 YL1025 或 GY，实物框号保持原号" /></el-form-item><el-form-item label="型号"><el-input v-model="frameForm.frameType" maxlength="30" /></el-form-item>
        <el-form-item label="存放位置"><el-input v-model="frameForm.location" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="submitting" @click="frameCreateVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitFrameCreate">保存网框</el-button></template>
    </el-dialog>

    <el-dialog v-model="inspectionVisible" title="网版检验 / 放行" width="600px" destroy-on-close :show-close="!submitting" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting">
      <el-alert v-if="inspectionError" :title="inspectionError" type="error" :closable="false" />
      <el-form label-width="90px" :disabled="submitting || !canInspect">
        <el-form-item label="网版">{{ inspectionForm.plateNo }}（{{ ScreenPlateStatusEnum.getLabel(inspectionForm.status) }}）</el-form-item>
        <el-form-item label="检验结果"><el-radio-group v-model="inspectionForm.passed"><el-radio :value="true">合格，放行</el-radio><el-radio :value="false">不合格</el-radio></el-radio-group></el-form-item>
        <el-form-item label="检验记录" required><el-input v-model="inspectionForm.inspection" type="textarea" :rows="4" maxlength="500" placeholder="填写实际检验结果" /></el-form-item>
      </el-form>
      <p class="tip">放行须由具有检验放行权限的另一位操作员完成，制版人不能放行本人制版。</p>
      <template #footer><el-button :disabled="submitting" @click="inspectionVisible=false">关闭</el-button><el-button type="primary" :disabled="!canInspect" :loading="submitting" @click="submitInspection">确认检验结果</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="760px" title="网框详情" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="框号">{{ detail.frameNo }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ detail.frameType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{
              statusLabel(detail.status)
            }}</el-descriptions-item>
            <el-descriptions-item label="位置">{{ detail.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{
              detail.remark || '-'
            }}</el-descriptions-item>
          </el-descriptions>
          <div class="section-head">绷网记录</div>
          <el-table :data="detail.stretches || []" size="small" border>
            <el-table-column prop="mesh" label="目数" width="90" />
            <el-table-column prop="tensionNcm" label="张力(N/cm)" width="110" />
            <el-table-column prop="material" label="网纱" min-width="120" />
            <el-table-column label="合格" width="80">
              <template #default="{ row }">{{ row.qualified ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="stretchNo" label="绷网编号" min-width="150" />
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

    <el-dialog v-model="remarkVisible" title="编辑网框备注" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="网框">{{ remarkForm.frameNo }}</el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="remarkForm.remark"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="存放或使用注意事项，可留空"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remarkVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveRemark">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stretchVisible" title="绷网 / 重绷" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="目数"
          ><el-input-number v-model="stretchForm.mesh" :min="1" :precision="0"
        /></el-form-item>
        <el-form-item label="张力(N/cm)"
          ><el-input-number v-model="stretchForm.tensionNcm" :min="0" :precision="2"
        /></el-form-item>
        <el-form-item label="网纱"
          ><el-input v-model="stretchForm.material" maxlength="200"
        /></el-form-item>
        <el-form-item label="合格"><el-switch v-model="stretchForm.qualified" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stretchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStretch">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="plateVisible" title="制版" width="800px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="选择菲林" required>
          <PrintFilmMultiSelector v-if="plateVisible" v-model="plateSources" :initial-keyword="carriedVersion ? `${carriedVersion.artworkNo} ${carriedVersion.versionNo}` : ''" :disabled="submitting" @loading="selectionLoading = $event" />
        </el-form-item>
        <el-form-item :label="`已选菲林（${plateSources.length}）`">
          <div v-if="!plateSources.length" class="tip">勾选上方搜索结果即可加入。</div>
          <el-collapse v-else class="selected-films">
            <el-collapse-item v-for="source in plateSources" :key="source.filmId" :name="source.filmId">
              <template #title><div class="selected-film-heading">
                <span>{{ source.artworkName || source.artworkNo }} · {{ source.versionNo }} · {{ source.filmNo }}</span>
                <span class="tip">已选 {{ source.styleNos?.length || 0 }}/{{ source.styles.length }} 个样式</span>
                <el-button link type="danger" :disabled="submitting" @click.stop="plateSources = plateSources.filter(item => item.filmId !== source.filmId)">移除</el-button>
              </div></template>
              <el-checkbox-group v-model="source.styleNos" :disabled="submitting"><el-checkbox v-for="style in source.styles" :key="style.styleNo" :value="style.styleNo">{{ style.styleNo }} {{ style.name }} · {{ style.inkColor }}</el-checkbox></el-checkbox-group>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>
        <el-form-item label="实际制版排布"><el-input v-model="plateForm.layout" type="textarea" maxlength="2000" placeholder="记录实际排布，与菲林样式定义分开" /></el-form-item>
        <el-form-item label="版号" required
          ><el-input v-model="plateForm.plateNo" maxlength="80"
        /></el-form-item>
        <el-form-item label="印刷位置" required
          ><el-input v-model="plateForm.position" maxlength="100"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="plateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="selectionLoading" @click="submitPlate">确认制版</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { type PlateSource } from '@/components/printing/printStyles'
/** 网版作业列表页（dev-20261126-001）：网框分页 + 绷网/制版/洗版/维护。 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ScreenFrameStatusEnum, ScreenPlateStatusEnum } from '@/enums/engineering/resource'
import { ArtworkVersionStatusEnum } from '@/enums/product/film'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { printingArtworkApi } from '@/api/product/film'
import PrintFilmMultiSelector from '@/components/printing/PrintFilmMultiSelector.vue'
import type { PrintSearchRow } from '@/api/engineering/printingSearch'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'PrintResources' })
const route = useRoute()
const carriedVersion = ref<PrintSearchRow | null>(null)
const contextError = ref('')
let contextGeneration = 0
watch(() => [route.query.artworkId, route.query.versionId], async () => {
  const generation = ++contextGeneration
  carriedVersion.value = null; contextError.value = ''
  const artworkId = Number(route.query.artworkId), versionId = Number(route.query.versionId)
  if (!artworkId || !versionId) return
  try {
    const result: any = await printingArtworkApi.detail(artworkId)
    if (generation !== contextGeneration) return
    const version = result.data?.versions?.find((item: any) => item.versionId === versionId)
    if (!version) throw new Error('来源菲林版本不存在，请重新从菲林管理进入')
    if (version.status !== ArtworkVersionStatusEnum.RELEASED.value) throw new Error('来源菲林版本尚未发布或已停用')
    carriedVersion.value = { artworkId, artworkNo: result.data.artworkNo, name: result.data.name, versionId, versionNo: version.versionNo }
  } catch (error: any) { if (generation === contextGeneration) contextError.value = error.message || '来源菲林加载失败' }
}, { immediate: true })

const frameActions: TableAction<any>[] = [
  { key: 'remark', label: '编辑备注', type: 'primary', permission: 'engineering:resource:edit' },
  { key: 'stretch', label: '绷网', type: 'primary', permission: 'engineering:resource:maintain' },
  { key: 'plate', label: '制版', type: 'success', permission: 'engineering:resource:edit' },
  { key: 'inspect', label: '检验 / 放行', type: 'success', permission: 'engineering:screen-resource:release', visible: ({ row }) => !!row.plateId },
  { key: 'wash', label: '洗版', type: 'warning', permission: 'engineering:screen-resource:wash' },
  { key: 'maintain', label: '送修', type: 'info', permission: 'engineering:resource:maintain' },
  { key: 'resume', label: '恢复使用', type: 'info', permission: 'engineering:resource:maintain' },
  { key: 'scrap', label: '报废', type: 'info', permission: 'engineering:resource:maintain' },
]

function handleFrameAction(key: string, row: any) {
  switch (key) {
    case 'remark': return openRemark(row)
    case 'stretch': return openStretch(row)
    case 'plate': return openPlate(row)
    case 'inspect': return openInspection(row)
    case 'wash': return wash(row)
    case 'maintain':
    case 'resume':
    case 'scrap': return maintain(row, key)
  }
}

const statusOptions = [
  ScreenFrameStatusEnum.READY,
  ScreenFrameStatusEnum.MAINTENANCE,
  ScreenFrameStatusEnum.SCRAPPED,
]

const query = reactive({ keyword: '', status: '' })
const rows = ref<any[]>([])
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const submitting = ref(false)
const frameCreateVisible = ref(false), frameCreateError = ref('')
const frameForm = reactive({ frameNo: '', frameType: '', location: '', frameSeries: '' })
let frameCreateRequestId = ''
function openFrameCreate() {
  if (submitting.value) return
  Object.assign(frameForm, { frameNo: '', frameType: '', location: '', frameSeries: '' })
  frameCreateError.value = ''; frameCreateRequestId = newRequestId(); frameCreateVisible.value = true
}
async function submitFrameCreate() {
  if (submitting.value) return
  if (!frameForm.frameNo.trim()) return ElMessage.warning('请填写实物网框编号')
  submitting.value = true; frameCreateError.value = ''
  try {
    await engineeringResourceApi.createPrintFrame({ requestId: frameCreateRequestId, expectedRevision: 0, frameNo: frameForm.frameNo.trim(), frameSeries: frameForm.frameSeries.trim() || undefined, frameType: frameForm.frameType.trim() || undefined, location: frameForm.location.trim() || undefined })
    frameCreateVisible.value = false; query.keyword = frameForm.frameNo.trim(); query.status = ''
    ElMessage.success('网框已新增，请在该网框行登记绷网'); await load(1)
  } catch (error: any) { frameCreateError.value = error.response?.data?.message || error.message || '网框新增失败，输入已保留' }
  finally { submitting.value = false }
}

const inspectionVisible = ref(false), inspectionError = ref('')
const inspectionForm = reactive({ plateId: 0, plateNo: '', status: '', revision: 0, passed: true, inspection: '' })
let inspectionRequestId = ''
const canInspect = computed(() => [ScreenPlateStatusEnum.PENDING.value, ScreenPlateStatusEnum.FAILED.value].some(status => status === inspectionForm.status))
async function openInspection(row: any) {
  if (submitting.value || !row.plateId) return
  try {
    const result: any = await engineeringResourceApi.getPrintPlate(Number(row.plateId))
    const plate = result.data
    Object.assign(inspectionForm, { plateId: plate.plateId, plateNo: plate.plateNo, status: plate.status, revision: plate.revision, passed: true, inspection: '' })
    inspectionError.value = ''; inspectionRequestId = newRequestId(); inspectionVisible.value = true
  } catch { /* 拦截器已提示 */ }
}
async function submitInspection() {
  if (submitting.value || !canInspect.value) return
  if (!inspectionForm.inspection.trim()) return ElMessage.warning('请填写实际检验记录')
  submitting.value = true; inspectionError.value = ''
  const command = { requestId: inspectionRequestId, expectedRevision: inspectionForm.revision, inspection: inspectionForm.inspection.trim() }
  try {
    if (inspectionForm.passed) await engineeringResourceApi.releasePrintPlate(inspectionForm.plateId, command)
    else await engineeringResourceApi.failPrintPlate(inspectionForm.plateId, command)
    inspectionVisible.value = false; ElMessage.success(inspectionForm.passed ? '网版已检验放行' : '已登记检验不合格'); await load()
  } catch (error: any) { inspectionError.value = error.response?.data?.message || error.message || '检验保存失败，输入已保留' }
  finally { submitting.value = false }
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)

const stretchVisible = ref(false)
const stretchForm = reactive<any>({
  mesh: undefined,
  tensionNcm: undefined,
  material: '',
  qualified: true,
})

const plateVisible = ref(false)
const plateForm = reactive({ plateNo: '', position: '', layout: '' })
const plateSources = ref<PlateSource[]>([])
const selectionLoading = ref(false)
let stretchRequestId = '', plateRequestId = ''

const remarkVisible = ref(false)
const remarkForm = reactive({ frameId: 0, frameNo: '', revision: 0, remark: '' })
let remarkRequestId = ''

async function openRemark(row: any) {
  try {
    const res: any = await engineeringResourceApi.getPrintFrame(row.frameId)
    const frame = res.data
    Object.assign(remarkForm, {
      frameId: frame.frameId,
      frameNo: frame.frameNo,
      revision: frame.revision,
      remark: frame.remark || '',
    })
    remarkRequestId = newRequestId()
    remarkVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

async function saveRemark() {
  if (submitting.value) return
  submitting.value = true
  try {
    await engineeringResourceApi.editPrintFrameRemark(remarkForm.frameId, {
      requestId: remarkRequestId,
      expectedRevision: remarkForm.revision,
      remark: remarkForm.remark,
    })
    ElMessage.success('网框备注已保存')
    remarkVisible.value = false
    await load()
  } catch {
    /* 失败保留输入和原 revision，不静默覆盖并发修改 */
  } finally {
    submitting.value = false
  }
}

let currentFrame: any = null

function statusLabel(status?: string) {
  return ScreenFrameStatusEnum.getLabel(status || '') || status || '-'
}
function statusTag(status?: string) {
  return ScreenFrameStatusEnum.getTagProps(status || '').type
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
  if (submitting.value) return
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
  stretchRequestId = newRequestId()
  stretchVisible.value = true
}

async function submitStretch() {
  if (submitting.value || !currentFrame) return
  if (!stretchForm.mesh || !stretchForm.tensionNcm) {
    ElMessage.warning('请填写目数与张力')
    return
  }
  submitting.value = true
  try {
    await engineeringResourceApi.stretchPrintFrame(Number(currentFrame.frameId), {
      requestId: stretchRequestId,
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
  if (submitting.value) return
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
  plateSources.value = []; selectionLoading.value = false; plateForm.layout = ''
  plateForm.plateNo = row.frameNo + '-' + new Date().toISOString().slice(5, 10).replace('-', '')
  plateForm.position = '居中'
  plateVisible.value = true
  plateRequestId = newRequestId()
}

async function submitPlate() {
  if (submitting.value || selectionLoading.value) return
  if (!currentFrame || !plateSources.value.length) {
    ElMessage.warning('请至少勾选一张实体菲林')
    return
  }
  if (plateSources.value.some(source => !source.styleNos?.length)) {
    ElMessage.warning('每张实体菲林至少选择一个样式')
    return
  }
  if (!plateForm.plateNo || !plateForm.position) {
    ElMessage.warning('请填写版号与印刷位置')
    return
  }
  submitting.value = true
  try {
    await engineeringResourceApi.createPrintPlate({
      requestId: plateRequestId,
      expectedRevision: Number(currentFrame.revision ?? 0),
      frameId: Number(currentFrame.frameId),
      stretchId: Number(currentFrame.stretchId),
      sourcesJson: JSON.stringify(plateSources.value.map(({ filmId, versionId, styleNos }) => ({ filmId, versionId, styleNos }))),
      layout: plateForm.layout || undefined,
      plateNo: plateForm.plateNo,
      position: plateForm.position,
    })
    ElMessage.success('制版已登记，待另一位有权限的操作员检验放行')
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
    if (action === 'maintain')
      await engineeringResourceApi.maintainPrintFrame(Number(row.frameId), command)
    else if (action === 'resume')
      await engineeringResourceApi.resumePrintFrame(Number(row.frameId), command)
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
.selected-films { width: 100%; }
.selected-film-heading { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.search-card {
  margin-bottom: 16px;
}
.tip {
  color: #909399;
  font-size: 12px;
}
.section-head {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
