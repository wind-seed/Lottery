# Lottery 项目文件说明

本文档用于帮助你从文件层面理解整个抽奖系统。项目是一个 Java 8 + Spring Boot 2.6.0 + Maven 多模块工程，采用 DDD 分层思想：接口层负责对外暴露，应用层负责流程编排，领域层负责核心业务规则，基础设施层负责数据库和缓存，RPC 模块负责远程调用契约，`db-router-spring-boot-starter` 负责分库分表路由。

## 1. 项目整体结构

```text
D:\java\Lottery
├── pom.xml
├── README-LOCAL.md
├── PROJECT-FILES-GUIDE.md
├── .gitignore
├── .run
├── scripts
├── db-router-spring-boot-starter
├── lottery-common
├── lottery-rpc
├── lottery-domain
├── lottery-infrastructure
├── lottery-application
├── lottery-interfaces
└── doc
```

| 路径 | 作用 |
| --- | --- |
| `pom.xml` | 父级 Maven 配置，声明所有子模块、统一依赖版本、Java 版本和编译插件。 |
| `README-LOCAL.md` | 本地运行说明，记录 Windows 下启动、停止、初始化数据库、冒烟测试等操作。 |
| `PROJECT-FILES-GUIDE.md` | 当前这份项目文件说明文档。 |
| `.gitignore` | Git 忽略规则，排除 IDE 临时文件、编译产物、日志、压缩包等不应提交的文件。 |
| `.run/` | IntelliJ IDEA 运行配置目录，用来保存一键启动配置。 |
| `scripts/` | Windows PowerShell 辅助脚本目录，用来初始化数据库、启动服务、停止服务、做接口冒烟测试。 |
| `db-router-spring-boot-starter/` | 自定义分库分表 Starter，给业务项目提供动态数据源、库表路由和 MyBatis 表名替换能力。 |
| `lottery-common/` | 公共模块，放全局常量、统一结果对象、分页对象等基础类。 |
| `lottery-rpc/` | RPC 契约模块，定义 Dubbo 接口、请求对象、响应对象和 DTO。 |
| `lottery-domain/` | 领域模块，包含抽奖活动、抽奖策略、规则引擎、奖品发放、ID 生成、Redis 配置等核心业务。 |
| `lottery-infrastructure/` | 基础设施模块，包含 MyBatis DAO、数据库 PO、仓储实现、Redis 工具。 |
| `lottery-application/` | 应用服务模块，负责抽奖流程、活动部署流程、Kafka 消费生产、XXL-Job 定时任务。 |
| `lottery-interfaces/` | 启动和接口模块，包含 Spring Boot 主类、Dubbo Facade、HTTP API、本地 UI 静态页面、MyBatis XML 配置和测试代码。 |
| `doc/` | 项目文档、数据库 SQL、Docker 部署配置、设计图、演示图片、PPT、Excel 等资料。 |

## 2. 模块依赖关系

```text
lottery-interfaces
├── lottery-application
├── lottery-infrastructure
├── lottery-domain
├── lottery-rpc
└── lottery-common

lottery-application
└── lottery-domain

lottery-infrastructure
└── lottery-domain

lottery-domain
├── lottery-common
└── db-router-spring-boot-starter

lottery-rpc
└── lottery-common
```

实际运行时从 `lottery-interfaces` 模块的 `LotteryApplication` 启动。浏览器访问 `http://localhost:8081/` 时，会加载 `lottery-interfaces/src/main/resources/static/index.html`，前端 JS 调用 `LotteryApiController` 的 `/api/lottery/**` 接口，接口再调用领域服务、应用服务和 DAO。

## 3. 根目录文件

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | 父工程 POM。`packaging=pom`，声明 7 个子模块：`db-router-spring-boot-starter`、`lottery-application`、`lottery-domain`、`lottery-infrastructure`、`lottery-interfaces`、`lottery-rpc`、`lottery-common`。同时统一管理 MyBatis、MySQL、Fastjson、Dubbo、Nacos、MapStruct、XXL-Job、Redis 等依赖版本。 |
| `.gitignore` | 忽略 `.idea/`、`target/`、日志、压缩包、编译产物，避免把本地临时文件推到 GitHub。 |
| `LICENSE` | 开源许可证文本。 |
| `README-LOCAL.md` | 本地启动说明。包含 `start-local.ps1`、`stop-local.ps1`、`smoke-test.ps1`、`init-db.ps1` 的使用方式。 |
| `run-lottery-*.log` | 本地启动服务时产生的运行日志，用来排查 Spring Boot、数据库、接口调用问题。 |
| `run-lottery-*.err.log` | 本地启动服务时的错误日志，通常为空表示没有标准错误输出。 |

## 4. `.run` 目录

| 文件 | 作用 |
| --- | --- |
| `.run/LotteryApplication_local.run.xml` | IntelliJ IDEA 的本地运行配置。用于在 IDEA 顶部运行按钮中一键启动 `LotteryApplication`，通常会指定 Maven/Java 启动参数、工作目录和 `local` profile。 |

## 5. `scripts` 目录

