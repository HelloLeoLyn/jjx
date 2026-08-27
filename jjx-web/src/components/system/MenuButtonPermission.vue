<template>
  <div class="menu-button-permission">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchText"
        placeholder="搜索菜单或权限"
        clearable
        style="width: 300px"
        @clear="handleSearchClear"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="handleSearch" style="margin-left: 10px"> 搜索 </el-button>
      <el-button @click="handleResetSearch">重置</el-button>

      <div class="operation-buttons" style="margin-left: auto" v-hasPermi="['system:role:edit']">
        <el-button @click="expandAll">展开全部</el-button>
        <el-button @click="collapseAll">折叠全部</el-button>
        <el-button type="primary" @click="selectAll">全选</el-button>
        <el-button @click="clearAll">清空</el-button>
      </div>
    </div>

    <!-- 权限树 -->
    <div class="permission-tree">
      <el-tree
        ref="treeRef"
        :data="filteredMenuTree"
        show-checkbox
        node-key="menuId"
        :props="treeProps"
        :default-expand-all="defaultExpandAll"
        :filter-node-method="filterNode"
        :check-strictly="checkStrictly"
        @check="handleCheckChange"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <span class="node-label">{{ data.menuName }}</span>

            <!-- 菜单类型标签 -->
            <el-tag
              v-if="data.menuType"
              :type="getMenuTypeTagType(data.menuType)"
              size="small"
              style="margin-left: 8px"
            >
              {{ getMenuTypeLabel(data.menuType) }}
            </el-tag>

            <!-- 权限标识 -->
            <span v-if="data.perms" class="perm-label">
              {{ data.perms }}
            </span>

            <!-- 按钮权限统计 -->
            <span
              v-if="data.menuType === 'C' && data.children && data.children.length > 0"
              class="button-count"
            >
              ({{ countButtons(data) }}个按钮)
            </span>
          </div>
        </template>
      </el-tree>
    </div>

    <!-- 权限摘要 -->
    <div class="permission-summary">
      <el-card shadow="never">
        <template #header>
          <div class="summary-header">
            <span>权限摘要</span>
            <el-button link @click="toggleSummary">
              {{ showSummary ? '隐藏详情' : '显示详情' }}
            </el-button>
          </div>
        </template>

        <div v-if="showSummary">
          <div class="summary-item">
            <span class="summary-label">已选权限数：</span>
            <span class="summary-value">{{ selectedMenuIds.length }} 个</span>
          </div>

          <!-- 选中的权限列表 -->
          <div v-if="selectedPermsList.length > 0" class="selected-perms">
            <div class="perms-title">选中的权限标识：</div>
            <div class="perms-list">
              <el-tag
                v-for="item in selectedPermsList"
                :key="item.menuId"
                type="info"
                size="small"
                style="margin: 2px"
                closable
                @close="removePermission(item.perms)"
              >
                {{ item.perms }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 权限继承设置 -->
    <div class="inheritance-settings">
      <el-card shadow="never">
        <template #header>
          <span>权限继承设置</span>
        </template>

        <div class="settings-content">
          <el-checkbox v-model="autoSelectChildren" @change="updateInheritanceSettings">
            选中父菜单时自动选中所有子项
          </el-checkbox>
          <el-checkbox v-model="includeButtonsOnParentSelect" @change="updateInheritanceSettings">
            选中菜单时自动包含所有按钮权限
          </el-checkbox>
          <el-checkbox v-model="warnOnConflict" @change="updateInheritanceSettings">
            权限冲突时显示警告
          </el-checkbox>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage, ElTree } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type { SysMenu } from '@/types/system'

// 组件属性
interface Props {
  roleId?: number
  menuTree?: SysMenu[]
  selectedMenuIds?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  roleId: undefined,
  menuTree: () => [],
  selectedMenuIds: () => [],
})

// 事件定义
interface Emits {
  (e: 'update:selectedMenuIds', value: number[]): void
  (e: 'change', value: number[]): void
}

const emit = defineEmits<Emits>()

// 树形组件引用
const treeRef = ref<InstanceType<typeof ElTree>>()

// 搜索相关
const searchText = ref('')
const defaultExpandAll = ref(true)

// 权限继承设置
const autoSelectChildren = ref(true)
const includeButtonsOnParentSelect = ref(false)
const warnOnConflict = ref(true)
const checkStrictly = ref(true) // 设置为true以支持按钮单独选定

// 显示控制
const showSummary = ref(true)

// 树形配置
const treeProps = {
  children: 'children',
  label: 'menuName',
}

