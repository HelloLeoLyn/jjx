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