| 文件 | 作用 |
| --- | --- |
| `scripts/start-local.ps1` | 本地启动脚本。一般会先 Maven 打包，再用本地配置启动 Spring Boot 服务；支持停止旧进程、跳过构建等参数。 |
| `scripts/stop-local.ps1` | 停止本地服务脚本。用于关闭占用 `8081` 的 Lottery Java 进程。 |
| `scripts/init-db.ps1` | 数据库初始化脚本。根据 `doc/assets/sql` 下的 SQL 创建或重建 `lottery`、`lottery_01`、`lottery_02` 数据库。 |
| `scripts/smoke-test.ps1` | 冒烟测试脚本。服务启动后调用健康检查、诊断、活动、策略、奖品、规则、首页等接口，确认项目可运行。 |

## 6. `db-router-spring-boot-starter` 模块

这个模块是项目内置的分库分表组件。业务代码通过注解声明路由字段，运行时根据路由字段计算应该访问哪个库、哪张表。

| 文件 | 作用 |
| --- | --- |
| `db-router-spring-boot-starter/pom.xml` | 分库分表 Starter 的 Maven 配置，依赖 Spring Boot 自动配置、Spring JDBC、AOP 和 MyBatis。 |
| `src/main/resources/META-INF/spring.factories` | Spring Boot 自动装配入口，让使用方引入依赖后自动加载 `DBRouterAutoConfiguration`。 |
| `annotation/DBRouter.java` | 方法级路由注解，标记某个 DAO 或仓储方法需要按指定字段分库分表。 |
| `annotation/DBRouterStrategy.java` | 类级策略注解，声明该类是否参与分库分表策略。 |
| `config/DBContextHolder.java` | 使用 `ThreadLocal` 保存当前线程要访问的数据库索引和表索引。 |
| `config/DBRouterAutoConfiguration.java` | 自动配置类，读取 `mini-db-router.jdbc.datasource.*` 配置，注册动态数据源、AOP 切面、MyBatis 拦截器。 |
| `config/DBRouterJoinPoint.java` | AOP 切面。拦截带 `@DBRouter` 的方法，从参数中取路由键，计算库表位置并写入 `DBContextHolder`。 |
| `config/DynamicDataSource.java` | 继承 `AbstractRoutingDataSource`，根据 `DBContextHolder` 决定当前 SQL 使用哪个数据源。 |
| `config/DynamicMybatisTableInterceptor.java` | MyBatis 拦截器。执行 SQL 前把逻辑表名替换成真实分表名。 |
| `strategy/IDBRouterStrategy.java` | 分库分表算法接口。 |
| `strategy/impl/DBRouterStrategyHashCode.java` | Hash 路由算法实现，根据路由字段 hash 值计算库编号和表编号。 |

## 7. `lottery-common` 模块

公共基础模块，不依赖业务流程，其他模块都可以复用。

| 文件 | 作用 |
| --- | --- |
| `lottery-common/pom.xml` | 公共模块 Maven 配置。 |
| `common/Constants.java` | 全局常量和枚举。包括响应码、活动状态、抽奖策略模式、中奖状态、奖品类型、ID 生成策略、任务状态、发奖状态、MQ 状态、Redis Key 等。 |
| `common/Result.java` | 统一业务返回对象，封装 `code`、`info` 等结果信息。 |
| `common/PageRequest.java` | 通用分页请求对象，封装页码、每页行数等分页参数。 |

## 8. `lottery-rpc` 模块

RPC 模块只定义接口和数据对象，不直接实现业务。它相当于“对外服务合同”，供 Dubbo 服务提供者和消费者共同依赖。

| 文件 | 作用 |
| --- | --- |
| `lottery-rpc/pom.xml` | RPC 模块 Maven 配置。 |
| `activity/deploy/ILotteryActivityDeploy.java` | 活动部署/活动查询 RPC 接口。 |
| `activity/deploy/dto/ActivityDTO.java` | 活动信息 DTO，给外部系统展示活动 ID、名称、状态、库存、起止时间等。 |
| `activity/deploy/req/ActivityPageReq.java` | 活动分页查询请求，继承 `PageRequest`，支持按活动 ID、名称等条件查询。 |
| `activity/deploy/res/ActivityRes.java` | 活动分页查询响应，包含活动列表、总条数和结果状态。 |
| `activity/booth/ILotteryActivityBooth.java` | 抽奖入口 RPC 接口，提供普通抽奖和量化人群抽奖。 |
| `activity/booth/req/DrawReq.java` | 普通抽奖请求，主要包含用户 ID 和活动 ID。 |
| `activity/booth/req/QuantificationDrawReq.java` | 量化人群抽奖请求，包含用户 ID、规则树 ID 和用户属性 Map。 |
| `activity/booth/dto/AwardDTO.java` | 抽奖返回的奖品 DTO。 |
| `activity/booth/res/DrawRes.java` | 抽奖响应对象，继承 `Result`，携带是否中奖、奖品信息等。 |

## 9. `lottery-domain` 模块

领域模块是项目的核心。这里不关心 HTTP 怎么调用，也不关心 SQL 怎么写，重点处理“抽奖系统的业务规则”。

### 9.1 活动领域 `domain/activity`

