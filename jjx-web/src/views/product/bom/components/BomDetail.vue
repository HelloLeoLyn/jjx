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
    <el-descriptions :column="2" border>
      <el-descriptions-item label="BOM编码">{{ detail.bomCode }}</el-descriptions-item>
      <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
      <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
      <el-descriptions-item label="BOM版本">{{ detail.bomVersion }}</el-descriptions-item>
      <el-descriptions-item label="审核状态">
        <el-tag :type="BomStatusEnum.getTagProps(detail.approveStatus)?.type">
          {{ BomStatusEnum.getLabel(detail.approveStatus) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="生效日期">
        {{ parseDate(detail.effectiveDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="失效日期">
        {{ parseDate(detail.expiryDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">{{
        parseTime(detail.createTime)
      }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{
        parseTime(detail.updateTime)
      }}</el-descriptions-item>
      <el-descriptions-item label="创建人">{{ detail.createBy }}</el-descriptions-item>
      <el-descriptions-item label="更新人">{{ detail.updateBy }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核备注" :span="2">{{
        detail.approveRemark || '-'
      }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="bomDetailList" border style="width: 100%" stripe>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="物料编码" prop="materialCode" width="180" />
      <el-table-column label="物料名称" prop="materialName" width="180" />
      <!-- <el-table-column label="规格型号" prop="specification" width="120" /> -->
      <el-table-column label="单位" prop="unit" width="80" />
      <el-table-column label="数量" prop="quantity" width="80" align="right" />
      <el-table-column label="损耗率(%)" prop="lossRate" width="100" align="right" />
      <el-table-column label="模数" prop="moduleQty" width="80" align="right" />
      <el-table-column label="基数" prop="baseQty" width="80" align="right" />
      <el-table-column label="备注" prop="remark" />
    </el-table>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'
import { productBomApi } from '@/api/product/bom'
import { parseDate, parseTime } from '@/utils/format'
import type { EngineeringBomItem, EngineeringBom } from '@/types/product/bom'
import { BomStatusEnum } from '@/enums/product'

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

// 详情数据
const detail = reactive<EngineeringBom>({
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
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

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

// 加载BOM详情数据
const loadBomDetail = () => {
  if (!props.bomId) {
    // 重置数据
    Object.assign(detail, {
      bomId: 0,
      bomCode: '',
      bomName: '',
      productId: 0,
      productCode: '',
      productName: '',
      version: '',
      bomStatus: '',
      approveStatus: '',
      isCurrent: false,
      effectiveDate: '',
      expiryDate: '',
      remark: '',
      approveRemark: '',
      createTime: '',
      updateTime: '',
      createBy: '',
      updateBy: '',
    })
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
      Object.assign(detail, {
        bomId: 0,
        bomCode: '',
        bomName: '',
        productId: 0,
        productCode: '',
        productName: '',
        version: '',
        bomStatus: '',
        approveStatus: '',
        isCurrent: false,
        effectiveDate: '',
        expiryDate: '',
        remark: '',
        createTime: '',
        updateTime: '',
        createBy: '',
        updateBy: '',
      })
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

.search-card {
  margin-bottom: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>
