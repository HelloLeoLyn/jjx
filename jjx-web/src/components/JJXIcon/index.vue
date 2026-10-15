<template>
  <div class="jjx-icon-selector">
    <!-- 搜索框 -->
    <div class="search-bar">
    <el-input
        v-model="searchKey"
        placeholder="搜索图标"
        clearable
        size="small"
      prefix-icon="Search"
    />
    <el-upload
      v-if="canUpload"
      :show-file-list="false"
      accept="image/png,image/jpeg,image/svg+xml"
      :http-request="uploadIcon"
    >
      <el-button size="small" type="primary" plain>上传 JJX 图标</el-button>
    </el-upload>
    </div>

    <!-- 分类标签 -->
    <el-tabs v-model="activeTab" type="border-card" class="icon-tabs">
      <el-tab-pane v-for="tab in iconTabs" :key="tab.name" :label="tab.label" :name="tab.name">
        <!-- 图标网格 -->
        <div class="icon-grid">
          <div
            v-for="icon in getFilteredIcons(tab.name)"
            :key="icon.name"
            class="icon-item"
            :class="{ active: modelValue === icon.value }"
            @click="handleSelect(icon.value)"
          >
            <img v-if="icon.previewUrl" :src="icon.previewUrl" class="uploaded-icon" />
            <SvgIcon v-else :name="`${icon.value}`" :size="32" />
            <span class="icon-name">{{ icon.label }}</span>
          </div>
        </div>

        <!-- 无结果提示 -->
        <el-empty
          v-if="getFilteredIcons(tab.name).length === 0"
          description="未找到匹配的图标"
          :image-size="60"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { useDict } from '@/composables/useDict'
import { uploadProcessIcon } from '@/api/system/processIcon'
import { usePermission } from '@/composables/usePermission'

interface Props {
  modelValue?: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
}
type IconOption = {
  name: string
  value: string
  label: string
  category?: string
  previewUrl?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
})

const emit = defineEmits<Emits>()
const iconDictCode = 'engineering_jjx_icon'
const { options: dictionaryOptions, refresh: refreshDictionary } = useDict(iconDictCode)
const { hasAnyPermission } = usePermission()
const canUpload = computed(() => hasAnyPermission(['engineering:standardProcess:add', 'engineering:standardProcess:edit']))

const searchKey = ref('')
const activeTab = ref('mianban')

// 标签页配置
const iconTabs = [
  { name: 'mianban', label: '面板' },
  { name: 'shangxian', label: '上线' },
  { name: 'xiaxian', label: '下线' },
  { name: 'tanpian', label: '弹片' },
  { name: 'others', label: '其他' },
  { name: 'nouse', label: '未使用' },
]

const dictionaryIcons = computed(() =>
  dictionaryOptions.value
    .filter((item) => item.isActive !== 0)
    .map((item) => {
      let meta: { category?: string; previewUrl?: string } = {}
      try {
        meta = item.remark ? JSON.parse(item.remark) : {}
      } catch {
        // 兼容历史非 JSON 备注
      }
      return {
        name: item.itemKey,
        value: item.itemValue || item.itemKey,
        label: item.label || item.itemKey,
        category: meta.category || 'others',
        previewUrl: meta.previewUrl,
      }
    })
)

// 获取过滤后的图标
const getFilteredIcons = (tabName: string): IconOption[] => {
  // 字典是唯一运行时来源；字典接口不可用时显示空状态，避免继续展示已失效的硬编码数组。
  const icons = dictionaryIcons.value.filter((icon) => icon.category === tabName)
  if (!searchKey.value) return icons
  const key = searchKey.value.toLowerCase()
  return icons.filter(
    (icon) => icon.name.toLowerCase().includes(key) || icon.label.includes(searchKey.value)
  )
}

/** 每次用户上传使用独立幂等键。 */
function buildIconRequestKey(): string {
  const uuid = globalThis.crypto?.randomUUID?.()
  const raw = uuid
    ? uuid.replace(/-/g, '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`
  return raw.replace(/[^A-Za-z0-9_-]/g, '').padEnd(16, '0').slice(0, 64)
}

async function uploadIcon(options: UploadRequestOptions) {
  try {
    if (options.file.size > 1024 * 1024) throw new Error('图标不能超过1MB')
    const name = await ElMessageBox.prompt('请输入图标名称', '上传 JJX 图标', {
      inputValue: options.file.name.replace(/\.[^.]+$/, ''),
      inputValidator: (value: string) => (value.trim().length > 0 && value.trim().length <= 50) || '图标名称应为1至50个字符',
    })
    // 上传、图标库登记和永久化由服务端同一事务完成，不要求系统字典权限。
    const uploaded = await uploadProcessIcon(options.file as File, buildIconRequestKey(), name.value.trim(), activeTab.value)
    const attachmentId = Number(uploaded?.data)
    if (!attachmentId) throw new Error('图标上传失败')
    await refreshDictionary()
    ElMessage.success('图标已上传并加入 JJX 图标库')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '图标上传失败')
  }
}

// 选择图标
const handleSelect = (name: string) => {
  emit('update:modelValue', name)
}
</script>

<style scoped lang="scss">
.jjx-icon-selector {
  .search-bar {
    margin-bottom: 12px;
  }

  .icon-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }

    :deep(.el-tabs__content) {
      overflow: visible;
    }
  }

  .icon-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
    gap: 8px;
    max-height: 360px;
    overflow-y: auto;
    padding: 4px;

    .icon-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 12px 4px 8px;
      border: 1px solid #e4e7ed;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        border-color: #409eff;
        background-color: #ecf5ff;
      }

      &.active {
        border-color: #409eff;
        background-color: #ecf5ff;
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }

      .icon-name {
        margin-top: 6px;
        font-size: 11px;
        color: #606266;
        text-align: center;
        line-height: 1.3;
        word-break: break-all;
      }

      .uploaded-icon {
        width: 32px;
        height: 32px;
        object-fit: contain;
      }
    }
  }
}
</style>