| 文件 | 作用 |
| --- | --- |
| `model/aggregates/ActivityConfigRich.java` | 活动配置聚合对象。一次创建活动时，把活动信息、策略信息、奖品信息组织成一个整体传入领域服务。 |
| `model/aggregates/ActivityInfoLimitPageRich.java` | 活动分页查询聚合对象，包含分页活动信息和总数。 |
| `model/req/ActivityConfigReq.java` | 创建活动配置请求，携带活动 ID 和 `ActivityConfigRich`。 |
| `model/req/ActivityInfoLimitPageReq.java` | 活动分页查询请求，继承公共分页对象。 |
| `model/req/PartakeReq.java` | 用户参与活动请求，包含用户 ID、活动 ID 等。 |
| `model/res/PartakeResult.java` | 用户参与活动结果，说明是否领取到活动参与资格、活动单号等。 |
| `model/res/StockResult.java` | 活动库存扣减结果。 |
| `model/vo/ActivityVO.java` | 活动值对象，表示活动 ID、名称、描述、库存、状态、起止时间、策略 ID 等。 |
| `model/vo/ActivityBillVO.java` | 活动账单/参与凭据对象，用于描述用户参与活动时生成的业务单据。 |
| `model/vo/ActivityPartakeRecordVO.java` | 活动参与记录值对象。 |
| `model/vo/AlterStateVO.java` | 活动状态变更值对象，封装活动 ID、当前状态、目标状态等。 |
| `model/vo/AwardVO.java` | 创建活动时的奖品配置值对象。 |
| `model/vo/DrawOrderVO.java` | 抽奖订单值对象，记录用户抽奖后生成的订单信息。 |
| `model/vo/InvoiceVO.java` | 发奖单据值对象，用于后续 MQ 或同步发奖。 |
| `model/vo/StrategyDetailVO.java` | 活动策略明细值对象，描述某个奖品在策略中的库存和概率。 |
| `model/vo/StrategyVO.java` | 活动策略值对象，描述策略 ID、策略模式、发放方式等。 |
| `model/vo/UserTakeActivityVO.java` | 用户领取活动记录值对象。 |
| `repository/IActivityRepository.java` | 活动仓储接口，定义活动创建、查询、状态变更、库存扣减等持久化能力。 |
| `repository/IUserTakeActivityRepository.java` | 用户参与活动仓储接口，定义用户领取次数、参与记录、发奖单据等持久化能力。 |
| `service/deploy/IActivityDeploy.java` | 活动部署领域服务接口。 |
| `service/deploy/impl/ActivityDeployImpl.java` | 活动部署领域服务实现，负责保存活动、策略、奖品配置，扫描待处理活动。 |
| `service/partake/IActivityPartake.java` | 用户参与活动领域服务接口。 |
| `service/partake/ActivityPartakeSupport.java` | 用户参与活动的支撑类，统一注入仓储、ID 生成等依赖。 |
| `service/partake/BaseActivityPartake.java` | 用户参与活动模板流程，处理校验、库存、领取记录等通用步骤。 |
| `service/partake/impl/ActivityPartakeImpl.java` | 用户参与活动具体实现，完成领取资格、扣减库存、生成单据等操作。 |
| `service/stateflow/IStateHandler.java` | 活动状态机统一接口，定义提审、审核通过、拒绝、撤审、运行、关闭、开启等动作。 |
| `service/stateflow/AbstractState.java` | 状态节点抽象类，给各个状态类提供默认行为。 |
| `service/stateflow/StateConfig.java` | 状态机配置，把活动状态枚举映射到具体状态处理类。 |
| `service/stateflow/impl/StateHandlerImpl.java` | 状态机入口实现，根据当前状态找到对应处理类并执行动作。 |
| `service/stateflow/event/EditingState.java` | 编辑状态下允许的状态流转逻辑。 |
| `service/stateflow/event/ArraignmentState.java` | 提审状态下允许的状态流转逻辑。 |
| `service/stateflow/event/PassState.java` | 审核通过状态下允许的状态流转逻辑。 |
| `service/stateflow/event/RefuseState.java` | 审核拒绝状态下允许的状态流转逻辑。 |
| `service/stateflow/event/DoingState.java` | 运行中状态下允许的状态流转逻辑。 |
| `service/stateflow/event/CloseState.java` | 已关闭状态下允许的状态流转逻辑。 |
| `service/stateflow/event/OpenState.java` | 开启状态下允许的状态流转逻辑。 |

说明：活动状态枚举里有“撤审”状态码，但当前代码没有单独的 `RevokeState.java` 文件，撤审动作由现有状态处理类完成。

### 9.2 策略领域 `domain/strategy`

