<template>
  <el-dialog
    :model-value="visible"
    title="🔧 工程打样工作台"
    width="1200px"
    append-to-body
    @update:model-value="onClose"
    @open="onOpen"
  >
    <div v-if="card" class="wb-body">
      <!-- 轮次切换（DEV-500） -->
      <el-tabs v-model="activeRound" style="margin-bottom:10px">
        <el-tab-pane
          v-for="r in roundList"
          :key="r.roundNo"
          :name="String(r.roundNo)"
          :label="`Round ${r.roundNo}${r.roundNo === (card.sampleRound || 1) ? '（当前）' : ''}`"
        />
      </el-tabs>

      <template v-if="isCurrentRound">
        <!-- ① 样品单信息（底部内嵌汇总） -->
        <el-card class="wb-card" shadow="never">
          <template #header><span style="font-weight:600">样品单信息</span></template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="单号">{{ card.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="客户">{{ card.customerName }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ card.productName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="轮次">Round {{ card.sampleRound || 1 }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" type="warning">工程打样中</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="接单人">{{ card.engineeringAcceptor || '-' }}</el-descriptions-item>
          </el-descriptions>
          <!-- 汇总 -->
          <div class="summary-inline">
            <div class="summary-item">
              <div class="summary-num">{{ doneCount }} / {{ planList.length }}</div>
              <div class="summary-label">工序完成</div>
            </div>
            <div class="summary-item">
              <div class="summary-num">{{ summary.totalHours ?? '-' }}</div>
              <div class="summary-label">总工时(小时)</div>
            </div>
            <div class="summary-item">
              <div class="summary-num">¥{{ summary.materialCost ?? '-' }}</div>
              <div class="summary-label">材料成本(估算)</div>
            </div>
            <div class="summary-tip">工时=已完成工序耗时之和；材料成本=材料用量×标准单价</div>
          </div>
          <!-- 接单/拒单 -->
          <div v-if="!card.engineeringAcceptor" class="accept-row">
            <el-button type="primary" @click="handleAccept" :loading="saving">✅ 工程接单</el-button>
            <el-button type="danger" plain style="margin-left:8px" @click="handleReject">✋ 工程拒单</el-button>
          </div>
          <div v-else class="accept-row">
            <el-tag type="success">已接单：{{ card.engineeringAcceptor }}</el-tag>
            <span style="margin-left:12px;color:#909399;font-size:12px">接单后开始记录打样过程</span>
          </div>
        </el-card>

        <!-- ② 中间：左选择器 + 右工序计划 -->
        <div class="mid-row">
          <el-card class="col-picker" shadow="never">
            <template #header>
              <span style="font-weight:600">① 选择作业项目</span>
              <span class="desc">已选 <b>{{ selectedProcessIds.length }}</b> 个</span>
            </template>
            <WorkProjectPicker v-model="selectedProcessIds" />
            <div class="picker-actions">
              <el-button type="success" size="small" :loading="savingPlan" @click="savePlan">💾 保存工序计划</el-button>
              <span class="desc">勾选后自动加入右侧计划，保存后生效</span>
            </div>
          </el-card>

          <el-card class="col-plan" shadow="never">
            <template #header>
              <span style="font-weight:600">② 打样工序计划</span>
              <span class="desc">共 {{ planList.length }} 道</span>
            </template>
            <div class="plan-scroll">
              <div v-for="(p, idx) in planList" :key="p.uid || p.processId" class="plan-card">
                <div class="pc-head">
                  <span class="pc-num">{{ idx + 1 }}</span>
                  <IconStepBadge
                    v-if="p.icon"
                    :icon="p.icon"
                    :description="p.description"
                    :size="26"
                    class="pc-ico"
                    @update-description="(v: string) => onUpdateDesc(p, v)"
                    @jump="onJump"
                  />
                  <span v-else class="pc-ico pc-emoji">📦</span>
                  <div class="pc-title">
                    <span class="pc-name">{{ p.processName }}</span>
                    <el-tag v-if="p.processType" size="small">{{ typeLabel(p.processType) }}</el-tag>
                    <el-tag v-if="p.processCategory" size="small" type="info">{{ categoryLabel(p.processCategory) }}</el-tag>
                    <el-tag v-if="!p.stdProcessId" size="small" type="warning">自定义</el-tag>
                  </div>
                  <div class="pc-right">
                    <el-tag v-if="p.status === 2" size="small" type="success">✓ 已完成</el-tag>
                    <el-tag v-else-if="p.status === 1" size="small" type="warning">⏳ 进行中</el-tag>
                    <el-tag v-else size="small" type="info">待做</el-tag>
                    <el-button
                      v-if="p.processId && p.status !== 2" type="primary" size="small"
                      @click="advancePlan(p)" :loading="p.advancing"
                    >{{ p.status === 1 ? '✓ 完成' : '▶ 开始' }}</el-button>
                    <span v-if="p.status === 2 && p.durationMinutes" style="color:#909399;font-size:12px">⏱ {{ p.durationMinutes }}分钟</span>
                    <el-button v-if="p.processId" type="primary" size="small" plain :loading="p.savingCard" @click="saveCard(p)">保存</el-button>
                    <el-button size="small" link type="danger" @click="removePlan(p)">删</el-button>
                  </div>
                </div>
                <div class="pc-body">
                  <div class="pc-row">
                    <div class="pc-row-label">📝 工艺说明</div>
                    <el-input v-model="p.processNote" type="textarea" :rows="2" placeholder="如：丝印机200目网版，刮刀压力3kg，室温干燥30分钟" />
                  </div>
                  <div class="pc-row">
                    <div class="pc-row-label">🧾 材料</div>
                    <div class="pc-mat">
                      <div class="mat-tags">
                        <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px;margin-bottom:4px">
                          {{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}{{ m.unit ? ' ' + m.unit : '' }}
                        </el-tag>
                        <span v-if="!parseMaterials(p.materials).length" style="color:#c0c4cc;font-size:12px">未选择材料</span>
                      </div>
                      <el-button type="primary" size="small" plain @click="toggleMatEditor(p)">{{ p.matEditing ? '收起材料' : '＋ 添加材料' }}</el-button>
                      <!-- 材料行内编辑（物料档案选择） -->
                      <div v-if="p.matEditing" style="margin-top:8px">
                        <div v-for="(m, ri) in p.materialRows" :key="ri" style="display:flex;gap:6px;margin-bottom:6px;align-items:center;flex-wrap:wrap">
                          <el-select
                            v-model="m.materialId"
                            filterable
                            remote
                            :remote-method="(q: string) => searchMaterials(q, m)"
                            :loading="m.loading"
                            placeholder="搜索物料档案"
                            style="width: 170px"
                            @change="(v: any) => onMaterialSelected(m, v)"
                          >
                            <el-option v-for="opt in m.options" :key="opt.materialId" :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`" :value="opt.materialId" />
                          </el-select>
                          <el-input v-model="m.spec" placeholder="规格" style="width:80px" :disabled="!!m.materialId" />
                          <el-input-number v-model="m.qty" :min="0" :precision="4" :controls="false" placeholder="用量" style="width:76px" />
                          <el-input v-model="m.unit" placeholder="单位" style="width:52px" :disabled="!!m.materialId" />
                          <el-button type="primary" size="small" link @click="openMaterialCreate(p, m)">新建物料</el-button>
                          <el-button type="danger" size="small" link @click="p.materialRows.splice(ri, 1)">删</el-button>
                        </div>
                        <el-button type="primary" size="small" plain icon="Plus" @click="addMaterialRow(p)">添加材料</el-button>
                        <el-button type="primary" size="small" :loading="p.savingCard" @click="saveCard(p)">💾 保存材料</el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-if="!planList.length" description="左侧勾选作业项目，自动生成工序计划" :image-size="70" />
            </div>
          </el-card>
        </div>

        <!-- ③ 底部：左执行时间线 + 右 BOM -->
        <div class="bottom-row">
          <el-card class="col-timeline" shadow="never">
            <template #header><span style="font-weight:600">③ 执行时间线</span><span class="desc">Round {{ card.sampleRound || 1 }} · 按计划流转</span></template>
            <div v-if="planList.length" class="timeline">
              <div
                v-for="(p, i) in planList" :key="p.uid || p.processId"
                class="tl-item" :class="p.status === 2 ? 'done' : p.status === 1 ? 'doing' : ''"
              >
                <div class="t">
                  {{ p.processName }}
                  <el-tag v-if="p.status === 2" size="small" type="success">完成</el-tag>
                  <el-tag v-else-if="p.status === 1" size="small" type="warning">进行中</el-tag>
                  <el-tag v-else size="small" type="info">待做</el-tag>
                </div>
                <div class="s">
                  <template v-if="p.operator">{{ p.operator }} · </template>
                  <template v-if="p.startTime">{{ formatTime(p.startTime) }}</template>
                  <template v-if="p.endTime"> - {{ formatTime(p.endTime) }}</template>
                  <template v-if="p.durationMinutes"> · {{ p.durationMinutes }}分钟</template>
                  <template v-if="!p.startTime && p.status === 0">—</template>
                </div>
                <div v-if="p.processNote" class="n">🔧 {{ p.processNote }}</div>
                <div v-if="parseMaterials(p.materials).length" class="n" style="margin-top:2px">
                  <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
                </div>
              </div>
            </div>
            <div v-else style="color:#999;font-size:13px">暂无工序计划</div>
          </el-card>

          <el-card class="col-bom" shadow="never">
            <template #header><span style="font-weight:600">BOM 物料清单</span><span class="desc">各工序材料自动聚合</span></template>
            <el-table v-if="bomList.length > 0" :data="bomList" size="small" border style="width:100%">
              <el-table-column prop="process" label="来源工序" width="100" />
              <el-table-column prop="name" label="材料" min-width="120" />
              <el-table-column prop="spec" label="规格" min-width="100" />
              <el-table-column prop="qty" label="用量" width="80" />
              <el-table-column prop="unit" label="单位" width="60" />
            </el-table>
            <div v-else style="color:#999;font-size:13px">暂无材料（在工序中添加材料后自动汇总）</div>
            <div class="transfer-zone">
              <el-button type="success" size="small" @click="handleTransfer" :loading="transfering">📦 一键转正式工艺路线</el-button>
              <div class="desc">打样确认后，把本轮工序计划+材料建档为产品/BOM/工艺路线</div>
            </div>
          </el-card>
        </div>

        <!-- 工艺参数 / 图纸 -->
        <div class="bottom-row">
          <el-card class="col-note" shadow="never">
            <template #header><span style="font-weight:600">工艺参数 / 工程备注</span></template>
            <el-input v-model="form.note" type="textarea" :rows="3"
              placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"
              maxlength="2000" show-word-limit />
            <el-button type="primary" size="small" style="margin-top:8px" @click="saveNote" :loading="saving">💾 保存工艺参数</el-button>
          </el-card>
          <el-card class="col-files" shadow="never">
            <template #header><span style="font-weight:600">图纸 / 工艺文件</span></template>
            <el-upload ref="engUploadRef" :http-request="engUploadFile" :on-remove="engRemoveFile"
              :file-list="engFileList" :before-upload="engBeforeUpload"
              list-type="text" multiple>
              <el-button type="primary" size="small">📤 上传图纸/文件</el-button>
            </el-upload>
            <div v-if="engFileList.length > 0" style="margin-top:8px">
              <div v-for="f in engFileList" :key="f.uid || f.name" style="padding:4px 0;display:flex;align-items:center;gap:8px;border-bottom:1px solid #f0f0f0">
                <el-link v-if="f.url" :href="f.url" target="_blank" type="primary" underline="never">📎 {{ f.name }}</el-link>
                <span v-else>{{ f.name }} <el-tag size="small" type="warning">待上传</el-tag></span>
              </div>
            </div>
            <div v-else style="color:#999;font-size:12px;margin-top:6px">菲林图 / 丝印图 / 模切图 / 规格书（≤10MB）</div>
          </el-card>
        </div>

        <!-- 标记完成 -->
        <div style="text-align:center;margin:12px 0 4px">
          <el-button type="success" size="large" @click="handleMarkReady" :loading="saving" style="width:220px">🎯 标记样品完成（送样）</el-button>
        </div>
      </template>

      <!-- 历史轮次（只读，DEV-500） -->
      <div v-else class="round-readonly">
        <el-alert
          type="info" :closable="false" show-icon style="margin-bottom:12px"
          title="历史轮次（只读）"
          description="该轮次已归档，如需调整请在当前轮次重新打样"
        />
        <div v-if="activeRoundData" style="margin-bottom:12px">
          <el-tag :type="activeRoundData.result === 'confirmed' ? 'success' : activeRoundData.result === 'rejected' ? 'danger' : 'info'">
            {{ activeRoundData.result === 'confirmed' ? '✅ 已确认' : activeRoundData.result === 'rejected' ? '⛔ 已退回' : '🔄 进行中' }}
          </el-tag>
          <span v-if="activeRoundData.rejectReason" style="margin-left:8px;color:#f56c6c;font-size:13px">
            退回原因：{{ activeRoundData.rejectReason }}
          </span>
          <span v-if="activeRoundData.engineeringNote" style="margin-left:12px;color:#606266;font-size:13px">
            工艺参数：{{ activeRoundData.engineeringNote }}
          </span>
        </div>
        <el-card shadow="never" style="margin-bottom:16px">
          <template #header><span style="font-weight:600">📜 工序快照</span></template>
          <el-timeline v-if="activeRoundProcesses.length" style="padding-left:2px">
            <el-timeline-item v-for="(p, i) in activeRoundProcesses" :key="i" :timestamp="formatTime(p.startTime)" placement="top" :type="i === activeRoundProcesses.length - 1 ? 'primary' : 'info'">
              <div style="font-size:13px">
                <span style="font-weight:600">{{ p.processName }}</span>
                <span v-if="p.durationMinutes" style="margin-left:8px;color:#606266;font-size:12px">⏱ {{ p.durationMinutes }}分钟</span>
                <span v-if="p.operator" style="margin-left:8px;color:#909399;font-size:12px">操作人：{{ p.operator }}</span>
                <div v-if="p.processNote" style="color:#606266;font-size:12px;margin-top:2px">🔧 {{ p.processNote }}</div>
                <div v-if="p.materials" style="margin-top:2px">
                  <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div v-else style="color:#999;font-size:13px">该轮次无工序快照</div>
        </el-card>
        <el-card shadow="never">
          <template #header><span style="font-weight:600">🧾 BOM 物料快照</span></template>
          <el-table v-if="activeRoundBom.length" :data="activeRoundBom" size="small" border style="width:100%">
            <el-table-column prop="process" label="工序" width="90" />
            <el-table-column prop="name" label="材料" min-width="140" />
            <el-table-column prop="spec" label="规格" min-width="120" />
            <el-table-column prop="qty" label="用量" width="90" />
            <el-table-column prop="unit" label="单位" width="70" />
          </el-table>
          <div v-else style="color:#999;font-size:13px">该轮次无物料快照</div>
        </el-card>
      </div>
    </div>
    <template #footer>
      <el-button @click="onClose(false)">关闭</el-button>
    </template>
    <!-- 物料建档弹窗（DEV-526：材料必须建档后选择） -->
    <MaterialFormDialog
      v-model="materialCreateVisible"
      :preset-data="materialPreset"
      @success="onMaterialCreated"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps } from 'element-plus'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import WorkProjectPicker from './WorkProjectPicker.vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  visible: boolean
  card: any
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const saving = ref(false)
const savingPlan = ref(false)
const transfering = ref(false)
const form = reactive({ note: '' })

// ===== 工序计划（方案A）=====
const planList = ref<any[]>([])
const selectedProcessIds = ref<number[]>([])

const { options: typeOptions } = useDict('process_type')
const { options: categoryOptions } = useDict('process_category')

function typeLabel(value: string): string {
  return typeOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}
function categoryLabel(value: string): string {
  return categoryOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}

// 已选变化 → 自动同步到右侧计划（所见即所得）
watch(selectedProcessIds, (ids) => {
  const existing = new Map<number, any>()
  for (const p of planList.value) {
    if (p.stdProcessId) existing.set(p.stdProcessId, p)
  }
  // 新增的追加
  const targetIds = new Set(ids)
  for (const id of ids) {
    if (!existing.has(id)) {
      const src = allProcesses.value.find((x) => x.processId === id)
      if (src) {
        planList.value.push({
          uid: `new-${id}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          stdProcessId: id,
          processName: src.processName,
          processType: src.processType,
          processCategory: src.processCategory,
          icon: src.icon,
          description: src.description,
          status: 0,
          processNote: '',
          materials: null,
          durationMinutes: null,
          processId: null as number | null,
          materialRows: [],
          matEditing: false,
        })
      }
    }
  }
  // 取消勾选的移除（保存计划时生效）
  planList.value = planList.value.filter((p) => !p.stdProcessId || targetIds.has(p.stdProcessId))
}, { deep: false })

