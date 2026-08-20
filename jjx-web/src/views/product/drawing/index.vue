<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="图纸编号" prop="drawingNo">
          <el-input
            v-model="queryParams.drawingNo"
            placeholder="请输入图纸编号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="图纸名称" prop="drawingName">
          <el-input
            v-model="queryParams.drawingName"
            placeholder="请输入图纸名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品编码" prop="productCode">
          <el-input
            v-model="queryParams.productCode"
            placeholder="请输入产品编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="图纸类型" prop="drawingType">
          <el-select
            v-model="queryParams.drawingType"
            placeholder="请选择图纸类型"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in drawingTypeOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
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
          <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="() => handleUpdate()"
            v-hasPermi="['engineering:edit']"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="() => handleDelete()"
            v-hasPermi="['engineering:delete']"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="info"
            plain
            icon="Upload"
            :disabled="single"
            @click="() => handleUpload()"
            >上传图纸</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Download"
            :disabled="single"
            @click="() => handleDownload()"
            >下载图纸</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="drawingList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="图纸编号" align="center" prop="drawingNo" width="160" />
        <el-table-column label="图纸名称" align="center" prop="drawingName" width="180" />
        <el-table-column label="产品编码" align="center" prop="productCode" width="160" />
        <el-table-column label="产品名称" align="center" prop="productName" width="180" />
        <el-table-column label="图纸类型" prop="drawingType" width="120">
          <template #default="scope">
            <el-tag :type="getDrawingTypeTagType(scope.row.drawingType)">
              {{ getDrawingTypeLabel(scope.row.drawingType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" prop="version" width="80" />
        <el-table-column label="文件大小" prop="fileSize" width="100">
          <template #default="scope">
            <span>{{ formatFileSize(scope.row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文件格式" prop="fileFormat" width="100" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="300"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                v-hasPermi="['engineering:edit']"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                v-hasPermi="['engineering:delete']"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="info" icon="View" @click="handleView(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="下载" placement="top">
              <el-button
                link
                type="success"
                icon="Download"
                @click="handleDownloadDrawing(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="预览" placement="top">
              <el-button
                link
                type="warning"
                icon="Picture"
                @click="handlePreview(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 添加或修改图纸对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="drawingFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="图纸编号" prop="drawingNo">
              <el-input
                v-model="form.drawingNo"
                placeholder="系统自动生成"
                maxlength="50"
                :readonly="true"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="form.productId"
                placeholder="请选择产品"
                filterable
                remote
                :remote-method="searchProduct"
                :loading="productLoading"
                style="width: 100%"
                @change="handleProductChange"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.productId"
                  :label="item.productName"
                  :value="item.productId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="图纸名称" prop="drawingName">
              <el-input v-model="form.drawingName" placeholder="请输入图纸名称" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图纸类型" prop="drawingType">
              <el-select
                v-model="form.drawingType"
                placeholder="请选择图纸类型"
                style="width: 100%"
              >
                <el-option
                  v-for="dict in drawingTypeOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="版本" prop="version">
              <el-input v-model="form.version" placeholder="请输入版本号" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文件" prop="file">
              <el-upload
                class="upload-demo"
                action="#"
                :auto-upload="false"
                :on-change="handleFileChange"
                :show-file-list="false"
              >
                <el-button type="primary">选择文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持PDF、DWG、DXF、JPG、PNG格式，大小不超过50MB</div>
                </template>
              </el-upload>
              <div v-if="form.fileName" class="file-info">
                <el-icon><Document /></el-icon>
                <span>{{ form.fileName }}</span>
                <span class="file-size">{{ formatFileSize(form.fileSize) }}</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                placeholder="请输入备注"
                :rows="3"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 图纸详情对话框 -->
    <el-dialog title="技术图纸详情" v-model="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="图纸编号">{{ detail.drawingNo }}</el-descriptions-item>
        <el-descriptions-item label="图纸名称">{{ detail.drawingName }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="图纸类型">
          <el-tag :type="getDrawingTypeTagType(detail.drawingType)">
            {{ getDrawingTypeLabel(detail.drawingType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="文件名称">{{ detail.fileName }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{
          formatFileSize(detail.fileSize)
        }}</el-descriptions-item>
        <el-descriptions-item label="文件格式">{{ detail.fileFormat }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(detail.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          parseTime(detail.updateTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detail.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detail.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 图纸预览对话框 -->
    <el-dialog title="图纸预览" v-model="previewOpen" width="90%" top="5vh" append-to-body>
      <div class="preview-container">
        <div v-if="previewType === 'image'" class="image-preview">
          <img :src="previewUrl" alt="图纸预览" style="max-width: 100%" />
        </div>
        <div v-else-if="previewType === 'pdf'" class="pdf-preview">
          <iframe :src="previewUrl" width="100%" height="600" frameborder="0"></iframe>
        </div>
        <div v-else class="unsupported-preview">
          <el-alert
            title="不支持在线预览"
            type="warning"
            description="该文件格式不支持在线预览，请下载后查看"
            show-icon
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductDrawing',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { productApi } from '@/api/product'
import { parseTime } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  drawingNo: undefined as string | undefined,
  drawingName: undefined as string | undefined,
  productCode: undefined as string | undefined,
  drawingType: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 响应式数据
const loading = ref(false)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const previewOpen = ref(false)
const productLoading = ref(false)
const productOptions = ref<Array<{ productId: number; productCode: string; productName: string }>>(
  []
)
const previewUrl = ref('')
const previewType = ref('')

// 表单数据
const form = reactive({
  drawingId: undefined as number | undefined,
  drawingNo: '',
  drawingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  drawingType: '2d',
  version: '1.0',
  fileName: '',
  fileSize: 0,
  fileFormat: '',
  filePath: '',
  remark: '',
  file: null as File | null,
})

// 详情数据
const detail = reactive({
  drawingId: 0,
  drawingNo: '',
  drawingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  drawingType: '',
  version: '',
  fileName: '',
  fileSize: 0,
  fileFormat: '',
  filePath: '',
  remark: '',
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

// 表单引用
const drawingFormRef = ref<FormInstance>()

// 表单验证规则
const rules = reactive<FormRules>({
  drawingName: [{ required: true, message: '请输入图纸名称', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  drawingType: [{ required: true, message: '请选择图纸类型', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
})

// 表格数据
const drawingList = ref<any[]>([])

// 字典选项
const drawingTypeOptions = ref([
  { value: '2d', label: '2D图纸' },
  { value: '3d', label: '3D模型' },
  { value: 'assembly', label: '装配图' },
  { value: 'detail', label: '零件图' },
  { value: 'scheme', label: '方案图' },
  { value: 'process', label: '工艺图' },
  { value: 'other', label: '其他' },
])

// 获取图纸列表
const getList = async () => {
  loading.value = true
  try {
    // 这里应该调用图纸API
    // 暂时使用模拟数据
    setTimeout(() => {
      drawingList.value = [
        {
          drawingId: 1,
          drawingNo: 'DRW001',
          drawingName: '产品A装配图',
          productId: 1,
          productCode: 'P001',
          productName: '产品A',
          drawingType: 'assembly',
          version: '1.0',
          fileName: 'assembly_drw001.pdf',
          fileSize: 2048000,
          fileFormat: 'PDF',
          createTime: '2024-01-15 10:30:00',
        },
        {
          drawingId: 2,
          drawingNo: 'DRW002',
          drawingName: '产品B零件图',
          productId: 2,
          productCode: 'P002',
          productName: '产品B',
          drawingType: 'detail',
          version: '1.0',
          fileName: 'detail_drw002.dwg',
          fileSize: 5120000,
          fileFormat: 'DWG',
          createTime: '2024-01-16 11:30:00',
        },
      ]
      total.value = 2
      loading.value = false
    }, 500)
  } catch (error) {
    console.error('获取图纸列表失败:', error)
    loading.value = false
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 图纸类型标签类型/文本
const getDrawingTypeTagType = (type: string) => {
  switch (type) {
    case '2d':
      return 'primary'
    case '3d':
      return 'success'
    case 'assembly':
      return 'warning'
    case 'detail':
      return 'info'
    case 'scheme':
      return 'danger'
    case 'process':
      return 'success'
    default:
      return 'info'
  }
}

const getDrawingTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    '2d': '2D图纸',
    '3d': '3D模型',
    assembly: '装配图',
    detail: '零件图',
    scheme: '方案图',
    process: '工艺图',
    other: '其他',
  }
  return map[type] || '未知'
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    drawingNo: undefined,
    drawingName: undefined,
    productCode: undefined,
    drawingType: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.drawingId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 排序触发
const handleSortChange = (column: any) => {
  if (column.prop && column.order) {
    queryParams.orderByColumn = column.prop
    queryParams.isAsc = column.order === 'ascending' ? 'asc' : 'desc'
  } else {
    queryParams.orderByColumn = undefined
    queryParams.isAsc = undefined
  }
  getList()
}

// 新增按钮操作
const handleAdd = () => {
  resetForm()
  open.value = true
  title.value = '新增技术图纸'
}

// 修改按钮操作
const handleUpdate = (row?: any) => {
  resetForm()
  const drawingId = row?.drawingId || ids.value[0]
  // 这里应该调用获取图纸详情API
  // 暂时使用模拟数据
  setTimeout(() => {
    Object.assign(form, {
      drawingId: drawingId,
      drawingNo: 'DRW001',
      drawingName: '产品A装配图',
      productId: 1,
      productCode: 'P001',
      productName: '产品A',
      drawingType: 'assembly',
      version: '1.0',
      fileName: 'assembly_drw001.pdf',
      fileSize: 2048000,
      fileFormat: 'PDF',
      remark: '测试图纸',
    })
    open.value = true
    title.value = '修改技术图纸'
  }, 100)
}

// 删除按钮操作
const handleDelete = (row?: any) => {
  const drawingIds = row?.drawingId || ids.value[0]
  ElMessageBox.confirm('是否确认删除图纸编号为"' + drawingIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 这里应该调用删除图纸API
      return Promise.resolve()
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有图纸数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 这里应该调用导出图纸API
      return Promise.resolve()
    })
    .then(() => {
      ElMessage.success('导出成功')
    })
    .catch(() => {})
}

// 上传图纸按钮操作
const handleUpload = (row?: any) => {
  const drawingId = row?.drawingId || ids.value[0]
  ElMessageBox.confirm('是否确认上传图纸文件？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      // 这里应该调用上传图纸API
      return Promise.resolve()
    })
    .then(() => {
      getList()
      ElMessage.success('上传成功')
    })
    .catch(() => {})
}

// 下载图纸按钮操作
const handleDownload = (row?: any) => {
  const drawingId = row?.drawingId || ids.value[0]
  ElMessageBox.confirm('是否确认下载图纸文件？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      // 这里应该调用下载图纸API
      return Promise.resolve()
    })
    .then(() => {
      ElMessage.success('下载成功')
    })
    .catch(() => {})
}

// 查看详情按钮操作
const handleView = (row: any) => {
  const drawingId = row.drawingId
  // 这里应该调用获取图纸详情API
  // 暂时使用模拟数据
  setTimeout(() => {
    Object.assign(detail, {
      drawingId: drawingId,
      drawingNo: 'DRW001',
      drawingName: '产品A装配图',
      productId: 1,
      productCode: 'P001',
      productName: '产品A',
      drawingType: 'assembly',
      version: '1.0',
      fileName: 'assembly_drw001.pdf',
      fileSize: 2048000,
      fileFormat: 'PDF',
      filePath: '/uploads/drawings/assembly_drw001.pdf',
      remark: '测试图纸',
      createTime: '2024-01-15 10:30:00',
      updateTime: '2024-01-15 10:30:00',
      createBy: 'admin',
      updateBy: 'admin',
    })
    detailOpen.value = true
  }, 100)
}

// 下载图纸按钮操作
const handleDownloadDrawing = (row: any) => {
  const drawingId = row.drawingId
  ElMessageBox.confirm('是否确认下载此图纸文件？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      // 这里应该调用下载图纸API
      return Promise.resolve()
    })
    .then(() => {
      ElMessage.success('下载成功')
    })
    .catch(() => {})
}

// 预览图纸按钮操作
const handlePreview = (row: any) => {
  const drawingId = row.drawingId
  const fileFormat = row.fileFormat.toLowerCase()

  // 根据文件格式确定预览类型
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp'].includes(fileFormat)) {
    previewType.value = 'image'
    previewUrl.value = '/api/drawing/preview/' + drawingId
  } else if (fileFormat === 'pdf') {
    previewType.value = 'pdf'
    previewUrl.value = '/api/drawing/preview/' + drawingId
  } else {
    previewType.value = 'unsupported'
  }

  previewOpen.value = true
}

// 搜索产品
const searchProduct = (query: string) => {
  if (query) {
    productLoading.value = true
    // 这里应该调用产品搜索API
    setTimeout(() => {
      productOptions.value = [
        { productId: 1, productCode: 'P001', productName: '产品A' },
        { productId: 2, productCode: 'P002', productName: '产品B' },
        { productId: 3, productCode: 'P003', productName: '产品C' },
      ].filter((item) => item.productCode.includes(query) || item.productName.includes(query))
      productLoading.value = false
    }, 300)
  } else {
    productOptions.value = []
  }
}

// 处理产品选择变化
const handleProductChange = (productId: number) => {
  const selectedProduct = productOptions.value.find((item) => item.productId === productId)
  if (selectedProduct) {
    form.productCode = selectedProduct.productCode
    form.productName = selectedProduct.productName
  }
}

// 处理文件选择变化
const handleFileChange = (file: any) => {
  form.file = file.raw
  form.fileName = file.name
  form.fileSize = file.size
  form.fileFormat = file.name.split('.').pop()?.toUpperCase() || ''
}

// 表单重置
const resetForm = () => {
  if (drawingFormRef.value) {
    drawingFormRef.value.resetFields()
  }
  Object.assign(form, {
    drawingId: undefined,
    drawingNo: '',
    drawingName: '',
    productId: 0,
    productCode: '',
    productName: '',
    drawingType: '2d',
    version: '1.0',
    fileName: '',
    fileSize: 0,
    fileFormat: '',
    filePath: '',
    remark: '',
    file: null,
  })
}

// 提交表单
const submitForm = () => {
  if (!drawingFormRef.value) return

  drawingFormRef.value.validate((valid) => {
    if (valid) {
      if (form.drawingId !== undefined) {
        // 这里应该调用修改图纸API
        setTimeout(() => {
          ElMessage.success('修改成功')
          open.value = false
          getList()
        }, 500)
      } else {
        // 这里应该调用新增图纸API
        setTimeout(() => {
          ElMessage.success('新增成功')
          open.value = false
          getList()
        }, 500)
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  open.value = false
  resetForm()
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.file-info {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding: 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.file-info .el-icon {
  margin-right: 8px;
  color: #409eff;
}

.file-info .file-size {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

.preview-container {
  min-height: 400px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.image-preview {
  max-width: 100%;
  text-align: center;
}

.pdf-preview {
  width: 100%;
  height: 600px;
}

.unsupported-preview {
  width: 100%;
}
</style>
