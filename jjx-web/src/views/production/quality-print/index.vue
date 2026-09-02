<template>
  <div class="quality-print-page">
    <div class="page-header">
      <div><h1>质量记录打印中心</h1><p>选择已生效的质量记录，打印通用 A4 空白表单或下载模板文件。</p></div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="主管部门">
          <el-select v-model="query.ownerDept" clearable placeholder="全部部门" style="width: 160px">
            <el-option v-for="dept in ownerDepts" :key="dept" :label="dept" :value="dept" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="query.category" clearable placeholder="全部类别" style="width: 140px">
            <el-option v-for="item in QualityTemplateCategoryEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="记录编号或名称" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="recordNo" label="记录编号" width="140" />
        <el-table-column prop="recordName" label="名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="version" label="版次" width="70" />
        <el-table-column prop="ownerDept" label="主管部门" width="110" />
        <el-table-column label="保存期限" width="100"><template #default="{ row }">{{ row.retentionYears }} 年</template></el-table-column>
        <el-table-column label="类别" width="105"><template #default="{ row }"><el-tag :type="QualityTemplateCategoryEnum.getTagProps(row.category).type">{{ QualityTemplateCategoryEnum.getLabel(row.category) }}</el-tag></template></el-table-column>
        <el-table-column label="联动状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.category === QualityTemplateCategory.DATA" type="success">已联动</el-tag>
            <el-tag v-else-if="row.category === QualityTemplateCategory.BLANK && row.bizType" type="warning">规划中</el-tag>
            <el-tag v-else-if="row.category === QualityTemplateCategory.BLANK" type="info">空白表</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模板文件" width="120">
          <template #default="{ row }"><el-link v-if="row.hasFile && row.fileId" type="primary" :href="attachmentApi.downloadUrl(row.fileId)" target="_blank">已上传 · 打开</el-link><el-tag v-else type="info">未上传</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <template v-if="row.category === QualityTemplateCategory.DATA">
              <el-button v-if="row.bizType && BIZ_TYPE_ROUTE[row.bizType]" link type="primary" @click="goBusinessModule(row.bizType)">去业务模块打印</el-button>
              <el-tag v-else type="warning">请到对应业务模块打印</el-tag>
            </template>
            <template v-else><el-button link type="primary" @click="openPrint(row)">空白表打印</el-button><el-link v-if="row.fileId" class="download-link" :href="attachmentApi.downloadUrl(row.fileId)" target="_blank">下载</el-link></template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" class="pagination" @change="load" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { attachmentApi } from '@/api/system/attachment'
import { getQualityTemplateOwnerDepts, getQualityTemplatePage, type QualityTemplate, type QualityTemplateQuery } from '@/api/production/qualityTemplate'
import { QualityTemplateCategory, QualityTemplateCategoryEnum, QualityTemplateStatus } from '@/enums/production/QualityTemplateEnum'

const router = useRouter()
const loading = ref(false)
const rows = ref<QualityTemplate[]>([])
const total = ref(0)
const ownerDepts = ref<string[]>([])
const query = reactive<QualityTemplateQuery>({ pageNum: 1, pageSize: 20, status: QualityTemplateStatus.ACTIVE })
const BIZ_TYPE_ROUTE: Record<string, string> = {
  quality_inspection: '/production/quality',
  operation_execution: '/production/execution',
  inventory_inbound: '/inventory/inbound',
  inventory_outbound: '/inventory/outbound',
  production_order: '/production/order',
  purchase_order: '/purchase/order',
  sales_delivery: '/sales/delivery',
  sales_order_review: '/sales/order',
  sales_inquiry: '/sales/inquiry',
  product: '/product/list',
  production_equipment: '/production/equipment',
  purchase_supplier: '/purchase/supplier',
}

async function load() {
  loading.value = true
  try {
    const res: any = await getQualityTemplatePage(query)
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
async function loadOwnerDepts() { const res: any = await getQualityTemplateOwnerDepts(); ownerDepts.value = res.data || [] }
function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: query.pageSize, keyword: undefined, ownerDept: undefined, category: undefined, status: QualityTemplateStatus.ACTIVE }); load() }
function openPrint(row: QualityTemplate) { router.push({ path: '/production/quality-print/print', query: { templateId: row.id } }) }
function goBusinessModule(bizType: string) { router.push(BIZ_TYPE_ROUTE[bizType]) }
onMounted(() => { load(); loadOwnerDepts() })
</script>

<style scoped>
.quality-print-page { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-header h1 { margin: 0 0 6px; font-size: 22px; }
.page-header p { margin: 0; color: #909399; }
.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-form-item) { margin-bottom: 0; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.download-link { margin-left: 12px; vertical-align: middle; }
</style>
