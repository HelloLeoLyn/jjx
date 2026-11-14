<template>
  <el-dialog v-model="visible" width="1200" :fullscreen="isFullscreen">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">BOM详情</span>
        <el-button text @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span style="margin-left: 4px">{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </el-button>
      </div>
    </template>
    <el-descriptions :column="2" border class="bom-basic-info">
      <el-descriptions-item label="BOM编码">{{ detail.bomCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="BOM名称">{{ detail.bomName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="BOM版本">
        {{ detail.bomVersion || '-' }}
        <span v-if="detail.versionTotal" class="version-total">（共 {{ detail.versionTotal }} 版）</span>
      </el-descriptions-item>
      <el-descriptions-item label="当前版本">
        <el-tag v-if="detail.isCurrent" type="success" size="small">当前版本</el-tag>
        <el-tag v-else type="info" size="small">非当前版本</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="产出类型">
        <el-tag :type="outputTypeTag" size="small">{{ outputTypeLabel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="产出对象">
        {{ detail.outputCode || detail.productCode || '-' }}
        {{ detail.outputName || detail.productName || '' }}
      </el-descriptions-item>
      <el-descriptions-item label="审核状态">
        <el-tag :type="BomStatusEnum.getTagProps(detail.approveStatus)?.type">
          {{ BomStatusEnum.getLabel(detail.approveStatus) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="生效日期">
        {{ parseDate(detail.effectiveDate) || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="失效日期">
        {{ parseDate(detail.expiryDate) || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ parseTime(detail.createTime) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ parseTime(detail.updateTime) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
      <el-descriptions-item label="更新人">{{ detail.updateBy || '-' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核备注" :span="2">{{ detail.approveRemark || '-' }}</el-descriptions-item>
    </el-descriptions>
    <BomDetailTable :items="bomDetailList" height="auto" stripe />
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'
import { productBomApi } from '@/api/product/bom'
import { parseDate, parseTime } from '@/utils/format'
import type { EngineeringBomItem, EngineeringBom } from '@/types/product/bom'
import { BomStatusEnum, ManufacturingOutputTypeEnum } from '@/enums/product'
import BomDetailTable from './BomDetailTable.vue'

// Props
const props = defineProps({
  bomId: {
    type: Number,
    required: false,
    default: undefined,
  },
  modelValue: {
    type: Boolean,
    default: false,
  },
})

// Emits
const emit = defineEmits(['update:modelValue'])

/** 详情初始值；关闭/无 bomId 时统一回到这里，避免残留上一张 BOM 的字段。 */
const emptyDetail = (): EngineeringBom => ({
  bomId: 0,
  bomCode: '',
  bomName: '',
  productId: 0,
  productCode: '',
  productName: '',
  bomVersion: '',
  approveStatus: 0,
  isCurrent: false,
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  approveRemark: '',
  outputType: ManufacturingOutputTypeEnum.PRODUCT,
  outputCode: '',
  outputName: '',
  versionTotal: 0,
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

// 详情数据
const detail = reactive<EngineeringBom>(emptyDetail())

// BOM明细数据
const bomDetailList = ref<EngineeringBomItem[]>([])

// Dialog visibility
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// 全屏控制
const isFullscreen = ref(false)
const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

const outputTypeLabel = computed(() =>
  detail.outputType === ManufacturingOutputTypeEnum.MATERIAL ? '物料' : '产品'
)
const outputTypeTag = computed(() =>
  detail.outputType === ManufacturingOutputTypeEnum.MATERIAL ? 'warning' : 'primary'
)

// 加载BOM详情数据
const loadBomDetail = () => {
  if (!props.bomId) {
    Object.assign(detail, emptyDetail())
    bomDetailList.value = []
    return
  }

  // 加载BOM信息
  productBomApi.getEngineeringBomInfo(props.bomId).then((response: any) => {
    Object.assign(detail, response.data)
    bomDetailList.value = response.data.items || []
  })
}

// 监听bomId和visible变化，避免重复请求
watch(
  [() => props.bomId, () => visible.value],
  ([newBomId, newVisible], [oldBomId, oldVisible]) => {
    // 只有当对话框打开且有有效的bomId时才加载数据
    if (newVisible && newBomId) {
      // 避免重复加载：只有当bomId变化或对话框从关闭变为打开时才加载
      if (newBomId !== oldBomId || !oldVisible) {
        loadBomDetail()
      }
    } else if (!newVisible) {
      // 对话框关闭时重置数据
      Object.assign(detail, emptyDetail())
      bomDetailList.value = []
    }
  },
  { immediate: true }
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

.bom-basic-info {
  margin-bottom: 16px;
}

.version-total {
  margin-left: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