// 计算属性：过滤后的菜单树
const filteredMenuTree = computed(() => {
  if (!searchText.value) return props.menuTree

  const filter = (nodes: SysMenu[]): SysMenu[] => {
    return nodes
      .map((node) => {
        const newNode = { ...node }
        const children = node.children ? filter(node.children) : []

        // 如果节点本身匹配，或者有子节点匹配，则保留
        const nodeMatches =
          node.menuName?.includes(searchText.value) || node.perms?.includes(searchText.value)

        if (nodeMatches || children.length > 0) {
          newNode.children = children
          return newNode
        }
        return null
      })
      .filter(Boolean) as SysMenu[]
  }

  return filter(props.menuTree)
})

// 计算属性：选中的菜单ID（包含所有类型：目录、菜单、按钮）
const selectedMenuIds = computed({
  get: () => props.selectedMenuIds,
  set: (value) => emit('update:selectedMenuIds', value),
})

// 计算属性：选中的权限标识列表（用于展示）
const selectedPermsList = computed(() => {
  const perms: { menuId: number; perms: string }[] = []
  props.selectedMenuIds.forEach((id) => {
    const node = findNodeByKey(id)
    if (node && node.perms) {
      perms.push({ menuId: id, perms: node.perms })
    }
  })
  return perms
})

// 方法：获取菜单类型标签
const getMenuTypeTagType = (menuType: string) => {
  switch (menuType) {
    case 'M':
      return 'primary' // 目录
    case 'C':
      return 'success' // 菜单
    case 'F':
      return 'warning' // 按钮
    default:
      return 'info'
  }
}

const getMenuTypeLabel = (menuType: string) => {
  switch (menuType) {
    case 'M':
      return '目录'
    case 'C':
      return '菜单'
    case 'F':
      return '按钮'
    default:
      return '未知'
  }
}

// 方法：统计按钮数量
const countButtons = (node: SysMenu): number => {
  if (!node.children) return 0
  return node.children.filter((child) => child.menuType === 'F').length
}

// 方法：处理树节点选中变化
const handleCheckChange = (checkedNode: any, checkedInfo: any) => {
  const { checkedKeys } = checkedInfo
  const currentNode = checkedNode as SysMenu

  // 判断该节点是被选中还是取消
  const isChecked = checkedKeys.includes(currentNode.menuId)

  if (isChecked) {
    // 半严格级联：子→父，自动勾选所有祖先节点
    checkAncestors(currentNode)
  }

  // 如果启用了自动选中子项，父→子级联（由 autoSelectChildren 复选框控制）
  if (autoSelectChildren.value && currentNode.children && currentNode.children.length > 0) {
    const childIds = getAllChildIds(currentNode)
    childIds.forEach((childId) => {
      treeRef.value?.setChecked(childId, isChecked, false)
    })
  }

  // 重新获取更新后的选中状态
  const updatedCheckedKeys = (treeRef.value?.getCheckedKeys() || []) as number[]
  const updatedHalfCheckedKeys = (treeRef.value?.getHalfCheckedKeys() || []) as number[]

  // 更新选中的菜单ID（包含所有类型：目录、菜单、按钮）
  const menuIds = [...updatedCheckedKeys, ...updatedHalfCheckedKeys].map((key) => Number(key))

  selectedMenuIds.value = menuIds

  // 触发change事件
  emit('change', menuIds)
}

// 向上递归勾选所有祖先节点（半严格级联的核心）
const checkAncestors = (node: SysMenu) => {
  if (!node.parentId || !treeRef.value) return
  const parent = findNodeByKey(node.parentId)
  if (parent && parent.menuId) {
    treeRef.value.setChecked(parent.menuId, true, false)
    checkAncestors(parent)
  }
}

// 方法：获取所有子节点ID（递归）
const getAllChildIds = (node: SysMenu): number[] => {
  let ids: number[] = []
  if (node.children) {
    node.children.forEach((child) => {
      if (child.menuId) {
        ids.push(child.menuId)
      }
      if (child.children) {
        ids = ids.concat(getAllChildIds(child))
      }
    })
  }
  return ids
}

// 方法：根据key查找节点
const findNodeByKey = (key: number): SysMenu | null => {
  const findInTree = (nodes: SysMenu[]): SysMenu | null => {
    for (const node of nodes) {
      if (node.menuId === key) return node
      if (node.children) {
        const found = findInTree(node.children)
        if (found) return found
      }
    }
    return null
  }

  return findInTree(props.menuTree)
}

// 方法：搜索相关
const handleSearch = () => {
  if (!treeRef.value) return
  treeRef.value.filter(searchText.value)
}

