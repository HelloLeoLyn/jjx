# 数据模型设计

## 1. 核心表结构

### 1.1 看板模板

#### board_template — 看板模板定义

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 模板编码：`production` / `office` / `emergency` |
| name | VARCHAR(100) | NOT NULL | 模板名称：生产工单 / 办公室任务 / 紧急任务 |
| icon | VARCHAR(50) | DEFAULT NULL | 图标标识 |
| sort_order | INT | DEFAULT 0 | 排序 |
| status | TINYINT | DEFAULT 1 | 状态：0禁用 1启用 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

```sql
CREATE TABLE board_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '模板编码',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    icon VARCHAR(50) DEFAULT NULL COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板模板定义';
```

### 1.2 工序/列配置

#### board_column_def — 看板列定义

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| template_id | BIGINT | FK → board_template.id | 所属模板 |
| column_id | VARCHAR(50) | NOT NULL | 列标识（供前端引用） |
| label | VARCHAR(100) | NOT NULL | 列标题 |
| color | VARCHAR(20) | DEFAULT NULL | 颜色值（HEX） |
| filter_field | VARCHAR(50) | DEFAULT NULL | 筛选字段名 |
| filter_value | VARCHAR(100) | DEFAULT NULL | 筛选值 |
| max_cards | INT | DEFAULT 0 | 最大卡片数（0不限） |
| sort_order | INT | DEFAULT 0 | 列排序 |
| created_at | DATETIME | NOT NULL | 创建时间 |

```sql
CREATE TABLE board_column_def (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '所属模板',
    column_id VARCHAR(50) NOT NULL COMMENT '列标识',
    label VARCHAR(100) NOT NULL COMMENT '列标题',
    color VARCHAR(20) DEFAULT NULL COMMENT '颜色 HEX',
    filter_field VARCHAR(50) DEFAULT NULL COMMENT '筛选字段',
    filter_value VARCHAR(100) DEFAULT NULL COMMENT '筛选值',
    max_cards INT DEFAULT 0 COMMENT '上限 0不限',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template (template_id),
    CONSTRAINT fk_column_template FOREIGN KEY (template_id)
        REFERENCES board_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板列定义';
```

### 1.3 视图配置

#### board_view_def — 视图定义

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| template_id | BIGINT | FK → board_template.id | 所属模板 |
| view_id | VARCHAR(50) | NOT NULL | 视图标识 |
| name | VARCHAR(100) | NOT NULL | 视图名称 |
| group_by | VARCHAR(50) | NOT NULL | 分组字段名 |
| sort_order | INT | DEFAULT 0 | 排序 |
| created_at | DATETIME | NOT NULL | 创建时间 |

```sql
CREATE TABLE board_view_def (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    view_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    group_by VARCHAR(50) NOT NULL COMMENT '分组字段',
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template (template_id),
    CONSTRAINT fk_view_template FOREIGN KEY (template_id)
        REFERENCES board_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视图定义';
```

### 1.4 工单工序进度（核心）

#### work_order_process — 工单工序进度

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| work_order_id | VARCHAR(50) | NOT NULL | ERP 工单号 |
| product_name | VARCHAR(200) | DEFAULT NULL | 产品名称 |
| product_type | VARCHAR(50) | DEFAULT NULL | 产品类型编码 |
| quantity | INT | DEFAULT 0 | 工单数量 |
| customer | VARCHAR(100) | DEFAULT NULL | 客户名称 |
| template_id | BIGINT | FK → board_template.id | 工序模板 |
| current_step | INT | DEFAULT 1 | 当前工序序号 |
| total_steps | INT | NOT NULL | 总工序数 |
| status | VARCHAR(20) | NOT NULL | 工单状态 |
| priority | VARCHAR(20) | DEFAULT 'normal' | 优先级 |
| assignee | VARCHAR(50) | DEFAULT NULL | 负责人 |
| deadline | DATE | DEFAULT NULL | 截止日期 |
| version | INT | DEFAULT 1 | 乐观锁 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

