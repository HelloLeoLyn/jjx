<template>
  <div class="sys-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">系统配置</span>
          <span class="card-sub">按分组管理系统参数（公司信息/业务参数/预警阈值等）</span>
        </div>
      </template>

      <div class="config-layout">
        <!-- 左侧分组 -->
        <div class="config-sidebar">
          <div
            v-for="g in groups"
            :key="g.group"
            class="group-item"
            :class="{ active: activeGroup === g.group }"
            @click="activeGroup = g.group"
          >
            <span class="group-name">{{ groupLabel(g.group) }}</span>
            <span class="group-count">{{ g.count }}</span>
          </div>
        </div>

        <!-- 右侧配置表单 -->
        <div class="config-content" v-loading="loading">
          <template v-if="activeGroup">
            <el-form :model="formMap" label-width="160px" style="max-width: 620px">
              <div v-for="item in activeConfigs" :key="item.configId" class="config-item">
                <el-form-item :label="item.configName">
                  <!-- 颜色选择器 -->
                  <el-color-picker
                    v-if="isColorKey(item.configKey)"
                    v-model="formMap[item.configKey]"
                  />
                  <!-- 开关 -->
                  <el-switch
                    v-else-if="isSwitchKey(item.configKey)"
                    v-model="formMap[item.configKey]"
                    active-value="1"
                    inactive-value="0"
                  />
                  <!-- 数字 -->
                  <el-input-number
                    v-else-if="isNumberValue(item.configValue)"
                    v-model="formMap[item.configKey]"
                    :min="0"
                    style="width: 200px"
                  />
                  <!-- 文本 -->
                  <el-input
                    v-else
                    v-model="formMap[item.configKey]"
                    :placeholder="item.remark || `请输入${item.configName}`"
                    style="width: 100%"
                  />
                  <div v-if="item.remark && !isColorKey(item.configKey)" class="config-tip">
                    {{ item.remark }}
                  </div>
                </el-form-item>
              </div>

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
                <el-button @click="loadData">重置</el-button>
              </el-form-item>
            </el-form>
          </template>
          <el-empty v-else description="请选择左侧分组" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysConfigApi, type SysConfigItem } from '@/api/system/sysConfig'

const loading = ref(false)
const saving = ref(false)

const allConfigs = ref<SysConfigItem[]>([])
const activeGroup = ref('')

// 分组（保留数据库顺序）
const groups = computed(() => {
  const seen = new Set<string>()
  const result: { group: string; count: number }[] = []
  for (const item of allConfigs.value) {
    const g = item.configGroup
    if (!seen.has(g)) {
      seen.add(g)
      result.push({ group: g, count: 0 })
    }
    const found = result.find((r) => r.group === g)
    if (found) found.count++
  }
  return result
})

const activeConfigs = computed(() =>
  allConfigs.value.filter((c) => c.configGroup === activeGroup.value),
)

// 表单值映射：configKey → value
const formMap = ref<Record<string, any>>({})

const GROUP_LABELS: Record<string, string> = {
  business: '业务参数',
  inventory: '库存',
  sales: '销售',
  email: '邮件',
  sms: '短信',
  pdf_template: 'PDF模板 / 公司信息',
  system: '系统',
}

function groupLabel(group: string): string {
  return GROUP_LABELS[group] || group
}

// 字段类型判断
function isColorKey(key: string): boolean {
  return key.includes('color') || key.includes('theme')
}

function isSwitchKey(key: string): boolean {
  return key.startsWith('show_') || key.startsWith('is_') || key.startsWith('enable')
}

function isNumberValue(value?: string): boolean {
  if (!value) return false
  return /^-?\d+(\.\d+)?$/.test(value.trim())
}

async function loadData() {
  loading.value = true
  try {
    const res = await sysConfigApi.list()
    allConfigs.value = res.data || []
    // 默认选中第一个分组
    if (groups.value.length > 0 && !activeGroup.value) {
      activeGroup.value = groups.value[0].group
    }
    buildForm()
  } catch (e) {
    console.error('加载配置失败:', e)
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

function buildForm() {
  const map: Record<string, any> = {}
  for (const item of allConfigs.value) {
    map[item.configKey] = item.configValue ?? ''
  }
  formMap.value = map
}

async function handleSave() {
  saving.value = true
  try {
    // 只保存当前分组的配置
    for (const item of activeConfigs.value) {
      const newValue = String(formMap.value[item.configKey] ?? '')
      if (newValue !== (item.configValue ?? '')) {
        await sysConfigApi.update(item.configId, newValue)
      }
    }
    ElMessage.success('保存成功')
    await loadData()
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
.sys-config-page {
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

.config-layout {
  display: flex;
  gap: 16px;
  min-height: 400px;
}

.config-sidebar {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  padding-right: 12px;
}

.group-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}

.group-item:hover {
  background: #f5f7fa;
}

.group-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.group-count {
  font-size: 12px;
  color: #909399;
  background: #f0f2f5;
  border-radius: 10px;
  padding: 0 8px;
}

.group-item.active .group-count {
  background: #d9ecff;
  color: #409eff;
}

.config-content {
  flex: 1;
  padding: 0 8px;
}

.config-item {
  border-bottom: 1px dashed #ebeef5;
  padding: 4px 0;
}

.config-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
