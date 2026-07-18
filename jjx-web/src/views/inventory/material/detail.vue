<template>
  <div class="material-detail">
    <el-card class="detail-card" shadow="never">
      <div class="header-row">
        <h2>物料详情</h2>
        <el-button type="primary" @click="goBack">返回</el-button>
      </div>

      <el-form label-width="120px" class="detail-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料编码">
              <span>{{ material.materialCode }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称">
              <span>{{ material.materialName }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机种">
              <span>{{ material.materialNameEn }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料类型">
              <span>{{ typeLabel }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <span>{{ material.unit }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="安全库存">
              <span>{{ material.safeStock }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高库存">
              <span>{{ material.maxStock }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="再订货点">
              <span>{{ material.reorderPoint }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次管理">
              <span>{{ material.batchControl ? '是' : '否' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准单价">
              <span>¥ {{ formatCurrency(material.standardPrice) }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购周期(天)">
              <span>{{ material.leadTime }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保质期(天)">
              <span>{{ material.shelfLife }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警提前天数">
              <span>{{ material.expiryAlertDays }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input type="textarea" :rows="3" v-model="material.remark" readonly />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="创建时间">
              <span>{{ material.createTime || '-' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <span>{{ material.status === 'active' ? '启用' : '停用' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'MaterialDetail',
})

import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { formatCurrency } from '@/utils/format'
import type { InventoryMaterial } from '@/types/inventory/material'
import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'

const route = useRoute()
const router = useRouter()
const materialId = route.params.materialId as string

const material = ref<InventoryMaterial>({
  materialId: 0,
  materialCode: '',
  materialName: '',
  materialNameEn: '',
  materialType: '',
  categoryId: undefined,
  specification: '',
  unit: '',
  safeStock: 0,
  maxStock: 0,
  reorderPoint: 0,
  standardPrice: 0,
  leadTime: undefined,
  supplierName: '',
  batchControl: false,
  shelfLife: undefined,
  expiryAlertDays: 0,
  status: '',
  remark: '',
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

const typeLabel = computed(() => {
  return MaterialTypeEnum.getLabel(material.value.materialType) || '-'
})

const getMaterialDetail = async () => {
  if (!materialId) {
    ElMessage.error('找不到物料ID')
    router.back()
    return
  }

  try {
    const res = await materialApi.getInfo(materialId)
    if (res.code === 200 && res.data) {
      material.value = { ...material.value, ...res.data }
    } else {
      ElMessage.error(res.msg || '获取物料详情失败')
      router.back()
    }
  } catch (error) {
    console.error('获取物料详情失败:', error)
    ElMessage.error('获取物料详情失败')
    router.back()
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  getMaterialDetail()
})
</script>

<style scoped>
.material-detail {
  padding: 20px;
}

.detail-card {
  max-width: 1200px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-form .el-form-item {
  margin-bottom: 14px;
}
</style>
