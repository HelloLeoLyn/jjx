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
            <el-descriptions-item label="产品类型">{{
              ProductTypeEnum.getLabel(Number(productData.product?.productType))
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

          <!-- 产品资料（dev-20261013-041：改走公共上传组件的 refs 产品视图；详情页只读，不给上传入口） -->
          <el-divider content-position="left">产品资料</el-divider>
          <ProductDocPanel v-if="productId" :product-id="productId" readonly />

          <el-divider content-position="left">业务流转附件</el-divider>
          <div v-loading="bizAttachmentsLoading" class="biz-attachments">
            <el-collapse v-if="bizAttachmentGroups.length">
              <el-collapse-item
                v-for="group in bizAttachmentGroups"
                :key="`${group.sourceType}-${group.sourceId}`"
                :name="`${group.sourceType}-${group.sourceId}`"
              >
                <template #title>
                  <span class="biz-source-title">
                    {{ sourceTypeLabel(group.sourceType) }} {{ group.sourceNo || group.sourceId }}
                  </span>
                  <el-tag size="small" type="info">{{ group.files.length }}</el-tag>
                </template>
                <div class="biz-file-list">
                  <div v-for="file in group.files" :key="file.id" class="biz-file-item">
                    <div class="biz-file-info">
                      <el-icon class="biz-file-icon"><Document /></el-icon>
                      <div class="biz-file-meta">
                        <el-link
                          type="primary"
                          :href="attachmentRefApi.downloadUrlByRef(file.id)"
                          :underline="false"
                          target="_blank"
                        >
                          {{ file.fileName || '-' }}
                        </el-link>
                        <div class="biz-file-sub">
                          <span>{{ formatFileSize(file.fileSize) }}</span>
                          <span v-if="file.createBy">· {{ file.createBy }}</span>
                          <span v-if="file.createTime"
                            >· {{ formatAttachmentTime(file.createTime) }}</span
                          >
                        </div>
                      </div>
                    </div>
                    <el-button
                      link
                      type="primary"
                      :icon="Download"
                      @click="openAttachment(file.id)"
                    />
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
            <el-empty
              v-else-if="!bizAttachmentsLoading"
              description="暂无业务流转附件"
              :image-size="60"
            />
          </div>
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
import { ref, watch } from 'vue'
import { productApi } from '@/api/product'
import { parseTime } from '@/utils/format'
import { ProductEnum, ProductTypeEnum } from '@/enums'
import type { ProductFullVO } from '@/types/product'
import ProductDocPanel from '@/components/product/ProductDocPanel.vue'
import ProductBasicInfo from '@/components/product/ProductBasicInfo.vue'
import BomDetailTable from '@/views/product/bom/components/BomDetailTable.vue'
import RouteProcessTable from '@/views/product/route/components/RouteProcessTable.vue'
import ProductFilmPanel from '@/views/product/film/components/ProductFilmPanel.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import type { ProductBizAttachmentGroup } from '@/api/product'
import { Document, Download } from '@element-plus/icons-vue'

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
const bizAttachmentsLoading = ref(false)
const bizAttachmentGroups = ref<ProductBizAttachmentGroup[]>([])
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
  if (!props.productId) {
    error.value = '产品ID不能为空'
    return
  }

  activeTab.value = 'basic'
  loading.value = true
  error.value = ''

  try {
    const response = await productApi.full(props.productId)
    if (response.data) {
      productData.value = response.data
      await loadBizAttachments()

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

const loadBizAttachments = async () => {
  if (!props.productId) return
  bizAttachmentsLoading.value = true
  try {
    const response = await productApi.bizAttachments(props.productId)
    bizAttachmentGroups.value = response.data || []
  } catch (err) {
    console.error('加载业务流转附件失败:', err)
    bizAttachmentGroups.value = []
  } finally {
    bizAttachmentsLoading.value = false
  }
}

const sourceTypeLabel = (sourceType: ProductBizAttachmentGroup['sourceType']): string =>
  ({
    inquiry: '询价单',
    quotation: '报价单',
    sales_order: '订单',
  })[sourceType]

const formatFileSize = (size?: number): string => {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}

const formatAttachmentTime = (time: string): string => time.replace('T', ' ').slice(0, 16)

const openAttachment = (id: number) => {
  window.open(attachmentRefApi.downloadUrlByRef(id), '_blank')
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
      bizAttachmentGroups.value = []
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

.biz-source-title {
  margin-right: 8px;
  font-weight: 600;
}

.biz-file-list {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.biz-file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.biz-file-item:last-child {
  border-bottom: none;
}

.biz-file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.biz-file-icon {
  color: #409eff;
  flex-shrink: 0;
}

.biz-file-meta {
  min-width: 0;
}

.biz-file-sub {
  font-size: 12px;
  color: #909399;
}
</style>
