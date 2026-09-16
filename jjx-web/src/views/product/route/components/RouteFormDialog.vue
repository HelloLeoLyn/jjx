<template>
  <el-dialog
    v-model="visible"
    width="1400px"
    append-to-body
    :fullscreen="isFullscreen"
    destroy-on-close
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ title }}</span>
        <el-button text @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span style="margin-left: 4px">{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </el-button>
      </div>
    </template>

    <div v-loading="loading">
      <div v-if="isEdit" class="version-badge">
        当前版本：<el-tag size="small" type="primary">{{ displayVersion }}</el-tag>
        <el-tag v-if="formData.sourceSampleId" size="small" type="info" style="margin-left: 6px">
          来源打样单 #{{ formData.sourceSampleId }}
        </el-tag>
        <el-tag
          v-if="formData.parentRoutingId"
          size="small"
          type="warning"
          style="margin-left: 6px"
        >
          升版自 V{{ parentVersionHint }}
        </el-tag>
      </div>

      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-input
                v-if="isEdit"
                v-model="formData.productId"
                placeholder="请选择产品"
                readonly
              />
              <ProductSelector
                v-else
                v-model="selectedProduct"
                value-type="object"
                status-scope="active"
                @change="handleProductChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路线编码" prop="routingCode">
              <el-input v-model="formData.routingCode" placeholder="请输入路线编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="formData.routingName" placeholder="请输入路线名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="routingVersion">
              <el-input v-model="formData.routingVersion" placeholder="请输入版本号，如 V1.0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路线说明" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="2"
                placeholder="请输入路线说明"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="formData.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert
        v-if="isEdit && hasChanges"
        type="warning"
        show-icon
        :closable="false"
        :title="`检测到工序内容变更，保存时将自动升级版本（${displayVersion} → ${nextVersionHint}），旧版本将失效`"
        style="margin-bottom: 10px"
      />
      <div v-if="isEdit && hasChanges" style="margin-bottom: 10px">
        <el-input
          v-model="changeNote"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="变更说明（可选，自动记录到新版本备注，如：增加面板丝印工序 / 调整工时）"
        />
      </div>
      <RouteItemIconEditor
        ref="editorRef"
        :model-value="formData.items"
        :standard-processes="standardProcesses"
        modern-operation
        @update:model-value="handleItemsUpdate"
      />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确 定</el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { FullScreen } from '@element-plus/icons-vue'
import { productRouteApi } from '@/api/product/routing'
import { RouteStatusEnum } from '@/enums/product'
import type { ProductItem, StandardProcessOption } from '@/types/product'
import type {
  ProductRouteFormData,
  EngineeringRoutingItemDTO,
  EngineeringRoutingItemVO,
} from '@/types/product/routing'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import RouteItemIconEditor from './RouteItemIconEditor.vue'

interface Props {
  modelValue: boolean
  routingId?: number
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  routingId: undefined,
})
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const isEdit = computed(() => props.routingId !== undefined)
const title = computed(() => (isEdit.value ? '修改工艺路线' : '新增工艺路线'))
const isFullscreen = ref(false)
const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const editorRef = ref<InstanceType<typeof RouteItemIconEditor>>()
const standardProcesses = ref<StandardProcessOption[]>([])
const selectedProduct = ref<ProductItem | null>(null)
const changeNote = ref('')
const hasChanges = ref(false)
const currentApproveStatus = ref<number>()
let initialItemsSnapshot = ''

// RouteStatusEnum 是位置式枚举（无具名成员），按 label 取「已批准」的值，避免直接写数字（AGENTS.md 状态枚举条款）
const APPROVED_ROUTE_STATUS = RouteStatusEnum.items.find((item) => item.label === '已批准')?.value

const formData = reactive<ProductRouteFormData>({
  routingCode: '',
  routingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  routingVersion: '',
  description: '',
  remark: '',
  items: [],
})

const rules = reactive<FormRules<ProductRouteFormData>>({
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  routingCode: [{ required: true, message: '请输入路线编码', trigger: 'blur' }],
  routingName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  routingVersion: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
})

const displayVersion = computed(() => formData.version || formData.routingVersion || '-')
const parentVersionHint = computed(() => '上一版')
const nextVersionHint = computed(() => {
  const matched = displayVersion.value.match(/V?(\d+)/)
  return matched ? `V${Number(matched[1]) + 1}.0` : 'V2.0'
})

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

function printNameFromParams(json?: string): string {
  if (!json) return ''
  try {
    return JSON.parse(json).printName || ''
  } catch {
    return ''
  }
}

function mapRouteItem(item: any): any {
  return {
    itemId: item.itemId || 0,
    routingId: item.routingId || 0,
    parentId: item.parentId ?? null,
    groupId: item.groupId || undefined,
    groupOrder: item.groupOrder || 0,
    groupName: item.groupName || '',
    processId: item.processId ?? undefined,
    processOrder: item.processOrder || 0,
    customLaborHours: item.customLaborHours || 0,
    customMachineHours: item.customMachineHours || 0,
    customProcessParams: item.customProcessParams || '',
    description: item.description || '',
    processCategory: item.processCategory || '',
    majorCategory: item.majorCategory || 'ASSEMBLY',
    processName:
      item.processName ||
      (item.majorCategory === 'PRINT' ? printNameFromParams(item.customProcessParams) : '') ||
      '',
    processCode: item.processCode || '',
    icon: item.icon || '',
    hasIndex: item.hasIndex ?? 0,
    hasWorkInstruction: item.hasWorkInstruction ?? 0,
    indexNumber: item.indexNumber ?? null,
    children: (item.children || []).map(mapRouteItem),
  }
}

