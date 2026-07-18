import type { PageQuery } from '..'
import type { RouteRecordRaw, RouteMeta } from 'vue-router'
import type { Component } from 'vue'

export interface LoginResponse {
  token: string
  userInfo: LoginUser
  roles: string[]
  permissions: string[]
  isLogin: boolean
}
export interface LoginUser {
  userId: number
  userName: string
  nickName?: string
  realName?: string
  avatar?: string
  deptId?: number
  roles: string[]
  permissions: string[]
}
export interface RouteRecord {
  path: string
  name?: string
  component?: any
  redirect?: string
  meta?: RouteMeta
  children?: RouteRecord[]
}

// 用户状态接口
export interface UserState {
  token: string
  userInfo?: LoginUser | null
  roles: string[]
  permissions: string[]
  sidebarCollapsed: boolean
  isLogin: boolean
}

// 路由配置接口
export interface RouteConfig {
  path: string
  name?: string
  component?: string | Component
  redirect?: string
  children?: RouteConfig[]
  meta?: RouteMeta
  hidden?: boolean
  alwaysShow?: boolean
}

// 菜单项接口
export interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  permission?: string
  hidden?: boolean
  sort?: number
  children?: MenuItem[]
}

// 异步路由配置
export interface AsyncRouteConfig {
  path: string
  name?: string
  component?: string | Component
  redirect?: string
  children?: AsyncRouteConfig[]
  meta?: {
    title?: string
    icon?: string
    hidden?: boolean
    permission?: string
    sort?: number
    keepAlive?: boolean
    affix?: boolean
    [key: string]: any
  }
  [key: string]: any
}

// 登录表单接口
export interface LoginForm {
  username: string
  password: string
  captcha?: string
  rememberMe?: boolean
}
export interface UserStoreReturn {
  // state
  token: import('vue').Ref<string>
  userInfo: import('vue').Ref<SysUser | null>
  permissions: import('vue').Ref<string[]>
  accessibleRoutes: import('vue').Ref<RouteRecordRaw[]>
  menuList: import('vue').Ref<MenuItem[]>
  sidebarCollapsed: import('vue').Ref<boolean>
  // getters
  isLoggedIn: import('vue').ComputedRef<boolean>
  getUserName: import('vue').ComputedRef<string>
  getNickName: import('vue').ComputedRef<string>
  getAvatar: import('vue').ComputedRef<string>
  getPermissions: import('vue').ComputedRef<string[]>
  getAccessibleRoutes: import('vue').ComputedRef<RouteRecordRaw[]>
  getMenuList: import('vue').ComputedRef<MenuItem[]>
  getSidebarCollapsed: import('vue').ComputedRef<boolean>
  // actions
  setToken: (token: string) => void
  setUserInfo: (userInfo: SysUser) => void
  setPermissions: (permissions: string[]) => void
  setAccessibleRoutes: (routes: RouteRecordRaw[]) => void
  setMenuList: (menus: MenuItem[]) => void
  toggleSidebar: () => void
  loadUserPermissions: () => Promise<void>
  getUserInfo: (forceRefresh?: boolean) => Promise<SysUser>
  login: (loginForm: LoginForm) => Promise<any>
  resetToken: () => void
  logout: () => Promise<void>
}
// 用户相关类型
export interface SysUser {
  userId?: number
  deptId?: number
  userName: string
  nickName?: string
  userType?: string
  email?: string
  phone?: string
  sex?: string
  avatar?: string
  password?: string
  status?: string
  delFlag?: string
  loginIp?: string
  loginDate?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  remark?: string
  dept?: SysDept
  roles?: SysRole[]
  roleIds?: number[]
  postIds?: number[]
  roleId?: number
  permissions?: string[] // 用户权限列表
}
export interface SecurityUser {
  userId?: number
  password?: string
  newPassword?: string
  oldPassword?: string
  confirmPassword?: string
}
export interface SysUserDTO extends SecurityUser {
  userId?: number
  deptId?: number
  userName?: string
  nickName?: string
  userType?: string
  email?: string
  phone?: string
  sex?: string
  avatar?: string
  remark?: string
  status?: string
  roleIds?: number[]
  permissions?: string[] // 用户权限列表
}
export interface SysUserRole {
  userId: number
  roleId: number
}

