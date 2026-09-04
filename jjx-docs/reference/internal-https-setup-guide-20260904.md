# 内网 HTTPS 方案：Docker nginx 反代 + 自建 CA IP 证书

> 2026-09-04 建档 | 适用：JJX ERP 局域网内手机/PDA 访问（摄像头扫码需要安全上下文 HTTPS）

## 1. 背景

移动端 H5 的**摄像头扫码**（getUserMedia / Html5Qrcode）只能在**安全上下文**下工作：
- 满足条件 = HTTPS，或 localhost/127.0.0.1
- 手机通过局域网 IP 访问（如 `http://192.168.1.176:3000`）属于不安全上下文，Chrome 会拒绝调用摄像头

因此局域网内需要一层 HTTPS 入口。本方案在 2026-09-01 已部署于 JJX 开发机，本文档供**其他电脑/新环境复刻**。

## 2. 方案定性

**Docker 容器跑 nginx（alpine）做反向代理 + openssl 自建根 CA 签发 IP 证书**（非 mkcert、非公网证书）：

```
手机 / 其他电脑 / PDA
      │  https://192.168.1.176  （信任 JJX CA 根证书后绿锁）
      ▼
Docker 容器 jjx-nginx（nginx:alpine，端口 80 + 443）
      │  • 80 端口 → 301 强制跳 HTTPS
      │  • 443 端口：server.crt + server.key（CN=192.168.1.176，SAN 含 IP）
      ▼  proxy_pass http://host.docker.internal:3000
vite dev :3000（前端；其自身 proxy 再转发 /api → 后端 8080）
```

- 证书有效期：10 年（2026-09-01 ~ 2036-08-29），免频繁续期
- 所有客户端设备只需**安装一次 ca.crt（根证书）**，之后所有用该 CA 签发的服务证书都受信任

## 3. 当前部署解剖（JJX 开发机，复刻参照）

### 3.1 文件位置

| 路径 | 内容 |
|---|---|
| `~/nginx-conf/nginx.conf` | nginx 配置（挂载进容器） |
| `~/nginx-certs/ca.crt` / `ca.key` | 自建根 CA（subject: `C=CN, O=JJX-ERP, CN=JJX CA`） |
| `~/nginx-certs/server.crt` / `server.key` | 服务器证书（CN=192.168.1.176，由 JJX CA 签发） |
| `~/nginx-certs/server.csr` | 签发请求（可留档） |
| `~/nginx-certs/san.cnf` | SAN 扩展配置（IP 必须写这里） |
| `~/nginx-certs/ca.srl` | CA 序列号文件 |

### 3.2 Docker 容器

```bash
docker ps  # jjx-nginx: nginx:alpine, 0.0.0.0:80->80, 0.0.0.0:443->443
# 挂载：
#   ~/nginx-conf/nginx.conf -> /etc/nginx/nginx.conf:ro
#   ~/nginx-certs           -> /etc/nginx/certs:ro
# 容器内可经 host.docker.internal 访问宿主机（--add-host host.docker.internal:host-gateway）
```

### 3.3 nginx.conf（现用全文，注释为关键点）

```nginx
# JJX ERP 内网 HTTPS 反向代理（docker nginx:alpine）
# 443 HTTPS → 反代 http://host.docker.internal:3000 (vite dev)
# 证书挂载自 ~/nginx-certs

worker_processes 1;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    sendfile on;

    # HTTP 80 → 强制跳 HTTPS
    server {
        listen 80;
        server_name 192.168.1.176 localhost;
        return 301 https://$host$request_uri;
    }

    # HTTPS 主服务
    server {
        listen 443 ssl;
        server_name 192.168.1.176 localhost;

        ssl_certificate     /etc/nginx/certs/server.crt;
        ssl_certificate_key /etc/nginx/certs/server.key;
        ssl_protocols       TLSv1.2 TLSv1.3;
        ssl_ciphers         HIGH:!aNULL:!MD5;

        # WebSocket 升级（vite HMR 需要）
        location / {
            proxy_pass http://host.docker.internal:3000;
            # vite dev server 校验 Host，必须改写成它认可的主机名
            proxy_set_header Host localhost:3000;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
        }
    }
}
```

