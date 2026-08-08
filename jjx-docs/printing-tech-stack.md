# JJX ERP 打印技术方案文档

> 版本：v1.0
> 日期：2026-08-07
> 范围：当前系统前后端所有"打印/导出PDF"涉及的技术栈、组件、调用链路、待改进项

---

## 一、技术栈总览

| 层 | 技术 | 版本 | 用途 |
|---|---|---|---|
| 后端 PDF 生成 | OpenPDF（librepdf，原 iText 分支） | 1.3.30 | 生成单据 PDF |
| 后端字体 | 项目内嵌 Noto Sans CJK / 文泉驿微米黑（resources/fonts/） | - | 中文渲染（打包 jar 可用） |
| 后端模板配置 | sys_config 表（group=pdf_template）+ PdfTemplateConfig/PdfConfigLoader | - | 公司抬头/主题色等后台配置 |
| 前端 PDF 预览 | pdfjs-dist（Mozilla PDF.js） | ^6.2.108 | 网页内渲染 PDF 预览 |
| 前端打印组件 | PrintDialog（自研，基于 pdfjs） | - | PDF 预览 + 下载 + 打印 一体化 |
| 前端浏览器打印 | window.print() | - | HTML 直接打印 |

---

## 二、后端 PDF 生成链路

### 2.1 核心构建器：PdfDocBuilder

```
PdfDocBuilder.java（common/utils/pdf/）
├── PdfTemplateConfig.java    // 模板配置模型（公司名/主题色/签名栏/页脚）
├── PdfConfigLoader.java      // 从 sys_config 读取 pdf_template 组配置
└── PdfDocBuilder.java        // 链式构建：title/info/items/amounts/remark/signatures
```

**调用方式**（8 个单据统一）：
```java
PdfDocBuilder.create()
    .withConfig(pdfConfigLoader.load())   // 后台配置（可空，空则基础样式）
    .title("报  价  单")
    .info(infoMap)                        // 单据信息区（两对 label/value，4列）
    .items(headers, rows)                 // 明细表格（表头主题色/斑马纹）
    .amounts(rows)                        // 金额汇总（合计行主题色突出）
    .remark(text)                         // 备注
    .signatures("销售负责人", "客户确认", "日期")  // 签名区（用配置标题）
    .toBytes();
```

### 2.2 调用方（8 个单据导出）

| 模块 | 文件 | 接口 |
|---|---|---|
| 销售-报价单 | QuotationServiceImpl | exportPdf/exportExcel |
| 销售-订单 | OrderServiceImpl | exportPdf（2处） |
| 销售-发货单 | SalesDeliveryServiceImpl | exportPdf |
| 库存-出库单 | InventoryOutboundServiceImpl | exportPdf |
| 库存-入库单 | InventoryInboundServiceImpl | exportPdf |
| 采购-采购单 | PurchaseOrderServiceImpl | exportPdf |
| 生产-工单 | ProductionOrderServiceImpl | exportPdf |
| 生产-质检报告 | QualityInspectionServiceImpl | exportPdf |

### 2.3 模板配置（后台可配置）

**数据源**：sys_config 表 group=`pdf_template`（10 项）

| config_key | 说明 | 默认值 |
|---|---|---|
| company_name | 公司名称（抬头） | （空） |
| company_address | 公司地址 | （空） |
| company_phone | 联系电话 | （空） |
| company_email | 邮箱 | （空） |
| theme_color | 主题色（#RRGGBB） | #2B5AA7 商务蓝 |
| show_header | 显示公司抬头 1/0 | 1 |
| show_footer | 显示页脚公司名 1/0 | 1 |
| signature_label1/2/3 | 签名栏标题 | 销售负责人/客户确认/日期 |

**渲染效果**（配置开启时）：
- 公司抬头区（主题色大字 + 联系信息小字灰）
- 标题下主题色装饰线
- 信息区 label 浅色底
- 明细表头主题色底白字 + 斑马纹
- 合计行主题色底白字加粗
- 页脚公司名

**配置页**：系统管理 → 单据模板配置（views/system/pdfConfig/index.vue）

---

## 三、前端打印链路

### 3.1 统一打印组件：PrintDialog（推荐路径）

```
components/PrintDialog/index.vue
模式1 pdf-blob：后端 PDF Blob → pdfjs 渲染预览 → 下载 + 打印
模式2 插槽：插槽内容 → window.print() 打印 HTML
```

