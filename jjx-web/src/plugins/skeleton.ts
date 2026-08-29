import type { App } from 'vue'
import {
  SkeletonQuery,
  SkeletonStats,
  SkeletonToolbar,
  SkeletonTable,
  SkeletonAction,
} from '@/components/page-skeleton'

/** 全局注册骨架占位组件 */
export function setupSkeleton(app: App) {
  app.component('SkeletonQuery', SkeletonQuery)
  app.component('SkeletonStats', SkeletonStats)
  app.component('SkeletonToolbar', SkeletonToolbar)
  app.component('SkeletonTable', SkeletonTable)
  app.component('SkeletonAction', SkeletonAction)
}
