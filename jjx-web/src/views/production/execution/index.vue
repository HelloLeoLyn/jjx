<template>
  <div class="execution-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">工序执行</h1>
      <div class="page-actions">
        <el-button
          icon="Document"
          v-hasPermi="['production:work-report:view']"
          @click="openMyReports"
          >我的报工</el-button
        >
        <el-button
          type="primary"
          icon="Stamp"
          v-hasPermi="['production:work-report:approve']"
          @click="openPendingApproval"
          >待我审批</el-button
        >
      </div>
    </div>

    <div class="scope-switch" aria-label="工序执行视图">
      <button
        type="button"
        :class="['scope-card', { active: viewMode === 'mine' }]"
        @click="switchView('mine')"
      >
        <strong>我的生产任务</strong><span>默认只显示与本人有效任务有关的工序</span>
      </button>
      <button
        type="button"
        :class="['scope-card', { active: viewMode === 'responsibility' }]"
        @click="switchView('responsibility')"
      >
        <strong>责任汇总</strong><span>我负责范围内含下级</span>
      </button>
      <button
        v-if="canViewAll"
        type="button"
        :class="['scope-card', { active: viewMode === 'all' }]"
        @click="switchView('all')"
      >
        <strong>全部工序</strong><span>查看全部工序（仅管理权限可用）</span>
      </button>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="queryParams.orderNo"
          placeholder="工单编号"
          clearable
          style="width: 150px"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
        <el-input
          v-model="queryParams.processName"
          placeholder="工序"
          clearable
          style="width: 120px"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
        <el-select
          v-model="queryParams.executionStatus"
          placeholder="状态"
          clearable
          style="width: 120px"
          @change="handleQuery"
        >
          <el-option
            v-for="s in STATUS_ITEMS"
            :key="s.value"
            :label="s.label"
            :value="String(s.value)"
          />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 我的生产任务：从本人有效 ProductionTask 起查，按 execution 聚合 -->
    <el-card v-if="viewMode === 'mine'" class="list-card" shadow="never">
      <el-table v-loading="loading" :data="myExecutionList" style="width: 100%">
        <el-table-column label="工单号 / 工序" min-width="180">
          <template #default="{ row }"
            ><strong>{{ row.orderNo || '-' }}</strong>
            <div>
              {{ row.processName || '-'
              }}<span v-if="row.processOrder"> · 序 {{ row.processOrder }}</span>
            </div>
            <div v-if="Number(row.taskCount || 0) === 1" class="text-muted">
              任务号：{{ row.taskNo || '-' }}
            </div>
            <div v-else-if="Number(row.taskCount || 0) > 1" class="text-muted">
              我的任务：{{ row.taskCount }}条
            </div></template
          >
        </el-table-column>
        <el-table-column label="设备" width="105"
          ><template #default="{ row }">{{
            row.equipmentName || '不限'
          }}</template></el-table-column
        >
        <el-table-column label="工序计划数量" width="144" align="right"
          ><template #default="{ row }">{{
            fmtQty(row.plannedQuantity)
          }}</template></el-table-column
        >
        <el-table-column label="我的责任" width="95" align="right"
          ><template #default="{ row }"
            ><strong>{{ fmtQty(row.myResponsibilityQuantity) }}</strong></template
          ></el-table-column
        >
        <el-table-column label="我的已完成" width="100" align="right"
          ><template #default="{ row }">{{
            fmtQty(row.myCompletedQuantity)
          }}</template></el-table-column
        >
        <el-table-column label="我的待审核" width="100" align="right"
          ><template #default="{ row }"
            ><span class="pending-value">{{ fmtQty(row.myPendingReviewQuantity) }}</span></template
          ></el-table-column
        >
        <el-table-column label="我的可处理" width="100" align="right"
          ><template #default="{ row }"
            ><span class="processable-value">{{
              fmtQty(row.myProcessableQuantity)
            }}</span></template
          ></el-table-column
        >
        <el-table-column label="下级已完成" width="100" align="right"
          ><template #default="{ row }"
            ><span class="completed-value">{{ fmtQty(row.childCompletedQuantity) }}</span></template
          ></el-table-column
        >
        <el-table-column label="下级处理中" width="100" align="right"
          ><template #default="{ row }"
            ><el-button
              v-if="Number(row.childProcessingQuantity || 0) > 0"
              class="child-link"
              type="primary"
              link
              @click="openChildProcessing(row)"
              >{{ fmtQty(row.childProcessingQuantity) }}</el-button
            ><span v-else class="child-value">0</span></template
          ></el-table-column
        >
        <el-table-column label="待我审批" width="90" align="right"
          ><template #default="{ row }"
            ><span class="approval-value">{{
              fmtQty(row.pendingMyApprovalQuantity)
            }}</span></template
          ></el-table-column
        >
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag size="small" :type="statusTag(row.executionStatus)">{{
              statusLabel(row.executionStatus)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" min-width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.PENDING.value && row.orderStatus === ProductionOrderStatusEnum.IN_PROGRESS.value"
              type="success"
              link
              icon="PlayCircle"
              v-hasPermi="['production:operation-execution:edit']"
              @click="handleStart(asExecution(row))"
              >开始生产</el-button
            >
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.PENDING.value && row.orderStatus !== ProductionOrderStatusEnum.IN_PROGRESS.value"
              type="info"
              link
              @click="goProductionOrder(row)"
              >请先启动工单</el-button
            >
            <el-button
              v-if="Number(row.myProcessableQuantity || 0) > 0 && row.executionStatus === ExecutionStatusEnum.EXECUTING.value"
              type="primary"
              link
              @click="openReportDialog(asExecution(row))"
              >报工</el-button
            >
            <el-button
              v-if="Number(row.myProcessableQuantity || 0) > 0"
              type="primary"
              link
              @click="goDispatchForRow(row)"
              >分配</el-button
            >
            <el-button
              v-if="Number(row.pendingMyApprovalQuantity || 0) > 0"
              type="warning"
              link
              @click="openPendingApproval"
              >去审批</el-button
            >
            <el-button type="info" link @click="handleView(asExecution(row))">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- 责任汇总：本人责任范围内包含整棵下级子树 -->
    <el-card v-if="viewMode === 'responsibility'" class="list-card" shadow="never">
      <el-table v-loading="loading" :data="myExecutionList" style="width: 100%">
        <el-table-column label="工单号 / 工序" min-width="180">
          <template #default="{ row }"
            ><strong>{{ row.orderNo || '-' }}</strong>
            <div>
              {{ row.processName || '-'
              }}<span v-if="row.processOrder"> · 序 {{ row.processOrder }}</span>
            </div></template
          >
        </el-table-column>
        <el-table-column label="工序计划数量" width="144" align="right"
          ><template #default="{ row }">{{
            fmtQty(row.plannedQuantity)
          }}</template></el-table-column
        >
        <el-table-column width="105" align="right">
          <template #header>
            <el-tooltip :content="RESPONSIBILITY_CONSERVATION" placement="top">
              <span class="summary-header"
                >我的责任 <el-icon><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <template #default="{ row }"
            ><strong>{{ fmtQty(row.myResponsibilityQuantity) }}</strong></template
          >
        </el-table-column>
        <el-table-column width="145" align="right">
          <template #header>
            <el-tooltip :content="RESPONSIBILITY_CONSERVATION" placement="top">
              <span class="summary-header"
                >已完成(含下级) <el-icon><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span class="completed-value">{{
              fmtQty(Number(row.myCompletedQuantity || 0) + Number(row.childCompletedQuantity || 0))
            }}</span>
          </template>
        </el-table-column>
        <el-table-column width="145" align="right">
          <template #header>
            <el-tooltip :content="RESPONSIBILITY_CONSERVATION" placement="top">
              <span class="summary-header"
                >待审批(含下级) <el-icon><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span class="pending-value">{{
              fmtQty(
                Number(row.myPendingReviewQuantity || 0) + Number(row.childPendingQuantity || 0)
              )
            }}</span>
          </template>
        </el-table-column>
        <el-table-column width="120" align="right">
          <template #header>
            <el-tooltip :content="RESPONSIBILITY_CONSERVATION" placement="top">
              <span class="summary-header"
                >我的可处理 <el-icon><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span class="processable-value">{{ fmtQty(row.myProcessableQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column width="130" align="right">
          <template #header>
            <el-tooltip :content="RESPONSIBILITY_CONSERVATION" placement="top">
              <span class="summary-header"
                >下级未完成 <el-icon><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <el-button
              v-if="
                Number(row.childProcessingQuantity || 0) - Number(row.childPendingQuantity || 0) > 0
              "
              class="child-link"
              type="primary"
              link
              @click="openChildProcessing(row)"
              >{{
                fmtQty(
                  Number(row.childProcessingQuantity || 0) - Number(row.childPendingQuantity || 0)
                )
              }}</el-button
            ><span v-else class="child-value">0</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.executionStatus)">{{
              statusLabel(row.executionStatus)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="
                row.executionStatus === ExecutionStatusEnum.PENDING.value &&
                row.orderStatus === ProductionOrderStatusEnum.IN_PROGRESS.value
              "
              type="success"
              link
              icon="PlayCircle"
              v-hasPermi="['production:operation-execution:edit']"
              @click="handleStart(asExecution(row))"
              >开始生产</el-button
            >
            <el-button
              v-if="
                row.executionStatus === ExecutionStatusEnum.PENDING.value &&
                row.orderStatus !== ProductionOrderStatusEnum.IN_PROGRESS.value
              "
              type="info"
              link
              @click="goProductionOrder(row)"
              >请先启动工单</el-button
            >
            <el-button
              v-if="
                Number(row.myProcessableQuantity || 0) > 0 &&
                row.executionStatus === ExecutionStatusEnum.EXECUTING.value
              "
              type="primary"
              link
              @click="openReportDialog(asExecution(row))"
              >报工</el-button
            >
            <el-button
              v-if="Number(row.myProcessableQuantity || 0) > 0"
              type="primary"
              link
              @click="goDispatchForRow(row)"
              >分配</el-button
            >
            <el-button
              v-if="Number(row.pendingMyApprovalQuantity || 0) > 0"
              type="warning"
              link
              @click="openPendingApproval"
              >去审批</el-button
            >
            <el-button type="info" link @click="handleView(asExecution(row))">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- 全部工序：保留原 OperationExecution 视角 -->
    <el-card v-if="viewMode === 'all'" class="list-card" shadow="never">
      <el-table v-loading="loading" :data="executionList" style="width: 100%">
        <el-table-column prop="orderNo" label="工单编号" width="180" show-overflow-tooltip />
        <el-table-column label="工序" min-width="130">
          <template #default="{ row }">
            <span>{{ row.processName || '-' }}</span>
            <div v-if="row.processOrder" style="font-size: 12px; color: #909399">
              序 {{ row.processOrder }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.equipmentName">{{ row.equipmentName }}</span>
            <span v-else style="color: #c0c4cc">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="计划数量" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.inputQuantity) }}</template>
        </el-table-column>
        <!-- 累计投影（WorkReport projection，后端提供） -->
        <el-table-column label="累计合格（已审批）" width="135" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="累计不良（已审批）" width="135" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="累计产出（已审批）" width="135" align="right">
          <template #default="{ row }">{{ fmtQty(row.outputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待审批" width="90" align="right">
          <template #default="{ row }">
            <span class="approval-value">{{ fmtQty(row.pendingApprovalQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.executionStatus)">{{
              statusLabel(row.executionStatus)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.PENDING.value"
              type="success"
              link
              icon="PlayCircle"
              v-hasPermi="['production:operation-execution:edit']"
              @click="handleStart(row)"
              >开始</el-button
            >
            <el-button
              v-if="canReportInAllView(row)"
              type="primary"
              link
              icon="EditPen"
              v-hasPermi="['production:work-report:add']"
              @click="openReportDialog(row)"
              >报工</el-button
            >
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.EXECUTING.value && !canReportInAllView(row)"
              type="info"
              link
              @click="switchView('mine')"
              >请到我的任务报工</el-button
            >
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.EXECUTING.value"
              type="warning"
              link
              icon="Pause"
              v-hasPermi="['production:operation-execution:edit']"
              @click="handlePause(row)"
              >暂停</el-button
            >
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.EXECUTING.value"
              type="primary"
              link
              icon="View"
              @click="handleView(row)"
              >详情</el-button
            >
            <el-button
              v-if="[ExecutionStatusEnum.EXECUTING.value, ExecutionStatusEnum.PAUSED.value].includes(row.executionStatus)"
              type="success"
              link
              icon="Check"
              v-hasPermi="['production:operation-execution:edit']"
              @click="handleComplete(row)"
              >完成</el-button
            >
            <el-button
              v-if="row.executionStatus === ExecutionStatusEnum.EXECUTING.value"
              type="warning"
              link
              icon="WarningFilled"
              v-hasPermi="['production:quality:view']"
              @click="handleQualityCheck(row)"
              >首检/巡检</el-button
            >
            <el-button
              v-if="row.executionId"
              type="info"
              link
              icon="List"
              v-hasPermi="['production:quality:view']"
              @click="goQualityRecords(row)"
              >质检记录</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <el-drawer v-model="childProcessingOpen" title="下级处理明细" size="760px" append-to-body>
      <div v-loading="childProcessingLoading">
        <el-descriptions v-if="childProcessingDetail" :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ childProcessingDetail.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ childProcessingDetail.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序状态">{{ statusLabel(childProcessingDetail.executionStatus) }}</el-descriptions-item>
          <el-descriptions-item label="我的责任">{{ fmtQty(childProcessingDetail.myResponsibilityQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="下级已完成">{{ fmtQty(childProcessingDetail.childCompletedQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="下级处理中">{{ fmtQty(childProcessingDetail.childProcessingQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="待我审批">{{ fmtQty(childProcessingDetail.pendingMyApprovalQuantity) }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="childProcessingDetail" :data="childProcessingDetail.records" size="small" style="margin-top: 16px">
          <el-table-column label="任务号" prop="taskNo" min-width="245" show-overflow-tooltip />
          <el-table-column label="下属执行人" prop="assigneeName" min-width="110" />
          <el-table-column label="部门" prop="departmentName" min-width="110" />
          <el-table-column label="任务数量" width="90" align="right"><template #default="{ row }">{{ fmtQty(row.taskQuantity) }}</template></el-table-column>
          <el-table-column label="已完成" width="80" align="right"><template #default="{ row }">{{ fmtQty(row.completedQuantity) }}</template></el-table-column>
          <el-table-column label="待审批" width="80" align="right"><template #default="{ row }"><span class="approval-value">{{ fmtQty(row.pendingApprovalQuantity) }}</span></template></el-table-column>
          <el-table-column label="处理中" width="80" align="right"><template #default="{ row }"><span class="child-value">{{ fmtQty(row.processingQuantity) }}</span></template></el-table-column>
          <el-table-column label="Task状态" width="90"><template #default="{ row }"><el-tag size="small">{{ row.statusLabel || row.status }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="145" fixed="right">
            <template #default="{ row }">
              <el-button v-if="Number(row.pendingApprovalQuantity || 0) > 0" type="warning" link @click="openApprovalFromChild">去审批</el-button>
              <el-button type="primary" link @click="viewReportsFromChild">查看报工</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!childProcessingLoading && childProcessingDetail && !childProcessingDetail.records.length" description="暂无直接下级处理任务" />
      </div>
    </el-drawer>

    <!-- ============ 详情 Drawer（Tabs） ============ -->
    <el-drawer v-model="detailOpen" size="760px" append-to-body>
      <template #header>
        <div class="detail-header">
          <div>
            <div class="detail-title">
              {{ detailForm.orderNo || '-' }} · {{ detailForm.processName || '未命名工序' }}
            </div>
            <div class="detail-subtitle">
              序 {{ detailForm.processOrder || '-' }} ·
              {{ fmtTime(detailForm.plannedStartTime) }} 至 {{ fmtTime(detailForm.plannedEndTime) }}
            </div>
          </div>
          <el-tag :type="statusTag(detailForm.executionStatus)">{{
            statusLabel(detailForm.executionStatus)
          }}</el-tag>
        </div>
      </template>

      <div class="metric-grid">
        <div class="metric-card">
          <span>计划数量</span><strong>{{ fmtQty(detailForm.inputQuantity) }}</strong>
        </div>
        <div class="metric-card">
          <span>已分配</span><strong>{{ fmtQty(detailRootTask?.assignedQuantity) }}</strong>
        </div>
        <div class="metric-card warning">
          <span>待审批</span
          ><strong>{{ fmtQty(detailRootTask?.pendingQuantity ?? detailForm.pendingApprovalQuantity) }}</strong>
        </div>
        <div class="metric-card success">
          <span>已完成（仅已审批报工）</span
          ><strong>{{
            fmtQty(detailRootTask?.completedQuantity ?? detailForm.outputQuantity)
          }}</strong>
        </div>
        <div class="metric-card primary">
          <span>剩余可分配</span><strong>{{ fmtQty(detailRootTask?.remainingQuantity) }}</strong>
        </div>
      </div>

      <el-card class="responsibility-card" shadow="never">
        <template #header>
          <div class="section-head">
            <span>责任摘要</span>
            <el-button type="primary" link @click="goDispatchManagement">进入派工管理</el-button>
          </div>
        </template>
        <div v-if="detailRootTask" class="root-summary">
          <div>
            主任务 #{{ detailRootTask.taskId }} ·
            {{ detailRootTask.statusLabel || detailRootTask.status }}
          </div>
          <div class="text-muted">
            直接责任 {{ detailChildren.length }} 人，未继续分配
            {{ fmtQty(detailRootTask.remainingQuantity) }}
          </div>
          <div v-if="detailChildren.length" class="responsibility-list">
            <el-tag v-for="child in detailChildren" :key="child.taskId" effect="plain">
              {{ child.assigneeName || '未分配' }} · {{ fmtQty(child.taskQuantity) }}
            </el-tag>
          </div>
        </div>
        <el-empty
          v-else-if="!contextLoading"
          description="暂无责任任务或无查看权限"
          :image-size="42"
        />
      </el-card>

      <el-tabs v-model="detailTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="base">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单">{{ detailForm.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="工序"
              >{{ detailForm.processName }}（序
              {{ detailForm.processOrder }}）</el-descriptions-item
            >
            <el-descriptions-item label="状态">{{
              statusLabel(detailForm.executionStatus)
            }}</el-descriptions-item>
            <el-descriptions-item label="设备">{{
              detailForm.equipmentName || '不限'
            }}</el-descriptions-item>
            <el-descriptions-item label="操作员（旧）">{{
              detailForm.operatorName || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{
              fmtQty(detailForm.inputQuantity)
            }}</el-descriptions-item>
            <el-descriptions-item label="累计合格">{{
              fmtQty(detailForm.qualifiedQuantity)
            }}</el-descriptions-item>
            <el-descriptions-item label="累计不良">{{
              fmtQty(detailForm.defectiveQuantity)
            }}</el-descriptions-item>
            <el-descriptions-item label="累计产出">{{
              fmtQty(detailForm.outputQuantity)
            }}</el-descriptions-item>
            <el-descriptions-item label="人工工时"
              >{{ fmtQty(detailForm.actualLaborHours) }}h</el-descriptions-item
            >
            <el-descriptions-item label="机器工时"
              >{{ fmtQty(detailForm.actualMachineHours) }}h</el-descriptions-item
            >
            <el-descriptions-item label="开始时间" :span="2">{{
              fmtTime(detailForm.actualStartTime)
            }}</el-descriptions-item>
            <el-descriptions-item label="完成时间" :span="2">{{
              fmtTime(detailForm.actualEndTime)
            }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-print-actions">
            <el-button type="primary" icon="Printer" @click="openExecutionPrint('daily-report')">打印生产日报</el-button>
            <el-button type="warning" icon="Printer" @click="openExecutionPrint('first-piece')">打印首件检查表</el-button>
          </div>
          <div style="color: #909399; font-size: 12px; margin-top: 8px">
            数量/工时由报工记录自动汇总，不可直接编辑。
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`我的任务 (${detailMyTasks.length})`" name="mine">
          <el-table :data="detailMyTasks" size="small">
            <el-table-column label="责任来源" min-width="130">
              <template #default="{ row }">{{ row.parentAssigneeName || '主任务' }}</template>
            </el-table-column>
            <el-table-column label="任务数量" width="90" align="right"
              ><template #default="{ row }">{{
                fmtQty(row.taskQuantity)
              }}</template></el-table-column
            >
            <el-table-column label="已完成" width="90" align="right"
              ><template #default="{ row }">{{
                fmtQty(row.completedQuantity)
              }}</template></el-table-column
            >
            <el-table-column label="待审批" width="90" align="right"
              ><template #default="{ row }">{{
                fmtQty(row.pendingQuantity)
              }}</template></el-table-column
            >
            <el-table-column label="可报数量" width="90" align="right"
              ><template #default="{ row }">{{
                fmtQty(row.remainingQuantity)
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="80">
              <template #default="{ row }"
                ><el-button type="primary" link @click="openReportDialog(detailForm, row.taskId)"
                  >报工</el-button
                ></template
              >
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!detailMyTasks.length"
            description="当前工序没有本人可报工任务"
            :image-size="50"
          />
        </el-tab-pane>

        <!-- 报工记录 -->
        <el-tab-pane label="报工记录" name="reports">
          <el-table v-loading="reportsLoading" :data="reportList" size="small">
            <el-table-column label="报工单号" prop="reportNo" min-width="160" />
            <el-table-column label="报工时间" width="120">
              <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
            </el-table-column>
            <el-table-column label="报工人" prop="reporterName" width="90" />
            <el-table-column label="合格" width="70" align="right">
              <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
            </el-table-column>
            <el-table-column label="不良" width="70" align="right">
              <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag
                  v-if="row.reportStatus === 'CANCELLED'"
                  size="small"
                  type="danger"
                  effect="plain"
                  >已撤销</el-tag
                >
                <el-tag
                  v-else-if="row.reportStatus === 'REJECTED'"
                  size="small"
                  type="warning"
                  effect="plain"
                  >已驳回</el-tag
                >
                <el-tag
                  v-else-if="row.reportStatus === 'APPROVED'"
                  size="small"
                  type="success"
                  effect="plain"
                  >已通过</el-tag
                >
                <el-tag v-else size="small" type="info" effect="plain">待审批</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openReportDetail(row)"
                  >详情</el-button
                >
                <el-button link size="small" type="primary" @click="printWorkReport(row)"
                  >打印</el-button
                >
                <el-button
                  v-if="canCancelReport(row)"
                  link
                  size="small"
                  type="danger"
                  @click="openCancelReport(row)"
                  >撤销</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!reportList.length" description="暂无报工记录" :image-size="50" />
        </el-tab-pane>

        <el-tab-pane :label="`质检记录 (${qualityList.length})`" name="quality">
          <el-table :data="qualityList" size="small">
            <el-table-column label="类型" prop="inspectionTypeName" min-width="90" />
            <el-table-column label="检验数量" prop="totalQty" width="90" align="right" />
            <el-table-column label="合格" prop="passQty" width="70" align="right" />
            <el-table-column label="不良" prop="failQty" width="70" align="right" />
            <el-table-column label="结果" prop="resultName" width="80" />
            <el-table-column label="检验人" prop="inspector" min-width="90" />
          </el-table>
          <el-empty v-if="!qualityList.length" description="暂无质检记录" :image-size="50" />
        </el-tab-pane>

        <!-- 操作记录（现有 Timeline 能力，P2-D 不接线） -->
        <el-tab-pane label="操作记录" name="ops">
          <el-empty description="操作记录（P4 Trace 统一接线）" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 报工详情弹窗 -->
    <el-dialog v-model="reportDetailVisible" title="报工详情" width="460px" append-to-body>
      <el-descriptions v-if="reportDetail" :column="1" border size="small">
        <el-descriptions-item label="报工单号">{{ reportDetail.reportNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报工人">{{ reportDetail.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="合格数量">{{
          fmtQty(reportDetail.qualifiedQuantity)
        }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{
          fmtQty(reportDetail.defectiveQuantity)
        }}</el-descriptions-item>
        <el-descriptions-item label="不良原因">{{
          reportDetail.defectReason || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="人工工时"
          >{{ fmtQty(reportDetail.laborHours) }}h</el-descriptions-item
        >
        <el-descriptions-item label="机器工时"
          >{{ fmtQty(reportDetail.machineHours) }}h</el-descriptions-item
        >
        <el-descriptions-item label="设备">{{
          reportDetail.equipmentName || '不限'
        }}</el-descriptions-item>
        <el-descriptions-item label="生产区间"
          >{{ fmtTime(reportDetail.workStartTime) }} ~
          {{ fmtTime(reportDetail.workEndTime) }}</el-descriptions-item
        >
        <el-descriptions-item label="报工时间">{{
          fmtTime(reportDetail.reportTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ reportDetail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          reportDetail.reportStatusLabel
        }}</el-descriptions-item>
        <template
          v-if="
            reportDetail.reportStatus === 'APPROVED' || reportDetail.reportStatus === 'REJECTED'
          "
        >
          <el-descriptions-item label="审批人">{{
            reportDetail.reviewerName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{
            fmtTime(reportDetail.reviewTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="审批备注">{{
            reportDetail.reviewRemark || '-'
          }}</el-descriptions-item>
        </template>
        <template v-if="reportDetail.reportStatus === 'CANCELLED'">
          <el-descriptions-item label="撤销人">{{
            reportDetail.cancelledByName
          }}</el-descriptions-item>
          <el-descriptions-item label="撤销时间">{{
            fmtTime(reportDetail.cancelledAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="撤销原因">{{
            reportDetail.cancelReason
          }}</el-descriptions-item>
        </template>
      </el-descriptions>
      <div style="color: #909399; font-size: 12px; margin-top: 8px">
        报工为不可覆盖的生产事实，不允许编辑。
      </div>
    </el-dialog>

    <!-- 撤销确认弹窗 -->
    <el-dialog v-model="cancelVisible" title="撤销报工" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span
            >{{ cancelTarget?.reporterName }}：合格 {{ fmtQty(cancelTarget?.qualifiedQuantity) }} /
            不良 {{ fmtQty(cancelTarget?.defectiveQuantity) }}</span
          >
        </el-form-item>
        <el-form-item label="撤销原因" required>
          <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
        <div style="color: #f56c6c; font-size: 12px">
          撤销后该报工不计入累计，但历史保留（显示已撤销）。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" :loading="cancelling" @click="handleCancelReport"
          >确认撤销</el-button
        >
      </template>
    </el-dialog>

    <!-- 质检（保留现状） -->
    <el-dialog v-model="qcVisible" title="质量检查" width="480px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="检查类型">
          <el-radio-group v-model="qcForm.checkType">
            <el-radio value="first_piece">首检</el-radio>
            <el-radio value="patrol">巡检</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽检数量">
          <el-input-number v-model="qcForm.checkQty" :min="1" />
        </el-form-item>
        <el-form-item label="合格数量">
          <el-input-number v-model="qcForm.passQty" :min="0" :max="qcForm.checkQty" />
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="qcForm.result">
            <el-radio value="pass">✅ 合格</el-radio>
            <el-radio value="fail">❌ 不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="不良描述">
          <el-input
            v-model="qcForm.defectDesc"
            type="textarea"
            :rows="2"
            placeholder="不合格时描述"
            maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQc">提交</el-button>
      </template>
    </el-dialog>

    <!-- 报工提交 Dialog（P6：仅当前 Task 执行人可报；数量 <= 任务剩余） -->
    <el-dialog v-model="reportOpen" title="报工" width="560px" append-to-body>
      <template v-if="reportExec">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单">{{ reportExec.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{
            reportExec.processName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="累计合格">{{
            fmtQty(reportExec.qualifiedQuantity)
          }}</el-descriptions-item>
          <el-descriptions-item label="累计不良">{{
            fmtQty(reportExec.defectiveQuantity)
          }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="96px" style="margin-top: 12px">
          <el-form-item label="我的任务" required>
            <el-select
              v-model="reportTaskId"
              placeholder="选择本次报工对应的任务"
              style="width: 100%"
              :loading="reportTaskLoading"
            >
              <el-option
                v-for="t in reportTasks"
                :key="t.taskId"
                :value="t.taskId"
                :label="`${t.taskNo || '-'} · 责任 ${fmtQty(t.taskQuantity)} · 剩余 ${fmtQty(t.remainingQuantity)}`"
              />
            </el-select>
            <div class="text-muted tip">
              报工必须选择由本人执行且正在进行的任务；报工数量不能超过任务剩余数量。
            </div>
          </el-form-item>
          <el-form-item label="合格数量" required>
            <el-input-number
              v-model="reportForm.qualifiedQuantity"
              :min="0"
              :precision="2"
              :step="1"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="不良数量">
            <el-input-number
              v-model="reportForm.defectiveQuantity"
              :min="0"
              :precision="2"
              :step="1"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-if="Number(reportForm.defectiveQuantity) > 0" label="不良原因" required>
            <el-input
              v-model="reportForm.defectReason"
              type="textarea"
              :rows="2"
              placeholder="不良数量大于 0 时必填"
            />
          </el-form-item>
          <el-form-item label="人工工时">
            <el-input-number
              v-model="reportForm.laborHours"
              :min="0"
              :precision="2"
              :step="0.5"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="机器工时">
            <el-input-number
              v-model="reportForm.machineHours"
              :min="0"
              :precision="2"
              :step="0.5"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="生产区间">
            <el-date-picker
              v-model="reportTimeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="reportForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="reportOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reportLoading"
          :disabled="!canSubmitReport"
          @click="handleReportSubmit"
          >提交报工</el-button
        >
      </template>
    </el-dialog>

    <!-- 我的报工 Drawer -->
    <el-drawer v-model="myReportsOpen" title="我的报工" size="720px" append-to-body>
      <el-table v-loading="myReportsLoading" :data="myReports" size="small">
        <el-table-column label="报工单号" prop="reportNo" min-width="160" />
        <el-table-column label="工单/工序" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderNo || '-' }} · {{ row.executionId }}</template>
        </el-table-column>
        <el-table-column label="合格" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="报工时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.reportStatusLabel || row.reportStatus }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-if="row.reportStatus === 'PENDING'"
              link
              size="small"
              type="danger"
              @click="openCancelReport(row)"
              >撤销</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="myReportsPage"
          v-model:page-size="myReportsPageSize"
          :total="myReportsTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadMyReports"
          @current-change="loadMyReports"
        />
      </div>
    </el-drawer>

    <!-- 待我审批 Drawer -->
    <el-drawer v-model="pendingOpen" title="待我审批" size="760px" append-to-body>
      <el-table v-loading="pendingLoading" :data="pendingList" size="small">
        <el-table-column label="报工单号" prop="reportNo" min-width="160" />
        <el-table-column label="工单/工序" min-width="130" show-overflow-tooltip>
          <template #default="{ row }"
            >{{ row.orderNo || '-' }} · {{ row.processName || row.executionId }}</template
          >
        </el-table-column>
        <el-table-column label="报工人" prop="reporterName" width="90" />
        <el-table-column label="合格" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="报工时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link size="small" type="success" @click="openApprove(row)">通过</el-button>
            <el-button link size="small" type="danger" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pendingPage"
          v-model:page-size="pendingPageSize"
          :total="pendingTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadPendingApproval"
          @current-change="loadPendingApproval"
        />
      </div>
    </el-drawer>

    <!-- 审批通过（备注可选） -->
    <el-dialog v-model="approveVisible" title="审批通过" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span
            >{{ approveTarget?.reporterName }}：合格
            {{ fmtQty(approveTarget?.qualifiedQuantity) }} / 不良
            {{ fmtQty(approveTarget?.defectiveQuantity) }}</span
          >
        </el-form-item>
        <el-form-item label="审批备注">
          <el-input v-model="approveRemark" type="textarea" :rows="2" placeholder="可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="approveLoading" @click="handleApprove"
          >确认通过</el-button
        >
      </template>
    </el-dialog>

    <!-- 审批驳回（原因必填） -->
    <el-dialog v-model="rejectVisible" title="审批驳回" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span
            >{{ rejectTarget?.reporterName }}：合格 {{ fmtQty(rejectTarget?.qualifiedQuantity) }} /
            不良 {{ fmtQty(rejectTarget?.defectiveQuantity) }}</span
          >
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="rejectLoading"
          :disabled="!rejectReason.trim()"
          @click="handleReject"
          >确认驳回</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { operationExecutionApi } from '@/api/production/operationExecution'
import {
  cancelWorkReport,
  submitWorkReport,
  approveWorkReport,
  rejectWorkReport,
  getMyWorkReports,
  getPendingApprovalWorkReports,
  getWorkReportsByExecution,
  type WorkReportVO,
} from '@/api/production/workReport'
import {
  getExecutionRootTask,
  getMyTasks,
  getTaskChildren,
  getMyProductionExecutions,
  getProductionExecutionScope,
  getMyChildProcessingDetail,
} from '@/api/production/task'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import type {
  TaskTreeRow,
  MyProductionExecution,
  ChildProcessingDetail,
} from '@/types/production/task'
import type {
  OperationExecutionVO,
  OperationExecutionQuery,
} from '@/types/production/operationExecution'
import { ExecutionStatusEnum, ProductionOrderStatusEnum } from '@/enums/production'

defineOptions({ name: 'ProductionExecutionList' })

const router = useRouter()

const STATUS_ITEMS = ExecutionStatusEnum.items
const RESPONSIBILITY_CONSERVATION = '责任 = 已完成 + 待审批 + 可处理 + 下级未完成'

function statusLabel(s?: number): string {
  return s === undefined ? '未知' : ExecutionStatusEnum.getLabel(s)
}
function statusTag(s?: number) {
  return s === undefined ? 'info' : ExecutionStatusEnum.getTagProps(s).type
}
function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '0'
  return String(Number(v))
}
function fmtTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

const loading = ref(false)
const executionList = ref<OperationExecutionVO[]>([])
const myTaskExecutionIds = ref<Set<number>>(new Set())
const myExecutionList = ref<MyProductionExecution[]>([])
const viewMode = ref<'mine' | 'responsibility' | 'all'>('mine')
const canViewAll = ref(false)
const childProcessingOpen = ref(false)
const childProcessingLoading = ref(false)
const childProcessingDetail = ref<ChildProcessingDetail | null>(null)
const childProcessingExecution = ref<MyProductionExecution | null>(null)
const total = ref(0)
const queryParams = reactive<OperationExecutionQuery>({
  orderNo: '',
  processName: '',
  executionStatus: '',
  operatorName: '',
  pageNum: 1,
  pageSize: 10,
})

const getList = async () => {
  loading.value = true
  try {
    const res: any =
      viewMode.value !== 'all'
        ? await getMyProductionExecutions(queryParams)
        : await operationExecutionApi.globalList(queryParams)
    const data = res?.data
    if (viewMode.value !== 'all') myExecutionList.value = data?.records || []
    else {
      executionList.value = Array.isArray(data) ? data : data?.records || []
      try {
        const myTasksResult: any = await getMyTasks()
        myTaskExecutionIds.value = new Set(
          (myTasksResult?.data || [])
            .filter((task: TaskTreeRow) => Number(task.remainingQuantity || 0) > 0)
            .map((task: TaskTreeRow) => task.executionId)
        )
      } catch {
        myTaskExecutionIds.value = new Set()
      }
    }
    total.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch {
    executionList.value = []
  } finally {
    loading.value = false
  }
}
const switchView = (mode: 'mine' | 'responsibility' | 'all') => {
  if (mode === 'all' && !canViewAll.value) return
  viewMode.value = mode
  queryParams.pageNum = 1
  getList()
}
const canReportInAllView = (row: OperationExecutionVO) =>
  row.executionStatus === ExecutionStatusEnum.EXECUTING.value &&
  !!row.executionId &&
  myTaskExecutionIds.value.has(row.executionId)
const asExecution = (row: MyProductionExecution): OperationExecutionVO => ({
  executionId: row.executionId,
  orderId: row.orderId,
  orderNo: row.orderNo,
  processId: row.processId,
  processName: row.processName,
  processOrder: row.processOrder,
  executionStatus: row.executionStatus,
  actualStartTime: row.actualStartTime,
  equipmentId: row.equipmentId,
  equipmentCode: row.equipmentCode,
  equipmentName: row.equipmentName,
  inputQuantity: row.plannedQuantity,
})
const goDispatchForRow = (row: MyProductionExecution) => {
  router.push({
    path: '/production/dispatch',
    query: { executionId: String(row.executionId), keyword: row.orderNo || '' },
  })
}
const goProductionOrder = (row: MyProductionExecution) => {
  router.push({ path: '/production/order', query: { orderNo: row.orderNo || '' } })
}
const openChildProcessing = async (row: MyProductionExecution) => {
  if (Number(row.childProcessingQuantity || 0) <= 0) return
  childProcessingExecution.value = row
  childProcessingDetail.value = null
  childProcessingOpen.value = true
  childProcessingLoading.value = true
  try {
    const res: any = await getMyChildProcessingDetail(row.executionId)
    childProcessingDetail.value = res?.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || '下级处理明细加载失败')
  } finally {
    childProcessingLoading.value = false
  }
}
const openApprovalFromChild = () => {
  childProcessingOpen.value = false
  openPendingApproval()
}
const viewReportsFromChild = async () => {
  if (!childProcessingExecution.value) return
  childProcessingOpen.value = false
  await handleView(asExecution(childProcessingExecution.value))
  detailTab.value = 'reports'
}
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}
const handleReset = () => {
  Object.assign(queryParams, { orderNo: '', processName: '', executionStatus: '', pageNum: 1 })
  getList()
}

// ============ 开始/暂停/恢复/完成 ============
const handleStart = async (row: OperationExecutionVO) => {
  // 扫码C：可选扫设备码（不一致后端软校验放行并记录），跳过=不校验
  let deviceCode: string | undefined
  try {
    const { value } = await ElMessageBox.prompt(
      row.equipmentName ? `指定设备：${row.equipmentName}（${row.equipmentCode || '-'}）` : '该工序未指定设备',
      '开始工序（可选扫设备码）',
      {
        confirmButtonText: '开始',
        cancelButtonText: '跳过',
        inputPlaceholder: '扫码枪扫设备码，或直接点开始',
        closeOnClickModal: false,
      },
    )
    deviceCode = value?.trim() || undefined
  } catch {
    return // 用户取消
  }
  try {
    const result = await operationExecutionApi.start(row.executionId!, deviceCode)
    if (result.data !== true) throw new Error(result.msg || '开始工序失败')
    ElMessage.success('已开始')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
const handlePause = async (row: OperationExecutionVO) => {
  try {
    const result = await operationExecutionApi.pause(row.executionId!)
    if (result.data !== true) throw new Error(result.msg || '暂停工序失败')
    ElMessage.success('已暂停')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 完成提示：0 报工 / 低于计划 / 超计划 warning（后端 gate 为准）
const handleComplete = async (row: OperationExecutionVO) => {
  await loadExecutionContext(row)
  const qualified = Number(row.qualifiedQuantity || 0)
  const defective = Number(row.defectiveQuantity || 0)
  const planned = Number(row.inputQuantity || 0)
  const blockers: string[] = []
  if (qualified === 0 && defective === 0) blockers.push('尚无有效报工记录')
  if (Number(detailRootTask.value?.pendingQuantity || 0) > 0)
    blockers.push(`还有 ${fmtQty(detailRootTask.value?.pendingQuantity)} 件报工待审批`)
  if (Number(detailRootTask.value?.remainingQuantity || 0) > 0)
    blockers.push(`还有 ${fmtQty(detailRootTask.value?.remainingQuantity)} 件任务未分配或未完成`)
  if (Number(detailRootTask.value?.assignedQuantity || 0) > 0)
    blockers.push(`已派出的任务还有 ${fmtQty(detailRootTask.value?.assignedQuantity)} 件未完成`)
  if (planned > 0 && qualified < planned)
    blockers.push(`合格数量 ${qualified}，未达到计划数量 ${planned}`)
  if (blockers.length && detailRootTask.value?.allowedActions?.includes('COMPLETE'))
    blockers.push(
      `父级任务需负责人在电脑端任务管理中确认${
        detailRootTask.value?.assigneeName ? `（负责人：${detailRootTask.value.assigneeName}）` : ''
      }`
    )
  if (blockers.length) {
    await ElMessageBox.alert(blockers.map((v) => `✗ ${v}`).join('\n'), '暂不能完成该工序', {
      type: 'warning',
    })
    return
  }
  try {
    await ElMessageBox.confirm(
      `该工序已满足前端完成条件。\n计划 ${planned}，合格 ${qualified}，不良 ${defective}。`,
      '完成工序',
      { type: 'info', confirmButtonText: '确认完成' }
    )
  } catch {
    return
  }
  try {
    const result = await operationExecutionApi.complete(row.executionId!)
    if (result.data !== true) {
      throw new Error(result.msg || '工序完成失败')
    }
    ElMessage.success('工序已完成；若为最后工序将自动生成完工检验，等待质检')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============ 详情 Drawer ============
const detailOpen = ref(false)
const detailTab = ref('base')
const detailForm = reactive<Record<string, any>>({})
const contextLoading = ref(false)
const detailRootTask = ref<TaskTreeRow | null>(null)
const detailChildren = ref<TaskTreeRow[]>([])
const detailMyTasks = ref<TaskTreeRow[]>([])
const qualityList = ref<QualityVO[]>([])

const loadExecutionContext = async (row: OperationExecutionVO) => {
  if (!row.executionId) return
  contextLoading.value = true
  Object.assign(detailForm, row)
  detailRootTask.value = null
  detailChildren.value = []
  detailMyTasks.value = []
  qualityList.value = []
  const [rootResult, mineResult, reportResult, qualityResult] = await Promise.allSettled([
    getExecutionRootTask(row.executionId),
    getMyTasks(row.executionId),
    getWorkReportsByExecution(row.executionId),
    qualityApi.page({ pageNum: 1, pageSize: 100, executionId: row.executionId }),
  ])
  if (rootResult.status === 'fulfilled') {
    detailRootTask.value = (rootResult.value as any)?.data || null
    if (detailRootTask.value?.taskId) {
      try {
        const childRes: any = await getTaskChildren(detailRootTask.value.taskId)
        detailChildren.value = childRes?.data || []
      } catch {
        detailChildren.value = []
      }
    }
  }
  if (mineResult.status === 'fulfilled') detailMyTasks.value = (mineResult.value as any)?.data || []
  if (reportResult.status === 'fulfilled')
    reportList.value = (reportResult.value as any)?.data || []
  if (qualityResult.status === 'fulfilled') {
    const data: any = (qualityResult.value as any)?.data
    qualityList.value = Array.isArray(data) ? data : data?.records || []
  }
  contextLoading.value = false
}

const handleView = async (row: OperationExecutionVO) => {
  detailTab.value = 'base'
  detailOpen.value = true
  await loadExecutionContext(row)
}

const goDispatchManagement = () => {
  router.push({
    path: '/production/dispatch',
    query: { executionId: String(detailForm.executionId || ''), keyword: detailForm.orderNo || '' },
  })
}

const openExecutionPrint = (page: 'daily-report' | 'first-piece') => {
  if (!detailForm.executionId) return
  window.open(router.resolve({ path: `/production/quality-print/${page}`, query: { executionId: detailForm.executionId } }).href, '_blank')
}

// ============ 报工历史 ============
const reportsLoading = ref(false)
const reportList = ref<WorkReportVO[]>([])

const loadReports = async () => {
  if (!detailForm.executionId) return
  reportsLoading.value = true
  try {
    const res: any = await getWorkReportsByExecution(detailForm.executionId)
    reportList.value = res?.data || []
  } catch {
    reportList.value = []
  } finally {
    reportsLoading.value = false
  }
}

const canCancelReport = (row: WorkReportVO) => {
  // P3：仅 PENDING 可撤销；APPROVED 为有效完成事实禁止普通撤销（更正走 P4 冲销）
  return row.reportStatus === 'PENDING'
}

// 报工详情
const reportDetailVisible = ref(false)
const reportDetail = ref<WorkReportVO | null>(null)
const openReportDetail = (row: WorkReportVO) => {
  reportDetail.value = row
  reportDetailVisible.value = true
}

// 打印工票（DEV-1247：报工单打印页）
const printWorkReport = (row: WorkReportVO) => {
  if (!row.reportId) {
    ElMessage.error('报工单ID缺失，无法打印')
    return
  }
  const { href } = router.resolve(`/production/report/print/${row.reportId}`)
  window.open(href, '_blank')
}

// 撤销
const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelTarget = ref<WorkReportVO | null>(null)
const cancelReason = ref('')

const openCancelReport = (row: WorkReportVO) => {
  cancelTarget.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

const handleCancelReport = async () => {
  if (!cancelReason.value.trim()) {
    ElMessage.warning('撤销原因必填')
    return
  }
  cancelling.value = true
  try {
    await cancelWorkReport(cancelTarget.value!.reportId, {
      cancelReason: cancelReason.value.trim(),
    })
    ElMessage.success('已撤销')
    cancelVisible.value = false
    loadReports()
    getList()
    if (myReportsOpen.value) loadMyReports()
    if (pendingOpen.value) loadPendingApproval()
  } catch (e: any) {
    ElMessage.error(e?.message || '撤销失败')
  } finally {
    cancelling.value = false
  }
}

// P3-D：跳转质检管理页并按当前工序过滤（FQC/IPQC 记录）
const goQualityRecords = (row: OperationExecutionVO) => {
  const query: Record<string, string> = {}
  if (row.executionId) query.executionId = String(row.executionId)
  if (row.orderId) query.orderId = String(row.orderId)
  router.push({ path: '/production/quality', query })
}

// ============ 质检（保留现状） ============
const qcVisible = ref(false)
const qcForm = reactive({
  checkType: 'first_piece',
  checkQty: 5,
  passQty: 5,
  result: 'pass',
  defectDesc: '',
  inspector: '',
})
let qcCurrentRow: any = null

const handleQualityCheck = (row: any) => {
  qcCurrentRow = row
  Object.assign(qcForm, {
    checkType: 'first_piece',
    checkQty: 5,
    passQty: 5,
    result: 'pass',
    defectDesc: '',
  })
  qcVisible.value = true
}

const submitQc = async () => {
  if (qcForm.passQty > qcForm.checkQty) {
    ElMessage.warning('合格数量不能大于抽检数量')
    return
  }
  if (!qcCurrentRow?.executionId) return
  try {
    const checkResult = qcForm.result === 'pass' ? 'PASS' : 'FAIL'
    const checkItems = `抽检${qcForm.checkQty}件/合格${qcForm.passQty}件${qcForm.defectDesc ? '/' + qcForm.defectDesc : ''}`
    await operationExecutionApi.qualityCheck(
      qcCurrentRow.executionId,
      qcForm.checkType === 'first_piece' ? 'FIRST' : 'PATROL',
      checkResult,
      checkItems,
      qcForm.defectDesc || undefined
    )
    ElMessage.success('质检完成')
    qcVisible.value = false
    if (qcForm.result === 'fail') ElMessage.warning('不合格，工序已自动暂停，请排查问题！')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '质检提交失败')
  }
}

// ============ P6 报工提交 ============
const reportOpen = ref(false)
const reportLoading = ref(false)
const reportExec = ref<OperationExecutionVO | null>(null)
const reportTasks = ref<TaskTreeRow[]>([])
const reportTaskLoading = ref(false)
const reportTaskId = ref<number | null>(null)
const reportTimeRange = ref<[string, string] | null>(null)
const reportForm = reactive({
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  defectReason: '',
  laborHours: 0,
  machineHours: 0,
  remark: '',
})

const formatLocalDateTime = (date: Date): string => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const normalizeDateTime = (value?: string): string | null =>
  value ? value.replace('T', ' ').slice(0, 19) : null

const hoursBetween = (start: string, end: string): number => {
  const startAt = new Date(start.replace(' ', 'T')).getTime()
  const endAt = new Date(end.replace(' ', 'T')).getTime()
  if (!Number.isFinite(startAt) || !Number.isFinite(endAt) || endAt <= startAt) return 0
  return Math.round(((endAt - startAt) / 3_600_000) * 100) / 100
}

const canSubmitReport = computed(() => {
  if (!reportTaskId.value) return false
  const q = Number(reportForm.qualifiedQuantity || 0)
  const d = Number(reportForm.defectiveQuantity || 0)
  if (q + d <= 0) return false
  if (d > 0 && !reportForm.defectReason.trim()) return false
  const task = reportTasks.value.find((t) => t.taskId === reportTaskId.value)
  if (task && q + d > Number(task.remainingQuantity || 0)) return false
  const start = reportTimeRange.value?.[0]
  const end = reportTimeRange.value?.[1]
  if (!!start !== !!end) return false
  return true
})

const openReportDialog = async (row: OperationExecutionVO, preferredTaskId?: number) => {
  reportExec.value = row
  reportTasks.value = []
  reportTaskId.value = null
  Object.assign(reportForm, {
    qualifiedQuantity: 0,
    defectiveQuantity: 0,
    defectReason: '',
    laborHours: 0,
    machineHours: 0,
    remark: '',
  })
  const startTime = normalizeDateTime(row.actualStartTime)
  const endTime = formatLocalDateTime(new Date())
  reportTimeRange.value = startTime ? [startTime, endTime] : null
  if (startTime) reportForm.laborHours = hoursBetween(startTime, endTime)
  reportOpen.value = true
  if (!row.executionId) return
  reportTaskLoading.value = true
  try {
    const res: any = await getMyTasks(row.executionId)
    reportTasks.value = res?.data || []
    if (preferredTaskId && reportTasks.value.some((t) => t.taskId === preferredTaskId))
      reportTaskId.value = preferredTaskId
    if (!reportTasks.value.length) {
      ElMessage.warning('当前工序没有可报工的任务，请确认任务已分配给您且正在进行中')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '我的任务加载失败')
  } finally {
    reportTaskLoading.value = false
  }
}

const handleReportSubmit = async () => {
  const exec = reportExec.value
  if (!exec?.executionId || !reportTaskId.value) return
  const qualified = Number(reportForm.qualifiedQuantity || 0)
  const defective = Number(reportForm.defectiveQuantity || 0)
  if (qualified + defective <= 0) {
    ElMessage.warning('合格与不良数量之和必须大于 0')
    return
  }
  if (defective > 0 && !reportForm.defectReason.trim()) {
    ElMessage.warning('不良数量大于 0 时，不良原因必填')
    return
  }
  reportLoading.value = true
  try {
    const result: any = await submitWorkReport({
      executionId: exec.executionId,
      taskId: reportTaskId.value,
      qualifiedQuantity: qualified,
      defectiveQuantity: defective,
      laborHours: Number(reportForm.laborHours || 0) || undefined,
      machineHours: Number(reportForm.machineHours || 0) || undefined,
      workStartTime: reportTimeRange.value?.[0] || undefined,
      workEndTime: reportTimeRange.value?.[1] || undefined,
      defectReason: defective > 0 ? reportForm.defectReason.trim() : undefined,
      remark: reportForm.remark.trim() || undefined,
    })
    if (!result?.data) throw new Error(result?.msg || '报工提交失败')
    ElMessage.success('报工已提交，等待审批')
    reportOpen.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '报工提交失败')
  } finally {
    reportLoading.value = false
  }
}

// ============ P6 我的报工 ============
const myReportsOpen = ref(false)
const myReportsLoading = ref(false)
const myReports = ref<WorkReportVO[]>([])
const myReportsPage = ref(1)
const myReportsPageSize = ref(10)
const myReportsTotal = ref(0)

const loadMyReports = async () => {
  myReportsLoading.value = true
  try {
    const res: any = await getMyWorkReports({
      pageNum: myReportsPage.value,
      pageSize: myReportsPageSize.value,
    })
    const data = res?.data
    myReports.value = Array.isArray(data) ? data : data?.records || []
    myReportsTotal.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '我的报工加载失败')
    myReports.value = []
  } finally {
    myReportsLoading.value = false
  }
}

const openMyReports = () => {
  myReportsOpen.value = true
  loadMyReports()
}

// ============ P6 待我审批 ============
const pendingOpen = ref(false)
const pendingLoading = ref(false)
const pendingList = ref<WorkReportVO[]>([])
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const pendingTotal = ref(0)

const loadPendingApproval = async () => {
  pendingLoading.value = true
  try {
    const res: any = await getPendingApprovalWorkReports({
      pageNum: pendingPage.value,
      pageSize: pendingPageSize.value,
    })
    const data = res?.data
    pendingList.value = Array.isArray(data) ? data : data?.records || []
    pendingTotal.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '待审批列表加载失败')
    pendingList.value = []
  } finally {
    pendingLoading.value = false
  }
}

const openPendingApproval = () => {
  pendingOpen.value = true
  loadPendingApproval()
}

// 审批通过
const approveVisible = ref(false)
const approveLoading = ref(false)
const approveTarget = ref<WorkReportVO | null>(null)
const approveRemark = ref('')

const openApprove = (row: WorkReportVO) => {
  approveTarget.value = row
  approveRemark.value = ''
  approveVisible.value = true
}

const handleApprove = async () => {
  const target = approveTarget.value
  if (!target) return
  approveLoading.value = true
  try {
    await approveWorkReport(target.reportId, {
      reviewRemark: approveRemark.value.trim() || undefined,
    })
    ElMessage.success('审批通过')
    approveVisible.value = false
    loadPendingApproval()
  } catch (e: any) {
    ElMessage.error(e?.message || '审批失败')
  } finally {
    approveLoading.value = false
  }
}

// 审批驳回
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectTarget = ref<WorkReportVO | null>(null)
const rejectReason = ref('')

const openReject = (row: WorkReportVO) => {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const handleReject = async () => {
  const target = rejectTarget.value
  if (!target) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回原因必填')
    return
  }
  rejectLoading.value = true
  try {
    await rejectWorkReport(target.reportId, { reviewRemark: rejectReason.value.trim() })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadPendingApproval()
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  } finally {
    rejectLoading.value = false
  }
}

onMounted(async () => {
  try {
    const scope: any = await getProductionExecutionScope()
    canViewAll.value = Boolean(scope?.data?.global)
  } catch {
    canViewAll.value = false
  }
  getList()
})
</script>

<style scoped>
.execution-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-actions {
  display: flex;
  gap: 8px;
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
  flex-wrap: wrap;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.scope-switch {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.scope-card {
  width: 255px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-bg-color);
  text-align: left;
  cursor: pointer;
}
.scope-card strong,
.scope-card span {
  display: block;
}
.scope-card span {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.scope-card.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.summary-header {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: help;
}
.pending-value {
  color: var(--el-color-danger);
}
.processable-value {
  color: var(--el-color-success);
  font-weight: 600;
}
.completed-value {
  color: var(--el-color-success);
}
.child-value {
  color: var(--el-color-primary);
}
.child-link {
  font-weight: 600;
}
.approval-value {
  color: var(--el-color-warning);
  font-weight: 600;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 12px;
}
.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.detail-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.metric-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.metric-card span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.metric-card strong {
  display: block;
  margin-top: 5px;
  font-size: 20px;
}
.metric-card.warning strong {
  color: var(--el-color-warning);
}
.metric-card.success strong {
  color: var(--el-color-success);
}
.metric-card.primary strong {
  color: var(--el-color-primary);
}
.responsibility-card {
  margin-bottom: 14px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.root-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.responsibility-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}
.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
