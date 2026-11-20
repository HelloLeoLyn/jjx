<template>
  <el-dialog v-model="visible" title="物料详情" width="min(1000px, calc(100vw - 24px))" append-to-body>
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="material">
      <el-descriptions title="基本信息" :column="2" border size="small">
        <el-descriptions-item label="物料编码">{{
          text(material.materialCode)
        }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{
          text(material.materialName)
        }}</el-descriptions-item>
        <el-descriptions-item label="主要供应商">{{ text(material.supplierName) }}</el-descriptions-item>
        <el-descriptions-item label="品类">{{ categoryName }}</el-descriptions-item>
        <el-descriptions-item label="大类">{{ materialTypeText }}</el-descriptions-item>
        <el-descriptions-item label="材料">{{ text(material.materialGrade) }}</el-descriptions-item>
        <el-descriptions-item label="材质">{{
          dictionaryText('material_meta', material.materialMeta)
        }}</el-descriptions-item>
        <el-descriptions-item label="形态">{{
          dictionaryText('form', material.materialForm)
        }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ text(material.model) }}</el-descriptions-item>
        <el-descriptions-item label="颜色">{{
          dictionaryText('color', material.color)
        }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{
          dictionaryText('brand', material.brand)
        }}</el-descriptions-item>
        <el-descriptions-item label="别名">{{
          text(material.searchKeywords)
        }}</el-descriptions-item>
        <el-descriptions-item label="尺寸">{{ dimensionText }}</el-descriptions-item>
        <el-descriptions-item label="规格" :span="2">{{
          text(material.specification)
        }}</el-descriptions-item>
        <el-descriptions-item label="实物单位">{{ measurementPolicy?.physicalUnit || '未配置' }}</el-descriptions-item>
        <el-descriptions-item label="采购（计价）单位">{{ measurementPolicy?.priceUnit || '未配置' }}</el-descriptions-item>
        <el-descriptions-item label="换算方式" :span="2">{{ measurementPolicy ? MeasurementModeEnum.getLabel(measurementPolicy.mode) : '未配置（现有库存单位：' + text(material.unit) + '）' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag.type" size="small">{{ statusTag.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="批次管理">{{
          material.batchControl ? '启用' : '禁用'
        }}</el-descriptions-item>
        <el-descriptions-item label="客供专用">{{
          material.isCustomerSupplied ? '是' : '否'
        }}</el-descriptions-item>
        <el-descriptions-item label="样品专用">{{
          material.isSample ? '是' : '否'
        }}</el-descriptions-item>
        <el-descriptions-item label="物料标签" :span="2">
          <template v-if="material.tags?.length">
            <el-tag v-for="tag in material.tags" :key="tag.tagId" size="small" class="detail-tag">
              {{ tag.tagName }}
            </el-tag>
          </template>
          <template v-else>-</template>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="备注 / 说明" :column="1" border size="small" class="detail-block">
        <el-descriptions-item label="物料备注及迁移来源"><div class="remark-text" data-test="material-remark">{{ text(material.remark) }}</div></el-descriptions-item>
      </el-descriptions>

      <el-descriptions
        v-if="attributeRows.length"
        title="品类属性"
        :column="2"
        border
        size="small"
        class="detail-block"
      >
        <el-descriptions-item v-for="row in attributeRows" :key="row.key" :label="row.label">{{
          row.value
        }}</el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="stockError" :title="stockError" type="warning" :closable="false" class="detail-block" />
      <el-descriptions v-if="canViewStock" title="库存（全部仓库）" :column="3" border size="small" class="detail-block">
        <el-descriptions-item label="当前库存">{{ liveQuantity(stock?.physicalQuantity ?? stock?.totalQuantity, stock?.physicalQuantity == null ? stock?.unit : stock?.physicalUnit) }}</el-descriptions-item>
        <el-descriptions-item label="可用库存">{{ liveQuantity(stock?.availablePhysicalQuantity ?? stock?.availableQuantity, stock?.availablePhysicalQuantity == null ? stock?.unit : stock?.physicalUnit) }}</el-descriptions-item>
        <el-descriptions-item label="预留库存">{{ liveQuantity(stock?.reservedPhysicalQuantity ?? stock?.totalReserved, stock?.reservedPhysicalQuantity == null ? stock?.unit : stock?.physicalUnit) }}</el-descriptions-item>
      </el-descriptions>
      <el-descriptions title="库存设置" :column="3" border size="small" class="detail-block">
        <el-descriptions-item label="在途库存（档案记录）">{{ stockQuantity(material.inTransitStock) }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ stockQuantity(material.safeStock) }}</el-descriptions-item>
        <el-descriptions-item label="最高库存">{{ stockQuantity(material.maxStock) }}</el-descriptions-item>
        <el-descriptions-item label="再订货点">{{
          stockQuantity(material.reorderPoint)
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="采购 / 保质期" :column="3" border size="small" class="detail-block">
        <el-descriptions-item label="标准单价">{{
          money(material.standardPrice)
        }}</el-descriptions-item>
        <el-descriptions-item label="采购周期(天)">{{
          text(material.leadTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="保质期(天)">{{
          text(material.shelfLife)
        }}</el-descriptions-item>
        <el-descriptions-item label="预警提前天数">{{
          text(material.expiryAlertDays)
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="其他" :column="2" border size="small" class="detail-block">
        <el-descriptions-item label="创建人">{{ text(material.createBy) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          text(material.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ text(material.updateBy) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          text(material.updateTime)
        }}</el-descriptions-item>
      </el-descriptions>
    </template>
    <el-empty v-else description="未找到物料信息" />

    <template #footer>
      <el-button v-if="material && canEdit" data-test="edit-material" @click="editVisible = true">编辑物料</el-button>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
  <MaterialMasterFormDialog v-if="editVisible && material && canEdit" v-model="editVisible" :material-id="material.materialId" @success="handleEdited" />
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { stockApi } from '@/api/inventory/stock'
import type { StockVO } from '@/types/inventory/stock'
import { usePermission } from '@/composables/usePermission'
import MaterialMasterFormDialog from './MaterialMasterFormDialog.vue'
import { materialApi } from '@/api/inventory/material'
import { MaterialStatusEnum } from '@/enums/inventory/MaterialEnum'
import {
  loadMaterialCategoryOptions,
  type MaterialCategoryOption,
} from './materialMasterFields'
import type {
  MaterialMasterRecord,
  MaterialMasterDetail,
  MaterialDictionaries,
} from '@/types/inventory/materialMaster'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { previewMasterSpecification, readMeasurementPolicy } from './materialMasterModel'
import { MeasurementModeEnum } from '@/enums/inventory/MeasurementEnum'
import { parseMaterialSize, renderMaterialDimensions } from './materialSize'

interface Props {
  modelValue: boolean
  materialId?: number | null
  allowEdit?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
  allowEdit: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'changed'): void
}>()
const { hasPermission } = usePermission()
const canEdit = computed(() => props.allowEdit && hasPermission('inventory:material:edit'))
const canViewStock = computed(() => hasPermission('inventory:stock:view'))
const editVisible = ref(false)
const stock = ref<StockVO | null>(null)
const stockError = ref('')
const liveQuantity = (value?: number | null, unit?: string | null) => value == null ? '—' : `${num(value)} ${unit || ''}`.trim()
const handleEdited = async () => {
  editVisible.value = false
  emit('changed')
  if (props.modelValue && props.materialId) await loadDetail(props.materialId)
}

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const material = ref<MaterialMasterRecord | null>(null)
const masterDetail = ref<MaterialMasterDetail | null>(null)
const dictionaries = ref<MaterialDictionaries>({})
const stockQuantity = (value?: number | null) => `${num(value)} ${material.value?.unit || ''}`.trim()
const measurementPolicy = computed(() => readMeasurementPolicy(material.value?.measurementPolicyJson))
let detailRequest = 0
onBeforeUnmount(() => { detailRequest++ })
const dictionaryText = (dict: string, value?: string | null) =>
  dictionaries.value[dict]?.find((o) => o.code === value)?.label || text(value)
const attributeRows = computed(() => {
  const detail = masterDetail.value
  if (!detail?.configuration) return []
  return detail.configuration.schema.fields.flatMap((field) => {
    const value = previewMasterSpecification(
      { ...detail.configuration!.schema, fields: [field] },
      detail.attributes,
      ''
    )
    return value
      ? [{ key: field.key, label: field.label, value: value.slice(field.label.length + 1) }]
      : []
  })
})
const dimensionText = computed(() => material.value
  ? renderMaterialDimensions(material.value, parseMaterialSize(material.value.size)?.round === true, masterDetail.value?.configuration?.schema.spec_pattern?.fields || []) || text(material.value.size)
  : '-')
const categoryOptions = ref<MaterialCategoryOption[]>([])

const text = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

const num = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  return Number.isNaN(parsed) ? String(value) : String(parsed)
}

const money = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  return Number.isNaN(parsed) ? String(value) : `¥ ${parsed.toFixed(4)}`
}

const category = computed(
  () => categoryOptions.value.find((item) => item.categoryId === material.value?.categoryId) || null
)

const categoryName = computed(
  () =>
    masterDetail.value?.configuration?.category.pathName ||
    category.value?.categoryName ||
    material.value?.categoryName ||
    '-'
)

const materialTypeText = computed(() =>
  masterDetail.value?.configuration?.path[0]?.categoryName || material.value?.categoryRootName ||
  category.value?.categoryRootName || '未归类'
)

const statusTag = computed(() => {
  const status = material.value?.status as number | string | undefined
  const normalized = typeof status === 'string' ? Number(status) : status
  return {
    label:
      normalized === undefined || Number.isNaN(normalized)
        ? '-'
        : MaterialStatusEnum.getLabel(normalized),
    type: MaterialStatusEnum.getTagProps(normalized as number).type,
  }
})

const loadCategoryOptions = async () => {
  try { categoryOptions.value = await loadMaterialCategoryOptions() }
  catch { categoryOptions.value = [] }
}

const loadDetail = async (materialId: number) => {
  const request = ++detailRequest
  loading.value = true
  editVisible.value = false
  material.value = null
  masterDetail.value = null
  stock.value = null
  stockError.value = ''
  const stockRequest = canViewStock.value
    ? stockApi.getByMaterial(String(materialId)).then(res => {
      if (request === detailRequest) stock.value = res.data || null
    }).catch(() => {
      if (request === detailRequest) stockError.value = '库存数量暂时读取失败，可关闭详情后重试'
    }) : Promise.resolve()
  try {
    const [res, detail, dict] = await Promise.all([
      materialApi.getInfo(String(materialId)),
      materialMasterApi.detail(materialId),
      materialMasterApi.dictionaries(),
    ])
    if (request !== detailRequest) return
    material.value = res.data || null
    masterDetail.value = detail.data || null
    dictionaries.value = dict.data || {}
  } catch (error) {
    if (request === detailRequest) {
      console.error('加载物料详情失败:', error)
      ElMessage.error('加载物料详情失败')
      material.value = null
      masterDetail.value = null
    }
  } finally {
    await stockRequest
    if (request === detailRequest) loading.value = false
  }
}

watch(
  () => [props.modelValue, props.materialId] as const,
  ([isOpen, id]) => {
    if (!isOpen) {
      detailRequest++
      editVisible.value = false
      return
    }
    if (id && Number.isSafeInteger(id) && id > 0) void loadDetail(id)
    else {
      detailRequest++
      material.value = null
      masterDetail.value = null
      stock.value = null
      loading.value = false
    }
    loadCategoryOptions()
  },
  { immediate: true }
)
</script>

<style scoped>
.remark-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.detail-block {
  margin-top: 16px;
}

.detail-tag {
  margin-right: 6px;
}
</style>
