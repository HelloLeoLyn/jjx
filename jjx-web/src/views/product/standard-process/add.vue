<template>
  <div class="standard-process-add">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>新增标准工序</span>
          <div>
            <el-button @click="handleBack">返回</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit"
              >保存</el-button
            >
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序编码" prop="processCode">
              <el-input v-model="formData.processCode" placeholder="自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="processName">
              <el-input
                v-model="formData.processName"
                placeholder="请输入工序名称"
                @blur="handleProceessNameChanage"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序类型" prop="processType">
              <el-select
                v-model="formData.processType"
                placeholder="请选择工序类型"
                style="width: 100%"
                @change="handleProcessTypeChange"
              >
                <el-option
                  v-for="item in processTypeOptions"
                  :key="item.itemValue"
                  :label="item.label"
                  :value="item.itemValue"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序类别" prop="processCategory">
              <el-select
                v-model="formData.processCategory"
                placeholder="请选择工序类别"
                style="width: 100%"
                @change="handleProcessCategoryChange"
              >
                <el-option
                  v-for="item in processCategoryOptions"
                  :key="item.itemValue"
                  :label="item.label"
                  :value="item.itemValue"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准人工工时" prop="standardLaborHours">
              <el-input-number
                v-model="formData.standardLaborHours"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
                placeholder="请输入标准人工工时"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准机器工时" prop="standardMachineHours">
              <el-input-number
                v-model="formData.standardMachineHours"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
                placeholder="请输入标准机器工时"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="displayOrder">
              <el-input-number
                v-model="formData.displayOrder"
                :min="0"
                :step="1"
                style="width: 100%"
                placeholder="请输入显示顺序"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用" prop="isEnabled">
              <el-radio-group v-model="formData.isEnabled">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="设备类型" prop="equipmentType">
              <el-input v-model="formData.equipmentType" placeholder="请输入设备类型" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技能要求" prop="skillRequirement">
              <el-input v-model="formData.skillRequirement" placeholder="请输入技能要求" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="质量标准" prop="qualityStandard">
              <el-input
                v-model="formData.qualityStandard"
                type="textarea"
                :rows="2"
                placeholder="请输入质量标准"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺参数模板" prop="processParamTemplate">
              <el-input
                v-model="formData.processParamTemplate"
                type="textarea"
                :rows="2"
                placeholder="请输入工艺参数模板"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="图标" prop="icon">
              <el-popover placement="bottom" trigger="click" width="400">
                <template #reference>
                  <el-input
                    v-model="formData.icon"
                    placeholder="点击选择图标"
                    readonly
                    style="cursor: pointer"
                  >
                    <template #prefix>
                      <SvgIcon v-if="formData.icon" :name="formData.icon" :size="20" />
                    </template>
                  </el-input>
                </template>
                <JJXIcon v-model="formData.icon" />
              </el-popover>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="工序说明" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="3"
                placeholder="请输入工序说明"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StandardProcessAdd',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import type { StandardProcessFormData } from '@/types/product/standardProcess'
import JJXIcon from '@/components/JJXIcon/index.vue'

const router = useRouter()
const route = useRoute()
const tagsViewStore = useTagsViewStore()

const formRef = ref<FormInstance>()
const submitLoading = ref(false)

// 工序类型/类别选项（字典维护）
const { options: processTypeOptions } = useDict('process_type')
const { options: processCategoryOptions } = useDict('process_category')

// 表单数据
const formData = reactive<StandardProcessFormData>({
  processCode: '',
  processName: '',
  processType: '',
  processCategory: '',
  standardLaborHours: 0,
  standardMachineHours: 0,
  processParamTemplate: '',
  skillRequirement: '',
  equipmentType: '',
  qualityStandard: '',
  icon: '',
  description: '',
  isEnabled: 1,
  displayOrder: 0,
})

const rules = reactive<FormRules<StandardProcessFormData>>({
  processCode: [{ required: true, message: '工序编码不能为空', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }],
  processType: [{ required: true, message: '请选择工序类型', trigger: 'change' }],
  processCategory: [{ required: true, message: '请选择工序类别', trigger: 'change' }],
})

// ==================== 编码生成 ====================

/**
 * 生成工序编码
 * 规则：T{工序类型编码}+C{工序类别编码}+3位序号
 * 示例：T4C3001
 */
const generateProcessCode = async () => {
  if (!formData.processType || !formData.processCategory) {
    formData.processCode = ''
    return
  }

  try {
    const res = await standardProcessApi.generateNextProcessCode(
      formData.processType,
      formData.processCategory
    )
    formData.processCode = res.data || ''
  } catch (error) {
    console.error('生成工序编码失败:', error)
    formData.processCode = ''
  }
}

// ==================== 事件 ====================
const handleProceessNameChanage = () => {
  if (formData.processType && formData.processCategory) {
    generateProcessCode()
  }
}
const handleProcessTypeChange = () => {
  generateProcessCode()
}

const handleProcessCategoryChange = () => {
  generateProcessCode()
}

// 提交保存
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitLoading.value = true
    await standardProcessApi.create(formData)
    ElMessage.success('新增成功')
    //清空表单
    // formData.processCode = ''
    // formData.processName = ''
    // // 关闭当前标签页并跳转到列表页
    // tagsViewStore.delView({
    //   name: route.name as string,
    //   path: route.path,
    //   title: (route.meta?.title as string) || '新增标准工序',
    //   query: route.query,
    //   meta: route.meta,
    // })
    // router.replace('/product/standard-process')
  } catch (error) {
    console.error('新增标准工序失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 返回
const handleBack = () => {
  router.push('/product/standard-process')
}

onMounted(() => {
  // useDict 会自动加载，无需手动调用
})
</script>

<style scoped>
.standard-process-add {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
