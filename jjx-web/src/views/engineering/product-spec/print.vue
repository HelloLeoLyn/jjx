<template>
  <ProductSpecPreview
    :product-id="productId"
    :version-id="versionId"
    :product-code="productCode"
    :product-name="productName"
    :initial-sections="initialSections"
    :initial-files="initialFiles"
    :initial-font-settings="initialFontSettings"
    :initial-cell-font-settings="route.query.cellFonts == null ? undefined : String(route.query.cellFonts)"
    :initial-template="initialWorkSpecTemplate(route.query.template)"
    :auto-print="route.query.print === '1'"
    standalone
  />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import ProductSpecPreview from './components/ProductSpecPreview.vue'
import { documentSections } from './components/docset'
import { initialWorkSpecTemplate } from './components/workSpecFont'

const route = useRoute()
const productId = Number(route.params.productId)
const versionId = route.query.versionId ? Number(route.query.versionId) : undefined
const productCode = route.query.productCode ? String(route.query.productCode) : ''
const productName = route.query.productName ? String(route.query.productName) : ''
const initialSections = route.query.sections == null
  ? documentSections.map((section) => section.key)
  : String(route.query.sections).split(',').filter(Boolean)
const initialFiles = route.query.files == null ? undefined : String(route.query.files).split(',').filter(Boolean)
const initialFontSettings = route.query.fonts == null ? undefined : String(route.query.fonts)
</script>
