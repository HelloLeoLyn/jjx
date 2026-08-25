<template>
  <el-dialog
    :title="title"
    v-model="dialogVisible"
    width="600px"
    append-to-body
    @close="handleCancel"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="上级部门" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="deptOptions"
          :props="defaultProps"
          value-key="id"
          placeholder="选择上级部门"
          check-strictly
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="部门名称" prop="deptName">
        <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
      </el-form-item>
      <el-form-item label="显示排序" prop="orderNum">
        <el-input-number v-model="formData.orderNum" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item label="负责人" prop="leader">
        <UserSelect
          v-model="formData.leaderUserId"
          placeholder="搜索用户名/姓名选择负责人"
          @change="handleLeaderChange"
          @clear="handleLeaderClear"
        />
      </el-form-item>
      <el-form-item label="联系电话" prop="phone">
        <el-input v-model="formData.phone" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="formData.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formData.status">
          <el-radio value="0">正常</el-radio>
          <el-radio value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="formData.remark"
          type="textarea"
          placeholder="请输入内容"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { deptApi } from '@/api/system/dept'
import UserSelect from '@/components/system/UserSelect.vue'
import type { SysDept, SysUser } from '@/types/system'

interface Props {
  visible: boolean
  title: string
  formData: {
    id?: number
    parentId: number
    deptName: string
    orderNum: number
    leader: string
    leaderUserId?: number | null
    phone: string
    email: string
    status: string
    remark: string
  }
  deptOptions: SysDept[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
  cancel: []
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const formRef = ref<FormInstance>()
const submitLoading = ref(false)

const defaultProps = {
  children: 'children',
  label: 'deptName',
}

const rules = reactive<FormRules>({
  deptName: [{ required: true, message: '部门名称不能为空', trigger: 'blur' }],
  orderNum: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
})

/** DEV-1106：选中用户后联动填充负责人姓名；部门邮箱/电话为空时自动带出用户信息（不覆盖已填） */
const handleLeaderChange = (user: SysUser | null) => {
  // 回显同步导致的 change(null) 不处理，避免误清空已有负责人姓名
  if (!user) return
  props.formData.leader = user.nickName || user.userName
  if (!props.formData.email && user.email) {
    props.formData.email = user.email
  }
  if (!props.formData.phone && user.phone) {
    props.formData.phone = user.phone
  }
}

/** DEV-1106：用户主动清空选择 → 同时清空负责人姓名与用户ID */
const handleLeaderClear = () => {
  props.formData.leader = ''
  props.formData.leaderUserId = null
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      // DEV-1106：leaderUserId 为 null（清空）时按 undefined 提交，避免 null 写库
      const payload = {
        ...props.formData,
        leaderUserId: props.formData.leaderUserId ?? undefined,
      }
      if (props.formData.id) {
        await deptApi.edit(payload)
        ElMessage.success('修改成功')
      } else {
        await deptApi.add(payload)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      emit('success')
    } catch (error) {
      console.error('保存部门失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleCancel = () => {
  dialogVisible.value = false
  emit('cancel')
}
</script>

<style scoped lang="scss">
.dialog-footer {
  text-align: right;
}
</style>