// 作业项目全量（WorkProjectPicker 内部已加载，这里再取一次用于名称回填）
const allProcesses = ref<any[]>([])
async function loadAllProcesses() {
  try {
    const res = await request.get('/engineering/standard-processes/page', {
      params: { pageNum: 1, pageSize: 100, isEnabled: 1, orderByColumn: 'displayOrder', isAsc: 'asc' },
    })
    allProcesses.value = res.data?.records || []
  } catch {
    allProcesses.value = []
  }
}

// 保存工序计划（整单覆盖当前轮次）
async function savePlan() {
  if (!props.card?.orderId) return
  if (!planList.value.length) {
    ElMessage.warning('工序计划为空，请先勾选作业项目')
    return
  }
  savingPlan.value = true
  try {
    const items = planList.value.map((p) => ({
      processId: p.processId ?? undefined,
      stdProcessId: p.stdProcessId ?? undefined,
      processName: p.processName,
      materials: p.materials,
      processNote: p.processNote,
      status: p.status ?? 0,
    }))
    await sampleOrderApi.saveProcessPlan(props.card.orderId, { items })
    ElMessage.success(`工序计划已保存（${items.length}道）`)
    await loadPlan()
    await refreshCard()
    await loadSummary()
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存工序计划失败')
  } finally {
    savingPlan.value = false
  }
}

