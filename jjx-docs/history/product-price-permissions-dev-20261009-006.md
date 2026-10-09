# 产品模块独立价格维护与权限控制

任务：dev-20261009-006；日期：2026-10-09；执行人：codex。状态：代码完成，待审核；菜单导入、角色授权、服务更新与验收由用户执行。

## 用户口径

产品新增/修改中的价格信息移到产品模块独立维护，接入现有权限系统，由获授权人员操作。本次按用户要求只改代码，不执行测试、编译、类型检查、浏览器/接口验收或重跑，不启停任何服务。

## 页面及数据

产品管理→产品价格维护，组件views/product/price/index.vue。支持产品编码/名称查询，查看产品所属客户、单位、基础售价、标准成本及自动计算的参考毛利率；单个产品维护两个金额，精度两位、非负，缺值需明确填写，0为有效金额。未填写成本或售价不大于0时毛利率显示空值。

复用product.base_price、product.cost_price，均decimal(12,2)，不建表、不增列、不搬迁或改写既有业务数据。仅分离维护入口，实际客户报价、销售订单单价/金额仍来自各单据；现有ProductionCostController直接读取产品标准成本，口径保持。金额单位沿用原产品表单“元”，本次不新增客户价目表、币种/数量阶梯、生效日期或价格版本模型。

产品新增/修改移除基础售价、标准成本与毛利率区域，保留最小起订量/标准交期；提交时明确剔除从详情带回的价格字段，避免普通编辑覆盖调价。产品详情中价格仅在有查看权限时显示。

## 权限与接口

- product:price:view：价格页面、GET /product/prices、GET /product/prices/{productId}。
- product:price:edit：维护按钮和PUT /product/prices/{productId}；更新同时要求查看/修改两项权限。前端引用既有hasPermi，后端SaToken与服务入口均校验。
- 普通产品API的列表、分页、详情、完整详情、搜索、按分类查询，在无价格查看权限时清空两个价格字段。此处针对产品档案接口；生产成本分析等既有业务接口沿用自身权限与成本口径，本次不调整其他模块的成本权限。
- 普通产品POST/PUT拒绝携带非空价格字段，提示进入价格维护。Product的两个价格字段使用FieldStrategy.NEVER禁止通用实体更新写入，既有状态流转等updateById也不会覆盖价格；专用价格服务通过明确的LambdaUpdateWrapper.set维护。
- 调价读取最新值，客户端携带expectedBasePrice/expectedCostPrice。服务比较原值，并在UPDATE条件中再次匹配；并发变更或产品删除时提示刷新，避免覆盖他人调价。不更新其他产品字段。沿用@Log记录操作者、时间、产品及售价/成本前后值；金额相同不执行更新。

## 菜单部署

配置SQL：jjx-docs/sql/config/product_price_permissions_dev20261009_006.sql。仅幂等新增sys_menu中的价格页面及修改权限按钮；动态查找产品父菜单，使用自增ID，不使用迁移编号、不执行DDL。没有自动给现有角色分配权限。本次SQL未执行，现有角色和菜单未改。

用户部署时执行该配置，再在系统角色管理中给相应角色分配价格查看权限，需要维护的角色同时分配修改权限；重新登录获取权限。后端代码需要用户自行更新运行服务。只涉及配置新增与单条产品价格维护，不涉及备份规则中的表结构变更、清库或批量数据订正；agent未生成备份。

## 验证交接

本次未测试/未编译，不宣称运行通过。用户验收：仅产品编辑权限的账号无价格入口且普通编辑不能改价；仅查看权限账号可查看不能保存；查看+修改账号可调价并看到变更日志；验证负数/超精度拒绝、0金额展示、并发调价冲突；核对产品基本资料保存及状态流转不覆盖价格，生产成本对比继续使用标准成本。

既有包含价格的产品新增/修改API调用方需要去除basePrice/costPrice，改走专用接口。本仓库前端已处理；外部脚本/旧客户端需用户同步。不调整测试代码，旧测试中直接向产品建档API提交价格的断言需要按新维护边界更新。

白名单：server product/controller/{ProductController.java,ProductPriceController.java}、domain/{dto/ProductPriceUpdateDTO.java,vo/ProductPriceVO.java,entity/Product.java}、service/{ProductPriceService.java,impl/ProductServiceImpl.java}；web api/product/price.ts、views/product/price/index.vue、views/product/list/components/{ProductFormContent.vue,ProductDetail.vue}；本配置SQL、本记录、history/INDEX.md仅本任务行。其他会话产品列表/报价/备份等改动不纳入。