function snapshotItems(items: any[]): string {
  return JSON.stringify(
    (items || []).map((item) => ({
      processId: item.processId,
      stdProcessId: item.stdProcessId,
      processName: item.processName,
      processCategory: item.processCategory,
      processOrder: item.processOrder,
      customLaborHours: item.customLaborHours,
      customMachineHours: item.customMachineHours,
      isOptional: item.isOptional,
      indexNumber: item.indexNumber,
      children: (item.children || []).map((child: any) => ({
        processId: child.processId,
        processName: child.processName,
        customLaborHours: child.customLaborHours,
        customMachineHours: child.customMachineHours,
      })),
    }))
  )
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    routingId: undefined,
    routingCode: '',
    routingName: '',
    productId: 0,
    productCode: '',
    productName: '',
    routingVersion: '',
    version: undefined,
    parentRoutingId: undefined,
    sourceSampleId: undefined,
    description: '',
    remark: '',
    items: [],
  })
  selectedProduct.value = null
  currentApproveStatus.value = undefined
  initialItemsSnapshot = ''
  hasChanges.value = false
  changeNote.value = ''
  editorRef.value?.setItems([])
}

const handleProductChange = (_value: any, product: ProductItem | null) => {
  if (product) {
    formData.productId = product.productId
    formData.productCode = product.productCode
    formData.productName = product.productName
    formData.routingCode = product.productCode + '-ROUTING'
    formData.routingName = product.productCode + '工艺路线'
    formData.routingVersion = 'V1.0'
  } else {
    formData.productId = 0
    formData.productCode = ''
    formData.productName = ''
    formData.routingCode = ''
    formData.routingName = ''
  }
}

const loadStandardProcesses = async () => {
  try {
    const response = await productRouteApi.getEnabledProcesses()
    standardProcesses.value = response.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

const loadRouteDetail = async () => {
  if (props.routingId === undefined) return
  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(props.routingId)
    const detail = response.data
    if (!detail) {
      ElMessage.error('加载工艺路线详情失败')
      return
    }
    const items = (detail.items || []).map(mapRouteItem)
    Object.assign(formData, {
      routingId: detail.routingId,
      routingCode: detail.routingCode,
      routingName: detail.routingName,
      productId: detail.productId,
      productCode: detail.productCode,
      productName: detail.productName,
      routingVersion: detail.routingVersion,
      version: detail.version,
      parentRoutingId: detail.parentRoutingId,
      sourceSampleId: detail.sourceSampleId,
      description: detail.description,
      remark: detail.remark,
      items,
    })
    currentApproveStatus.value = detail.approveStatus
    changeNote.value = ''
    await nextTick()
    editorRef.value?.setItems(items as EngineeringRoutingItemVO[])
    await nextTick()
    initialItemsSnapshot = snapshotItems(editorRef.value?.getItems() || [])
    hasChanges.value = false
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
    ElMessage.error('加载工艺路线详情失败')
  } finally {
    loading.value = false
  }
}

const handleItemsUpdate = (items: EngineeringRoutingItemVO[]) => {
  formData.items = items
  if (isEdit.value && initialItemsSnapshot) {
    hasChanges.value = snapshotItems(items) !== initialItemsSnapshot
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    const items = editorRef.value?.getItems() || []
    if (!items.length) {
      ElMessage.warning('请至少添加一道工序')
      return
    }
    submitLoading.value = true
    if (isEdit.value && props.routingId !== undefined) {
      const changed = snapshotItems(items) !== initialItemsSnapshot
      const isApproved = currentApproveStatus.value === APPROVED_ROUTE_STATUS
      const payload: any = { ...formData, items, bumpVersion: changed && isApproved }
      if (changed && isApproved) payload.changeNote = changeNote.value.trim()
      const res = await productRouteApi.editProductRoute(props.routingId, payload)
      if (payload.bumpVersion === true) {
        ElMessage.success(
          `保存成功，已升级为 ${res?.data?.version || res?.data?.routingVersion || ''}（旧版本失效）`
        )
      } else {
        ElMessage.success('修改成功')
      }
    } else {
      const itemDTOs: EngineeringRoutingItemDTO[] = items.map((item: EngineeringRoutingItemVO) => ({
        itemId: item.itemId,
        routingId: item.routingId,
        groupId: item.groupId,
        groupOrder: item.groupOrder,
        groupName: item.groupName,
        processId: item.processId,
        processOrder: item.processOrder,
        customLaborHours: item.customLaborHours,
        customMachineHours: item.customMachineHours,
        customProcessParams: item.customProcessParams,
        description: item.description,
        processCategory: item.processCategory,
        majorCategory: item.majorCategory,
      }))
      await productRouteApi.addProductRoute({ ...formData, items: itemDTOs as any })
      ElMessage.success('新增成功')
    }
    emit('success')
    visible.value = false
  } catch (error) {
    console.error(isEdit.value ? '修改工艺路线失败:' : '新增工艺路线失败:', error)
  } finally {
    submitLoading.value = false
  }
}

watch(
  () => props.modelValue,
  async (opened) => {
    if (!opened) return
    resetForm()
    await loadStandardProcesses()
    if (isEdit.value) await loadRouteDetail()
  }
)
</script>

<style scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
}

.dialog-footer {
  text-align: right;
}

.version-badge {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
}
</style>
