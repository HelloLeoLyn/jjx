# 物料分类与属性只读试算（阶段一）

任务码：dev-20261024-008「重算分类与属性回填」
生成时间：2026-10-24T11:24:42+08:00
状态：阶段一输出，任务保持进行中；待 007 后、用户确认试算结果再应用。

## 背景与依据

目标模型见 [定稿](material-master-target-model-dev-20261024-003.md) §1/§3/§9。当前定稿 §8 是导入，未含卡面所说优先级；本次规则顺序直接采用用户本轮明确指令。
仅 SELECT inventory_material 的 material_code/material_name/specification/size/remark，全量读取，不按旧状态过滤；不读旧分类、旧 material_grade 或 material_type，不依赖 007 新列。
唯一事实取 remark 的【原名称】【原规格】；原名称缺失才回退当前名称，标 low。任一原始标记缺失亦标低置信；原规格空串保留，缺失不回退当前规格/尺寸。当前规格/尺寸仅供 Excel 对照。
R13 仅使用原始文本显式“类型:F/S”或“成品/半成品”声明，F→FINISHED、S→SEMI；不读旧 material_type，不用冻结编码反推分类，名称中的 F150/S200 不视为类型。没有明确声明时保持未归类。
R03 在用户“胶粘类”关键词基础上含字面“胶带”；PMMA 含中文同义词“压克力/亚克力”。未命中绝不塞入 OTHER；零条分类保持展示。

## 现状证据与分布

快照总数 1607；规则命中 1041；未归类 566；命中率 64.78%。
五列排序快照 SHA-256：`af15c9cbfb616f6b02f70beaa615fc75b62d17ddd75912eae9a187d27ee4843a`。

| 目标分类码 | 名称 | 条数 |
| --- | --- | --- |
| RAW_BASE_PC | PC膜/片 | 165 |
| RAW_BASE_PET | PET膜/片 | 194 |
| RAW_BASE_PVC | PVC | 7 |
| RAW_BASE_PMMA | PMMA/压克力 | 32 |
| RAW_BASE_OTHER | 其他基材 | 0 |
| RAW_AUX_PROTECT | 保护膜 | 116 |
| RAW_AUX_RELEASE | 离型纸/膜 | 78 |
| RAW_AUX_TAPE | 胶带/胶粘 | 205 |
| RAW_AUX_FOAM | 泡棉/硅胶 | 24 |
| RAW_AUX_FOIL | 金属箔/银龙 | 11 |
| RAW_AUX_OPTICAL | 光学膜 | 9 |
| RAW_AUX_ELECTRONIC | 电子料 | 200 |
| RAW_AUX_OTHER | 其他辅材 | 0 |
| RAW_INK | 油墨 | 0 |
| SEMI | 半成品 | 0 |
| FINISHED | 成品 | 0 |
| 未归类 | 空目标分类码 | 566 |

## 规则命中率与来源

| 规则 | 目标 | 关键词/判据 | 首命中数 | 占总数 |
| --- | --- | --- | --- | --- |
| R01 | RAW_AUX_PROTECT | 保护膜 | 116 | 7.22% |
| R02 | RAW_AUX_RELEASE | 离型 | 78 | 4.85% |
| R03 | RAW_AUX_TAPE | (?<![A-Z0-9.])3M(?!M)\|TESA\|VHB\|双面胶\|胶带\|OCA | 205 | 12.76% |
| R04 | RAW_AUX_FOAM | 泡棉\|(?<![A-Z])EVA(?![A-Z])\|硅胶 | 24 | 1.49% |
| R05 | RAW_AUX_FOIL | 铝箔\|铜箔\|银龙 | 11 | 0.68% |
| R06 | RAW_AUX_OPTICAL | 扩散\|防窥\|光学膜 | 9 | 0.56% |
| R07 | RAW_AUX_ELECTRONIC | 连接器\|HOUSING\|(?<![A-Z])PIN(?![A-Z])\|(?<![A-Z])LED(?![A-Z])\|电阻\|电容\|二极管\|弹片 | 200 | 12.45% |
| R08 | RAW_INK | 油墨 | 0 | 0.00% |
| R09 | RAW_BASE_PC | (?<![A-Z])PC(?![A-Z]) | 165 | 10.27% |
| R10 | RAW_BASE_PET | (?<![A-Z])PET(?![A-Z]) | 194 | 12.07% |
| R11 | RAW_BASE_PVC | (?<![A-Z])PVC(?![A-Z]) | 7 | 0.44% |
| R12 | RAW_BASE_PMMA | (?<![A-Z])PMMA(?![A-Z])\|压克力\|亚克力 | 32 | 1.99% |
| R13 | FINISHED/SEMI | 原始文本显式类型 F/S 或成品/半成品 | 0 | 0.00% |
| R14 | 未归类 | 所有规则未命中 | 566 | 35.22% |

优先级跨原名称/原规格整体执行；多分类命中保留全部证据，选择编号最小规则并标 medium，保护膜始终最高。high 仅表示明确名称关键词命中，不等于人工确认。

| confidence | 条数 |
| --- | --- |
| high | 852 |
| medium | 188 |
| low | 1 |
| 未归类 | 566 |

| 分类依据来源 | 条数 |
| --- | --- |
| 原名称 | 872 |
| 原名称+原规格 | 44 |
| 原规格 | 124 |
| 当前名称 | 1 |
| 无 | 566 |

## 未归类与人工确认规模

| 未归类原因 | 条数 |
| --- | --- |
| 仅型号/规格代号，缺少可判分类的关键词 | 388 |
| 名称/规格未命中已授权分类规则 | 9 |
| 有用途/材质描述，但不在本轮已授权关键词规则内 | 169 |

分类待人工确认全部 1607 条（自动命中不等于人工确认）；其中优先复核并集 757 条。
优先复核口径：未归类 566；medium 188；low 1；属性冲突 3（分组可重叠）。
原始标记/原名称缺失 1；多分类规则命中 64。候选只为人工分流，不作为分类结果。
未归类 Excel：`/home/administrator/物料未归类清单_20261024.xlsx`，仅业务留档，不入 git。

## 属性试算口径与覆盖

属性仅从同一原始事实/标记后的当前名称提取，并逐字段输出 attribute_evidence；当前 specification/size 不参与推断。
厚度：显式“厚/厚度/克重”或明确膜片类名称开头的单位串/小数，保留原单位，不把 PET125 的 125 擅自转厚度；原规格纯数字按宽、二维数值按长×宽保留，缺单位不补单位。
材质取明确材质词，3M9469PC 等型号后缀不视为 PC 材质；颜色取明确颜色词；型号取“型号:”或名称中字母开头且含数字的串，保留明确 3M 产品型号，其他数字开头串歧义大留空；品牌仅取明确 3M/tesa 或“品牌:”，括号供应商不猜品牌。多候选不同值留空并列 attribute_conflicts；未抽出留空。

| 属性 | 非空 | 空值 | 冲突留空 |
| --- | --- | --- | --- |
| thickness | 344 | 1263 | 0 |
| width_size | 1194 | 413 | 0 |
| material | 546 | 1061 | 0 |
| color | 342 | 1265 | 1 |
| model | 999 | 608 | 2 |
| brand | 200 | 1407 | 0 |

## 每类样例

按物料编码排序等距抽取至多 10 条，末尾亦覆盖；不足 10 条全部列出，零条类明确无样例。原名称缺失以“（缺失）”显示，逐行输出保留实际依据来源。

