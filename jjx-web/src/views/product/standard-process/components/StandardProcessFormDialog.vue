<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="900px"
    append-to-body
    destroy-on-close
  >
    <div v-loading="loading">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序编码" prop="processCode">
              <el-input v-model="formData.processCode" placeholder="自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="processName">
              <el-input v-model="formData.processName" placeholder="请输入工序名称" />
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
                @change="handleProcessAttrChange"
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
                @change="handleProcessAttrChange"
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
                <el-radio :value="CommonStatusEnum.NORMAL.value">启用</el-radio>
                <el-radio :value="CommonStatusEnum.DISABLED.value">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否带下标" prop="hasIndex">
              <el-checkbox
                :model-value="formData.hasIndex === 1"
                @change="(v: boolean | string | number) => (formData.hasIndex = v ? 1 : 0)"
              >
                带下标（在工艺路线中需输入下标数字，如 ④）
              </el-checkbox>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否带作业说明" prop="hasWorkInstruction">
              <el-checkbox
                :model-value="formData.hasWorkInstruction === 1"
                @change="(v: boolean | string | number) => (formData.hasWorkInstruction = v ? 1 : 0)"
              >带作业说明（使用时填写，如：线路外形）</el-checkbox>
            </el-form-item>
          </el-col>
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
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确 定</el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { standardProcessApi } from '@/api/product/standardProcess'
import JJXIcon from '@/components/JJXIcon/index.vue'
import { useDict } from '@/composables/useDict'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import type { StandardProcessFormData } from '@/types/product/standardProcess'

interface Props {
  modelValue: boolean
  processId?: number
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  processId: undefined,
})
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const title = computed(() => (props.processId !== undefined ? '编辑标准工序' : '新增标准工序'))
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)

const { options: processTypeOptions } = useDict('process_type')
const { options: processCategoryOptions } = useDict('process_category')

const formData = reactive<StandardProcessFormData>({
  processId: undefined,
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
  hasIndex: 0,
  hasWorkInstruction: 0,
  isEnabled: CommonStatusEnum.NORMAL.value,
  displayOrder: 0,
})

const rules = reactive<FormRules<StandardProcessFormData>>({
  processCode: [{ required: true, message: '工序编码不能为空', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }],
  processType: [{ required: true, message: '请选择工序类型', trigger: 'change' }],
  processCategory: [{ required: true, message: '请选择工序类别', trigger: 'change' }],
})

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    processId: undefined,
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
    hasIndex: 0,
    hasWorkInstruction: 0,
    isEnabled: CommonStatusEnum.NORMAL.value,
    displayOrder: 0,
  })
  formRef.value?.clearValidate()
}

const generateProcessCode = async () => {
  if (!formData.processType || !formData.processCategory) return

  try {
    const res = await standardProcessApi.generateNextProcessCode(
      formData.processType,
      formData.processCategory
    )
    formData.processCode = res.data || ''
  } catch (error) {
    console.error('生成工序编码失败:', error)
  }
}

// 编码单规则（2026-09-16）：SP-<段位><序号>，段位 = 1面板/2上线/3下线/4其他，后端按工序类别生成。
// 只有"新增未保存"时随类型/类别重算；编辑已有工序时编码一经分配即稳定，不再变更。
const canRegenerateCode = computed(() => props.processId === undefined)

const handleProcessAttrChange = () => {
  if (!canRegenerateCode.value) return
  generateProcessCode()
}

const loadData = async (processId: number) => {
  loading.value = true
  try {
    const response = await standardProcessApi.getById(processId)
    const data = response.data
    if (data) {
      Object.assign(formData, {
        processId: data.processId,
        processCode: data.processCode,
        processName: data.processName,
        processType: data.processType,
        processCategory: data.processCategory,
        standardLaborHours: data.standardLaborHours,
        standardMachineHours: data.standardMachineHours,
        processParamTemplate: data.processParamTemplate,
        skillRequirement: data.skillRequirement,
        equipmentType: data.equipmentType,
        qualityStandard: data.qualityStandard,
        icon: data.icon,
        description: data.description,
        hasIndex: data.hasIndex,
        hasWorkInstruction: data.hasWorkInstruction,
        isEnabled: data.isEnabled,
        displayOrder: data.displayOrder,
      })
    }
  } catch (error) {
    console.error('加载标准工序详情失败:', error)
    ElMessage.error('加载标准工序详情失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitLoading.value = true
    if (props.processId !== undefined) {
      await standardProcessApi.update(props.processId, formData)
      ElMessage.success('修改成功')
    } else {
      await standardProcessApi.create(formData)
      ElMessage.success('新增成功')
    }
    emit('success')
    visible.value = false
  } catch (error) {
    console.error(`${props.processId !== undefined ? '修改' : '新增'}标准工序失败:`, error)
  } finally {
    submitLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    resetForm()
    if (props.processId !== undefined) loadData(props.processId)
  }
)
</script>
