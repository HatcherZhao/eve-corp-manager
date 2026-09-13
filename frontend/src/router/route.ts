import type { RouteRecordRaw } from 'vue-router'

/** 默认布局 */
const Layout = () => import('@/layout/index.vue')

/** 系统路由 */
export const systemRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { hidden: true },
  },
  {
    path: '/eve/register',
    name: 'EveRegistration',
    component: () => import('@/views/eve/register/index.vue'),
    meta: { title: '通过 EVE 注册', hidden: true },
  },
  {
    path: '/eve/recover',
    name: 'EvePasswordRecovery',
    component: () => import('@/views/eve/recover/index.vue'),
    meta: { title: '使用 EVE 找回密码', hidden: true },
  },
  /** 未指定页面时直接进入军团工作台，不保留框架默认仪表盘。 */
  {
    path: '/',
    redirect: '/eve/workspace',
    meta: { hidden: true },
  },
  {
    path: '/social/callback',
    component: () => import('@/views/login/social/index.vue'),
    meta: { hidden: true },
  },
  {
    path: '/pwdExpired',
    component: () => import('@/views/login/pwdExpired/index.vue'),
    meta: { hidden: true },
  },
  {
    path: '/user',
    name: 'User',
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: '/user/profile',
        name: 'UserProfile',
        component: () => import('@/views/user/profile/index.vue'),
        meta: { title: '个人中心', showInTabs: false },
      },
      {
        path: '/user/message',
        name: 'UserMessage',
        component: () => import('@/views/user/message/index.vue'),
        meta: { title: '消息中心', showInTabs: false },
      },
      {
        path: '/user/notice',
        name: 'UserNotice',
        component: () => import('@/views/user/message/components/view/index.vue'),
        meta: { title: '查看公告' },
      },
      {
        path: '/eve/permissions',
        name: 'EvePermissions',
        component: () => import('@/views/eve/permissions/index.vue'),
        meta: { title: 'EVE 权限', hidden: true },
      },
      {
        path: '/eve/access-management',
        name: 'EveAccessManagement',
        component: () => import('@/views/eve/access-management/index.vue'),
        meta: { title: '军团成员权限', hidden: true },
      },
    ],
  },
]

// 固定路由（默认路由）
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/default/redirect/index.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/default/error/404.vue'),
    meta: { hidden: true },
  },
  {
    path: '/403',
    component: () => import('@/views/default/error/403.vue'),
    meta: { hidden: true },
  },
]
