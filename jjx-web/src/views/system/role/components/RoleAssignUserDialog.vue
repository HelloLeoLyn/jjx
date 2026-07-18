<template>
  <el-dialog
    title="分配用户"
    v-model="dialogVisible"
    width="1200px"
    append-to-body
    @close="handleClose"
  >
    <div class="auth-user-container">
      <div class="left-panel">
        <div class="panel-header">
          <span>未分配用户</span>
          <el-input
            v-model="unallocatedQuery.userName"
            placeholder="请输入用户名称"
            clearable
            style="width: 200px; margin-left: 10px"
            @keyup.enter="getUnallocatedUserList"
          >
            <template #append>
              <el-button icon="Search" @click="getUnallocatedUserList" />
            </template>
          </el-input>
        </div>
        <el-table
          v-loading="unallocatedLoading"
          :data="unallocatedUserList"
          @selection-change="handleUnallocatedSelectionChange"
          border
          stripe
          height="400"
        >
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="用户名称" align="center" prop="userName" />
          <el-table-column label="用户昵称" align="center" prop="nickName" />
          <el-table-column label="手机号码" align="center" prop="phoneNumber" />
        </el-table>
        <div class="panel-pagination">
          <el-pagination
            v-model:current-page="unallocatedQuery.pageNum"
            v-model:page-size="unallocatedQuery.pageSize"
            :total="unallocatedTotal"
            layout="total, prev, pager, next"
            @size-change="handleUnallocatedSizeChange"
            @current-change="handleUnallocatedCurrentChange"
          />
        </div>
      </div>
      <div class="center-panel">
        <div class="transfer-buttons">
          <el-button type="primary" icon="ArrowRight" @click="addAuthUser" />
          <el-button type="danger" icon="ArrowLeft" @click="removeAuthUser" />
        </div>
      </div>
      <div class="right-panel">
        <div class="panel-header">
          <span>已分配用户</span>
          <el-input
            v-model="allocatedQuery.userName"
            placeholder="请输入用户名称"
            clearable
            style="width: 200px; margin-left: 10px"
            @keyup.enter="getAllocatedUserList"
          >
            <template #append>
              <el-button icon="Search" @click="getAllocatedUserList" />
            </template>
          </el-input>
        </div>
        <el-table
          v-loading="allocatedLoading"
          :data="allocatedUserList"
          @selection-change="handleAllocatedSelectionChange"
          border
          stripe
          height="400"
        >
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="用户名称" align="center" prop="userName" />
          <el-table-column label="用户昵称" align="center" prop="nickName" />
          <el-table-column label="手机号码" align="center" prop="phoneNumber" />
        </el-table>
        <div class="panel-pagination">
          <el-pagination
            v-model:current-page="allocatedQuery.pageNum"
            v-model:page-size="allocatedQuery.pageSize"
            :total="allocatedTotal"
            layout="total, prev, pager, next"
            @size-change="handleAllocatedSizeChange"
            @current-change="handleAllocatedCurrentChange"
          />
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitAuthUser">确 定</el-button>
        <el-button @click="dialogVisible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi } from '@/api/system/role'
import type { SysUserVO, RoleUserQueryDTO } from '@/types/system'

interface Props {
  visible: boolean
  roleId: number | undefined
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// ==================== 状态 ====================
const unallocatedLoading = ref(false)
const allocatedLoading = ref(false)
const unallocatedUserList = ref<SysUserVO[]>([])
const allocatedUserList = ref<SysUserVO[]>([])
const unallocatedTotal = ref(0)
const allocatedTotal = ref(0)
const unallocatedSelection = ref<SysUserVO[]>([])
const allocatedSelection = ref<SysUserVO[]>([])

const unallocatedQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined as string | undefined,
})

const allocatedQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined as string | undefined,
})

// ==================== API ====================
const getUnallocatedUserList = async () => {
  if (!props.roleId) return

  unallocatedLoading.value = true
  try {
    const params: RoleUserQueryDTO = {
      roleId: props.roleId,
      userName: unallocatedQuery.userName || undefined,
      pageNum: unallocatedQuery.pageNum,
      pageSize: unallocatedQuery.pageSize,
    }
    const res = await roleApi.unallocatedList(params)
    if (res) {
      unallocatedUserList.value = res.data?.records || []
      unallocatedTotal.value = res.data?.total || 0
    }
  } catch (error) {
    console.error('获取未分配用户列表失败:', error)
  } finally {
    unallocatedLoading.value = false
  }
}

