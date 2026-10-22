<template>
  <div class="uic-editor" :class="{ 'library-hidden': !libraryVisible }">
    <aside v-if="libraryVisible" class="library">
      <div class="library-title">
        <b>标准工序</b
        ><button class="quiet-button" title="收起工序库" @click="libraryVisible = false">‹</button>
      </div>
      <div class="library-hint">选择工序，拖到右侧添加</div>
      <input
        v-model="search"
        class="search-input"
        placeholder="搜索标准工序…"
        aria-label="搜索标准工序"
      />
      <div class="library-items">
        <div
          v-for="item in filteredLibrary"
          :key="item.name"
          class="library-item"
          :class="{ chosen: selected.includes(item.name) }"
          draggable="true"
          @dragstart="dragStart($event, item.name)"
        >
          <input
            type="checkbox"
            v-model="selected"
            :value="item.name"
            :aria-label="`选择${item.name}`"
          />
          <ProcessIcon :icon="item.icon" :name="item.name" :size="29" />
          <div>
            <b>{{ item.name }}</b
            ><small>{{ item.category }}</small>
          </div>
          <button class="add-icon" :aria-label="`添加${item.name}`" @click="addGroup([item.name])">
            ＋
          </button>
        </div>
      </div>
      <el-button
        class="combine-button"
        type="primary"
        plain
        :disabled="!selected.length"
        @click="addGroup(selected)"
        >{{ selected.length > 1 ? `组合添加（${selected.length}）` : '添加所选工序' }}</el-button
      >
      <div class="library-tip">
        <span>组合操作</span>
        <p>勾选多道工序后一起添加，即可组成一个工序项目。</p>
        <p>材料归属具体子工序，组合自动汇总。</p>
      </div>
    </aside>
    <section class="plan-area" @dragover.prevent @drop.prevent="dropGroup">
      <div class="plan-heading">
        <div>
          <button v-if="!libraryVisible" class="text-button" @click="libraryVisible = true">
            ☰ 展开工序库</button
          ><b>工序与材料组合</b><span>{{ groups.length }} 个项目</span>
        </div>
        <div class="heading-actions">
          <button class="text-button" @click="toggleAll">
            {{ allCollapsed ? '全部展开' : '全部收起' }}</button
          ><span class="heading-divider"></span><span>按工序顺序排列</span>
        </div>
      </div>
      <article
        v-for="(group, groupIndex) in groups"
        :key="group.key"
        class="group-card"
        :data-group="group.key"
        @dragover.stop.prevent
        @drop.stop.prevent="dropProcess($event, group)"
      >
        <div class="group-header">
          <div class="group-title">
            <span class="group-number">{{ String(groupIndex + 1).padStart(2, '0') }}</span>
            <div class="combined-icons">
              <template v-for="(process, pi) in group.processes" :key="process.key"
                ><span v-if="pi" class="icon-plus">＋</span>
                <div class="group-icon">
                  <ProcessIcon :icon="process.icon" :name="process.name" :size="38" /><span>{{
                    process.name
                  }}</span>
                </div></template
              >
            </div>
            <span class="group-kind" :class="{ composite: group.processes.length > 1 }">{{
              group.processes.length > 1 ? '组合工序' : '单工序'
            }}</span
            ><small
              >{{ group.processes.length }} 道工序 · {{ group.materials.length }} 条材料</small
            >
          </div>
          <div class="group-actions">
            <button
              :disabled="groupIndex === 0"
              class="quiet-button"
              title="上移项目"
              @click="moveGroup(groupIndex, -1)"
            >
              ↑</button
            ><button
              :disabled="groupIndex === groups.length - 1"
              class="quiet-button"
              title="下移项目"
              @click="moveGroup(groupIndex, 1)"
            >
              ↓</button
            ><button class="quiet-button" title="删除项目" @click="removeGroup(group)">删除</button
            ><button
              class="quiet-button collapse-button"
              :aria-expanded="!collapsed.has(group.key)"
              @click="toggleGroup(group.key)"
            >
              {{ collapsed.has(group.key) ? '展开 ＋' : '收起 −' }}
            </button>
          </div>
          <div class="group-identity">
            <div class="summary-label">组合材料</div>
            <p class="material-summary">
              {{ group.materials.map(materialLabel).join(' ＋ ') || '暂无材料' }}
            </p>
            <template v-if="group.processes.length > 1"
              ><label class="summary-label" :for="`group-remark-${group.key}`"
                >组合整体备注 <small>预览项</small></label
              ><textarea
                :id="`group-remark-${group.key}`"
                v-model="group.remark"
                class="group-remark"
                placeholder="整个组合的补充说明"
                rows="1"
              />
            </template>
          </div>
        </div>
        <div v-if="!collapsed.has(group.key)" class="group-content">
          <div class="group-tables">
            <div class="table-scroll">
              <table class="process-table">
                <thead>
                  <tr>
                    <th class="process-col">工序子项目</th>
                    <th>材料摘要</th>
                    <th class="instruction-col">作业说明</th>
                    <th class="remark-col">工序备注</th>
                    <th class="operation-col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="process in group.processes"
                    :key="process.key"
                    :class="{ 'selected-process': filters[group.key] === process.key }"
                  >
                    <td>
                      <button
                        class="process-select"
                        :aria-label="`查看${process.name}材料`"
                        @click="filterProcess(group, process.key)"
                      >
                        <span class="badge-space"
                          ><ProcessIcon :icon="process.icon" :name="process.name" :size="30" /><span
                            v-if="process.instruction"
                            class="instruction-subscript"
                            :title="process.instruction"
                            >{{ process.instruction }}</span
                          ></span
                        ><span class="process-name"
                          >{{ process.name }}<small>{{ process.key.toUpperCase() }}</small></span
                        >
                      </button>
                    </td>
                    <td>
                      <div class="material-chips">
                        <span v-for="m in processMaterials(group, process.key)" :key="m.key">{{
                          materialLabel(m)
                        }}</span
                        ><span
                          v-if="!processMaterials(group, process.key).length"
                          class="empty-text"
                          >暂无材料</span
                        >
                      </div>
                    </td>
                    <td>
                      <input
                        v-model="process.instruction"
                        class="cell-input instruction-input"
                        maxlength="80"
                        :aria-label="`${process.name}作业说明`"
                        placeholder="填写作业说明"
                      />
                    </td>
                    <td>
                      <input
                        v-model="process.remark"
                        class="cell-input"
                        :aria-label="`${process.name}工序备注`"
                        placeholder="填写工序说明"
                      />
                    </td>
                    <td>
                      <div class="row-actions">
                        <button class="text-button" @click="openAddMaterial(group, process.key)">
                          ＋材料</button
                        ><button class="text-button subdued" @click="showProcess(process)">
                          详情</button
                        ><button
                          class="text-button danger"
                          title="删除子工序"
                          @click="removeProcess(group, process)"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="material-toolbar">
              <div class="material-tools">
                <select v-model="filters[group.key]" :aria-label="`${group.key}材料筛选`">
                  <option value="">全部子工序</option>
                  <option v-for="p in group.processes" :key="p.key" :value="p.key">
                    {{ p.name }}
                  </option></select
                ><button class="text-button" @click="openAddMaterial(group, filters[group.key])">
                  ＋ 添加材料
                </button>
              </div>
            </div>
            <div class="table-scroll">
              <table class="material-table">
                <thead>
                  <tr>
                    <th class="owner-col">所属子工序</th>
                    <th class="material-name-col">子材料名称</th>
                    <th class="spec-col">规格</th>
                    <th class="number-col">模数</th>
                    <th class="number-col">基数</th>
                    <th class="split-col">跳距</th>
                    <th class="operation-col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in visibleMaterials(group)" :key="m.key" :data-material="m.key">
                    <td>
                      <select
                        v-model="m.ownerKey"
                        class="owner-select"
                        :aria-label="`${m.key}所属子工序`"
                      >
                        <option v-for="p in group.processes" :key="p.key" :value="p.key">
                          {{ p.name }}
                        </option>
                      </select>
                    </td>
                    <td>
                      <div class="material-name">
                        <span class="material-dot" :class="{ unregistered: !m.registered }"></span
                        ><input
                          v-model="m.name"
                          class="cell-input"
                          :aria-label="`${m.key}材料名称`"
                        /><span v-if="!m.registered" class="pending-label">未建档</span>
                      </div>
                    </td>
                    <td>
                      <input
                        v-model="m.spec"
                        class="cell-input"
                        :aria-label="`${m.key}规格`"
                        placeholder="—"
                      />
                    </td>
                    <td>
                      <el-input-number
                        v-model="m.moduleQty"
                        :min="1"
                        :precision="0"
                        :controls="false"
                        size="small"
                        :aria-label="`${m.key}模数`"
                      />
                    </td>
                    <td>
                      <el-input-number
                        v-model="m.baseQty"
                        :min="1"
                        :precision="0"
                        :controls="false"
                        size="small"
                        :aria-label="`${m.key}基数`"
                      />
                    </td>
                    <td>
                      <div class="split-input">
                        <el-input-number
                          v-model="m.split"
                          :min="0"
                          :precision="0"
                          :controls="false"
                          size="small"
                          :aria-label="`${m.key}跳距`"
                          placeholder="—"
                        /><span>mm</span>
                      </div>
                    </td>
                    <td>
                      <div class="row-actions">
                        <button
                          class="text-button"
                          :disabled="m.registered"
                          @click="registerMaterial(m)"
                        >
                          {{ m.registered ? '已建档' : '建档' }}</button
                        ><button class="text-button subdued" @click="showMaterial(group, m)">
                          详情</button
                        ><button
                          class="text-button danger"
                          @click="
                            group.materials = group.materials.filter((row) => row.key !== m.key)
                          "
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!visibleMaterials(group).length">
                    <td colspan="7" class="empty-material">此处暂无材料，点击“添加材料”录入。</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="group-footnote">
              材料随所属子工序保存 · 参数可直接编辑 · 组合材料由明细自动汇总
            </div>
          </div>
        </div>
      </article>
      <div class="drop-zone">
        <span>＋</span><b>拖入工序，新建一个工序项目</b
        ><small>也可以拖入已有项目，追加为子工序</small>
      </div>
    </section>
    <el-dialog v-model="addVisible" title="添加材料" width="460px" append-to-body>
      <el-form label-position="top"
        ><el-form-item label="所属子工序"
          ><el-select v-model="newMaterial.ownerKey" style="width: 100%"
            ><el-option
              v-for="p in addTarget?.processes || []"
              :key="p.key"
              :label="p.name"
              :value="p.key" /></el-select></el-form-item
        ><el-form-item label="材料名称"
          ><el-input v-model="newMaterial.name" placeholder="如：离型膜" /></el-form-item
        ><el-form-item label="规格"
          ><el-input
            v-model="newMaterial.spec"
            placeholder="如：透明 / 30×12" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="addVisible = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!newMaterial.name.trim() || !newMaterial.ownerKey"
          @click="confirmAddMaterial"
          >添加到预览</el-button
        ></template
      >
    </el-dialog>
    <el-dialog v-model="detailVisible" :title="detailTitle" width="480px" append-to-body
      ><dl class="detail-list">
        <template v-for="item in detailItems" :key="item[0]"
          ><dt>{{ item[0] }}</dt>
          <dd>{{ item[1] || '—' }}</dd></template
        >
      </dl>
      <template #footer
        ><el-button @click="detailVisible = false">关闭</el-button></template
      ></el-dialog
    >
  </div>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProcessIcon from '@/components/ProcessIcon.vue'
