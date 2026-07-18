<template>
  <el-dialog title="数据权限" v-model="visible" width="800px" append-to-body @close="handleClose">
    <div class="data-scope-container">
      <!-- 角色信息 -->
      <div class="role-info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="角色名称">{{ roleInfo?.roleName }}</el-descriptions-item>
          <el-descriptions-item label="权限字符">{{ roleInfo?.roleKey }}</el-descriptions-item>
          <el-descriptions-item label="角色ID">{{ roleInfo?.roleId }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="roleInfo?.status === '0' ? 'success' : 'danger'">
              {{ roleInfo?.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 数据权限配置 -->
      <div class="data-scope-config">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="数据范围" prop="dataScope">
            <el-radio-group
              v-model="form.dataScope"
              @change="
                (val: string | number | boolean | undefined) => handleDataScopeChange(val as string)
              "
            >
              <el-radio value="1">全部数据权限</el-radio>
              <el-radio value="2">自定数据权限</el-radio>
              <el-radio value="3">本部门数据权限</el-radio>
              <el-radio value="4">本部门及以下数据权限</el-radio>
              <el-radio value="5">仅本人数据权限</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 部门选择（当数据范围为自定时显示） -->
          <el-form-item label="选择部门" prop="deptIds" v-if="form.dataScope === '2'">
            <el-tree
              ref="deptTreeRef"
              :data="deptOptions"
              show-checkbox
              node-key="deptId"
              :props="defaultProps"
              empty-text="加载中，请稍后"
              :check-strictly="false"
              style="max-height: 300px; overflow: auto"
            />
          </el-form-item>

          <!-- 权限说明 -->
          <el-form-item label="权限说明">
            <div class="permission-description">
              <p v-if="form.dataScope === '1'">拥有所有数据的访问权限</p>
              <p v-if="form.dataScope === '2'">自定义选择可访问的部门数据</p>
              <p v-if="form.dataScope === '3'">只能访问本部门的数据</p>
              <p v-if="form.dataScope === '4'">可以访问本部门及所有下级部门的数据</p>
              <p v-if="form.dataScope === '5'">只能访问自己的数据</p>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitForm" :loading="loading">确 定</el-button>
        <el-button @click="handleClose">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { roleApi } from '@/api/system/role'
import { deptApi } from '@/api/system/dept'
import type { SysRole, SysDept } from '@/types/system'

// 组件属性
interface Props {
  modelValue: boolean
  roleInfo?: SysRole
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

// 表单引用
const formRef = ref<FormInstance>()
const deptTreeRef = ref<TreeInstance>()

// 加载状态
const loading = ref(false)

// 部门选项
const deptOptions = ref<SysDept[]>([])

// 树形组件配置
const defaultProps = {
  children: 'children',
  label: 'deptName',
}

// 表单数据
const form = reactive({
  roleId: undefined as number | undefined,
  dataScope: '1', // 1:全部 2:自定 3:本部门 4:本部门及以下 5:仅本人
  deptIds: [] as number[],
})

// 表单验证规则
const rules = reactive<FormRules>({
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
})

// 对话框显示状态
const visible = ref(false)

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val && props.roleInfo) {
      loadData()
    }
  }
)

// 监听visible变化
watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 加载数据
const loadData = async () => {
  if (!props.roleInfo?.roleId) return

  try {
    loading.value = true

    // 1. 加载部门树
    const deptRes = await deptApi.treeselect({})
    if (deptRes.data) {
      deptOptions.value = deptRes.data
    }

    // 2. 加载角色的数据权限配置
    const roleRes = await roleApi.getDataScope(props.roleInfo.roleId)
    if (roleRes.data) {
      Object.assign(form, {
        roleId: roleRes.data.roleId,
        dataScope: roleRes.data.dataScope?.toString() || '1',
        deptIds: roleRes.data.deptIds || [],
      })

      // 设置选中的部门
      nextTick(() => {
        if (deptTreeRef.value && form.deptIds.length > 0) {
          deptTreeRef.value.setCheckedKeys(form.deptIds)
        }
      })
    }
  } catch (error) {
    console.error('加载数据权限配置失败:', error)
    ElMessage.error('加载数据权限配置失败')
  } finally {
    loading.value = false
  }
}

// 处理数据范围变化
const handleDataScopeChange = (value: string) => {
  // 当选择非自定范围时，清空部门选择
  if (value !== '2') {
    form.deptIds = []
    if (deptTreeRef.value) {
      deptTreeRef.value.setCheckedKeys([])
    }
  }
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 获取选中的部门ID（当数据范围为自定时）
    if (form.dataScope === '2' && deptTreeRef.value) {
      const checkedKeys = deptTreeRef.value.getCheckedKeys()
      const halfCheckedKeys = deptTreeRef.value.getHalfCheckedKeys()
      form.deptIds = [...checkedKeys, ...halfCheckedKeys].map((key) => Number(key))
    }

    try {
      loading.value = true

      const params = {
        roleId: form.roleId ? form.roleId : 0,
        dataScope: Number(form.dataScope),
        deptIds: form.dataScope === '2' ? form.deptIds : [],
      }

      await roleApi.updateDataScope(params)
      ElMessage.success('数据权限配置成功')
      emit('success')
      handleClose()
    } catch (error) {
      console.error('保存数据权限配置失败:', error)
      ElMessage.error('保存数据权限配置失败')
    } finally {
      loading.value = false
    }
  })
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  resetForm()
}

// 重置表单
const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  if (deptTreeRef.value) {
    deptTreeRef.value.setCheckedKeys([])
  }
  Object.assign(form, {
    roleId: undefined,
    dataScope: '1',
    deptIds: [],
  })
}
</script>

<style scoped lang="scss">
.data-scope-container {
  .role-info {
    margin-bottom: 20px;
  }

  .data-scope-config {
    .permission-description {
      padding: 10px;
      background-color: #f5f7fa;
      border-radius: 4px;
      color: #666;
      font-size: 14px;

      p {
        margin: 5px 0;
      }
    }
  }
}

.dialog-footer {
  text-align: right;
}
</style>