// 开始/完成
async function advancePlan(p: any) {
  if (!props.card?.orderId || !p.processId) return
  const next = p.status === 1 ? 2 : 1
  p.advancing = true
  try {
    await sampleOrderApi.updateProcessItemStatus(props.card.orderId, p.processId, { status: next })
    ElMessage.success(next === 2 ? '工序已完成' : '工序已开始')
    await loadPlan()
    await refreshCard()
    await loadSummary()
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    p.advancing = false
  }
}

// 保存单张卡片（描述/材料）
async function saveCard(p: any) {
  if (!props.card?.orderId || !p.processId) return
  p.savingCard = true
  try {
    const validMats = (p.materialRows || [])
      .filter((m: any) => m.name && m.name.trim())
      .map((m: any) => ({
        name: m.name,
        spec: m.spec || '',
        qty: m.qty ?? 1,
        unit: m.unit || 'PCS',
        materialId: m.materialId,
        materialCode: m.materialCode || '',
      }))
    const materialsJson = validMats.length > 0 ? JSON.stringify(validMats) : null
    await sampleOrderApi.updateProcessItemStatus(props.card.orderId, p.processId, {
      status: p.status ?? 0,
      processNote: p.processNote || undefined,
      materials: materialsJson,
    })
    p.materials = materialsJson
    p.matEditing = false
    ElMessage.success('已保存')
    await loadBom()
    await loadSummary()
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    p.savingCard = false
  }
}

