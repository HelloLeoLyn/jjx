<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`分配角色 - ${userName}`"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="dataLoading" class="assign-role-content">
      <template v-if="!dataLoading">
        <el-alert title="提示" type="info" :closable="false" show-icon style="margin-bottom: 20px">
          <template #default>
            正在为用户 <strong>{{ userName }}</strong> 分配角色
          </template>
        </el-alert>

        <!-- 搜索栏 -->
        <div class="search-bar">
          <el-input
            v-model="searchParams.roleName"
            placeholder="角色名称"
            clearable
            style="width: 200px"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select
            v-model="searchParams.roleStatus"
            placeholder="角色状态"
            clearable
            style="width: 120px; margin-left: 10px"
            @change="handleSearch"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>

          <el-button type="primary" @click="handleSearch" style="margin-left: 10px">
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>

        <!-- 已选择角色展示 -->
        <div class="selected-roles" :class="{ 'is-empty': allSelectedIds.length === 0 }">
          <span class="selected-roles-label">已选择角色</span>
          <div class="selected-roles-tags">
            <template v-if="allSelectedIds.length > 0">
              <el-tag
                v-for="rid in allSelectedIds"
                :key="rid"
                size="small"
                class="role-tag"
                :type="getRoleStatus(rid) === '1' ? 'info' : 'primary'"
                effect="light"
                closable
                @close="removeSelectedRole(rid)"
              >
                {{ getRoleName(rid) }}
              </el-tag>
            </template>
            <span v-else class="empty-tip">暂未选择角色</span>
          </div>
        </div>

        <!-- 角色表格 -->
        <div class="role-table-wrapper">
          <div class="table-header">
            <div class="header-left">
              <span class="total-info">共 {{ total }} 个角色</span>
            </div>
            <div class="header-right">
              <span class="selected-info">
                已选择
                <strong class="selected-count">{{ allSelectedIds.length }}</strong>
                个角色
              </span>
            </div>
          </div>

          <el-table
            ref="tableRef"
            v-loading="tableLoading"
            :data="tableData"
            border
            row-key="roleId"
            @select="handleSelect"
            @select-all="handleSelectAll"
          >
            <el-table-column type="selection" width="55" :selectable="checkSelectable" />

            <el-table-column prop="roleId" label="角色ID" width="80" />

            <el-table-column prop="roleName" label="角色名称" min-width="120">
              <template #default="{ row }">
                <span :class="{ 'disabled-text': row.roleStatus === '1' }">
                  {{ row.roleName }}
                </span>
              </template>
            </el-table-column>

            <el-table-column prop="roleStatus" label="状态" width="80" align="center">
              <template #default="{ row }"> </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleCurrentChange"
              @size-change="handleSizeChange"
            />
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <div class="selected-summary">
          <el-icon><Check /></el-icon>
          <span
            >已选择 <strong>{{ allSelectedIds.length }}</strong> 个角色</span
          >
        </div>
        <div>
          <el-button @click="handleClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave"> 确定分配 </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Check } from '@element-plus/icons-vue'
import { userApi } from '@/api/system/user'
import { roleApi } from '@/api/system/role'
import { type SysUserRoleVO, type SysRole, type SysUserRoleQuery } from '@/types/system'

// Props
interface Props {
  visible: boolean
  userId: number
  userName: string
}

const props = defineProps<Props>()

// Emits
interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

const emit = defineEmits<Emits>()

// 弹窗可见性
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// 加载状态
const dataLoading = ref(false)
const tableLoading = ref(false)

// 分页参数
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 搜索参数
const searchParams = ref({
  roleName: '',
  roleStatus: '',
})

const tableData = ref<SysUserRoleVO[]>([])
const tableRef = ref()

// 所有已选中的角色ID（跨页保存）
const allSelectedIds = ref<number[]>([])

// 全量角色映射（roleId -> 角色），用于展示已选角色名称
const roleMap = ref<Map<number, SysRole>>(new Map())

// 获取角色名称
const getRoleName = (roleId: number) => {
  return roleMap.value.get(roleId)?.roleName || `角色#${roleId}`
}

