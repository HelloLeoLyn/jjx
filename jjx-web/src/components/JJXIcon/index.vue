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
import { dictApi } from '@/api/system/dict'
import { attachmentApi } from '@/api/system/attachment'

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

async function uploadIcon(options: UploadRequestOptions) {
  try {
    const name = await ElMessageBox.prompt('请输入图标名称', '上传 JJX 图标', {
      inputValue: options.file.name.replace(/\.[^.]+$/, ''),
      inputPattern: /\S+/,
      inputErrorMessage: '图标名称不能为空',
    })
    const uploaded: any = await attachmentApi.upload(
      options.file as File,
      'engineering_jjx_icon',
      0,
      JSON.stringify({ category: activeTab.value }),
    )
    const attachmentId = uploaded?.data?.data ?? uploaded?.data
    const previewUrl = attachmentApi.downloadUrl(Number(attachmentId))
    await dictApi.addItem({
      dictCode: iconDictCode,
      itemKey: `jjx-upload-${attachmentId}`,
      itemValue: previewUrl,
      label: name.value,
      remark: JSON.stringify({ category: activeTab.value, source: 'UPLOAD', previewUrl }),
      sortOrder: 999,
      isActive: 1,
    })
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