// 删除计划行（保存计划时生效；已保存的走整单覆盖）
function removePlan(p: any) {
  planList.value = planList.value.filter((x) => x !== p)
  if (p.stdProcessId) {
    selectedProcessIds.value = selectedProcessIds.value.filter((id) => id !== p.stdProcessId)
  }
}

// 图标下标数字：更新 description 中 <jump>N</jump> 并保存（2026-08-09）
async function onUpdateDesc(p: any, desc: string) {
  p.description = desc
  if (!p.stdProcessId) return
  try {
    await standardProcessApi.update(p.stdProcessId, { description: desc } as any)
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// 跳转（现阶段空函数，后续接跳转逻辑）
function onJump(step: number) {
  // TODO: 跳转到对应步骤（如跳转到下线第 N 步）
  console.log('[IconStepBadge] jump step =', step)
}

// 材料编辑展开/收起
function toggleMatEditor(p: any) {
  p.matEditing = !p.matEditing
  if (p.matEditing && (!p.materialRows || !p.materialRows.length)) {
    p.materialRows = (parseMaterials(p.materials) || []).map((m: any) => ({
      name: m.name || '',
      spec: m.spec || '',
      qty: m.qty ?? 1,
      unit: m.unit || 'PCS',
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      options: [],
      loading: false,
    }))
    if (!p.materialRows.length) addMaterialRow(p)
  }
}

function addMaterialRow(p: any) {
  p.materialRows.push({ name: '', spec: '', qty: 1, unit: 'PCS', materialId: undefined as number | undefined, materialCode: '', options: [], loading: false })
}

// 远程搜索物料档案
async function searchMaterials(query: string, m: any) {
  if (!query || query.trim().length < 1) {
    m.options = []
    return
  }
  m.loading = true
  try {
    const res: any = await materialApi.search({ materialName: query.trim(), pageNum: 1, pageSize: 10 })
    m.options = res?.data?.records || res?.data || []
  } catch {
    m.options = []
  } finally {
    m.loading = false
  }
}

// 选中物料 → 自动填名称/规格/单位
function onMaterialSelected(m: any, materialId: number) {
  const mat = (m.options || []).find((o: any) => o.materialId === materialId)
  if (!mat) return
  m.name = mat.materialName
  m.spec = mat.specification || ''
  m.unit = mat.unit || 'PCS'
  m.materialCode = mat.materialCode || ''
}

// 建档弹窗
const materialCreateVisible = ref(false)
const materialPreset = ref<any>({})
const materialTarget = ref<any>(null)
function openMaterialCreate(p: any, m: any) {
  materialTarget.value = { card: p, row: m }
  materialPreset.value = { materialName: m.name || '', specification: m.spec || '', unit: m.unit || 'PCS' }
  materialCreateVisible.value = true
}

// 建档成功 → 填入目标行
function onMaterialCreated(mat: any) {
  const t = materialTarget.value
  if (t?.row) {
    t.row.materialId = mat.materialId
    t.row.materialCode = mat.materialCode || ''
    t.row.name = mat.materialName
    t.row.spec = mat.specification || ''
    t.row.unit = mat.unit || 'PCS'
    t.row.options = [mat]
  }
}

// 解析材料JSON
function parseMaterials(json?: string | null) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

// ===== 汇总 =====
const doneCount = computed(() => planList.value.filter((p) => p.status === 2).length)
const summary = ref<any>({})
async function loadSummary() {
  if (!props.card?.orderId) return
  try {
    const res = await request.get(`/sales/sample-order/summary/${props.card.orderId}`)
    summary.value = res.data || {}
  } catch {
    summary.value = {}
  }
}

// ===== 轮次展示（DEV-500）=====
const roundList = ref<any[]>([])
const activeRound = ref('')
const isCurrentRound = computed(() => Number(activeRound.value) === (props.card?.sampleRound || 1))
const activeRoundData = computed(() => roundList.value.find((r) => String(r.roundNo) === activeRound.value) || null)
const activeRoundProcesses = computed(() => {
  const d = activeRoundData.value
  if (!d?.processSnapshot) return []
  try {
    const arr = JSON.parse(d.processSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
const activeRoundBom = computed(() => {
  const d = activeRoundData.value
  if (!d?.bomSnapshot) return []
  try {
    const arr = JSON.parse(d.bomSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
async function loadRounds() {
  if (!props.card?.orderId) return
  try {
    const res = await sampleOrderApi.getRounds(props.card.orderId)
    const rounds: any[] = res.data || []
    const current = props.card?.sampleRound || 1
    if (!rounds.some((r) => r.roundNo === current)) {
      rounds.push({ roundNo: current, result: 'pending' })
    }
    roundList.value = rounds.sort((a, b) => a.roundNo - b.roundNo)
    activeRound.value = String(current)
  } catch {
    roundList.value = [{ roundNo: props.card?.sampleRound || 1, result: 'pending' }]
    activeRound.value = String(props.card?.sampleRound || 1)
  }
}
const bomList = ref<any[]>([])
const engUploadRef = ref()
const engFileList = ref<any[]>([])

function onClose(val: boolean) {
  if (!val) emit('update:visible', false)
}

async function onOpen() {
  if (!props.card?.orderId) return
  await Promise.all([loadRounds(), loadPlan(), loadBom(), loadEngFiles(), loadSummary(), loadAllProcesses()])
  form.note = props.card.engineeringNote || ''
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

// 接单
async function handleAccept() {
  if (!props.card?.orderId) return
  try {
    await ElMessageBox.confirm('确认接单开始打样？', '工程接单', { confirmButtonText: '确认接单', cancelButtonText: '取消', type: 'info' })
    await sampleOrderApi.acceptEngineering(props.card.orderId)
    ElMessage.success('接单成功')
    emit('saved')
    await refreshCard()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '接单失败')
  }
}

// 拒单
async function handleReject() {
  if (!props.card?.orderId) return
  try {
    const { value } = await ElMessageBox.prompt('请填写拒单原因', '工程拒单', {
      confirmButtonText: '确认拒单', cancelButtonText: '取消', type: 'warning',
      inputPlaceholder: '拒单原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '拒单原因不能为空'),
    })
    await sampleOrderApi.rejectEngineering(props.card.orderId, value.trim())
    ElMessage.success('已拒单，退回待审核')
    emit('saved')
    onClose(false)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '拒单失败')
  }
}

// 保存工艺参数
async function saveNote() {
  if (!props.card?.orderId) return
  saving.value = true
  try {
    await sampleOrderApi.startEngineering(props.card.orderId, form.note)
    props.card.engineeringNote = form.note
    ElMessage.success('工艺参数已保存')
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 资料转移（一键转量产建档）
async function handleTransfer() {
  if (!props.card?.orderId) return
  try {
    await ElMessageBox.confirm('确认执行资料转移？将按本轮工序计划建档产品/BOM/工艺路线（仅已确认或已转量产状态可转移）', '资料转移', {
      confirmButtonText: '确认转移', cancelButtonText: '取消', type: 'warning',
    })
    transfering.value = true
    const res: any = await sampleOrderApi.transfer(props.card.orderId)
    const d = res.data || {}
    ElMessage.success(`转移成功：${d.transferNo || ''}`)
    if (d.detail?.length) {
      ElMessageBox.alert((d.detail || []).join('\n'), '转移明细', { confirmButtonText: '知道了' })
    }
    emit('saved')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '转移失败')
  } finally {
    transfering.value = false
  }
}

// 图纸
const engBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过10MB')
    return false
  }
  return true
}
async function engUploadFile(options: any) {
  if (!props.card?.orderId) return
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'sample')
  fd.append('bizId', String(props.card.orderId))
  if (props.card?.traceId) {
    fd.append('traceId', props.card.traceId)
  }
  try {
    const res = await request.post('/system/attachment/upload', fd)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('上传成功')
      await loadEngFiles()
      emit('saved')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  }
}
async function engRemoveFile(file: any) {
  if (file.id) {
    try {
      await request.delete(`/system/attachment/${file.id}`)
    } catch { /* ignore */ }
  }
  engFileList.value = engFileList.value.filter(f => f.uid !== file.uid)
}
async function loadEngFiles() {
  if (!props.card?.orderId) return
  try {
    const res = await request.get(`/system/attachment/list?bizType=sample&bizId=${props.card.orderId}`)
    engFileList.value = (res.data || []).map((a: any) => ({
      uid: a.id, name: a.fileName, url: a.filePath, id: a.id,
    }))
  } catch {
    engFileList.value = []
  }
}

// 标记完成
async function handleMarkReady() {
  if (!props.card?.orderId) return
  try {
    await ElMessageBox.confirm('确认样品制作完成？将进入待送样状态', '标记完成', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'success',
    })
    await sampleOrderApi.markReady(props.card.orderId)
    ElMessage.success('已标记完成，待送样')
    emit('saved')
    onClose(false)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

// 加载工序计划（当前轮次，按顺序）
async function loadPlan() {
  if (!props.card?.orderId) return
  try {
    const res = await sampleOrderApi.listProcesses(props.card.orderId, props.card?.sampleRound || undefined)
    const list: any[] = res.data || []
    list.sort((a, b) => (a.processOrder || 999) - (b.processOrder || 999) || (a.processId || 0) - (b.processId || 0))
    planList.value = list.map((p) => ({
      ...p,
      uid: `db-${p.processId}`,
      materialRows: [],
      matEditing: false,
      advancing: false,
      savingCard: false,
    }))
    // 同步选择器勾选状态
    selectedProcessIds.value = list
      .map((p) => p.stdProcessId)
      .filter((id): id is number => !!id)
  } catch {
    planList.value = []
  }
}

async function loadBom() {
  // 从工序材料聚合
  if (!props.card?.orderId) return
  try {
    const res = await sampleOrderApi.listProcesses(props.card.orderId)
    const procs = res.data || []
    const agg: any[] = []
    for (const p of procs) {
      if (!p.materials) continue
      try {
        const mats = JSON.parse(p.materials)
        for (const m of mats) {
          agg.push({ process: p.processName, name: m.name, spec: m.spec, qty: m.qty, unit: m.unit })
        }
      } catch { /* ignore */ }
    }
    bomList.value = agg
  } catch { bomList.value = [] }
}
async function refreshCard() {
  if (!props.card?.orderId) return
  try { const res = await sampleOrderApi.getInfo(props.card.orderId); Object.assign(props.card, res.data) } catch { /* ignore */ }
}

watch(() => props.visible, (v) => { if (v) onOpen() })
</script>

<style scoped>
.wb-body {
  height: 780px;
  overflow-y: auto;
  padding-right: 8px;
}

/* el-dialog 固定高度 */
:deep(.el-dialog) {
  height: 800px;
  display: flex;
  flex-direction: column;
}
:deep(.el-dialog__body) {
  flex: 1;
  overflow: hidden;
}

.wb-card {
  margin-bottom: 14px;
}

.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}

/* 汇总（样品单信息底部） */
.summary-inline {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 36px;
  flex-wrap: wrap;
}
.summary-item {
  text-align: center;
}
.summary-num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.summary-item:nth-child(2) .summary-num {
  color: #67c23a;
}
.summary-item:nth-child(3) .summary-num {
  color: #e6a23c;
}
.summary-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.summary-tip {
  font-size: 12px;
  color: #999;
}

.accept-row {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}

/* 中间左右分栏 */
.mid-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.col-picker {
  width: 400px;
  flex-shrink: 0;
}
.col-plan {
  flex: 1;
  min-width: 0;
}
.picker-actions {
  margin-top: 10px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 底部左右分栏 */
.bottom-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.col-timeline {
  flex: 1.2;
  min-width: 0;
}
.col-bom {
  flex: 1;
  min-width: 0;
}
.col-note {
  flex: 1.2;
  min-width: 0;
}
.col-files {
  flex: 1;
  min-width: 0;
}

/* 工序卡片 */
.plan-scroll {
  max-height: 460px;
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-width: thin;
}
.plan-scroll::-webkit-scrollbar {
  width: 6px;
}
.plan-scroll::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

.plan-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(31, 45, 61, 0.05);
  transition: box-shadow 0.2s, border-color 0.2s;
}
.plan-card:hover {
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.1);
  border-color: #c6d9f5;
}

.pc-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pc-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.pc-ico {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #f0f6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pc-emoji {
  font-size: 18px;
}
.pc-title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.pc-name {
  font-size: 14px;
  font-weight: 600;
}
.pc-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.pc-body {
  margin-top: 10px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.pc-row-label {
  font-size: 12px;
  color: #909399;
  width: 72px;
  flex-shrink: 0;
  line-height: 26px;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
.mat-tags {
  margin-bottom: 6px;
}

/* 执行时间线 */
.timeline {
  position: relative;
  padding-left: 20px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 4px;
  bottom: 4px;
  width: 2px;
  background: #e4e7ed;
}
.tl-item {
  position: relative;
  padding-bottom: 16px;
}
.tl-item::before {
  content: '';
  position: absolute;
  left: -17px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.tl-item.done::before {
  background: #67c23a;
}
.tl-item.doing::before {
  background: #409eff;
  box-shadow: 0 0 0 3px #ecf5ff;
}
.tl-item .t {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tl-item .s {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.tl-item .n {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
}

/* BOM 转移区 */
.transfer-zone {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.transfer-zone .desc {
  display: block;
  margin-left: 0;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