const getAllocatedUserList = async () => {
  if (!props.roleId) return

  allocatedLoading.value = true
  try {
    const params: RoleUserQueryDTO = {
      roleId: props.roleId,
      userName: allocatedQuery.userName || undefined,
      pageNum: allocatedQuery.pageNum,
      pageSize: allocatedQuery.pageSize,
    }
    const res = await roleApi.allocatedList(params)
    if (res) {
      allocatedUserList.value = res.data?.records || []
      allocatedTotal.value = res.data?.total || 0
    }
  } catch (error) {
    console.error('获取已分配用户列表失败:', error)
  } finally {
    allocatedLoading.value = false
  }
}

// ==================== 事件 ====================
const handleUnallocatedSelectionChange = (selection: SysUserVO[]) => {
  unallocatedSelection.value = selection
}

const handleAllocatedSelectionChange = (selection: SysUserVO[]) => {
  allocatedSelection.value = selection
}

const addAuthUser = async () => {
  if (!props.roleId || unallocatedSelection.value.length === 0) {
    ElMessage.warning('请选择要分配的用户')
    return
  }

  const userIds = unallocatedSelection.value.map((user) => user.userId!)
  const userNames = unallocatedSelection.value.map(
    (user) => user.userName || user.nickName || '未知用户'
  )

  try {
    await ElMessageBox.confirm(
      `是否确认将用户"${userNames.join(',')}"分配给当前角色？`,
      '确认分配',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await roleApi.addAuthUser({
      roleId: props.roleId,
      userIds,
    })
    ElMessage.success('分配成功')
    getUnallocatedUserList()
    getAllocatedUserList()
    unallocatedSelection.value = []
  } catch (error) {
    // 用户取消分配
  }
}

const removeAuthUser = async () => {
  if (!props.roleId || allocatedSelection.value.length === 0) {
    ElMessage.warning('请选择要取消分配的用户')
    return
  }

  const userIds = allocatedSelection.value.map((user) => user.userId!)
  const userNames = allocatedSelection.value.map(
    (user) => user.userName || user.nickName || '未知用户'
  )

  try {
    await ElMessageBox.confirm(
      `是否确认将用户"${userNames.join(',')}"从当前角色移除？`,
      '确认移除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await roleApi.cancelAuthUser({
      roleId: props.roleId,
      userIds,
    })
    ElMessage.success('取消分配成功')
    getUnallocatedUserList()
    getAllocatedUserList()
    allocatedSelection.value = []
  } catch (error) {
    // 用户取消移除
  }
}

const submitAuthUser = () => {
  dialogVisible.value = false
  emit('success')
  ElMessage.success('分配用户完成')
}

const handleUnallocatedSizeChange = (val: number) => {
  unallocatedQuery.pageSize = val
  getUnallocatedUserList()
}

const handleUnallocatedCurrentChange = (val: number) => {
  unallocatedQuery.pageNum = val
  getUnallocatedUserList()
}

const handleAllocatedSizeChange = (val: number) => {
  allocatedQuery.pageSize = val
  getAllocatedUserList()
}

const handleAllocatedCurrentChange = (val: number) => {
  allocatedQuery.pageNum = val
  getAllocatedUserList()
}

const resetData = () => {
  unallocatedUserList.value = []
  allocatedUserList.value = []
  unallocatedTotal.value = 0
  allocatedTotal.value = 0
  unallocatedSelection.value = []
  allocatedSelection.value = []
  Object.assign(unallocatedQuery, {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
  })
  Object.assign(allocatedQuery, {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
  })
}

const handleClose = () => {
  resetData()
  dialogVisible.value = false
}

// 监听弹窗打开
watch(
  () => props.visible,
  (newVal) => {
    if (newVal && props.roleId) {
      resetData()
      getUnallocatedUserList()
      getAllocatedUserList()
    }
  }
)
</script>

<style scoped lang="scss">
.auth-user-container {
  display: flex;
  gap: 20px;

  .left-panel,
  .right-panel {
    flex: 1;

    .panel-header {
      display: flex;
      align-items: center;
      margin-bottom: 10px;
      font-weight: bold;
    }

    .panel-pagination {
      margin-top: 10px;
      display: flex;
      justify-content: flex-end;
    }
  }

  .center-panel {
    display: flex;
    align-items: center;
    justify-content: center;

    .transfer-buttons {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
  }
}

.dialog-footer {
  text-align: right;
}
</style>
