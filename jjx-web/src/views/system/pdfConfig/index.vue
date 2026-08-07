<template>
  <div class="pdf-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">PDF 单据模板配置</span>
          <span class="card-sub">配置后对报价单/订单/出入库/采购/生产等所有单据 PDF 生效</span>
        </div>
      </template>

      <el-form :model="form" label-width="140px" style="max-width: 640px" v-loading="loading">
        <!-- 公司信息 -->
        <el-divider content-position="left">公司信息（单据抬头）</el-divider>
        <el-form-item label="公司名称">
          <el-input v-model="form.company_name" placeholder="如：江苏某某薄膜开关有限公司" />
        </el-form-item>
        <el-form-item label="公司地址">
          <el-input v-model="form.company_address" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.company_phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.company_email" placeholder="选填" />
        </el-form-item>

        <!-- 样式 -->
        <el-divider content-position="left">样式</el-divider>
        <el-form-item label="主题色">
          <div class="color-row">
            <el-color-picker v-model="form.theme_color" />
            <span class="color-value">{{ form.theme_color }}</span>
          </div>
        </el-form-item>
        <el-form-item label="显示公司抬头">
          <el-switch v-model="showHeader" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="显示页脚公司名">
          <el-switch v-model="showFooter" active-value="1" inactive-value="0" />
        </el-form-item>

        <!-- 签名栏 -->
        <el-divider content-position="left">签名栏</el-divider>
        <el-form-item label="签名栏1">
          <el-input v-model="form.signature_label1" />
        </el-form-item>
        <el-form-item label="签名栏2">
          <el-input v-model="form.signature_label2" />
        </el-form-item>
        <el-form-item label="签名栏3">
          <el-input v-model="form.signature_label3" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-button @click="loadData">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysConfigApi, type SysConfigItem } from '@/api/system/sysConfig'

const loading = ref(false)
const saving = ref(false)

const form = reactive<Record<string, string>>({
  company_name: '',
  company_address: '',
  company_phone: '',
  company_email: '',
  theme_color: '#2B5AA7',
  signature_label1: '销售负责人',
  signature_label2: '客户确认',
  signature_label3: '日期',
})

const showHeader = ref('1')
const showFooter = ref('1')

// key → 表单字段映射
const FIELD_MAP: Record<string, string> = {
  company_name: 'company_name',
  company_address: 'company_address',
  company_phone: 'company_phone',
  company_email: 'company_email',
  theme_color: 'theme_color',
  show_header: 'show_header',
  show_footer: 'show_footer',
  signature_label1: 'signature_label1',
  signature_label2: 'signature_label2',
  signature_label3: 'signature_label3',
}

async function loadData() {
  loading.value = true
  try {
    const res = await sysConfigApi.listByGroup('pdf_template')
    const list: SysConfigItem[] = res.data || []
    for (const item of list) {
      const field = FIELD_MAP[item.configKey]
      if (!field) continue
      if (field === 'show_header') showHeader.value = item.configValue ?? '1'
      else if (field === 'show_footer') showFooter.value = item.configValue ?? '1'
      else form[field] = item.configValue ?? ''
    }
  } catch (e) {
    console.error('加载配置失败:', e)
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const res = await sysConfigApi.listByGroup('pdf_template')
    const list: SysConfigItem[] = res.data || []
    const idMap: Record<string, number> = {}
    for (const item of list) {
      const field = FIELD_MAP[item.configKey]
      if (field) idMap[field] = item.configId
    }

    // 组装待保存值
    const values: Record<string, string> = {
      company_name: form.company_name,
      company_address: form.company_address,
      company_phone: form.company_phone,
      company_email: form.company_email,
      theme_color: form.theme_color,
      show_header: showHeader.value,
      show_footer: showFooter.value,
      signature_label1: form.signature_label1,
      signature_label2: form.signature_label2,
      signature_label3: form.signature_label3,
    }

    for (const [field, value] of Object.entries(values)) {
      const id = idMap[field]
      if (id) {
        await sysConfigApi.update(id, value)
      }
    }
    ElMessage.success('保存成功，下次导出 PDF 生效')
  } catch (e) {
    console.error('保存配置失败:', e)
    ElMessage.error('保存配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.pdf-config-page {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  color: #909399;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-value {
  font-size: 13px;
  color: #606266;
  font-family: monospace;
}
</style>
