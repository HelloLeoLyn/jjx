<template>
  <el-dialog
    :title="`字典项管理 - ${dictName}`"
    v-model="visible"
    width="800px"
    append-to-body
    :before-close="handleCancel"
  >
    <el-alert
      v-if="readonly"
      :title="readonlyTip"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />
    <!-- 工具栏 -->
    <div style="margin-bottom: 16px">
      <el-tooltip :content="readonlyTip" :disabled="!readonly">
        <span><el-button type="primary" icon="Plus" :disabled="readonly" @click="handleAdd" v-hasPermi="['system:dict:add']">新增字典项</el-button></span>
      </el-tooltip>
    </div>

    <!-- 字典项表格 -->
    <el-table :data="itemList" border style="width: 100%" v-loading="loading">
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="字典键" prop="itemKey" width="150" />
      <el-table-column label="字典值" prop="itemValue" width="180" />
      <el-table-column label="标签" prop="label" width="100" />
      <el-table-column v-if="isAttachmentType" label="一级分类" width="120">
        <template #default="{ row }">{{ extSummary(row).parent }}</template>
      </el-table-column>
      <el-table-column v-if="isAttachmentType" label="来源(sources)" min-width="200">
        <template #default="{ row }">{{ extSummary(row).sources }}</template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="状态" prop="isActive" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isActive === 1 ? 'success' : 'danger'" size="small">
            {{ row.isActive === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="readonly" @click="handleEdit(row)" v-hasPermi="['system:dict:edit']">
            修改
          </el-button>
          <el-button
            link
            :type="row.isActive === 1 ? 'warning' : 'success'"
            :disabled="readonly"
            @click="handleToggleStatus(row)"
            v-hasPermi="['system:dict:edit']"
          >
            {{ row.isActive === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="readonly"
            @click="handleDelete(row)"
            v-hasPermi="['system:dict:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 字典项表单对话框 -->
    <el-dialog :title="itemDialogTitle" v-model="itemDialogVisible" width="800px" append-to-body>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item label="字典键" prop="itemKey">
          <el-input
            v-model="itemForm.itemKey"
            placeholder="请输入字典键(实际存储值)"
            maxlength="50"
            :disabled="!!itemForm.itemId"
          />
        </el-form-item>
        <el-form-item label="字典值" prop="itemValue">
          <el-input
            v-model="itemForm.itemValue"
            placeholder="请输入字典值(显示文本)"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="标签" prop="label">
          <el-input v-model="itemForm.label" placeholder="请输入标签(扩展)" maxlength="50" />
        </el-form-item>
        <template v-if="isAttachmentType">
          <el-form-item label="一级分类" required>
            <el-select v-model="extParent" placeholder="请选择一级分类(parent)" style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源" required>
            <el-select
              v-model="extSources"
              multiple
              placeholder="请选择可用来源(sources)"
              style="width: 100%"
            >
              <el-option v-for="s in ATTACHMENT_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="isActive">
          <el-radio-group v-model="itemForm.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="itemForm.remark"
            type="textarea"
            placeholder="请输入备注"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitItemForm" :loading="itemSubmitLoading">
            确 定
          </el-button>
          <el-button @click="itemDialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { dictApi } from '@/api/system/dict'
import type { SysDictItem, SysDictItemDTO } from '@/types/system/dict'

// 组件属性
interface Props {
  visible: boolean
  dictCode: string
  dictName: string
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  dictCode: '',
  dictName: '',
  readonly: false,
})

const readonlyTip = '由后端枚举自动导入，页面显示以代码枚举为准，此处仅供查看'

// 组件事件
const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

// 响应式数据
const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const loading = ref(false)
const itemList = ref<SysDictItem[]>([])

// ===== 附件资料类型字典：ext_data(parent + sources) 结构化编辑（dev-20261013-007） =====
/** 仅 attachment_document_type 使用 ext_data；其它字典项不显示 ext_data（避免裸编 JSON 写坏） */
const isAttachmentType = computed(() => props.dictCode === 'attachment_document_type')
/** 可选来源（与后端 AttachmentSourcePolicy 白名单对齐） */
const ATTACHMENT_SOURCES = [
  { value: 'product', label: '产品' },
  { value: 'inquiry', label: '询价单' },
  { value: 'quotation', label: '报价单' },
  { value: 'sales_order', label: '销售订单' },
  { value: 'sample', label: '样品单' },
  { value: 'purchase_order', label: '采购订单' },
  { value: 'purchase_payment', label: '付款凭证' },
  { value: 'production_order', label: '生产工单' },
  { value: 'material', label: '物料' },
]
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
const extParent = ref('')
const extSources = ref<string[]>([])

async function loadCategoryOptions() {
  if (!isAttachmentType.value || categoryOptions.value.length) return
  try {
    const res = await dictApi.getItems('attachment_category')
    categoryOptions.value = (res.data || [])
      .filter((it: SysDictItem) => it.isActive === 1)
      .map((it: SysDictItem) => ({ value: it.itemKey, label: it.label || it.itemKey }))
  } catch {
    categoryOptions.value = []
  }
}

function parseExtData(ext?: string): { parent: string; sources: string[] } {
  if (!ext) return { parent: '', sources: [] }
  try {
    const o = JSON.parse(ext)
    return {
      parent: typeof o?.parent === 'string' ? o.parent : '',
      sources: Array.isArray(o?.sources) ? o.sources.map((s: any) => String(s)) : [],
    }
  } catch {
    return { parent: '', sources: [] }
  }
}

function extSummary(row: SysDictItem) {
  const { parent, sources } = parseExtData(row.extData)
  const pLabel = categoryOptions.value.find((c) => c.value === parent)?.label || parent || '—'
  const sLabel =
    sources.map((s) => ATTACHMENT_SOURCES.find((o) => o.value === s)?.label || s).join('、') || '—'
  return { parent: pLabel, sources: sLabel }
}

// 字典项表单
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const itemFormRef = ref<FormInstance>()
const itemSubmitLoading = ref(false)
const itemForm = ref<SysDictItemDTO>({
  itemId: undefined,
  dictCode: '',
  itemKey: '',
  itemValue: '',
  label: '',
  remark: '',
  sortOrder: 0,
  isActive: 1,
})

// 表单验证规则
const itemRules: FormRules = {
  itemKey: [
    { required: true, message: '字典键不能为空', trigger: 'blur' },
    { max: 50, message: '字典键长度不能超过50个字符', trigger: 'blur' },
  ],
  itemValue: [
    { required: true, message: '字典值不能为空', trigger: 'blur' },
    { max: 100, message: '字典值长度不能超过100个字符', trigger: 'blur' },
  ],
}

// 获取字典项列表
const getItemList = async () => {
  if (!props.dictCode) return
  loading.value = true
  try {
    const res = await dictApi.getItems(props.dictCode)
    itemList.value = res.data || []
  } finally {
    loading.value = false
  }
}

// 监听对话框打开
watch(visible, (newVal) => {
  if (newVal) {
    getItemList()
    loadCategoryOptions()
  }
})

// 新增字典项
const handleAdd = () => {
  itemDialogTitle.value = '新增字典项'
  itemForm.value = {
    itemId: undefined,
    dictCode: props.dictCode,
    itemKey: '',
    itemValue: '',
    label: '',
    remark: '',
    sortOrder: 0,
    isActive: 1,
  }
  extParent.value = ''
  extSources.value = []
  loadCategoryOptions()
  itemDialogVisible.value = true
}

// 修改字典项
const handleEdit = (row: SysDictItem) => {
  itemDialogTitle.value = '修改字典项'
  itemForm.value = {
    itemId: row.itemId,
    dictCode: row.dictCode,
    itemKey: row.itemKey,
    itemValue: row.itemValue,
    label: row.label,
    remark: row.remark,
    sortOrder: row.sortOrder,
    isActive: row.isActive,
  }
  const { parent, sources } = parseExtData(row.extData)
  extParent.value = parent
  extSources.value = sources
  loadCategoryOptions()
  itemDialogVisible.value = true
}

// 删除字典项
const handleDelete = async (row: SysDictItem) => {
  await ElMessageBox.confirm(`是否确认删除字典项"${row.itemValue}"？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await dictApi.removeItem([row.itemId!])
  ElMessage.success('删除成功')
  getItemList()
}

// 启用/禁用字典项
const handleToggleStatus = async (row: SysDictItem) => {
  const newStatus = row.isActive === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`是否确认${statusText}字典项"${row.itemValue}"？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await dictApi.changeItemStatus(row.itemId!, newStatus)
  ElMessage.success(`${statusText}成功`)
  getItemList()
}

// 提交字典项表单
const submitItemForm = async () => {
  if (!itemFormRef.value) return
  await itemFormRef.value.validate(async (valid) => {
    if (!valid) return
    // 附件资料类型：ext_data(parent+sources) 必填校验（与后端 AttachmentDocumentTypeService 对齐）
    if (isAttachmentType.value) {
      if (!extParent.value) {
        ElMessage.warning('请选择一级分类（parent）')
        return
      }
      if (!extSources.value.length) {
        ElMessage.warning('请至少选择一个来源（sources）')
        return
      }
      itemForm.value.extData = JSON.stringify({
        parent: extParent.value,
        sources: extSources.value,
      })
    }
    itemSubmitLoading.value = true
    try {
      if (itemForm.value.itemId) {
        await dictApi.updateItem(itemForm.value.itemId, itemForm.value)
        ElMessage.success('修改成功')
      } else {
        await dictApi.addItem(itemForm.value)
        ElMessage.success('新增成功')
      }
      itemDialogVisible.value = false
      getItemList()
    } finally {
      itemSubmitLoading.value = false
    }
  })
}

// 取消操作
const handleCancel = () => {
  visible.value = false
}
</script>