| 文件 | 作用 |
| --- | --- |
| `annotation/Strategy.java` | 自定义策略注解，用于标记抽奖算法或策略实现。 |
| `model/aggregates/StrategyRich.java` | 策略聚合对象，包含策略主信息、策略明细、奖品信息。 |
| `model/req/DrawReq.java` | 领域层抽奖请求。 |
| `model/res/DrawResult.java` | 领域层抽奖结果，包含中奖状态和奖品信息。 |
| `model/vo/AwardBriefVO.java` | 奖品简要信息值对象。 |
| `model/vo/AwardRateVO.java` | 奖品概率值对象，用于算法初始化和概率计算。 |
| `model/vo/DrawAwardVO.java` | 抽奖命中奖品值对象。 |
| `model/vo/StrategyBriefVO.java` | 策略简要信息值对象。 |
| `model/vo/StrategyDetailBriefVO.java` | 策略明细简要信息值对象。 |
| `repository/IStrategyRepository.java` | 策略仓储接口，定义查询策略、奖品、扣减库存等能力。 |
| `service/algorithm/IDrawAlgorithm.java` | 抽奖算法接口。 |
| `service/algorithm/BaseAlgorithm.java` | 抽奖算法基类，提供概率元组、概率范围、随机数等通用能力。 |
| `service/algorithm/impl/EntiretyRateRandomDrawAlgorithm.java` | 总体概率算法。某个奖品库存为空时，剩余奖品按剩余概率重新分布。 |
| `service/algorithm/impl/SingleRateRandomDrawAlgorithm.java` | 单项概率算法。某个奖品库存为空时，该奖品命中会显示未中奖，不重新分配概率。 |
| `service/draw/IDrawExec.java` | 抽奖执行服务接口。 |
| `service/draw/DrawConfig.java` | 抽奖配置类，注入并维护不同抽奖算法实现。 |
| `service/draw/DrawStrategySupport.java` | 抽奖流程支撑类，统一查询策略、奖品、库存等数据。 |
| `service/draw/AbstractDrawBase.java` | 抽奖模板流程，负责统一抽奖步骤：参数校验、获取策略、执行算法、扣库存、包装结果。 |
| `service/draw/impl/DrawExecImpl.java` | 抽奖执行实现类，是领域抽奖流程的主要入口。 |

### 9.3 规则领域 `domain/rule`

| 文件 | 作用 |
| --- | --- |
| `model/aggregates/TreeRuleRich.java` | 规则树聚合对象，包含树根、节点、连线等完整规则结构。 |
| `model/req/DecisionMatterReq.java` | 规则引擎决策请求，携带用户 ID、规则树 ID、用户属性。 |
| `model/res/EngineResult.java` | 规则引擎决策结果，返回命中的节点、策略 ID 等。 |
| `model/vo/TreeRootVO.java` | 规则树根节点值对象。 |
| `model/vo/TreeNodeVO.java` | 规则树节点值对象。 |
| `model/vo/TreeNodeLineVO.java` | 规则树节点连线值对象，描述条件、流转方向。 |
| `repository/IRuleRepository.java` | 规则仓储接口，定义查询完整规则树的能力。 |
| `service/engine/EngineFilter.java` | 规则引擎接口。 |
| `service/engine/EngineConfig.java` | 规则引擎配置，注入年龄、性别等逻辑过滤器。 |
| `service/engine/EngineBase.java` | 规则引擎模板基类，封装规则树遍历和节点决策流程。 |
| `service/engine/impl/RuleEngineHandle.java` | 规则引擎实现，按规则树计算用户应该进入哪个策略或结果节点。 |
| `service/logic/LogicFilter.java` | 规则节点逻辑过滤器接口。 |
| `service/logic/BaseLogic.java` | 规则逻辑基类，封装条件比较，如等于、大于、小于、枚举等。 |
| `service/logic/impl/UserAgeFilter.java` | 用户年龄规则过滤器。 |
| `service/logic/impl/UserGenderFilter.java` | 用户性别规则过滤器。 |

### 9.4 奖品领域 `domain/award`

| 文件 | 作用 |
| --- | --- |
| `model/req/GoodsReq.java` | 发奖请求对象，包含用户 ID、订单 ID、奖品 ID、奖品名称、奖品内容。 |
| `model/res/DistributionRes.java` | 发奖结果对象，返回发奖状态码和说明。 |
| `model/vo/ShippingAddress.java` | 实物奖品发货地址值对象。 |
| `repository/IOrderRepository.java` | 订单/发奖单据仓储接口。 |
| `service/factory/GoodsConfig.java` | 奖品发放服务配置，维护不同奖品类型和对应发放实现的映射。 |
| `service/factory/DistributionGoodsFactory.java` | 奖品发放工厂，根据奖品类型返回对应发放服务。 |
| `service/goods/IDistributionGoods.java` | 奖品发放接口。 |
| `service/goods/DistributionBase.java` | 发奖基类，提供统一响应包装或公共处理。 |
| `service/goods/impl/DescGoods.java` | 文字描述类奖品发放实现。 |
| `service/goods/impl/RedeemCodeGoods.java` | 兑换码类奖品发放实现。 |
| `service/goods/impl/CouponGoods.java` | 优惠券类奖品发放实现。 |
| `service/goods/impl/PhysicalGoods.java` | 实物奖品发放实现。 |

### 9.5 支撑能力 `domain/support`

| 文件 | 作用 |
| --- | --- |
| `support/ids/IIdGenerator.java` | ID 生成器接口。 |
| `support/ids/IdContext.java` | ID 生成器上下文，把不同 ID 策略注册为可选择的生成器。 |
| `support/ids/policy/SnowFlake.java` | 雪花算法 ID 生成器，适合生成全局唯一长整型 ID。 |
| `support/ids/policy/ShortCode.java` | 短码 ID 生成器，适合生成较短业务编号。 |
| `support/ids/policy/RandomNumeric.java` | 随机数字 ID 生成器。 |
| `support/redis/RedisConfig.java` | Redis 序列化和缓存配置。 |

## 10. `lottery-infrastructure` 模块

基础设施层负责“把领域对象保存到数据库、从数据库读出来、操作 Redis”。它实现了领域层定义的仓储接口。

### 10.1 DAO 接口 `infrastructure/dao`

