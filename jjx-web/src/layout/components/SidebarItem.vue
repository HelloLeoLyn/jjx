<!-- src/layout/components/SidebarItem.vue -->
<template>
  <div v-if="!item.hidden">
    <template v-if="hasChildren">
      <el-sub-menu :index="resolvePath">
        <template #title>
          <svg-icon
            v-if="isSvgIcon"
            :name="item.icon!"
            :size="18"
            class="menu-icon"
          />
          <el-icon v-else-if="item.icon" :size="18" class="menu-icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.title }}</span>
        </template>
        <sidebar-item
          v-for="child in item.children"
          :key="child.path"
          :item="child"
          :base-path="resolvePath"
        />
      </el-sub-menu>
    </template>
    <template v-else>
      <el-menu-item :index="resolvePath" @click="handleClick">
        <svg-icon
          v-if="isSvgIcon"
          :name="item.icon!"
          :size="18"
          class="menu-icon"
        />
        <el-icon v-else-if="item.icon" :size="18" class="menu-icon">
          <component :is="item.icon" />
        </el-icon>
        <template #title>
          <span>{{ item.title }}</span>
        </template>
      </el-menu-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import path from 'path-browserify'
import type { MenuItem } from '@/types/system'
import SvgIcon from '@/components/SvgIcon/index.vue'

const props = defineProps({
  item: {
    type: Object as () => MenuItem,
    required: true,
  },
  basePath: {
    type: String,
    default: '',
  },
})

const router = useRouter()

// 常见 Element Plus 图标列表
const commonElementIcons = [
  'HomeFilled',
  'Setting',
  'User',
  'UserFilled',
  'Menu',
  'OfficeBuilding',
  'Goods',
  'List',
  'Folder',
  'Document',
  'SetUp',
  'Box',
  'Picture',
  'ShoppingCart',
  'PriceTag',
  'Location',
  'ShoppingBag',
  'Money',
  'Files',
  'Dashboard',
  'Route',
  'Play',
  'Check',
  'Lock',
  'Search',
  'BellFilled',
  'CirclePlusFilled',
  'RemoveFilled',
  'Switch',
  'Plus',
  'Edit',
]

// 判断是否是 SVG 图标（不在常见 Element Plus 图标列表中的就是 SVG）
const isSvgIcon = computed(() => {
  if (!props.item.icon) return false
  return !commonElementIcons.includes(props.item.icon)
})

const hasChildren = computed(() => {
  return props.item.children && props.item.children.length > 0
})

const resolvePath = computed(() => {
  if (props.basePath) {
    return path.resolve(props.basePath, props.item.path)
  }
  return props.item.path
})

const handleClick = () => {
  if (props.item.external) {
    window.open(props.item.external, '_blank')
    return
  }
  router.push(resolvePath.value)
}
</script>
