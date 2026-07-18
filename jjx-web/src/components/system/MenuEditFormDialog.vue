<template>
  <el-dialog
    :title="title"
    v-model="dialogVisible"
    width="800px"
    append-to-body
    @close="handleCancel"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-row>
        <el-col :span="24">
          <el-form-item label="上级菜单">
            <el-tree-select
              v-model="formData.parentId"
              :data="menuOptions"
              :props="defaultProps"
              value-key="menuId"
              placeholder="选择上级菜单"
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-form-item label="菜单类型" prop="menuType">
            <el-radio-group v-model="formData.menuType">
              <el-radio value="M">目录</el-radio>
              <el-radio value="C">菜单</el-radio>
              <el-radio value="F">按钮</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="formData.menuType === 'F'">
        <el-col :span="24">
          <el-form-item label="按钮列表" prop="perms">
            <div class="button-row">
              <el-button type="primary" dashed @click="handleBtnClick('add', 'primary', '新增')"
                >新增</el-button
              >
              <el-button type="warning" dashed @click="handleBtnClick('edit', 'warning', '修改')"
                >修改</el-button
              >
              <el-button type="danger" dashed @click="handleBtnClick('delete', 'danger', '删除')"
                >删除</el-button
              >
              <el-button type="success" dashed @click="handleBtnClick('query', 'success', '查询')"
                >查询</el-button
              >
              <el-button type="info" dashed @click="handleBtnClick('other', 'info', '其他')"
                >其他</el-button
              >
            </div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="菜单图标" prop="icon">
            <icon-selector v-model="formData.icon" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="菜单名称" prop="menuName">
            <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="显示排序" prop="orderNum">
            <el-input-number v-model="formData.orderNum" controls-position="right" :min="0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="路由地址" prop="path" v-if="formData.menuType !== 'F'">
            <el-input v-model="formData.path" placeholder="请输入路由地址" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="formData.menuType === 'C'">
        <el-col :span="24">
          <el-form-item label="组件路径" prop="component">
            <el-input v-model="formData.component" placeholder="请输入组件路径" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="权限标识" prop="perms">
            <el-input v-model="formData.perms" placeholder="请输入权限标识" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="显示状态">
            <el-radio-group v-model="formData.visible">
              <el-radio value="0">显示</el-radio>
              <el-radio value="1">隐藏</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="菜单状态">
            <el-radio-group v-model="formData.status">
              <el-radio value="0">正常</el-radio>
              <el-radio value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否缓存" v-if="formData.menuType === 'C'">
            <el-radio-group v-model="formData.isCache">
              <el-radio value="0">缓存</el-radio>
              <el-radio value="1">不缓存</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
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
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import IconSelector from '@/components/IconSelector.vue'
import { menuApi } from '@/api/system/menu'
import type { SysMenu } from '@/types/system'
import { removeLastSegment } from '@/utils/split'
import { menuDict } from '@/views/system/menu/index'

// Props
interface Props {
  visible: boolean
  title: string
  formData: Partial<SysMenu>
  menuOptions: SysMenu[]
}

const props = defineProps<Props>()

// Emits
const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
  cancel: []
}>()

// Refs
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 计算属性：控制对话框显示
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

// 树形组件配置
const defaultProps = {
  children: 'children',
  label: 'menuName',
}

// 表单验证规则
const rules: FormRules = {
  menuName: [{ required: true, message: '菜单名称不能为空', trigger: 'blur' }],
  orderNum: [{ required: true, message: '菜单顺序不能为空', trigger: 'blur' }],
  path: [{ required: true, message: '路由地址不能为空', trigger: 'blur' }],
}

// 获取 formData 中的 btn 属性（运行时动态添加）
const getBtn = () => {
  return (props.formData as any).btn as { label: string; type: string } | undefined
}

// 设置 formData 中的 btn 属性
const setBtn = (btn: { label: string; type: string }) => {
  ;(props.formData as any).btn = btn
}

// 按钮点击处理
const handleBtnClick = (label: string, type: string, title: string) => {
  let btn = getBtn()
  if (!btn) {
    btn = { label: '', type: '' }
    setBtn(btn)
  }
  btn.label = label
  btn.type = type
  generatePerms(title)
}

// 生成权限标识
const generatePerms = (title: string) => {
  if (props.formData.menuType === 'C') {
    props.formData.perms = props.formData.perms ? `${props.formData.perms}:` : ''
  } else if (props.formData.menuType === 'F') {
    // 生成权限标识，格式为：前缀:按钮类型（如：sys:user:add），获取父菜单的权限标识去除最后一个冒号后的部分作为前缀
    const permsArray = (props.formData.perms || '').split(':')
    const prefix = removeLastSegment(props.formData.perms || '')
    const btn = getBtn()
    props.formData.perms = `${prefix}:${btn?.label.toLowerCase() || ''}`
    if (permsArray.length > 2) {
      const a = permsArray[permsArray.length - 2]
      props.formData.menuName = title + (menuDict[a]?.text || '')
    } else {
      props.formData.menuName = title
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 准备提交数据，确保必需字段有值
      const submitData: SysMenu = {
        menuName: props.formData.menuName || '',
        menuType: props.formData.menuType || 'C',
        ...props.formData,
        // 确保数值字段有默认值
        parentId: props.formData.parentId || 0,
        orderNum: props.formData.orderNum || 0,
        status: props.formData.status || '0',
        visible: props.formData.visible || '0',
        isCache: props.formData.isCache || '0',
      } as SysMenu

      if (props.formData.menuId) {
        // 修改菜单
        await menuApi.edit(submitData)
        ElMessage.success('修改成功')
      } else {
        // 新增菜单
        await menuApi.add(submitData)
        ElMessage.success('添加成功')
      }

      // 提交成功，关闭对话框并通知父组件
      dialogVisible.value = false
      emit('success')
    } catch (error) {
      console.error('提交失败:', error)
      ElMessage.error('提交失败')
    } finally {
      submitting.value = false
    }
  })
}

// 取消操作
const handleCancel = () => {
  dialogVisible.value = false
  emit('cancel')
}

// 监听表单数据变化，确保 btn 对象存在
watch(
  () => props.formData,
  (newVal) => {
    const data = newVal as any
    if (!data.btn) {
      data.btn = { label: '', type: '' }
    }
  },
  { deep: true, immediate: true }
)
</script>

<style scoped lang="scss">
.button-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
