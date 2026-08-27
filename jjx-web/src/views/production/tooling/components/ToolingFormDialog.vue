<template>
  <el-dialog
    :model-value="modelValue"
    :title="form.toolingId ? '编辑工装模具' : '新增工装模具'"
    width="640px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="handleOpen"
  >
    <el-form ref="formRef" :model="form" label-width="110px" :rules="rules">
      <!-- 类型 -->
      <el-form-item label="类型" prop="toolingType">
        <el-radio-group v-model="form.toolingType">
          <el-radio-button value="SCREEN">网框</el-radio-button>
          <el-radio-button value="DIE">刀模</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <!-- 编号 + 生成 -->
      <el-form-item label="编号" prop="toolingNo">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-input v-model="form.toolingNo" placeholder="点击右侧按钮自动生成，也可手动输入" />
          <el-button :loading="genLoading" @click="handleGenNo">生成编号</el-button>
        </div>
      </el-form-item>

      <!-- 名称 -->
      <el-form-item label="名称" prop="toolingName">
        <el-input v-model="form.toolingName" placeholder="如 3#丝印网框 / 主面板模切刀模" />
      </el-form-item>

      <!-- 参数 -->
      <el-form-item label="参数">
        <el-input
          v-model="form.spec"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="如：材质：xxx&#10;尺寸：xxx"
        />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="设计寿命(次)">
            <el-input-number v-model="form.lifeLimit" :min="1" style="width: 100%" placeholder="刀模冲切次数上限" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="已冲切次数">
            <el-input-number v-model="form.currentCount" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="存放位置">
            <el-input v-model="form.location" placeholder="如 B区-3号柜" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户">
            <el-input v-model="form.customer" placeholder="定制工装所属客户" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="责任人">
            <el-input v-model="form.responsible" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用日期">
            <el-date-picker v-model="form.enableDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" style="width: 100%">
              <el-option
                v-for="s in ToolingStatusEnum.items"
                :key="s.value"
                :label="s.label"
                :value="s.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 实物照片 -->
      <el-form-item label="实物照片">
        <div class="photo-uploader">
          <img
            v-if="previewUrl"
            :src="previewUrl"
            class="photo-preview"
            alt="实物照片"
          />
          <div v-else class="photo-placeholder">未上传</div>
          <div class="photo-actions">
            <el-button size="small" @click="pickFile">选择照片</el-button>
            <el-button v-if="previewUrl" size="small" type="danger" plain @click="removePhoto">移除</el-button>
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              style="display: none"
              @change="onFileChange"
            />
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { genToolingNo, type ToolingForm, type ToolingVO } from '@/api/production/tooling'
import { attachmentApi } from '@/api/system/attachment'
import { ToolingStatusEnum } from '@/enums/production/ToolingEnum'

const props = defineProps<{
  modelValue: boolean
  formData: ToolingVO | null
  photoId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submit', payload: { form: ToolingForm; photoFile: File | null; removeOldPhoto: boolean }): void
}>()

const formRef = ref<FormInstance>()
const genLoading = ref(false)
const submitting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const photoFile = ref<File | null>(null)
const removeOldPhoto = ref(false)

const defaultForm = (): ToolingForm => ({
  toolingId: undefined,
  toolingNo: '',
  toolingName: '',
  toolingType: 'SCREEN',
  spec: '',
  lifeLimit: undefined,
  currentCount: 0,
  status: 0,
  location: '',
  department: '',
  responsible: '',
  customer: '',
  enableDate: '',
  remark: '',
})

const form = reactive<ToolingForm>(defaultForm())

const rules = {
  toolingType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  toolingNo: [{ required: true, message: '请填写编号或点击生成', trigger: 'blur' }],
  toolingName: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

// 照片预览：新选的文件优先，否则用已存在的附件地址
const previewUrl = computed(() => {
  if (photoFile.value) return URL.createObjectURL(photoFile.value)
  if (props.photoId) return attachmentApi.downloadUrl(props.photoId)
  return ''
})

function handleOpen() {
  Object.assign(form, defaultForm())
  photoFile.value = null
  removeOldPhoto.value = false
  if (props.formData) {
    Object.assign(form, {
      toolingId: props.formData.toolingId,
      toolingNo: props.formData.toolingNo,
      toolingName: props.formData.toolingName,
      toolingType: props.formData.toolingType,
      spec: props.formData.spec || '',
      lifeLimit: props.formData.lifeLimit,
      currentCount: props.formData.currentCount ?? 0,
      status: props.formData.status ?? 0,
      location: props.formData.location || '',
      department: props.formData.department || '',
      responsible: props.formData.responsible || '',
      customer: props.formData.customer || '',
      enableDate: props.formData.enableDate || '',
      remark: props.formData.remark || '',
    })
  }
  formRef.value?.clearValidate()
}

async function handleGenNo() {
  if (!form.toolingType) return
  genLoading.value = true
  try {
    const res: any = await genToolingNo(form.toolingType)
    form.toolingNo = res?.data || ''
  } catch (e: any) {
    ElMessage.error(e?.message || '编号生成失败')
  } finally {
    genLoading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  photoFile.value = file
  removeOldPhoto.value = true
  input.value = ''
}

function removePhoto() {
  photoFile.value = null
  removeOldPhoto.value = true
}

async function handleSubmit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  submitting.value = true
  try {
    emit('submit', {
      form: { ...form },
      photoFile: photoFile.value,
      removeOldPhoto: removeOldPhoto.value,
    })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.photo-uploader {
  display: flex;
  align-items: center;
  gap: 12px;
}
.photo-preview {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #e4e7ed;
}
.photo-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
  background: #fafafa;
}
</style>
