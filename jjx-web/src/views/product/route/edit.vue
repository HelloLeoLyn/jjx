<template>
  <div class="product-route-edit">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>编辑工艺路线</span>
          <!-- 版本号显示（10-10 新增） -->
          <span class="version-badge">
            当前版本：<el-tag size="small" type="primary">{{ displayVersion }}</el-tag>
            <el-tag v-if="formData.sourceSampleId" size="small" type="info" style="margin-left:6px">
              来源打样单 #{{ formData.sourceSampleId }}
            </el-tag>
            <el-tag v-if="formData.parentRoutingId" size="small" type="warning" style="margin-left:6px">
              升版自 V{{ parentVersionHint }}
            </el-tag>
          </span>
          <div>
            <el-button @click="handleBack">返回</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit"
              >保存</el-button
            >
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
        v-loading="loading"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品Id" prop="productId">
              <el-input v-model="formData.productId" placeholder="请选择产品" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路线编码" prop="routingCode">
              <el-input v-model="formData.routingCode" placeholder="请输入路线编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="formData.routingName" placeholder="请输入路线名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="routingVersion">
              <el-input v-model="formData.routingVersion" placeholder="请输入版本号，如 V1.0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="路线说明" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="2"
                placeholder="请输入路线说明"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="formData.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工序明细编辑器 -->
      <!-- 变更提示 + 变更说明（有内容变更时显示，10-10 新增） -->
      <el-alert
        v-if="hasChanges"
        type="warning"
        show-icon
        :closable="false"
        :title="`检测到工序内容变更，保存时将自动升级版本（${displayVersion} → ${nextVersionHint}），旧版本将失效`"
        style="margin-bottom:10px"
      />
      <div v-if="hasChanges" style="margin-bottom:10px">
        <el-input
          v-model="changeNote"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="变更说明（可选，自动记录到新版本备注，如：增加面板丝印工序 / 调整工时）"
        />
      </div>
      <RouteItemIconEditor
        ref="routeItemEditorRef"
        :model-value="formData.items"
        :standard-processes="standardProcesses"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductRouteEdit',
})

import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productRouteApi } from '@/api/product/routing'
import type { StandardProcessOption } from '@/types/product'
import type { ProductRouteFormData, EngineeringRoutingItemVO } from '@/types/product/routing'
import RouteItemIconEditor from './components/RouteItemIconEditor.vue'

const route = useRoute()
const router = useRouter()

const routingId = Number(route.params.routingId)

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const routeItemEditorRef = ref<InstanceType<typeof RouteItemIconEditor>>()

const standardProcesses = ref<StandardProcessOption[]>([])

// 表单数据
const formData = reactive<ProductRouteFormData>({
  routingCode: '',
  routingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  routingVersion: '',
  description: '',
  remark: '',
  items: [],
})

// ===== 版本号 + 变更检测（10-10 新增） =====
/** 初始工序快照（加载时的 items，用于对比变更） */
let initialItemsSnapshot = ''
/** 变更说明（用户输入） */
const changeNote = ref('')

/** 展示版本号（优先 version 字段，兜底 routingVersion） */
const displayVersion = computed(() => formData.version || formData.routingVersion || '-')
/** 父版本号提示 */
const parentVersionHint = computed(() => '上一版')
/** 下一版本号提示（V1.0 → V2.0） */
const nextVersionHint = computed(() => {
  const cur = displayVersion.value
  const m = cur.match(/V?(\d+)/)
  if (!m) return 'V2.0'
  return `V${Number(m[1]) + 1}.0`
})

/** 是否有内容变更（工序增删改） */
const hasChanges = ref(false)

/** 生成 items 快照（忽略 itemId/groupId 等易变字段，只比工序构成业务字段） */
function snapshotItems(items: any[]): string {
  return JSON.stringify((items || []).map((it) => ({
    processId: it.processId,
    stdProcessId: it.stdProcessId,
    processName: it.processName,
    processCategory: it.processCategory,
    processOrder: it.processOrder,
    customLaborHours: it.customLaborHours,
    customMachineHours: it.customMachineHours,
    isOptional: it.isOptional,
    indexNumber: it.indexNumber,
  })))
}

/** 检测变更：对比当前编辑器 items 与初始快照 */
function detectChanges() {
  const current = routeItemEditorRef.value?.getItems() || []
  hasChanges.value = snapshotItems(current) !== initialItemsSnapshot
}

