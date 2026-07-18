<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <span class="theme-picker-trigger">
      <el-tooltip content="主题色" placement="bottom" :role="undefined">
        <el-icon class="theme-picker-icon">
          <Brush />
        </el-icon>
      </el-tooltip>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="theme in themes"
          :key="theme.name"
          :command="theme.name"
          :class="{ active: currentTheme === theme.name }"
        >
          <div class="theme-item">
            <div class="theme-color" :style="{ backgroundColor: theme.color }"></div>
            <span>{{ theme.label }}</span>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Brush } from '@element-plus/icons-vue'

// 主题列表
const themes = ref([
  { name: 'default', label: '默认主题', color: '#409eff' },
  { name: 'green', label: '绿色主题', color: '#67c23a' },
  { name: 'orange', label: '橙色主题', color: '#e6a23c' },
  { name: 'red', label: '红色主题', color: '#f56c6c' },
  { name: 'purple', label: '紫色主题', color: '#8a2be2' }
])

// 当前主题
const currentTheme = ref('default')

// 处理主题切换
const handleCommand = (themeName: string) => {
  currentTheme.value = themeName
  applyTheme(themeName)
}

// 应用主题
const applyTheme = (themeName: string) => {
  // 移除现有的主题类
  document.documentElement.classList.remove('theme-default', 'theme-green', 'theme-orange', 'theme-red', 'theme-purple')

  // 添加新的主题类
  document.documentElement.classList.add(`theme-${themeName}`)

  // 保存到本地存储
  localStorage.setItem('theme', themeName)

  // 更新Element Plus主题色
  updateElementTheme(themeName)
}

// 更新Element Plus主题色
const updateElementTheme = (themeName: string) => {
  const theme = themes.value.find(t => t.name === themeName)
  if (theme) {
    // 这里可以添加更新Element Plus主题色的逻辑
    console.log('切换主题色:', theme.color)
  }
}

// 初始化主题
const initTheme = () => {
  const savedTheme = localStorage.getItem('theme') || 'default'
  currentTheme.value = savedTheme
  applyTheme(savedTheme)
}

// 初始化
initTheme()
</script>

<style scoped>
.theme-picker-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 5px;
  border-radius: 4px;

  &:hover {
    background-color: #f5f7fa;
  }
}

.theme-picker-icon {
  font-size: 18px;
  color: #666;

  &:hover {
    color: #409eff;
  }
}

.theme-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-color {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
}

.el-dropdown-item.active {
  background-color: #f5f7fa;
  color: #409eff;
}
</style>

<style>
/* 主题样式 */
.theme-default {
  --el-color-primary: #409eff;
}

.theme-green {
  --el-color-primary: #67c23a;
}

.theme-orange {
  --el-color-primary: #e6a23c;
}

.theme-red {
  --el-color-primary: #f56c6c;
}

.theme-purple {
  --el-color-primary: #8a2be2;
}
</style>