const handleSearchClear = () => {
  searchText.value = ''
  handleSearch()
}

const handleResetSearch = () => {
  searchText.value = ''
  defaultExpandAll.value = true
  if (treeRef.value) {
    treeRef.value.filter('')
  }
}

// 方法：过滤树节点
const filterNode = (value: string, data: any) => {
  if (!value) return true
  const menuData = data as SysMenu
  return menuData.menuName?.includes(value) || menuData.perms?.includes(value) || false
}

// 方法：树操作
const expandAll = () => {
  if (!treeRef.value) return
  const nodes = (treeRef.value as any).store._getAllNodes()
  nodes.forEach((node: any) => {
    node.expanded = true
  })
}

const collapseAll = () => {
  if (!treeRef.value) return
  const nodes = (treeRef.value as any).store._getAllNodes()
  nodes.forEach((node: any) => {
    node.expanded = false
  })
}

const selectAll = () => {
  if (!treeRef.value) return

  // 获取所有节点key（包括按钮节点）
  const getAllMenuKeys = (nodes: SysMenu[]): number[] => {
    let keys: number[] = []
    nodes.forEach((node) => {
      if (node.menuId) {
        keys.push(node.menuId)
      }
      if (node.children) {
        keys = keys.concat(getAllMenuKeys(node.children))
      }
    })
    return keys
  }

  const allKeys = getAllMenuKeys(props.menuTree)
  treeRef.value.setCheckedKeys(allKeys)
}

const clearAll = () => {
  if (!treeRef.value) return
  treeRef.value.setCheckedKeys([])
}

// 方法：权限继承设置更新
const updateInheritanceSettings = () => {
  // 更新check-strictly模式
  checkStrictly.value = !autoSelectChildren.value

  ElMessage.success('权限继承设置已更新')
}

// 方法：移除单个权限
const removePermission = (perm: string) => {
  // 从树中取消选中对应的节点
  if (treeRef.value) {
    const node = findNodeByPerm(perm)
    if (node && node.menuId) {
      treeRef.value.setChecked(node.menuId, false, false)
    }
  }
}

// 方法：根据权限标识查找节点
const findNodeByPerm = (perm: string): SysMenu | null => {
  const findInTree = (nodes: SysMenu[]): SysMenu | null => {
    for (const node of nodes) {
      if (node.perms === perm) return node
      if (node.children) {
        const found = findInTree(node.children)
        if (found) return found
      }
    }
    return null
  }

  return findInTree(props.menuTree)
}

// 方法：切换摘要显示
const toggleSummary = () => {
  showSummary.value = !showSummary.value
}

// 监听选中的菜单ID变化，更新树状态
watch(
  () => props.selectedMenuIds,
  (newIds) => {
    nextTick(() => {
      if (treeRef.value) {
        // 设置选中的菜单节点（包含所有类型）
        treeRef.value.setCheckedKeys(newIds)
      }
    })
  },
  { immediate: true }
)

// 监听菜单树变化，重新初始化
watch(
  () => props.menuTree,
  () => {
    nextTick(() => {
      if (treeRef.value && props.selectedMenuIds.length > 0) {
        treeRef.value.setCheckedKeys(props.selectedMenuIds)
      }
    })
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.menu-button-permission {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;

  .search-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px;
    background-color: #f5f7fa;
    border-radius: 4px;

    .operation-buttons {
      display: flex;
      gap: 8px;
    }
  }

  .permission-tree {
    flex: 1;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    padding: 16px;
    overflow: auto;
    max-height: 500px;

    .tree-node {
      display: flex;
      align-items: center;
      padding: 4px 0;

      .node-label {
        font-weight: 500;
      }

      .perm-label {
        margin-left: 12px;
        font-size: 12px;
        color: #909399;
        background-color: #f0f2f5;
        padding: 2px 6px;
        border-radius: 3px;
      }

      .button-count {
        margin-left: 8px;
        font-size: 12px;
        color: #67c23a;
      }
    }
  }

  .permission-summary {
    .summary-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .summary-item {
      margin-bottom: 8px;

      .summary-label {
        font-weight: 500;
        color: #606266;
      }

      .summary-value {
        color: #409eff;
        font-weight: 600;
      }
    }

    .selected-perms {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #e4e7ed;

      .perms-title {
        font-weight: 500;
        margin-bottom: 8px;
        color: #606266;
      }

      .perms-list {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
        max-height: 120px;
        overflow-y: auto;
        padding: 8px;
        background-color: #f8f9fa;
        border-radius: 4px;
      }
    }
  }

  .inheritance-settings {
    .settings-content {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
  }
}
</style>
