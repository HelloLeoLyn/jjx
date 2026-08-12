<template>
  <div v-loading="loading">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="路线编码" :span="1">{{
        detail.routingCode
      }}</el-descriptions-item>
      <el-descriptions-item label="路线名称" :span="1">{{
        detail.routingName
      }}</el-descriptions-item>
      <el-descriptions-item label="产品名称" :span="1">{{
        detail.productName
      }}</el-descriptions-item>
      <el-descriptions-item label="产品编码" :span="1">{{
        detail.productCode
      }}</el-descriptions-item>
      <el-descriptions-item label="版本号" :span="1">{{
        detail.routingVersion
      }}</el-descriptions-item>
      <el-descriptions-item label="当前版本" :span="1">
        <el-tag :type="detail.isCurrent === 1 ? 'success' : 'info'" size="small">{{
          detail.isCurrentName
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="审核状态" :span="1">
        <el-tag :type="RouteStatusEnum.getTagProps(detail.approveStatus).type" size="small">{{
          RouteStatusEnum.getLabel(detail.approveStatus)
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="工序数量" :span="1">{{
        detail.processCount
      }}</el-descriptions-item>
      <el-descriptions-item label="总人工工时" :span="1">{{
        detail.totalLaborHours
      }}</el-descriptions-item>
      <el-descriptions-item label="总机器工时" :span="1">{{
        detail.totalMachineHours
      }}</el-descriptions-item>
      <el-descriptions-item label="创建人" :span="1">{{ detail.createBy }}</el-descriptions-item>
      <el-descriptions-item label="创建时间" :span="1">{{
        detail.createTime
      }}</el-descriptions-item>
      <el-descriptions-item label="更新人" :span="1">{{ detail.updateBy }}</el-descriptions-item>
      <el-descriptions-item label="更新时间" :span="1">{{
        detail.updateTime
      }}</el-descriptions-item>
      <el-descriptions-item label="路线说明" :span="2">{{
        detail.description || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-divider content-position="left">工序明细</el-divider>
    <el-table :data="groups" border stripe style="width: 100%">
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
              <!-- 有下标（hasIndex=1）：图标+红底数字（只读） -->
              <IconStepBadge
                v-if="item.hasIndex === 1"
                :icon="item.icon || ''"
                :size="14"
                :index="item.indexNumber ?? null"
              />
              <!-- 无下标：原样图标+名称（印刷工序带标识，2026-08-12） -->
              <template v-else>
                <SvgIcon
                  v-if="item.icon"
                  :name="item.icon"
                  :size="14"
                  style="margin-right: 4px; vertical-align: middle"
                />
                <el-tag v-if="item.majorCategory === 'PRINT'" size="small" type="warning" effect="plain" style="margin-right: 4px">印刷</el-tag>
                <span>{{ item.processName }}</span>
              </template>
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
    <slot name="extra" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { productRouteApi } from '@/api/product/routing'
import type { EngineeringRoutingVO, EngineeringRoutingItemVO } from '@/types/product/routing'
import { RouteStatusEnum, StepTypeEnum } from '@/enums/product'
import IconStepBadge from '@/components/IconStepBadge/index.vue'

const loading = ref(false)
const detail = reactive<EngineeringRoutingVO>({
  routingId: 0,
  routingCode: '',
  routingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  routingVersion: '',
  isCurrent: 0,
  isCurrentName: '',
  approveStatus: 0,
  approveStatusName: '',
  totalLaborHours: 0,
  totalMachineHours: 0,
  processCount: 0,
  description: '',
  createBy: '',
  createTime: '',
  updateBy: '',
  updateTime: '',
  remark: '',
  items: [],
})

interface GroupDisplay {
  groupOrder: number
  groupName: string
  items: EngineeringRoutingItemVO[]
  totalLaborHours: number
  totalMachineHours: number
  remark: string
  processCategoryName: string
}

const groups = ref<GroupDisplay[]>([])

const buildGroups = (items: EngineeringRoutingItemVO[]) => {
  if (!items || items.length === 0) {
    groups.value = []
    return
  }
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
  groups.value = sortedEntries.map(([, items]) => ({
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
}

const loadDetail = async (routingId: number) => {
  if (!routingId) return
  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(routingId)
    Object.assign(detail, response.data)
    buildGroups(detail.items || [])
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
  } finally {
    loading.value = false
  }
}

const resetDetail = () => {
  Object.assign(detail, {
    routingId: 0,
    routingCode: '',
    routingName: '',
    productId: 0,
    productCode: '',
    productName: '',
    routingVersion: '',
    isCurrent: 0,
    isCurrentName: '',
    approveStatus: 0,
    approveStatusName: '',
    totalLaborHours: 0,
    totalMachineHours: 0,
    processCount: 0,
    description: '',
    createBy: '',
    createTime: '',
    updateBy: '',
    updateTime: '',
    remark: '',
    items: [],
  })
  groups.value = []
}

defineExpose({ loadDetail, resetDetail })
</script>

<style scoped>
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
