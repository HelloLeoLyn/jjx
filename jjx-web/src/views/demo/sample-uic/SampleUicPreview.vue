<template>
  <div class="preview-page">
    <header class="page-header">
      <div class="brand">
        <span class="brand-mark">J</span
        ><span>JJX 工程管理<span class="crumb"> / 打样工作台 / 界面预览</span></span>
      </div>
      <span class="preview-label">UIC · 独立交互预览</span>
    </header>
    <main>
      <div class="page-title">
        <div>
          <div class="eyebrow">SAMPLE WORKBENCH</div>
          <h1>工序与材料<span>方案编制</span></h1>
          <p>以工序组织方案，让每一项材料的归属清晰可见。</p>
        </div>
        <div class="title-actions">
          <el-button @click="reset">重置示例</el-button
          ><el-button type="primary" @click="save">保存预览草稿</el-button>
        </div>
      </div>
      <div class="sample-bar">
        <div>
          <span class="muted">示例产品</span><strong>薄膜开关面板</strong
          ><span class="sample-code">SAMPLE-DEMO</span>
        </div>
        <div class="sample-metrics">
          <span>Round 1</span><span>{{ groups.length }} 个工序项目</span
          ><span>{{ materialCount }} 条材料</span>
        </div>
      </div>
      <div class="demo-note">
        <span class="note-dot"></span>示例数据 · 可拖拽、编辑和调整材料归属，操作仅在本页生效。<span
          class="save-indicator"
          >{{ dirty ? '草稿有修改' : saved ? '本页草稿已保存' : '初始示例' }}</span
        >
      </div>
      <SampleUicEditor v-model="groups" />
      <div class="overview">
        <button class="overview-toggle" @click="showOverview = !showOverview">
          <span><b>全单 BOM 总览</b><small>跨工序核对材料与归属</small></span
          ><span>{{ materialCount }} 条材料　{{ showOverview ? '收起 −' : '展开 ＋' }}</span>
        </button>
        <div v-if="showOverview" class="overview-body">
          <table>
            <thead>
              <tr>
                <th>工序项目</th>
                <th>所属子工序</th>
                <th>材料</th>
                <th>规格</th>
                <th>模数 / 基数</th>
                <th>跳距</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(group, index) in groups" :key="group.key"
                ><tr v-for="m in group.materials" :key="m.key">
                  <td>
                    {{ String(index + 1).padStart(2, '0') }} ·
                    {{ group.processes.map((p) => p.name).join(' + ') }}
                  </td>
                  <td>{{ group.processes.find((p) => p.key === m.ownerKey)?.name }}</td>
                  <td>{{ m.name }}</td>
                  <td>{{ m.spec || '—' }}</td>
                  <td>{{ m.moduleQty }} / {{ m.baseQty }}</td>
                  <td>{{ m.split ? `${m.split}mm` : '—' }}</td>
                </tr></template
              >
            </tbody>
          </table>
        </div>
      </div>
      <footer>
        <span>预览内容：单工序 · 组合工序 · 子工序材料归属 · 作业说明 · 备注 · 跳距</span
        ><span>UIC / 01</span>
      </footer>
    </main>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SampleUicEditor from './SampleUicEditor.vue'
import { demoGroups } from './model'
const groups = ref(demoGroups())
const savedSnapshot = ref(JSON.stringify(groups.value))
const saved = ref(false)
const showOverview = ref(false)
const materialCount = computed(() => groups.value.reduce((sum, g) => sum + g.materials.length, 0))
const dirty = computed(() => JSON.stringify(groups.value) !== savedSnapshot.value)
function reset() {
  groups.value = demoGroups()
  savedSnapshot.value = JSON.stringify(groups.value)
  saved.value = false
  ElMessage.success('已恢复初始示例')
}
function save() {
  savedSnapshot.value = JSON.stringify(groups.value)
  saved.value = true
  ElMessage.success('预览草稿已保留在本页，刷新后恢复示例')
}
</script>
<style>
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  background: #f3f5f8;
  color: #263449;
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
}
button,
input,
select {
  font: inherit;
}
button {
  cursor: pointer;
}
button:disabled {
  cursor: not-allowed;
}
input:focus-visible,
button:focus-visible,
select:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}
.preview-page {
  --el-color-primary: #2875d4;
  --el-border-radius-base: 6px;
}
.page-header {
  height: 58px;
  background: #fff;
  border-bottom: 1px solid #e5e9ef;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 650;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: #256ed0;
  border-radius: 7px;
  color: white;
  font-size: 20px;
}
.crumb {
  color: #8993a4;
  font-weight: 400;
  font-size: 12px;
}
.preview-label {
  font-size: 11px;
  letter-spacing: 0.5px;
  background: #edf4fd;
  color: #3a72b7;
  padding: 6px 10px;
  border-radius: 4px;
}
main {
  max-width: 1760px;
  margin: auto;
  padding: 28px 32px 20px;
}
.page-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 700;
  color: #8b99ac;
  margin-bottom: 7px;
}
h1 {
  font-size: 25px;
  letter-spacing: 0.5px;
  margin: 0;
  font-weight: 650;
}
h1 span {
  font-size: 12px;
  vertical-align: middle;
  color: #71839b;
  background: #e8edf4;
  border-radius: 4px;
  margin-left: 14px;
  padding: 4px 8px;
  font-weight: 400;
}
.page-title p {
  margin: 9px 0 0;
  color: #8590a1;
  font-size: 12px;
}
.sample-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 18px;
  background: #fff;
  border: 1px solid #e3e8ef;
  border-radius: 8px;
}
.sample-bar strong {
  margin: 0 14px;
  font-weight: 600;
}
.muted,
.sample-code {
  color: #8b96a6;
  font-size: 12px;
}
.sample-metrics {
  display: flex;
  gap: 24px;
  color: #64758b;
  font-size: 12px;
}
.demo-note {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #7c889b;
  font-size: 12px;
  margin: 14px 0 18px;
}
.note-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6191cf;
}
.save-indicator {
  margin-left: auto;
  color: #778ba6;
}
.overview {
  border: 1px solid #e0e6ee;
  border-radius: 8px;
  background: white;
  margin-top: 20px;
  overflow: hidden;
}
.overview-toggle {
  background: white;
  border: 0;
  padding: 18px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #5e718b;
}
.overview-toggle b {
  font-weight: 600;
  color: #344760;
}
.overview-toggle small {
  margin-left: 16px;
  color: #94a0b0;
}
.overview-body {
  padding: 0 18px 15px;
  overflow: auto;
}
.overview table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
}
.overview th,
.overview td {
  padding: 12px;
  border-bottom: 1px solid #edf0f5;
  text-align: left;
  font-size: 12px;
}
.overview th {
  background: #f7f9fc;
  color: #7f8da0;
  font-weight: 500;
}
footer {
  display: flex;
  justify-content: space-between;
  color: #9ba6b5;
  font-size: 11px;
  padding-top: 20px;
}
@media (max-width: 900px) {
  main {
    padding: 18px 12px;
  }
  .page-header {
    padding: 0 14px;
  }
  .crumb {
    display: none;
  }
  .sample-bar {
    align-items: flex-start;
    gap: 15px;
    flex-direction: column;
  }
  .sample-metrics {
    gap: 15px;
  }
  .page-title {
    gap: 15px;
  }
  .page-title h1 {
    font-size: 21px;
  }
  .title-actions {
    display: flex;
  }
  .title-actions .el-button {
    padding: 8px;
  }
  .demo-note {
    line-height: 1.7;
  }
  .overview-toggle small {
    display: none;
  }
}
</style>
