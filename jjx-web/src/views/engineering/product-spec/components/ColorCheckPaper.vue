<template>
  <div class="color-paper">
    <section v-for="group in COLOR_CHECK_GROUPS" :key="group.key" class="color-block">
      <div class="block-title">{{ group.label }}</div>
      <table class="color-grid">
        <colgroup>
          <col style="width: 34%" />
          <col style="width: 12%" />
          <col style="width: 40%" />
          <col style="width: 14%" />
        </colgroup>
        <thead>
          <tr><th>检查项目</th><th>自查正确</th><th>自查错误（原因）</th><th>复查</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in group.items" :key="item.key">
            <td class="item-cell">{{ item.label }}</td>
            <td class="center">{{ correctMark(item.key) }}</td>
            <td class="reason-cell">{{ incorrectReason(item.key) }}</td>
            <td></td>
          </tr>
          <tr class="note-row">
            <td class="note-label">注意事项</td>
            <td colspan="3" class="note-cell">{{ group.note }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { DocsetData } from './docset'
import { COLOR_CHECK_GROUPS, ColorCheckResultEnum } from '@/enums/product/ColorCheckEnum'

const props = defineProps<{ data: DocsetData }>()

function itemOf(key: string) {
  return props.data.workSpec.colorCheck?.items?.[key]
}
/** 正确：在“自查正确”列打勾；不适用：明确打印“不适用”，与未检查区分。 */
function correctMark(key: string): string {
  const result = itemOf(key)?.result || ''
  if (result === ColorCheckResultEnum.CORRECT.value) return '√'
  if (result === ColorCheckResultEnum.NA.value) return '不适用'
  return ''
}
/** 错误：在“自查错误”列打印原因；未检查留空。 */
function incorrectReason(key: string): string {
  const item = itemOf(key)
  return item && item.result === ColorCheckResultEnum.INCORRECT.value ? item.reason || '' : ''
}
</script>

<style scoped>
.color-paper { display:flex; flex-direction:column; padding:10px 12px 0; font-family:'SimSun','Songti SC',serif; }
.color-block { margin-bottom:10px; }
.block-title { height:26px; line-height:26px; text-align:center; font-size:13px; border:1.2px solid #262626; border-bottom:0; background:#f4f4f4; }
.color-grid { width:100%; border-collapse:collapse; table-layout:fixed; font-size:12px; }
.color-grid th,.color-grid td { border:1px solid #343434; padding:2px 5px; line-height:1.35; overflow-wrap:anywhere; vertical-align:top; }
.color-grid th { height:24px; font-weight:500; font-size:12px; text-align:center; }
.color-grid td { height:22px; box-sizing:border-box; }
.center { text-align:center; }
.item-cell { font-size:12px; }
.reason-cell { font-size:11px; }
.note-row td { height:38px; }
.note-label { text-align:center; color:#555; }
.note-cell { font-size:11px; color:#333; }
</style>