export interface SysUserRoleQuery extends PageQuery {
  userId: number
  roleId?: number
  roleName?: string
  roleStatus?: string
}

export interface SysUserRoleVO {
  userId?: number
  roleId?: number
  roleName?: string
  roleStatus?: string
}

// 用户VO（对应后端 SysUserVO）
export interface SysUserVO {
  userId: number
  deptId?: number
  userName: string
  nickName?: string
  userType?: string
  email?: string
  phone?: string
  sex?: string
  avatar?: string
  status?: number
  roleIds?: number[]
}

// 角色用户查询DTO
export interface RoleUserQueryDTO {
  roleId: number
  userName?: string
  phone?: string
  status?: string
  pageNum?: number
  pageSize?: number
}

// 角色相关类型
export interface SysRole {
  roleId?: number
  roleName: string
  roleKey: string
  roleSort?: number
  dataScope?: string
  menuCheckStrictly?: boolean
  deptCheckStrictly?: boolean
  status?: string
  delFlag?: string
  remark?: string
  flag?: boolean
  menuIds?: number[]
  deptIds?: number[]
  menus?: SysMenu[]
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}
// 菜单相关类型
export interface SysMenu {
  menuId?: number
  menuName: string
  routeName?: string
  parentId?: number
  orderNum?: number
  path?: string
  component?: string
  query?: string
  isFrame?: string
  isCache?: string
  menuType: string
  visible?: string
  status?: string
  perms?: string
  icon?: string
  remark?: string
  children?: SysMenu[]
  parentName?: string
  checked?: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}
export interface SysMenuQuery {
  menuName?: string
  status?: string
  perms?: string
  icon?: string
  remark?: string
  parentId?: number
  menuType?: string
  isFrame?: string
  isCache?: string
  visable?: string
}
// 路由菜单相关类型
export interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  permission?: string
  external?: string
  affix?: boolean
  hidden?: boolean
  children?: MenuItem[]
  sort?: number
}

// 菜单类型枚举
export enum MenuType {
  DIRECTORY = 'M', // 目录
  MENU = 'C', // 菜单
  BUTTON = 'F', // 按钮
}

// 菜单组件类型
export type MenuComponent = string | Component | (() => Promise<Component>)

// 菜单元数据
export interface MenuMeta {
  title?: string
  icon?: string
  hidden?: boolean
  permission?: string
  keepAlive?: boolean
  affix?: boolean
  sort?: number
  [key: string]: any
}

// 过滤选项
export interface FilterOptions {
  lastRouter?: AsyncRouteConfig | boolean
  type?: boolean
  keepChildren?: boolean
}
// 后端返回的菜单项类型
export interface BackendMenuItem {
  id: number
  parentId: number
  name: string
  path: string
  component: string
  redirect?: string
  title: string
  icon?: string
  permission?: string
  sort: number
  hidden?: boolean
  alwaysShow?: boolean
  children?: BackendMenuItem[]
}
// 部门相关类型
export interface SysDept {
  id?: number
  parentId?: number
  ancestors?: string
  deptName?: string
  orderNum?: number
  leader?: string
  phone?: string
  email?: string
  status?: string
  delFlag?: string
  parentName?: string
  children?: SysDept[]
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 字典相关类型
export interface SysDictType {
  dictId?: number
  dictName: string
  dictType: string
  status?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

export interface SysDictData {
  dictCode?: number
  dictSort?: number
  dictLabel: string
  dictValue: string
  dictType: string
  cssClass?: string
  listClass?: string
  isDefault?: string
  status?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 字典类型别名，用于向后兼容
export type SysDict = SysDictType

// 日志相关类型
export * from './operation-log'
export * from './login-log'
export * from './exception-log'
export * from './status-change-log'
