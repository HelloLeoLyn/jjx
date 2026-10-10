<template>
  <el-dialog
    v-model="visible"
    width="1400px"
    append-to-body
    :fullscreen="isFullscreen"
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
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
        <el-button type="primary" :loading="submitLoading" @click="handleSave">保 存</el-button>
        <el-button :loading="submitLoading" @click="handleSaveAndClose">保存并关闭</el-button>
        <el-button @click="handleCloseClick">关 闭</el-button>
        <el-tag v-if="isDirty" type="warning" size="small" effect="plain" style="margin-left: 8px">
          未保存
        </el-tag>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { FullScreen } from '@element-plus/icons-vue'
import { productRouteApi } from '@/api/product/routing'
import { RouteStatusEnum } from '@/enums/product'
import type { ProductItem, StandardProcessOption } from '@/types/product'
import type { ProductRouteFormData, EngineeringRoutingItemVO } from '@/types/product/routing'
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
const isEdit = computed(() => activeRoutingId.value !== undefined)
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
// 未保存检测：完整表单基线（基本信息 + 整棵工序树）。dev-20261010-009
const activeRoutingId = ref<number | undefined>(props.routingId)
const baseline = ref('')
const isDirty = ref(false)
// 内部主动关闭时置位，跳过离开拦截
const suppressGuard = ref(false)

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

/** 空白字符串归一为空串（mapRouteItem / snapshotItems 共用）。dev-20261010-008 */
const text = (value?: string) => (value?.trim() ? value : '')

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
    description: text(item.description),
    remark: text(item.remark),
    workInstruction: item.workInstruction ?? null,
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
    precondition: item.precondition ?? null,
    preconditionDisplay: item.preconditionDisplay ?? null,
    isOptional: item.isOptional,
    children: (item.children || []).map(mapRouteItem),
  }
}

// JSON按字段排序，避免仅格式或键顺序变化被当作工艺内容修改。
function snapshotProcessParams(value?: string): unknown {
  if (!value?.trim()) return null
  try {
    const sortValue = (node: any): any => {
      if (Array.isArray(node)) return node.map(sortValue)
      if (node && typeof node === 'object') {
        return Object.fromEntries(
          Object.keys(node).sort().map((key) => [key, sortValue(node[key])])
        )
      }
      return node
    }
    const parsed = JSON.parse(value)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const meaningful = Object.fromEntries(
        Object.entries(parsed).filter(([, v]) => v != null && !(typeof v === 'string' && !v.trim()))
      )
      return Object.keys(meaningful).length ? sortValue(meaningful) : null
    }
    return sortValue(parsed)
  } catch {
    return value
  }
}

function snapshotItems(items: any[]): string {
  const snapshotItem = (item: any): any => ({
    processId: item.processId,
    stdProcessId: item.stdProcessId,
    processName: text(item.processName),
    majorCategory: item.majorCategory,
    processCategory: item.processCategory,
    processOrder: item.processOrder,
    customLaborHours: Number(item.customLaborHours) || 0,
    customMachineHours: Number(item.customMachineHours) || 0,
    customProcessParams: snapshotProcessParams(item.customProcessParams),
    description: item.description || '',
    remark: item.remark || '',
    workInstruction: text(item.workInstruction),
    isOptional: item.isOptional,
    indexNumber: item.indexNumber,
    precondition: text(item.precondition),
    preconditionDisplay: text(item.preconditionDisplay),
    children: (item.children || []).map(snapshotItem),
  })
  return JSON.stringify((items || []).map(snapshotItem))
}

// —— 未保存状态：完整表单快照（基本信息 + 整棵工序树）dev-20261010-009 ——
const fullFormSnapshot = (): string =>
  JSON.stringify({
    productId: formData.productId,
    routingCode: text(formData.routingCode),
    routingName: text(formData.routingName),
    routingVersion: text(formData.routingVersion),
    description: text(formData.description),
    remark: text(formData.remark),
    items: snapshotItems(editorRef.value?.getItems() || formData.items || []),
  })

const setBaseline = () => {
  baseline.value = fullFormSnapshot()
  isDirty.value = false
}

const recomputeDirty = () => {
  if (!baseline.value) {
    isDirty.value = false
    return
  }
  isDirty.value = fullFormSnapshot() !== baseline.value
}

// 表单（含基本信息）任意变更都重算未保存状态；工序变更由 handleItemsUpdate 额外触发。
watch(formData, recomputeDirty, { deep: true })

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
  baseline.value = ''
  isDirty.value = false
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

const loadRouteDetail = async (silentError = false) => {
  const rid = activeRoutingId.value
  if (rid === undefined) return
  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(rid)
    const detail = response.data
    if (!detail) {
      if (!silentError) ElMessage.error('加载工艺路线详情失败')
      throw new Error('工艺路线详情为空')
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
    activeRoutingId.value = detail.routingId ?? rid
    currentApproveStatus.value = detail.approveStatus
    changeNote.value = ''
    await nextTick()
    editorRef.value?.setItems(items as EngineeringRoutingItemVO[])
    await nextTick()
    initialItemsSnapshot = snapshotItems(editorRef.value?.getItems() || [])
    hasChanges.value = false
    setBaseline()
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
    if (!silentError) ElMessage.error('加载工艺路线详情失败')
    throw error
  } finally {
    loading.value = false
  }
}

