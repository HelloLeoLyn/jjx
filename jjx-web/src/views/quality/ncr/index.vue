<template>
  <div class="ncr-page">
    <el-alert type="info" :closable="false" show-icon class="scope-guide">
      <template #title>
        <div class="scope-guide__content">
          <span>本页主要处理成品检验批不良：返工、让步接收（特采）或报废。成品让步接收必须取得客户确认。</span>
          <el-button link type="primary" @click="goIqcQuarantine">查看来料不合格处置</el-button>
        </div>
      </template>
    </el-alert>
    <el-card>
      <template #header>
        <div class="header">
          <span>产品不良台账</span>
          <div>
            <el-select v-model="query.lotType" clearable placeholder="来源类型" style="width: 140px" @change="load(1)">
              <el-option label="来料检验" value="IQC" />
              <el-option label="成品检验" value="FQC" />
            </el-select>
            <el-select v-model="query.status" clearable placeholder="状态" style="width: 130px" @change="load(1)">
              <el-option label="待处置" value="PENDING" />
              <el-option label="处置中" value="DISPOSING" />
              <el-option label="已结" value="CLOSED" />
            </el-select>
            <el-input v-model="query.materialCode" clearable placeholder="物料编码" style="width: 160px" @keyup.enter="load(1)" />
            <el-button type="primary" @click="load(1)">查询</el-button>
            <!-- dev-20260924-007：隔离台账（在隔离的货 = 未处置不良；只做标识，不动库存） -->
            <el-button type="warning" plain @click="openQuarantine">隔离台账</el-button>
            <el-button @click="load()">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border size="small">
        <template #empty><el-empty description="暂无不良记录" /></template>
        <el-table-column prop="ncrNo" label="不良单号" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.lotType === 'IQC' ? '来料' : '成品' }}</template>
        </el-table-column>
        <el-table-column label="工单/来源" min-width="150">
          <template #default="{ row }">
            <!-- dev-20260923-036：显示单号而不是裸 ID（原来「工单 #2 / 批 #11」看不出对的是谁） -->
            <div>{{ row.orderNo || (row.orderId ? '工单 #' + row.orderId : '-') }}</div>
            <div v-if="row.lotNo" class="sub">
              检验批 {{ row.lotNo }}
              <el-tag v-if="row.lotSuperseded" size="small" type="info" effect="plain">已失效</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物料/产品" min-width="150">
          <template #default="{ row }">
            {{ row.materialCode || row.productCode || '-' }}
            <span class="sub">{{ row.materialName || row.productName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="120" />
        <el-table-column label="不良数量" width="95" align="right">
          <template #default="{ row }">{{ num(row.defectQuantity) }}</template>
        </el-table-column>
        <el-table-column label="CR/MA/MI" width="110" align="center">
          <template #default="{ row }">{{ num(row.crQuantity) }}/{{ num(row.maQuantity) }}/{{ num(row.miQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已处置" width="90" align="right">
          <template #default="{ row }">{{ num(row.disposedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待处置" width="120" align="right">
          <template #default="{ row }">
            <el-tag v-if="pending(row) > 0" type="danger" size="small">{{ num(pending(row)) }}</el-tag>
            <!-- dev-20260924-007：待处置 = 隔离中（未处置的不良件视为在隔离；一期只做标识，不动库存） -->
            <el-tag v-if="pending(row) > 0" type="warning" size="small" effect="plain" style="margin-left: 4px">
              隔离中
            </el-tag>
            <span v-if="pending(row) <= 0">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="QualityNcrStatusEnum.getTagProps(row.status).type">
              {{ QualityNcrStatusEnum.getLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defectReason" label="不良原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <!-- dev-20260923-039：按钮只按后端下发的 allowedActions 渲染（前端不再写状态条件） -->
            <el-button
              v-if="can(row, 'NCR_DISPOSE')"
              link
              type="primary"
              size="small"
              @click="openDispose(row)"
              >处置</el-button
            >
            <el-button link size="small" @click="openActions(row)">处置记录</el-button>
            <!-- dev-20260924-004：不良件级明细（件号/主缺陷/实测值/状态）—— 件是处置的最小单位 -->
            <el-button link size="small" @click="openPieces(row)">不良件</el-button>
            <!-- dev-20260923-040：随批作废（正式入口）—— 仅「来源批已被后继复检版本取代」的悬空单会下发该动作 -->
            <el-button
              v-if="can(row, 'NCR_VOID_SUPERSEDED')"
              link
              type="danger"
              size="small"
              @click="handleVoidSuperseded(row)"
              >随批作废</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load()"
          @size-change="load(1)"
        />
      </div>
    </el-card>

    <!-- 处置 -->
    <el-dialog v-model="disposeVisible" title="不良处置" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="不良单号">{{ current?.ncrNo }}</el-form-item>
        <el-form-item label="待处置数量">{{ num(current ? pending(current) : 0) }}</el-form-item>
        <el-form-item label="处置方式" required>
          <el-select v-model="disposeForm.actionType" style="width: 100%">
            <el-option label="返工" value="REWORK" />
            <el-option label="让步接收（特采）" value="CONCESSION" />
            <el-option label="报废" value="SCRAP" />
          </el-select>
        </el-form-item>
        <!-- dev-20260924-005：报废授权分档提示（阈值 sys_config: quality.ncr.scrap.approval-threshold，缺省 5） -->
        <el-alert
          v-if="disposeForm.actionType === NcrActionType.SCRAP && Number(disposeForm.quantity || 0) > 5"
          type="warning"
          :closable="false"
          show-icon
          title="报废超过阈值（缺省 5 件）：提交后进入「待审批」，需品质主管审批通过才计入台账与件级（审批人不得为提交人）"
          style="margin-bottom: 12px"
        />
        <el-form-item label="处置数量" required>
          <el-input-number v-model="disposeForm.quantity" :min="1" :max="current ? pending(current) : 0" />
        </el-form-item>
        <template v-if="disposeForm.actionType === NcrActionType.REWORK">
          <el-form-item label="返工工序" required>
            <el-select v-model="disposeForm.standardProcessId" filterable style="width: 100%" placeholder="选择标准工序">
              <el-option
                v-for="process in standardProcesses"
                :key="process.processId"
                :label="`${process.processCode || ''} ${process.processName}`.trim()"
                :value="process.processId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="返工要求">
            <el-input v-model="disposeForm.reworkRequirement" type="textarea" :rows="3" placeholder="填写本次返工的特殊要求" />
          </el-form-item>
        </template>
        <el-form-item v-if="disposeForm.actionType === NcrActionType.CONCESSION" label="客户已确认">
          <el-switch v-model="disposeForm.customerConfirmed" />
          <span class="tip">让步接收必须先取得客户确认</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="disposeForm.resultRemark" type="textarea" :rows="2" placeholder="可空" />
        </el-form-item>
        <div class="tip block">
          返工：暂不影响库存，生成返工工序并在复检合格后入库；让步接收（特采）：影响库存，须客户确认后转良品库存；报废：不影响库存，只记台账（不良品未入良品库）
        </div>
      </el-form>
      <template #footer>
        <el-button @click="disposeVisible = false">取消</el-button>
        <el-button type="primary" :loading="disposing" @click="submitDispose">提交处置</el-button>
      </template>
    </el-dialog>

    <!-- 报废处置行发起补料（dev-20260923-025）：复用领料预览弹窗的补料模式 -->
    <PickPreviewDialog
      v-if="supplementOrderId"
      v-model="supplementDialogVisible"
      :work-order-id="supplementOrderId"
      :order-no="supplementOrderNo"
      mode="supplement"
      preset-reason-type="SCRAP_REPLENISHMENT"
      :preset-ncr-id="supplementNcrId"
      :preset-production-quantity="supplementPresetQty"
      @success="onSupplementSuccess"
    />

    <!-- 隔离台账（dev-20260924-007 一期） -->
    <el-dialog v-model="quarantineVisible" title="隔离台账（在隔离的货）" width="960px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="口径：隔离 = 未处置不良（不良 − 已处置）；件级 = 不良件状态「待处置」。一期只做标识，库存不动（库存只装良品）。"
        style="margin-bottom: 10px"
      />
      <el-table v-loading="quarantineLoading" :data="quarantineRows" border size="small" max-height="56vh">
        <el-table-column prop="ncrNo" label="不良单号" min-width="140" />
        <el-table-column label="工单/批" min-width="180">
          <template #default="{ row }">
            <div>{{ row.orderNo || (row.orderId ? '工单 #' + row.orderId : '-') }}</div>
            <div class="sub">{{ row.lotNo ? '批 ' + row.lotNo : '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="物料/产品" min-width="160">
          <template #default="{ row }">
            {{ row.materialCode || row.productCode || '-' }}
            <span class="sub">{{ row.materialName || row.productName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="110" />
        <el-table-column label="不良" width="80" align="right">
          <template #default="{ row }">{{ num(row.defectQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已处置" width="85" align="right">
          <template #default="{ row }">{{ num(row.disposedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="隔离中" width="85" align="right">
          <template #default="{ row }">
            <el-tag type="warning" size="small" effect="plain">{{ num(row.quarantineQuantity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="件（待处置/总）" width="130" align="center">
          <template #default="{ row }">
            <span v-if="row.pieceTotal">{{ row.piecePending }}/{{ row.pieceTotal }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="主缺陷" min-width="130">
          <template #default="{ row }">
            <span v-if="row.mainCheckItem">{{ row.mainCheckItem }}</span>
            <el-tag v-if="row.mainDefectLevel" :type="levelTag(row.mainDefectLevel)" size="small" effect="plain" style="margin-left: 4px">
              {{ row.mainDefectLevel }}
            </el-tag>
            <span v-if="!row.mainCheckItem && !row.mainDefectLevel">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link size="small" @click="goDispose(row)">去处置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!quarantineLoading && !quarantineRows.length" class="sub">当前没有在隔离的货</div>
    </el-dialog>

    <!-- 不良件级明细（dev-20260924-004） -->
    <el-dialog v-model="piecesVisible" title="不良件（件级追溯）" width="900px" append-to-body>
      <div class="piece-tip">
        件号 {{ piecesNcr?.ncrNo }}-D…；件只做身份与追溯，不参与库存数量计算。处置按件生效（一件一个决定）。
      </div>
      <el-table :data="pieces" border size="small" row-key="pieceId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="piece-defects">
              <div v-for="(d, i) in row.defects || []" :key="i" class="piece-defect-row">
                <el-tag :type="levelTag(d.defectLevel)" size="small" effect="plain">{{ d.defectLevel || '其他' }}</el-tag>
                <span>{{ d.checkItem }}</span>
                <el-tag v-if="d.isMain === 1" type="warning" size="small" effect="plain">主缺陷</el-tag>
                <span v-if="d.remark" class="piece-defect-remark">{{ d.remark }}</span>
              </div>
              <div v-if="!(row.defects || []).length" class="piece-defect-remark">无缺陷记录</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="件号" prop="pieceNo" min-width="180" />
        <el-table-column label="主缺陷" min-width="150">
          <template #default="{ row }">
            <span v-if="row.mainCheckItem">{{ row.mainCheckItem }}</span>
            <span v-else>-</span>
            <el-tag v-if="row.mainDefectLevel" :type="levelTag(row.mainDefectLevel)" size="small" effect="plain" style="margin-left: 4px">
              {{ row.mainDefectLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实测值" width="120">
          <template #default="{ row }">{{ row.actualValue || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ row.statusLabel || row.status }}</template>
        </el-table-column>
        <el-table-column label="处置" width="100">
          <template #default="{ row }">{{ row.disposeType ? actionLabel(row.disposeType) : '-' }}</template>
        </el-table-column>
        <el-table-column label="工单 / 批" min-width="160">
          <template #default="{ row }">
            <span class="piece-sub">{{ row.workOrderNo || '-' }} / {{ row.lotNo || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!pieces.length" class="piece-tip">该不良单暂无不良件（本功能上线前登记的老单不追溯件级）</div>
    </el-dialog>

    <!-- 处置记录 -->
    <el-dialog v-model="actionsVisible" title="处置记录" width="680px" append-to-body>
      <el-table :data="actions" border size="small">
        <el-table-column label="方式" width="130">
          <template #default="{ row }">{{ actionLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="客户确认" width="100" align="center">
          <template #default="{ row }">{{ row.customerConfirmed ? '是' : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ actionStatusLabel(row.status) }}</template>
        </el-table-column>
        <!-- dev-20260923-031：返工要有进度（原来只给裸 ID「工序 #12」，看不出修到哪一步） -->
        <el-table-column label="返工 / 复检进度" min-width="260">
          <template #default="{ row }">
            <div v-if="reworkOf(row)" class="rework-cell">
              <el-tag type="danger" size="small" effect="plain">返工</el-tag>
              <span class="rework-tip">
                {{ reworkOf(row)?.processName || '返工工序' }} ·
                {{ reworkOf(row)?.statusText || '' }}
              </span>
              <div v-if="reworkOf(row)?.reinspectionLotNo" class="rework-tip">
                复检批 {{ reworkOf(row)?.reinspectionLotNo }}
              </div>
              <!-- dev-20260923-036：台账不承担派工动作（用户反馈：派工不该在这里）→ 只提示去哪派 -->
              <div v-if="reworkOf(row)?.executionId" class="rework-tip">
                待派工：到「生产管理 → 派工管理」选该工单把这道返工任务派给工人
              </div>
            </div>
            <div v-else-if="row.reinspectionLotId" class="rework-tip">
              复检批 #{{ row.reinspectionLotId }}
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultRemark" label="说明" min-width="220" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="can(row, 'NCR_REWORK_COMPLETE')"
              link
              type="primary"
              size="small"
              @click="completeAction(row)"
              >推进返工闭环</el-button
            >
            <el-button
              v-if="can(row, 'NCR_REWORK_SUPPLEMENT') && current?.orderId"
              link
              type="warning"
              size="small"
              @click="openSupplement(row)"
              >补料</el-button
            >
            <!-- dev-20260923-025：报废处置行可发起补料（报废补产场景；来源/不良单/建议补产数量预填） -->
            <el-button
              v-if="row.actionType === NcrActionType.SCRAP && current?.orderId"
              link
              type="warning"
              size="small"
              @click="openSupplementFromDispose()"
              >申请补料</el-button
            >
            <!-- dev-20260924-005：报废授权 —— 超阈值的报废待审批，品质主管「通过 / 驳回」（审批人≠提交人） -->
            <el-button
              v-if="can(row, 'NCR_SCRAP_APPROVE')"
              link
              type="primary"
              size="small"
              @click="handleScrapApprove(row)"
              >审批通过</el-button
            >
            <el-button
              v-if="can(row, 'NCR_SCRAP_REJECT')"
              link
              type="danger"
              size="small"
              @click="handleScrapReject(row)"
              >驳回</el-button
            >
            <!-- dev-20260923-022 二期：已生效报废可受控撤销（需权限点 + 填原因，留痕） -->
            <el-button
              v-if="can(row, 'NCR_REVOKE') && canRevoke"
              link
              type="danger"
              size="small"
              @click="openRevoke(row)"
              >撤销</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="revokeVisible" title="撤销处置" width="560px" append-to-body>
      <el-alert
        title="撤销是受控动作：必填原因并留痕（谁/何时/为何）；撤销后该批判定上界会随之恢复，可重新判定（若批已结，需先「重开」）。"
        type="warning"
        :closable="false"
      />
      <el-form label-width="90px" style="margin-top: 12px">
        <el-form-item label="处置方式">{{ actionLabel(revokeTarget?.actionType || '') }}</el-form-item>
        <el-form-item label="数量">{{ num(revokeTarget?.quantity) }}</el-form-item>
        <el-form-item label="撤销原因" required>
          <el-input v-model="revokeReason" type="textarea" :rows="2" placeholder="例如：误判，复检合格需回填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="danger" :loading="revoking" @click="submitRevoke">确认撤销</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supplementVisible" title="返工补料" width="760px" append-to-body>
      <el-alert title="补料不占 BOM 剩余定额；提交即记录当前用户为审批人，并生成待仓库发料单。" type="warning" :closable="false" />
      <el-table :data="supplementItems" border size="small" style="margin-top: 12px">
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="available" label="可用库存" width="100" align="right" />
        <el-table-column label="补料数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :max="Number(row.available || 0)" :precision="4" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="supplementVisible = false">取消</el-button>
        <el-button type="primary" :loading="supplementing" @click="submitSupplement">确认并生成补料单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  NcrActionStatus,
  NcrActionStatusEnum,
  NcrActionType,
  NcrActionTypeEnum,
  QualityNcrStatus,
  QualityNcrStatusEnum,
} from '@/enums/quality'
import { qualityNcrApi, type QualityNcr, type QualityNcrAction } from '@/api/quality/lot'
// dev-20260923-025：报废处置行发起补料（复用领料预览弹窗的补料模式）
import PickPreviewDialog from '@/views/production/order/components/PickPreviewDialog.vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import { outboundApi } from '@/api/inventory/outbound'
import type { PickPreviewRow } from '@/types/inventory/outbound'
import { hasPermi } from '@/directives'
import { reworkTraceApi } from '@/api/production/rework'
import type { ReworkTraceVO } from '@/types/production/operationExecution'

const router = useRouter()
const route = useRoute()
const goIqcQuarantine = () => router.push('/inventory/iqc-quarantine')
const loading = ref(false)
const rows = ref<QualityNcr[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, lotType: '', status: '', materialCode: '' })
// dev-20260922-012（G3）：支持从检验批工作台判定后带 materialCode 跳进来，直接筛到该物料
if (route.query.materialCode) {
  query.materialCode = String(route.query.materialCode)
}
const current = ref<QualityNcr | null>(null)
/** dev-20260923-022 二期：撤销是受控动作（需 quality:ncr:revoke，后端同样校验） */
const canRevoke = computed(() => hasPermi('quality:ncr:revoke'))

const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/**
 * 待处置数量 —— dev-20260923-038：已作废/已结的单不再计待处置
 * （原来只算「不良 − 已处置」，作废单会算出 2 件待处置，与「已作废」状态自相矛盾）
 */
const pending = (row: QualityNcr) =>
  String(row.status) === QualityNcrStatus.VOID ||
  String(row.status) === QualityNcrStatus.CLOSED
    ? 0
    : Math.max(0, Number(row.defectQuantity || 0) - Number(row.disposedQuantity || 0))
/**
 * dev-20260923-039：**按钮只按后端下发的 allowedActions 渲染**，前端不再写状态条件。
 * 唯一出处是后端 AllowedActionResolver（见 design 045 §2.3）；这样"界面给了注定失败的动作"不会再发生。
 * dev-20260923-040：NCR_VOID_SUPERSEDED（随批作废）已在本页渲染（正式入口：权限 quality:ncr:void-superseded + 必填原因 + 留痕，幂等）。
 */
const can = (row: { allowedActions?: string[] }, code: string) =>
  Array.isArray(row?.allowedActions) && row.allowedActions.includes(code)
// dev-20260923-041：展示文案统一走枚举（不再本地写状态字符串映射）
const statusLabel = (status: string) => QualityNcrStatusEnum.getLabel(status)
const actionLabel = (type: string) => NcrActionTypeEnum.getLabel(type)
const actionStatusLabel = (status: string) => NcrActionStatusEnum.getLabel(status)

const load = async (page?: number) => {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const res: any = await qualityNcrApi.page({ ...query })
    const data = res?.data
    rows.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : Number(data?.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载产品不良台账失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

const disposeVisible = ref(false)
const disposing = ref(false)
const standardProcesses = ref<StandardProcessItem[]>([])
const disposeForm = reactive({
  actionType: 'REWORK',
  quantity: 1,
  customerConfirmed: false,
  standardProcessId: undefined as number | undefined,
  reworkRequirement: '',
  resultRemark: '',
})
const openDispose = (row: QualityNcr) => {
  current.value = row
  disposeForm.actionType = 'REWORK'
  disposeForm.quantity = pending(row)
  disposeForm.customerConfirmed = false
  disposeForm.standardProcessId = undefined
  disposeForm.reworkRequirement = ''
  disposeForm.resultRemark = ''
  disposeVisible.value = true
}
const submitDispose = async () => {
  if (!current.value) return
  if (disposeForm.actionType === NcrActionType.CONCESSION && !disposeForm.customerConfirmed) {
    return ElMessage.warning('让步接收必须先勾选"客户已确认"')
  }
  if (disposeForm.actionType === NcrActionType.REWORK && !disposeForm.standardProcessId) {
    return ElMessage.warning('请选择返工工序')
  }
  disposing.value = true
  try {
    await qualityNcrApi.dispose(current.value.ncrId, { ...disposeForm })
    ElMessage.success('处置已登记')
    disposeVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '处置失败')
  } finally {
    disposing.value = false
  }
}

const actionsVisible = ref(false)
const actions = ref<QualityNcrAction[]>([])
/** 返工链进度（按 actionId 索引）—— dev-20260923-031 */
const reworkTraceMap = ref<Record<number, ReworkTraceVO>>({})
const reworkOf = (row: QualityNcrAction) =>
  row?.actionId ? reworkTraceMap.value[row.actionId] || null : null
const supplementVisible = ref(false)
const supplementing = ref(false)
const supplementAction = ref<QualityNcrAction | null>(null)
const supplementItems = ref<Array<PickPreviewRow & { quantity: number }>>([])
// ============ 不良件级明细（dev-20260924-004） ============
// ============ 隔离台账（dev-20260924-007 一期） ============
const quarantineVisible = ref(false)
const quarantineLoading = ref(false)
const quarantineRows = ref<any[]>([])
const openQuarantine = async () => {
  quarantineVisible.value = true
  quarantineLoading.value = true
  try {
    const res: any = await qualityNcrApi.quarantine()
    quarantineRows.value = res?.data || []
  } catch {
    quarantineRows.value = []
  } finally {
    quarantineLoading.value = false
  }
}
/** 隔离台账 → 直接对这条不良单处置（复用台账页处置弹窗） */
const goDispose = (row: any) => {
  quarantineVisible.value = false
  const target = rows.value.find((r) => Number(r.ncrId) === Number(row.ncrId))
  if (target) openDispose(target)
}

// ============ 报废处置行发起补料（dev-20260923-025） ============
const supplementDialogVisible = ref(false)
const supplementOrderId = ref<number>()
const supplementOrderNo = ref('')
const supplementNcrId = ref<number>()
const supplementPresetQty = ref<number>()
const openSupplementFromDispose = () => {
  const ncr = current.value
  if (!ncr?.orderId) {
    ElMessage.warning('该不良单未关联生产工单，无法发起补料')
    return
  }
  supplementOrderId.value = Number(ncr.orderId)
  supplementOrderNo.value = ncr.orderNo || ''
  supplementNcrId.value = ncr.ncrId
  // 建议补产数量默认取待处置量（工人可改）
  supplementPresetQty.value = Number(pending(ncr)) || undefined
  supplementDialogVisible.value = true
}
const onSupplementSuccess = () => {
  supplementDialogVisible.value = false
  ElMessage.success('补料单已生成（报废补产已同时生成补产任务，请到「生产管理 → 派工管理」派工）')
  load()
}

const piecesVisible = ref(false)
const pieces = ref<any[]>([])
const piecesNcr = ref<QualityNcr | null>(null)
const openPieces = async (row: QualityNcr) => {
  piecesNcr.value = row
  try {
    const res: any = await qualityNcrApi.pieces(row.ncrId)
    pieces.value = res?.data || []
  } catch {
    pieces.value = []
  }
  piecesVisible.value = true
}
/** 分级标签色：CR 致命=红 / MA 严重=橙 / MI 轻微=灰 */
const levelTag = (level?: string) =>
  level === 'CR' ? 'danger' : level === 'MA' ? 'warning' : 'info'

const openActions = async (row: QualityNcr) => {
  current.value = row
  try {
    const res: any = await qualityNcrApi.actions(row.ncrId)
    actions.value = res?.data || []
    // dev-20260923-031：一并取返工链进度（工序名 / 状态 / 回收数），把裸 ID 换成看得懂的一行
    reworkTraceMap.value = {}
    try {
      const trace: any = await reworkTraceApi.trace({ ncrId: row.ncrId })
      const map: Record<number, ReworkTraceVO> = {}
      ;(trace?.data || []).forEach((item: ReworkTraceVO) => {
        if (item.actionId) map[item.actionId] = item
      })
      reworkTraceMap.value = map
    } catch {
      reworkTraceMap.value = {}
    }
  } catch {
    actions.value = []
  }
  actionsVisible.value = true
}

/** ===== dev-20260923-022 二期：撤销已生效处置（本期支持报废 SCRAP） ===== */
const revokeVisible = ref(false)
const revoking = ref(false)
const revokeReason = ref('')
const revokeTarget = ref<QualityNcrAction | null>(null)

/**
 * dev-20260923-040：随批作废（正式入口）—— 仅「来源检验批已被后继复检版本取代」的悬空单可用；
 * 必填原因（留痕）+ 后端幂等；作废后该单不可再处置。
 */
async function handleVoidSuperseded(row: QualityNcr) {
  let reason = ''
  try {
    const res: any = await ElMessageBox.prompt(
      `不良单 ${row.ncrNo} 的来源检验批已被后续复检版本取代。作废后该单不可再处置，请填写原因：`,
      '随批作废',
      {
        confirmButtonText: '确认作废',
        cancelButtonText: '取消',
        inputPlaceholder: '如：复检换代 QL260923012',
        inputValidator: (v: string) => (v && v.trim() ? true : '必须填写原因（留痕要求）'),
      }
    )
    reason = String(res?.value || '').trim()
  } catch {
    return
  }
  if (!reason) return
  try {
    await qualityNcrApi.voidSuperseded(row.ncrId, reason)
    ElMessage.success('已随批作废（已留痕）')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '随批作废失败')
  }
}

// ============ 报废审批（dev-20260924-005） ============
const handleScrapApprove = async (row: QualityNcrAction) => {
  try {
    await ElMessageBox.confirm(
      `确认通过这笔报废（${num(row.quantity)} 件）？通过后会计入台账已处置量、检验批已处置量与件级状态（报废无库存扣减）。`,
      '报废审批通过',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await qualityNcrApi.approveScrap(row.actionId)
    ElMessage.success('已审批通过')
    load()
    if (current.value) await openActions(current.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '审批失败')
  }
}

const handleScrapReject = async (row: QualityNcrAction) => {
  let reason = ''
  try {
    const res: any = await ElMessageBox.prompt('驳回必须填写原因（留痕）', '报废驳回', {
      inputPlaceholder: '驳回原因',
      inputValidator: (v: string) => (v && v.trim() ? true : '请填写驳回原因'),
    })
    reason = res?.value || ''
  } catch {
    return
  }
  try {
    await qualityNcrApi.rejectScrap(row.actionId, reason)
    ElMessage.success('已驳回')
    load()
    if (current.value) await openActions(current.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  }
}

const openRevoke = (action: QualityNcrAction) => {
  revokeTarget.value = action
  revokeReason.value = ''
  revokeVisible.value = true
}

const submitRevoke = async () => {
  const target = revokeTarget.value
  if (!target) return
  const reason = revokeReason.value.trim()
  if (!reason) return ElMessage.warning('请填写撤销原因（留痕要求）')
  try {
    await ElMessageBox.confirm(
      `确认撤销「${actionLabel(target.actionType)} ${num(target.quantity)} 件」？撤销后该批判定上界会恢复，可重新判定。`,
      '撤销确认',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  revoking.value = true
  try {
    await qualityNcrApi.revokeAction(target.actionId, reason)
    ElMessage.success('处置已撤销（已留痕）')
    revokeVisible.value = false
    if (current.value) await openActions(current.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '撤销失败')
  } finally {
    revoking.value = false
  }
}

const openSupplement = async (action: QualityNcrAction) => {  if (!current.value?.orderId) return
  supplementAction.value = action
  try {
    const res: any = await outboundApi.pickPreview(current.value.orderId)
    supplementItems.value = (res?.data || []).map((item: PickPreviewRow) => ({ ...item, quantity: 0 }))
    supplementVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载补料物料失败')
  }
}

const submitSupplement = async () => {
  if (!current.value?.orderId || !supplementAction.value) return
  const items = supplementItems.value
    .filter((item) => Number(item.quantity) > 0)
    .map((item) => ({
      materialId: item.materialId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      quantity: item.quantity,
    }))
  if (!items.length) return ElMessage.warning('请填写至少一项补料数量')
  supplementing.value = true
  try {
    await outboundApi.createReworkSupplement(current.value.orderId, current.value.ncrId, items)
    ElMessage.success('返工补料单已生成，等待仓库发料')
    supplementVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '生成返工补料单失败')
  } finally {
    supplementing.value = false
  }
}
const completeAction = async (row: QualityNcrAction) => {
  try {
    const res: any = await qualityNcrApi.completeAction(row.actionId)
    ElMessage.success(res?.data?.status === NcrActionStatus.DONE ? '返工复检已合格，处置完成' : '返工报工已完成，已生成 FQC 复检批')
    if (current.value) openActions(current.value)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(async () => {
  load(1)
  try {
    const res: any = await standardProcessApi.getEnabledProcesses()
    standardProcesses.value = res?.data || []
  } catch {
    standardProcesses.value = []
  }
})
</script>

<style scoped>
/* dev-20260923-031：返工进度单元格 */
.rework-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.rework-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.scope-guide {
  margin-bottom: 16px;
}
.scope-guide__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.sub {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.tip.block {
  padding-left: 110px;
  margin-left: 0;
  line-height: 1.5;
}
</style>
