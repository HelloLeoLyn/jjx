# JJX 本地 PaddleOCR 服务

用于 `dev-20260911-004` 工程管理历史档案录入。服务只在本机处理图片，不调用外部接口。

## 安装与启动

```bash
cd /home/administrator/jjx/tools/paddleocr-service
python3.11 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8866
```

首次启动会下载中文 OCR 模型。健康检查：`curl http://127.0.0.1:8866/health`。

当前解析器仅支持 JJX“产品作业规范”固定版式 JPG/PNG。不同版式应新增模板解析器，不能套用本模板坐标。
## 当前模板工序网格

默认固定版式为：上部分 14 格；下部分面板 14 格、下线 14 格、上线 6 格。中间产品结构图按固定位置处理，不参与工序图标切分。若上下区域整体偏移，应调整区域坐标，不要改变单格顺序。
