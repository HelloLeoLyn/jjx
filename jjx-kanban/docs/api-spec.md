# API 接口规范

## 1. 通用约定

### 1.1 基础信息

| 项目 | 规范 |
|------|------|
| 基础路径 | `/api/v1` |
| 协议 | HTTP/HTTPS |
| 数据格式 | JSON (UTF-8) |
| 认证方式 | JWT Bearer Token |
| 分页 | `?page=1&size=20` |

### 1.2 通用响应格式

```json
{
    "code": 0,
    "message": "success",
    "data": {}
}
```

**状态码说明：**

| code | 含义 |
|------|------|
| 0 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 冲突（乐观锁） |
| 500 | 服务器内部错误 |

### 1.3 认证 Header

```
Authorization: Bearer <jwt_token>
```

---

## 2. 看板接口

### 2.1 获取可用模板

```
GET /api/v1/board/templates
```

**Response:**
```json
{
    "code": 0,
    "data": [
        {
            "code": "production",
            "name": "生产工单",
            "icon": "Sell"
        }
    ]
}
```

### 2.2 获取视图列表

```
GET /api/v1/board/{templateCode}/views
```

**Path Parameters:**

| 参数 | 类型 | 说明 |
|------|------|------|
| templateCode | string | 模板编码（production/office/emergency） |

**Response:**
```json
{
    "code": 0,
    "data": [
        {
            "id": "process",
            "name": "工序视图",
            "groupBy": "currentProcess",
            "columns": [
                { "id": "printing", "label": "印刷", "color": "#409eff" }
            ]
        }
    ]
}
```

### 2.3 获取看板数据

