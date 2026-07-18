# JJX ERP开发上下文备忘录

## 📋 核心原则

### 1. 复用已有组件

**不要重复创建** jjx-common 和 jjx-framework 中已有的基础设施组件。

### 2. 只创建业务代码

每个业务模块**只创建**：

- 实体类（基于数据库表）
- Mapper接口（继承BaseMapper）
- Service层（业务逻辑）
- Controller层（API接口）

### 3. 遵循项目结构

参考其他业务模块（如 jjx-system）的结构和代码风格。

## 🏗️ 已有基础设施（禁止重复创建）

### ✅ jjx-common 中已有（直接使用）：

- **统一响应**：`com.jjx.common.core.result.R`
- **业务异常**：`com.jjx.common.exception.ServiceException`
- **分页查询**：`com.jjx.common.core.page.PageQuery`
- **工具类**：`DateUtils`、`ExcelUtils`、`TreeUtils`、`ConversionUtils`等
- **常量类**：`Constants`、`UserConstants`、`StatusEnum`等

### ✅ jjx-framework 中已有（直接使用）：

- **MyBatisPlus配置**：`com.jjx.framework.config.MyBatisPlusConfig`
- **权限配置**：`com.jjx.framework.config.SaTokenConfig`
- **API文档配置**：`com.jjx.framework.config.Knife4jConfig`
- **全局异常处理**：`com.jjx.framework.config.GlobalExceptionHandler`
- **Web配置**：`com.jjx.framework.config.WebConfig`

## 📁 业务模块标准结构

每个业务模块**只包含以下目录和文件**：

```
com/jjx/[模块名]/
├── controller/           # Controller层（必须）
│   └── *.java           # 使用@Tag定义API分组
├── domain/              # 领域层（必须）
│   ├── entity/          # 实体类（基于数据库表）
│   ├── dto/             # 数据传输对象（可选）
│   └── vo/              # 视图对象（可选）
├── mapper/              # 数据访问层（必须）
│   └── *.java           # 继承BaseMapper<实体类>
├── service/             # 业务逻辑层（必须）
│   ├── *.java           # Service接口
│   └── impl/            # Service实现
│       └── *.java
└── enums/               # 枚举类（可选）
    └── *.java           # 业务相关枚举
```

## 🔍 快速检查清单（开发前必读）

### 开始开发前检查：

- [ ] **查看pom.xml**：确认模块依赖关系
- [ ] **查看jjx-common**：了解可复用的公共组件
- [ ] **查看jjx-framework**：了解已有的配置类
- [ ] **参考其他模块**：如jjx-system、jjx-product等
- [ ] **阅读规范文档**：`开发规范手册.md`、`代码规范.md`、`API设计规范.md`

### 编码时检查：

- [ ] **不要创建**：`ApiResult`、`BusinessException`、`PageQuery`等已有组件
- [ ] **不要创建**：`MyBatisPlusConfig`、`SaTokenConfig`、`Knife4jConfig`等配置类
- [ ] **必须使用**：`R`类作为统一响应，`ServiceException`作为业务异常
- [ ] **必须继承**：`BaseMapper<实体类>`作为Mapper接口
- [ ] **必须遵循**：项目命名规范和代码风格

## 🚫 禁止创建的文件列表

### 配置类（使用jjx-framework中的）：

- `config/MyBatisPlusConfig.java`
- `config/SaTokenConfig.java`
- `config/Knife4jConfig.java`
- `config/WebConfig.java`

### 公共组件（使用jjx-common中的）：

- `common/ApiResult.java`
- `common/PageQuery.java`
- `common/PageResult.java`
- `exception/BusinessException.java`
- `exception/GlobalExceptionHandler.java`

### 工具类（使用jjx-common中的）：

- `utils/DateUtils.java`
- `utils/ExcelUtils.java`
- `utils/TreeUtils.java`
- `utils/ConversionUtils.java`

## ✅ 允许创建的文件列表

### 业务特有内容（必须创建）：

- `domain/entity/*.java` - 实体类（基于数据库表）
- `mapper/*.java` - Mapper接口（继承BaseMapper）
- `service/*.java` - Service接口
- `service/impl/*.java` - Service实现
- `controller/*.java` - Controller类

### 业务可选内容（根据需要创建）：

- `domain/dto/*.java` - 数据传输对象
- `domain/vo/*.java` - 视图对象
- `enums/*.java` - 业务枚举
- `exception/*.java` - 业务特有异常（如`ResourceNotFoundException`）

## 📝 使用示例

### Controller示例：

```java
@RestController
@RequestMapping("/production/order")
@Tag(name = "生产管理-生产订单")
public class ProductionOrderController {

    @Autowired
    private ProductionOrderService orderService;

    @GetMapping("/list")
    @Operation(summary = "分页查询生产订单")
    public R<PageResult<ProductionOrderVO>> list(PageQuery query) {
        return R.ok(orderService.pageList(query));
    }
}
```

### Service示例：

```java
public interface ProductionOrderService {

    PageResult<ProductionOrderVO> pageList(PageQuery query);

    R<ProductionOrderVO> getById(Long orderId);

    R<Void> create(ProductionOrderCreateDTO dto);
}
```

### Mapper示例：

```java
@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
    // 可以添加自定义查询方法
}
```

## 🔗 相关文档

1. **`docs/05-开发指南/开发规范手册.md`** - 完整开发规范
2. **`docs/05-开发指南/代码规范.md`** - 代码编写规范
3. **`docs/02-技术架构/API设计规范.md`** - API设计规范
4. **`docs/02-技术架构/技术栈选型.md`** - 技术栈说明
5. **`docs/02-技术架构/数据库设计.md`** - 数据库设计规范

## 🎨 前端开发规范

### Element Plus 组件使用规范

#### 按钮组件规范

**重要更新**：Element Plus 的 `type="text"` 按钮类型已经弃用，请使用 `link` 属性代替。

**错误用法**：

```vue
<!-- 已弃用，不要使用 -->
<el-button type="text" @click="handleClick">按钮</el-button>
```

**正确用法**：

````vue
<!-- 使用 link 属性代替 -->
<el-button link @click="handleClick">按钮</el-button>

**错误用法**： ```vue
<!-- label 弃用-->
<el-radio-button label="week">本周</el-radio-button>
<el-radio label="week">本周</el-radio>

**正确用法**： ```vue
<!-- 使用 value 属性代替 -->
<el-radio-button value="week">本周</el-radio-button>
<el-radio value="week">本周</el-radio>
````

#### 其他前端规范

1. **组件命名**：使用 PascalCase 命名组件，如 `OrderStatsCards.vue`
2. **文件结构**：按照功能模块组织文件，使用 `composables/` 目录存放可复用逻辑
3. **类型定义**：使用 TypeScript 定义接口和类型，确保类型安全
4. **响应式数据**：优先使用 `ref` 和 `reactive` 管理状态
5. **样式规范**：使用 Scoped CSS，避免样式污染

## 📞 遇到问题时

1. **先检查**：jjx-common和jjx-framework中是否已有类似组件
2. **再参考**：其他业务模块（如jjx-system）的实现方式
3. **最后问**：如果还不确定，再询问项目负责人

---

**最后更新**：2026-04-12
**维护者**：AI助手
**目的**：避免重复创建基础设施，专注于业务开发
