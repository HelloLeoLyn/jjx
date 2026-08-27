import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { SysRole } from '@/types/system'
import { roleApi } from '@/api/system/role'
import { userApi } from '@/api/system/user'

export function useRole() {
  const loading = ref(false)
  const roleList = ref<SysRole[]>([])
  const total = ref(0)
  const assignedRoleIds = ref<number[]>([])

  // 分页参数
  const pagination = reactive({
    pageNum: 1,
    pageSize: 10,
  })

  // 查询角色列表（分页）
  const fetchRoleList = async (params?: any) => {
    loading.value = true
    try {
      const res = await roleApi.list({
        ...params,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      })
      roleList.value = res.data || []
      // total.value = res.total
    } catch (error) {
      console.error('获取角色列表失败:', error)
      ElMessage.error('获取角色列表失败')
    } finally {
      loading.value = false
    }
  }

  // 获取用户已分配的角色
  const fetchAssignedRoles = async (userId: number) => {
    try {
      const res = await userApi.selectUserRoles({
        userId: userId,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      })
      if (res.data) {
        assignedRoleIds.value = res.data?.records.map((role: { roleId: any }) => role.roleId!) // 提取已分配角色的ID
      }
    } catch (error) {
      console.error('获取已分配角色失败:', error)
      ElMessage.error('获取已分配角色失败')
    }
  }

  // 保存分配的角色
  const saveAssignedRoles = async (userId: number, roleIds: number[]) => {
    loading.value = true
    try {
      await userApi.authRole(userId, roleIds)
      ElMessage.success('分配角色成功')
      return true
    } catch (error) {
      console.error('分配角色失败:', error)
      ElMessage.error('分配角色失败')
      return false
    } finally {
      loading.value = false
    }
  }

  // 改变页码
  const changePage = (page: number) => {
    pagination.pageNum = page
  }

  // 改变每页条数
  const changePageSize = (size: number) => {
    pagination.pageSize = size
    pagination.pageNum = 1
  }

  return {
    loading,
    roleList,
    total,
    pagination,
    assignedRoleIds,
    fetchRoleList,
    fetchAssignedRoles,
    saveAssignedRoles,
    changePage,
    changePageSize,
  }
}
