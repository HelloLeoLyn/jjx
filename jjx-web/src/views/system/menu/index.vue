<template>
  <div class="menu-container">
    <el-card class="box-card">
      <!-- 搜索区域 -->
      <SearchForm
        v-model="queryParams"
        :fields="uiConfig.searchOptions"
        @search="handleQuery"
        @reset="resetQuery"
      />

      <!-- 操作按钮区域 -->
      <Toolbar
        :buttons="uiConfig.toolbarOptions"
        :selected-count="ids.length"
        :show-batch-bar="true"
        @click="handleToolbarClick"
        @refresh="getList"
      >
        <template #batch-actions>
          <el-button
            type="danger"
            size="small"
            @click="() => handleDelete()"
            v-hasPermi="['system:menu:remove']"
          >
            批量删除
          </el-button>
        </template>
      </Toolbar>

      <!-- 表格区域 - 改为 el-table 树形显示 -->
      <div class="table-wrapper">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="menuList"
          row-key="menuId"
          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
          @selection-change="handleSelectionChange"
          @row-click="toggleExpand"
          border
          stripe
          style="width: 100%"
        >
          <!-- 菜单名称列 -->
          <el-table-column prop="menuName" label="菜单名称" min-width="120">
            <template #default="{ row }">
              <span>{{ row.menuName }}</span>
            </template>
          </el-table-column>

          <!-- 图标列 -->
          <el-table-column prop="icon" label="图标" width="80" align="center">
            <template #default="{ row }">
              <universal-icon :icon="row.icon" :size="18" />
            </template>
          </el-table-column>

          <!-- 排序列 -->
          <el-table-column prop="orderNum" label="排序" width="80" align="center" />
          <el-table-column prop="path" label="路由地址" min-width="180" show-overflow-tooltip />
          <el-table-column prop="redirect" label="重定向" min-width="140" align="center" show-overflow-tooltip />
          <!-- 权限标识列 -->
          <el-table-column prop="perms" label="权限标识" min-width="150">
            <template #default="{ row }">
              <span>{{ row.perms || '-' }}</span>
            </template>
          </el-table-column>

          <!-- 组件路径列 -->
          <el-table-column prop="component" label="组件路径" min-width="180">
            <template #default="{ row }">
              <span>{{ row.component || '-' }}</span>
            </template>
          </el-table-column>

          <!-- 状态列 -->
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
                {{ row.status === '0' ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 更新时间列 -->
          <el-table-column prop="updateTime" label="更新时间" width="160" align="center">
            <template #default="{ row }">
              <span>{{ parseTime(row.updateTime) }}</span>
            </template>
          </el-table-column>

          <!-- 操作列 -->
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                @click="handleAdd(row)"
                v-hasPermi="['system:menu:add']"
              >
                新增
              </el-button>
              <el-button
                link
                type="warning"
                size="small"
                @click="handleUpdate(row)"
                v-hasPermi="['system:menu:edit']"
              >
                修改
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
                v-hasPermi="['system:menu:remove']"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 添加或修改菜单对话框 -->
    <MenuEditFormDialog
      v-model:visible="dialog.visible"
      :title="dialog.title"
      :form-data="form"
      :menu-options="menuOptions"
      @success="handleDialogSuccess"
      @cancel="cancel"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Menu',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UniversalIcon from '@/components/UniversalIcon.vue'
import { Toolbar, SearchForm } from '@/components/common-ui/index'
import { menuApi } from '@/api/system/menu'
import type { SysMenu, SysMenuQuery } from '@/types/system'
import * as uiConfig from './index'
import { parseTime } from '@/utils/format'
import MenuEditFormDialog from '@/components/system/MenuEditFormDialog.vue'

// ==================== 状态 ====================
const loading = ref(false)
const menuList = ref<SysMenu[]>([])
const ids = ref<number[]>([])
const names = ref<string[]>([])

const queryParams = reactive<SysMenuQuery>({
  menuName: undefined,
  status: undefined,
  perms: undefined,
})

const dialog = reactive({
  visible: false,
  title: '',
})

const form = reactive({
  menuId: undefined,
  parentId: 0,
  menuName: '',
  menuType: 'C',
  orderNum: 0,
  path: '',
  component: '',
  perms: '',
  icon: '',
  status: '0',
  visible: '0',
  isCache: '0',
  remark: '',
})
const tableRef = ref()
const menuOptions = ref<SysMenu[]>([])
// 切换展开/收起
const toggleExpand = (row: SysMenu) => {
  tableRef.value.toggleRowExpansion(row)
}
// 查询菜单列表 - 改为树形数据
const getList = async () => {
  loading.value = true
  try {
    const params: SysMenu = {
      menuName: queryParams.menuName || '',
      menuType: '', // 添加必需的 menuType 属性
      status: queryParams.status || '',
      perms: queryParams.perms || '',
    }
    const res = await menuApi.treeselect(params)
    if (res.data) {
      menuList.value = res.data
    }
  } catch (error) {
    console.error('获取菜单列表失败:', error)
    ElMessage.error('获取菜单列表失败')
  } finally {
    loading.value = false
  }
}

// 获取菜单树
const getMenuTree = async () => {
  try {
    const res = await menuApi.treeselect()
    if (res.data) {
      menuOptions.value = res.data
    }
  } catch (error) {
    console.error('获取菜单树失败:', error)
  }
}

// 搜索按钮操作
const handleQuery = () => {
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  queryParams.menuName = undefined
  queryParams.status = undefined
  queryParams.perms = undefined
  getList()
}

// 工具栏按钮点击处理
const handleToolbarClick = (key: string) => {
  switch (key) {
    case 'add':
      handleAdd()
      break
    case 'edit':
      if (ids.value.length === 0) {
        ElMessage.warning('请选择要修改的菜单')
      } else if (ids.value.length > 1) {
        ElMessage.warning('只能选择一个菜单进行修改')
      } else {
        const menu = menuList.value.find((item) => item.menuId === ids.value[0])
        if (menu) handleUpdate(menu)
      }
      break
    case 'remove':
      if (ids.value.length === 0) {
        ElMessage.warning('请选择要删除的菜单')
      } else {
        handleDelete()
      }
      break
    case 'refresh':
      getList()
      break
    default:
      break
  }
}

// 多选框选中数据
const handleSelectionChange = (selection: SysMenu[]) => {
  ids.value = selection.map((item) => item.menuId!)
  names.value = selection.map((item) => item.menuName)
}

// 重置表单
const resetForm = () => {
  form.menuId = undefined
  form.parentId = 0
  form.menuName = ''
  form.menuType = 'C'
  form.orderNum = 0
  form.path = ''
  form.component = ''
  form.perms = ''
  form.icon = ''
  form.status = '0'
  form.visible = '0'
  form.isCache = '0'
  form.remark = ''
}

// 新增按钮操作
const handleAdd = (row?: SysMenu) => {
  resetForm()
  if (row && 'menuId' in row && row.menuId) {
    form.parentId = row.menuId
    form.perms = row.perms ? row.perms : ''
    form.menuName = row.menuName
  }
  dialog.visible = true
  dialog.title = '添加菜单'
}

// 修改按钮操作
const handleUpdate = async (row: SysMenu) => {
  resetForm()
  const menuId = row.menuId
  if (!menuId) return

  try {
    const res = await menuApi.getInfo(menuId)
    if (res.data) {
      const menu = res.data
      Object.assign(form, {
        menuId: menu.menuId,
        parentId: menu.parentId,
        menuName: menu.menuName,
        menuType: menu.menuType,
        orderNum: menu.orderNum,
        path: menu.path,
        component: menu.component,
        perms: menu.perms,
        icon: menu.icon,
        status: menu.status,
        visible: menu.visible,
        isCache: menu.isCache,
        remark: menu.remark,
      })
      dialog.visible = true
      dialog.title = '修改菜单'
    }
  } catch (error) {
    console.error('获取菜单详情失败:', error)
    ElMessage.error('获取菜单详情失败')
  }
}

// 删除按钮操作
const handleDelete = async (row?: SysMenu) => {
  let deleteIds: number[] = []
  let deleteNames: string[] = []

  if (row) {
    deleteIds = [row.menuId!]
    deleteNames = [row.menuName]
  } else {
    deleteIds = ids.value
    deleteNames = names.value
  }

  if (deleteIds.length === 0) {
    ElMessage.warning('请选择要删除的菜单')
    return
  }

  try {
    await ElMessageBox.confirm(
      `是否确认删除菜单"${deleteNames.join(', ')}"? 删除后可能导致其子菜单也被删除，请谨慎操作！`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await menuApi.remove(deleteIds)
    ElMessage.success('删除成功')
    getList()
    getMenuTree()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 对话框成功回调
const handleDialogSuccess = () => {
  // 刷新菜单列表和菜单树
  getList()
  getMenuTree()
}

// 取消按钮
const cancel = () => {
  dialog.visible = false
  resetForm()
}

// 初始化
onMounted(() => {
  getList()
  getMenuTree()
})
</script>

<style scoped lang="scss">
.menu-container {
  padding: 20px;

  .box-card {
    width: 100%;
  }

  .assign-role-container {
    .menu-info {
      margin-bottom: 20px;
    }

    .role-assign-panel {
      display: flex;
      gap: 20px;
      min-height: 350px;

      .assigned-roles,
      .available-roles {
        flex: 1;

        .panel-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
          font-weight: bold;
        }
      }

      .transfer-buttons {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 10px;
        width: 60px;
      }
    }
  }
}
</style>