## 4. 复刻步骤（新电脑/新环境）

### 4.1 生成证书（IP 换成目标机器局域网 IP）

```bash
mkdir -p ~/nginx-certs && cd ~/nginx-certs

# ① 根 CA（10 年）
openssl req -x509 -newkey rsa:2048 -days 3650 -nodes \
  -keyout ca.key -out ca.crt -subj "/C=CN/O=YOUR-ERP/CN=YOUR CA"

# ② 服务器密钥 + CSR（CN 用目标 IP）
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr \
  -subj "/C=CN/O=YOUR-ERP/CN=192.168.x.x"

# ③ SAN 扩展文件（IP 必须与目标机器一致！）
cat > san.cnf <<'EOF'
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=IP:192.168.x.x,DNS:localhost,IP:127.0.0.1
EOF

# ④ 用 CA 签发服务器证书（10 年）
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 3650 -extfile san.cnf

# 验证
openssl x509 -in server.crt -noout -subject -ext subjectAltName
```

### 4.2 编写 nginx.conf 并启动容器

nginx.conf 直接复用第 3.3 节（改 `server_name` 与 SAN IP 一致即可；proxy_pass 目标按实际服务端口调整）：

```bash
mkdir -p ~/nginx-conf
# 将上方 nginx.conf 保存为 ~/nginx-conf/nginx.conf

docker run -d --name jjx-nginx --restart=always \
  -p 80:80 -p 443:443 \
  -v ~/nginx-conf/nginx.conf:/etc/nginx/nginx.conf:ro \
  -v ~/nginx-certs:/etc/nginx/certs:ro \
  --add-host host.docker.internal:host-gateway \
  nginx:alpine

# 验证
curl -k -I https://127.0.0.1/          # 期望 200
curl -s -I http://127.0.0.1/ | head -1 # 期望 301
```

### 4.3 客户端信任根证书（每台设备装一次 ca.crt）

| 设备 | 操作 |
|---|---|
| Windows | 双击 `ca.crt` → 安装证书 → 本地计算机 → 受信任的根证书颁发机构 |
| Android | 设置 → 安全 → 加密与凭据 → 安装 CA 证书（或浏览器下载后按系统引导） |
| iPhone/iPad | 用 Safari 下载 ca.crt → 设置 → 已下载描述文件 → 安装；再到 设置→通用→关于本机→证书信任设置 打开完全信任 |
| 其他电脑 | 同 Windows |

装完后浏览器访问 `https://192.168.1.176` 显示绿锁；摄像头扫码即可正常调用。

## 5. 注意事项 / 排障

1. **IP 变了证书就失效**：服务器证书 SAN 绑定的是 IP，换网络/换机器需按 4.1 重新签发
2. **SAN 必须含目标 IP**：漏了 SAN 浏览器会报 `NET::ERR_CERT_COMMON_NAME_INVALID`
3. **客户端装完根证书要重启浏览器**（或清除该站缓存）才生效
4. **端口占用**：80/443 被占用时 `docker run` 会失败，先 `ss -tlnp | grep -E ':(80|443)'` 排查
5. **vite dev 的 Host 校验**：proxy_set_header Host 必须与 dev server 认可的主机一致（默认 localhost:端口），否则 403/400
6. **WebSocket**：HMR/推送需要 upgrade 头，别删第 3.3 节那段
7. 排障好工具：`curl --noproxy '*' -k -v https://IP/` 直连看证书链路（注意 WSL/代理环境 curl 可能走系统代理产生误导，务必加 `--noproxy '*'`）
8. 本方案 nginx 只反代前端 3000；后端 /api 由 vite proxy 转发。若服务改端口，同步改 proxy_pass

## 6. 复刻速查

```bash
# 一句话流程
gen certs (IP) -> save nginx.conf -> docker run -> 每台设备装 ca.crt -> https://IP 访问
```