const rules = reactive<FormRules<ProductRouteFormData>>({
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  routingCode: [{ required: true, message: '请输入路线编码', trigger: 'blur' }],
  routingName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  routingVersion: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
})

// 加载标准工序
const loadStandardProcesses = async () => {
  try {
    const response = await productRouteApi.getEnabledProcesses()
    standardProcesses.value = response.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

// 加载工艺路线详情
const loadRouteDetail = async () => {
  if (!routingId) {
    ElMessage.error('缺少工艺路线ID')
    return
  }

  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(routingId)
    const detail = response.data
    if (!detail) {
      ElMessage.error('加载工艺路线详情失败')
      return
    }

    const items = (detail.items || []).map((item: any) => ({
      itemId: item.itemId || 0,
      routingId: item.routingId || 0,
      groupId: item.groupId || undefined,
      groupOrder: item.groupOrder || 0,
      groupName: item.groupName || '',
      processId: item.processId ?? undefined,
      processOrder: item.processOrder || 0,
      customLaborHours: item.customLaborHours || 0,
      customMachineHours: item.customMachineHours || 0,
      customProcessParams: item.customProcessParams || '',
      description: item.description || '',
      processCategory: item.processCategory || '',
      // 2026-08-11 修复：补齐显示字段（否则编辑页图标/文字/下标全丢）
      processName: item.processName || '',
      processCode: item.processCode || '',
      icon: item.icon || '',
      hasIndex: item.hasIndex ?? 0,
      indexNumber: item.indexNumber ?? null,
    }))

    Object.assign(formData, {
      routingId: detail.routingId,
      routingCode: detail.routingCode,
      routingName: detail.routingName,
      productId: detail.productId,
      productCode: detail.productCode,
      productName: detail.productName,
      routingVersion: detail.routingVersion,
      version: detail.version,
      parentRoutingId: detail.parentRoutingId,
      sourceSampleId: detail.sourceSampleId,
      description: detail.description,
      remark: detail.remark,
      items,
    })

    // 初始快照（用于变更检测）
    initialItemsSnapshot = snapshotItems(items)
    hasChanges.value = false
    changeNote.value = ''

    // 等待组件挂载后，通过 ref 设置 RouteItemIconEditor 的数据
    await nextTick()
    if (routeItemEditorRef.value) {
      routeItemEditorRef.value.setItems(items as any)
    }
    // 编辑器渲染完成后取一次 getItems 作为变更检测基准（避免 processOrder/分组差异误报）
    setTimeout(() => {
      initialItemsSnapshot = snapshotItems(routeItemEditorRef.value?.getItems() || [])
      hasChanges.value = false
    }, 300)
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
    ElMessage.error('加载工艺路线详情失败')
  } finally {
    loading.value = false
  }
}

// 提交保存
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    // 从 RouteItemIconEditor 获取最新的工序数据
    const items = routeItemEditorRef.value?.getItems() || []
    if (items.length === 0) {
      ElMessage.warning('请至少添加一道工序')
      return
    }

    // 检测是否有变更（对比初始快照）
    const changed = snapshotItems(items) !== initialItemsSnapshot

    submitLoading.value = true
    const payload: any = { ...formData, items }
    if (changed) {
      // 有变更 → 自动升版
      payload.bumpVersion = true
      payload.changeNote = changeNote.value?.trim() || ''
    } else {
      payload.bumpVersion = false
    }
    const res = await productRouteApi.editProductRoute(routingId, payload)
    const newVersion = res?.data?.version || res?.data?.routingVersion
    if (changed) {
      ElMessage.success(`保存成功，已升级为 ${newVersion}（旧版本失效）`)
      // 刷新为新版本
      if (res?.data?.routingId) {
        router.replace(`/product/route/edit/${res.data.routingId}`)
        loadRouteDetail()
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    router.push('/engineering/route')
  } catch (error) {
    console.error('保存工艺路线失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 返回
const handleBack = () => {
  router.push('/engineering/route')
}

onMounted(() => {
  loadStandardProcesses()
  loadRouteDetail()
})
</script>

<style scoped>
.product-route-edit {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.version-badge {
  font-size: 13px;
  color: #606266;
  display: inline-flex;
  align-items: center;
}
</style>
