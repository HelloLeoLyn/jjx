<template>
  <div class="material-dictionary-page">
    <el-tabs v-model="activeCode">
      <el-tab-pane v-for="dict in dictionaries" :key="dict.code" :name="dict.code" :label="dict.name" />
    </el-tabs>
    <DictItemPanel :dict-code="activeCode" :api="itemApi" :permissions="permissions" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { materialMasterApi, type MaterialDictionaryCode } from '@/api/inventory/materialMaster'
import type { SysDictItemDTO } from '@/types/system/dict'
import DictItemPanel from '@/views/system/dict/components/DictItemPanel.vue'

defineOptions({ name: 'MaterialDictionary' })

const dictionaries: { code: MaterialDictionaryCode; name: string }[] = [
  { code: 'brand', name: '品牌' },
  { code: 'color', name: '颜色' },
  { code: 'form', name: '形态' },
  { code: 'unit', name: '单位' },
]
const activeCode = ref<MaterialDictionaryCode>('brand')
const permissions = {
  add: 'inventory:material:dict:edit',
  edit: 'inventory:material:dict:edit',
  delete: 'inventory:material:dict:edit',
}
const itemApi = {
  getItems: (code: string) => materialMasterApi.dictionaryItems(code as MaterialDictionaryCode),
  addItem: (item: SysDictItemDTO) =>
    materialMasterApi.mutateDictionaryItem(activeCode.value, { action: 'CREATE', item }),
  updateItem: (itemId: number, item: SysDictItemDTO) =>
    materialMasterApi.mutateDictionaryItem(activeCode.value, { action: 'UPDATE', item: { ...item, itemId } }),
  removeItem: (itemIds: number[]) =>
    materialMasterApi.mutateDictionaryItem(activeCode.value, { action: 'DELETE', itemId: itemIds[0]! }),
  changeItemStatus: (itemId: number, isActive: number) =>
    materialMasterApi.mutateDictionaryItem(activeCode.value, { action: 'STATUS', itemId, isActive }),
}
</script>

<style scoped>
.material-dictionary-page { padding: 20px; }
</style>
