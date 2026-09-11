<template>
  <el-table-column
    :label="label"
    :width="width"
    :min-width="width == null ? minWidth : undefined"
    :fixed="fixed"
    :align="align"
  >
    <template #default="{ row, $index }">
      <div class="table-action-column">
        <slot name="before" :row="row" :index="$index" :$index="$index" />

        <template
          v-for="action in getVisibleActions(row, $index).slice(0, maxVisible)"
          :key="action.key"
        >
          <el-tooltip
            :content="getTooltip(action, row, $index)"
            :disabled="!getTooltip(action, row, $index)"
            placement="top"
          >
            <el-button
              link
              size="small"
              :type="resolveValue(action.type, row, $index) || 'primary'"
              :icon="getActionIcon(action, row, $index)"
              :aria-label="resolveValue(action.label, row, $index)"
              :disabled="resolveValue(action.disabled, row, $index)"
              :loading="resolveValue(action.loading, row, $index)"
              @click="handleAction(action, row, $index)"
            >
              <template v-if="getDisplay(action) !== 'icon'">
                {{ resolveValue(action.label, row, $index) }}
              </template>
            </el-button>
          </el-tooltip>
        </template>

        <el-dropdown
          v-if="getVisibleActions(row, $index).length > maxVisible"
          trigger="click"
          @command="handleOverflowAction($event, row, $index)"
        >
          <el-button link size="small" type="primary" :icon="MoreFilled">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="action in getVisibleActions(row, $index).slice(maxVisible)"
                :key="action.key"
                :command="action.key"
                :disabled="resolveValue(action.disabled, row, $index)"
              >
                <el-icon v-if="resolveValue(action.icon, row, $index)">
                  <component :is="resolveValue(action.icon, row, $index)" />
                </el-icon>
                {{ resolveValue(action.label, row, $index) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <slot name="after" :row="row" :index="$index" :$index="$index" />

        <span v-if="!hasContent(row, $index)" class="table-action-column__empty">-</span>
      </div>
    </template>
  </el-table-column>
</template>

<script setup lang="ts" generic="Row extends Record<string, any>">
import { useSlots } from 'vue'
import { MoreFilled } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { hasPermi, hasRole } from '@/directives'
import type { TableAction, TableActionDisplay, TableActionValue } from './types'

defineOptions({ name: 'TableActionColumn' })

const props = withDefaults(
  defineProps<{
    actions?: TableAction<Row>[]
    label?: string
    width?: string | number
    minWidth?: string | number
    fixed?: boolean | 'left' | 'right'
    align?: 'left' | 'center' | 'right'
    maxVisible?: number
    display?: TableActionDisplay
  }>(),
  {
    label: '操作',
    actions: () => [],
    minWidth: 200,
    fixed: 'right',
    align: 'center',
    maxVisible: 3,
    display: 'text',
  }
)

const emit = defineEmits<{
  action: [key: string, row: Row, index: number]
}>()
const slots = useSlots()

function resolveValue<Value>(
  value: TableActionValue<Row, Value> | undefined,
  row: Row,
  index: number
): Value | undefined {
  return typeof value === 'function'
    ? (value as (context: { row: Row; index: number }) => Value)({ row, index })
    : value
}

function hasActionPermission(action: TableAction<Row>): boolean {
  const permissionAllowed = action.permission ? hasPermi(action.permission) : true
  const roleAllowed = action.role ? hasRole(action.role) : true
  return permissionAllowed && roleAllowed
}

function getDisplay(action: TableAction<Row>): TableActionDisplay {
  if (action.display) return action.display
  if (!action.icon) return 'text'
  return props.display
}

function getActionIcon(action: TableAction<Row>, row: Row, index: number) {
  return getDisplay(action) === 'text' ? undefined : resolveValue(action.icon, row, index)
}

function getTooltip(action: TableAction<Row>, row: Row, index: number): string | undefined {
  if (resolveValue(action.disabled, row, index)) {
    const reason = resolveValue(action.disabledReason, row, index)
    if (reason) return reason
  }

  const tooltip = resolveValue(action.tooltip, row, index)
  if (tooltip) return tooltip
  return getDisplay(action) === 'icon' ? resolveValue(action.label, row, index) : undefined
}

function getVisibleActions(row: Row, index: number): TableAction<Row>[] {
  return props.actions
    .filter(
      (action) => hasActionPermission(action) && resolveValue(action.visible, row, index) !== false
    )
    .slice()
    .sort((left, right) => (left.order ?? 0) - (right.order ?? 0))
}

function hasContent(row: Row, index: number): boolean {
  return getVisibleActions(row, index).length > 0 || !!slots.before || !!slots.after
}

async function handleAction(action: TableAction<Row>, row: Row, index: number) {
  if (resolveValue(action.disabled, row, index) || resolveValue(action.loading, row, index)) return

  const confirmMessage = resolveValue(action.confirm, row, index)
  if (confirmMessage) {
    try {
      await ElMessageBox.confirm(confirmMessage, '提示', { type: 'warning' })
    } catch {
      return
    }
  }

  emit('action', action.key, row, index)
}

function handleOverflowAction(key: string, row: Row, index: number) {
  const action = getVisibleActions(row, index).find((item) => item.key === key)
  if (action) void handleAction(action, row, index)
}
</script>

<style scoped>
.table-action-column {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  white-space: nowrap;
}

.table-action-column :deep(.el-button + .el-button) {
  margin-left: 0;
}

.table-action-column__empty {
  color: var(--el-text-color-placeholder);
}
</style>
