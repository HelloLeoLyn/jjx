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
    /** 可选执行人（平铺用户列表，按 deptId 分组展示） */
    users: PickerUser[]
    /** 已选 userId 列表 */
    modelValue: number[]
    title?: string
  }>(),
  { title: '选择执行人' },
)

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'update:modelValue', ids: number[]): void
  (e: 'confirm', ids: number[]): void
}>()

const treeRef = ref<any>()
const keyword = ref('')

// 按部门分组构建树（部门节点 d{deptId}，用户叶子=userId）
const treeData = computed(() => {
  const map = new Map<number, { id: string; label: string; children: any[] }>()
  for (const u of props.users || []) {
    const deptId = u.deptId ?? 0
    if (!map.has(deptId)) {
      map.set(deptId, { id: `d${deptId}`, label: u.deptName || `部门${deptId}`, children: [] })
    }
    map.get(deptId)!.children.push({
      id: u.userId,
      label: u.nickName || u.userName || `用户${u.userId}`,
    })
  }
  return [...map.values()]
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
