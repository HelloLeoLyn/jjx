<template>
  <article class="ink-paper">
    <header>
      <div>精捷信塑胶五金电子制品厂</div>
      <h1>油墨调配记录表</h1>
    </header>
    <table v-for="group in 5" :key="group" class="ink-grid">
      <colgroup>
        <col style="width: 15%" /><col style="width: 20%" />
        <col style="width: 10%" /><col style="width: 10%" />
        <col style="width: 12%" /><col style="width: 11%" />
        <col style="width: 13%" /><col style="width: 9%" />
      </colgroup>
      <tbody>
        <tr class="machine-row">
          <th>机种</th><td colspan="4" class="machine">{{ productCode }}</td>
          <th>材料</th><td colspan="2"></td>
        </tr>
        <tr class="labels">
          <th>色号及色样</th><th>原料名称</th><th>重量(g)</th><th>百分比例</th>
          <th>油墨罐号</th><th>调墨员</th><th>调墨日期</th><th>溶剂</th>
        </tr>
        <tr v-for="row in 6" :key="row" class="material-row">
          <td v-if="row === 1" rowspan="2" class="color-label">色号</td>
          <td v-if="row === 3" rowspan="4" class="color-label">色样</td>
          <td></td><td></td><td></td>
          <td v-if="row === 1" rowspan="6"></td>
          <td v-if="row === 1" rowspan="6"></td>
          <td v-if="row === 1" rowspan="6"></td>
          <td v-if="row === 1" rowspan="6"></td>
        </tr>
      </tbody>
    </table>
  </article>
</template>

<script setup lang="ts">
defineProps<{ productCode?: string }>()
</script>

<style scoped>
.ink-paper { height: 278mm; box-sizing: border-box; display: flex; flex-direction: column; color: #222; font-family: SimSun, 'Songti SC', serif; border: 1px solid #222; }
header { height: 23mm; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 21px; letter-spacing: 2px; }
h1 { margin: 0; font-size: 23px; font-weight: 400; line-height: 1.4; letter-spacing: 5px; }
.ink-grid { width: 100%; height: 51mm; flex-shrink: 0; table-layout: fixed; border-collapse: collapse; font-size: 13px; }
th, td { border: 1px solid #222; padding: 0 3px; box-sizing: border-box; font-weight: 400; line-height: 1.2; }
th { text-align: center; white-space: nowrap; }
.machine-row, .labels { height: 5.5mm; }
.material-row { height: calc(40mm / 6); }
.machine { overflow-wrap: anywhere; font-size: 12px; }
.color-label { text-align: left; padding-left: 6px; vertical-align: middle; }
@media print { .ink-paper { break-inside: avoid; } }
</style>