**工作流程（模式1，PDF 路径）**：
1. 前端调后端导出接口 → 拿 PDF Blob（responseType: 'blob'）
2. PrintDialog 打开 → pdfjs-dist 加载 PDF → canvas 逐页渲染预览
3. 用户操作：**下载**（saveAs）/ **打印**（iframe 承载 blob → contentWindow.print()）
4. 打印的就是后端生成的 PDF，**所见即所得**

**当前接入情况**：仅 `inventory/outbound/index.vue`（出库单导出 PDF 走此组件）

### 3.2 各页面当前打印方式（现状盘点）

| 页面 | 打印方式 | 导出PDF | 是否用 PrintDialog |
|---|---|---|---|
| 报价单（QuotationSendDialog） | **window.print() 打 HTML** ❌ | 直接下载 blob | ❌ |
| 报价单列表 | - | 直接下载 | ❌ |
| 销售订单 | - | 直接下载 | ❌ |
| 入库单 | - | 直接下载 | ❌ |
| **出库单** | ✅ PrintDialog | 预览+下载+打印 | ✅ |
| 采购单 | - | 直接下载 | ❌ |
| 质检报告 | - | 直接下载 | ❌ |
| 生产订单 | - | 直接下载 | ❌ |

### 3.3 两条打印路径对比

| 项 | HTML 直接打印（window.print） | PDF 路径（PrintDialog） |
|---|---|---|
| 渲染内容 | 前端 HTML + CSS | 后端生成 PDF |
| 字体 | 浏览器默认字体（随系统） | 内嵌中文字体（稳定） |
| 效果一致性 | 差（浏览器/系统不同则不同） | 好（同一 PDF） |
| 预览 | 无（直接进打印对话框） | 有（pdfjs 逐页预览） |
| 可配置模板 | 需改 CSS | 后台配置（公司/主题色） |
| 适用 | 简单快速打印 | 正式单据 |

---

## 四、问题与改进方向

### 4.1 现状问题

1. **报价单「打印」走 HTML 路径**——效果不稳定、不可预览、不跟 PDF 模板
2. **PrintDialog 只接了出库单**——其他 7 个单据还是"直接下载"或"HTML 打印"
3. **html2canvas/jspdf 未使用**——前端 HTML→PDF 转换方案未采用（当前无此依赖）
4. **页脚多页页号未实现**——当前页脚仅公司名，无"第X页/共Y页"（需 PageEvent 增强）

### 4.2 改进方向（待确认后实施）

**方向 A：统一接入 PrintDialog（推荐）**
- 报价/订单/入库/采购/质检等 7 个单据「打印/导出PDF」统一改为：后端生成 PDF → PrintDialog 预览 → 打印/下载
- 配合后台模板配置，实现"配置 → 预览 → 打印"闭环，所见即所得
- 改动点：各页面 handleExportPdf 改为打开 PrintDialog（模式1），去掉 window.print()

**方向 B：PDF 模板继续增强**
- 页脚页号（PageEvent）
- LOGO 图片上传（配置加 logo 字段）
- 多页分页优化、列宽定制

**方向 C：配置页预览闭环（可视化调试）**
- 配置页加「预览」按钮：读当前配置 + 示例数据 → 后端生成示例 PDF → iframe 内嵌显示
- 改配置 → 点预览 → 立刻看效果，无需重启/下载

---

## 五、关键技术备注

- **OpenPDF 中文**：必须用内嵌字体（resources/fonts/），服务器无中文字体也能渲染
- **pdfjs-dist v6**：ESM 加载，worker 需 `new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url)`
- **iframe 打印**：PDF blob 转 objectURL 后 iframe 承载，调 contentWindow.print() 打印 PDF
- **sys_config 实时生效**：每次导出 PDF 都重新读库，后台改配置即时生效，无需重启

---

## 六、相关文件索引

**后端**：
- `jjx-server/.../common/utils/pdf/PdfDocBuilder.java`
- `jjx-server/.../common/utils/pdf/PdfTemplateConfig.java`
- `jjx-server/.../common/utils/pdf/PdfConfigLoader.java`
- `jjx-server/.../system/service/SysConfigService.java`
- `jjx-server/.../system/controller/SysConfigController.java`

**前端**：
- `jjx-web/src/components/PrintDialog/index.vue`
- `jjx-web/src/views/system/pdfConfig/index.vue`
- `jjx-web/src/api/system/sysConfig.ts`
- 各单据页面 handleExportPdf / handlePrint

**数据**：
- `sys_config` 表（group=pdf_template）
- `sys_menu` 表（单据模板配置菜单 249）
