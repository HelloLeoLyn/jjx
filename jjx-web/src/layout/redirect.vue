<!-- src/layout/redirect.vue -->
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const { params, query } = route
const { path } = params

router.replace({ path: String(path), query })
</script>

<template>
  <div></div>
</template>