const handleItemsUpdate = (items: EngineeringRoutingItemVO[]) => {
  formData.items = items
  if (isEdit.value && initialItemsSnapshot) {
    hasChanges.value = snapshotItems(items) !== initialItemsSnapshot
  }
  recomputeDirty()
}

const toItemDTO = (item: EngineeringRoutingItemVO) => ({
  itemId: item.itemId,
  routingId: item.routingId,
  groupId: item.groupId ?? undefined,
  groupOrder: item.groupOrder ?? undefined,
  groupName: item.groupName ?? undefined,
  processId: item.processId,
  processOrder: item.processOrder,
  customLaborHours: item.customLaborHours,
  customMachineHours: item.customMachineHours,
  customProcessParams: item.customProcessParams,
  description: item.description,
  remark: item.remark,
  processCategory: item.processCategory,
  majorCategory: item.majorCategory,
  children: item.children,
})

/** 校验 + 保存；成功返回 true。不负责关闭弹窗（关闭由调用方按需执行）。 */
const doSave = async (): Promise<boolean> => {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
  } catch {
    return false
  }
  const items = editorRef.value?.getItems() || []
  if (!items.length) {
    ElMessage.warning('请至少添加一道工序')
    return false
  }
  submitLoading.value = true
  try {
    if (activeRoutingId.value !== undefined) {
      const changed = snapshotItems(items) !== initialItemsSnapshot
      const isApproved = currentApproveStatus.value === APPROVED_ROUTE_STATUS
      const payload: any = {
        ...formData,
        routingId: activeRoutingId.value,
        items,
        bumpVersion: changed && isApproved,
      }
      if (changed && isApproved) payload.changeNote = changeNote.value.trim()
      const res = await productRouteApi.editProductRoute(activeRoutingId.value, payload)
      // 升版保存会新建版本，以接口返回的真实ID为准（否则后续会改错对象）
      const returnedId = res?.data?.routingId
      if (returnedId !== undefined && returnedId !== null) activeRoutingId.value = returnedId
      if (payload.bumpVersion === true) {
        ElMessage.success(
          `保存成功，已升级为 ${res?.data?.version || res?.data?.routingVersion || ''}（旧版本失效）`
        )
      } else {
        ElMessage.success('保存成功')
      }
    } else {
      const res = await productRouteApi.addProductRoute({
        ...formData,
        items: items.map(toItemDTO) as any,
      })
      const newId = res?.data?.routingId
      ElMessage.success('新增成功')
      if (newId === undefined || newId === null) {
        // 未拿到真实ID：关闭并刷新，避免继续以“新增”身份重复建单
        emit('success')
        suppressGuard.value = true
        visible.value = false
        return true
      }
      // 新增成功后切换为修改该路线（后续保存改同一条，不再重复新增）
      activeRoutingId.value = newId
    }
    // 保存后同步：以真实ID回读最新详情（真实父子明细ID/版本），重设基线
    await reloadDetailAfterSave()
    emit('success')
    return true
  } catch (error) {
    console.error(isEdit.value ? '修改工艺路线失败:' : '新增工艺路线失败:', error)
    ElMessage.error(isEdit.value ? '保存失败，请检查后重试' : '新增失败，请检查后重试')
    return false
  } finally {
    submitLoading.value = false
  }
}

/** 保存成功后回读详情，取得真实父子明细ID与版本并重设基线 */
const reloadDetailAfterSave = async () => {
  try {
    await loadRouteDetail(true)
  } catch {
    ElMessage.warning('已保存，但重新加载失败，请关闭后重新打开确认')
  }
}

/** 未保存离开拦截：返回 true=允许离开，false=留在页面 */
const resolveLeave = async (): Promise<boolean> => {
  if (suppressGuard.value) return true
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('当前工艺路线有未保存的修改。', '未保存提醒', {
      distinguishCancelAndClose: true,
      confirmButtonText: '保存并离开',
      cancelButtonText: '放弃修改',
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
    })
    // 保存并离开：保存成功才离开；校验不过/失败则留在页面
    return await doSave()
  } catch (action) {
    // 放弃修改 → 离开；X / Esc（close）→ 继续编辑
    return action === 'cancel'
  }
}

/** 弹窗关闭前拦截（× / Esc 触发） */
const handleBeforeClose = async (done: () => void) => {
  if (submitLoading.value) return
  if (await resolveLeave()) {
    suppressGuard.value = true
    done()
  }
}

const handleSave = () => {
  void doSave()
}

const handleSaveAndClose = async () => {
  const ok = await doSave()
  if (ok) {
    suppressGuard.value = true
    visible.value = false
  }
}

const handleCloseClick = async () => {
  if (submitLoading.value) return
  if (await resolveLeave()) {
    suppressGuard.value = true
    visible.value = false
  }
}

/** 浏览器刷新/关闭标签页原生提醒（仅确有未保存修改时） */
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (props.modelValue && isDirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

// 供父级路由离开保护使用
defineExpose({
  formIsDirty: () => isDirty.value,
  resolveLeave,
})

watch(
  () => props.modelValue,
  async (opened) => {
    if (!opened) return
    activeRoutingId.value = props.routingId
    suppressGuard.value = false
    resetForm()
    await loadStandardProcesses()
    if (isEdit.value) {
      try {
        await loadRouteDetail()
      } catch {
        // 加载失败已在 loadRouteDetail 内提示
      }
    } else {
      await nextTick()
      setBaseline()
    }
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
