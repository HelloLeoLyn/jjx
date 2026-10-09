<template>
  <ProductSpecPreview
    :product-id="productId"
    :initial-sections="initialSections"
    :initial-files="initialFiles"
    :auto-print="route.query.print === '1'"
    standalone
  />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import ProductSpecPreview from './components/ProductSpecPreview.vue'
import { documentSections } from './components/docset'

const route = useRoute()
const productId = Number(route.params.productId)
const initialSections = route.query.sections == null
  ? documentSections.map((section) => section.key)
  : String(route.query.sections).split(',').filter(Boolean)
const initialFiles = route.query.files == null ? undefined : String(route.query.files).split(',').filter(Boolean)
</script>
