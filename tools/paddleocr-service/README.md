# JJX 本地 PaddleOCR 服务

用于工程管理历史档案本地识别（`dev-20260911-004`、`dev-20260912-013`）。服务只在本机处理图片，不调用外部接口。

## 安装与启动

```bash
cd /home/administrator/jjx/tools/paddleocr-service
python3.11 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8866
```

也可以使用项目专用脚本：

```bash
bash start.sh
bash restart.sh
bash stop.sh
```

脚本只匹配本项目 `.venv/bin/uvicorn app:app` 进程，不会停止其他项目的 OCR 服务。若由 systemd 管理，请使用 `systemctl restart/stop jjx-paddleocr`，不要同时运行手动脚本。

首次启动会下载中文 OCR 模型。健康检查：`curl http://127.0.0.1:8866/health`。

当前解析器仅支持 JJX“产品作业规范”固定版式 JPG/PNG。不同版式应新增模板解析器，不能套用本模板坐标。

识别结果只形成可编辑草稿，不直接生成产品、BOM 或工艺路线。原图由后端保留；材料明细、刀模位置、凹凸条件、三个作业流程分组及完整工序格切片由后端落到 `upload/engineering-archive/crops/<archiveId>/`。

## 当前模板工序网格

面板、上线、下线分别定位到各自“作业流程”列，再通过连续横向黑边框计算实际格数。只有无法取得至少两条有效边框时，才分别按面板 14 格、上线 6 格、下线 14 格兜底。每格保留完整图片；OCR 文本中含 `+` 时按顺序生成子工序草稿，无法识别的子工序文字保留为空，供人工修正。

每个工序格分别记录内容形态 `EMPTY/TEXT_ONLY/ICON_ONLY/MIXED/UNKNOWN` 和工序结构 `EMPTY/SINGLE/COMPOSITE/UNDECIDED`。自动判断不等于人工确认，工作台按“分组确认 → 工序分格 → 内容分类 → 复合拆分 → 工序匹配 → 草稿检查”保存确认状态。

## 验证

```bash
cd /home/administrator/jjx/tools/paddleocr-service
.venv/bin/python -m unittest discover -s tests -v
curl --noproxy '*' http://127.0.0.1:8866/health
```

## 清理 E2E 测试数据

在仓库根目录执行。默认只预览，必须显式传 `--yes` 才会清理：

```bash
bash scripts/clean-archive-ocr-data.sh
bash scripts/clean-archive-ocr-data.sh --yes --task dev-20260912-013
```

执行模式会先在 Git 仓库外的 `JJX_BACKUP_DIR`（默认同级 `jjx-backups/`）生成全库备份、相关表 guard 备份和本地图片压缩包。发现非草稿状态或下游业务引用时会拒绝清理。
