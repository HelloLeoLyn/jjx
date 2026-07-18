<template>
  <div class="role-container">
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
            v-hasPermi="['system:role:remove']"
          >
            批量删除
          </el-button>
        </template>
      </Toolbar>

      <!-- 表格区域 -->
      <DataTable
        :showIndex="false"
        v-model="queryParams"
        :data="roleList"
        :loading="loading"
        :total="total"
        :columns="uiConfig.tableOptions"
        :show-action="true"
        :action-width="400"
        @selection-change="handleSelectionChange"
        @page-change="handleCurrentChange"
        @size-change="handleSizeChange"
      >
        <!-- 状态列自定义渲染 -->
        <template #status="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>

        <!-- 创建时间列自定义渲染 -->
        <template #createTime="{ row }">
          <span>{{ parseTime(row.createTime) }}</span>
        </template>

        <!-- 操作列 -->
        <template #action="{ row }">
          <el-button
            link
            type="primary"
            @click="handleUpdate(row)"
            v-hasPermi="['system:role:edit']"
          >
            修改
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(row)"
            v-hasPermi="['system:role:remove']"
          >
            删除
          </el-button>
          <el-button
            link
            type="success"
            @click="handleAuthUser(row)"
            v-hasPermi="['system:role:edit']"
          >
            分配用户
          </el-button>
          <el-button
            link
            type="warning"
            @click="handleAuthMenuButton(row)"
            v-hasPermi="['system:role:edit']"
          >
            菜单权限
          </el-button>
        </template>
      </DataTable>
    </el-card>

    <!-- 添加或修改角色对话框 -->
    <RoleFormDialog
      v-model:visible="dialog.visible"
      :title="dialog.title"
      :form-data="dataForm"
      @success="handleDialogSuccess"
      @cancel="cancel"
    />

    <!-- 分配用户对话框 -->
    <RoleAssignUserDialog
      v-model:visible="authUserDialog.visible"
      :role-id="authUserDialog.roleId"
      @success="handleAssignUserSuccess"
    />

    <!-- 菜单按钮权限对话框 -->
    <el-dialog
      title="菜单按钮权限配置"
      v-model="menuButtonDialog.visible"
      width="1000px"
      append-to-body
      @close="closeMenuButtonDialog"
    >
      <MenuButtonPermission
        v-if="menuButtonDialog.visible"
        :role-id="menuButtonDialog.roleId"
        :menu-tree="menuButtonTree"
        :selected-menu-ids="menuButtonDialog.selectedMenuIds"
        @update:selected-menu-ids="handleMenuIdsUpdate"
        @change="handleMenuButtonChange"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="saveMenuButtonPermissions"> 保存 </el-button>
          <el-button @click="menuButtonDialog.visible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Role',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import { Toolbar, DataTable, SearchForm } from '@/components/common-ui/index'
import { roleApi } from '@/api/system/role'
import { menuApi } from '@/api/system/menu'
import MenuButtonPermission from '@/components/system/MenuButtonPermission.vue'
import RoleFormDialog from './components/RoleFormDialog.vue'
import RoleAssignUserDialog from './components/RoleAssignUserDialog.vue'
import type { SysRole, SysMenu } from '@/types/system'
import * as uiConfig from './index'
import { assignExisting } from '@/utils/object'
import { parseTime } from '@/utils/format'

// ==================== 配置 ====================

// 搜索表单引用
const queryFormRef = ref<FormInstance>()

// 加载状态
const loading = ref(false)

// 角色列表数据
const roleList = ref<SysRole[]>([])

// 总条数
const total = ref(0)

// 选中数组
const ids = ref<number[]>([])
const names = ref<string[]>([])

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roleName: undefined,
  roleKey: undefined,
  status: undefined,
})

// 对话框状态
const dialog = reactive({
  visible: false,
  title: '',
})

// 分配用户对话框状态
const authUserDialog = reactive({
  visible: false,
  roleId: undefined as number | undefined,
})

// 菜单按钮权限对话框状态
const menuButtonDialog = reactive({
  visible: false,
  roleId: undefined as number | undefined,
  selectedMenuIds: [] as number[],
})

// 菜单按钮权限树
const menuButtonTree = ref<SysMenu[]>([])

// 表单数据
const dataForm = reactive({
  roleId: undefined,
  roleName: '',
  roleKey: '',
  roleSort: 0,
  status: '0',
  menuIds: [] as number[],
  remark: '',
  menuCheckStrictly: true,
})

