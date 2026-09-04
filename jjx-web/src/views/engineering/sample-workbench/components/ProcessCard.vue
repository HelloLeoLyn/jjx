<template>
  <div
    class="plan-card"
    :class="{ 'drag-over': pc.draggingOver, 'batch-selected': batchSelected }"
    @dragover.prevent="onCardDragOver(pc)"
    @dragleave="onCardDragLeave(pc)"
    @drop.stop="onCardDrop($event, pc)"
  >
    <!-- 行1：序号 + 状态 + 操作 -->
    <div class="pc-head">
      <el-checkbox
        v-if="batchMode"
        :model-value="batchSelected"
        @change="(v: boolean | string | number) => $emit('toggle-select', !!v)"
        class="batch-check"
      />
      <span class="pc-num">{{ index + 1 }}</span>
      <span class="save-state" :class="`save-${pc.saveState || 'synced'}`">{{
        saveStateText(pc)
      }}</span>
      <div class="pc-head-right">
        <el-tag v-if="pc.status === 2" size="small" type="success">✓ 已完成</el-tag>
        <el-tag v-else-if="pc.status === 1" size="small" type="warning">⏳ 进行中</el-tag>
        <el-tag v-else size="small" type="info">待做</el-tag>
        <span v-if="pc.status === 2 && pc.durationMinutes" style="color: #909399; font-size: 12px"
          >⏱ {{ pc.durationMinutes }}分钟</span
        >
      </div>
    </div>
    <!-- 行2：标准工序（组合，任意结构） -->
    <div class="pc-row">
      <div class="pc-row-label">标准工序</div>
      <div class="pc-items">
        <el-tag
          v-for="(it, ii) in pc.items"
          :key="ii"
          size="small"
          :closable="!readonly && pc.editing"
          :disable-transitions="false"
          @close="$emit('remove-item', Number(ii))"
          style="margin-right: 6px; margin-bottom: 4px"
        >
          <IconStepBadge
            v-if="it.hasIndex === 1"
            :icon="it.icon || ''"
            :size="16"
            :index="it.indexNumber ?? null"
            @update:index="(n: number) => $emit('update-index', it, n)"
          />
          <template v-else>
            <SvgIcon
              v-if="it.icon"
              :name="it.icon"
              :size="14"
              style="vertical-align: -2px; margin-right: 4px"
            />
            {{ it.processName }}
          </template>
        </el-tag>
        <el-button
          v-if="!readonly && !pc.editing"
          size="small"
          link
          type="primary"
          @click="$emit('open-picker')"
          >＋ 添加标准工序</el-button
        >
        <span v-if="!pc.items.length && !pc.editing" style="color: #c0c4cc; font-size: 12px"
          >未选择标准工序</span
        >
      </div>
    </div>
    <!-- 行3：材料表格 -->
    <div class="pc-row">
      <div class="pc-row-label">🧾 材料</div>
      <div class="pc-mat">
        <el-table
          v-if="(pc.editing ? pc.materialRows : parseMaterials(pc.materials)).length"
          :data="pc.editing ? pc.materialRows : parseMaterials(pc.materials)"
          size="small"
          border
          style="width: 100%"
        >
          <el-table-column label="材料" min-width="150">
            <template #default="{ row }">
              <template v-if="pc.editing">
                <el-select
                  v-model="row.materialId"
                  filterable
                  remote
                  :remote-method="(q: string) => $emit('search-material', q, row)"
                  :loading="row.loading"
                  :popper-class="`material-popper-${row.uid}`"
                  placeholder="搜索物料档案"
                  style="width: 100%"
                  @change="(v: any) => $emit('material-selected', row, v)"
                  @visible-change="(v: boolean) => $emit('select-visible', row, v)"
                >
                  <el-option
                    v-for="opt in row.options"
                    :key="opt.materialId"
                    :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
                    :value="opt.materialId"
                  />
                  <el-option
                    v-if="row.loading"
                    value="__loading__"
                    disabled
                    style="text-align: center; color: #909399; font-size: 12px"
                    >加载中…</el-option
                  >
                  <el-option
                    v-else-if="row.options.length && row.total > row.options.length"
                    value="__more__"
                    disabled
                    style="text-align: center; color: #909399; font-size: 12px"
                    >下拉加载更多（还有 {{ row.total - row.options.length }} 条）</el-option
                  >
                  <el-option
                    v-else-if="row.options.length"
                    value="__all__"
                    disabled
                    style="text-align: center; color: #c0c4cc; font-size: 12px"
                    >已加载全部（共 {{ row.total }} 条）</el-option
                  >
                </el-select>
              </template>
              <template v-else>{{ row.name }}</template>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="110">
            <template #default="{ row }">
              <el-input
                v-if="pc.editing"
                v-model="row.spec"
                size="small"
                placeholder="规格"
                :disabled="!!row.materialId"
              />
              <span v-else>{{ row.spec || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用量" width="90">
            <template #default="{ row }">
              <el-input-number
                v-if="pc.editing"
                v-model="row.qty"
                :min="0"
                :precision="4"
                :controls="false"
                size="small"
                style="width: 100%"
              />
              <span v-else>{{ row.qty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">
              <el-input
                v-if="pc.editing"
                v-model="row.unit"
                size="small"
                :disabled="!!row.materialId"
              />
              <span v-else>{{ row.unit || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!readonly && pc.editing" label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button size="small" link type="danger" @click="pc.materialRows.splice($index, 1)"
                >删</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!readonly && pc.editing" style="margin-top: 6px; display: flex; gap: 6px">
          <el-button size="small" plain icon="Plus" @click="$emit('add-material-row')"
            >添加材料</el-button
          >
          <el-button size="small" link type="primary" @click="$emit('create-material', null)"
            >新建物料</el-button
          >
        </div>
        <span
          v-else-if="!parseMaterials(pc.materials).length"
          style="color: #c0c4cc; font-size: 12px"
          >无材料</span
        >
      </div>
    </div>
    <!-- 行4：描述 -->
    <div class="pc-row">
      <div class="pc-row-label">📝 描述</div>
      <el-input
        v-if="!readonly && pc.editing"
        v-model="pc.processNote"
        type="textarea"
        :rows="2"
        placeholder="如：丝印机200目网版，刮刀压力3kg，室温干燥30分钟"
      />
      <div v-else class="pc-desc-readonly">{{ pc.processNote || '—' }}</div>
    </div>
    <!-- 右下角：删除/保存/编辑 -->
    <div class="pc-footer">
      <el-button v-if="!readonly && !pc.editing" size="small" @click="$emit('edit')"
        >✏️ 编辑</el-button
      >
      <template v-else-if="!readonly">
        <el-button size="small" @click="$emit('cancel-edit')">取消</el-button>
        <el-button size="small" type="danger" plain @click="$emit('delete')">🗑 删除</el-button>
        <el-button size="small" type="primary" :loading="pc.savingCard" @click="$emit('save')"
          >💾 保存</el-button
        >
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

/**
 * 工序卡片（dev-20260811-008 组件化核心）
 * 自包含：状态/标准工序/材料/描述/编辑操作
 * 预留：卡片类型字段（标准/自定义），印刷工序后续扩展
 */
defineProps<{
  pc: any
  index: number
  batchMode: boolean
  batchSelected: boolean
  saveStateText: (pc: any) => string
  parseMaterials: (json?: string | null) => any[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-select', v: boolean): void
  (e: 'remove-item', idx: number): void
  (e: 'update-index', item: any, n: number): void
  (e: 'open-picker'): void
  (e: 'search-material', q: string, row: any): void
  (e: 'material-selected', row: any, v: any): void
  (e: 'select-visible', row: any, v: boolean): void
  (e: 'add-material-row'): void
  (e: 'create-material', m: any): void
  (e: 'edit'): void
  (e: 'cancel-edit'): void
  (e: 'delete'): void
  (e: 'save'): void
  (e: 'card-dragover', pc: any): void
  (e: 'card-dragleave', pc: any): void
  (e: 'card-drop', ev: DragEvent, pc: any): void
}>()

function onCardDragOver(pc: any) {
  emit('card-dragover', pc)
}
function onCardDragLeave(pc: any) {
  emit('card-dragleave', pc)
}
function onCardDrop(e: DragEvent, pc: any) {
  emit('card-drop', e, pc)
}
</script>

<style scoped>
.plan-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(31, 45, 61, 0.05);
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}
.plan-card.drag-over {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
  background: #f5f9ff;
}
.plan-card.batch-selected {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25);
}
.plan-card:hover {
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.1);
  border-color: #c6d9f5;
}

.pc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.pc-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.pc-head-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.batch-check {
  margin-right: 2px;
}
.save-state {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.save-synced {
  color: #67c23a;
}
.save-dirty {
  color: #909399;
}
.save-saving {
  color: #409eff;
}
.save-error {
  color: #f56c6c;
}

.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.pc-row-label {
  font-size: 12px;
  color: #909399;
  width: 72px;
  flex-shrink: 0;
  line-height: 26px;
}
.pc-items {
  flex: 1;
  min-width: 0;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
.pc-desc-readonly {
  flex: 1;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
  background: #fafbfc;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 26px;
  white-space: pre-wrap;
}
.pc-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 8px;
}
</style>
