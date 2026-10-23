<template>
  <el-dialog
    v-model="visible"
    title="物料详情"
    width="900px"
    append-to-body
    @opened="handleOpened"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="material">
      <el-descriptions title="基本信息" :column="2" border size="small">
        <el-descriptions-item label="物料编码">{{ text(material.materialCode) }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ text(material.materialName) }}</el-descriptions-item>
        <el-descriptions-item label="品类">{{ categoryName }}</el-descriptions-item>
        <el-descriptions-item label="大类">{{ materialTypeText }}</el-descriptions-item>
        <el-descriptions-item label="材料">{{ text(material.materialGrade) }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ text(material.model) }}</el-descriptions-item>
        <el-descriptions-item label="颜色">{{ text(material.color) }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ text(material.brand) }}</el-descriptions-item>
        <el-descriptions-item label="别名">{{ text(material.searchKeywords) }}</el-descriptions-item>
        <el-descriptions-item label="尺寸">{{ text(material.size) }}</el-descriptions-item>
        <el-descriptions-item label="规格" :span="2">{{ text(material.specification) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ text(material.unit) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag.type" size="small">{{ statusTag.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="机种">{{ text(material.materialNameEn) }}</el-descriptions-item>
        <el-descriptions-item label="批次管理">{{ material.batchControl ? '启用' : '禁用' }}</el-descriptions-item>
        <el-descriptions-item label="物料标签" :span="2">
          <template v-if="material.tags?.length">
            <el-tag v-for="tag in material.tags" :key="tag.tagId" size="small" class="detail-tag">
              {{ tag.tagName }}
            </el-tag>
          </template>
          <template v-else>-</template>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="库存" :column="3" border size="small" class="detail-block">
        <el-descriptions-item label="当前库存">{{ num(material.currentStock) }}</el-descriptions-item>
        <el-descriptions-item label="可用库存">{{ num(material.availableStock) }}</el-descriptions-item>
        <el-descriptions-item label="预留库存">{{ num(material.reservedStock) }}</el-descriptions-item>
        <el-descriptions-item label="在途库存">{{ num(material.inTransitStock) }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ num(material.safeStock) }}</el-descriptions-item>
        <el-descriptions-item label="最高库存">{{ num(material.maxStock) }}</el-descriptions-item>
        <el-descriptions-item label="再订货点">{{ num(material.reorderPoint) }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="采购 / 保质期" :column="3" border size="small" class="detail-block">
        <el-descriptions-item label="标准单价">{{ money(material.standardPrice) }}</el-descriptions-item>
        <el-descriptions-item label="采购周期(天)">{{ text(material.leadTime) }}</el-descriptions-item>
        <el-descriptions-item label="主要供应商">{{ text(material.supplierName) }}</el-descriptions-item>
        <el-descriptions-item label="保质期(天)">{{ text(material.shelfLife) }}</el-descriptions-item>
        <el-descriptions-item label="预警提前天数">{{ text(material.expiryAlertDays) }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="其他" :column="2" border size="small" class="detail-block">
        <el-descriptions-item label="备注" :span="2">{{ text(material.remark) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ text(material.createBy) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ text(material.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ text(material.updateBy) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ text(material.updateTime) }}</el-descriptions-item>
      </el-descriptions>
    </template>
    <el-empty v-else description="未找到物料信息" />

    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { MaterialStatusEnum, MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
import {
  deriveMaterialType,
  loadMaterialCategoryOptions,
  type MaterialCategoryOption,
} from './materialMasterFields'
import type { InventoryMaterial } from '@/types/inventory/material'

interface Props {
  modelValue: boolean
  materialId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
})

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const material = ref<InventoryMaterial | null>(null)
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
  () =>
    categoryOptions.value.find((item) => item.categoryId === material.value?.categoryId) || null
)

const categoryName = computed(
  () => category.value?.categoryName || material.value?.categoryName || '-'
)

const materialTypeText = computed(() => {
  const type = material.value?.materialType || deriveMaterialType(category.value?.categoryCode)
  return type ? `${type} · ${MaterialTypeEnum.getLabel(type)}` : '-'
})

const statusTag = computed(() => {
  const status = material.value?.status as number | string | undefined
  const normalized = typeof status === 'string' ? Number(status) : status
  return {
    label: normalized === undefined || Number.isNaN(normalized) ? '-' : MaterialStatusEnum.getLabel(normalized),
    type: MaterialStatusEnum.getTagProps(normalized as number).type,
  }
})

const loadCategoryOptions = async () => {
  categoryOptions.value = await loadMaterialCategoryOptions()
}

const loadDetail = async (materialId: number) => {
  loading.value = true
  try {
    const res = await materialApi.getInfo(String(materialId))
    material.value = res.data || null
  } catch (error) {
    console.error('加载物料详情失败:', error)
    ElMessage.error('加载物料详情失败')
    material.value = null
  } finally {
    loading.value = false
  }
}

const handleOpened = () => {
  if (props.materialId) loadDetail(props.materialId)
}

watch(
  () => [props.modelValue, props.materialId] as const,
  ([isOpen, id]) => {
    if (!isOpen) return
    if (id) loadDetail(id)
    loadCategoryOptions()
  },
  { immediate: true }
)
</script>

<style scoped>
.detail-block {
  margin-top: 16px;
}

.detail-tag {
  margin-right: 6px;
}
</style>