// 获取角色状态（用于停用角色置灰）
const getRoleStatus = (roleId: number) => {
  return roleMap.value.get(roleId)?.status ?? '0'
}

// 移除已选角色（点击标签关闭按钮）
const removeSelectedRole = (roleId: number) => {
  allSelectedIds.value = allSelectedIds.value.filter((id) => id !== roleId)
  // 同步取消当前页表格中的勾选
  const row = tableData.value.find((item) => item.roleId === roleId)
  if (tableRef.value && row) {
    tableRef.value.toggleRowSelection(row, false)
  }
}

// 保存状态
const saving = ref(false)

// 防止重复请求的标志
let isLoading = false

// 判断角色是否可选（停用的角色不能选择）
const checkSelectable = (row: SysUserRoleVO) => {
  return row.roleStatus === '0'
}

// 单行勾选/取消（仅用户手动操作时触发，数据刷新不会触发）
const handleSelect = (selection: SysRole[], row: SysRole) => {
  if (row.roleId === undefined) return
  const isChecked = selection.some((item) => item.roleId === row.roleId)
  if (isChecked) {
    if (!allSelectedIds.value.includes(row.roleId)) {
      allSelectedIds.value.push(row.roleId)
    }
  } else {
    allSelectedIds.value = allSelectedIds.value.filter((id) => id !== row.roleId)
  }
}

// 全选/取消全选（仅当前页）
const handleSelectAll = (selection: SysRole[]) => {
  const currentPageIds = tableData.value
    .map((item) => item.roleId)
    .filter((id): id is number => id !== undefined)
  const selectedIds = selection
    .map((item) => item.roleId)
    .filter((id): id is number => id !== undefined)

  // 从全局选中中移除当前页的所有ID，再加回当前页选中的ID
  allSelectedIds.value = allSelectedIds.value.filter((id) => !currentPageIds.includes(id))
  allSelectedIds.value.push(...selectedIds)
}

// 恢复当前页的选中状态
const restoreCurrentPageSelection = () => {
  if (!tableRef.value) return

  tableData.value.forEach((row) => {
    if (row.roleId && allSelectedIds.value.includes(row.roleId)) {
      tableRef.value.toggleRowSelection(row, true)
    }
  })
}

// 加载角色列表
const loadRoleList = async () => {
  if (isLoading) return
  if (!props.userId || props.userId <= 0) return

  isLoading = true
  tableLoading.value = true

  try {
    const params: SysUserRoleQuery = {
      roleName: searchParams.value.roleName || undefined,
      roleStatus: searchParams.value.roleStatus || undefined,
      userId: props.userId,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }

    const res = await userApi.selectUserRoles(params)

    if (res.code === 200 && res.data) {
      const records = res.data.records || []
      tableData.value = records
      total.value = res.data.total || 0

      // 延迟执行选中操作，确保表格已渲染
      setTimeout(() => {
        restoreCurrentPageSelection()
      }, 100)
    } else {
      ElMessage.error(res.msg || '获取角色列表失败')
    }
  } catch (error) {
    console.error('加载角色列表失败:', error)
    ElMessage.error('加载角色列表失败')
  } finally {
    tableLoading.value = false
    isLoading = false
  }
}

// 加载全量角色映射（用于展示已选角色名称）
const loadRoleOptions = async () => {
  try {
    const res = await roleApi.optionselect()
    const roles = res.data || []
    roleMap.value = new Map(roles.map((role) => [role.roleId!, role]))
  } catch (error) {
    console.error('获取角色选项失败:', error)
  }
}

// 获取用户已分配的全部角色ID（全量，作为跨页勾选基准）
const loadUserRoleIds = async () => {
  if (!props.userId || props.userId <= 0) return
  try {
    const res = await userApi.getInfo(props.userId)
    if (res.code === 200 && res.data) {
      allSelectedIds.value = (res.data.roleIds || []).filter((id): id is number => id !== undefined)
    } else {
      ElMessage.error(res.msg || '获取用户角色失败')
    }
  } catch (error) {
    console.error('获取用户角色失败:', error)
    ElMessage.error('获取用户角色失败')
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadRoleList()
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    roleName: '',
    roleStatus: '',
  }
  currentPage.value = 1
  loadRoleList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadRoleList()
}

