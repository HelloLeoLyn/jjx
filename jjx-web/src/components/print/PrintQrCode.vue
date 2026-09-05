<template>
  <img
    v-if="dataUrl"
    :src="dataUrl"
    class="print-qrcode"
    :style="imageStyle"
    alt="二维码"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import QRCode from 'qrcode'

const props = withDefaults(
  defineProps<{
    text: string
    size?: number
  }>(),
  {
    size: 72,
  }
)

const dataUrl = ref('')
const imageStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))

watch(
  () => props.text,
  async (text, _previousText, onCleanup) => {
    let cancelled = false
    onCleanup(() => {
      cancelled = true
    })

    if (!text) {
      dataUrl.value = ''
      return
    }

    try {
      const url = await QRCode.toDataURL(text, { width: 256, margin: 1 })
      if (!cancelled) dataUrl.value = url
    } catch {
      if (!cancelled) dataUrl.value = ''
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.print-qrcode {
  position: absolute;
  top: 0;
  right: 0;
  padding: 3px;
  border: 1px solid #dcdfe6;
  background: #fff;
  box-sizing: border-box;
  object-fit: contain;
}
</style>
