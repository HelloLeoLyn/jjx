<template>
  <div class="app-container bom-change">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">BOM 变更单（ECN-lite）</span>
          <div>
            <el-button type="primary" v-hasPermi="['product:bom:edit']" @click="openCreate">新建变更单</el-button>
            <el-button icon="Refresh" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" border size="small" v-loading="loading">
        <el-table-column prop="changeNo" label="变更号" width="180" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="effectiveDate" label="生效日期" width="120">
          <template #default="{ row }">{{ row.effectiveDate || '按审批日' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              link
              type="primary"
              v-hasPermi="['product:bom:edit']"
              @click="handleSubmit(row)"
              >提交</el-button
            >
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              v-hasPermi="['product:bom:approve']"
              @click="handleApprove(row)"
              >审批通过</el-button
            >
            <el-button
              v-if="row.status === 'APPROVED'"
              link
              type="primary"
              v-hasPermi="['product:bom:approve']"
              @click="handleActivate(row)"
              >立即生效</el-button
            >
            <el-button
              v-if="row.status !== 'EFFECTIVE' && row.status !== 'CANCELLED'"
              link
              type="danger"
              v-hasPermi="['product:bom:edit']"
              @click="handleCancel(row)"
              >取消</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="onPageChange"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新建 BOM 变更单" width="820px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" placeholder="如：多层 BOM 统一升版" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="留空按审批日" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="BOM 明细">
          <div class="item-editor">
            <div v-for="(item, index) in form.items" :key="index" class="item-row">
              <el-input-number v-model="item.bomId" :min="1" :controls="false" placeholder="BOM ID" />
              <el-input v-model="item.bomVersion" placeholder="版本（可空）" style="width: 140px" />
              <el-date-picker v-model="item.targetEffectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="行生效日" style="width: 150px" />
              <el-button link type="danger" @click="form.items.splice(index, 1)">移除</el-button>
            </div>
            <el-button link type="primary" @click="form.items.push({ bomId: undefined, bomVersion: '', targetEffectiveDate: '' })">+ 添加 BOM</el-button>
            <div class="hint">提交时会逐张执行单张校验；任一不通过整单不可提交。</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="变更单详情" width="820px" destroy-on-close>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="变更号">{{ detail?.changeNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail?.status) }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail?.title }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ detail?.effectiveDate || '按审批日' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail?.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ detail?.approvedBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生效人">{{ detail?.activatedBy || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailItems" border size="small" class="items">
        <el-table-column prop="bomId" label="BOM ID" width="100" />
        <el-table-column prop="bomVersion" label="版本" width="120" />
        <el-table-column prop="targetEffectiveDate" label="行生效日" width="130" />
        <el-table-column prop="validateResult" label="提交校验" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bomChangeApi, type BomChangeItem, type BomChangeRequest } from '@/api/product/bomChange'
import { statusLabel, statusType } from './status'

const rows = ref<BomChangeRequest[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<BomChangeRequest | null>(null)
const detailItems = ref<BomChangeItem[]>([])

const form = reactive({
  title: '',
  effectiveDate: '' as string | null,
  remark: '',
  items: [] as Array<{ bomId?: number; bomVersion?: string; targetEffectiveDate?: string | null }>,
})



async function loadList() {
  loading.value = true
  try {
    const response = await bomChangeApi.page({ pageNum: pageNum.value, pageSize })
    rows.value = response.data?.records || []
    total.value = Number(response.data?.total || 0)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(value: number) {
  pageNum.value = value
  void loadList()
}

function openCreate() {
  form.title = ''
  form.effectiveDate = ''
  form.remark = ''
  form.items = [{ bomId: undefined, bomVersion: '', targetEffectiveDate: '' }]
  createVisible.value = true
}

async function handleCreate() {
  if (!form.title.trim()) return void ElMessage.warning('请填写标题')
  const items = form.items.filter((row) => Number(row.bomId) > 0)
  if (!items.length) return void ElMessage.warning('至少添加一张 BOM')
  saving.value = true
  try {
    await bomChangeApi.create({
      title: form.title.trim(),
      effectiveDate: form.effectiveDate || null,
      remark: form.remark,
      items: items.map((row) => ({
        bomId: Number(row.bomId),
        bomVersion: row.bomVersion || null,
        targetEffectiveDate: row.targetEffectiveDate || null,
      })),
    })
    ElMessage.success('已保存草稿')
    createVisible.value = false
    await loadList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row: BomChangeRequest) {
  detail.value = row
  detailItems.value = []
  detailVisible.value = true
  try {
    const [head, items] = await Promise.all([bomChangeApi.detail(row.changeId), bomChangeApi.items(row.changeId)])
    detail.value = head.data || row
    detailItems.value = items.data || []
  } catch {
    /* 详情失败时保留列表行信息 */
  }
}

async function handleSubmit(row: BomChangeRequest) {
  try {
    await bomChangeApi.submit(row.changeId)
    ElMessage.success('已提交')
    await loadList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  }
}

async function handleApprove(row: BomChangeRequest) {
  try {
    await ElMessageBox.confirm(`审批通过变更单 ${row.changeNo}？`, '审批', { type: 'warning' })
    await bomChangeApi.approve(row.changeId)
    ElMessage.success('已审批通过')
    await loadList()
  } catch {
    /* 取消不提示 */
  }
}

async function handleActivate(row: BomChangeRequest) {
  try {
    await ElMessageBox.confirm(
      `立即生效会按批次统一启用该单全部 BOM，并让旧当前版本失效。继续？`,
      '立即生效',
      { type: 'warning' },
    )
    await bomChangeApi.activate(row.changeId, true)
    ElMessage.success('已立即生效')
    await loadList()
  } catch {
    /* 取消不提示 */
  }
}

async function handleCancel(row: BomChangeRequest) {
  try {
    await ElMessageBox.confirm(`取消变更单 ${row.changeNo}？取消不会改动任何 BOM。`, '取消', { type: 'warning' })
    await bomChangeApi.cancel(row.changeId)
    ElMessage.success('已取消')
    await loadList()
  } catch {
    /* 取消不提示 */
  }
}

onMounted(loadList)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.item-editor {
  width: 100%;
}
.item-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.items {
  margin-top: 12px;
}
</style>
