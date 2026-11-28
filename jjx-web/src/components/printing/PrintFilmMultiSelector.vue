<template>
  <div class="film-picker">
    <el-input v-model="query" :disabled="disabled" clearable placeholder="搜索菲林名称、版本或实体片编号，勾选可直接加入" aria-label="选择菲林" />
    <p class="hint">可连续勾选多张，已选菲林会保留在下方。每张默认使用全部样式。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="search.loading.value" class="results" aria-label="菲林搜索结果">
      <div v-for="row in candidates" :key="row.filmId" class="film-option">
        <el-checkbox :model-value="isSelected(row.filmId!) || pending.has(row.filmId!)" :disabled="disabled || pending.has(row.filmId!)" @update:model-value="checked => toggle(row, !!checked)">
          <span class="film-name">{{ row.name || row.artworkNo }} · {{ row.versionNo }}</span>
          <span class="film-no">{{ row.filmNo }}</span>
          <span v-if="pending.has(row.filmId!)" class="hint">正在读取样式…</span>
        </el-checkbox>
      </div>
      <p v-if="!query.trim()" class="hint">输入关键词搜索实体菲林。</p>
      <p v-else-if="search.searched.value && !search.loading.value && !candidates.length" class="hint">当前结果中没有可用实体菲林，可换关键词或继续加载。</p>
      <el-button v-if="search.failed.value" link type="primary" :disabled="disabled" @click="search.retry()">查询失败，点击重试</el-button>
      <el-button v-else-if="search.items.value.length < search.total.value" link type="primary" :disabled="disabled" :loading="search.loadingMore.value" @click="search.loadMore()">加载更多</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { usePrintResourceSearch } from '@/components/Selector/usePrintResourceSearch'
import { printingArtworkApi } from '@/api/product/film'
import type { PrintSearchRow } from '@/api/engineering/printingSearch'
import type { ArtworkView } from '@/types/engineering/printingArtwork'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'
import { readStyles, type PlateSource } from './printStyles'

const props = withDefaults(defineProps<{ modelValue: PlateSource[]; initialKeyword?: string; disabled?: boolean }>(), { initialKeyword: '', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: PlateSource[]]; loading: [value: boolean] }>()
const query = ref(props.initialKeyword)
const search = usePrintResourceSearch()
const pending = ref(new Set<number>())
const selected = ref<PlateSource[]>(props.modelValue)
const error = ref('')
const details = new Map<number, Promise<ArtworkView>>()
let disposed = false
watch(() => props.modelValue, value => { selected.value = value }, { flush: 'sync' })
watch(query, value => { error.value = ''; search.search(value) }, { immediate: true })
watch(() => pending.value.size, count => emit('loading', count > 0), { flush: 'sync' })
const candidates = computed(() => [...new Map(search.items.value
  .filter(row => row.filmId && row.versionId && row.artworkId && row.versionStatus === ArtworkVersionStatusEnum.RELEASED.value && row.filmStatus === FilmCopyStatusEnum.AVAILABLE.value)
  .map(row => [row.filmId!, row])).values()])
const isSelected = (id: number) => selected.value.some(source => source.filmId === id)

async function detail(id: number) {
  if (!details.has(id)) {
    const request = printingArtworkApi.detail(id).then(response => response.data)
    details.set(id, request)
    request.catch(() => { details.delete(id) })
  }
  return details.get(id)!
}

async function toggle(row: PrintSearchRow, checked: boolean) {
  if (props.disabled || !row.filmId || !row.versionId || !row.artworkId || pending.value.has(row.filmId)) return
  if (!checked) {
    selected.value = selected.value.filter(source => source.filmId !== row.filmId)
    emit('update:modelValue', selected.value)
    return
  }
  if (isSelected(row.filmId)) return
  error.value = ''
  pending.value.add(row.filmId)
  try {
    const value = await detail(row.artworkId)
    if (disposed || props.disabled) return
    const version = value.versions?.find(item => item.versionId === row.versionId)
    const film = value.films?.find(item => item.filmId === row.filmId && item.versionId === row.versionId && item.status === FilmCopyStatusEnum.AVAILABLE.value)
    if (!version || version.status !== ArtworkVersionStatusEnum.RELEASED.value || !film) throw new Error('该实体菲林或版本已不可用，请重新搜索')
    const styles = readStyles(version.stylesJson)
    if (!styles.length) throw new Error('该菲林版本尚未定义样式，请先补充版本样式')
    selected.value = [...selected.value, { filmId: film.filmId, filmNo: film.filmNo, versionId: version.versionId,
      artworkNo: value.artworkNo, artworkName: value.name, versionNo: version.versionNo,
      styles, styleNos: styles.map(style => style.styleNo!) }]
    emit('update:modelValue', selected.value)
  } catch (cause) {
    if (!disposed) error.value = cause instanceof Error ? cause.message : '菲林样式加载失败，请重新勾选重试'
  } finally { pending.value.delete(row.filmId) }
}
onBeforeUnmount(() => { disposed = true; emit('loading', false) })
</script>

<style scoped>
.film-picker { width: 100%; }
.results { max-height: 230px; overflow-y: auto; border: 1px solid var(--el-border-color); border-radius: 4px; padding: 8px 12px; }
.film-option { display: block; padding: 3px 0; }
.film-name { font-weight: 500; }
.film-no { margin-left: 12px; color: var(--el-text-color-secondary); }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin: 6px 0; }
</style>
