<template>
  <el-dialog
    v-model="visible"
    title="工艺路线详情"
    width="80%"
    @close="handleClose"
    @opened="handleOpened"
  >
    <RouteDetailView ref="detailViewRef" />

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import RouteDetailView from './RouteDetailView.vue'

const props = defineProps<{
  modelValue: boolean
  routingId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const detailViewRef = ref<InstanceType<typeof RouteDetailView>>()

const handleOpened = () => {
  if (props.routingId) {
    nextTick(() => {
      detailViewRef.value?.loadDetail(props.routingId!)
    })
  }
}

const handleClose = () => {
  detailViewRef.value?.resetDetail()
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