```
GET /api/v1/board/{templateCode}/data?view={viewId}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| view | string | 是 | 视图标识 |
| keyword | string | 否 | 关键字搜索 |
| assignee | string | 否 | 负责人筛选 |
| priority | string | 否 | 优先级筛选 |
| status | string | 否 | 状态筛选 |
| deadlineFrom | string | 否 | 截止日期起始 |
| deadlineTo | string | 否 | 截止日期截止 |

**Response:**
```json
{
    "code": 0,
    "data": {
        "view": {
            "id": "process",
            "name": "工序视图"
        },
        "columns": [
            {
                "def": {
                    "id": "printing",
                    "label": "印刷",
                    "color": "#409eff"
                },
                "cards": [
                    {
                        "id": "WO-001",
                        "title": "薄膜开关-MK12",
                        "workOrderNo": "WO-202607-001",
                        "productName": "薄膜开关-MK12",
                        "quantity": 5000,
                        "customer": "华为",
                        "currentProcess": "印刷",
                        "processOrder": 1,
                        "priority": "urgent",
                        "status": "in_progress",
                        "assignee": "张三",
                        "deadline": "2026-07-25",
                        "remark": "",
                        "createdAt": "2026-07-01",
                        "updatedAt": "2026-07-18"
                    }
                ]
            }
        ]
    }
}
```

### 2.4 获取卡片详情

```
GET /api/v1/board/cards/{cardId}
```

**Response:**
```json
{
    "code": 0,
    "data": {
        "id": "WO-001",
        "title": "薄膜开关-MK12",
        "workOrderNo": "WO-202607-001",
        "productName": "薄膜开关-MK12",
        "quantity": 5000,
        "customer": "华为",
        "currentProcess": "印刷",
        "processOrder": 1,
        "priority": "urgent",
        "status": "in_progress",
        "assignee": "张三",
        "deadline": "2026-07-25",
        "remark": "注意：材料已齐",
        "processes": [
            { "order": 1, "name": "印刷", "status": "completed", "startedAt": "07-15", "completedAt": "07-16" },
            { "order": 2, "name": "冲切", "status": "in_progress", "startedAt": "07-17", "completedAt": null }
        ],
        "createdAt": "2026-07-01",
        "updatedAt": "2026-07-18"
    }
}
```

### 2.5 移动卡片

```
PATCH /api/v1/board/cards/{cardId}/move
```

**Request Body:**
```json
{
    "toColumnId": "cutting",
    "version": 3
}
```

**Response:**
```json
{
    "code": 0,
    "message": "移动成功"
}
```

### 2.6 创建卡片

```
POST /api/v1/board/cards
```

**Request Body（生产工单）：**
```json
{
    "templateType": "production",
    "title": "薄膜开关-MK12",
    "workOrderNo": "WO-202607-021",
    "productName": "薄膜开关-MK12",
    "quantity": 5000,
    "customer": "华为",
    "targetProcess": "印刷",
    "priority": "normal",
    "assignee": "张三",
    "deadline": "2026-07-30",
    "remark": ""
}
```

**Request Body（办公室任务）：**
```json
{
    "templateType": "office",
    "title": "采购询价-薄膜材料",
    "taskType": "采购",
    "department": "采购部",
    "priority": "urgent",
    "assignee": "李四",
    "deadline": "2026-07-20"
}
```

**Request Body（紧急任务）：**
```json
{
    "templateType": "emergency",
    "title": "MK12 返工-印刷偏位",
    "urgencyType": "rework",
    "sourceOrderNo": "WO-202607-005",
    "reason": "印刷偏位超公差",
    "assignee": "王五",
    "deadline": "2026-07-19"
}
```

**Response:**
```json
{
    "code": 0,
    "data": {
        "id": "WO-021",
        "title": "薄膜开关-MK12"
    },
    "message": "创建成功"
}
```

### 2.7 更新卡片

```
PATCH /api/v1/board/cards/{cardId}
```

**Request Body:**
```json
{
    "remark": "等待材料到位",
    "assignee": "赵六",
    "version": 2
}
```

### 2.8 删除卡片

```
DELETE /api/v1/board/cards/{cardId}
```

---

## 3. 报工接口

### 3.1 提交报工

```
POST /api/v1/reports
```

**Request Body:**
```json
{
    "workOrderId": "WO-202607-001",
    "processName": "冲切",
    "operator": "张三",
    "totalQty": 4980,
    "defectQty": 20,
    "defectReason": "尺寸偏大",
    "remark": "已调机"
}
```

### 3.2 查询报工记录

```
GET /api/v1/reports?workOrderId=WO-001&operator=张三&dateFrom=2026-07-01&dateTo=2026-07-18
```

### 3.3 查询工序统计

```
GET /api/v1/statistics/process?dateFrom=2026-07-01&dateTo=2026-07-18
```

**Response:**
```json
{
    "code": 0,
    "data": {
        "totalProcessed": 150,
        "totalDefect": 350,
        "avgDefectRate": 2.3,
        "topDefectReasons": [
            { "reason": "印刷偏位", "count": 120 },
            { "reason": "贴合气泡", "count": 85 },
            { "reason": "线路断路", "count": 45 }
        ],
        "processBreakdown": [
            { "processName": "印刷", "totalQty": 30000, "defectQty": 120, "defectRate": 0.4 },
            { "processName": "冲切", "totalQty": 28000, "defectQty": 45, "defectRate": 0.16 }
        ]
    }
}
```

---

## 4. 权限接口

### 4.1 登录

```
POST /api/v1/auth/login
```

**Request Body:**
```json
{
    "username": "zhangsan",
    "password": "***"
}
```

**Response:**
```json
{
    "code": 0,
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "user": {
            "id": 1,
            "username": "zhangsan",
            "displayName": "张三",
            "role": "operator",
            "department": "生产部"
        }
    }
}
```

### 4.2 获取当前用户信息

```
GET /api/v1/auth/me
```

### 4.3 获取用户权限

```
GET /api/v1/auth/permissions
```

**Response:**
```json
{
    "code": 0,
    "data": {
        "role": "operator",
        "permissions": [
            "card:view",
            "card:move",
            "card:report"
        ]
    }
}
```

---

## 5. 大屏接口

### 5.1 获取大屏数据

```
GET /api/v1/board/big-screen?templateCode=production
```

返回精简数据（不含富文本、备注等无关字段），适合大屏展示

**Response:**
```json
{
    "code": 0,
    "data": {
        "summary": {
            "totalOrders": 45,
            "inProgress": 32,
            "completed": 8,
            "overdue": 5,
            "blocked": 3
        },
        "overdueList": [
            { "title": "...", "overdueDays": 3 }
        ],
        "columns": [
            { "label": "印刷", "count": 8, "cards": [...] }
        ]
    }
}
```

---

## 6. 错误响应示例

```json
{
    "code": 409,
    "message": "数据已被其他用户修改，请刷新后重试",
    "data": {
        "currentVersion": 4,
        "yourVersion": 3
    }
}
```

```json
{
    "code": 400,
    "message": "参数校验失败",
    "data": {
        "errors": {
            "title": "标题不能为空",
            "deadline": "截止日期格式错误（应为 YYYY-MM-DD）"
        }
    }
}
```
