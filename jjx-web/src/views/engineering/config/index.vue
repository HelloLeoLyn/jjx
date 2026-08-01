<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>产品配置模型</span>
          <el-button type="primary" size="small" icon="Plus" @click="openCreate">新增模型</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border size="default">
        <el-table-column prop="modelCode" label="模型编码" width="140" />
        <el-table-column prop="modelName" label="模型名称" min-width="160" />
        <el-table-column label="所属产品" min-width="160">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.isDefault !== 1" link type="warning" size="small" @click="handleSetDefault(row)">设默认</el-button>
            <el-button link :type="row.status === 1 ? 'info' : 'success'" size="small" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑配置模型' : '新增配置模型'" size="640px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="模型编码" required>
          <el-input v-model="form.modelCode" placeholder="如 MBS-STD-A" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="如 标准面板A款" />
        </el-form-item>
        <el-form-item label="所属产品" required>
          <el-select v-model="form.productId" filterable placeholder="选择产品" style="width:100%">
            <el-option v-for="p in productOptions" :key="p.productId" :label="`${p.productCode} ${p.productName}`" :value="p.productId" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>

        <el-divider content-position="left">配置选项</el-divider>
        <div v-for="(opt, idx) in form.options" :key="idx" style="border:1px solid #eee;border-radius:6px;padding:10px;margin-bottom:8px">
          <div style="display:flex;gap:8px;align-items:center;margin-bottom:6px">
            <el-input v-model="opt.optionCode" placeholder="选项编码" style="width:130px" />
            <el-input v-model="opt.optionName" placeholder="选项名称" style="width:150px" />
            <el-select v-model="opt.optionType" placeholder="类型" style="width:100px">
              <el-option label="输入" value="input" />
              <el-option label="单选" value="select" />
              <el-option label="复选" value="checkbox" />
            </el-select>
            <el-button type="danger" size="small" link @click="form.options.splice(idx, 1)">删</el-button>
          </div>
          <el-input v-model="opt.valueJson" type="textarea" :rows="2" placeholder='选项值JSON，如 ["透明","磨砂"] 或 {"0":"无","1":"有"}' />
        </div>
        <el-button type="primary" size="small" plain icon="Plus" @click="addOption">添加选项</el-button>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { configModelApi } from '@/api/product/configModel'
import request from '@/utils/request'

defineOptions({ name: 'EngineeringConfig' })

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const productOptions = ref<any[]>([])
const drawerVisible = ref(false)
const isEdit = ref(false)

const form = reactive<any>({
  modelId: null, modelCode: '', modelName: '', productId: null, remark: '', options: [],
})

async function getList() {
  loading.value = true
  try {
    const res = await configModelApi.list()
    list.value = (res as any).data || []
  } finally {
    loading.value = false
  }
}

async function getProducts() {
  try {
    const res = await request.get('/product/page', { params: { pageNum: 1, pageSize: 200 } })
    productOptions.value = (res as any).data?.records || (res as any).data || []
  } catch {
    productOptions.value = []
  }
}

function productName(pid: number) {
  return productOptions.value.find(p => p.productId === pid)?.productName || `#${pid}`
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { modelId: null, modelCode: '', modelName: '', productId: null, remark: '', options: [] })
  addOption()
  drawerVisible.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  try {
    const res = await configModelApi.detail(row.modelId)
    const detail = (res as any).data
    Object.assign(form, {
      modelId: detail.model.modelId,
      modelCode: detail.model.modelCode,
      modelName: detail.model.modelName,
      productId: detail.model.productId,
      remark: detail.model.remark || '',
      options: (detail.options || []).map((o: any) => ({
        optionId: o.optionId, optionCode: o.optionCode, optionName: o.optionName,
        optionType: o.optionType || 'input', valueJson: o.valueJson || '', isRequired: o.isRequired,
      })),
    })
    if (form.options.length === 0) addOption()
    drawerVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

function addOption() {
  form.options.push({ optionCode: '', optionName: '', optionType: 'input', valueJson: '', isRequired: 0 })
}

async function handleSave() {
  if (!form.modelCode || !form.modelName || !form.productId) {
    ElMessage.warning('请填写编码、名称和所属产品')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, options: form.options.filter((o: any) => o.optionCode && o.optionName) }
    if (isEdit.value) {
      await configModelApi.update(payload)
      ElMessage.success('已更新')
    } else {
      await configModelApi.create(payload)
      ElMessage.success('已创建')
    }
    drawerVisible.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleSetDefault(row: any) {
  try {
    await configModelApi.setDefault(row.modelId)
    ElMessage.success('已设为默认')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleToggleStatus(row: any) {
  try {
    await configModelApi.changeStatus(row.modelId, row.status === 1 ? 0 : 1)
    ElMessage.success('状态已更新')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除模型「${row.modelName}」？`, '删除确认', { type: 'warning' })
    await configModelApi.remove(row.modelId)
    ElMessage.success('已删除')
    getList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  getList()
  getProducts()
})
</script>