import { createGroup, materialLabel, nextKey, processLibrary, processMaterials } from './model'
import type { UicGroup, UicMaterial, UicProcess } from './model'
const groups = defineModel<UicGroup[]>({ required: true })
const libraryVisible = ref(true)
const search = ref('')
const selected = ref<string[]>([])
const collapsed = reactive(new Set<string>(['led']))
const filters = reactive<Record<string, string>>({ single: '', composite: '', led: '' })
const filteredLibrary = computed(() =>
  processLibrary.filter((p) => p.name.includes(search.value.trim()))
)
const allCollapsed = computed(
  () => groups.value.length > 0 && groups.value.every((g) => collapsed.has(g.key))
)
function addGroup(names: string[]) {
  const group = createGroup([...names])
  groups.value.push(group)
  filters[group.key] = ''
  selected.value = []
  ElMessage.success('已添加工序项目')
}
function dragStart(event: DragEvent, name: string) {
  event.dataTransfer?.setData(
    'application/x-jjx-uic',
    JSON.stringify(selected.value.includes(name) ? selected.value : [name])
  )
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}
function droppedNames(event: DragEvent): string[] {
  try {
    const names = JSON.parse(event.dataTransfer?.getData('application/x-jjx-uic') || '[]')
    return Array.isArray(names) ? names.filter((n) => processLibrary.some((p) => p.name === n)) : []
  } catch {
    return []
  }
}
function dropGroup(event: DragEvent) {
  const names = droppedNames(event)
  if (names.length) addGroup(names)
}
function dropProcess(event: DragEvent, group: UicGroup) {
  const names = droppedNames(event)
  if (names.length) {
    group.processes.push(...createGroup(names).processes)
    collapsed.delete(group.key)
    selected.value = []
  }
}
function toggleGroup(key: string) {
  collapsed.has(key) ? collapsed.delete(key) : collapsed.add(key)
}
function toggleAll() {
  if (allCollapsed.value) collapsed.clear()
  else groups.value.forEach((g) => collapsed.add(g.key))
}
function moveGroup(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= groups.value.length) return
  const [group] = groups.value.splice(index, 1)
  groups.value.splice(target, 0, group)
}
function filterProcess(group: UicGroup, key: string) {
  filters[group.key] = filters[group.key] === key ? '' : key
}
function visibleMaterials(group: UicGroup) {
  return group.materials.filter((m) => !filters[group.key] || m.ownerKey === filters[group.key])
}
async function removeGroup(group: UicGroup) {
  try {
    await ElMessageBox.confirm('从本页预览移除此工序项目及材料？', '删除预览项目', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    groups.value = groups.value.filter((g) => g.key !== group.key)
  } catch {
    /* 用户取消 */
  }
}
function removeProcess(group: UicGroup, process: UicProcess) {
  if (group.processes.length === 1) {
    removeGroup(group)
    return
  }
  if (processMaterials(group, process.key).length) {
    ElMessage.warning('请先在材料明细中调整材料归属，再删除此子工序')
    return
  }
  group.processes = group.processes.filter((p) => p.key !== process.key)
  if (filters[group.key] === process.key) filters[group.key] = ''
}
function registerMaterial(material: UicMaterial) {
  material.registered = true
  ElMessage.success('已模拟建档，仅更新本页预览标记')
}
const addVisible = ref(false)
const addTarget = ref<UicGroup | null>(null)
const newMaterial = reactive({ ownerKey: '', name: '', spec: '' })
function openAddMaterial(group: UicGroup, ownerKey?: string) {
  addTarget.value = group
  Object.assign(newMaterial, {
    ownerKey: ownerKey || (group.processes.length === 1 ? group.processes[0].key : ''),
    name: '',
    spec: '',
  })
  addVisible.value = true
}
function confirmAddMaterial() {
  if (
    !addTarget.value ||
    !newMaterial.name.trim() ||
    !addTarget.value.processes.some((p) => p.key === newMaterial.ownerKey)
  )
    return
  addTarget.value.materials.push({
    key: nextKey(),
    ownerKey: newMaterial.ownerKey,
    name: newMaterial.name.trim(),
    spec: newMaterial.spec.trim(),
    moduleQty: 1,
    baseQty: 1,
    split: null,
    registered: false,
  })
  addVisible.value = false
}
const detailVisible = ref(false)
const detailTitle = ref('')
const detailItems = ref<string[][]>([])
function showProcess(process: UicProcess) {
  detailTitle.value = '工序详情'
  detailItems.value = [
    ['工序', process.name],
    ['示例工序行标识', process.key],
    ['作业说明', process.instruction],
    ['工序备注', process.remark],
  ]
  detailVisible.value = true
}
function showMaterial(group: UicGroup, m: UicMaterial) {
  detailTitle.value = '材料详情'
  detailItems.value = [
    ['材料', m.name],
    ['所属子工序', group.processes.find((p) => p.key === m.ownerKey)?.name || ''],
    ['规格', m.spec],
    ['模数 / 基数', `${m.moduleQty} / ${m.baseQty}`],
    ['跳距', m.split ? `${m.split}mm` : ''],
    ['建档', m.registered ? '已建档（示例）' : '未建档（示例）'],
  ]
  detailVisible.value = true
}
</script>
<style scoped>
.uic-editor {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.uic-editor.library-hidden {
  grid-template-columns: minmax(0, 1fr);
}
.library {
  position: sticky;
  top: 16px;
  background: white;
  border: 1px solid #e0e6ef;
  border-radius: 8px;
  padding: 16px 12px;
}
.library-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
  font-size: 14px;
}
.library-hint {
  font-size: 11px;
  color: #9aa5b4;
  margin: 6px 4px 14px;
}
.search-input {
  width: 100%;
  border: 1px solid #e3e8f0;
  background: #fafbfd;
  border-radius: 5px;
  padding: 9px 10px;
  outline: 0;
  font-size: 12px;
}
.library-items {
  margin: 12px 0;
}
.library-item {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 12px 6px;
  border-radius: 6px;
  border: 1px solid transparent;
  cursor: grab;
  margin-bottom: 4px;
}
.library-item:hover,
.library-item.chosen {
  background: #f0f6ff;
  border-color: #dbe8fa;
}
.library-item input {
  margin: 0;
  accent-color: #2875d4;
}
.library-item b {
  font-size: 12px;
  font-weight: 500;
}
.library-item small {
  display: block;
  font-size: 10px;
  color: #9ba6b4;
  margin-top: 4px;
}
.add-icon {
  margin-left: auto;
  background: transparent;
  border: 0;
  color: #6f8ba9;
  font-size: 18px;
  padding: 2px;
}
.combine-button {
  width: 100%;
  font-size: 12px;
}
.library-tip {
  border-top: 1px solid #edf0f5;
  margin-top: 20px;
  padding: 17px 5px 0;
  color: #97a2b2;
  font-size: 11px;
  line-height: 1.8;
}
.library-tip span {
  font-weight: 600;
  color: #72839b;
}
.library-tip p {
  margin: 7px 0;
}
.plan-area {
  min-width: 0;
}
.plan-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 34px;
  margin-bottom: 12px;
}
.plan-heading b {
  font-size: 14px;
}
.plan-heading span {
  font-size: 11px;
  color: #91a0b2;
  margin-left: 12px;
}
.heading-actions {
  display: flex;
  align-items: center;
}
.heading-divider {
  height: 12px;
  width: 1px;
  background: #d7dfe8;
}
.group-card {
  background: white;
  border: 1px solid #dfe6ef;
  border-radius: 9px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 2px 4px #273e5d03;
}
.group-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  padding: 13px 16px;
  background: #fafbfd;
  border-bottom: 1px solid #e7ecf3;
  gap: 10px;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 11px;
  flex-wrap: wrap;
}
.group-title b {
  font-size: 13px;
  font-weight: 600;
}
.group-number {
  font-size: 12px;
  font-weight: 650;
  color: #6482aa;
  background: #eaf0f8;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 5px;
}
.group-kind {
  font-size: 10px;
  color: #8090a4;
  border: 1px solid #e0e6ee;
  padding: 2px 6px;
  border-radius: 4px;
  background: white;
}
.group-kind.composite {
  color: #347bc9;
  border-color: #cfe2f8;
  background: #eff6ff;
}
.group-title small {
  font-size: 11px;
  color: #9ba6b5;
}
.group-actions {
  display: flex;
  gap: 7px;
  flex-shrink: 0;
}
.quiet-button {
  border: 0;
  background: transparent;
  color: #8b99ac;
  padding: 4px;
  font-size: 11px;
}
.quiet-button:hover {
  color: #2875d4;
}
.quiet-button:disabled {
  opacity: 0.3;
}
.collapse-button {
  margin-left: 10px;
}
.group-content {
  min-width: 0;
}
.group-identity {
  flex-basis: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 6px 12px;
  padding-top: 10px;
  border-top: 1px solid #e8edf3;
}
.combined-icons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.group-icon {
  display: flex;
  align-items: center;
  gap: 7px;
}
.group-icon span {
  font-size: 13px;
  font-weight: 600;
  color: #536983;
}
.icon-plus {
  font-size: 14px;
  color: #9cadc2;
}
.summary-label {
  color: #97a5b7;
  font-size: 11px;
}
.summary-label small {
  font-size: 9px;
  color: #b3bdc9;
}
.material-summary {
  margin: 0;
  font-size: 11px;
  line-height: 1.9;
  color: #647a96;
  overflow-wrap: anywhere;
}
.group-remark {
  font-size: 11px;
  line-height: 1.7;
  width: 100%;
  resize: vertical;
  border: 1px solid #e6ecf3;
  border-radius: 4px;
  padding: 4px 7px;
  background: white;
  color: #718197;
  outline: 0;
}
.group-tables {
  min-width: 0;
}
.material-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 5px 16px;
  background: #f7fafc;
  border-top: 1px solid #e4ebf3;
  border-bottom: 1px solid #edf1f6;
}
.table-scroll {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  text-align: left;
}
th {
  font-size: 10px;
  font-weight: 500;
  color: #8998ac;
  background: #fbfcfe;
  padding: 10px 12px;
  border-bottom: 1px solid #edf1f6;
}
td {
  padding: 10px 12px;
  border-bottom: 1px solid #edf1f6;
  font-size: 12px;
  vertical-align: middle;
}
.process-table {
  min-width: 770px;
}
.process-table td {
  height: 78px;
}
.material-table {
  min-width: 770px;
}
.process-col {
  width: 185px;
}
.instruction-col {
  width: 144px;
}
.remark-col {
  width: 150px;
}
.operation-col {
  width: 130px;
}
.owner-col {
  width: 118px;
}
.material-name-col {
  width: 190px;
}
.spec-col {
  width: 100px;
}
.number-col {
  width: 62px;
}
.split-col {
  width: 100px;
}
.process-select {
  border: 0;
  background: transparent;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
  color: #344860;
  width: 100%;
}
.badge-space {
  position: relative;
  display: inline-flex;
  width: 44px;
  flex-shrink: 0;
  align-self: flex-start;
  margin-bottom: 17px;
}
.instruction-subscript {
  position: absolute;
  left: 24px;
  top: 25px;
  font-size: 9px;
  line-height: 13px;
  color: #406ca0;
  max-width: 90px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: #fff;
  padding-left: 2px;
  z-index: 1;
}
.process-name {
  font-size: 12px;
  margin-top: -8px;
}
.process-name small {
  display: block;
  font-size: 9px;
  color: #b1bac7;
  margin-top: 5px;
}
.material-chips {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}
.material-chips span {
  font-size: 10px;
  color: #76879c;
  background: #f2f5f9;
  border-radius: 3px;
  padding: 4px 6px;
  word-break: break-word;
}
.material-chips .empty-text {
  background: transparent;
  color: #bec6d1;
}
.selected-process {
  background: #f5f9ff;
}
.selected-process .instruction-subscript {
  background: #f5f9ff;
}
.cell-input {
  width: 100%;
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  padding: 7px 5px;
  color: #506680;
  font-size: 11px;
  outline: 0;
}
.cell-input:hover {
  border-color: #dbe4f0;
  background: white;
}
.cell-input:focus {
  border-color: #83b3ec;
  background: white;
  box-shadow: 0 0 0 2px #eaf3ff;
}
.cell-input::placeholder {
  color: #bcc5d1;
}
.instruction-input {
  color: #477bb6;
}
.row-actions {
  display: flex;
  gap: 9px;
  align-items: center;
  white-space: nowrap;
}
.text-button {
  background: transparent;
  border: 0;
  color: #377dc8;
  padding: 2px 0;
  font-size: 11px;
}
.text-button.subdued {
  color: #8293a9;
}
.text-button.danger {
  color: #a2acb8;
}
.text-button.danger:hover {
  color: #db6262;
}
.text-button:disabled {
  color: #bac4d1;
}
.material-tools {
  display: flex;
  gap: 15px;
  align-items: center;
}
.material-tools select {
  border: 1px solid #e0e8f0;
  border-radius: 4px;
  padding: 4px 6px;
  color: #73879f;
  font-size: 10px;
  background: white;
  max-width: 145px;
}
.owner-select {
  width: 100%;
  border: 1px solid #dbe7f4;
  border-radius: 4px;
  padding: 6px;
  color: #5e84ae;
  font-size: 10px;
  background: #f5f9fe;
  outline: 0;
}
.material-name {
  display: flex;
  align-items: center;
  gap: 3px;
}
.material-dot {
  width: 5px;
  height: 5px;
  flex-shrink: 0;
  background: #83af9b;
  border-radius: 50%;
}
.material-dot.unregistered {
  background: #e1b86b;
}
.pending-label {
  white-space: nowrap;
  color: #c6a05d;
  font-size: 9px;
}
.material-table :deep(.el-input-number) {
  width: 44px;
}
.material-table :deep(.el-input__wrapper) {
  padding: 0 3px;
  box-shadow: 0 0 0 1px #e7ecf2 inset;
}
.material-table :deep(.el-input__inner) {
  font-size: 11px;
  color: #647991;
}
.split-input {
  display: flex;
  align-items: center;
  gap: 4px;
}
.split-input span {
  color: #9daabd;
  font-size: 10px;
}
.split-input :deep(.el-input-number) {
  width: 53px;
}
.group-footnote {
  padding: 10px 16px;
  font-size: 10px;
  color: #a0adbd;
}
.empty-material {
  text-align: center;
  height: 65px;
  color: #a5b1c0;
}
.drop-zone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px dashed #cbd8e8;
  border-radius: 8px;
  padding: 22px;
  color: #8da3bf;
  background: #f7faff;
}
.drop-zone > span {
  font-size: 20px;
  font-weight: 300;
}
.drop-zone b {
  font-weight: 400;
  font-size: 12px;
}
.drop-zone small {
  font-size: 10px;
  color: #a6b3c5;
}
.detail-list {
  display: grid;
  grid-template-columns: 115px 1fr;
  gap: 18px;
  line-height: 1.6;
}
.detail-list dt {
  color: #95a3b5;
}
.detail-list dd {
  margin: 0;
  color: #475c76;
  overflow-wrap: anywhere;
}
@media (max-width: 1150px) {
  .uic-editor {
    grid-template-columns: 175px minmax(0, 1fr);
    gap: 12px;
  }
  .group-title small {
    display: none;
  }
  .group-actions {
    gap: 3px;
  }
  .group-header {
    padding: 10px;
  }
  .group-kind {
    display: none;
  }
}
@media (max-width: 760px) {
  .uic-editor {
    grid-template-columns: minmax(0, 1fr);
  }
  .library {
    position: static;
  }
  .library-items {
    display: flex;
    flex-wrap: wrap;
  }
  .library-item {
    width: 48%;
  }
  .library-tip {
    display: none;
  }
  .plan-heading .heading-actions > span {
    display: none;
  }
  .group-title {
    gap: 7px;
  }
  .drop-zone small {
    display: none;
  }
}
</style>
