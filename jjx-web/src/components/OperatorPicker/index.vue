<template>
  <el-dialog
    :model-value="visible"
    title="选择执行人"
    width="440px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
    @open="onOpen"
  >
    <el-input
      v-model="keyword"
      placeholder="搜索姓名/部门"
      clearable
      prefix-icon="Search"
      style="margin-bottom: 8px"
    />

    <el-tree
      ref="treeRef"
      :data="treeData"
      node-key="id"
      show-checkbox
      default-expand-all
      :props="{ label: 'label', children: 'children' }"
      :filter-node-method="filterNode"
      @check="onCheck"
      style="max-height: 320px; overflow: auto"
    />

    <div class="op-picker-count">
      已选 {{ selectedNames.length }} 人<template v-if="selectedNames.length">：{{ selectedNames.join('、') }}</template>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="confirm">确定（{{ selectedNames.length }}）</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface PickerUser {
  userId: number
  userName?: string
  nickName?: string
  deptId?: number
  deptName?: string
}

const props = withDefaults(
  defineProps<{
    visible: boolean
    /** 可选执行人（平铺用户列表，按 deptId 挂到部门树） */
    users: PickerUser[]
    /** 已选 userId 列表 */
    modelValue: number[]
    /** 部门树（可选）：按真实层级组织部门节点；不传则平铺分组 */
    deptTree?: any[]
    title?: string
  }>(),
  { title: '选择执行人', deptTree: () => [] },
)

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'update:modelValue', ids: number[]): void
  (e: 'confirm', ids: number[]): void
}>()

const treeRef = ref<any>()
const keyword = ref('')

// 按部门树层级组织：部门节点按管辖树结构，用户挂到所属部门下（2026-08-19：修复部门平铺成平级的怪树）
const treeData = computed(() => {
  // 用户按 deptId 分组
  const byDept = new Map<number, any[]>()
  for (const u of props.users || []) {
    const deptId = u.deptId ?? 0
    if (!byDept.has(deptId)) byDept.set(deptId, [])
    byDept.get(deptId)!.push({
      id: u.userId,
      label: u.nickName || u.userName || `用户${u.userId}`,
    })
  }
  const build = (nodes: any[]): any[] => {
    const out: any[] = []
    for (const n of nodes || []) {
      const deptId = n.id
      const node: any = { id: `d${deptId}`, label: n.deptName || `部门${deptId}`, children: [] }
      if (byDept.has(deptId)) node.children.push(...byDept.get(deptId)!)
      node.children.push(...build(n.children || []))
      // 没人也没下级 → 不显示空部门
      if (node.children.length) out.push(node)
    }
    return out
  }
  const tree = build(props.deptTree || [])
  if (tree.length) return tree
  // 兜底：无部门树时按 deptId 平铺分组
  return [...byDept].map(([deptId, users]) => ({
    id: `d${deptId}`,
    label: `部门${deptId}`,
    children: users,
  }))
})

const selectedNames = computed(() => {
  const names: string[] = []
  const walk = (nodes: any[]) => {
    for (const n of nodes || []) {
      if (n.children?.length) walk(n.children)
      else if (props.modelValue.includes(n.id)) names.push(n.label)
    }
  }
  walk(treeData.value)
  return names
})

// 打开时回显已选
const onOpen = () => {
  keyword.value = ''
  treeRef.value?.setCheckedKeys(props.modelValue)
}

// 勾选变化实时同步（叶子节点=用户）
const onCheck = () => {
  const keys = treeRef.value?.getCheckedKeys(true) || []
  emit('update:modelValue', keys.map(Number))
}

const filterNode = (value: string, data: any) => {
  if (!value) return true
  return data.label.includes(value)
}

watch(keyword, (v) => {
  treeRef.value?.filter(v)
})

const confirm = () => {
  const keys = treeRef.value?.getCheckedKeys(true) || []
  emit('confirm', keys.map(Number))
  emit('update:visible', false)
}
</script>

<style scoped>
.op-picker-count {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
