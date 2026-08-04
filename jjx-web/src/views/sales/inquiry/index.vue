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
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
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
          width="300"
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
              <el-select
                v-model="form.customerId"
                placeholder="请选择客户"
                filterable
                remote
                :remote-method="searchCustomer"
                :loading="customerLoading"
                style="width: 100%"
                @change="customerChanged"
              >
                <el-option
                  v-for="item in customerOptions"
                  :key="item.customerId"
                  :label="item.customerName"
                  :value="item.customerId"
                />
              </el-select>
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

        <el-divider content-position="left">规格要求</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="按键数量" prop="keyCount">
              <el-input-number
                v-model="form.keyCount"
                :min="0"
                placeholder="按键数"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="尺寸要求" prop="sizeDescription">
              <el-input v-model="form.sizeDescription" placeholder="长×宽×厚" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="材料要求" prop="materialRequirements">
              <el-input
                v-model="form.materialRequirements"
                placeholder="PET/银浆/弹片等"
                maxlength="500"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线路要求" prop="circuitRequirements">
              <el-input
                v-model="form.circuitRequirements"
                placeholder="线路类型/阻值等"
                maxlength="500"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="连接器要求" prop="connectorRequirements">
              <el-input
                v-model="form.connectorRequirements"
                placeholder="连接器型号/引脚数"
                maxlength="500"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有图纸" prop="hasDrawing">
              <el-switch v-model="form.hasDrawing" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="询价类型" prop="inquiryType">
              <el-radio-group v-model="form.inquiryType">
                <el-radio :value="1" border>标准品</el-radio>
                <el-radio :value="2" border>样品（需打样）</el-radio>
              </el-radio-group>
            </el-form-item>
            <!-- 标准品：从产品库选择，自动带出描述 -->
            <el-form-item v-if="form.inquiryType === 1" label="选择产品" prop="productId">
              <el-select
                v-model="form.productId"
                filterable
                placeholder="请选择产品"
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
            <el-form-item v-if="form.inquiryType === 1" label="产品描述" prop="productDescription">
              <el-input
                v-model="form.productDescription"
                type="textarea"
                :rows="3"
                placeholder="选择产品后自动带出，可修改"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <!-- 样品：手填描述 -->
            <el-form-item v-else label="产品描述" prop="productDescription">
              <el-input
                v-model="form.productDescription"
                type="textarea"
                :rows="3"
                placeholder="详细描述产品规格/功能要求"
                maxlength="2000"
                show-word-limit
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
        <el-divider content-position="left">附件</el-divider>
        <el-row>
          <el-col :span="24">
            <el-upload
              ref="uploadRef"
              :http-request="customUpload"
              :on-success="handleUploadSuccess"
              :on-remove="handleUploadRemove"
              :file-list="attachmentList"
              :before-upload="beforeUpload"
              list-type="text"
              multiple
            >
              <el-button type="primary" size="small">
                <el-icon><Upload /></el-icon> 上传客户资料
              </el-button>
              <template #tip>
                <div class="el-upload__tip">
                  支持客户资料(PDF/DWG/DXF/图片/Word/Excel/Markdown/ZIP)，单个文件不超过10MB；新增时文件保存后自动上传
                </div>
              </template>
            </el-upload>
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
          <el-descriptions-item label="关联产品">{{
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
          <el-descriptions-item label="按键数量">{{
            detailData.keyCount ?? '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="尺寸要求">{{
            detailData.sizeDescription || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="材料要求" :span="2">{{
            detailData.materialRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="线路要求" :span="2">{{
            detailData.circuitRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="连接器要求" :span="2">{{
            detailData.connectorRequirements || '-'
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
import { ref, reactive, onMounted, nextTick } from 'vue'
import type { TagType } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import BizFlowDetail from '@/components/BizFlowDetail/index.vue'
import type { FormInstance, UploadInstance, UploadProps, UploadRawFile } from 'element-plus'
import request from '@/utils/request'
import { Upload } from '@element-plus/icons-vue'
import { inquiryApi } from '@/api/sales/inquiry'
import { customerApi } from '@/api/sales/customer'
import { listProduct } from '@/api/product'
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
  productDescription: '',
  keyCount: undefined as number | undefined,
  sizeDescription: '',
  materialRequirements: '',
  circuitRequirements: '',
  connectorRequirements: '',
  specialRequirements: '',
  hasDrawing: 0,
  inquiryStatus: 0,
  inquiryType: 1,
  remark: '',
  salesPersonId: undefined as number | undefined,
  salesPersonName: '',
})

// 文件上传完成回调（占位，实际用途不大）
const handleUploadSuccess: UploadProps['onSuccess'] = () => {}

// 自定义上传：新建时暂存，编辑时立即上传
const customUpload: UploadProps['httpRequest'] = async (options) => {
  // 新建：暂存文件，等保存成功后再上传
  if (!form.inquiryId) {
    pendingUploads.value.push({ file: options.file })
    attachmentList.value.push({
      name: options.file.name,
      status: 'ready',
      uid: options.file.uid,
    })
    options.onSuccess({ name: options.file.name, status: 'ready' })
    return
  }

  // 编辑已有记录：立即上传
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('bizType', 'inquiry')
  formData.append('bizId', String(form.inquiryId))
  if ((form as any).traceId) formData.append('traceId', (form as any).traceId)
  try {
    const res: any = await request({
      url: '/system/attachment/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (res?.code === 200) {
      options.onSuccess(res.data)
      ElMessage.success('附件上传成功')
    } else {
      options.onError(new Error(res?.msg || '上传失败') as any)
    }
  } catch (e: any) {
    options.onError(e)
  }
}

// 查看报价单（跳转）
function gotoQuotation(row: any) {
  if (row.convertedQuotationId) {
    window.open(`/sales/quotation`, '_blank')
    ElMessage.info(`已打开的报价单页面，可搜索关联的询价单`)
  }
}

// 删除附件
const handleUploadRemove: UploadProps['onRemove'] = async (file) => {
  // 新建的待上传文件：从暂存列表移除
  if (!form.inquiryId) {
    const idx = pendingUploads.value.findIndex(
      (p) => p.file.name === file.name && (p.file as any).uid === (file as any).uid
    )
    if (idx !== -1) {
      pendingUploads.value.splice(idx, 1)
    }
    return
  }
  // 已上传的文件：调用删除接口
  if (file.response) {
    try {
      await request({ url: '/system/attachment/' + file.response, method: 'delete' })
    } catch {
      // 静默处理
    }
  }
}

// 上传前校验
const beforeUpload: UploadProps['beforeUpload'] = (file: UploadRawFile) => {
  const maxSize = 10 * 1024 * 1024 // 10MB
  const allowedTypes = [
    '.pdf',
    '.doc',
    '.docx',
    '.xls',
    '.xlsx',
    '.jpg',
    '.jpeg',
    '.png',
    '.dwg',
    '.dxf',
    '.zip',
    '.md',
  ]
  const ext = '.' + (file.name.split('.').pop()?.toLowerCase() || '')
  if (!allowedTypes.includes(ext)) {
    ElMessage.error('不支持的文件格式，支持 PDF/Word/Excel/图片/DWG/DXF/Markdown/ZIP')
    return false
  }
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }
  return true
}

// 上传暂存的文件（在保存成功后调用）
async function uploadPendingFiles(inquiryId: number, traceId?: string) {
  if (pendingUploads.value.length === 0) return
  for (const item of pendingUploads.value) {
    const formData = new FormData()
    formData.append('file', item.file)
    formData.append('bizType', 'inquiry')
    formData.append('bizId', String(inquiryId))
    if (traceId) formData.append('traceId', traceId)
    try {
      await request({
        url: '/system/attachment/upload',
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    } catch (e) {
      console.error('上传附件失败:', item.file.name, e)
    }
  }
  pendingUploads.value = []
}

// 加载已上传的附件
async function loadAttachments(bizId: number) {
  try {
    const res: any = await request({
      url: '/system/attachment/list',
      method: 'get',
      params: { bizType: 'inquiry', bizId },
    })
    if (res?.code === 200 && res.data) {
      attachmentList.value = res.data.map((a: any) => ({
        name: a.fileName,
        url: `/system/attachment/download/${a.id}`,
        response: a.id,
        status: 'success',
      }))
    }
  } catch {
    attachmentList.value = []
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
  { key: 'keyCount', label: '按键数量' },
  { key: 'sizeDescription', label: '尺寸要求' },
  { key: 'materialRequirements', label: '材料要求' },
  { key: 'circuitRequirements', label: '线路要求' },
  { key: 'connectorRequirements', label: '连接器要求' },
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

// ==================== 附件管理 ====================
const uploadRef = ref<UploadInstance>()
const attachmentList = ref<any[]>([])
const pendingUploads = ref<Array<{ file: File }>>([])

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
  }
}

// 加载产品列表（标准品选择用）
async function loadProducts() {
  productLoading.value = true
  try {
    const res = await listProduct({ pageNum: 1, pageSize: 100 } as any)
    productOptions.value = (res?.data as any)?.records || res?.data || []
  } catch {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

// 选择产品：自动带出产品描述
function onProductSelect(val: number) {
  const product = productOptions.value.find((p: any) => p.productId === val)
  if (product) {
    form.productDescription = `${product.productName}（${product.productCode}）`
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
      productDescription: data.productDescription,
      keyCount: data.keyCount,
      sizeDescription: data.sizeDescription,
      materialRequirements: data.materialRequirements,
      circuitRequirements: data.circuitRequirements,
      connectorRequirements: data.connectorRequirements,
      specialRequirements: data.specialRequirements,
      hasDrawing: data.hasDrawing ?? 0,
      inquiryStatus: data.inquiryStatus,
      inquiryType: data.inquiryType ?? 1,
      remark: data.remark,
      salesPersonId: data.salesPersonId,
      salesPersonName: data.salesPersonName,
    })
    // 加载已上传附件
    loadAttachments(id)
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
// 删除
function handleDelete(row?: any) {
  const delIds = row ? [row.inquiryId] : ids.value
  if (!delIds.length) return

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
  ElMessage.success('导出功能待完善')
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
    productDescription: '',
    keyCount: undefined,
    sizeDescription: '',
    materialRequirements: '',
    circuitRequirements: '',
    connectorRequirements: '',
    specialRequirements: '',
    hasDrawing: 0,
    inquiryStatus: 0,
    remark: '',
    salesPersonId: undefined,
    salesPersonName: '',
  })
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
      if (pendingUploads.value.length > 0) {
        const newId = (res as any)?.data?.inquiryId || form.inquiryId || 0
        const newTraceId = (res as any)?.data?.traceId || ''
        form.inquiryId = newId
        await uploadPendingFiles(newId, newTraceId)
      }
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
  pendingUploads.value = []
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
