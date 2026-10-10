<template>
  <div ref="paper" class="print-spec-paper">
    <table v-for="group in page.groups" :key="group.label" class="print-grid">
      <colgroup>
        <col :style="{ width: page.showFilm ? '25%' : '29%' }" />
        <col :style="{ width: page.showFilm ? '13%' : '15%' }" />
        <col :style="{ width: page.showFilm ? '14%' : '17%' }" />
        <col v-if="page.showFilm" style="width:12%" />
        <col :style="{ width: page.showFilm ? '14%' : '17%' }" />
        <col style="width:22%" />
      </colgroup>
      <thead><tr><th>{{ group.symbol }} {{ group.label }}印序</th><th>色号</th><th>油墨编号</th><th v-if="page.showFilm">菲林</th><th>网框编号</th><th>备注</th></tr></thead>
      <tbody>
        <tr v-for="(row, i) in padded(group)" :key="i">
          <td v-for="field in columns" :key="field.key">
            <div class="cell-text" :data-overflow-title="`${group.label}印刷第${sequence(group, i)}道 · ${field.label}`">
              <span v-if="row && field.key === 'name'" class="print-number">{{ sequence(group, i) }}. </span>{{ value(row, field.key) }}
            </div>
          </td>
          <td v-if="i === 0" :rowspan="group.capacity" class="group-remark">
            <div class="remark-text" :style="{ height: `${(group.capacity || 1) * 26 - 8}px` }"
              :data-overflow-title="group.label + '印刷整组备注'" :data-overflow-repeat="Boolean(page.continuation)"
            >{{ group.remark }}</div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { printParams, type DocPage, type PaperTextLine } from './docset'
import { useWorkSpecOverflow } from './useWorkSpecOverflow'

const props = defineProps<{ page: DocPage }>()
const emit = defineEmits<{ overflow: [lines: PaperTextLine[]] }>()
const paper = ref<HTMLElement>()
type PrintGroup = NonNullable<DocPage['groups']>[number]
const columns = computed(() => [
  { key: 'name', label: '印刷名称' }, { key: 'colorNo', label: '色号' }, { key: 'inkNo', label: '油墨编号' },
  ...(props.page.showFilm ? [{ key: 'filmNo', label: '菲林' }] : []), { key: 'screenNo', label: '网框编号' },
])
function padded(group: PrintGroup): (Record<string, any> | null)[] {
  return Array.from({ length: group.capacity || 1 }, (_, i) => group.rows[i] || null)
}
function sequence(group: PrintGroup, index: number) { return (props.page.continuation || 0) * (group.capacity || 1) + index + 1 }
function value(row: Record<string, any> | null, key: string): string {
  if (!row) return ''
  const params = printParams(row)
  return String(key === 'name' ? (row.processName ?? params.printName ?? '') : (params[key] ?? ''))
}
const { measure } = useWorkSpecOverflow(paper, () => JSON.stringify(props.page), lines => emit('overflow', lines))
defineExpose({ measure })
</script>

<style scoped>
.print-grid { width:100%; table-layout:fixed; border-collapse:collapse; font-size:12px; }
.print-grid th { height:34px; font-size:14px; font-weight:500; padding:0 3px; border:1px solid #343434; }
.print-grid td { height:26px; padding:2px 4px; box-sizing:border-box; border:1px solid #343434; }
.print-grid + .print-grid { margin-top:-1px; }
.cell-text { height:21px; line-height:21px; overflow:hidden; white-space:pre-wrap; overflow-wrap:anywhere; position:relative; }
.print-number { font-size:10px; color:#666; }
.group-remark { vertical-align:top; }
.remark-text { line-height:20px; white-space:pre-wrap; overflow-wrap:anywhere; overflow:hidden; position:relative; }
[data-overflowing]::after { content:'续见附页'; position:absolute; bottom:0; right:0; background:#fff; padding-left:3px; color:#555; font-size:9px; line-height:12px; }
</style>