```sql
CREATE TABLE work_order_process (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id VARCHAR(50) NOT NULL COMMENT 'ERP工单号',
    product_name VARCHAR(200) DEFAULT NULL COMMENT '产品名称',
    product_type VARCHAR(50) DEFAULT NULL COMMENT '产品类型编码',
    quantity INT DEFAULT 0 COMMENT '数量',
    customer VARCHAR(100) DEFAULT NULL COMMENT '客户',
    template_id BIGINT DEFAULT NULL COMMENT '工序模板',
    current_step INT DEFAULT 1 COMMENT '当前工序序号',
    total_steps INT NOT NULL COMMENT '总工序数',
    status VARCHAR(20) NOT NULL COMMENT '工单状态',
    priority VARCHAR(20) DEFAULT 'normal' COMMENT '优先级',
    assignee VARCHAR(50) DEFAULT NULL COMMENT '负责人',
    deadline DATE DEFAULT NULL COMMENT '截止日期',
    version INT DEFAULT 1 COMMENT '乐观锁',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_work_order (work_order_id),
    INDEX idx_status (status),
    INDEX idx_assignee (assignee),
    INDEX idx_deadline (deadline),
    INDEX idx_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单工序进度';
```

**status 枚举：**

| 值 | 说明 |
|----|------|
| `pending` | 待处理 |
| `in_progress` | 进行中 |
| `review` | 待审核 |
| `completed` | 已完成 |
| `blocked` | 阻塞 |
| `cancelled` | 已取消 |

**priority 枚举：**

| 值 | 说明 |
|----|------|
| `urgent` | 紧急 |
| `high` | 高 |
| `normal` | 普通 |
| `low` | 低 |

### 1.5 工序明细

#### work_order_process_item — 工序明细

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| process_id | BIGINT | FK → work_order_process.id | 所属工单进度 |
| process_order | INT | NOT NULL | 工序序号 |
| process_name | VARCHAR(100) | NOT NULL | 工序名称 |
| status | VARCHAR(20) | DEFAULT 'pending' | 该工序状态 |
| started_at | DATETIME | DEFAULT NULL | 工序开始时间 |
| completed_at | DATETIME | DEFAULT NULL | 工序完成时间 |
| duration_minutes | INT | DEFAULT 0 | 耗时（分钟） |
| operator | VARCHAR(50) | DEFAULT NULL | 操作人 |
| remark | TEXT | DEFAULT NULL | 备注 |

```sql
CREATE TABLE work_order_process_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_id BIGINT NOT NULL COMMENT '所属工单进度',
    process_order INT NOT NULL COMMENT '工序序号',
    process_name VARCHAR(100) NOT NULL COMMENT '工序名称',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '该工序状态',
    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
    duration_minutes INT DEFAULT 0 COMMENT '耗时(分钟)',
    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    remark TEXT DEFAULT NULL COMMENT '备注',
    INDEX idx_process (process_id),
    INDEX idx_operator (operator),
    CONSTRAINT fk_item_process FOREIGN KEY (process_id)
        REFERENCES work_order_process(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序明细';
```

### 1.6 办公室任务

#### office_task — 办公室任务

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| title | VARCHAR(200) | NOT NULL | 任务标题 |
| task_type | VARCHAR(50) | DEFAULT NULL | 任务类型 |
| department | VARCHAR(50) | DEFAULT NULL | 所属部门 |
| status | VARCHAR(20) | NOT NULL | 状态 |
| priority | VARCHAR(20) | DEFAULT 'normal' | 优先级 |
| assignee | VARCHAR(50) | DEFAULT NULL | 负责人 |
| deadline | DATE | DEFAULT NULL | 截止日期 |
| remark | TEXT | DEFAULT NULL | 备注 |
| created_by | VARCHAR(50) | DEFAULT NULL | 创建人 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

```sql
CREATE TABLE office_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    task_type VARCHAR(50) DEFAULT NULL COMMENT '任务类型',
    department VARCHAR(50) DEFAULT NULL COMMENT '所属部门',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态',
    priority VARCHAR(20) DEFAULT 'normal' COMMENT '优先级',
    assignee VARCHAR(50) DEFAULT NULL COMMENT '负责人',
    deadline DATE DEFAULT NULL COMMENT '截止日期',
    remark TEXT DEFAULT NULL COMMENT '备注',
    created_by VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_assignee (assignee),
    INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='办公室任务';
```

### 1.7 紧急任务

#### emergency_task — 紧急任务

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| title | VARCHAR(200) | NOT NULL | 任务标题 |
| urgency_type | VARCHAR(20) | NOT NULL | 类型：rework / rush / insert |
| source_order_no | VARCHAR(50) | DEFAULT NULL | 来源单号 |
| reason | TEXT | DEFAULT NULL | 原因说明 |
| status | VARCHAR(20) | NOT NULL | 状态 |
| assignee | VARCHAR(50) | DEFAULT NULL | 负责人/跟进人 |
| deadline | DATE | DEFAULT NULL | 截止日期 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

