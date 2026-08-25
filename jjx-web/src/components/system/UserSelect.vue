<template>
  <el-select
    v-model="selectedValue"
    filterable
    remote
    clearable
    :remote-method="remoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    style="width: 100%"
    @change="handleSelectChange"
    @clear="handleClear"
  >
    <el-option
      v-for="u in options"
      :key="u.userId"
      :label="optionLabel(u)"
      :value="u.userId!"
    />
  </el-select>
</template>

<script setup lang="ts">
/**
 * 用户选择组件（远程搜索，DEV-1106）
 *
 * 输入关键字 → 并发搜索 用户名(userName)+昵称(nickName) → 下拉选择
 * v-model 绑定 userId；change 事件回传完整用户对象，供父组件联动填名字/邮箱/电话等字段
 * 编辑回显：modelValue 有值且本地无匹配项时，自动按 userId 拉取用户信息展示
 */
import { ref, computed, watch } from 'vue'
import { userApi } from '@/api/system/user'
import type { SysUser } from '@/types/system'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    placeholder?: string
  }>(),
  {
    modelValue: null,
    placeholder: '搜索用户名/姓名选择',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  /** 选中用户（完整对象）；未匹配到时为 null */
  change: [user: SysUser | null]
  /** 用户主动清空选择（区别于回显同步导致的 change(null)） */
  clear: []
}>()

const loading = ref(false)
const options = ref<SysUser[]>([])

// 照 DeptSelect 模式：computed 包装 v-model，绕开 el-select modelValue 泛型严格检查
const selectedValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

/** 展示格式：昵称（用户名） 部门名 */
const optionLabel = (u: SysUser) => {
  const name = u.nickName || u.userName
  const dept = u.dept?.deptName
  return dept ? `${name}（${u.userName}）· ${dept}` : `${name}（${u.userName}）`
}

/** 远程搜索：用户名 + 昵称 双查合并去重（后端 userName/nickName 是 AND 关系，不能同传） */
const remoteSearch = async (kw: string) => {
  const keyword = kw.trim()
  if (!keyword) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const [byName, byNick] = await Promise.all([
      userApi.list({ userName: keyword, pageNum: 1, pageSize: 20 }),
      userApi.list({ userName: '', nickName: keyword, pageNum: 1, pageSize: 20 }),
    ])
    const map = new Map<number, SysUser>()
    ;[...(byName.data?.records || []), ...(byNick.data?.records || [])].forEach((u) => {
      if (u.userId != null) map.set(u.userId, u)
    })
    options.value = [...map.values()]
  } finally {
    loading.value = false
  }
}

/** 回显：外部已选中（编辑场景），本地无该用户时按 ID 拉取 */
watch(
  () => props.modelValue,
  async (id) => {
    if (id == null) return
    if (options.value.some((u) => u.userId === id)) return
    try {
      const res = await userApi.getInfo(id)
      if (res.data && !options.value.some((u) => u.userId === id)) {
        options.value = [res.data, ...options.value]
      }
    } catch {
      // 用户不存在/无权限：保持空白，不阻塞编辑
    }
  },
  { immediate: true },
)

/** 选中变化 → 回传完整用户对象（未匹配到时 null） */
const handleSelectChange = (val: number | null) => {
  const user = options.value.find((u) => u.userId === val) || null
  emit('change', user)
}

/** 用户主动清空（el-select 的 clear 不触发 change，需单独处理） */
const handleClear = () => {
  emit('clear')
}
</script>
