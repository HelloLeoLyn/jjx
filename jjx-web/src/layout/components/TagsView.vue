<!-- src/layout/components/TagsView.vue -->
<template>
  <div class="tags-view-container">
    <scroll-pane ref="scrollPane" class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :class="isActive(tag) ? 'active' : ''"
        :to="{ path: tag.path, query: tag.query }"
        class="tags-view-item"
        @click.middle="closeSelectedTag(tag)"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        {{ tag.title }}
        <el-icon
          v-if="!tag.affix"
          class="close-icon"
          @click.prevent.stop="closeSelectedTag(tag)"
        >
          <Close />
        </el-icon>
      </router-link>
    </scroll-pane>

    <!-- 右键菜单 -->
    <ul
      v-show="visible"
      :style="{ left: left + 'px', top: top + 'px' }"
      class="contextmenu"
    >
      <li @click="refreshSelectedTag(selectedTag)">刷新</li>
      <li
        v-if="!(selectedTag && selectedTag.affix)"
        @click="closeSelectedTag(selectedTag)"
      >
        关闭
      </li>
      <li @click="closeOthersTags">关闭其他</li>
      <li @click="closeAllTags(selectedTag)">关闭所有</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useTagsViewStore } from '@/store/modules/tagsView'
import ScrollPane from './ScrollPane.vue'

const route = useRoute()
const router = useRouter()
const tagsViewStore = useTagsViewStore()

const visitedViews = computed(() => tagsViewStore.getVisitedViews)
const visible = ref(false)
const top = ref(0)
const left = ref(0)
const selectedTag = ref<any>(null)

const isActive = (tag: any) => {
  return tag.path === route.path
}

const addTags = () => {
  const { name } = route
  if (name) {
    tagsViewStore.addView(route)
  }
  return false
}

const refreshSelectedTag = (tag: any) => {
  router.replace({
    path: '/redirect' + tag.path,
    query: tag.query,
  })
}

const closeSelectedTag = (tag: any) => {
  tagsViewStore.delView(tag)
  if (isActive(tag)) {
    toLastView(tag)
  }
}

const closeOthersTags = () => {
  if (selectedTag.value) {
    tagsViewStore.delOthersViews(selectedTag.value)
    if (!isActive(selectedTag.value)) {
      router.push(selectedTag.value.path)
    }
  }
}

const closeAllTags = (tag?: any) => {
  tagsViewStore.delAllViews()
  if (tag && tag.affix) {
    router.push(tag.path)
  } else {
    router.push('/')
  }
}

const toLastView = (tag: any) => {
  const views = visitedViews.value
  if (views.length > 0) {
    const lastView = views[views.length - 1]
    router.push(lastView.path)
  } else {
    router.push('/')
  }
}

const openMenu = (tag: any, e: MouseEvent) => {
  const menuMinWidth = 105
  const offsetLeft = 20
  const offsetTop = 16

  selectedTag.value = tag
  visible.value = true

  const maxLeft = window.innerWidth - menuMinWidth
  left.value =
    e.clientX + offsetLeft > maxLeft ? maxLeft : e.clientX + offsetLeft
  top.value = e.clientY + offsetTop
}

const closeMenu = () => {
  visible.value = false
}

watch(
  route,
  () => {
    addTags()
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('click', closeMenu)
  addTags()
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenu)
})
</script>

<style scoped lang="scss">
.tags-view-container {
  height: 34px;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid #d8dce5;
  box-shadow:
    0 1px 3px 0 rgba(0, 0, 0, 0.12),
    0 0 3px 0 rgba(0, 0, 0, 0.04);

  .tags-view-wrapper {
    white-space: nowrap;
    position: relative;
    overflow-x: auto;
    overflow-y: hidden;
    height: 34px;

    .tags-view-item {
      display: inline-block;
      position: relative;
      cursor: pointer;
      height: 26px;
      line-height: 26px;
      border: 1px solid #d8dce5;
      color: #495060;
      background: #fff;
      padding: 0 8px;
      font-size: 12px;
      margin-left: 5px;
      margin-top: 4px;
      text-decoration: none;

      &:first-of-type {
        margin-left: 15px;
      }

      &:last-of-type {
        margin-right: 15px;
      }

      &.active {
        background-color: #42b983;
        color: #fff;
        border-color: #42b983;

        &::before {
          content: '';
          background: #fff;
          display: inline-block;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          position: relative;
          margin-right: 4px;
        }
      }

      .close-icon {
        width: 12px;
        height: 12px;
        margin-left: 6px;
        transition: all 0.3s;

        &:hover {
          background-color: #b4bccc;
          color: #fff;
          border-radius: 50%;
        }
      }
    }
  }
}

.contextmenu {
  margin: 0;
  background: #fff;
  z-index: 3000;
  position: absolute;
  list-style-type: none;
  padding: 5px 0;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #333;
  box-shadow: 2px 2px 3px 0 rgba(0, 0, 0, 0.3);

  li {
    margin: 0;
    padding: 7px 16px;
    cursor: pointer;

    &:hover {
      background: #eee;
    }
  }
}
</style>
