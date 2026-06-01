import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { layout: 'blank' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { layout: 'blank' }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('../views/ForgotPasswordView.vue'),
    meta: { layout: 'blank' }
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('../views/ResetPasswordView.vue'),
    meta: { layout: 'blank' }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/DashboardAnalyticsView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/workspace',
    name: 'workspace',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/board',
    name: 'board',
    component: () => import('../views/BoardView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('../views/NotificationsView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/inbox',
    name: 'inbox',
    component: () => import('../views/InboxView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/projects/:id',
    name: 'project-detail',
    component: () => import('../views/ProjectDetailView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/projects/:id/members',
    name: 'project-members',
    component: () => import('../views/ProjectMembersView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/projects/:id/activity',
    name: 'project-activity',
    component: () => import('../views/ProjectActivityView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/projects/:id/audit',
    name: 'project-audit',
    component: () => import('../views/ProjectAuditView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/projects/:projectId/tasks/:taskId',
    name: 'task-detail',
    component: () => import('../views/TaskDetailView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/ai/chat',
    name: 'ai-chat',
    component: () => import('../views/AiChatView.vue'),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/ai/history',
    name: 'ai-history',
    redirect: (to) => ({ name: 'ai-chat', query: { ...to.query, tab: 'history' } }),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/ai/code-review',
    name: 'ai-code-review',
    redirect: (to) => ({ name: 'ai-chat', query: { ...to.query, tab: 'review' } }),
    meta: { requiresAuth: true, layout: 'app' }
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('../views/AdminUsersView.vue'),
    meta: { requiresAuth: true, layout: 'app', requiresRole: 'ADMIN' }
  },
  {
    path: '/project-invite',
    name: 'project-invite',
    component: () => import('../views/ProjectInviteView.vue'),
    meta: { layout: 'blank' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: { layout: 'blank' }
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  const auth = useAuthStore()
  if (!auth.isAuthed) return { name: 'login', query: { redirect: to.fullPath } }
  const requiresRole = to.meta.requiresRole ? String(to.meta.requiresRole) : null
  if (requiresRole && auth.role !== requiresRole) return { name: 'dashboard' }
  return true
})