```sql
CREATE TABLE emergency_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    urgency_type VARCHAR(20) NOT NULL COMMENT '类型: rework/rush/insert',
    source_order_no VARCHAR(50) DEFAULT NULL COMMENT '来源单号',
    reason TEXT DEFAULT NULL COMMENT '原因',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态',
    assignee VARCHAR(50) DEFAULT NULL COMMENT '负责人',
    deadline DATE DEFAULT NULL COMMENT '截止日期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (urgency_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急任务';
```

### 1.8 报工记录

#### work_report — 报工记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| process_item_id | BIGINT | FK → work_order_process_item.id | 关联工序明细 |
| work_order_id | VARCHAR(50) | NOT NULL | 工单号 |
| process_name | VARCHAR(100) | NOT NULL | 工序名称 |
| operator | VARCHAR(50) | NOT NULL | 操作人 |
| report_time | DATETIME | NOT NULL | 报工时间 |
| total_qty | INT | NOT NULL | 完成数量 |
| defect_qty | INT | DEFAULT 0 | 不良数量 |
| defect_reason | VARCHAR(200) | DEFAULT NULL | 不良原因 |
| remark | TEXT | DEFAULT NULL | 备注 |

```sql
CREATE TABLE work_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_item_id BIGINT DEFAULT NULL COMMENT '关联工序',
    work_order_id VARCHAR(50) NOT NULL COMMENT '工单号',
    process_name VARCHAR(100) NOT NULL COMMENT '工序名称',
    operator VARCHAR(50) NOT NULL COMMENT '操作人',
    report_time DATETIME NOT NULL COMMENT '报工时间',
    total_qty INT NOT NULL COMMENT '完成数量',
    defect_qty INT DEFAULT 0 COMMENT '不良数量',
    defect_reason VARCHAR(200) DEFAULT NULL COMMENT '不良原因',
    remark TEXT DEFAULT NULL COMMENT '备注',
    INDEX idx_work_order (work_order_id),
    INDEX idx_operator (operator),
    INDEX idx_report_time (report_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报工记录';
```

### 1.9 权限相关

#### sys_user — 用户

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 登录名 |
| display_name | VARCHAR(100) | NOT NULL | 显示名称 |
| password | VARCHAR(255) | NOT NULL | 加密密码 |
| role | VARCHAR(50) | NOT NULL | 角色标识 |
| department | VARCHAR(50) | DEFAULT NULL | 所属部门 |
| avatar | VARCHAR(255) | DEFAULT NULL | 头像URL |
| status | TINYINT | DEFAULT 1 | 状态 |
| last_login | DATETIME | DEFAULT NULL | 最后登录 |
| created_at | DATETIME | NOT NULL | 创建时间 |

```sql
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    department VARCHAR(50) DEFAULT NULL,
    avatar VARCHAR(255) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    last_login DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';
```

#### sys_role_permission — 角色权限

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| role | VARCHAR(50) | NOT NULL | 角色标识 |
| permission | VARCHAR(100) | NOT NULL | 权限标识 |
| UNIQUE KEY uk_role_perm (role, permission) |

## 2. 实体关系图（ER）

```
board_template 1──N board_column_def
board_template 1──N board_view_def
board_template 1──N work_order_process

work_order_process 1──N work_order_process_item
work_order_process_item 1──N work_report

office_task (独立)
emergency_task (独立)

sys_user (独立，关联 assignee 字段)
```

## 3. 数据同步策略

### 看板服务持有的数据

```
┌─────────────────────┐
│   看板服务 (Kanban)  │
├─────────────────────┤
│                      │
│  自管数据：            │
│  ├─ work_order_process        │
│  ├─ work_order_process_item   │
│  ├─ work_report               │
│  ├─ office_task               │
│  ├─ emergency_task            │
│  └─ sys_user                  │
│                      │
│  从 ERP 同步：         │
│  ├─ 工单基本信息        │
│  ├─ 产品/工序模板       │
│  └─ 客户信息           │
│                      │
└─────────────────────┘
```

### 同步流程

```
ERP 工单创建 → Kafka/MQ 消息 → Kanban 服务接收
  → 查找产品工序模板
  → 自动生成 work_order_process + work_order_process_item 记录
  → 状态初始化：current_step = 1，所有工序 status = 'pending'
```

## 4. 索引策略

| 表 | 索引 | 查询场景 |
|----|------|---------|
| work_order_process | (status, assignee) | 看板按负责人筛选 |
| work_order_process | (deadline) | 逾期查询 |
| work_order_process_item | (process_id) | 工序明细加载 |
| work_order_process_item | (operator) | 按人统计工作量 |
| work_report | (report_time) | 时间段报工统计 |
| work_report | (work_order_id) | 单个工单报工记录 |
