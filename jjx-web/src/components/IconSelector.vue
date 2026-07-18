<!-- components/IconSelector.vue -->
<template>
  <div class="icon-selector">
    <!-- 触发按钮 -->
    <div class="icon-trigger" @click="openDialog">
      <div class="icon-preview">
        <!-- 预览 SVG 图标 -->
        <svg-icon
          v-if="selectedIconType === 'svg' && selectedIcon"
          :name="selectedIcon"
          :size="24"
        />
        <!-- 预览 Element 图标 -->
        <el-icon v-else-if="selectedIconType === 'el' && selectedIcon" :size="24">
          <component :is="selectedIcon" />
        </el-icon>
        <!-- 未选择图标 -->
        <!-- <span v-else class="placeholder">未选择</span> -->
      </div>
      <el-icon class="arrow-icon"><ArrowDown /></el-icon>
    </div>

    <!-- 图标选择弹窗 -->
    <el-dialog v-model="dialogVisible" title="选择图标" width="800px" :close-on-click-modal="false">
      <!-- 标签页切换 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="Element 图标" name="el">
          <div class="icon-search">
            <el-input
              v-model="elSearchText"
              placeholder="搜索图标"
              clearable
              prefix-icon="Search"
            />
          </div>
          <div class="icon-grid">
            <div
              v-for="icon in filteredElIcons"
              :key="icon"
              class="icon-item"
              :class="{ active: selectedIcon === icon && selectedIconType === 'el' }"
              @click="selectElIcon(icon)"
            >
              <el-icon :size="24"><component :is="icon" /></el-icon>
              <span class="icon-name">{{ icon }}</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="SVG 图标" name="svg">
          <div class="icon-search">
            <el-input
              v-model="svgSearchText"
              placeholder="搜索图标"
              clearable
              prefix-icon="Search"
            />
          </div>
          <div class="icon-grid">
            <div
              v-for="icon in filteredSvgIcons"
              :key="icon"
              class="icon-item"
              :class="{ active: selectedIcon === icon && selectedIconType === 'svg' }"
              @click="selectSvgIcon(icon)"
            >
              <svg-icon :name="icon" :size="24" />
              <span class="icon-name">{{ icon }}</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 底部操作 -->
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="clearIcon">清空图标</el-button>
          <el-button type="primary" @click="confirmSelect">确认选择</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ArrowDown, Search } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  // 图标类型：'el' 或 'svg'
  iconType: {
    type: String,
    default: 'el',
  },
})

// Emits
const emit = defineEmits(['update:modelValue', 'update:iconType', 'change'])

// 状态
const dialogVisible = ref(false)
const activeTab = ref(props.iconType === 'svg' ? 'svg' : 'el')
const selectedIcon = ref(props.modelValue)
const selectedIconType = ref(props.iconType)
const elSearchText = ref('')
const svgSearchText = ref('')

// 获取所有 Element 图标（排除组件内部使用的特殊图标）
const getAllElIcons = () => {
  const excludeIcons = ['ArrowDown', 'Search'] // 排除已在组件中使用的图标
  return Object.keys(ElementPlusIconsVue)
    .filter((key) => !excludeIcons.includes(key))
    .sort()
}

// 获取所有 SVG 图标（动态扫描 icons/svg 目录）
const getAllSvgIcons = () => {
  try {
    // 使用 Vite 的 import.meta.glob 动态获取 icons/svg 目录下的所有 SVG 文件
    const svgModules = import.meta.glob(['@/icons/svg/*.svg', '@/icons/jjx/*.svg'], {
      eager: false,
    })

    console.log('SVG 模块数量:', Object.keys(svgModules).length)
    console.log('SVG 模块路径:', Object.keys(svgModules))

    // 提取文件名（不带扩展名）作为图标名称
    const iconNames = Object.keys(svgModules).map((filePath) => {
      // 文件路径示例：/src/icons/svg/analysisDashboard.svg
      const fileName = filePath.split('/').pop() // 获取文件名，如 analysisDashboard.svg
      const iconName = fileName.replace('.svg', '') // 移除 .svg 扩展名
      return iconName
    })

    console.log('提取的 SVG 图标名称:', iconNames)

    // 按字母顺序排序
    const sortedIcons = iconNames.sort()
    console.log('排序后的 SVG 图标:', sortedIcons)

    return sortedIcons
  } catch (error) {
    console.error('动态获取 SVG 图标失败:', error)
    // 如果动态获取失败，返回一个空数组作为回退
    return []
  }
}

// 图标列表
const elIcons = ref(getAllElIcons())
const svgIcons = ref(getAllSvgIcons())

// 过滤后的图标
const filteredElIcons = computed(() => {
  if (!elSearchText.value) return elIcons.value
  return elIcons.value.filter((icon) =>
    icon.toLowerCase().includes(elSearchText.value.toLowerCase())
  )
})

const filteredSvgIcons = computed(() => {
  if (!svgSearchText.value) return svgIcons.value
  return svgIcons.value.filter((icon) =>
    icon.toLowerCase().includes(svgSearchText.value.toLowerCase())
  )
})

// 方法
const openDialog = () => {
  dialogVisible.value = true
  // 同步当前选中的图标
  selectedIcon.value = props.modelValue
  selectedIconType.value = props.iconType
  activeTab.value = props.iconType === 'svg' ? 'svg' : 'el'
}

const handleTabChange = (tab) => {
  // 切换标签页时清空搜索
  if (tab === 'el') {
    elSearchText.value = ''
  } else {
    svgSearchText.value = ''
  }
}

const selectElIcon = (icon) => {
  selectedIcon.value = icon
  selectedIconType.value = 'el'
}

const selectSvgIcon = (icon) => {
  selectedIcon.value = icon
  selectedIconType.value = 'svg'
}

const clearIcon = () => {
  selectedIcon.value = ''
  selectedIconType.value = 'el'
}

const confirmSelect = () => {
  emit('update:modelValue', selectedIcon.value)
  emit('update:iconType', selectedIconType.value)
  emit('change', {
    icon: selectedIcon.value,
    type: selectedIconType.value,
  })
  dialogVisible.value = false
}

// 监听外部变化
watch(
  () => props.modelValue,
  (newVal) => {
    selectedIcon.value = newVal
  }
)

watch(
  () => props.iconType,
  (newVal) => {
    selectedIconType.value = newVal
    activeTab.value = newVal === 'svg' ? 'svg' : 'el'
  }
)
</script>

<style scoped lang="scss">
.icon-selector {
  display: inline-block;
  width: 180px;
}

.icon-trigger {
  width: 180px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  background-color: #fff;
  transition: border-color 0.2s;

  &:hover {
    border-color: #409eff;
  }
}

.icon-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;

  .placeholder {
    width: 100%;
    font-size: 12px;
    color: #909399;
  }
}

.arrow-icon {
  font-size: 14px;
  color: #909399;
}

.icon-search {
  margin-bottom: 16px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 100px));
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
  padding: 8px 0;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
    background-color: #ecf5ff;
    transform: translateY(-2px);
  }

  &.active {
    border-color: #409eff;
    background-color: #ecf5ff;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
  }

  .icon-name {
    margin-top: 8px;
    font-size: 12px;
    color: #606266;
    text-align: center;
    word-break: keep-all;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
