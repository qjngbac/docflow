import { createRouter, createWebHistory } from 'vue-router'
import { getStoredUser, hasAuthSession } from '../utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { guest: true }
  },
  {
    path: '/share/:token',
    name: 'ShareView',
    component: () => import('../views/ShareView.vue'),
    props: true,
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'DocList', component: () => import('../views/DocList.vue') },
      { path: 'doc/:id', name: 'DocEdit', component: () => import('../views/DocEdit.vue'), props: true },
      { path: 'admin', name: 'Admin', component: () => import('../views/Admin.vue'), meta: { admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  const authenticated = hasAuthSession()
  if (to.meta.requiresAuth && !authenticated) return '/login'
  if (to.meta.admin && getStoredUser()?.systemRole !== 'ADMIN') return '/'
  if (to.name === 'Login' && authenticated) return '/'
  return true
})

export default router