### RAW_BASE_PC（PC膜/片，165 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000001 | 0.125中砂PC(尚昇) | RAW_BASE_PC | R09 |
| RM000020 | 0.25中砂PC-安迪PC35(暂放鑫信泰)金玛莱 | RAW_BASE_PC | R09 |
| RM000039 | 0.175PC透明PC-印刷级(地博) | RAW_BASE_PC | R09 |
| RM000058 | 0.375PC透明PC-印刷级(地博) | RAW_BASE_PC | R09 |
| RM000076 | 0.8mm透明PC(地博) | RAW_BASE_PC | R09 |
| RM000095 | 1.0PC透明-印刷级(尚昇) | RAW_BASE_PC | R09 |
| RM000113 | 2.0mm透明PC-印刷级(贯昌) | RAW_BASE_PC | R09 |
| RM000165 | 0.8mm 茶色PC（金地鑫） | RAW_BASE_PC | R09 |
| RM000185 | 0.38mm无卤阻燃砂黑色PC | RAW_BASE_PC | R09 |
| RM001605 | 0.254PC | RAW_BASE_PC | R09 |

### RAW_BASE_PET（PET膜/片，194 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX000856 | 25#油胶光白PET(TLT25白) | RAW_BASE_PET | R10 |
| RM000214 | 白色PET100# | RAW_BASE_PET | R10 |
| RM000236 | 黑色PET188#(永安） | RAW_BASE_PET | R10 |
| RM000260 | PET50# | RAW_BASE_PET | R10 |
| RM000282 | PET100# | RAW_BASE_PET | R10 |
| RM000303 | PET125# | RAW_BASE_PET | R10 |
| RM000325 | DK11PET125 | RAW_BASE_PET | R10 |
| RM000368 | PET125#双面复膜(成浩林） | RAW_BASE_PET | R10 |
| RM000390 | PET188#双面复膜(CHL） | RAW_BASE_PET | R10 |
| RM001623 | （缺失） | RAW_BASE_PET | R10 |

