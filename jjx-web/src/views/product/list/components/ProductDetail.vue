<template>
  <div v-loading="loading" class="product-detail">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-container">
      <el-alert title="加载失败" :description="error" type="error" show-icon :closable="false" />
      <div class="error-actions">
        <el-button type="primary" @click="loadData">重试</el-button>
      </div>
    </div>

    <!-- 内容展示 -->
    <div v-else>
      <!-- 基本信息 -->
      <el-divider content-position="left">基本信息</el-divider>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="产品编码">{{
          productData.product?.productCode
        }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{
          productData.product?.productName
        }}</el-descriptions-item>
        <el-descriptions-item label="产品分类">{{
          productData.category?.categoryName || productData.product?.categoryName
        }}</el-descriptions-item>
        <el-descriptions-item label="产品类型">{{
          productData.product?.productType === 'standard' ? '标准产品' : '定制产品'
        }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{
          productData.product?.unit || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="产品状态">
          <el-tag
            :type="ProductEnum.status.getTagProps(productData.product?.productStatus ?? 0).type"
          >
            {{ ProductEnum.status.getLabel(productData.product?.productStatus ?? 0) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="基础售价">{{
          productData.product?.basePrice ? '¥' + productData.product.basePrice : '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="标准成本">{{
          productData.product?.costPrice ? '¥' + productData.product.costPrice : '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="最小起订量">{{
          productData.product?.minOrderQty || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="标准交期(天)">{{
          productData.product?.leadTime || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="菲林数量">{{
          productData.product?.filmCount || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{
          productData.product?.specJson ? '已配置' : '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(productData.product?.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          parseTime(productData.product?.updateTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{
          productData.product?.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>

      <!-- BOM信息 -->
      <el-divider content-position="left">
        BOM配置
        <el-tag
          v-if="productData.bom"
          size="small"
          :type="productData.bom.approveStatus === 3 ? 'success' : 'warning'"
          style="margin-left: 8px"
        >
          {{ getApproveStatusText(productData.bom.approveStatus) }}
        </el-tag>
      </el-divider>
      <div v-if="productData.bom">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="BOM编码">{{ productData.bom.bomCode }}</el-descriptions-item>
          <el-descriptions-item label="BOM名称">{{ productData.bom.bomName }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ productData.bom.bomVersion }}</el-descriptions-item>
          <el-descriptions-item label="是否当前版本">
            <el-tag :type="productData.bom.isCurrent ? 'success' : 'info'" size="small">
              {{ productData.bom.isCurrent ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生效日期">{{
            productData.bom.effectiveDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="失效日期">{{
            productData.bom.expiryDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ productData.bom.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            parseTime(productData.bom.createTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{
            productData.bom.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- BOM物料清单 -->
        <div v-if="productData.bom.items && productData.bom.items.length > 0" class="sub-table">
          <div class="sub-table-title">物料清单（{{ productData.bom.items.length }} 项）</div>
          <el-table :data="productData.bom.items" border size="small" max-height="300">
            <el-table-column label="序号" type="index" width="60" />
            <el-table-column label="物料编码" prop="materialCode" width="120" />
            <el-table-column label="物料名称" prop="materialName" width="150" />
            <el-table-column label="规格" prop="materialSpec" width="120" />
            <el-table-column label="单位" prop="unit" width="60" />
            <el-table-column label="数量" prop="quantity" width="80" />
            <el-table-column label="损耗率(%)" prop="lossRate" width="100" />
            <el-table-column label="净用量" prop="netQuantity" width="80" />
            <el-table-column label="单价" prop="unitPrice" width="80" />
            <el-table-column label="总价" prop="totalPrice" width="80" />
            <el-table-column label="排序" prop="sortOrder" width="60" />
            <el-table-column label="备注" prop="remark" min-width="100" />
          </el-table>
        </div>
        <div v-else class="empty-section">
          <el-empty description="暂无物料清单" :image-size="40" />
        </div>
      </div>
      <div v-else class="empty-section">
        <el-empty description="未配置BOM" :image-size="40" />
      </div>

      <!-- 工艺路线信息 -->
      <el-divider content-position="left">
        工艺路线
        <el-tag v-if="productData.routing" size="small" type="primary" style="margin-left: 8px">
          {{ ProductEnum.routeStatus.getLabel(productData.routing.approveStatus) }}
        </el-tag>
      </el-divider>
      <div v-if="productData.routing">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="路线编码">{{
            productData.routing.routingCode
          }}</el-descriptions-item>
          <el-descriptions-item label="路线名称">{{
            productData.routing.routingName
          }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{
            productData.routing.routingVersion
          }}</el-descriptions-item>
          <el-descriptions-item label="是否当前版本">
            <el-tag :type="productData.routing.isCurrent === 1 ? 'success' : 'info'" size="small">
              {{ productData.routing.isCurrentName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工序数量"
            >{{ productData.routing.processCount }} 道</el-descriptions-item
          >
          <el-descriptions-item label="总工时(小时)">{{
            productData.routing.totalLaborHours || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="总机时(小时)">{{
            productData.routing.totalMachineHours || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{
            productData.routing.createBy
          }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            parseTime(productData.routing.createTime)
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 工序明细（按组合工序分组展示） -->
        <div v-if="routeGroups.length > 0" class="sub-table">
          <div class="sub-table-title">工序明细（{{ productData.routing.items.length }} 道）</div>
          <el-table :data="routeGroups" border size="small" max-height="400" style="width: 100%">
            <el-table-column label="序号" width="60" align="center">
              <template #default="scope">{{ scope.row.groupOrder }}</template>
            </el-table-column>
            <el-table-column label="组合工序" min-width="300">
              <template #default="scope">
                <div class="group-items">
                  <el-tag
                    v-for="item in scope.row.items"
                    :key="item.processId"
                    size="small"
                    class="group-item-tag"
                  >
                    <span>{{ item.processName }}</span>
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="工序类别" width="120" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.processCategoryName" type="info" size="small">{{
                  scope.row.processCategoryName
                }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="总人工工时" width="120" align="right">
              <template #default="scope">{{ scope.row.totalLaborHours }}</template>
            </el-table-column>
            <el-table-column label="总机器工时" width="120" align="right">
              <template #default="scope">{{ scope.row.totalMachineHours }}</template>
            </el-table-column>
            <el-table-column label="组合备注" min-width="200">
              <template #default="scope"
                ><span>{{ scope.row.remark || '-' }}</span></template
              >
            </el-table-column>
          </el-table>
        </div>
        <div v-else class="empty-section">
          <el-empty description="暂无工序明细" :image-size="40" />
        </div>
      </div>
      <div v-else class="empty-section">
        <el-empty description="未配置工艺路线" :image-size="40" />
      </div>

      <!-- 菲林信息 -->
      <el-divider content-position="left">
        菲林配置
        <el-tag
          v-if="productData.films && productData.films.length > 0"
          size="small"
          type="primary"
          style="margin-left: 8px"
        >
          {{ productData.films.length }} 个菲林
        </el-tag>
      </el-divider>
      <div v-if="productData.films && productData.films.length > 0">
        <el-table :data="productData.films" border size="small" max-height="400">
          <el-table-column label="菲林编码" prop="filmCode" width="130" />
          <el-table-column label="菲林名称" prop="filmName" width="150" />
          <el-table-column label="菲林类型" prop="filmTypeName" width="100" />
          <el-table-column label="版本" prop="version" width="80" />
          <el-table-column label="当前版本" prop="isCurrentName" width="80" />
          <el-table-column label="尺寸" prop="filmSize" width="120" />
          <el-table-column label="厚度(mm)" prop="filmThickness" width="100" />
          <el-table-column label="材料" prop="filmMaterial" width="100" />
          <el-table-column label="颜色" prop="color" width="80" />
          <el-table-column label="审核状态" prop="approveStatusName" width="100" />
          <el-table-column label="设计人员" prop="designerName" width="100" />
          <el-table-column label="是否下发" prop="isReleased" width="80">
            <template #default="scope">
              <el-tag :type="scope.row.isReleased === 1 ? 'success' : 'info'" size="small">
                {{ scope.row.isReleased === 1 ? '已下发' : '未下发' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="160">
            <template #default="scope">
              {{ parseTime(scope.row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="100" />
        </el-table>
      </div>
      <div v-else class="empty-section">
        <el-empty description="未配置菲林" :image-size="40" />
      </div>

      <!-- 产品文件库（DEV-734） -->
      <el-divider content-position="left">产品文件库</el-divider>
      <ProductFileLibrary
        v-if="productData.product?.productCode"
        :product-code="productData.product.productCode"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { productApi } from '@/api/product'
import { parseTime } from '@/utils/format'
import { ProductEnum, StepTypeEnum } from '@/enums'
import type { ProductFullVO } from '@/types/product'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'

// Props
interface Props {
  productId?: number
}

const props = withDefaults(defineProps<Props>(), {
  productId: undefined,
})

// Emits
interface Emits {
  (e: 'loaded', data: ProductFullVO): void
  (e: 'status-change', status: number): void
}

const emit = defineEmits<Emits>()

// 响应式数据
const loading = ref(false)
const error = ref<string>('')
const productData = ref<ProductFullVO>({
  product: undefined,
  bom: undefined,
  routing: undefined,
  category: undefined,
  films: [],
})

// 获取审核状态文本
const getApproveStatusText = (status: number): string => {
  const map: Record<number, string> = { 1: '草稿', 2: '待审核', 3: '已批准', 4: '已驳回' }
  return map[status] || '未知'
}

// 组合工序分组展示接口
interface GroupDisplay {
  groupOrder: number
  groupName: string
  items: EngineeringRoutingItemVO[]
  totalLaborHours: number
  totalMachineHours: number
  remark: string
  processCategoryName: string
}

// 按组合工序分组
const routeGroups = computed<GroupDisplay[]>(() => {
  const items = productData.value.routing?.items
  if (!items || items.length === 0) return []

  const groupMap = new Map<string, EngineeringRoutingItemVO[]>()
  items.forEach((item) => {
    const key = item.groupId
      ? 'group_' + item.groupId
      : 'independent_' + (item.itemId || Math.random())
    if (!groupMap.has(key)) {
      groupMap.set(key, [])
    }
    groupMap.get(key)!.push(item)
  })

  const sortedEntries = Array.from(groupMap.entries()).sort((a, b) => {
    return (a[1][0].groupOrder || 0) - (b[1][0].groupOrder || 0)
  })

  return sortedEntries.map(([, items]) => ({
    groupOrder: items[0].groupOrder || 0,
    groupName: items[0].groupName || '组合' + (items[0].groupOrder || ''),
    items: items,
    totalLaborHours: items.reduce(
      (sum, i) => sum + (i.customLaborHours || i.standardLaborHours || 0),
      0
    ),
    totalMachineHours: items.reduce(
      (sum, i) => sum + (i.customMachineHours || i.standardMachineHours || 0),
      0
    ),
    remark: items[0]?.description || '',
    processCategoryName: StepTypeEnum.getLabel(Number(items[0]?.processCategory) || 0) || '',
  }))
})

// 加载数据
const loadData = async () => {
  if (!props.productId) {
    error.value = '产品ID不能为空'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const response = await productApi.full(props.productId)
    if (response.data) {
      productData.value = response.data

      // 触发加载完成事件
      emit('loaded', productData.value)

      // 触发状态变化事件
      if (productData.value.product?.productStatus) {
        emit('status-change', productData.value.product.productStatus)
      }
    } else {
      error.value = '未找到产品信息'
    }
  } catch (err: any) {
    console.error('加载产品详情失败:', err)
    error.value = err.message || '加载产品详情失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 监听productId变化
watch(
  () => props.productId,
  (newId) => {
    if (newId) {
      loadData()
    } else {
      // 重置数据
      productData.value = {
        product: undefined,
        bom: undefined,
        routing: undefined,
        category: undefined,
        films: [],
      }
      error.value = ''
    }
  },
  { immediate: true }
)

// 暴露方法给父组件
defineExpose({
  loadData,
})
</script>

<style scoped lang="scss">
.product-detail {
}

.loading-container {
  padding: 20px;
}

.error-container {
  padding: 20px;
}

.error-actions {
  margin-top: 20px;
  text-align: center;
}

.sub-table {
  margin-top: 12px;
}

.sub-table-title {
  font-size: 14px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  padding-left: 4px;
  border-left: 3px solid #409eff;
  line-height: 1;
}

.empty-section {
  padding: 20px;
  text-align: center;
  color: #909399;
}

.group-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
}

.group-item-tag {
  cursor: default;
  user-select: none;
}
</style>
