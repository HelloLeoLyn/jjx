# JJX ERP 内网环境 · 排查手册（简版）

> 建档：2026-09-10 | 只讲"出问题怎么查"，不讲监控平台建设
> 配套：`internal-https-setup-guide-20260904.md`（https/证书全量方案）

## 0. 30 秒看全局（先跑这三条，能定位 80% 的问题）

```bash
docker ps -a | grep jjx-nginx          # 容器在不在 Up
ss -ltnp | grep -E ':(80|443|3000|8080|3306|6379)'   # 六个端口谁没监听
curl -sk -o /dev/null -w '%{http_code}\n' --noproxy '*' https://192.168.1.176/
```

## 1. 请求链路（按这个顺序从外往里查）

```
手机/PC
  └─ https://192.168.1.176 :443   docker 容器 jjx-nginx
       └─ http://host.docker.internal:3000   vite dev（前端）
            └─ /api  →  vite proxy → http://localhost:8080   后端 jar
                 └─ MySQL 127.0.0.1:3306 / Redis 127.0.0.1:6379
```

## 2. 手机打不开 https

先分清"本机通、手机不通"（网络/证书问题）还是"哪都不通"（服务没起）。

```bash
curl -sk -I --noproxy '*' https://192.168.1.176/     # 本机 200？
```

| 症状 | 判断 | 处理 |
|---|---|---|
| 本机也不通，`docker ps -a` 显示 Exited | 容器没起来（Docker Desktop 重启后单文件挂载失败，已发生两次） | `docker start jjx-nginx`；再起不来就按 https 文档 §4.2 重建容器 |
| 80/443 无监听 | 同上，或端口被占 | `ss -ltnp \| grep -E ':(80\|443)'` 查占用 |
| 手机提示证书不受信任 | 手机没装 CA，或地址不在证书 SAN 里 | 装 `~/nginx-certs/ca.crt`（手机副本 `/mnt/d/openclaw-workspace/docs/certs/JJX-CA.crt`）；**地址必须用 192.168.1.176**，用 172.18.0.1 一定报错 |
| 手机提示证书不匹配且最近换过网络 | 证书 SAN 绑定 IP，换网即失效 | 按 https 文档 §4.1 用新 IP 重签 |
| 连接超时 | 手机与机器不在同一网段 / 宿主防火墙 | 先用手机 ping 192.168.1.176 验证二层可达 |

证书自检（含 SAN 是否覆盖当前 IP）：

```bash
echo | openssl s_client -connect 192.168.1.176:443 -servername 192.168.1.176 -CAfile ~/nginx-certs/ca.crt 2>/dev/null \
  | openssl x509 -noout -subject -ext subjectAltName
```

## 3. 页面能开但接口报错 / 白屏

```bash
curl -sk --noproxy '*' -X POST https://192.168.1.176/sessions/auth \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}'
```

- 200 → 链路通，问题在前端页面逻辑（看浏览器控制台）
- 401/403 → 登录态/权限（注意 Sa-Token 头名是 `token`）
- 500 → 后端异常，走第 4 节
- 连不上 → vite dev 挂了：`ss -ltnp | grep :3000`，重跑 `npm run dev`

## 4. 后端 500 / 业务报错

1. **先确认跑的是新代码**（本项目高频坑）：`ps -o lstart,cmd -p <pid>`，与最近 pull/提交时间比对。旧进程/旧 target/classes → 重启；一律用打包 jar 起：`java -jar target/jjx-server-1.0.0.jar`
2. 查异常日志表（`exception_msg` 是 varchar(500)，**别截断看，语句全名在尾巴上**）：
   ```sql
   SELECT trigger_time, username, request_url, exception_msg
   FROM sys_error_log ORDER BY id DESC LIMIT 20;
   ```
3. 日志表被清过（`LogCleanTask` 每天 02:00/03:00/04:00 按保留天数清理并归档）→ 去归档表/文件找历史
4. 业务类报错（如"仅支持 PASS、FAIL"）是 `BusinessException` 主动抛的，**不进 sys_error_log**，直接按报错文案反查代码里的 throw

## 5. 数据库 / 迁移

- 动库前先备份（规范 §2 固定命令），备份落 `jjx-docs/sql/backups/`
- 迁移是否跑过：对比 `jjx-docs/sql/migrations/` 里的最大序号 与库中实际状态（当前只能人工看，属已知短板）
- 迁移一律进 `jjx-docs/sql/migrations/NN_*.sql`，幂等优先，破坏性语句单独文件

## 6. 谁改了什么

```sql
SELECT create_time, username, module, action, biz_type, biz_id, oper_url
FROM sys_oper_log WHERE biz_id = ? ORDER BY id DESC;
```

登录记录看 `sys_login_log`；单据级追溯用 `trace_id` 串起操作日志和业务流转。

## 7. 今天踩过的三个坑（当案例记）

1. **手机 https 全断** = 容器 Exited（不是配置/证书问题）。教训：先 `docker ps -a`，别先怀疑 nginx 配置。
2. **界面缺整块菜单** = 迁移没跑（代码到 78、库停在 74）。教训：拉完代码先确认迁移序号。
3. **改完代码不生效** = 8080 还是旧进程。教训：`ps -o lstart` 比对启动时间。
