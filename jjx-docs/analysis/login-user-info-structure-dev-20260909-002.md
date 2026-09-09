# 登录用户信息结构理顺 + isLeader 属性（A+B+isLeader 数据层）

## 背景
用户要优化个人中心(/profile/index)，检查发现登录后用户信息结构有四处问题，本次只做数据层，**不碰派工/工序/移动端任何按钮逻辑**。

## 范围（A+B+isLeader 数据层）
- A：登录/刷新返回的 userInfo 携带真实 nickName/avatar；刷新后 roles 恢复
- B：GET /system/user/current 返回 deptName + roleNames，profile 页删掉部门树/角色表两个全量接口与本地拼装
- isLeader：LoginVO 顶层加 isLeader（口径=当前用户是任一未删除部门的 leader_user_id），进 store

## 明确不做（禁止触碰）
- 生产派工、工序执行（production/execution、dispatch、mobile/order.vue、order/index.vue 等）任何按钮显示/权限/逻辑改动
- 数据库结构变更（无迁移）
- isLeader 在业务按钮上的任何消费

## 改动清单

### 后端
1. `jjx-server/.../system/domain/vo/LoginUser.java`：新增 `private String nickName;` 与 `private String avatar;`
2. `jjx-server/.../system/converter/SysUserConverter.java`：toVO 增加映射 `@Mapping(target="nickName", source="nickName")`、`@Mapping(target="avatar", source="avatar")`（现有 realName=nickName 别名保留不动，防其他消费点）
3. `jjx-server/.../system/domain/vo/LoginVO.java`：新增 `private Boolean isLeader;`（与 roles/permissions 平级）
4. `jjx-server/.../system/service/AuthService.java`：注入部门查询（现有 deptService/或 SysDeptMapper），buildLoginVO 计算 `isLeader = 存在 sys_dept 且 del_flag=0 且 leader_user_id = userId`，set 到 LoginVO。/sessions/current 读 sa-token 会话缓存的 LoginVO，自动带出。
5. `jjx-server/.../system/controller/system/SysUserController.java` GET `/system/user/current` 与 `jjx-server/.../service/impl/SysUserServiceImpl.java#selectUserById`（109-115 行，现只填 roleIds）：
   - SysUserVO 已有 deptName 字段但从未填充 → selectUserById 按 deptId join sys_dept 填 deptName
   - SysUserVO 新增 `private List<String> roleNames;`（transient 展示字段），selectUserById 按 roleIds 查 sys_role 填 roleName 列表
   - 注：profile 页头像上传已同步 store avatar（index.vue:620-627），无需新增
6. 前端类型 `jjx-web/src/types/system/index.ts`：
   - LoginResponse 增加 `isLeader: boolean`
   - LoginUser 的 nickName/avatar 保留可选（后端现在会真的给了）

### 前端 store
7. `jjx-web/src/store/modules/user.ts`：
   - state 增 `isLeader: false`
   - login()：`this.isLeader = !!res.data.isLeader`（LoginVO 顶层）
   - getUserInfo()：补 `if (res.data.roles) this.setRoles(res.data.roles)`（刷新恢复 roles，LoginVO 缓存里有）+ `this.isLeader = !!res.data.isLeader`
   - resetToken/logout 清 isLeader=false
   - getter `isLeader: (state) => state.isLeader`

### 前端 profile 页（B）
8. `jjx-web/src/views/system/user/profile/index.vue`：
   - 删 deptApi.treeselect 与 roleApi.optionselect 两个请求与 deptMap/roleMap/convertDeptToMap
   - deptName = user.deptName；roleNames = user.roleNames || []（后端已给）
   - 其余（基本资料/改密/头像表单逻辑）不动

## 验证
- 后端：`cd jjx-server && mvn -o clean compile`
- 前端：`cd jjx-web && npx vue-tsc --noEmit`（本任务文件不得报错）
- 人工：登录后顶栏显示真实昵称/头像；刷新页面后 roles/权限正常（完工按钮相关不在此次验证范围）；GET /system/user/current 返回 deptName/roleNames

## 注意
- 不改 git commit（由 Hermes 复核后提交）
- 工作区存在其他会话的脏文件（task1517 sql 暂存删除、https 文档、1625 文件等），只动上述清单文件
- isLeader 为登录时快照：中途调整部门负责人需重新登录生效（可接受）
