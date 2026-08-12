<template>
  <div class="wb-page">
    <!-- 样品单信息（含接单/拒单 + 图纸上传，dev-20260811-008） -->
    <SampleInfoCard
      :card="card"
      :done-count="doneCount"
      :plan-count="planList.length"
      :summary="summary"
      :saving="saving"
      :eng-file-list="engFileList"
      @history-copy="openHistoryCopy"
      @back="goBack"
      @accept="handleAccept"
      @reject="handleReject"
      @upload="engUploadFile"
      @remove="engRemoveFile"
    />

    <!-- 轮次切换（全局，冲型组装/印刷共用，2026-08-12 布局调整） -->
    <el-tabs v-model="activeRound" style="margin-bottom:10px">
      <el-tab-pane
        v-for="r in roundList"
        :key="r.roundNo"
        :name="String(r.roundNo)"
        :label="`Round ${r.roundNo}${r.roundNo === (card.sampleRound || 1) ? '（当前）' : ''}`"
      />
    </el-tabs>

    <!-- 一级大类 Tabs（dev-20260811-009）：冲型组装 | 印刷 -->
    <el-tabs v-model="majorCategory" style="margin-bottom:10px">
      <el-tab-pane label="🛠 冲型组装" name="ASSEMBLY">
      <template v-if="isCurrentRound">
      <!-- 工序计划面板（左选择器 + 右卡片区） -->
      <PlanBoard
        :batch-mode="batchMode"
        :batch-selected="batchSelected"
        :batch-category="batchCategory"
        :saving-plan="savingPlan"
        :frequent-materials="frequentMaterials"
        :plan-tabs="planTabs"
        v-model:active-plan-tab="activePlanTab"
        :category-options="categoryOptions"
        :cards-by-tab="cardsByTab"
        :save-state-text="saveStateText"
        :parse-materials="parseMaterials"
        :toggle-batch-mode="toggleBatchMode"
        :toggle-batch-select="toggleBatchSelect"
        :toggle-batch-select-all="toggleBatchSelectAll"
        :save-plan="savePlan"
        :add-frequent-material="addFrequentMaterial"
        :on-plan-drop="onPlanDrop"
        :on-card-drop="onCardDrop"
        :on-card-drag-over="onCardDragOver"
        :on-card-drag-leave="onCardDragLeave"
        :remove-card-item="removeCardItem"
        :remove-plan-card="removePlanCard"
        :advance-plan="advancePlan"
        :save-card="saveCard"
        :add-material-row="addMaterialRow"
        :start-edit="startEdit"
        :open-card-picker="openCardPicker"
        :open-batch-material="openBatchMaterial"
        :batch-delete="batchDelete"
        :apply-batch-category="applyBatchCategory"
        :on-update-index="onUpdateIndex"
        :search-materials="searchMaterials"
        :on-material-selected="onMaterialSelected"
        :on-select-visible-change="onSelectVisibleChange"
        :open-material-create="openMaterialCreate"
      />

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
      </el-tab-pane>
      <el-tab-pane label="🖨️ 印刷" name="PRINT">
      <template v-if="isCurrentRound">
        <PrintProcessPanel
          :print-list="printList"
          :saving-plan="savingPlan"
          :parse-materials="parseMaterials"
          :add-print-row="addPrintRow"
          :remove-print-row="removePrintRow"
          :move-print-row="movePrintRow"
          :advance-print="advancePrint"
          :save-plan="savePlan"
        />
      </template>
      <div v-else class="round-readonly">
        <el-alert
          type="info" :closable="false" show-icon style="margin-bottom:12px"
          title="历史轮次（只读）"
          description="该轮次已归档，印刷工序请查看下方快照"
        />
        <el-card shadow="never">
          <template #header><span style="font-weight:600">🖨️ 印刷工序快照</span></template>
          <el-table v-if="activeRoundPrintList.length" :data="activeRoundPrintList" size="small" border style="width:100%">
            <el-table-column prop="processName" label="印刷名称" min-width="130" />
            <el-table-column label="参数" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="(v, k) in printParamsOf(row)" :key="k" size="small" type="info" style="margin-right:4px">{{ k }}: {{ v }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="材料" min-width="160">
              <template #default="{ row }">
                <el-tag v-for="(m, i) in parseMaterials(row.materials)" :key="i" size="small" style="margin-right:4px">{{ m.name }}</el-tag>
                <span v-if="!parseMaterials(row.materials).length" style="color:#c0c4cc">无</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : row.status === 1 ? 'warning' : 'info'" size="small">
                  {{ row.status === 2 ? '完成' : row.status === 1 ? '进行中' : '待做' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else style="color:#999;font-size:13px">该轮次无印刷工序</div>
        </el-card>
      </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 公用区（冲型组装/印刷共用，2026-08-12 布局调整）：执行时间线 | BOM + 工艺参数/工程备注 + 标记完成 -->
    <template v-if="isCurrentRound">
      <div class="bottom-row">
        <ExecutionTimeline
          :plan-list="planList"
          :print-list="printList"
          :round="card.sampleRound || 1"
          :format-time="formatTime"
          :parse-materials="parseMaterials"
        />
        <BomPanel :bom-list="bomList" @transfer="handleTransfer" />
      </div>

      <!-- 工艺参数（图纸已挪到样品信息卡） -->
      <div class="bottom-row">
        <NoteFilesPanel v-model="form.note" :saving="saving" @save="saveNote" />
      </div>

      <!-- 标记完成 -->
      <div style="text-align:center;margin:12px 0 4px">
        <el-button type="success" size="large" @click="handleMarkReady" :loading="saving" style="width:220px">🎯 标记样品完成（送样）</el-button>
      </div>
    </template>

    <!-- 卡片作业项目追加选择器（多选，任意结构） -->
    <el-dialog v-model="cardPickerVisible" title="＋ 添加作业项目（可多选）" width="620px" append-to-body>
      <WorkProjectPicker v-model="cardPickerIds" @confirm="onCardPickerConfirm" />
    </el-dialog>

    <!-- 下标输入弹窗（DEV-777，仿工艺路线） -->
    <el-dialog v-model="indexDialogVisible" title="输入下标数字" width="380px" append-to-body>
      <div style="font-size:13px;color:#606266;margin-bottom:12px">
        作业项目 <b>{{ indexDialogName }}</b> 带下标，请输入下标数字（正整数）：
      </div>
      <el-input-number
        v-model="indexDialogValue"
        :min="1"
        :max="999"
        :precision="0"
        controls-position="right"
        style="width:100%"
        placeholder="如 4 显示为 ④"
      />
      <template #footer>
        <el-button @click="indexDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!indexDialogValue" @click="confirmIndexDialog">确定</el-button>
      </template>
    </el-dialog>

    <!-- 物料建档弹窗 -->
    <MaterialFormDialog
      v-model="materialCreateVisible"
      :preset-data="materialPreset"
      @success="onMaterialCreated"
    />

    <!-- 打样转标准·轻量版弹窗（DEV-764：资料转移统一入口） -->
    <SampleTransferDialog
      v-model="transferDialogVisible"
      :order-id="orderId"
      @success="onTransferSuccess"
    />

    <!-- 从历史打样复制弹窗 -->
    <el-dialog v-model="historyCopyVisible" title="📋 从历史打样复制" width="640px" append-to-body>
      <el-alert
        type="info" :closable="false" show-icon
        title="选择已转标准的样品单，复制其工序计划（工序/分组/材料）到当前打样单，追加到现有卡片后面"
        style="margin-bottom:12px"
      />
      <el-table
        v-loading="historyLoading" :data="historyOrders" size="small" border stripe
        max-height="360" highlight-current-row
        @current-change="(row: any) => (historySelected = row)"
      >
        <el-table-column prop="orderNo" label="样品单号" width="150" />
        <el-table-column prop="customerName" label="客户" min-width="130" />
        <el-table-column prop="sampleRound" label="轮次" width="70" align="center" />
        <el-table-column prop="orderDate" label="日期" width="110" />
      </el-table>
      <template #footer>
        <el-button @click="historyCopyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!historySelected" :loading="historyCopying" @click="confirmHistoryCopy">
          复制到当前
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量添加材料弹窗 -->
    <el-dialog v-model="batchMaterialVisible" title="批量添加材料" width="460px" append-to-body>
      <el-select
        v-model="batchMaterialId"
        filterable
        remote
        :remote-method="(q: string) => searchBatchMaterial(q)"
        :loading="batchMaterialLoading"
        placeholder="搜索物料档案"
        style="width:100%"
      >
        <el-option
          v-for="opt in batchMaterialOptions" :key="opt.materialId"
          :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
          :value="opt.materialId"
        />
      </el-select>
      <div style="font-size:12px;color:#909399;margin-top:8px">将添加到 {{ batchSelected.size }} 张选中卡片的材料列表</div>
      <template #footer>
        <el-button @click="batchMaterialVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!batchMaterialId" @click="confirmBatchMaterial">添加</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { useSampleWorkbench } from './composables/useSampleWorkbench'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import WorkProjectPicker from '@/views/sales/sample-order/components/WorkProjectPicker.vue'
import SampleTransferDialog from '@/views/sales/sample-order/components/SampleTransferDialog.vue'
import SampleInfoCard from './components/SampleInfoCard.vue'
import PlanBoard from './components/PlanBoard.vue'
import ExecutionTimeline from './components/ExecutionTimeline.vue'
import BomPanel from './components/BomPanel.vue'
import NoteFilesPanel from './components/NoteFilesPanel.vue'
import PrintProcessPanel from './components/PrintProcessPanel.vue'

defineOptions({ name: 'SampleWorkbenchPage' })

// 全部状态与逻辑来自 composable（dev-20260811-008 组件化）
const {
  card, orderId, saving, savingPlan, form, planList, majorCategory, printList,
  makePrintRow, addPrintRow, removePrintRow, movePrintRow, advancePrint,
  frequentMaterials, saveStateText, markDirty,
  batchMode, batchSelected, batchCategory, toggleBatchMode, toggleBatchSelect,
  toggleBatchSelectAll, batchSelectedCards, applyBatchCategory, batchDelete,
  batchMaterialVisible, batchMaterialId, batchMaterialOptions, batchMaterialLoading,
  openBatchMaterial, searchBatchMaterial, confirmBatchMaterial,
  historyCopyVisible, historyOrders, historyLoading, historySelected, historyCopying,
  openHistoryCopy, confirmHistoryCopy,
  loadFrequentMaterials, addFrequentMaterial,
  planTabs, activePlanTab, cardsByTab, typeOptions, categoryOptions, typeLabel, categoryLabel,
  allProcesses, loadAllProcesses, genUid, makeCard, savePlan,
  cardPickerVisible, cardPickerTarget, cardPickerIds, openCardPicker, onCardPickerConfirm,
  enrichProcess, indexDialogVisible, indexDialogValue, indexDialogName,
  openIndexDialog, confirmIndexDialog, maybePromptIndex, onUpdateIndex,
  parseDragData, onPlanDrop, onCardDrop, onCardDragOver, onCardDragLeave, clearDragOver,
  removeCardItem, removePlanCard, advancePlan, saveCard,
  addMaterialRow, startEdit, searchMaterials, onSelectVisibleChange, loadMoreMaterials,
  onMaterialSelected, materialCreateVisible, materialPreset, openMaterialCreate, onMaterialCreated,
  parseMaterials, doneCount, summary, loadSummary,
  roundList, activeRound, isCurrentRound, activeRoundData, activeRoundProcesses, activeRoundBom,
  activeRoundPrintList, printParamsOf,
  loadRounds, bomList, engUploadRef, engFileList, goBack,
  loadDetail, formatTime, handleAccept, handleReject, saveNote,
  handleTransfer, transferDialogVisible, onTransferSuccess,
  engBeforeUpload, engUploadFile, engRemoveFile, loadEngFiles, handleMarkReady,
  loadPlan, loadBom, refreshCard,
} = useSampleWorkbench()

// 加载（页面打开即载入）
loadDetail()
</script>

<style scoped>
.wb-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 8px;
}

.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}

.wb-card {
  margin-bottom: 14px;
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

/* 常用物料快捷区 */
.freq-materials {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f8fbff;
  border: 1px dashed #b3d8ff;
  border-radius: 8px;
}
.freq-label {
  font-size: 12px;
  font-weight: 600;
  color: #e6a23c;
  margin-right: 2px;
}
.freq-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.freq-tag:hover {
  border-color: #e6a23c;
  color: #e6a23c;
  background: #fdf6ec;
}

/* 保存状态标记 */
.save-state {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.save-synced {
  color: #67c23a;
}
.save-dirty {
  color: #909399;
}
.save-saving {
  color: #409eff;
}
.save-error {
  color: #f56c6c;
}

/* 批量编辑 */
.batch-check {
  margin-right: 2px;
}
.plan-card.batch-selected {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25);
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 10px 14px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 8px;
}
.batch-info {
  font-size: 13px;
  color: #b88230;
}
.batch-label {
  font-size: 12px;
  color: #606266;
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

/* 工序卡片（四行布局） */
.plan-scroll {
  max-height: 560px;
  min-height: 260px;
  overflow-y: auto;
  padding-right: 6px;
  padding-bottom: 40px; /* 卡片下方保留空白 drop 区（DEV-768后修复：已有卡片时仍可拖入新建） */
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
.plan-card.drag-over {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
  background: #f5f9ff;
}

.plan-drop-hint {
  border: 2px dashed #c0c4cc;
  border-radius: 10px;
  padding: 60px 0;
  text-align: center;
  color: #909399;
  font-size: 16px;
  background: #fafbfc;
}

.plan-card:hover {
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.1);
  border-color: #c6d9f5;
}

.pc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
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
.pc-head-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.pc-row-label {
  font-size: 12px;
  color: #909399;
  width: 72px;
  flex-shrink: 0;
  line-height: 26px;
}
.pc-items {
  flex: 1;
  min-width: 0;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
.pc-desc-readonly {
  flex: 1;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
  background: #fafbfc;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 26px;
  white-space: pre-wrap;
}

.pc-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 8px;
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
