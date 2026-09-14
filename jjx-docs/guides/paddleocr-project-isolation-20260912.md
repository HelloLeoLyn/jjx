# PaddleOCR 项目隔离启动与运维说明

## 适用范围

本文只针对 JJX ERP 的历史档案录入 OCR 服务。每个项目必须独立使用自己的服务目录、Python 虚拟环境、模型缓存、上传根目录、端口和 systemd 服务名，不共享其他项目的 OCR 进程或缓存。

## JJX ERP 目录约定

```text
项目根目录：/home/administrator/jjx
服务目录：  tools/paddleocr-service
虚拟环境：  tools/paddleocr-service/.venv
模型缓存：  tools/paddleocr-service/.model-cache
监听地址：  127.0.0.1:8866
```

Java 配置使用绝对上传根目录 `/home/administrator/jjx/upload`，可通过 `JJX_UPLOAD_ROOT` 覆盖。OCR 模型缓存使用 `PADDLE_PDX_CACHE_HOME`，不可指向其他项目目录。

## 开发启动

```bash
cd /home/administrator/jjx/tools/paddleocr-service
./start.sh
```

可用环境变量：

```bash
JJX_OCR_HOST=127.0.0.1 JJX_OCR_PORT=8866 ./start.sh
```

检查服务：

```bash
curl http://127.0.0.1:8866/health
```

## 生产自启动

将 `deploy/systemd/jjx-paddleocr.service` 安装为本项目专用服务：

```bash
sudo cp /home/administrator/jjx/deploy/systemd/jjx-paddleocr.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now jjx-paddleocr.service
systemctl status jjx-paddleocr.service
```

其他项目应复制 unit 后修改服务名、WorkingDirectory、ExecStart、缓存目录和端口，不能直接共用 `jjx-paddleocr.service`。

## 故障排查

1. 页面显示“OCR：未启动”：检查 `systemctl status jjx-paddleocr` 和 `curl http://127.0.0.1:8866/health`。
2. 端口冲突：确认 `JJX_OCR_PORT` 与 Java 的 `engineering.archive.ocr-url` 端口一致。
3. 原图找不到：确认 Java 的 `JJX_UPLOAD_ROOT` 与部署实例一致，不要使用 `./upload`。
4. 模型下载/缓存异常：确认 `.model-cache` 属于当前项目且有写权限。
5. 多项目部署：每个项目使用独立端口和 systemd unit，禁止多个项目共用同一个 OCR 进程。
