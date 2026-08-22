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
// 2026-08-21（TT-FINAL-02）：所有部门节点标签必须来自真实 sys_dept.dept_name；
// 禁止使用 deptId 拼接伪部门名称（部门{id}）；无部门人员归入「未设置部门」。
const treeData = computed(() => {
  // 用户按 deptId 分组
  const byDept = new Map<number, any[]>()
  const byDeptName = new Map<string, any[]>()
  const noDeptUsers: any[] = []
  for (const u of props.users || []) {
    const deptId = u.deptId ?? 0
    const leaf = { id: u.userId, label: u.nickName || u.userName || '未知人员' }
    if (u.deptId != null) {
      if (!byDept.has(deptId)) byDept.set(deptId, [])
      byDept.get(deptId)!.push(leaf)
      if (u.deptName) {
        if (!byDeptName.has(u.deptName)) byDeptName.set(u.deptName, [])
        byDeptName.get(u.deptName)!.push(leaf)
      }
    } else {
      noDeptUsers.push(leaf)
    }
  }
  const build = (nodes: any[]): any[] => {
    const out: any[] = []
    for (const n of nodes || []) {
      const deptId = n.id
      const node: any = { id: `d${deptId}`, label: n.deptName || n.label || '未设置部门', children: [] }
      if (byDept.has(deptId)) node.children.push(...byDept.get(deptId)!)
      node.children.push(...build(n.children || []))
      // 没人也没下级 → 不显示空部门
      if (node.children.length) out.push(node)
    }
    return out
  }
  const tree = build(props.deptTree || [])
  // 部门树未覆盖的人员（部门已删除/不在树内）→ 归入「未设置部门」，绝不显示 部门{id}
  const covered = new Set<number>()
  const collectIds = (nodes: any[]) => {
    for (const n of nodes || []) {
      if (n.id != null) covered.add(Number(n.id))
      collectIds(n.children || [])
    }
  }
  collectIds(props.deptTree || [])
  for (const [deptId, users] of byDept) {
    if (!covered.has(Number(deptId))) {
      noDeptUsers.push(...users)
    }
  }
  if (tree.length) {
    if (noDeptUsers.length) tree.push({ id: 'd-none', label: '未设置部门', children: noDeptUsers })
    return tree
  }
  // 兜底：无部门树时按真实 deptName 分组（绝不使用 deptId 拼接名称）
  const flat: any[] = []
  for (const [name, users] of byDeptName) {
    flat.push({ id: `n-${name}`, label: name, children: users })
  }
  if (noDeptUsers.length) flat.push({ id: 'd-none', label: '未设置部门', children: noDeptUsers })
  return flat
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
