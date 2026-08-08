<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="询价单号" prop="inquiryNo">
          <el-input
            v-model="queryParams.inquiryNo"
            placeholder="请输入询价单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="询价状态" prop="inquiryStatus">
          <el-select
            v-model="queryParams.inquiryStatus"
            placeholder="请选择状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in statusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="询价日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple || !canDelete" @click="handleDelete"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="inquiryList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="流水号" width="155" align="center" prop="traceId" />
        <el-table-column label="询价单号" align="center" width="180">
          <template #default="scope">
            <el-link type="primary" underline="never" @click="handleDetail(scope.row)">{{ scope.row.inquiryNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.inquiryType === 2" type="warning" size="small">样品</el-tag>
            <el-tag v-else type="primary" size="small">标准</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
        <el-table-column label="联系人" align="center" prop="contactPerson" width="120" />
        <el-table-column label="预估数量" align="center" prop="expectedQuantity" width="100">
          <template #default="scope">
            {{ scope.row.expectedQuantity || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="产品描述" align="center" prop="productDescription" min-width="200">
          <template #default="scope">
            <el-tooltip :content="scope.row.productDescription || '-'" placement="top">
              <span class="text-ellipsis">{{ scope.row.productDescription || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="需求图纸" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.hasDrawing" type="success" size="small">有图纸</el-tag>
            <el-tag v-else type="info" size="small">无图纸</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="询价日期" align="center" prop="inquiryDate" width="120" />
        <el-table-column label="询价状态" align="center" width="120">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.inquiryStatus)" size="small">
              {{ statusLabel(scope.row.inquiryStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="销售负责人" align="center" prop="salesPersonName" width="120" />
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="400"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleUpdate(scope.row)"
              :disabled="scope.row.inquiryStatus === 3"
              >编辑</el-button
            >
            <el-button link type="info" icon="Connection" @click="showTrace(scope.row)"
              >查看流水</el-button
            >
            <!-- 发送（草稿/待处理 → 已发送） -->
            <el-button
              v-if="[0, 1].includes(scope.row.inquiryStatus)"
              link
              type="warning"
              icon="Promotion"
              @click="handleSend(scope.row)"
              >发送</el-button
            >
            <!-- 客户确认/拒绝（已发送） -->
            <template v-if="scope.row.inquiryStatus === 2">
              <el-button link type="success" icon="CircleCheck" @click="handleAccept(scope.row)"
                >确认</el-button
              >
              <el-button link type="danger" icon="CircleClose" @click="handleReject(scope.row)"
                >拒绝</el-button
              >
            </template>
            <template v-if="scope.row.inquiryStatus === 3">
              <el-button link type="success" icon="Link" @click="gotoQuotation(scope.row)"
                >查看报价</el-button
              >
            </template>
            <template v-else>
              <el-button link type="primary" icon="Right" @click="handleConvert(scope.row)"
                >转报价</el-button
              >
            </template>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/修改询价单对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="800px"
      append-to-body
      @close="handleClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">客户信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <CustomerSelector
                v-model="form.customerId"
                value-type="customerId"
                placeholder="请选择客户"
                @change="customerChanged"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input v-model="form.customerName" placeholder="客户名称" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="询价日期" prop="inquiryDate">
              <el-date-picker
                v-model="form.inquiryDate"
                type="date"
                placeholder="请选择询价日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估数量" prop="expectedQuantity">
              <el-input-number
                v-model="form.expectedQuantity"
                :min="0"
                placeholder="预估数量"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">产品信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="类型" prop="inquiryType">
              <el-radio-group v-model="form.inquiryType" @change="onTypeChange">
                <el-radio :value="1" border>标准品</el-radio>
                <el-radio :value="2" border>样品（需打样）</el-radio>
              </el-radio-group>
            </el-form-item>

            <!-- 标准品：选择产品，自动回填描述/编码/名称 -->
            <el-form-item v-if="form.inquiryType === 1" label="选择产品" prop="productId">
              <el-select
                v-model="form.productId"
                filterable
                remote
                :remote-method="searchProducts"
                :loading="productLoading"
                placeholder="请输入产品名称/编码搜索"
                style="width: 100%"
                @change="onProductSelect"
              >
                <el-option
                  v-for="p in productOptions"
                  :key="p.productId"
                  :label="`${p.productName}（${p.productCode}）`"
                  :value="p.productId"
                />
              </el-select>
            </el-form-item>

            <!-- 样品：编码构成要素直接展示（客户简称+流水号+面板结构+线路结构） -->
            <template v-if="form.inquiryType === 2">
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="客户简称">
                    <el-input v-model="shortNameDisplay" readonly placeholder="选择客户后自动带出" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="流水号">
                    <el-input v-model="codeSerialNo" maxlength="3" placeholder="3位，点生成编码自动取号可改" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="面板结构" required>
                    <el-select v-model="codePanelType" placeholder="面板类型" style="width: 100%" @change="composeCode">
                      <el-option label="有面板有线路" value="M" />
                      <el-option label="仅有线路" value="S" />
                      <el-option label="仅有面板" value="P" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="面板特征" required>
                    <el-select v-model="codePanelFeature" placeholder="面板特征" style="width: 100%" @change="composeCode">
                      <el-option label="面板有凹凸" value="E" />
                      <el-option label="面板有窗口" value="W" />
                      <el-option label="有窗口也有凹凸" value="H" />
                      <el-option label="无" value="O" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="线路类型" required>
                    <el-select v-model="codeCircuitType" placeholder="线路类型" style="width: 100%" @change="composeCode">
                      <el-option label="无(印银平key)" value="O" />
                      <el-option label="有金属弹片" value="M" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="线路特征" required>
                    <el-select v-model="codeCircuitFeature" placeholder="线路特征" style="width: 100%" @change="composeCode">
                      <el-option label="无" value="O" />
                      <el-option label="有发光二极体" value="L" />
                      <el-option label="有连接器" value="C" />
                      <el-option label="有连接器及发光二极体" value="H" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <div class="form-tip" style="margin-bottom: 12px">
                编码格式：客户简称(3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)，如 JST001MEOL
              </div>
            </template>

            <!-- 产品描述 -->
            <el-form-item label="产品描述" prop="productDescription">
              <el-input
                v-model="form.productDescription"
                type="textarea"
                :rows="3"
                :placeholder="form.inquiryType === 1 ? '选择产品后自动带出，可修改' : '详细描述产品规格/功能要求'"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>

            <!-- 产品编码（标准品选产品回填/样品生成器生成，均可改） -->
            <el-form-item label="产品编码">
              <el-input
                v-model="form.productCode"
                placeholder="标准品选产品自动带出；样品选编码要素自动生成，可手动修改"
                maxlength="50"
              >
                <template #append>
                  <el-button v-if="form.inquiryType === 2" @click="generateCode" :loading="generatingSerial">
                    <el-icon><Refresh /></el-icon> 生成编码
                  </el-button>
                </template>
              </el-input>
            </el-form-item>

            <!-- 产品名称（默认与编码一致，可改） -->
            <el-form-item label="产品名称">
              <el-input
                v-model="form.productName"
                placeholder="默认与编码一致，可修改"
                maxlength="200"
                @input="nameEdited = true"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="特殊要求" prop="specialRequirements">
              <el-input
                v-model="form.specialRequirements"
                type="textarea"
                :rows="2"
                placeholder="其他特殊要求"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
                placeholder="备注"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="有图纸" prop="hasDrawing">
              <el-switch v-model="form.hasDrawing" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">附件</el-divider>
        <el-row>
          <el-col :span="24">
            <AttachmentPanel
              v-if="form.inquiryId"
              biz-type="inquiry"
              :biz-id="form.inquiryId"
              style="margin-bottom: 10px"
            />
            <AttachmentUploader
              ref="uploaderRef"
              biz-type="inquiry"
              :biz-id="form.inquiryId"
              :trace-id="(form as any).traceId"
              :accept="['.pdf','.doc','.docx','.xls','.xlsx','.jpg','.jpeg','.png','.dwg','.dxf','.zip','.md']"
              button-text="上传客户资料"
              tip="支持客户资料(PDF/DWG/DXF/图片/Word/Excel/Markdown/ZIP)，单个文件不超过10MB；新增时文件保存后自动上传"
            />
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm" :loading="submitting">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    
    <!-- 查看详情对话框 -->
    <el-dialog title="询价单详情" v-model="detailVisible" width="700px" append-to-body>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="询价单号" :span="2">{{
            detailData.inquiryNo
          }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag v-if="detailData.inquiryType === 2" type="warning" size="small">样品</el-tag>
            <el-tag v-else type="primary" size="small">标准</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品编码">{{ detailData.productCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{
            detailData.productName || (detailData.productId ? '产品#' + detailData.productId : '-')
          }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{
            detailData.customerName
          }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{
            detailData.contactPerson || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{
            detailData.contactPhone || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="询价日期">{{ detailData.inquiryDate }}</el-descriptions-item>
          <el-descriptions-item label="预估数量">{{
            detailData.expectedQuantity || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="产品描述" :span="2">{{
            detailData.productDescription || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="特殊要求" :span="2">{{
            detailData.specialRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="需求图纸">
            <el-tag v-if="detailData.hasDrawing" type="success" size="small">有图纸</el-tag>
            <el-tag v-else type="info" size="small">无图纸</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="询价状态">
            <el-tag :type="statusTagType(detailData.inquiryStatus)" size="small">
              {{ statusLabel(detailData.inquiryStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="销售负责人" :span="2">{{
            detailData.salesPersonName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            detailData.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-divider content-position="left">相关文档</el-divider>
      <AttachmentPanel
        v-if="detailData?.inquiryId"
        biz-type="inquiry"
        :biz-id="detailData.inquiryId"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 操作状态流转弹窗（审核/转报价） -->
    <BizFlowDetail
      v-model="opDialogVisible"
      :biz-type="'inquiry'"
      :biz-id="opRow?.inquiryId"
      :title="opTitle"
      :operation-name="opName"
      :biz-no="opRow?.inquiryNo"
      :from-status="opFromStatus"
      :from-status-label="opFromLabel"
      :to-status="opToStatus"
      :to-status-label="opToLabel"
      :confirm-text="opConfirmText"
      :data="opRow"
      :detail-items="inquiryDetailItems"
      :confirm-api="opConfirmApi"
      @confirm-success="handleOpSuccess"
    />
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import type { TagType } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import BizFlowDetail from '@/components/BizFlowDetail/index.vue'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import AttachmentUploader from '@/components/AttachmentUploader/index.vue'
import type { FormInstance } from 'element-plus'
import request from '@/utils/request'
import { inquiryApi } from '@/api/sales/inquiry'
import { customerApi } from '@/api/sales/customer'
import { listProduct, getProductInfo } from '@/api/product'
import { parseSpecJson } from '@/utils/specJsonHelper'
import { download } from '@/utils/format'
import type { ProductItem } from '@/types/product'
import type { CustomerSearchVO } from '@/types/sales/customer'

defineOptions({
  name: 'SalesInquiry',
})

// ==================== 数据定义 ====================
interface CustomerOption {
  customerId: number
  customerName: string
  contactPerson?: string
  contactPhone?: string
}

const loading = ref(false)
const submitting = ref(false)
const inquiryList = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const detailVisible = ref(false)

const dialogTitle = ref('')
const single = ref(true)
const multiple = ref(true)
const ids = ref<number[]>([])
const selectedRows = ref<any[]>([])
// 已转报价(3)的询价单禁止删除
const canDelete = computed(() => {
  return selectedRows.value.length > 0 && selectedRows.value.every((r) => r.inquiryStatus !== 3)
})
const dateRange = ref<string[]>([])

const customerOptions = ref<CustomerOption[]>([])
const customerLoading = ref(false)

// 产品选项（标准品选择用）
const productOptions = ref<ProductItem[]>([])
const productLoading = ref(false)

const statusOptions = ref<Array<{ value: string; label: string }>>([])

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  inquiryNo: '',
  customerName: '',
  inquiryStatus: '',
  orderByColumn: '',
  isAsc: '',
})

// 表单数据
const formRef = ref<FormInstance>()
const form = reactive({
  inquiryId: undefined as number | undefined,
  customerId: undefined as number | undefined,
  customerName: '',
  contactPerson: '',
  contactPhone: '',
  inquiryDate: '',
  expectedQuantity: undefined as number | undefined,
  productId: undefined as number | undefined,
  productCode: '',
  productName: '',
  customerShortName: '',
  productDescription: '',
  specialRequirements: '',
  hasDrawing: 0,
  inquiryStatus: 0,
  inquiryType: 1,
  remark: '',
  salesPersonId: undefined as number | undefined,
  salesPersonName: '',
})

// 查看报价单（跳转并定位，DEV-590）
function gotoQuotation(row: any) {
  if (row.convertedQuotationId) {
    window.open(`/sales/quotation?quotationId=${row.convertedQuotationId}`, '_blank')
  }
}

// 表单验证规则
const rules: Record<string, any> = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  inquiryDate: [{ required: true, message: '请选择询价日期', trigger: 'change' }],
}

// 详情数据
const detailData = ref<any>(null)

// ==================== 操作状态流转弹窗 ====================
const opDialogVisible = ref(false)
const opRow = ref<any>(null)
const opTitle = ref('')
const opName = ref('')
const opFromStatus = ref<number | null>(null)
const opFromLabel = ref('')
const opToStatus = ref<number | null>(null)
const opToLabel = ref('')
const opConfirmText = ref('确认')
const opConfirmApi = ref<any>(null)

// 询价通用详情字段配置
const inquiryDetailItems = [
  { key: 'inquiryNo', label: '询价单号' },
  { key: 'inquiryType', label: '类型', type: 'tag' as const, tagType: 'primary' as const, format: (v: any) => (v === 2 ? '样品' : '标准') },
  { key: 'customerName', label: '客户名称' },
  { key: 'contactPerson', label: '联系人' },
  { key: 'contactPhone', label: '联系电话' },
  { key: 'inquiryDate', label: '询价日期' },
  { key: 'expectedQuantity', label: '预估数量' },
  { key: 'productCode', label: '产品编码' },
  { key: 'productName', label: '产品名称' },
  { key: 'productDescription', label: '产品描述' },
  { key: 'specialRequirements', label: '特殊要求' },
  { key: 'salesPersonName', label: '销售负责人' },
  { key: 'remark', label: '备注' },
]

// 打开操作弹窗
function openOperation(row: any, kind: 'convert') {
  opRow.value = row
  const cur = Number(row.inquiryStatus)
  opFromStatus.value = cur
  opFromLabel.value = statusLabel(cur)
  opTitle.value = '询价转报价'
  opName.value = '转报价'
  opToStatus.value = 3
  opToLabel.value = '已转报价'
  opConfirmText.value = '确认转报价'
  opConfirmApi.value = {
    url: `/sales/inquiry/convert/${row.inquiryId}`,
    method: 'post',
    buildParams: (id: number) => ({}),
  }
  opDialogVisible.value = true
}

// 操作确认成功
function handleOpSuccess() {
  getList()
}

// ==================== 附件管理（DEV-733 统一组件） ====================
const uploaderRef = ref<InstanceType<typeof AttachmentUploader>>()

// ==================== 状态映射 ====================
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '待处理', type: 'warning' },
  2: { label: '已发送', type: 'primary' },
  3: { label: '已转报价', type: 'success' },
  4: { label: '已确认', type: 'success' },
  5: { label: '已拒绝', type: 'danger' },
  6: { label: '已过期', type: 'info' },
}

function statusLabel(status: number): string {
  return statusMap[status]?.label || String(status ?? '')
}

function statusTagType(status: number): TagType {
  return (statusMap[status]?.type || 'info') as TagType
}

// ==================== 方法 ====================
async function getList() {
  loading.value = true
  try {
    // 处理日期范围
    const params = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      ;(params as any).startDate = dateRange.value[0]
      ;(params as any).endDate = dateRange.value[1]
    }
    const res = await inquiryApi.list(params)
    inquiryList.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    console.error('获取询价单列表失败:', e)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.inquiryNo = ''
  queryParams.customerName = ''
  queryParams.inquiryStatus = ''
  queryParams.orderByColumn = ''
  queryParams.isAsc = ''
  dateRange.value = []
  handleQuery()
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.inquiryId)
  selectedRows.value = selection
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 搜索客户（远程搜索）
async function searchCustomer(query: string) {
  if (!query || query.length < 1) return
  customerLoading.value = true
  try {
    const res = await customerApi.searchCustomers(query)
    customerOptions.value = res.data || []
  } catch {
    customerOptions.value = []
  } finally {
    customerLoading.value = false
  }
}

function customerChanged(val: number) {
  const customer = customerOptions.value.find((c: any) => c.customerId === val)
  if (customer) {
    form.customerName = customer.customerName
    if (!form.contactPerson) form.contactPerson = (customer as any).contactPerson || ''
    if (!form.contactPhone) form.contactPhone = (customer as any).contactPhone || ''
    // 编码生成器：带出客户简称并自动取号（样品询价）
    form.customerShortName = (customer as any).customerShortName || ''
    if (form.inquiryType === 2) {
      generateCode()
    }
  }
}

// ==================== 编码生成器（参考产品新增面板线路） ====================
const codeSerialNo = ref('')
const codePanelType = ref('')
const codePanelFeature = ref('')
const codeCircuitType = ref('')
const codeCircuitFeature = ref('')
const nameEdited = ref(false)
const generatingSerial = ref(false)

const shortNameDisplay = computed(() => {
  const s = form.customerShortName || ''
  return s.substring(0, 3)
})

// 类型切换：标准品/样品切换时清掉编码生成状态（保留已填编码？切换时清空避免串数据）
function onTypeChange() {
  codeSerialNo.value = ''
  codePanelType.value = ''
  codePanelFeature.value = ''
  codeCircuitType.value = ''
  codeCircuitFeature.value = ''
  form.productCode = ''
  nameEdited.value = false
  if (form.inquiryType === 1) {
    form.productName = ''
  } else {
    form.productName = ''
    if (form.customerShortName) generateCode()
  }
}

// 自动取号 + 拼接编码
async function generateCode() {
  if (!form.customerShortName) {
    ElMessage.warning('请先选择客户（用于客户简称）')
    return
  }
  generatingSerial.value = true
  try {
    const res: any = await inquiryApi.nextSerial(shortNameDisplay.value)
    codeSerialNo.value = (res as any)?.data || '001'
  } catch {
    codeSerialNo.value = '001'
  } finally {
    generatingSerial.value = false
    composeCode()
  }
}

// 拼接：客户简称3 + 流水号3 + 面板结构2 + 线路结构2
function composeCode() {
  const customerPart = shortNameDisplay.value
  const serialPart = codeSerialNo.value || ''
  const panelPart = `${codePanelType.value}${codePanelFeature.value}`
  const circuitPart = `${codeCircuitType.value}${codeCircuitFeature.value}`
  if (customerPart.length === 3 && serialPart.length === 3 && panelPart.length === 2 && circuitPart.length === 2) {
    form.productCode = `${customerPart}${serialPart}${panelPart}${circuitPart}`
    if (!nameEdited.value) {
      form.productName = form.productCode
    }
  }
}

// 加载产品列表（标准品选择用，首次加载一批）
async function loadProducts() {
  productLoading.value = true
  try {
    const res = await listProduct({ pageNum: 1, pageSize: 50 } as any)
    productOptions.value = (res?.data as any)?.records || res?.data || []
  } catch {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

// 产品远程搜索（DEV-590）
async function searchProducts(query: string) {
  if (!query || query.length < 1) return
  productLoading.value = true
  try {
    const res = await listProduct({ pageNum: 1, pageSize: 50, productName: query } as any)
    productOptions.value = (res?.data as any)?.records || res?.data || []
  } catch {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

// 选择产品：带出描述 + 按规格参数回填技术要求（DEV-590）
async function onProductSelect(val: number) {
  const product = productOptions.value.find((p: any) => p.productId === val)
  if (product) {
    form.productDescription = `${product.productName}（${product.productCode}）`
    // 标准品：编码/名称带出产品档案（可改），并反解编码构成要素供查看/修改
    form.productCode = product.productCode
    form.productName = product.productName
    nameEdited.value = true
    const code = product.productCode || ''
    if (code.length >= 10) {
      form.customerShortName = code.substring(0, 3)
      codeSerialNo.value = code.substring(3, 6)
      codePanelType.value = code.substring(6, 7)
      codePanelFeature.value = code.substring(7, 8)
      codeCircuitType.value = code.substring(8, 9)
      codeCircuitFeature.value = code.substring(9, 10)
    }
  }
  try {
    const res: any = await getProductInfo(val)
    const vo = res?.data
    if (vo?.specJson) {
      // 规格要素拼接进产品描述（规格要求字段已移除，2026-08-08）
      const specs = parseSpecJson(vo.specJson)
      const specParts = specs.map((s: any) => `${s.name}:${s.value ?? ''}`)
      if (specParts.length && !form.productDescription) {
        form.productDescription = `${form.productName}（${form.productCode}） ${specParts.join('；')}`
      }
    }
  } catch (e) {
    console.error('加载产品规格失败:', e)
  }
}

// 新增
function handleAdd() {
  dialogTitle.value = '新增询价单'
  dialogVisible.value = true
  resetForm()
}

// 修改
function handleUpdate(row?: any) {
  const id = row?.inquiryId || ids.value[0]
  if (!id) return

  dialogTitle.value = '修改询价单'
  dialogVisible.value = true

  inquiryApi.getInfo(id).then((res) => {
    const data = res.data
    Object.assign(form, {
      inquiryId: data.inquiryId,
      customerId: data.customerId,
      customerName: data.customerName,
      contactPerson: data.contactPerson,
      contactPhone: data.contactPhone,
      inquiryDate: data.inquiryDate,
      expectedQuantity: data.expectedQuantity,
      productId: data.productId,
      productCode: data.productCode || '',
      productName: data.productName || '',
      productDescription: data.productDescription,
      specialRequirements: data.specialRequirements,
      hasDrawing: data.hasDrawing ?? 0,
      inquiryStatus: data.inquiryStatus,
      inquiryType: data.inquiryType ?? 1,
      remark: data.remark,
      salesPersonId: data.salesPersonId,
      salesPersonName: data.salesPersonName,
    })
    // 编码生成器回显：编码存在则反解构成要素（客户3/流水3/面板2/线路2）
    const code = form.productCode || ''
    if (code.length >= 10 && form.inquiryType === 2) {
      form.customerShortName = code.substring(0, 3)
      codeSerialNo.value = code.substring(3, 6)
      codePanelType.value = code.substring(6, 7)
      codePanelFeature.value = code.substring(7, 8)
      codeCircuitType.value = code.substring(8, 9)
      codeCircuitFeature.value = code.substring(9, 10)
      nameEdited.value = form.productName !== code
    }
  })
}

// 查看
function handleDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

// 转报价
function handleConvert(row: any) {
  if (row.inquiryStatus === 3) {
    ElMessage.warning('该询价单已转换')
    return
  }
  openOperation(row, 'convert')
}

// 发送询价（草稿/待处理 → 已发送）
function handleSend(row: any) {
  ElMessageBox.confirm(`确认发送询价单「${row.inquiryNo}」给客户吗？`, '发送确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      inquiryApi.send(row.inquiryId).then(() => {
        ElMessage.success('发送成功')
        getList()
      })
    })
    .catch(() => {})
}

// 客户确认询价（已发送 → 已确认）
function handleAccept(row: any) {
  ElMessageBox.confirm(`确认客户已接受询价单「${row.inquiryNo}」吗？`, '确认提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      inquiryApi.accept(row.inquiryId).then(() => {
        ElMessage.success('已确认')
        getList()
      })
    })
    .catch(() => {})
}

// 客户拒绝询价（已发送 → 已拒绝）
function handleReject(row: any) {
  ElMessageBox.prompt(`请输入拒绝询价单「${row.inquiryNo}」的原因`, '拒绝确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '拒绝原因（可选）',
    inputValidator: (value: string) => (value?.trim() ? true : '请输入拒绝原因'),
  })
    .then(() => {
      inquiryApi.reject(row.inquiryId).then(() => {
        ElMessage.success('已拒绝')
        getList()
      })
    })
    .catch(() => {})
}
// 删除
function handleDelete(row?: any) {
  const delRows = row ? [row] : selectedRows.value
  const delIds = row ? [row.inquiryId] : ids.value
  if (!delIds.length) return
  if (delRows.some((r: any) => r.inquiryStatus === 3)) {
    ElMessage.warning('已转报价的询价单不能删除')
    return
  }

  ElMessageBox.confirm(`确定删除选中的 ${delIds.length} 条询价单吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      inquiryApi.remove(delIds).then(() => {
        ElMessage.success('删除成功')
        getList()
      })
    })
    .catch(() => {})
}

// 导出
function handleExport() {
  inquiryApi
    .export(queryParams as any)
    .then((res: any) => {
      download(res, '询价单列表.xlsx')
    })
    .catch(() => {
      ElMessage.error('导出失败')
    })
}

// 表单操作
function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    inquiryId: undefined,
    customerId: undefined,
    customerName: '',
    contactPerson: '',
    contactPhone: '',
    inquiryDate: '',
    expectedQuantity: undefined,
    productId: undefined,
    productCode: '',
    productName: '',
    customerShortName: '',
    productDescription: '',
    specialRequirements: '',
    hasDrawing: 0,
    inquiryStatus: 0,
    remark: '',
    salesPersonId: undefined,
    salesPersonName: '',
  })
  // 重置编码生成器
  codeSerialNo.value = ''
  codePanelType.value = ''
  codePanelFeature.value = ''
  codeCircuitType.value = ''
  codeCircuitFeature.value = ''
  nameEdited.value = false
}

// 初始化客户选项（首次加载全部）
async function initCustomerOptions() {
  try {
    const res = await customerApi.searchCustomers('')
    customerOptions.value = res.data || []
  } catch {
    customerOptions.value = []
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.inquiryId) {
      await inquiryApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      const res = await inquiryApi.add(form as any)
      // 保存成功后用返回的新ID和traceId上传暂存的文件
      const newId = (res as any)?.data?.inquiryId || form.inquiryId || 0
      const newTraceId = (res as any)?.data?.traceId || ''
      form.inquiryId = newId
      await uploaderRef.value?.flushPending(newId, newTraceId)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch (e) {
    console.error('提交失败:', e)
  } finally {
    submitting.value = false
  }
}

function cancel() {
  dialogVisible.value = false
}

// 关闭对话框后重置附件状态
function handleClose() {
  uploaderRef.value?.clearPending()
}

// ==================== 初始化 ====================
onMounted(() => {
  getList()
  initCustomerOptions()
  loadProducts()
  // 加载状态选项
  inquiryApi
    .getStatusOptions()
    .then((res) => {
      statusOptions.value = res.data || []
    })
    .catch(() => {})
})
// 链路追踪抽屉
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
function showTrace(row: any) {
  currentTraceId.value = row.traceId || ''
  traceDrawerVisible.value = true
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
.mb8 {
  margin-bottom: 8px;
}
.text-ellipsis {
  display: inline-block;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