// 每页大小改变
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadRoleList()
}

// 重置所有状态
const resetState = () => {
  searchParams.value = {
    roleName: '',
    roleStatus: '',
  }
  currentPage.value = 1
  pageSize.value = 10
  total.value = 0
  allSelectedIds.value = []
  tableData.value = []
}

// 保存分配
const handleSave = async () => {
  if (!props.userId) {
    ElMessage.error('用户ID不存在')
    return
  }

  const selectedIds = allSelectedIds.value.filter((id) => id !== undefined)

  if (selectedIds.length === 0) {
    await ElMessageBox.confirm(`确定要清空用户「${props.userName}」的所有角色吗？`, '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } else {
    await ElMessageBox.confirm(
      `确定为用户「${props.userName}」分配 ${selectedIds.length} 个角色吗？`,
      '分配角色确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  }

  saving.value = true
  try {
    // 调用保存接口 - 根据实际API调整
    const res = await userApi.authRole(props.userId, selectedIds)
    if (res.code === 200) {
      ElMessage.success('角色分配成功')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 关闭弹窗
const handleClose = () => {
  resetState()
  emit('update:visible', false)
}

// 初始化加载数据
const initData = async () => {
  if (dataLoading.value || isLoading) return
  if (!props.userId || props.userId <= 0) return

  dataLoading.value = true

  try {
    resetState()
    // 先加载全量角色映射与用户已选角色，再加载第一页（翻页/搜索时只刷表格，不再重置选择）
    await loadRoleOptions()
    await loadUserRoleIds()
    await loadRoleList()
  } catch (error) {
    console.error('初始化数据失败:', error)
  } finally {
    dataLoading.value = false
  }
}

// 监听弹窗打开
watch(
  [() => props.visible, () => props.userId],
  async ([newVisible, newUserId], [oldVisible, oldUserId]) => {
    const isUserIdValid = newUserId && newUserId > 0
    const isUserIdChanged = newUserId !== oldUserId
    const isDialogJustOpened = newVisible && !oldVisible

    if (newVisible && isUserIdValid && (isUserIdChanged || isDialogJustOpened)) {
      setTimeout(() => {
        initData()
      }, 50)
    }
  }
)
</script>

<style scoped lang="scss">
.assign-role-content {
  min-height: 400px;

  .selected-roles {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 10px 16px;
    margin-bottom: 16px;
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
    background-color: #fafafa;

    .selected-roles-label {
      flex-shrink: 0;
      font-size: 13px;
      color: #606266;
      line-height: 24px;
      font-weight: 500;
    }

    .selected-roles-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;

      .role-tag {
        cursor: pointer;
      }
    }

    .empty-tip {
      font-size: 13px;
      color: #c0c4cc;
      line-height: 24px;
    }

    &.is-empty {
      border-style: dashed;
    }
  }

  .search-bar {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
  }

  .role-table-wrapper {
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    overflow: hidden;

    .table-header {
      padding: 12px 16px;
      background-color: #f5f7fa;
      border-bottom: 1px solid #e4e7ed;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;

        .total-info {
          font-size: 12px;
          color: #909399;
        }
      }

      .header-right {
        .selected-info {
          font-size: 14px;
          color: #606266;

          .selected-count {
            color: #409eff;
            font-size: 16px;
            margin: 0 4px;
          }
        }
      }
    }

    .disabled-text {
      color: #c0c4cc;
    }

    .pagination-wrapper {
      padding: 16px;
      display: flex;
      justify-content: flex-end;
      border-top: 1px solid #e4e7ed;
      background-color: #fff;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .selected-summary {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: #606266;

    .el-icon {
      color: #67c23a;
      font-size: 16px;
    }

    strong {
      color: #409eff;
      font-size: 16px;
      margin: 0 4px;
    }
  }
}
</style>