// 查询角色列表
const getList = async () => {
  loading.value = true
  try {
    const res = await roleApi.page(queryParams)
    if (res) {
      roleList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (error) {
    console.error('获取角色列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  if (queryFormRef.value) {
    queryFormRef.value.resetFields()
  }
  handleQuery()
}

// 多选框选中数据
const handleSelectionChange = (selection: SysRole[]) => {
  ids.value = selection.map((item) => item.roleId!)
  names.value = selection.map((item) => item.roleName)
}

// 新增按钮操作
const handleAdd = () => {
  resetForm()
  dialog.visible = true
  dialog.title = '添加角色'
}

// 修改按钮操作
const handleUpdate = async (row: SysRole) => {
  resetForm()
  const roleId = row.roleId || ids.value[0]
  if (!roleId) return

  try {
    const res = await roleApi.getInfo(roleId)
    if (res.data) {
      // 只复制存在的字段
      assignExisting(dataForm, res.data)
      dialog.visible = true
      dialog.title = '修改角色'
    }
  } catch (error) {
    console.error('获取角色信息失败:', error)
  }
}

// 分配用户操作
const handleAuthUser = (row: SysRole) => {
  authUserDialog.roleId = row.roleId
  authUserDialog.visible = true
}

// 分配用户成功回调
const handleAssignUserSuccess = () => {
  getList()
}

// 重置表单
const resetForm = () => {
  Object.assign(dataForm, {
    roleId: undefined,
    roleName: '',
    roleKey: '',
    roleSort: 0,
    status: '0',
    menuIds: [],
    remark: '',
    menuCheckStrictly: true,
  })
}

// 取消按钮
const cancel = () => {
  dialog.visible = false
  resetForm()
}

// 对话框成功回调
const handleDialogSuccess = () => {
  getList()
}

// 删除按钮操作
const handleDelete = async (row?: SysRole) => {
  const roleIds = row ? [row.roleId!] : ids.value
  const roleNames = row ? [row.roleName] : names.value

  if (roleIds.length === 0) {
    ElMessage.warning('请选择要删除的数据')
    return
  }

  try {
    await ElMessageBox.confirm(`是否确认删除角色名为"${roleNames.join(',')}"的数据项？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await roleApi.remove(roleIds)
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    // 用户取消删除
  }
}

// 工具栏按钮点击事件
const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
  if (key === 'export') handleExport()
}

// 导出按钮操作
const handleExport = () => {
  ElMessage.info('导出功能待实现')
}

// 分页大小改变
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 当前页改变
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

// 页面加载时
onMounted(() => {
  getList()
})

// 菜单按钮权限相关方法

// 获取包含按钮的菜单树
const getMenuButtonTree = async () => {
  try {
    // 这里需要调用一个API来获取包含按钮的完整菜单树
    // 暂时使用现有的菜单树，在实际项目中需要扩展API
    const res = await menuApi.treeselect()
    if (res.data) {
      menuButtonTree.value = res.data
    }
  } catch (error) {
    console.error('获取菜单按钮树失败:', error)
  }
}

// 处理菜单按钮权限配置
const handleAuthMenuButton = async (row: SysRole) => {
  menuButtonDialog.roleId = row.roleId
  menuButtonDialog.visible = true

  // 重置选择
  menuButtonDialog.selectedMenuIds = []

  // 加载菜单树
  await getMenuButtonTree()

  // 加载已有的权限配置
  await loadRoleMenuButtonPermissions(row.roleId!)
}

// 加载角色已有的菜单按钮权限
const loadRoleMenuButtonPermissions = async (roleId: number) => {
  try {
    const res = await roleApi.getAuthMenu(roleId)
    if (res.data) {
      menuButtonDialog.selectedMenuIds = res.data
    }
  } catch (error) {
    console.error('加载角色权限失败:', error)
  }
}

// 关闭菜单按钮对话框
const closeMenuButtonDialog = () => {
  menuButtonDialog.visible = false
  menuButtonDialog.roleId = undefined
  menuButtonDialog.selectedMenuIds = []
}

// 处理菜单ID更新
const handleMenuIdsUpdate = (menuIds: number[]) => {
  menuButtonDialog.selectedMenuIds = menuIds
}

// 处理菜单按钮变化
const handleMenuButtonChange = (menuIds: number[]) => {
  menuButtonDialog.selectedMenuIds = menuIds
}

// 保存菜单按钮权限
const saveMenuButtonPermissions = async () => {
  if (!menuButtonDialog.roleId) {
    ElMessage.warning('角色ID不能为空')
    return
  }

  try {
    // 这里需要调用API保存菜单和按钮权限
    // 暂时使用现有的菜单权限API
    await roleApi.addAuthMenu({
      roleId: menuButtonDialog.roleId,
      menuIds: menuButtonDialog.selectedMenuIds,
    })

    ElMessage.success('权限保存成功')
    menuButtonDialog.visible = false
  } catch (error) {
    console.error('保存权限失败:', error)
    ElMessage.error('保存权限失败')
  }
}
</script>

<style scoped lang="scss">
.role-container {
  padding: 20px;

  .search-container {
    margin-bottom: 20px;
  }

  .operation-container {
    margin-bottom: 20px;
  }

  .table-container {
    .pagination-container {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
  }
}

.dialog-footer {
  text-align: right;
}
</style>
