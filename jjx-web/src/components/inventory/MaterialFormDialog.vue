<template>
  <el-dialog :title="title" v-model="visible" width="700px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input
              v-model="form.materialCode"
              placeholder="系统自动生成"
              :readonly="!!form.materialId"
            >
              <template #append>
                <el-button :icon="Search" @click="handleGenerateCode" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="form.materialName" placeholder="请输入物料名称" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="物料类型" prop="materialType">
            <el-select v-model="form.materialType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="opt in MaterialTypeEnum.items"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="form.unit" placeholder="如：PCS、KG" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="规格型号" prop="specification">
            <el-input v-model="form.specification" placeholder="请输入规格型号" /> </el-form-item
        ></el-col>
        <el-col :span="12">
          <el-form-item label="机种" prop="materialNameEn">
            <el-input
              v-model="form.materialNameEn"
              placeholder="请输入机种（如：JST263）"
            /> </el-form-item></el-col
      ></el-row>
      <el-divider content-position="left">库存参数</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="安全库存" prop="safeStock">
            <el-input-number v-model="form.safeStock" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最高库存" prop="maxStock">
            <el-input-number v-model="form.maxStock" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="再订货点" prop="reorderPoint">
            <el-input-number v-model="form.reorderPoint" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="批次管理" prop="batchControl">
            <el-switch v-model="form.batchControl" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">采购信息</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="标准单价" prop="standardPrice">
            <el-input-number
              v-model="form.standardPrice"
              :min="0"
              :precision="4"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="采购周期(天)" prop="leadTime">
            <el-input-number v-model="form.leadTime" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="主要供应商" prop="supplierName">
        <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
      </el-form-item>

      <el-divider content-position="left">保质期管理</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="保质期(天)" prop="shelfLife">
            <el-input-number v-model="form.shelfLife" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预警提前天数" prop="expiryAlertDays">
            <el-input-number v-model="form.expiryAlertDays" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { materialApi } from '@/api/inventory/material'
import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
import type {
  MaterialSaveDTO,
  MaterialUpdateDTO,
  InventoryMaterial,
} from '@/types/inventory/material'

interface Props {
  modelValue: boolean
  materialId?: number | null
  /** 预填数据（用于从BOM编辑器快速建档时传入物料名称、规格等） */
  presetData?: {
    materialName?: string
    specification?: string
    unit?: string
  }
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', material: InventoryMaterial): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
  presetData: undefined,
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const title = computed(() => (props.materialId ? '编辑物料' : '新增物料'))

const submitting = ref(false)
const formRef = ref()

const form = reactive({
  materialId: null as number | null,
  materialCode: '',
  materialName: '',
  materialType: '',
  materialNameEn: '',
  specification: '',
  unit: 'PCS',
  safeStock: 0,
  maxStock: 0,
  reorderPoint: 0,
  standardPrice: null as number | null,
  leadTime: null as number | null,
  supplierName: '',
  batchControl: false,
  shelfLife: null as number | null,
  expiryAlertDays: 30,
  remark: '',
})

const rules = {
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
}

const resetForm = () => {
  form.materialId = null
  form.materialCode = ''
  form.materialName = ''
  form.materialType = ''
  form.materialNameEn = ''
  form.specification = ''
  form.unit = 'PCS'
  form.safeStock = 0
  form.maxStock = 0
  form.reorderPoint = 0
  form.standardPrice = null
  form.leadTime = null
  form.supplierName = ''
  form.batchControl = false
  form.shelfLife = null
  form.expiryAlertDays = 30
  form.remark = ''
}

const handleGenerateCode = async () => {
  try {
    const res = await materialApi.generateCode()
    if (res.data) {
      form.materialCode = res.data
    }
  } catch (error) {
    ElMessage.error('生成物料编码失败')
  }
}

const loadMaterialInfo = async (materialId: number) => {
  try {
    const res = await materialApi.getInfo(String(materialId))
    Object.assign(form, res.data)
  } catch (error) {
    ElMessage.error('加载物料信息失败')
  }
}

const submitForm = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    submitting.value = true
    try {
      if (form.materialId) {
        const updateData: MaterialUpdateDTO = {
          materialId: form.materialId,
          materialCode: form.materialCode,
          materialName: form.materialName,
          materialType: form.materialType,
          specification: form.specification,
          unit: form.unit,
          safeStock: form.safeStock,
          maxStock: form.maxStock,
          reorderPoint: form.reorderPoint,
          standardPrice: form.standardPrice || undefined,
          leadTime: form.leadTime || undefined,
          supplierName: form.supplierName,
          batchControl: form.batchControl,
          shelfLife: form.shelfLife || undefined,
          expiryAlertDays: form.expiryAlertDays,
          remark: form.remark,
        }
        await materialApi.update(updateData)
        ElMessage.success('修改成功')
        visible.value = false
        emit('success', { ...form } as InventoryMaterial)
      } else {
        const saveData: MaterialSaveDTO = {
          materialCode: form.materialCode,
          materialName: form.materialName,
          materialType: form.materialType,
          specification: form.specification,
          unit: form.unit,
          safeStock: form.safeStock,
          maxStock: form.maxStock,
          reorderPoint: form.reorderPoint,
          standardPrice: form.standardPrice || undefined,
          leadTime: form.leadTime || undefined,
          supplierName: form.supplierName,
          batchControl: form.batchControl,
          shelfLife: form.shelfLife || undefined,
          expiryAlertDays: form.expiryAlertDays,
          remark: form.remark,
        }
        await materialApi.add(saveData)
        ElMessage.success('新增成功')
        visible.value = false
        // 新增后查询完整物料信息返回
        const res = await materialApi.getByCode(form.materialCode)
        emit('success', res.data || ({ ...form } as InventoryMaterial))
      }
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

const handleCancel = () => {
  visible.value = false
}

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      resetForm()
      if (props.materialId) {
        loadMaterialInfo(props.materialId)
      } else {
        // 自动生成编码
        handleGenerateCode()
        // 如果有预填数据，填充到表单
        if (props.presetData) {
          if (props.presetData.materialName) form.materialName = props.presetData.materialName
          if (props.presetData.specification) form.specification = props.presetData.specification
          if (props.presetData.unit) form.unit = props.presetData.unit
        }
      }
    }
  }
)
</script>
