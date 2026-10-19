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
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <!-- 基本信息（公共组件，dev-20261011-032：产品编码/名称/客户/当前BOM/当前路线/规格） -->
          <el-divider content-position="left">基本信息</el-divider>
          <ProductBasicInfo :detail="productData" />
          <el-descriptions :column="3" border style="margin-top: 8px">
            <el-descriptions-item label="产品分类">{{
              productData.category?.categoryName || productData.product?.categoryName
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
            <el-descriptions-item v-if="hasPermi('product:price:view')" label="基础售价">{{
              productData.product?.basePrice != null
                ? '¥' + Number(productData.product.basePrice).toFixed(2)
                : '-'
            }}</el-descriptions-item>
            <el-descriptions-item v-if="hasPermi('product:price:view')" label="标准成本">{{
              productData.product?.costPrice != null
                ? '¥' + Number(productData.product.costPrice).toFixed(2)
                : '-'
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

          <el-divider content-position="left">产品资料</el-divider>
          <ProductDocLedger
            v-if="productId"
            ref="docLedger"
            :product-id="productId"
            @loaded="productDocIds = $event.map(row => row.attachmentId)"
            @loading="productDocsLoading = $event"
          />

          <el-divider content-position="left">业务流转</el-divider>
          <ProductBizTimeline
            v-if="productId"
            :product-id="productId"
            :product-attachment-ids="productDocIds"
            :product-docs-loading="productDocsLoading"
            @open-product-doc="docLedger?.focusAttachment($event)"
          />
        </el-tab-pane>
        <el-tab-pane label="BOM" name="bom">
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
              <el-descriptions-item label="BOM编码">{{
                productData.bom.bomCode
              }}</el-descriptions-item>
              <el-descriptions-item label="BOM名称">{{
                productData.bom.bomName
              }}</el-descriptions-item>
              <el-descriptions-item label="版本">{{
                productData.bom.bomVersion
              }}</el-descriptions-item>
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
              <el-descriptions-item label="创建人">{{
                productData.bom.createBy
              }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{
                parseTime(productData.bom.createTime)
              }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{
                productData.bom.remark || '-'
              }}</el-descriptions-item>
            </el-descriptions>

            <!-- BOM物料清单 -->
            <div v-if="productData.bom.items && productData.bom.items.length > 0" class="sub-table">
              <BomDetailTable :items="productData.bom.items" height="300" />
            </div>
            <div v-else class="empty-section">
              <el-empty description="暂无物料清单" :image-size="40" />
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="未配置BOM" :image-size="40" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="工艺路线" name="routing">
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
                <el-tag
                  :type="productData.routing.isCurrent === 1 ? 'success' : 'info'"
                  size="small"
                >
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

            <!-- 工序明细与工艺路线详情共用展示 -->
            <div v-if="productData.routing.items?.length" class="sub-table">
              <RouteProcessTable :items="productData.routing.items" />
            </div>
            <div v-else class="empty-section">
              <el-empty description="暂无工序明细" :image-size="40" />
            </div>
          </div>
          <div v-else class="empty-section">
            <el-empty description="未配置工艺路线" :image-size="40" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="菲林" name="film">
          <ProductFilmPanel :films="productData.films ?? []" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { hasPermi } from '@/directives'
import { onScopeDispose, ref, watch } from 'vue'
import { productApi } from '@/api/product'
import { parseTime } from '@/utils/format'
import { ProductEnum } from '@/enums'
import type { ProductFullVO } from '@/types/product'
import ProductDocLedger from '@/components/product/ProductDocLedger.vue'
import ProductBizTimeline from '@/components/product/ProductBizTimeline.vue'
import ProductBasicInfo from '@/components/product/ProductBasicInfo.vue'
import BomDetailTable from '@/views/product/bom/components/BomDetailTable.vue'
import RouteProcessTable from '@/views/product/route/components/RouteProcessTable.vue'
import ProductFilmPanel from '@/views/product/film/components/ProductFilmPanel.vue'

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
const activeTab = ref('basic')
const error = ref<string>('')
const docLedger = ref<InstanceType<typeof ProductDocLedger>>()
const productDocIds = ref<number[]>([])
const productDocsLoading = ref(true)
let detailRequest = 0
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

// 加载数据
const loadData = async () => {
  const current = ++detailRequest
  const productId = props.productId
  if (!productId) {
    error.value = '产品ID不能为空'
    return
  }

  activeTab.value = 'basic'
  loading.value = true
  error.value = ''

  try {
    const response = await productApi.full(productId)
    if (current !== detailRequest) return
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
    if (current !== detailRequest) return
    console.error('加载产品详情失败:', err)
    error.value = err.message || '加载产品详情失败，请稍后重试'
  } finally {
    if (current === detailRequest) loading.value = false
  }
}

// 监听productId变化
watch(
  () => props.productId,
  (newId) => {
    if (newId) {
      loadData()
    } else {
      detailRequest++
      loading.value = false
      // 重置数据
      productData.value = {
        product: undefined,
        bom: undefined,
        routing: undefined,
        category: undefined,
        films: [],
      }
      error.value = ''
      productDocIds.value = []
      productDocsLoading.value = false
    }
  },
  { immediate: true }
)

onScopeDispose(() => { detailRequest++ })

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

</style>
