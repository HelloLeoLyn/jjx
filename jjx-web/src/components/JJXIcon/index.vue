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
            :class="{ active: modelValue === icon.name }"
            @click="handleSelect(icon.name)"
          >
            <SvgIcon :name="`${icon.name}`" :size="32" />
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

interface Props {
  modelValue?: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
})

const emit = defineEmits<Emits>()

const searchKey = ref('')
const activeTab = ref('mianban')

// 面板分类
const mianban = [
  { name: '面板', label: '面板' },
  { name: '面板2', label: '面板2' },
  { name: '面板隔片', label: '面板隔片' },
  { name: '面板背胶', label: '面板背胶' },
  { name: '面板裁切', label: '面板裁切' },
  { name: '面板裁切', label: '面板裁切' },
  { name: '面板保护膜', label: '面板保护膜' },
  { name: '面板凹凸', label: '面板凹凸' },
  { name: '面板冲孔', label: '面板冲孔' },
  { name: '面板冲形', label: '面板冲形' },
  { name: '面板冲形 (1)', label: '面板冲形' },
  { name: '垫片', label: '垫片' },
  { name: '面板冲第一刀', label: '面板冲第一刀' },
  { name: '面板冲第二刀', label: '面板冲第二刀' },
]

// 上线分类
const shangxian = [
  { name: '上线', label: '上线' },
  { name: '上线隔片', label: '上线隔片' },
  { name: '上线裁切', label: '上线裁切' },
  { name: '上线加强片', label: '上线加强片' },
  { name: '上线保护膜', label: '上线保护膜' },
  { name: '上线凹凸', label: '上线凹凸' },
  { name: '上线冲孔', label: '上线冲孔' },
  { name: '上线冲第一刀', label: '上线冲第一刀' },
  { name: '上线冲型', label: '上线冲型' },
]

// 下线分类
const xiaxian = [
  { name: '下线', label: '下线' },
  { name: '下线隔片', label: '下线隔片' },
  { name: '下线隔片 (1)', label: '下线隔片' },
  { name: '下线裁切', label: '下线裁切' },
  { name: '下线加强片', label: '下线加强片' },
  { name: '下线保护膜', label: '下线保护膜' },
  { name: '下线背胶', label: '下线背胶' },
  { name: '下线冲孔', label: '下线冲孔' },
  { name: '下线第一刀', label: '下线第一刀' },
  { name: '下线第一刀 (1)', label: '下线第一刀' },
  { name: '下线冲型', label: '下线冲型' },
  { name: '下线连接器', label: '下线连接器' },
]

// 弹片分类
const tanpian = [
  { name: '弹片', label: '弹片' },
  { name: '弹片上贴黑豆', label: '弹片上贴黑豆' },
]

// 其他分类
const others = [
  { name: 'LED', label: 'LED' },
  { name: '成品', label: '成品' },
  { name: '打公PIN', label: '打公PIN' },
  { name: '打母PIN', label: '打母PIN' },
  { name: '撕水性保护膜', label: '撕水性保护膜' },
  { name: '清洁', label: '清洁' },
  { name: '裁切', label: '裁切' },
  { name: '线路测阻值', label: '线路测阻值' },
  { name: 'OHM', label: 'OHM' },
  { name: '贴RUBBER胶', label: '贴RUBBER胶' },
  { name: '贴周期码', label: '贴周期码' },
  { name: '贴离形纸', label: '贴离形纸' },
  { name: '长方形', label: '长方形' },
  { name: '凸台', label: '凸台' },
  { name: '包装', label: '包装' },
  { name: '包装2', label: '包装2' },
  { name: '品检', label: '品检' },
  { name: 'QC', label: 'QC' },
  { name: '垫片', label: '垫片' },
  { name: '连接器', label: '连接器' },
  { name: '连接器与适配器', label: '连接器与适配器' },
]

// 未使用分类
const nouse = [
  { name: 'omega', label: 'omega' },
  { name: 'rect', label: 'rect' },
  { name: 'rect-split', label: 'rect-split' },
  { name: 'shape-d', label: 'shape-d' },
  { name: 't_田字格', label: '田字格' },
  { name: 'tx-正方形', label: '正方形' },
  { name: '三角形', label: '三角形' },
  { name: '三角形 (1)', label: '三角形' },
]

// 标签页配置
const iconTabs = [
  { name: 'mianban', label: '面板' },
  { name: 'shangxian', label: '上线' },
  { name: 'xiaxian', label: '下线' },
  { name: 'tanpian', label: '弹片' },
  { name: 'others', label: '其他' },
  { name: 'nouse', label: '未使用' },
]

// 图标分类映射
const iconCategoryMap: Record<string, { name: string; label: string }[]> = {
  mianban,
  shangxian,
  xiaxian,
  tanpian,
  others,
  nouse,
}

// 获取过滤后的图标
const getFilteredIcons = (tabName: string) => {
  const icons = iconCategoryMap[tabName] || []
  if (!searchKey.value) return icons
  const key = searchKey.value.toLowerCase()
  return icons.filter(
    (icon) => icon.name.toLowerCase().includes(key) || icon.label.includes(searchKey.value)
  )
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
    }
  }
}
</style>
