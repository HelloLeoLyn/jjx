import { createRouter, createWebHistory } from 'vue-router'
import KanbanPage from '@/views/KanbanPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/kanban',
    },
    {
      path: '/kanban',
      name: 'kanban',
      component: KanbanPage,
    },
  ],
})

export default router