| 文件 | 作用 |
| --- | --- |
| `IActivityDao.java` | 活动表 DAO，负责活动新增、查询、状态更新、库存更新等 SQL 映射。 |
| `IAwardDao.java` | 奖品表 DAO，负责奖品新增和查询。 |
| `IStrategyDao.java` | 策略主表 DAO。 |
| `IStrategyDetailDao.java` | 策略明细表 DAO，负责奖品概率、库存等明细数据。 |
| `IUserTakeActivityDao.java` | 用户参与活动记录 DAO，通常带分库分表路由。 |
| `IUserTakeActivityCountDao.java` | 用户活动领取次数 DAO。 |
| `IUserStrategyExportDao.java` | 用户中奖发奖单 DAO。 |
| `RuleTreeDao.java` | 规则树主表 DAO。 |
| `RuleTreeNodeDao.java` | 规则树节点 DAO。 |
| `RuleTreeNodeLineDao.java` | 规则树节点连线 DAO。 |

### 10.2 PO 对象 `infrastructure/po`

| 文件 | 作用 |
| --- | --- |
| `Activity.java` | 活动数据库表对应对象。 |
| `Award.java` | 奖品数据库表对应对象。 |
| `Strategy.java` | 策略主表对应对象。 |
| `StrategyDetail.java` | 策略明细表对应对象。 |
| `UserTakeActivity.java` | 用户参与活动记录表对应对象。 |
| `UserTakeActivityCount.java` | 用户领取次数表对应对象。 |
| `UserStrategyExport.java` | 用户中奖发奖单表对应对象。 |
| `RuleTree.java` | 规则树主表对应对象。 |
| `RuleTreeNode.java` | 规则树节点表对应对象。 |
| `RuleTreeNodeLine.java` | 规则树连线表对应对象。 |

### 10.3 仓储实现 `infrastructure/repository`

| 文件 | 作用 |
| --- | --- |
| `ActivityRepository.java` | 实现 `IActivityRepository`，把活动领域对象转换为 PO 并调用 DAO。 |
| `UserTakeActivityRepository.java` | 实现 `IUserTakeActivityRepository`，处理用户参与、库存、发奖单、分库分表数据。 |
| `StrategyRepository.java` | 实现 `IStrategyRepository`，查询策略聚合、奖品信息、扣减策略奖品库存。 |
| `RuleRepository.java` | 实现 `IRuleRepository`，组装完整规则树聚合。 |
| `OrderRepository.java` | 实现 `IOrderRepository`，处理发奖订单相关持久化。 |

### 10.4 工具 `infrastructure/util`

| 文件 | 作用 |
| --- | --- |
| `RedisUtil.java` | Redis 操作工具类，封装 String、Hash、List、Set、过期时间等常用操作。 |

## 11. `lottery-application` 模块

应用层负责协调多个领域服务，完成一个完整业务用例。它不直接处理 HTTP，也不应该写复杂业务规则。

### 11.1 活动部署流程

| 文件 | 作用 |
| --- | --- |
| `process/deploy/IActivityDeployProcess.java` | 活动部署应用流程接口。 |
| `process/deploy/impl/ActivityDeployProcessImpl.java` | 活动部署应用流程实现，对外查询活动列表时组合领域查询和返回对象。 |

### 11.2 抽奖流程

| 文件 | 作用 |
| --- | --- |
| `process/draw/IActivityDrawProcess.java` | 抽奖应用流程接口。 |
| `process/draw/req/DrawProcessReq.java` | 抽奖流程请求对象，包含用户和活动信息。 |
| `process/draw/res/DrawProcessResult.java` | 抽奖流程结果，继承公共 `Result`。 |
| `process/draw/res/RuleQuantificationCrowdResult.java` | 量化人群规则计算结果，表示用户命中的策略或规则结果。 |
| `process/draw/impl/ActivityDrawProcessImpl.java` | 完整抽奖流程实现：参与活动、执行抽奖、生成发奖单、同步/异步发奖。 |

### 11.3 MQ 能力

| 文件 | 作用 |
| --- | --- |
| `mq/producer/KafkaProducer.java` | Kafka 生产者封装，用于发送活动参与记录、发奖单等消息。 |
| `mq/consumer/LotteryActivityPartakeRecordListener.java` | 活动参与记录消费者，用于异步处理用户参与后的后续动作。 |
| `mq/consumer/LotteryInvoiceListener.java` | 发奖单消费者，用于异步执行奖品发放。 |

### 11.4 定时任务

| 文件 | 作用 |
| --- | --- |
| `worker/LotteryXxlJobConfig.java` | XXL-Job 执行器配置，根据配置注册任务执行器。 |
| `worker/LotteryXxlJob.java` | 定时任务实现。常见任务包括扫描活动状态、处理发奖单状态等。 |

## 12. `lottery-interfaces` 模块

接口层是最终启动模块。它把领域能力包装成 HTTP、静态 UI 和 Dubbo 服务。

