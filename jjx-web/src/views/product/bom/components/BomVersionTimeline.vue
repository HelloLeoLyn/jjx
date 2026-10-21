<template>
  <div v-loading="loading" style="padding: 16px 32px">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-if="error" @click="load">重试</el-button>
    <el-empty v-else-if="!loading && !versions.length" description="暂无版本" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="version in versions"
        :key="version.bomId"
        :timestamp="parseTime(version.createTime)"
      >
        <el-button link type="primary" @click="$emit('view', version)">{{
          version.bomVersion
        }}</el-button>
        <el-tag :type="BomStatusEnum.getTagProps(version.approveStatus)?.type">{{
          BomStatusEnum.getLabel(version.approveStatus)
        }}</el-tag>
        <el-tag v-if="version.isCurrent" type="success">现行</el-tag>
        <p>
          生效：{{ parseDate(version.effectiveDate) || '—' }}　失效：{{
            parseDate(version.expiryDate) || '—'
          }}
        </p>
        <el-table :data="[version]" :show-header="false">
          <TableActionColumn
            :actions="actions"
            display="text"
            @action="(key: string, row: EngineeringBom) => $emit('action', key, row)"
          />
        </el-table>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { productBomApi } from '@/api/product/bom'
import { BomStatusEnum } from '@/enums/product'
import { parseTime, parseDate } from '@/utils/format'
import type { EngineeringBom } from '@/types/product/bom'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

const props = defineProps<{ bomCode: string; actions: TableAction<EngineeringBom>[] }>()
defineEmits<{ action: [key: string, row: EngineeringBom]; view: [row: EngineeringBom] }>()
const versions = ref<EngineeringBom[]>([])
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await productBomApi.getBomVersions(props.bomCode)
    versions.value = result.data || []
  } catch {
    error.value = '版本时间线加载失败'
  } finally {
    loading.value = false
  }
}
watch(() => props.bomCode, load, { immediate: true })
</script>
