<template>
  <div class="process-material-group">
    <header class="group-header">
      <el-checkbox
        v-if="batchMode"
        :model-value="batchSelected"
        @change="(value: boolean | string | number) => $emit('toggle-select', !!value)"
      />
      <span class="group-number">{{ (index ?? 0) + 1 }}</span>
      <span v-if="saveStateText" class="save-state" :class="`save-${pc.saveState || 'synced'}`">{{
        saveStateText(pc)
      }}</span>
      <div class="combined-processes">
        <template v-for="(item, index) in pc.items" :key="item.itemKey">
          <span v-if="index" class="combine-plus">＋</span>
          <div class="combined-process">
            <IconStepBadge
              v-if="item.icon"
              :icon="item.icon"
              :size="28"
              :work-instruction="item.workInstruction"
              :editable="false"
            />
            <span>{{ item.processName }}</span>
          </div>
        </template>
        <el-button v-if="editable" link type="primary" size="small" @click="$emit('open-picker')"
          >＋ 工序</el-button
        >
        <span v-if="!pc.items.length" class="muted">未选择标准工序</span>
      </div>
      <label class="group-note"
        ><span>工序备注</span>
        <el-input
          v-if="editable"
          v-model="pc.operationRemark"
          size="small"
          placeholder="填写整道工序说明"
          :aria-label="`${pc.uid}工序备注`"
        />
        <span v-else class="note-value">{{ pc.operationRemark || '—' }}</span>
      </label>
      <div v-if="!readonly" class="group-actions">
        <el-button v-if="!pc.editing" size="small" link type="primary" @click="$emit('edit')"
          >编辑</el-button
        >
        <el-dropdown trigger="click" @command="$emit('delete')">
          <el-button size="small" link aria-label="更多操作">更多 ⋯</el-button>
          <template #dropdown
            ><el-dropdown-menu
              ><el-dropdown-item command="delete">删除卡片</el-dropdown-item></el-dropdown-menu
            ></template
          >
        </el-dropdown>
      </div>
    </header>
    <section
      v-for="(item, index) in pc.items"
      :key="item.itemKey"
      class="child-process"
      :data-process="item.itemKey"
    >
      <div class="child-heading">
        <div class="child-name">
          <IconStepBadge
            v-if="item.icon"
            :icon="item.icon"
            :size="30"
            :work-instruction="item.workInstruction"
            :editable="false"
          />
          <b>{{ item.processName }}</b>
        </div>
        <label class="child-field"
          ><span>作业说明</span>
          <el-input
            v-if="editable"
            v-model="item.workInstruction"
            size="small"
            maxlength="500"
            placeholder="填写作业说明"
            :aria-label="`${item.itemKey}作业说明`"
          />
          <span v-else class="note-value">{{ item.workInstruction || '—' }}</span>
        </label>
        <label class="child-field"
          ><span>子工序备注</span>
          <el-input
            v-if="editable"
            v-model="item.subProcessRemark"
            size="small"
            maxlength="500"
            placeholder="填写子工序说明"
            :aria-label="`${item.itemKey}子工序备注`"
          />
          <span v-else class="note-value">{{ item.subProcessRemark || '—' }}</span>
        </label>
        <el-button
          v-if="editable"
          link
          size="small"
          type="danger"
          :aria-label="`移除${item.processName}`"
          @click="$emit('remove-item', Number(index))"
          >移除子工序</el-button
        >
      </div>
      <ProcessMaterialEditor
        :pc="pc"
        :readonly="readonly"
        :rows="rowsFor(item)"
        :parse-materials="parseMaterials"
        :add-label="`为${item.processName}添加材料`"
        @material-selected="(row, value) => $emit('material-selected', row, value)"
        @material-cleared="(row) => $emit('material-cleared', row)"
        @add-material-row="$emit('add-material-row', item.itemKey)"
        @remove-material="(index) => removeMaterial(rowsFor(item)[index])"
        @create-material="(row) => $emit('create-material', row)"
      />
    </section>
    <section v-if="unassigned.length" class="unassigned-materials">
      <b>待分配材料（{{ unassigned.length }}）</b>
      <p class="muted">请选择材料所属的子工序，分配后会显示在对应材料表中。</p>
      <div v-for="(row, index) in unassigned" :key="row.uid || index" class="unassigned-row">
        <span>{{ row.name || '未选择材料' }} {{ row.spec }}</span>
        <el-select
          v-if="editable"
          placeholder="分配到子工序"
          :aria-label="`分配${row.name || '材料'}`"
          @change="(key) => assignTo(row, key)"
        >
          <el-option
            v-for="item in pc.items"
            :key="item.itemKey"
            :label="item.processName"
            :value="item.itemKey"
          />
        </el-select>
        <el-button v-if="editable" size="small" link type="danger" @click="removeMaterial(row)"
          >删除</el-button
        >
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import ProcessMaterialEditor from './ProcessMaterialEditor.vue'
import { assignMaterial, materialOwner } from '../composables/sampleProcessGroups'
const props = defineProps<{
  pc: any
  index?: number
  batchMode?: boolean
  batchSelected?: boolean
  saveStateText?: (pc: any) => string
  readonly?: boolean
  parseMaterials: (json?: string | null) => any[]
}>()
const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'delete'): void
  (e: 'toggle-select', selected: boolean): void
  (e: 'remove-item', index: number): void
  (e: 'open-picker'): void
  (e: 'material-selected', row: any, value: any): void
  (e: 'material-cleared', row: any): void
  (e: 'add-material-row', itemKey: string): void
  (e: 'create-material', row: any): void
}>()
const editable = computed(() => !!props.pc.editing && !props.readonly)
const rows = computed<any[]>(() =>
  props.pc.editing ? props.pc.materialRows : props.parseMaterials(props.pc.materials)
)
const unassigned = computed(() => rows.value.filter((row) => !materialOwner(props.pc, row)))
function rowsFor(item: any) {
  return rows.value.filter((row) => materialOwner(props.pc, row) === item)
}
function removeMaterial(row: any) {
  if (!editable.value) return
  const index = props.pc.materialRows.indexOf(row)
  if (index >= 0) props.pc.materialRows.splice(index, 1)
}
function assignTo(row: any, key: string) {
  if (!editable.value) return
  const item = props.pc.items.find((item: any) => item.itemKey === key)
  if (item) assignMaterial(row, item)
}
</script>
<style scoped>
.save-state.save-synced {
  color: #67c23a;
}
.save-state.save-dirty {
  color: #909399;
}
.save-state.save-saving {
  color: #409eff;
}
.save-state.save-error {
  color: #f56c6c;
}
.group-number {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 5px;
  background: #eaf0f8;
  color: #6482aa;
  font-size: 12px;
  font-weight: 600;
}
.save-state {
  font-size: 12px;
  color: #909399;
}
.group-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}
.group-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 6px 0 14px;
}
.combined-processes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.combined-process {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #475c76;
  padding: 4px 0 10px;
}
.combine-plus {
  color: #9cadc2;
}
.group-note {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 200px;
}
.group-note > span:first-child,
.child-field > span:first-child {
  font-size: 12px;
  color: #8998ac;
  white-space: nowrap;
}
.child-process {
  border-top: 1px solid #e7ecf3;
  padding-top: 12px;
}
.child-process + .child-process {
  margin-top: 12px;
}
.child-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 12px;
}
.child-name {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 130px;
  font-size: 12px;
  color: #536983;
  padding-bottom: 14px;
}
.child-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 200px;
}
.note-value {
  color: #606266;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.muted {
  font-size: 12px;
  color: #909399;
}
.unassigned-materials {
  border: 1px solid #f0d9ac;
  border-radius: 6px;
  padding: 12px;
  margin-top: 12px;
  font-size: 12px;
}
.unassigned-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
.unassigned-row > span {
  flex: 1;
  min-width: 100px;
}
.unassigned-row :deep(.el-select) {
  width: 180px;
}
.child-process :deep(.el-table) {
  font-size: 12px;
}
</style>