### 12.1 主程序和 HTTP 接口

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/windseed/lottery/LotteryApplication.java` | Spring Boot 启动类。运行项目时从这里启动。启用 Dubbo，并加载各模块 Spring Bean。 |
| `interfaces/web/LotteryApiController.java` | 本地 UI 使用的 HTTP API 控制器。提供健康检查、诊断、活动创建、状态流转、参与活动、抽奖、规则决策、奖品发放、任务扫描等接口。 |

`LotteryApiController` 主要接口：

| 接口 | 作用 |
| --- | --- |
| `GET /api/lottery/health` | 健康检查。 |
| `GET /api/lottery/metadata` | 返回前端下拉选项，如活动状态、策略模式、奖品类型。 |
| `GET /api/lottery/diagnostics` | 返回运行时、数据库、数据量和告警信息。 |
| `GET /api/lottery/activities` | 分页查询活动。 |
| `POST /api/lottery/activities` | 创建活动、策略和奖品配置。 |
| `POST /api/lottery/activity/state` | 活动状态流转。 |
| `POST /api/lottery/partake` | 用户参与活动。 |
| `POST /api/lottery/draw` | 用户抽奖。 |
| `POST /api/lottery/quantification-draw` | 基于规则树的量化人群抽奖。 |
| `POST /api/lottery/rules/decision` | 单独执行规则树决策。 |
| `GET /api/lottery/strategies` | 查询策略列表。 |
| `GET /api/lottery/strategies/{strategyId}` | 查询策略聚合详情。 |
| `GET /api/lottery/awards` | 查询奖品列表。 |
| `GET /api/lottery/awards/{awardId}` | 查询单个奖品简要信息。 |
| `GET /api/lottery/rules` | 查询规则树列表。 |
| `GET /api/lottery/rules/{treeId}` | 查询规则树详情。 |
| `GET /api/lottery/users/{uId}/take-count` | 查询用户某活动领取次数。 |
| `GET /api/lottery/users/{uId}/take-records` | 查询用户参与记录。 |
| `GET /api/lottery/users/{uId}/awards` | 查询用户中奖/发奖记录。 |
| `GET /api/lottery/todo-activities` | 查询待扫描处理的活动。 |
| `POST /api/lottery/jobs/activity-state-scan` | 手动触发活动状态扫描任务。 |
| `POST /api/lottery/mq/scan` | 扫描待处理 MQ 发奖单据。 |
| `POST /api/lottery/distribution` | 手动触发奖品发放。 |

### 12.2 Dubbo Facade

| 文件 | 作用 |
| --- | --- |
| `interfaces/facade/LotteryActivityDeploy.java` | 实现 `ILotteryActivityDeploy`，对外提供活动列表查询等活动部署能力。 |
| `interfaces/facade/LotteryActivityBooth.java` | 实现 `ILotteryActivityBooth`，对外提供抽奖和量化人群抽奖能力。 |

### 12.3 MapStruct 对象转换

| 文件 | 作用 |
| --- | --- |
| `interfaces/assembler/IMapping.java` | MapStruct 通用映射接口，定义单对象和集合转换方法。 |
| `interfaces/assembler/ActivityMapping.java` | 活动领域对象 `ActivityVO` 到 RPC DTO `ActivityDTO` 的转换器。 |
| `interfaces/assembler/AwardMapping.java` | 领域中奖奖品对象 `DrawAwardVO` 到 RPC DTO `AwardDTO` 的转换器。 |

### 12.4 配置文件

| 文件 | 作用 |
| --- | --- |
| `src/main/resources/application.yml` | 默认运行配置。包含端口 `8081`、Kafka、Redis、XXL-Job、分库分表数据源、MyBatis、Nacos、Dubbo 等配置。 |
| `src/main/resources/application-local.yml` | 本地运行覆盖配置。关闭 Kafka 自动消费、Kafka 生产、Nacos 自动注册、XXL-Job 等外部依赖，让项目更容易在 Windows 本地直接运行。 |
| `src/main/resources/mybatis/config/mybatis-config.xml` | MyBatis 全局配置。 |
| `src/main/resources/mybatis/mapper/Activity_Mapper.xml` | 活动表 SQL 映射。 |
| `src/main/resources/mybatis/mapper/Award_Mapper.xml` | 奖品表 SQL 映射。 |
| `src/main/resources/mybatis/mapper/Strategy_Mapper.xml` | 策略主表 SQL 映射。 |
| `src/main/resources/mybatis/mapper/StrategyDetail_Mapper.xml` | 策略明细表 SQL 映射。 |
| `src/main/resources/mybatis/mapper/UserTakeActivity_Mapper.xml` | 用户参与活动记录 SQL 映射。 |
| `src/main/resources/mybatis/mapper/UserTakeActivityCount_Mapper.xml` | 用户领取活动次数 SQL 映射。 |
| `src/main/resources/mybatis/mapper/UserStrategyExport_Mapper.xml` | 用户中奖发奖单 SQL 映射。 |
| `src/main/resources/mybatis/mapper/RuleTree_Mapper.xml` | 规则树主表 SQL 映射。 |
| `src/main/resources/mybatis/mapper/RuleTreeNode_Mapper.xml` | 规则树节点 SQL 映射。 |
| `src/main/resources/mybatis/mapper/RuleTreeNodeLine_Mapper.xml` | 规则树连线 SQL 映射。 |

### 12.5 本地 UI 静态资源

| 文件 | 作用 |
| --- | --- |
| `src/main/resources/static/index.html` | 浏览器打开 `http://localhost:8081/` 时加载的管理界面入口页面。 |
| `src/main/resources/static/css/lottery-console.css` | 本地 UI 样式文件，控制布局、颜色、表格、表单、按钮、响应式展示等。 |
| `src/main/resources/static/js/lottery-console.js` | 本地 UI 交互逻辑。负责调用 `/api/lottery/**` 接口、渲染活动/策略/奖品/规则/用户记录/诊断信息，并处理创建、状态流转、抽奖、发奖等操作。 |

