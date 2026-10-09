<template>
  <el-drawer :model-value="modelValue" :title="file?.fileName || '文件预览'" size="72%" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <div v-if="file" class="preview-tools">
      <span>{{ file.drawingNo || '历史文件' }} · {{ file.version || '未标版本' }}</span>
      <el-button @click="zoom = Math.max(40, zoom - 10)">−</el-button><span>{{ zoom }}%</span>
      <el-button @click="zoom = Math.min(150, zoom + 10)">＋</el-button>
      <el-button type="primary" :loading="downloading" @click="download">下载原文件</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading" class="file-stage">
      <div v-if="images.length" class="file-pages" :style="{ width: `${zoom}%` }">
        <div v-for="(page, i) in images" :key="page.url" class="file-page"><img :src="page.url" /><span v-if="images.length > 1">第 {{ i + 1 }} / {{ images.length }} 页</span></div>
      </div>
      <el-empty v-else-if="!loading && !error" description="此格式请下载后使用工程软件打开；可上传同版本的PDF或图片作为预览打印件。" />
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ProductDrawingFile } from '@/api/system/attachment'
import { downloadFile, fileKind, renderFile, type FileImage } from './productFilePreview'
const props = defineProps<{ modelValue: boolean; file?: ProductDrawingFile }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const images = shallowRef<FileImage[]>([])
const error = ref('')
const loading = ref(false)
const downloading = ref(false)
const zoom = ref(100)
let generation = 0
function clear() { images.value.forEach((page) => URL.revokeObjectURL(page.url)); images.value = [] }
watch(() => [props.modelValue, props.file?.id], async () => {
  const current = ++generation
  clear(); error.value = ''; loading.value = false; zoom.value = 100
  if (!props.modelValue || !props.file || fileKind(props.file) === 'other') return
  loading.value = true
  try {
    const pages = await renderFile({ id: props.file.id, kind: fileKind(props.file) })
    if (current !== generation) pages.forEach((page) => URL.revokeObjectURL(page.url))
    else images.value = pages
  } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '预览失败，请重试' }
  finally { if (current === generation) loading.value = false }
}, { immediate: true })
async function download() {
  if (!props.file) return
  downloading.value = true
  try { await downloadFile(props.file) }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '下载失败') }
  finally { downloading.value = false }
}
onBeforeUnmount(() => { generation++; clear() })
</script>
<style scoped>
.preview-tools { display:flex; align-items:center; gap:10px; margin-bottom:16px; }.preview-tools > span:first-child { flex:1; color:#788493; font-size:13px; }
.file-stage { min-height:400px; padding:22px; background:#eef1f5; overflow:auto; }.file-pages { margin:auto; min-width:240px; }.file-page { margin-bottom:18px; background:#fff; box-shadow:0 2px 10px #dce1e9; }.file-page img { display:block; width:100%; }.file-page span { display:block; text-align:center; padding:8px; color:#8993a0; font-size:12px; }
</style>