### RAW_BASE_PVC（PVC，7 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000189 | 0.2哑黑PVC | RAW_BASE_PVC | R11 |
| RM000190 | 0.3PVC (哑面粗砂） | RAW_BASE_PVC | R11 |
| RM000191 | 0.5白色PVC(粗砂） | RAW_BASE_PVC | R11 |
| RM000192 | 0.8透明PVC | RAW_BASE_PVC | R11 |
| RM000193 | 1.0透明PVC | RAW_BASE_PVC | R11 |
| RM000194 | 1.0哑白PVC | RAW_BASE_PVC | R11 |
| RM000195 | 100#透明PVC | RAW_BASE_PVC | R11 |

### RAW_BASE_PMMA（PMMA/压克力，32 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000125 | 0.5mmYG透明双面压克力板 | RAW_BASE_PMMA | R12 |
| RM000128 | 1.0mm透明压克力板（贯昌） | RAW_BASE_PMMA | R12 |
| RM000132 | 1.5mmYSB透明双面压克力板 | RAW_BASE_PMMA | R12 |
| RM000135 | 1.5mmHSY透明双面压克力板 | RAW_BASE_PMMA | R12 |
| RM000139 | 2.0mm透明压克力(贯昌) | RAW_BASE_PMMA | R12 |
| RM000142 | 2.0mm东宇黑色压克力板 (贯昌） | RAW_BASE_PMMA | R12 |
| RM000146 | 3.0mm透明压克力(贯昌) | RAW_BASE_PMMA | R12 |
| RM000149 | 3.0mm透明压克力(贯昌) | RAW_BASE_PMMA | R12 |
| RM000153 | 4.0mm透明压克力板 | RAW_BASE_PMMA | R12 |
| RM000156 | 6.0mm透明压克力板 | RAW_BASE_PMMA | R12 |

### RAW_BASE_OTHER（其他基材，0 条）

无样例。

### RAW_AUX_PROTECT（保护膜，116 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000460 | 15C PE保护膜(宏裕) | RAW_AUX_PROTECT | R01 |
| RM000473 | T4901PET保护膜(华安) | RAW_AUX_PROTECT | R01 |
| RM000486 | T4901PET保护膜(华安) | RAW_AUX_PROTECT | R01 |
| RM000498 | T4901PET保护膜(华安) | RAW_AUX_PROTECT | R01 |
| RM000521 | 40J低粘耐高温PE保护膜（鸿发） | RAW_AUX_PROTECT | R01 |
| RM000534 | 020#进口PE保护膜（鸿发） | RAW_AUX_PROTECT | R01 |
| RM000547 | 020#进口PE保护膜（鸿发） | RAW_AUX_PROTECT | R01 |
| RM000559 | P8181中高粘PET保护膜(宏裕) | RAW_AUX_PROTECT | R01 |
| RM000572 | T5060H4中高粘保护膜(华安) | RAW_AUX_PROTECT | R01 |
| RM001607 | WG008网格PET保护膜 | RAW_AUX_PROTECT | R01 |

### RAW_AUX_RELEASE（离型纸/膜，78 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000344 | 50#单面离型膜 | RAW_AUX_RELEASE | R02 |
| RM000353 | 50#单面离型膜 | RAW_AUX_RELEASE | R02 |
| RM000361 | 100#单面离型膜 | RAW_AUX_RELEASE | R02 |
| RM000408 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM000416 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM000425 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM000433 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM000442 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM000451 | 90g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |
| RM001001 | 120g进口黄色离型纸 | RAW_AUX_RELEASE | R02 |

### RAW_AUX_TAPE（胶带/胶粘，205 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX000595 | 3M5604  VHB(閩盛) | RAW_AUX_TAPE | R03 |
| AUX000619 | 3M55236 (閩盛) | RAW_AUX_TAPE | R03 |
| AUX000641 | 3M55258D(万绰) | RAW_AUX_TAPE | R03 |
| AUX000664 | 3M467(万绰) | RAW_AUX_TAPE | R03 |
| AUX000687 | 3M468(万绰) | RAW_AUX_TAPE | R03 |
| AUX000709 | 3M9080A(万绰) | RAW_AUX_TAPE | R03 |
| AUX000732 | 3M9495LE(万绰) | RAW_AUX_TAPE | R03 |
| AUX000755 | 3M9495MP(万绰） | RAW_AUX_TAPE | R03 |
| AUX000777 | 3M9471LE(伟壹昊） | RAW_AUX_TAPE | R03 |
| RM000995 | KN-6100 / OCA 光学胶 样品 | RAW_AUX_TAPE | R03 |

### RAW_AUX_FOAM（泡棉/硅胶，24 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX001548 | 0.3mm黑色泡棉+胶=0.36 | RAW_AUX_FOAM | R04 |
| AUX001554 | 2.0mm E-4308泡棉 | RAW_AUX_FOAM | R04 |
| AUX001556 | 2.0mm黑色泡棉EVA+DSKH2 | RAW_AUX_FOAM | R04 |
| RM000511 | 6+5透明硅胶膜（鑫佑鑫） | RAW_AUX_FOAM | R04 |
| RM000513 | 6+5透明硅胶膜（鑫佑鑫） | RAW_AUX_FOAM | R04 |
| RM000516 | 6+5透明硅胶膜（鑫佑鑫） | RAW_AUX_FOAM | R04 |
| RM000518 | 6+5透明硅胶膜（鑫佑鑫） | RAW_AUX_FOAM | R04 |
| RM000952 | 70度黑色磨砂硅胶(1.0)+T4000 | RAW_AUX_FOAM | R04 |
| RM000954 | 70度黑色磨砂硅胶(2.0)-无胶 | RAW_AUX_FOAM | R04 |
| RM000957 | 0.6白色透明硅胶， | RAW_AUX_FOAM | R04 |

### RAW_AUX_FOIL（金属箔/银龙，11 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| RM000964 | 80#自粘铝箔（富华） | RAW_AUX_FOIL | R05 |
| RM000965 | 50#自粘铝箔（富华） | RAW_AUX_FOIL | R05 |
| RM000966 | 50#自粘铝箔（富华） | RAW_AUX_FOIL | R05 |
| RM000968 | 25#亚克力胶消银龙 | RAW_AUX_FOIL | R05 |
| RM000969 | 75#自粘铜箔 | RAW_AUX_FOIL | R05 |
| RM000971 | 100#亮银龙 | RAW_AUX_FOIL | R05 |
| RM000972 | 100#光银龙黄底纸（ 鸿盛） | RAW_AUX_FOIL | R05 |
| RM000974 | 0.05mm不带胶菲林复铝箔（宝佳盛） | RAW_AUX_FOIL | R05 |
| RM000976 | 50umAL(铝箔)+50umTLT(宇帆） | RAW_AUX_FOIL | R05 |
| RM000977 | 50umAL(铝箔)+50umTLT(宇帆） | RAW_AUX_FOIL | R05 |

### RAW_AUX_OPTICAL（光学膜，9 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX000978 | 扩散片125#白色(永安） | RAW_AUX_OPTICAL | R06 |
| AUX000979 | 扩散片125#白色(永安） | RAW_AUX_OPTICAL | R06 |
| AUX000980 | 扩散片125#白色(永安） | RAW_AUX_OPTICAL | R06 |
| AUX000981 | 扩散片125#白色(永安） | RAW_AUX_OPTICAL | R06 |
| AUX000982 | 扩散片125#白色(永安） | RAW_AUX_OPTICAL | R06 |
| AUX000983 | 扩散片DK-100#白色(ZQ客供） | RAW_AUX_OPTICAL | R06 |
| AUX000984 | 哑光YG100 扩散片(成浩林） | RAW_AUX_OPTICAL | R06 |
| AUX000985 | YG75 扩散片(联科） | RAW_AUX_OPTICAL | R06 |
| RM001566 | 防窥膜 | RAW_AUX_OPTICAL | R06 |

### RAW_AUX_ELECTRONIC（电子料，200 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX001002 | DN25.4-ODN-280-10C | RAW_AUX_ELECTRONIC | R07 |
| AUX001060 | 10-SDL-220 | RAW_AUX_ELECTRONIC | R07 |
| AUX001082 | 20-SNL-400 | RAW_AUX_ELECTRONIC | R07 |
| AUX001148 | OL-06 Housing-夏欣 | RAW_AUX_ELECTRONIC | R07 |
| AUX001170 | 2808H-19 连接器 | RAW_AUX_ELECTRONIC | R07 |
| AUX001193 | 2808H-02 | RAW_AUX_ELECTRONIC | R07 |
| AUX001216 | B-NH-BOI-4P | RAW_AUX_ELECTRONIC | R07 |
| AUX001238 | KB4603-HLD-04NJ | RAW_AUX_ELECTRONIC | R07 |
| AUX001376 | 0603  8K2电阻 | RAW_AUX_ELECTRONIC | R07 |
| AUX001400 | C0603X7R1C104KT000F 电容 | RAW_AUX_ELECTRONIC | R07 |

### RAW_AUX_OTHER（其他辅材，0 条）

无样例。

### RAW_INK（油墨，0 条）

无样例。

### SEMI（半成品，0 条）

无样例。

### FINISHED（成品，0 条）

无样例。

### 未归类（未归类，566 条）

| 编码 | 原名称 | 目标分类 | 命中规则 |
| --- | --- | --- | --- |
| AUX000597 | AV04B ( 鑫旺达  ) | 未归类 | R14 |
| AUX000848 | DP-188TA（SDT188） | 未归类 | R14 |
| AUX000928 | NO.5015(伟壹昊) | 未归类 | R14 |
| AUX001089 | 10-SNL-280(AU) | 未归类 | R14 |
| AUX001270 | HT-F170UY(0805黄灯) | 未归类 | R14 |
| AUX001333 | 15-22SURUYC/S530-A3/TR8 | 未归类 | R14 |
| RM000505 | W-1S2540T-0  30-50g | 未归类 | R14 |
| RM001453 | EBG250(SH) | 未归类 | R14 |
| RM001517 | F200-PY3M(XXT) | 未归类 | R14 |
| RM001603 | F200 | 未归类 | R14 |

## 验证与待确认点

复现（工作目录 /home/administrator/jjx；凭据通过 MYSQL_PWD 环境变量传入，不固化到脚本）：

```bash
python3 scripts/material-reclassify-dryrun.py --self-test
python3 scripts/material-reclassify-dryrun.py --user jjx_ro --host 127.0.0.1 \
  --report jjx-docs/history/material-reclassify-dryrun-dev-20261024-008.md \
  --excel /home/administrator/物料未归类清单_20261024.xlsx > /tmp/material-reclassify-dryrun.jsonl
cd jjx-web
npm run check:docs
```

真实库执行、反例与 Excel 回读结果见下节；尚未应用回填，不声称真实库回填事务/模板字段验收通过。
零条类别和未归类仅据原始文本规则统计，不能据此断言业务没有对应物料；待人工确认补充规则，不自动扩展分类。
待 007 后再核对模板、字典取值及必填属性，再经用户确认进入应用。阶段一不生成迁移，不写业务表，不运行服务生命周期操作。

## 本次执行证据（codex 自查，非独立验收）

使用 jjx_ro、固定 START TRANSACTION READ ONLY + 五列 SELECT + COMMIT 实际运行，退出码 0；共 1607 条。初始五列只读快照的离线结果与最终实时重跑的逐行分类结果用 cmp 对照一致。完整逐行结果经 stdout 输出并在 /tmp 临时对照，不入 git。

`python3 scripts/material-reclassify-dryrun.py --self-test`：47 个断言通过，包含 PET保护膜优先、0.175中砂面PC、当前规格/尺寸不得污染原始事实、F150 非成品类型、0.3mm 非 3M 品牌、型号后缀 PC 不猜材质等反例。

20 条原始文本样例逐条对照如下，目标分类全部符合表中预期；另对全量输出核对分类/来源/置信度必备信息，116 条含保护膜事实全部命中 R01，无落入基材。

| 编码 | 原名称 | 原规格 | 目标分类 | 命中规则 |
| --- | --- | --- | --- | --- |
| AUX000595 | 3M5604  VHB(閩盛) | 260 | RAW_AUX_TAPE | R03 |
| AUX000597 | AV04B ( 鑫旺达  ) | 235 | 未归类 | R14 |
| AUX000609 | 3M9469PC(万绰) | 140 | RAW_AUX_TAPE | R03 |
| AUX000786 | DP-025TA（SDT25) | 82 | 未归类 | R14 |
| AUX000856 | 25#油胶光白PET(TLT25白) | 90 | RAW_BASE_PET | R10 |
| AUX000978 | 扩散片125#白色(永安） | 130 | RAW_AUX_OPTICAL | R06 |
| AUX001002 | DN25.4-ODN-280-10C | 弹片 | RAW_AUX_ELECTRONIC | R07 |
| AUX001022 | HJ-8428FGN | 280g镀镍弹片 | RAW_AUX_ELECTRONIC | R07 |
| AUX001552 | 41-30045( 罗杰斯泡棉） | 1372*900 | RAW_AUX_FOAM | R04 |
| RM000001 | 0.125中砂PC(尚昇) | 470 | RAW_BASE_PC | R09 |
| RM000006 | 0.175中砂面PC-DB6842(地博） | 300*305 | RAW_BASE_PC | R09 |
| RM000125 | 0.5mmYG透明双面压克力板 | 280*210 | RAW_BASE_PMMA | R12 |
| RM000189 | 0.2哑黑PVC | 228*305 | RAW_BASE_PVC | R11 |
| RM000344 | 50#单面离型膜 | 215 | RAW_AUX_RELEASE | R02 |
| RM000460 | 15C PE保护膜(宏裕) | 145 | RAW_AUX_PROTECT | R01 |
| RM000964 | 80#自粘铝箔（富华） | 22 | RAW_AUX_FOIL | R05 |
| RM001482 | F150(AGK) | 350 | 未归类 | R14 |
| RM001577 | 0.3mm 透明PC 样品 （SS) | 1000 | RAW_BASE_PC | R09 |
| RM001607 | WG008网格PET保护膜 | 315 | RAW_AUX_PROTECT | R01 |
| RM001623 | （缺失，回退当前名称） |  | RAW_BASE_PET | R10 |

openpyxl 回读实际 Excel：566 条业务行、7 列，编码/当前名称/原名称/原规格/规格/尺寸/候选逐格与未归类结果相同；缺失原事实未伪造。额外用临时合成样例验证公式样文本保留文本类型、仓内 Excel 导出被拒。

`npm run check:docs`（jjx-web）：通过，history 264 / modules 4 / task-docs 54，存量基线缺 BOM 62、命名不合规 53，未扩基线。`bash scripts/check-model-baseline.sh`：通过，当前/基线均 136 张表，未批准新表 0。

开工 preflight 因 006 的 305 待执行迁移报红，本轮按用户只读阶段授权继续，不执行该迁移。mvn/vue-tsc 未跑（无 Java/前端改动）；业务写入、007 新字段、模板回填与真实库回填回滚未验证，属于后续应用阶段。

需要人工优先复核 757 条（未归类/medium/low/属性冲突并集），其中未归类 566；RAW_INK、SEMI、FINISHED 与两个 OTHER 叶子本轮零命中。建议先补齐纯型号与灯/光学胶等描述的人工归类规则，保持当前分类树，等 007 后由用户确认再应用。

## 阶段二：正式分类与属性回填（2026-10-24）

执行者：codex。用户明确授权本轮批量 UPDATE；执行完成后提交待审核，执行者自查不替代独立验收。
仅新增 `scripts/material-reclassify-apply.py` 并追加本报表；阶段一脚本、007/004 文件、备份未修改或暂存。沿用已有 INDEX 登记。

### 备份与应用护栏

用户手工备份：`jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1143.sql`，133 表，排除 hr_employee。
SHA-256：`c135680fa7dbf797dd881ece796eb801e21c559cde8df2d2191a9af17ae19332`。
备份中的物料结构尚无306新增列；独立逐条对照时，template_id/thickness/width_size 按306定义的初始NULL处理，其余业务字段直接与备份比较。未恢复备份、未创建新备份、未创建迁移、未启停服务。

脚本直接加载阶段一模块、调用 classify/summary；分类及厚宽/材质/颜色/型号/品牌提取不复制规则。五列排序摘要仍为 `af15c9cbfb616f6b02f70beaa615fc75b62d17ddd75912eae9a187d27ee4843a`；逐类数量、1041命中/566未归类均与阶段一相同。
先 dry-run 生成源数据/配置/目标三重摘要；apply 必须匹配计划。每批先锁定、复核分类/模板/字典，再 SELECT FOR UPDATE 逐列核对主键、编码、原事实与人工字段；每行UPDATE影响数必须1，写后全列核验才COMMIT。异常回滚当前批，不自动重试；已提交批次不自动回滚，修复后需重新 dry-run 再续跑。

写列限于分类、模板、属性及客供/样品标记，加 update_by/update_time 审计列；生成列只核对、不直接更新。material_type 不在写入白名单，分类不读取它；全行读取仅为防护和前后比较。任务开工前已核实 status=1/assignee_name=codex/start_time 非空。

### 属性口径

厚度、宽度写阶段一提取原样串（乘号沿用阶段一归一）。无单位数字不补单位，C/#/T/g不猜成mm。模板 thickness_mm 仅由明确mm/um/μm/µm/cm及中文同义单位换算，并校验范围、精度；无单位0.175写主列thickness=0.175，JSON不猜单位。
其他模板字段仅认原事实中明确的模板枚举标签/布尔标记；重叠标签认最长项，单选多值冲突留空、多选保留明确项；遵守visible_when。缺失字符串、数值、必填属性留空，不套默认值，不填OTHER，不把未知布尔补false。历史回填的必填缺口需人工补全后再走正常保存校验。

材质复用阶段一提取值，映射有效字典代码并限制在meta_options（硅胶→SILICONE、铝→AL、铜→CU、不锈钢→STEEL）；无依据/不在候选中留空。形态仅认原事实中的字面字典标签，多个形态冲突留空，不按模板默认值猜测。
attribute_json自定义键仅来自模板fields；另沿用既有MaterialMasterService保存契约的material材质镜像，供attr_meta生成列使用。color/brand经字典归一比较，model按原串；所有非空现值均保留，缺少提取值/字典候选时不填。
客供关键词为名称/备注中的“客供/客户供料/客户提供”，样品为“样品”；只置1。566条未归类不写分类、模板和属性，其中42条仅置标记。

### 分类分布对比

| 分类码 | 回填前 | 阶段一目标 | 回填后 |
| --- | --- | --- | --- |
| RAW_BASE_PC | 1 | 165 | 165 |
| RAW_BASE_PET | 0 | 194 | 194 |
| RAW_BASE_PVC | 0 | 7 | 7 |
| RAW_BASE_PMMA | 0 | 32 | 32 |
| RAW_BASE_OTHER | 0 | 0 | 0 |
| RAW_AUX_PROTECT | 0 | 116 | 116 |
| RAW_AUX_RELEASE | 0 | 78 | 78 |
| RAW_AUX_TAPE | 0 | 205 | 205 |
| RAW_AUX_FOAM | 0 | 24 | 24 |
| RAW_AUX_FOIL | 0 | 11 | 11 |
| RAW_AUX_OPTICAL | 0 | 9 | 9 |
| RAW_AUX_ELECTRONIC | 0 | 200 | 200 |
| RAW_AUX_OTHER | 0 | 0 | 0 |
| RAW_INK | 0 | 0 | 0 |
| SEMI | 0 | 0 | 0 |
| FINISHED | 0 | 0 | 0 |
| 未归类 | 1606 | 566 | 566 |

实际更新1083行：1041行分类属性＋42行未归类仅标记；6批200/200/200/200/200/83。客供25、样品27（全量标记，可重叠）。RM001623按规则由PC分类/材质改为PET，模板2；TRANSPARENT颜色、125型号原样保留，形态无事实依据留空。

### 属性填充率

| 指标 | 回填前 | 回填后 | 占全量1607 | 占已归类1041 |
| --- | --- | --- | --- | --- |
| JSON非NULL（含空对象） | 1 | 1041 | 64.78% | 100.00% |
| JSON有实际内容 | 0 | 762 | 47.42% | 73.20% |
| JSON含模板自定义字段（剔除材质镜像） | 0 | 499 | 31.05% | 47.93% |
| 厚度 | 0 | 344 | 21.41% | 33.05% |
| 宽度 | 0 | 840 | 52.27% | 80.69% |
| 材质 | 1 | 526 | 32.73% | 50.53% |
| 形态 | 1 | 334 | 20.78% | 32.08% |

模板绑定0→1041；template_id空值1607→566，仅属于未归类。JSON非NULL1041，其中762个有内容、279个空对象；499条有模板自定义属性。其余未知属性如实留空。

### 串行验证、真实退出码

凭据由MYSQL_PWD环境变量传入，工作目录 `/home/administrator/jjx`。

| 命令/核对 | 退出码 | 实际结果 |
| --- | --- | --- |
| `python3 scripts/material-reclassify-apply.py --dry-run` | 0 | 1041/566与阶段一逐类一致；计划1083行 |
| `python3 scripts/material-reclassify-apply.py --rollback-check` | 0 | 首批200条真实UPDATE后ROLLBACK；全量54列恢复原值 |
| `python3 scripts/material-reclassify-apply.py --apply` 第一次 | 0 | 六批1083行；全列核验通过 |
| `python3 scripts/material-reclassify-apply.py --apply` 第二次 | 0 | 更新0行、提交0批，数据和审计时间均未改动 |
| 下列校验SQL（mysql） | 0 | 分类空566/模板空566/错绑0/臆造0/模板外键0 |
| 备份逐条对照＋既有Excel编码集合（临时只读脚本） | 0 | material_type全1607条相等，566集合相等，435项冲突保留 |
| 脚本语法/SQL字面量/写列白名单/单位反例断言 | 0 | 175um可换算0.175mm；无单位0.175及125#不猜mm |

预应用排错记录：初次dry-run误按分类启用代码0过滤，退出1且未写库；对照真实库、StatusEnum.NORMAL=1修正。初次rollback-check漏投影生成列，退出1并回滚当前批；查库仍1607/分类空1606/模板空1607，补齐预期后重跑退出0。独立备份核对初次因备份没有306新增列退出1（只读）；按迁移初始NULL处理后退出0。失败记录不当作成功。

校验SQL原文：

```sql
START TRANSACTION READ ONLY;
SELECT COUNT(*) AS total,SUM(category_id IS NULL) AS category_null,
 SUM(template_id IS NULL) AS template_null,
 SUM(category_id IS NOT NULL AND template_id IS NULL) AS classified_template_missing,
 SUM(category_id IS NULL AND template_id IS NOT NULL) AS unclassified_template_present,
 SUM(attribute_json IS NOT NULL) AS json_present,
 SUM(COALESCE(JSON_LENGTH(attribute_json),0)>0) AS json_nonempty,
 SUM(NULLIF(thickness,'') IS NOT NULL) AS thickness_nonempty,
 SUM(NULLIF(width_size,'') IS NOT NULL) AS width_nonempty,
 SUM(NULLIF(material_meta,'') IS NOT NULL) AS material_meta_nonempty,
 SUM(NULLIF(material_form,'') IS NOT NULL) AS material_form_nonempty,
 SUM(is_customer_supplied) AS customer_supplied,SUM(is_sample) AS sample
FROM inventory_material;
SELECT c.category_code,COUNT(*) AS materials FROM inventory_material m
 LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
 GROUP BY c.category_code ORDER BY c.category_code;
SELECT COUNT(*) AS template_category_mismatch FROM inventory_material m
 JOIN inventory_attribute_template t ON t.template_id=m.template_id
 WHERE m.category_id<>t.category_id;
SELECT COUNT(*) AS unclassified_with_invented_values FROM inventory_material
 WHERE category_id IS NULL AND (template_id IS NOT NULL OR attribute_json IS NOT NULL
 OR NULLIF(thickness,'') IS NOT NULL OR NULLIF(width_size,'') IS NOT NULL
 OR NULLIF(material_meta,'') IS NOT NULL OR NULLIF(material_form,'') IS NOT NULL);
SELECT COUNT(*) AS unknown_template_keys FROM inventory_material m
 JOIN inventory_attribute_template t ON t.template_id=m.template_id
 JOIN JSON_TABLE(JSON_KEYS(m.attribute_json),'$[*]' COLUMNS (attr_key VARCHAR(50) PATH '$')) k
 WHERE k.attr_key<>'material' AND NOT JSON_CONTAINS(JSON_EXTRACT(t.attribute_schema,'$.fields[*].key'),JSON_QUOTE(k.attr_key));
SELECT material_type,COUNT(*) AS material_count FROM inventory_material GROUP BY material_type;
SELECT material_code,c.category_code,template_id,thickness,width_size,material_meta,material_form,color,model,brand,attribute_json
 FROM inventory_material m LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
 WHERE material_code IN ('RM000006','RM000473','RM001482','AUX000786','RM001623') ORDER BY material_code;
COMMIT;
```

独立核对结果：

```json
{
  "verified": true,
  "total": 1607,
  "unclassified_exact_set": 566,
  "unclassified_excel_exact_set": 566,
  "unclassified_classification_attribute_changes": 0,
  "preserved_existing": {
    "model": 1596,
    "brand": 764,
    "color": 349
  },
  "conflicts_preserved": 435,
  "material_type": {
    "A": 780,
    "R": 827
  },
  "before_type_sha": "cb8bad7a7884d3a83ece992f7822ff9156f06c99d1f5a1e50c6b9c64778d7470",
  "after_type_sha": "cb8bad7a7884d3a83ece992f7822ff9156f06c99d1f5a1e50c6b9c64778d7470",
  "flags_match_keywords": true,
  "mysql_exit_code": 0
}
```

原非空color349/model1596/brand764均逐条与用户备份相等；material_type仍为A780/R827，编码—类型排序JSON的SHA-256前后相同（见上），不按新分类改写旧类型。
反例：RM000473（T4901PET保护膜）落RAW_AUX_PROTECT/template6/PET；RM000006为PC、厚度0.175、宽度300×305；AUX000786（DP-025TA）与RM001482（F150）仍分类/模板/属性NULL，不用型号或冻结编码硬判。
566条未归类的分类、模板、厚宽、材质、形态、颜色、型号、品牌逐条与回填前相等；仅42条命中关键词置标记。
未归类清单：`/home/administrator/物料未归类清单_20261024.xlsx`（沿用阶段一，未改写、不入git）；openpyxl回读566行，编码集合与真实库category_id IS NULL完全相等。

### 人工字段冲突清单（全部保留现值）

435项、429条物料：color0/model242/brand193。区别于阶段一3条“提取候选自身冲突”；本清单是已有人工值与唯一提取值的差异，已逐项与应用后真实库核对。

| 物料编码 | 字段 | 保留现值 | 提取值（未覆盖） |
| --- | --- | --- | --- |
| AUX000595 | brand | 閩盛 | 3M |
| AUX000596 | brand | 鑫旺达 | 3M |
| AUX000598 | brand | 诚田佳 | 3M |
| AUX000599 | brand | 伟壹昊 | 3M |
| AUX000600 | brand | 鑫旺达 | 3M |
| AUX000601 | brand | 閩盛 | 3M |
| AUX000604 | brand | 万绰 | 3M |
| AUX000605 | brand | 万绰 | 3M |
| AUX000606 | brand | 万绰 | 3M |
| AUX000607 | brand | 万绰 | 3M |
| AUX000608 | brand | 鑫旺达 | 3M |
| AUX000609 | model | 3M9469 | 3M9469PC |
| AUX000609 | brand | 万绰 | 3M |
| AUX000610 | model | 3M9469 | 3M9469PC |
| AUX000610 | brand | 万绰 | 3M |
| AUX000611 | brand | 伟壹昊 | 3M |
| AUX000612 | brand | 伟壹昊 | 3M |
| AUX000613 | brand | 伟壹昊 | 3M |
| AUX000614 | brand | 伟壹昊 | 3M |
| AUX000615 | model | 3M9473 | 3M9473PC |
| AUX000615 | brand | 闽盛 | 3M |
| AUX000616 | model | 3M9473 | 3M9473PC |
| AUX000616 | brand | 鑫旺达 | 3M |
| AUX000617 | brand | 閩盛 | 3M |
| AUX000618 | brand | 閩盛 | 3M |
| AUX000619 | brand | 閩盛 | 3M |
| AUX000620 | brand | 万绰 | 3M |
| AUX000621 | brand | 万绰 | 3M |
| AUX000622 | brand | 万绰 | 3M |
| AUX000623 | brand | 万绰 | 3M |
| AUX000624 | brand | 万绰 | 3M |
| AUX000625 | brand | 万绰 | 3M |
| AUX000626 | brand | 万绰 | 3M |
| AUX000628 | brand | 鑫旺达 | 3M |
| AUX000629 | brand | 伟壹昊 | 3M |
| AUX000630 | brand | 伟壹昊 | 3M |
| AUX000631 | brand | 伟壹昊 | 3M |
| AUX000632 | brand | 鑫旺达 | 3M |
| AUX000633 | brand | 伟壹昊 | 3M |
| AUX000634 | brand | 鑫旺达 | 3M |
| AUX000635 | brand | 伟壹昊 | 3M |
| AUX000636 | brand | 伟壹昊 | 3M |
| AUX000637 | brand | 常疆 | 3M |
| AUX000638 | brand | 万绰 | 3M |
| AUX000639 | brand | 万绰 | 3M |
| AUX000640 | brand | 万绰 | 3M |
| AUX000641 | brand | 万绰 | 3M |
| AUX000642 | brand | 万绰 | 3M |
| AUX000643 | brand | 万绰 | 3M |
| AUX000644 | brand | 万绰 | 3M |
| AUX000645 | brand | 万绰 | 3M |
| AUX000646 | brand | 万绰 | 3M |
| AUX000647 | brand | 万绰 | 3M |
| AUX000648 | brand | 万绰 | 3M |
| AUX000649 | brand | 万绰 | 3M |
| AUX000650 | brand | 万绰 | 3M |
| AUX000651 | brand | 万绰 | 3M |
| AUX000652 | brand | 万绰 | 3M |
| AUX000653 | brand | 万绰 | 3M |
| AUX000654 | brand | 万绰 | 3M |
| AUX000655 | brand | 万绰 | 3M |
| AUX000656 | brand | 万绰 | 3M |
| AUX000657 | brand | 万绰 | 3M |
| AUX000658 | brand | 万绰 | 3M |
| AUX000659 | brand | 万绰 | 3M |
| AUX000660 | brand | 万绰 | 3M |
| AUX000661 | brand | 万绰 | 3M |
| AUX000662 | brand | 万绰 | 3M |
| AUX000663 | brand | 万绰 | 3M |
| AUX000664 | brand | 万绰 | 3M |
| AUX000665 | brand | 万绰 | 3M |
| AUX000666 | brand | 万绰 | 3M |
| AUX000667 | brand | 万绰 | 3M |
| AUX000668 | brand | 万绰 | 3M |
| AUX000669 | brand | 万绰 | 3M |
| AUX000670 | brand | 万绰 | 3M |
| AUX000671 | brand | 万绰 | 3M |
| AUX000672 | brand | 万绰 | 3M |
| AUX000673 | brand | 万绰 | 3M |
| AUX000674 | brand | 万绰 | 3M |
| AUX000675 | brand | 万绰 | 3M |
| AUX000676 | brand | 万绰 | 3M |
| AUX000677 | brand | 万绰 | 3M |
| AUX000678 | brand | 万绰 | 3M |
| AUX000679 | brand | 万绰 | 3M |
| AUX000680 | brand | 万绰 | 3M |
| AUX000681 | brand | 万绰 | 3M |
| AUX000682 | brand | 万绰 | 3M |
| AUX000683 | brand | 万绰 | 3M |
| AUX000684 | brand | 万绰 | 3M |
| AUX000685 | brand | 万绰 | 3M |
| AUX000686 | brand | 万绰 | 3M |
| AUX000687 | brand | 万绰 | 3M |
| AUX000688 | brand | 万绰 | 3M |
| AUX000689 | brand | 万绰 | 3M |
| AUX000690 | brand | 万绰 | 3M |
| AUX000691 | brand | 万绰 | 3M |
| AUX000692 | brand | 万绰 | 3M |
| AUX000693 | brand | 万绰 | 3M |
| AUX000694 | brand | 万绰 | 3M |
| AUX000695 | brand | 万绰 | 3M |
| AUX000696 | brand | 万绰 | 3M |
| AUX000697 | brand | 万绰 | 3M |
| AUX000698 | brand | 万绰 | 3M |
| AUX000699 | brand | 万绰 | 3M |
| AUX000700 | brand | 万绰 | 3M |
| AUX000701 | brand | 万绰 | 3M |
| AUX000702 | brand | 万绰 | 3M |
| AUX000703 | brand | 万绰 | 3M |
| AUX000704 | brand | 万绰 | 3M |
| AUX000705 | brand | 万绰 | 3M |
| AUX000706 | brand | 万绰 | 3M |
| AUX000707 | brand | 万绰 | 3M |
| AUX000708 | brand | 万绰 | 3M |
| AUX000709 | brand | 万绰 | 3M |
| AUX000710 | brand | 万绰 | 3M |
| AUX000711 | brand | 万绰 | 3M |
| AUX000712 | brand | 万绰 | 3M |
| AUX000713 | brand | 万绰 | 3M |
| AUX000714 | brand | 万绰 | 3M |
| AUX000715 | brand | 万绰 | 3M |
| AUX000716 | brand | 万绰 | 3M |
| AUX000717 | brand | 万绰 | 3M |
| AUX000718 | brand | 万绰 | 3M |
| AUX000719 | brand | 万绰 | 3M |
| AUX000720 | brand | 万绰 | 3M |
| AUX000721 | brand | 万绰 | 3M |
| AUX000722 | brand | 万绰 | 3M |
| AUX000723 | brand | 万绰 | 3M |
| AUX000724 | brand | 万绰 | 3M |
| AUX000725 | brand | 万绰 | 3M |
| AUX000726 | brand | 万绰 | 3M |
| AUX000727 | brand | 万绰 | 3M |
| AUX000728 | brand | 万绰 | 3M |
| AUX000729 | brand | 万绰 | 3M |
| AUX000730 | brand | 万绰 | 3M |
| AUX000731 | brand | 万绰 | 3M |
| AUX000732 | brand | 万绰 | 3M |
| AUX000733 | brand | 伟壹昊 | 3M |
| AUX000734 | brand | 伟壹昊 | 3M |
| AUX000735 | brand | 伟壹昊 | 3M |
| AUX000736 | brand | 伟壹昊 | 3M |
| AUX000737 | brand | 伟壹昊 | 3M |
| AUX000738 | brand | 万绰 | 3M |
| AUX000739 | brand | 万绰 | 3M |
| AUX000740 | brand | 万绰 | 3M |
| AUX000741 | brand | 万绰 | 3M |
| AUX000742 | brand | 万绰 | 3M |
| AUX000743 | brand | 万绰 | 3M |
| AUX000744 | brand | 万绰 | 3M |
| AUX000745 | brand | 万绰 | 3M |
| AUX000746 | brand | 万绰 | 3M |
| AUX000747 | brand | 万绰 | 3M |
| AUX000748 | brand | 万绰 | 3M |
| AUX000749 | brand | 万绰 | 3M |
| AUX000750 | brand | 万绰 | 3M |
| AUX000751 | brand | 万绰 | 3M |
| AUX000752 | brand | 万绰 | 3M |
| AUX000753 | brand | 万绰 | 3M |
| AUX000754 | brand | 万绰 | 3M |
| AUX000755 | brand | 万绰 | 3M |
| AUX000756 | brand | 万绰 | 3M |
| AUX000757 | brand | 万绰 | 3M |
| AUX000758 | brand | 万绰 | 3M |
| AUX000759 | brand | 万绰 | 3M |
| AUX000760 | brand | 鑫旺达 | 3M |
| AUX000761 | brand | 閩盛 | 3M |
| AUX000762 | brand | 伟壹昊 | 3M |
| AUX000763 | brand | 伟壹昊 | 3M |
| AUX000764 | brand | 伟壹昊 | 3M |
| AUX000765 | brand | 伟壹昊 | 3M |
| AUX000766 | brand | 伟壹昊 | 3M |
| AUX000767 | brand | 万绰 | 3M |
| AUX000768 | brand | 万绰 | 3M |
| AUX000769 | brand | 万绰 | 3M |
| AUX000770 | brand | 万绰 | 3M |
| AUX000772 | brand | 鑫旺达 | 3M |
| AUX000773 | model | 3M9485 | 3M9485PC |
| AUX000774 | model | 3M9485 | 3M9485PC |
| AUX000775 | brand | 閩盛 | 3M |
| AUX000776 | brand | 万绰 | 3M |
| AUX000777 | brand | 伟壹昊 | 3M |
| AUX000778 | brand | 伟壹昊 | 3M |
| AUX000779 | brand | 万绰 | 3M |
| AUX000780 | brand | 伟壹昊 | 3M |
| AUX000781 | brand | 万绰 | 3M |
| AUX000782 | brand | 万绰 | 3M |
| AUX000783 | brand | 万绰 | 3M |
| AUX000784 | brand | 万绰 | 3M |
| AUX000785 | brand | 万绰 | 3M |
| AUX000897 | model | DST-10 | DST-10/ |
| AUX000902 | brand | 閩盛 | tesa |
| AUX000907 | brand | 万绰 | 3M |
| AUX000908 | brand | 鑫旺达 | tesa |
| AUX000909 | model | 3M9415 | 3M9415PC |
| AUX000909 | brand | 万绰 | 3M |
| AUX001020 | model | HJ-8428FRN 2.5 | HJ-8428FRN |
| AUX001367 | model | J | R-07 |
| AUX001558 | model | 4701-30-20062 | 3M9495MP |
| AUX001558 | brand | 罗杰斯泡棉 | 3M |
| RM000006 | model | 0.175 | PC-DB6842 |
| RM000007 | model | 0.175 | PC-DB6842 |
| RM000008 | model | 0.175 | PC-DB6842 |
| RM000009 | model | 0.175 | PC-DB6842 |
| RM000010 | model | 0.175 | PC-DB6842 |
| RM000011 | model | 0.175 DB6842 | PC-DB6842 |
| RM000012 | model | 0.175 | PC35 |
| RM000014 | model | 0.175mm32 | PC32 |
| RM000015 | model | 0.175mm32 | PC32 |
| RM000018 | model | 0.25 | PC-DB6842 |
| RM000019 | model | 0.25 | PC-DB6842 |
| RM000020 | model | 0.25 | PC35 |
| RM000025 | model | 0.375 | PC-DB6842 |
| RM000026 | model | 0.38 | PC-835 |
| RM000028 | model | 0.5 | PC-DB6842 |
| RM000029 | model | 0.5 | PC-DB6842 |
| RM000030 | model | 0.5 | PC-DB6842 |
| RM000111 | model | 1.5 | DB6811 |
| RM000196 | model | 50# | PET50# |
| RM000197 | model | 50# | PET50# |
| RM000198 | model | 50# | PET50# |
| RM000199 | model | 50# | PET50# |
| RM000200 | model | 50# | PET50# |
| RM000201 | model | 50# | PET50# |
| RM000202 | model | 50# | PET50# |
| RM000203 | model | 50# | PET50# |
| RM000204 | model | 50# | PET50# |
| RM000205 | model | 50# | PET50# |
| RM000206 | model | 50# | PET50# |
| RM000207 | model | 50# | PET50# |
| RM000208 | model | 75# | PET75# |
| RM000209 | model | 75# | PET75# |
| RM000210 | model | 75# | PET75# |
| RM000211 | model | 75# | PET75# |
| RM000212 | model | 75# | PET75# |
| RM000213 | model | 100# | PET100# |
| RM000214 | model | 100# | PET100# |
| RM000215 | model | 100# | PET100# |
| RM000216 | model | 100# | PET100# |
| RM000217 | model | 100# | PET100# |
| RM000218 | model | 100# | PET100# |
| RM000219 | model | 100# | PET100# |
| RM000220 | model | 100# | PET100# |
| RM000221 | model | 100# | PET100# |
| RM000222 | model | 125# | PET125# |
| RM000223 | model | 125# | PET125# |
| RM000224 | model | 125# | PET125# |
| RM000225 | model | 188# | PET188# |
| RM000226 | model | 188# | PET188# |
| RM000227 | model | 250 | PET250 |
| RM000228 | model | 250 | PET250 |
| RM000229 | model | 250 | PET250 |
| RM000230 | model | 250 | PET250 |
| RM000231 | model | 350 FF-SS-12 | PET350 |
| RM000232 | model | 50# | PET50# |
| RM000233 | model | 125# | PET125# |
| RM000234 | model | 125# | PET125# |
| RM000235 | model | 188# | PET188# |
| RM000236 | model | 188# | PET188# |
| RM000237 | model | 188# | PET188# |
| RM000238 | model | 188# | PET188# |
| RM000239 | model | 188# | PET188# |
| RM000240 | model | 188# | PET188# |
| RM000241 | model | 188# | PET188# |
| RM000242 | model | 188# | PET188# |
| RM000243 | model | 188# | PET188# |
| RM000244 | model | 188# | PET188# |
| RM000245 | model | 250# | PET250# |
| RM000246 | model | 250# | PET250# |
| RM000247 | model | 250# | PET250# |
| RM000248 | model | 250# | PET250# |
| RM000249 | model | 250# | PET250# |
| RM000257 | model | 50# | PET50# |
| RM000258 | model | 50# | PET50# |
| RM000259 | model | 50# | PET50# |
| RM000260 | model | 50# | PET50# |
| RM000261 | model | 50# | PET50# |
| RM000262 | model | 50# | PET50# |
| RM000263 | model | 75# | PET75# |
| RM000264 | model | 75# | PET75# |
| RM000265 | model | 75# | PET75# |
| RM000266 | model | 100# | PET100# |
| RM000267 | model | 100# | PET100# |
| RM000268 | model | 100# | PET100# |
| RM000269 | model | 100# | PET100# |
| RM000270 | model | 100# | PET100# |
| RM000271 | model | 100# | PET100# |
| RM000272 | model | 100# | PET100# |
| RM000273 | model | 100# | PET100# |
| RM000274 | model | 100# | PET100# |
| RM000275 | model | 100# | PET100# |
| RM000276 | model | 100# | PET100# |
| RM000277 | model | 100# | PET100# |
| RM000278 | model | 100# | PET100# |
| RM000279 | model | 100# | PET100# |
| RM000280 | model | 100# | PET100# |
| RM000281 | model | 100# | PET100# |
| RM000282 | model | 100# | PET100# |
| RM000283 | model | 100# | PET100# |
| RM000284 | model | 100# | PET100# |
| RM000285 | model | 100# | PET100# |
| RM000286 | model | 125# | PET125# |
| RM000287 | model | 125# | PET125# |
| RM000288 | model | 125# | PET125# |
| RM000289 | model | 125# | PET125# |
| RM000290 | model | 125# | PET125# |
| RM000291 | model | 125# | PET125# |
| RM000292 | model | 125# | PET125# |
| RM000293 | model | 125# | PET125# |
| RM000294 | model | 125# | PET125# |
| RM000295 | model | 125# | PET125# |
| RM000296 | model | 125# | PET125# |
| RM000297 | model | 125# | PET125# |
| RM000298 | model | 125# | PET125# |
| RM000299 | model | 125# | PET125# |
| RM000300 | model | 125# | PET125# |
| RM000301 | model | 125# | PET125# |
| RM000302 | model | 125# | PET125# |
| RM000303 | model | 125# | PET125# |
| RM000304 | model | 125# | PET125# |
| RM000305 | model | 125# | PET125# |
| RM000306 | model | 125# | PET125# |
| RM000307 | model | 125# | PET125# |
| RM000308 | model | 125# | PET125# |
| RM000309 | model | 125# | PET125# |
| RM000310 | model | 125# | PET125# |
| RM000311 | model | 125# | PET125# |
| RM000312 | model | 125# | PET125# |
| RM000313 | model | 125# | PET125# |
| RM000314 | model | 125# | PET125# |
| RM000315 | model | 125# | PET125# |
| RM000316 | model | 125# | PET125# |
| RM000317 | model | 125# | PET125# |
| RM000318 | model | 125# | PET125# |
| RM000319 | model | 125# | PET125# |
| RM000320 | model | 125# | PET125# |
| RM000321 | model | 125# | PET125# |
| RM000322 | model | 125# | PET125# |
| RM000323 | model | 125# | PET125# |
| RM000324 | model | 125# | PET125# |
| RM000325 | model | DK11 | DK11PET125 |
| RM000326 | model | 125-PY2M | PET125-PY2M |
| RM000327 | model | 125-PY2M | PET125-PY2M |
| RM000328 | model | 125-PY2M | PET125-PY2M |
| RM000329 | model | 125-PY2M | PET125-PY2M |
| RM000330 | model | 125-PY2M | PET125-PY2M |
| RM000331 | model | 125-PY2M | PET125-PY2M |
| RM000332 | model | 188# | PET188# |
| RM000333 | model | 188# | PET188# |
| RM000334 | model | 188# | PET188# |
| RM000335 | model | 188# | PET188# |
| RM000336 | model | 188# | PET188# |
| RM000337 | model | 250# | PET250# |
| RM000338 | model | 250# | PET250# |
| RM000339 | model | 250# | PET250# |
| RM000340 | model | 250# | PET250# |
| RM000341 | model | 350# | PET350# |
| RM000342 | model | 350# | PET350# |
| RM000343 | model | 350# | PET350# |
| RM000366 | model | 188# | PET188# |
| RM000367 | model | 100# | PET100# |
| RM000368 | model | 125# | PET125# |
| RM000369 | model | 125# | PET125# |
| RM000370 | model | 125# | PET125# |
| RM000371 | model | 188 | PET188 |
| RM000372 | model | 188 | PET188 |
| RM000373 | model | 188 | PET188 |
| RM000374 | model | 188 | PET188 |
| RM000375 | model | 188 | PET188 |
| RM000376 | model | 188 | PET188 |
| RM000377 | model | 188 | PET188 |
| RM000378 | model | 188 | PET188 |
| RM000379 | model | 188 | PET188 |
| RM000380 | model | 188# | PET188# |
| RM000381 | model | 188 | PET188 |
| RM000382 | model | 188 | PET188 |
| RM000383 | model | 188 | PET188 |
| RM000384 | model | 188 | PET188 |
| RM000385 | model | 188 | PET188 |
| RM000386 | model | 188# | PET188# |
| RM000387 | model | 188 | PET188 |
| RM000388 | model | 188 | PET188 |
| RM000389 | model | 188 | PET188 |
| RM000390 | model | 188# | PET188# |
| RM000392 | model | 188# | PET188# |
| RM000393 | model | 250 | PET250 |
| RM000394 | model | 250 | PET250 |
| RM000395 | model | 250# | PET250# |
| RM000397 | model | 250# | PET250# |
| RM000398 | model | 250# | PET250# |
| RM000399 | model | 250# | PET250# |
| RM000452 | model | IYHG95-15 | IYHG95-15/90g |
| RM000453 | model | IYHG95-15 | IYHG95-15/90g |
| RM000473 | model | T4901 | T4901PET |
| RM000474 | model | T4901 | T4901PET |
| RM000475 | model | T4901 | T4901PET |
| RM000476 | model | T4901 | T4901PET |
| RM000477 | model | T4901 | T4901PET |
| RM000478 | model | T4901 | T4901PET |
| RM000479 | model | T4901 | T4901PET |
| RM000480 | model | T4901 | T4901PET |
| RM000481 | model | T4901 | T4901PET |
| RM000482 | model | T4901 | T4901PET |
| RM000483 | model | T4901 | T4901PET |
| RM000484 | model | T4901 | T4901PET |
| RM000485 | model | T4901 | T4901PET |
| RM000486 | model | T4901 | T4901PET |
| RM000487 | model | T4901 | T4901PET |
| RM000488 | model | T4901 | T4901PET |
| RM000489 | model | T4901 | T4901PET |
| RM000490 | model | T4901 | T4901PET |
| RM000491 | model | T4901 | T4901PET |
| RM000492 | model | T4901 | T4901PET |
| RM000493 | model | T4901 | T4901PET |
| RM000494 | model | T4901 | T4901PET |
| RM000495 | model | T4901 | T4901PET |
| RM000496 | model | T4901 | T4901PET |
| RM000497 | model | T4901 | T4901PET |
| RM000498 | model | T4901 | T4901PET |
| RM000499 | model | T4901 | T4901PET |
| RM000500 | model | T4901 | T4901PET |
| RM000951 | model | T4000 0.5 | T4000 |
| RM000952 | model | T4000 1.0 | T4000 |
| RM000953 | model | T4000 1.0 | T4000 |
| RM000967 | brand | 易碎 | tesa |
| RM000992 | brand | 万绰 | 3M |
| RM000993 | brand | 万绰 | 3M |
| RM000994 | brand | 万绰 | 3M |
| RM000999 | model | 125# | PET125# |
| RM001000 | model | 100# | PET100# |
| RM001570 | model | 188 | PET188 |
| RM001576 | model | -250 | PET-250 |
| RM001578 | model | -188 | PET-188 |
| RM001608 | model | 125 | PET125 |
| RM001623 | model | 125 | PET125 |

### 交付与待审核事项

仅交付应用脚本及本报表，不暂存backups/任何文件。mvn/vue-tsc未跑（没有Java/前端改动）。文档门禁、Git差异检查、表基线最终结果追加于下。
遗留：566条未归类待人工补证；435项人工字段差异保留待业务核对；缺失模板属性待人工补全，不臆造。

最终门禁（2026-10-24，codex）：`npm run check:docs`退出0（history264/modules4/task-docs54，原基线未扩）；`bash scripts/check-model-baseline.sh`退出0（当前136/基线136，未批准新表0）；`git diff --check`退出0。mvn/vue-tsc未跑。本次2个文件，backups/及其他任务在制内容不纳入提交。