### 12.6 测试代码

| 文件 | 作用 |
| --- | --- |
| `src/test/java/com/windseed/lottery/test/SpringRunnerTest.java` | Spring Boot 测试基类，通常负责加载测试上下文。 |
| `src/test/java/com/windseed/lottery/test/ApiTest.java` | 通用接口或基础功能测试入口。 |
| `test/application/ActivityProcessTest.java` | 活动流程测试。 |
| `test/application/KafkaProducerTest.java` | Kafka 生产者测试。 |
| `test/domain/ActivityTest.java` | 活动领域逻辑测试。 |
| `test/domain/DrawAlgorithmTest.java` | 抽奖算法测试。 |
| `test/domain/RuleTest.java` | 规则引擎测试。 |
| `test/domain/SupportTest.java` | ID、Redis 或支撑能力测试。 |
| `test/infrastructure/ActivityDaoTest.java` | 活动 DAO 测试。 |
| `test/infrastructure/RedisUtilTest.java` | Redis 工具测试。 |
| `test/infrastructure/UserStrategyExportDaoTest.java` | 用户中奖发奖 DAO 测试。 |
| `test/infrastructure/UserTakeActivityDaoTest.java` | 用户参与记录 DAO 测试。 |
| `test/interfaces/LotteryActivityBoothTest.java` | 抽奖 RPC Facade 测试。 |
| `test/interfaces/LotteryActivityDeployTest.java` | 活动部署 RPC Facade 测试。 |
| `test/vo2dto/*` | 对象转换和 Java Bean 拷贝示例测试，包含多组 `User`、`Page`、`UserDTO`、`ApiTest01` 到 `ApiTest06`。 |

## 13. `doc` 目录

`doc` 是资料目录，里面大多数文件不是运行必须代码，但对理解数据库、部署、需求、设计非常重要。

### 13.1 SQL

| 文件 | 作用 |
| --- | --- |
| `doc/assets/sql/lottery.sql` | 主库 SQL，包含活动、策略、奖品、规则等基础表和示例数据。 |
| `doc/assets/sql/lottery_01.sql` | 分库 `lottery_01` SQL，通常包含用户参与活动、发奖单等分表。 |
| `doc/assets/sql/lottery_02.sql` | 分库 `lottery_02` SQL，结构类似 `lottery_01`，用于分库分表测试。 |
| `doc/assets/sql/xxl-job.sql` | XXL-Job 调度中心数据库 SQL。 |
| `doc/docker/mysql/db/*.sql` | Docker 部署版本使用的数据库初始化 SQL，内容对应 Lottery、Nacos、XXL-Job 等服务。 |

### 13.2 Docker 部署

| 文件 | 作用 |
| --- | --- |
| `doc/docker/docker-compose.yml` | Docker Compose 编排文件，定义 MySQL、Redis、Nacos、Kafka、XXL-Job、Lottery 服务等容器。 |
| `doc/docker/deploy.sh` | Linux/Docker 部署脚本。 |
| `doc/docker/README-docker-compose版.md` | Docker Compose 版部署说明。 |
| `doc/docker/lottery部署docker版.md` | Lottery Docker 部署说明。 |
| `doc/docker/docker小白注意事项.md` | Docker 入门注意事项。 |
| `doc/docker/mysql/Dockerfile` | MySQL 镜像构建文件。 |
| `doc/docker/redis/Dockerfile` | Redis 镜像构建文件。 |
| `doc/docker/redis/conf/redis.conf` | Redis 容器配置。 |
| `doc/docker/nacos/Dockerfile` | Nacos 镜像构建文件。 |
| `doc/docker/nacos/conf/application.properties` | Nacos 服务配置。 |
| `doc/docker/kafka/kafka.md` | Kafka 部署或使用说明。 |
| `doc/docker/xxl-job/xxl-job.md` | XXL-Job 部署或使用说明。 |
| `doc/docker/lottery/api/Dockerfile` | Lottery API 服务镜像构建文件。 |
| `doc/docker/lottery/draw/Dockerfile` | Lottery 抽奖服务镜像构建文件。 |
| `doc/docker/lottery/erp/Dockerfile` | Lottery ERP/后台服务镜像构建文件。 |
| `doc/docker/lottery/erp/conf/server.xml` | ERP 服务容器的 Tomcat 配置。 |
| `doc/docker/website/Dockerfile` | 静态网站镜像构建文件。 |
| `doc/docker/website/conf/nginx.conf` | Nginx 主配置。 |
| `doc/docker/website/conf/conf.d/default.conf` | Nginx 站点配置。 |
| `doc/docker/website/html/index.html` | Docker 网站示例首页。 |
| `doc/docker/website/html/50x.html` | Docker 网站错误页。 |

### 13.3 设计资料和学习资料

