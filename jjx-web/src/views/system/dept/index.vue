<template>
  <div class="dept-container">
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
        :selected-count="checkedIdList.length"
        :show-batch-bar="true"
        @click="handleToolbarClick"
        @refresh="getList"
      >
      </Toolbar>

      <!-- 表格区域 -->
      <el-table
        v-loading="loading"
        :data="deptList"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        row-key="id"
        border
        style="width: 100%"
      >
        <!-- 选择列 -->
        <el-table-column type="selection" width="55" align="center" />
        <!-- 序号列 -->
        <el-table-column type="index" label="序号" width="60" />
        <!-- 部门名称列 -->
        <el-table-column prop="deptName" label="部门名称" min-width="180" />
        <!-- 显示排序列 -->
        <el-table-column prop="orderNum" label="显示排序" width="120" align="center" />
        <!-- 负责人列 -->
        <el-table-column prop="leader" label="负责人" width="150" />
        <!-- 联系电话列 -->
        <el-table-column prop="phone" label="联系电话" width="150" />
        <!-- 邮箱列 -->
        <el-table-column prop="email" label="邮箱" width="200" />
        <!-- 状态列 -->
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 创建时间列 -->
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            <span>{{ parseTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <!-- 操作列 -->
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handleUpdate(row)"
              v-hasPermi="['system:dept:edit']"
            >
              修改
            </el-button>
            <el-button
              link
              type="danger"
              @click="handleDelete(row)"
              v-hasPermi="['system:dept:remove']"
            >
              删除
            </el-button>
            <el-button link type="success" @click="handleAdd(row)" v-hasPermi="['system:dept:add']">
              新增
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加或修改部门对话框 -->
    <DeptFormDialog
      v-model:visible="dialog.visible"
      :title="dialog.title"
      :form-data="form"
      :dept-options="deptOptions"
      @success="handleDialogSuccess"
      @cancel="cancel"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Dept',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { Toolbar, SearchForm } from '@/components/common-ui/index'
import { deptApi } from '@/api/system/dept'
import type { SysDept } from '@/types/system'
import * as uiConfig from './index'
import { parseTime } from '@/utils/format'
import DeptFormDialog from './components/DeptFormDialog.vue'

// ==================== 状态 ====================
const loading = ref(false)
const deptList = ref<SysDept[]>([])
const checkedIdList = ref<number[]>([])
const names = ref<string[]>([])

const queryParams = reactive({
  deptName: undefined,
  status: undefined,
})

const dialog = reactive({
  visible: false,
  title: '',
})

const form = reactive({
  id: undefined,
  parentId: 0,
  deptName: '',
  orderNum: 0,
  leader: '',
  leaderUserId: null,
  phone: '',
  email: '',
  status: '0',
})

const deptOptions = ref<SysDept[]>([])

// ==================== API ====================
const getList = async () => {
  loading.value = true
  try {
    const params = {
      ...queryParams,
      deptName: queryParams.deptName || '',
      status: queryParams.status || '',
    }
    const res = await deptApi.treeselect(params)
    deptList.value = res.data || []
  } catch (error) {
    console.error('获取部门列表失败:', error)
  } finally {
    loading.value = false
  }
}

const getDeptTree = async () => {
  try {
    const res = await deptApi.treeselect({})
    if (res.data) {
      deptOptions.value = res.data
    }
  } catch (error) {
    console.error('获取部门树失败:', error)
  }
}

// ==================== 事件 ====================
const handleQuery = () => {
  getList()
}

const resetQuery = () => {
  queryParams.deptName = undefined
  queryParams.status = undefined
  handleQuery()
}

const handleAdd = (row?: SysDept) => {
  resetForm()
  if (row && 'id' in row && row.id) {
    form.parentId = row.id
  }
  getDeptTree()
  dialog.visible = true
  dialog.title = '添加部门'
}

const handleUpdate = async (row: SysDept) => {
  resetForm()
  const id = row.id || checkedIdList.value[0]
  if (!id) return

  try {
    const res = await deptApi.getInfo(id)
    if (res.data) {
      Object.assign(form, res.data)
      dialog.visible = true
      dialog.title = '修改部门'
      getDeptTree()
    } else {
      ElMessage.error('获取部门信息失败')
    }
  } catch (error) {
    console.error('获取部门信息失败:', error)
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    parentId: 0,
    deptName: '',
    orderNum: 0,
    leader: '',
    leaderUserId: null,
    phone: '',
    email: '',
    status: '0',
  })
}

const cancel = () => {
  dialog.visible = false
  resetForm()
}

// 对话框成功回调
const handleDialogSuccess = () => {
  getList()
}

const handleDelete = async (row?: SysDept) => {
  const deptIds = row ? [row.id!] : checkedIdList.value
  const deptNames = row ? [row.deptName] : names.value

  if (deptIds.length === 0) {
    ElMessage.warning('请选择要删除的数据')
    return
  }

  try {
    await ElMessageBox.confirm(`是否确认删除部门名为"${deptNames.join(',')}"的数据项？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await deptApi.remove(deptIds[0])
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    // 用户取消删除
  }
}

const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
  if (key === 'export') handleExport()
}

const handleExport = () => {
  ElMessage.info('导出功能待实现')
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.dept-container {
  padding: 20px;
}
</style>
