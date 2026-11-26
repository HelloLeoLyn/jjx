<template>
  <div class="screen-work">
    <div class="bar">
      <PrintResourceSelector v-model="selected" @select="onSelect" />
      <el-button size="small" :disabled="!frame" @click="reload">刷新</el-button>
      <span v-if="frame" class="tip">
        {{ frame.frameNo }} · {{ frame.status || '-' }} · rev {{ frame.revision ?? '-' }}
      </span>
    </div>

    <template v-if="frame">
      <el-descriptions :column="4" border size="small" class="meta">
        <el-descriptions-item label="网框号">{{ frame.frameNo }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ frame.frameType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前绷网">
          {{ currentStretch ? currentStretch.mesh + ' 目 / ' + currentStretch.tensionNcm + ' N/cm' : '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="占用版面">{{ frame.plateId || '无' }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-button size="small" @click="openStretch">绷网 / 重绷</el-button>
        <el-button size="small" @click="openPlateCreate">制版</el-button>
        <el-button size="small" :disabled="!frame.plateId" @click="openPlateAction('release')">放行</el-button>
        <el-button size="small" :disabled="!frame.plateId" @click="openPlateAction('fail')">检验失败</el-button>
        <el-button size="small" :disabled="!frame.plateId" @click="openPlateAction('wash')">洗版</el-button>
        <el-button size="small" @click="openClean">清洁</el-button>
        <el-button size="small" @click="maintain('maintain')">送修</el-button>
        <el-button size="small" @click="maintain('resume')">恢复使用</el-button>
        <el-button size="small" type="danger" @click="maintain('scrap')">报废</el-button>
      </div>

      <el-tabs>
        <el-tab-pane label="绷网记录">
          <el-table :data="frame.stretches || []" size="small" border>
            <el-table-column prop="stretchId" label="ID" width="80" />
            <el-table-column prop="mesh" label="目数" width="80" />
            <el-table-column prop="tensionNcm" label="张力(N/cm)" width="110" />
            <el-table-column prop="material" label="网纱" min-width="120" />
            <el-table-column label="合格" width="80"><template #default="{ row }">{{ row.qualified ? '是' : '否' }}</template></el-table-column>
            <el-table-column prop="stretchTime" label="绷网时间" width="160" />
            <el-table-column prop="endTime" label="结束时间" width="160" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="维护历史">
          <el-table :data="frame.history || []" size="small" border>
            <el-table-column prop="actionType" label="动作" width="110" />
            <el-table-column prop="beforeStatus" label="原状态" width="100" />
            <el-table-column prop="afterStatus" label="新状态" width="100" />
            <el-table-column prop="description" label="说明" min-width="180" />
            <el-table-column prop="operator" label="操作人" width="110" />
            <el-table-column prop="operateTime" label="时间" width="160" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-empty v-else description="先搜索并选择网框" :image-size="60" />

    <el-dialog v-model="stretchVisible" title="绷网 / 重绷" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="目数"><el-input-number v-model="stretchForm.mesh" :min="1" :precision="0" /></el-form-item>
        <el-form-item label="张力(N/cm)"><el-input-number v-model="stretchForm.tensionNcm" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="网纱"><el-input v-model="stretchForm.material" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stretchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStretch">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="plateVisible" title="制版" width="560px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="来源图案版本"><el-input :model-value="sourceVersionText" readonly /></el-form-item>
        <el-form-item label="实体底片"><el-input :model-value="sourceFilmText" readonly /></el-form-item>
        <el-form-item label="版号"><el-input v-model="plateForm.plateNo" maxlength="64" /></el-form-item>
        <el-form-item label="印刷位置"><el-input v-model="plateForm.position" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="plateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPlate">确认制版</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="plateActionVisible" :title="plateActionTitle" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item v-if="plateAction !== 'wash'" label="检验结论"><el-input v-model="plateActionForm.inspection" maxlength="200" /></el-form-item>
        <el-form-item label="原因 / 备注">
          <el-input v-model="plateActionForm.reason" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="plateActionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPlateAction">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/** 网框作业面板（dev-20261125-013）：搜索 → 网框 → 绷网/制版/放行/清洁/洗版。 */
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'
import { engineeringResourceApi } from '@/api/engineering/resource'
import type { PrintSearchRow } from '@/api/engineering/printingSearch'
import type { ScreenCommand, ScreenFrameView, ScreenStretch } from '@/types/engineering/printingScreen'

const selected = ref<PrintSearchRow | null>(null)
const frame = ref<ScreenFrameView | null>(null)
const submitting = ref(false)

const stretchVisible = ref(false)
const stretchForm = reactive<{ mesh?: number; tensionNcm?: number; material?: string }>({})
const stretchRequestId = ref('')

const plateVisible = ref(false)
const plateForm = reactive<{ plateNo?: string; position?: string }>({})
const plateRequestId = ref('')

const plateActionVisible = ref(false)
const plateAction = ref<'release' | 'fail' | 'wash'>('release')
const plateActionForm = reactive<{ inspection?: string; reason?: string }>({})
const plateActionRequestId = ref('')
const plateTarget = ref<Record<string, any> | null>(null)

const currentStretch = computed<ScreenStretch | undefined>(() => (frame.value?.stretches || [])[0])
const sourceVersionText = computed(() => {
  const row = selected.value
  if (!row?.versionId) return '未选择图案版本'
  return [row.artworkNo, row.name, row.versionNo].filter(Boolean).join(' / ')
})
const sourceFilmText = computed(() => selected.value?.filmNo || '未选择实体底片')
const plateActionTitle = computed(() =>
  plateAction.value === 'release' ? '网版放行' : plateAction.value === 'fail' ? '检验失败' : '洗版'
)

function newRequestId(): string {
  const uuid = globalThis.crypto?.randomUUID?.()
  return uuid || 'req-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

function onSelect(row: PrintSearchRow) {
  selected.value = row
  if (row?.frameId) {
    void loadFrame(Number(row.frameId))
  } else {
    ElMessage.warning('该候选没有 frameId，请选择网框候选')
  }
}

async function loadFrame(id: number) {
  const res = await engineeringResourceApi.getPrintFrame(id)
  frame.value = res.data as ScreenFrameView
}

async function reload() {
  if (frame.value?.frameId) await loadFrame(Number(frame.value.frameId))
}

async function submitCommand(kind: 'stretch' | 'clean' | 'plate' | 'plateAction', requestId: string, payload: Partial<ScreenCommand>) {
  if (!frame.value) return
  submitting.value = true
  try {
    const frameId = Number(frame.value.frameId)
    const command = { requestId, expectedRevision: Number(frame.value.revision ?? 0), ...payload } as ScreenCommand
    if (kind === 'stretch') await engineeringResourceApi.stretchPrintFrame(frameId, command)
    else if (kind === 'clean') await engineeringResourceApi.cleanPrintFrame(frameId, command)
    else if (kind === 'plate') await engineeringResourceApi.createPrintPlate(command)
    else {
      const plateId = Number(plateTarget.value?.plateId)
      const plateRevision = Number(plateTarget.value?.revision ?? 0)
      const body = { ...command, expectedRevision: plateRevision }
      if (plateAction.value === 'release') await engineeringResourceApi.releasePrintPlate(plateId, body)
      else if (plateAction.value === 'fail') await engineeringResourceApi.failPrintPlate(plateId, body)
      else await engineeringResourceApi.washPrintPlate(plateId, body)
    }
    ElMessage.success('操作成功')
    stretchVisible.value = false
    plateVisible.value = false
    plateActionVisible.value = false
    await reload()
  } catch (error: any) {
    const reason = error?.response?.data?.data?.reasonCode || error?.response?.data?.msg || error?.message
    if (String(reason).includes('REVISION_CHANGED') || String(reason).includes('revision')) {
      ElMessage.warning('数据已被他人修改，已刷新，请确认后重试（输入已保留）')
      await reload()
    } else {
      ElMessage.error(String(reason || '操作失败'))
    }
  } finally {
    submitting.value = false
  }
}

function openStretch() {
  stretchForm.mesh = currentStretch.value?.mesh
  stretchForm.tensionNcm = currentStretch.value?.tensionNcm
  stretchForm.material = currentStretch.value?.material
  stretchRequestId.value = newRequestId()
  stretchVisible.value = true
}

function submitStretch() {
  void submitCommand('stretch', stretchRequestId.value, {
    mesh: stretchForm.mesh,
    tensionNcm: stretchForm.tensionNcm,
    material: stretchForm.material,
    qualified: true,
  })
}

function openPlateCreate() {
  if (!selected.value?.versionId) {
    ElMessage.warning('请先通过搜索选择一个图案版本')
    return
  }
  plateForm.plateNo = ''
  plateForm.position = ''
  plateRequestId.value = newRequestId()
  plateVisible.value = true
}

function submitPlate() {
  if (!plateForm.plateNo) {
    ElMessage.warning('请填写版号')
    return
  }
  void submitCommand('plate', plateRequestId.value, {
    frameId: frame.value?.frameId,
    versionId: selected.value?.versionId,
    filmId: selected.value?.filmId,
    plateNo: plateForm.plateNo,
    position: plateForm.position,
  })
}

async function openPlateAction(kind: 'release' | 'fail' | 'wash') {
  if (!frame.value?.plateId) return
  plateAction.value = kind
  plateActionForm.inspection = ''
  plateActionForm.reason = ''
  plateActionRequestId.value = newRequestId()
  const res = await engineeringResourceApi.getPrintPlate(Number(frame.value.plateId))
  plateTarget.value = res.data as Record<string, any>
  plateActionVisible.value = true
}

function submitPlateAction() {
  if (plateAction.value === 'wash' && !plateActionForm.reason?.trim()) {
    ElMessage.warning('洗版必须填写原因')
    return
  }
  void submitCommand('plateAction', plateActionRequestId.value, {
    inspection: plateActionForm.inspection,
    reason: plateActionForm.reason,
  })
}

async function openClean() {
  try {
    await ElMessageBox.confirm('清洁后原版面继续占用网框，仅记录清洁动作。', '清洁', { type: 'info' })
  } catch {
    return
  }
  await submitCommand('clean', newRequestId(), {})
}

async function maintain(action: 'maintain' | 'resume' | 'scrap') {
  const label = action === 'maintain' ? '送修' : action === 'resume' ? '恢复使用' : '报废'
  try {
    await ElMessageBox.confirm('确认执行「' + label + '」？', label, { type: action === 'scrap' ? 'warning' : 'info' })
  } catch {
    return
  }
  if (!frame.value) return
  const requestId = newRequestId()
  submitting.value = true
  try {
    const command = { requestId, expectedRevision: Number(frame.value.revision ?? 0) }
    if (action === 'maintain') await engineeringResourceApi.maintainPrintFrame(Number(frame.value.frameId), command)
    else if (action === 'resume') await engineeringResourceApi.resumePrintFrame(Number(frame.value.frameId), command)
    else await engineeringResourceApi.scrapPrintFrame(Number(frame.value.frameId), command)
    ElMessage.success(label + '成功')
    await reload()
  } catch (error: any) {
    ElMessage.error(String(error?.response?.data?.msg || error?.message || '操作失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.screen-work { padding: 4px 0; }
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.bar :deep(.el-select), .bar > :first-child { width: 420px; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); }
.meta { margin-bottom: 10px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
</style>