| 路径 | 作用 |
| --- | --- |
| `doc/notes/介绍.md` | 项目介绍文档。 |
| `doc/notes/加入项目.md` | 加入或参与项目说明。 |
| `doc/assets/xmind/*.xmind` | XMind 脑图，按章节记录抽奖系统架构、领域建设、库表设计、RPC、策略模块等内容。 |
| `doc/assets/xmind/梳理/*.xmind` | 需求梳理、库表梳理、领域建设等专题脑图。 |
| `doc/assets/visio/*.drawio` | Draw.io 设计图，包括营销系统、抽奖系统、领域模型。 |
| `doc/assets/excel/*.xlsx` | Excel 资料，包括库表设计、项目学习意见等。 |
| `doc/assets/ppt/*.pptx` | PPT 资料，按功能领域、运营后台、应用场景、系统运维等部分组织。 |
| `doc/assets/redis/redis.conf` | Redis 配置示例。 |
| `doc/assets/other/settings.xml` | Maven `settings.xml` 示例，可用于配置仓库镜像。 |
| `doc/assets/natapp/*` | 内网穿透工具及配置示例。 |

### 13.4 图片和静态演示资源

| 路径 | 作用 |
| --- | --- |
| `doc/_media/*` | README 或介绍文档使用的封面图、架构图、部署截图等媒体资源。 |
| `doc/assets/img/Part-*/*.png` | 按章节保存的教程截图和系统说明图片。 |
| `doc/assets/Portainer-CN/*` | Portainer 中文静态页面资源，包含 HTML、CSS、JS、图片、字体、图标等，用于 Docker 管理界面资料或演示。 |

## 14. 构建产物和本地临时文件

这些文件由 Maven、IDEA 或本地启动脚本生成，一般不需要手动修改，也不建议提交：

| 路径 | 作用 |
| --- | --- |
| `*/target/` | Maven 编译输出目录，包含 `.class`、打包后的 `.jar/.war`、复制后的配置文件和静态资源。 |
| `.idea/` | IntelliJ IDEA 项目配置目录，大部分是个人本地配置。 |
| `run-lottery-*.log` | 本地服务运行日志。 |
| `run-lottery-*.err.log` | 本地服务错误输出日志。 |

## 15. 关键业务链路

### 15.1 创建活动

```text
UI 页面
-> LotteryApiController.POST /api/lottery/activities
-> IActivityDeploy.createActivity
-> ActivityDeployImpl
-> ActivityRepository / StrategyRepository
-> MyBatis Mapper
-> MySQL
```

创建活动时会同时保存活动主信息、策略主信息、策略明细和奖品信息。

### 15.2 活动状态流转

```text
UI 页面
-> LotteryApiController.POST /api/lottery/activity/state
-> IStateHandler
-> StateHandlerImpl
-> 当前状态类，例如 EditingState / PassState / DoingState
-> ActivityRepository.alterState
-> MySQL
```

状态机保证活动不能随意跳状态。例如编辑态可以提审，审核通过后可以进入运行态，运行态可以关闭。

### 15.3 用户参与活动

```text
UI 页面
-> LotteryApiController.POST /api/lottery/partake
-> IActivityPartake.doPartake
-> ActivityPartakeImpl / BaseActivityPartake
-> Redis 库存锁 + MySQL 领取记录
```

参与活动会校验活动是否可用、库存是否足够、用户是否超过领取次数，并生成参与记录。

### 15.4 抽奖

```text
UI 页面
-> LotteryApiController.POST /api/lottery/draw
-> ILotteryActivityBooth.doDraw
-> LotteryActivityBooth
-> ActivityDrawProcessImpl
-> ActivityPartakeImpl
-> DrawExecImpl
-> EntiretyRateRandomDrawAlgorithm / SingleRateRandomDrawAlgorithm
-> UserStrategyExport 发奖单
```

抽奖流程先确保用户拥有一次活动参与资格，再执行策略算法，最后生成中奖/未中奖结果和发奖单。

### 15.5 规则树量化人群抽奖

```text
UI 页面
-> LotteryApiController.POST /api/lottery/quantification-draw
-> RuleEngineHandle
-> UserAgeFilter / UserGenderFilter
-> 命中策略 ID
-> DrawExecImpl
```

规则树用于根据用户年龄、性别等属性决定用户进入哪个策略。

### 15.6 发奖

```text
中奖结果
-> UserStrategyExport 发奖单
-> DistributionGoodsFactory
-> DescGoods / RedeemCodeGoods / CouponGoods / PhysicalGoods
-> 更新发奖状态
```

本地模式下可以同步或手动触发发奖；完整部署时也可以通过 Kafka 消费发奖单。

## 16. 如何阅读这个项目

建议按下面顺序阅读：

1. 先看 `pom.xml`，理解多模块结构。
2. 再看 `lottery-interfaces/src/main/java/com/windseed/lottery/LotteryApplication.java`，知道程序从哪里启动。
3. 看 `LotteryApiController.java`，理解 UI 能调用哪些功能。
4. 看 `lottery-domain/domain/activity`，理解活动创建、参与和状态流转。
5. 看 `lottery-domain/domain/strategy`，理解抽奖算法。
6. 看 `lottery-domain/domain/rule`，理解规则树人群过滤。
7. 看 `lottery-infrastructure/repository` 和 `dao`，理解领域对象如何落库。
8. 看 `lottery-interfaces/src/main/resources/mybatis/mapper`，理解每个 DAO 对应的 SQL。
9. 看 `lottery-interfaces/src/main/resources/static`，理解本地 UI 如何调用后端接口。
10. 最后看 `doc/assets/sql` 和 `doc/docker`，理解数据库结构和部署方式。
