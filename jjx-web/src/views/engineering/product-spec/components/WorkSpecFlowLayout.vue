<template>
  <div ref="host" class="flow-layout-source" aria-hidden="true">
    <WorkSpecPaper :data="data" :page="sourcePage" :page-index="0" :page-count="1"
      :font-settings="fontSettings" :structure-image="structureImage" :structure-caption="structureCaption" spec-template="B" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import WorkSpecPaper from './WorkSpecPaper.vue'
import { workSpecFlowSource, type DocsetData } from './docset'
import type { WorkSpecFontSettings } from './workSpecFont'
import { paginateWorkSpecFlow } from './workSpecFlowPagination'
const props = defineProps<{ data: DocsetData; fontSettings: WorkSpecFontSettings; structureImage?: string; structureCaption?: string }>()
const emit = defineEmits<{ layout: [pages: string[]]; error: [message: string] }>()
const host = ref<HTMLElement>()
const sourcePage = computed(() => workSpecFlowSource(props.data))
let generation = 0, disposed = false
let observer: ResizeObserver | undefined
async function measure() {
  const current = ++generation
  try {
    await nextTick(); await document.fonts.ready
    const source = host.value?.querySelector<HTMLElement>('.work-paper')
    if (!source?.clientWidth) return
    await Promise.all(Array.from(source.querySelectorAll('img'), image => image.decode()))
    if (disposed || current !== generation) return
    emit('layout', paginateWorkSpecFlow(source))
  } catch (error) {
    if (!disposed && current === generation) emit('error', error instanceof Error ? error.message : '作业规范分页失败')
  }
}
const schedule = () => { void measure() }
watch(() => [props.data, props.fontSettings, props.structureImage, props.structureCaption], schedule, { deep: true, flush: 'post' })
onMounted(() => {
  observer = new ResizeObserver(schedule)
  if (host.value) observer.observe(host.value)
  document.fonts.addEventListener('loadingdone', schedule)
  schedule()
})
onBeforeUnmount(() => { disposed = true; generation++; observer?.disconnect(); document.fonts.removeEventListener('loadingdone', schedule) })
defineExpose({ measure })
</script>

<style scoped>
.flow-layout-source { position:fixed; left:-100000px; top:0; width:192mm; visibility:hidden; pointer-events:none; }
@media print { .flow-layout-source { display:none; } }
</style>
