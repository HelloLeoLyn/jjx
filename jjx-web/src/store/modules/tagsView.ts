// src/store/modules/tagsView.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TagView {
  name: string
  path: string
  title: string
  affix?: boolean
  query?: Record<string, any>
  meta?: Record<string, any>
}

export const useTagsViewStore = defineStore('tagsView', () => {
  // state
  const visitedViews = ref<TagView[]>([])
  const cachedViews = ref<string[]>([])

  // getters
  const getVisitedViews = computed(() => visitedViews.value)
  const getCachedViews = computed(() => cachedViews.value)

  // 添加访问过的视图
  function addVisitedView(view: RouteLocationNormalized) {
    // 检查是否已存在
    const hasView = visitedViews.value.some((v) => v.path === view.path)
    if (hasView) return

    const tagView: TagView = {
      name: view.name as string,
      path: view.path,
      title: (view.meta?.title as string) || '未命名',
      affix: view.meta?.affix as boolean,
      query: view.query,
      meta: view.meta,
    }

    visitedViews.value.push(tagView)
  }

  // 添加缓存的视图
  function addCachedView(view: RouteLocationNormalized) {
    if (cachedViews.value.includes(view.name as string)) return
    if (!view.meta?.noCache) {
      cachedViews.value.push(view.name as string)
    }
  }

  // 添加视图
  function addView(view: RouteLocationNormalized) {
    addVisitedView(view)
    addCachedView(view)
  }

  // 删除访问过的视图
  function delVisitedView(view: TagView) {
    const index = visitedViews.value.findIndex((v) => v.path === view.path)
    if (index > -1) {
      visitedViews.value.splice(index, 1)
    }
  }

  // 删除缓存的视图
  function delCachedView(view: TagView) {
    const index = cachedViews.value.indexOf(view.name)
    if (index > -1) {
      cachedViews.value.splice(index, 1)
    }
  }

  // 删除视图
  function delView(view: TagView) {
    delVisitedView(view)
    delCachedView(view)
  }

  // 删除其他访问过的视图
  function delOthersVisitedViews(view: TagView) {
    visitedViews.value = visitedViews.value.filter((v) => {
      return v.meta?.affix || v.path === view.path
    })
  }

  // 删除其他缓存的视图
  function delOthersCachedViews(view: TagView) {
    const index = cachedViews.value.indexOf(view.name)
    if (index > -1) {
      cachedViews.value = cachedViews.value.slice(index, index + 1)
    } else {
      cachedViews.value = []
    }
  }

  // 删除其他视图
  function delOthersViews(view: TagView) {
    delOthersVisitedViews(view)
    delOthersCachedViews(view)
  }

  // 删除所有访问过的视图
  function delAllVisitedViews() {
    const affixTags = visitedViews.value.filter((tag) => tag.affix)
    visitedViews.value = affixTags
  }

  // 删除所有缓存的视图
  function delAllCachedViews() {
    cachedViews.value = []
  }

  // 删除所有视图
  function delAllViews() {
    delAllVisitedViews()
    delAllCachedViews()
  }

  return {
    // state
    visitedViews,
    cachedViews,
    // getters
    getVisitedViews,
    getCachedViews,
    // actions
    addView,
    addVisitedView,
    addCachedView,
    delView,
    delVisitedView,
    delCachedView,
    delOthersViews,
    delOthersVisitedViews,
    delOthersCachedViews,
    delAllViews,
    delAllVisitedViews,
    delAllCachedViews,
  }
})
