# Lottery 项目面试问题与参考答案

这份文档按面试场景整理，目的是让你能把这个抽奖系统讲清楚。每个问题都包含“参考回答”和“项目结合点”，面试时可以先用 1 到 2 分钟讲核心，再根据面试官追问展开代码细节。

## 1. 项目介绍类

### 1. 你这个项目是做什么的？

参考回答：

这是一个面向营销场景的抽奖系统，核心能力包括活动配置、奖品配置、抽奖策略配置、活动状态流转、用户参与活动、执行抽奖、规则树人群过滤、中奖发奖、异步消息处理和定时任务扫描。项目采用 Java 8、Spring Boot、MyBatis、MySQL、Redis、Dubbo、Kafka、XXL-Job，并按照 DDD 分层拆成多个 Maven 模块。

项目结合点：

- 启动入口：`lottery-interfaces/src/main/java/com/windseed/lottery/LotteryApplication.java`
- HTTP 管理接口：`lottery-interfaces/src/main/java/com/windseed/lottery/interfaces/web/LotteryApiController.java`
- 核心领域：`lottery-domain`
- 数据库 SQL：`doc/assets/sql`

### 2. 这个项目主要解决哪些业务问题？

参考回答：

主要解决营销活动中的抽奖发奖问题。业务上要支持运营配置活动和奖品，用户在活动时间内参与抽奖，系统按配置的概率和规则判断是否中奖，并控制库存不能超卖。对于不同用户，还可以通过规则树做用户分层，例如按性别、年龄命中不同抽奖策略。中奖后生成发奖单，再通过同步或异步方式发放奖品。

项目结合点：

- 活动配置：`ActivityDeployImpl`
- 用户参与：`ActivityPartakeImpl`
- 抽奖执行：`DrawExecImpl`
- 规则树决策：`RuleEngineHandle`
- 发奖工厂：`DistributionGoodsFactory`

### 3. 你在项目里负责或重点理解了哪些模块？

参考回答：

可以重点讲 5 个模块：第一是抽奖策略模块，包括单项概率和总体概率两种算法；第二是活动状态机，限制活动从编辑、提审、审核通过、运行、关闭等状态按规则流转；第三是 Redis 扣库存，降低数据库压力并控制超卖；第四是分库分表组件，通过自定义注解、AOP、动态数据源和 MyBatis 拦截器实现路由；第五是应用层流程编排，把参与活动、抽奖、发奖、MQ 消息串成完整流程。

项目结合点：

- `lottery-domain/domain/strategy`
- `lottery-domain/domain/activity/service/stateflow`
- `ActivityRepository.subtractionActivityStockByRedis`
- `db-router-spring-boot-starter`
- `ActivityDrawProcessImpl`

### 4. 项目的整体调用链路是什么？

参考回答：

本地 UI 或外部系统先调用接口层。接口层的 Controller 或 Dubbo Facade 接收请求后，调用应用层流程服务。应用层再调用领域服务完成业务判断，比如参与活动、抽奖、规则树过滤、发奖。领域层依赖仓储接口，基础设施层实现仓储接口并通过 MyBatis、Redis、MySQL 落地数据。

项目链路：

```text
UI / HTTP / Dubbo
-> lottery-interfaces
-> lottery-application
-> lottery-domain
-> lottery-infrastructure
-> MyBatis / Redis / MySQL
```

## 2. 架构与分层类

### 5. 为什么项目要拆成这么多 Maven 模块？

参考回答：

拆模块是为了降低耦合，让每层职责清晰。`lottery-common` 放公共对象，`lottery-rpc` 放对外契约，`lottery-domain` 放业务规则，`lottery-infrastructure` 放数据库和缓存实现，`lottery-application` 放流程编排，`lottery-interfaces` 放启动入口和对外接口。这样领域逻辑不会直接依赖 Controller 或 SQL，后续如果要把 HTTP 改成 Dubbo、或者把 MySQL 替换成其他存储，对核心业务影响较小。

项目结合点：

- 父 POM：`pom.xml`
- 模块依赖从接口层向内收敛，领域层通过仓储接口隔离基础设施。

### 6. 这个项目是怎么体现 DDD 的？

参考回答：

项目按领域拆分为活动、策略、规则、奖品等子域。每个领域内又有模型、仓储接口和领域服务。比如活动领域包含活动配置聚合、参与活动请求、活动状态机、活动仓储接口；策略领域包含策略聚合、抽奖算法、抽奖执行服务；规则领域包含规则树聚合和规则引擎。应用层不写复杂业务规则，只负责串联多个领域服务完成用例。

项目结合点：

- 活动领域：`lottery-domain/src/main/java/com/windseed/lottery/domain/activity`
- 策略领域：`lottery-domain/src/main/java/com/windseed/lottery/domain/strategy`
- 规则领域：`lottery-domain/src/main/java/com/windseed/lottery/domain/rule`
- 奖品领域：`lottery-domain/src/main/java/com/windseed/lottery/domain/award`

### 7. 领域层和应用层有什么区别？

参考回答：

领域层负责业务规则本身，比如活动能不能参与、库存如何扣减、状态是否允许流转、抽奖算法如何选择奖品。应用层负责流程编排，比如一次抽奖流程要先参与活动、再执行抽奖、再生成发奖单、再发 MQ 或同步发奖。应用层不应该沉淀复杂业务规则，否则业务会散乱。

项目结合点：

- 领域层：`ActivityPartakeImpl`、`DrawExecImpl`、`RuleEngineHandle`
- 应用层：`ActivityDrawProcessImpl`

### 8. 为什么要有 repository 接口和 infrastructure 实现？

参考回答：

这是依赖倒置。领域层定义自己需要的持久化能力，例如查询活动、扣减库存、保存参与记录，但不关心具体用 MyBatis 还是其他存储。基础设施层实现这些接口，真正调用 DAO、Redis、MySQL。这样领域逻辑更稳定，也更方便测试和替换技术实现。

项目结合点：

- 领域接口：`IActivityRepository`、`IStrategyRepository`、`IRuleRepository`
- 基础设施实现：`ActivityRepository`、`StrategyRepository`、`RuleRepository`

### 9. VO、DTO、PO、REQ、RES 分别是什么？

参考回答：

PO 是数据库持久化对象，字段通常和表结构对应；VO 是领域值对象，表达业务概念；DTO 是对外传输对象，适合 RPC 或接口返回；REQ 是请求对象，RES 是响应对象。这样拆分可以避免数据库结构直接暴露给外部，也能让领域模型不被接口格式绑死。

项目结合点：

- PO：`lottery-infrastructure/infrastructure/po`
- VO：`lottery-domain/domain/**/model/vo`
- DTO：`lottery-rpc/**/dto`
- REQ/RES：`lottery-domain/domain/**/model/req|res`、`lottery-rpc/**/req|res`

### 10. 这个项目用了哪些设计模式？

参考回答：

主要有模板方法、策略模式、工厂模式、状态模式、仓储模式和适配/装配模式。抽奖流程 `AbstractDrawBase` 是模板方法；抽奖算法 `IDrawAlgorithm` 的不同实现是策略模式；奖品发放 `DistributionGoodsFactory` 是工厂模式；活动状态流转是状态模式；领域仓储接口和基础设施实现是仓储模式；MapStruct 负责领域对象到 DTO 的装配转换。

项目结合点：

- 模板方法：`AbstractDrawBase`、`BaseActivityPartake`
- 策略模式：`IDrawAlgorithm`
- 工厂模式：`DistributionGoodsFactory`
- 状态模式：`IStateHandler`、`AbstractState`
- 仓储模式：`IActivityRepository` 等
- 装配转换：`ActivityMapping`、`AwardMapping`

## 3. 抽奖业务类

### 11. 抽奖主流程是什么？

参考回答：

抽奖主流程是：用户发起抽奖请求，系统先判断用户是否能参与活动，参与成功后会生成一次活动参与记录；然后根据活动绑定的策略执行抽奖算法；如果中奖，扣减策略奖品库存并生成中奖发奖单；最后根据本地模式或消息模式进行奖品发放。

项目链路：

```text
LotteryApiController.draw
-> LotteryActivityBooth.doDraw
-> ActivityDrawProcessImpl.doDrawProcess
-> ActivityPartakeImpl.doPartake
-> DrawExecImpl.doDrawExec
-> UserStrategyExport
```

### 12. 为什么抽奖前要先参与活动？

参考回答：

因为参与活动本身要做库存和次数控制。一个用户是否有抽奖资格，不只是看策略概率，还要看活动是否在有效时间内、活动是否处于运行状态、库存是否充足、用户领取次数是否超过限制。先参与活动可以生成一次唯一的参与记录，后续抽奖和发奖都可以基于这个记录做幂等和追踪。

项目结合点：

- 参与活动：`BaseActivityPartake.doPartake`
- 用户参与记录：`user_take_activity`
- 用户领取次数：`user_take_activity_count`

### 13. 单项概率和总体概率有什么区别？

参考回答：

单项概率是每个奖品按原始概率独立占位。如果某个奖品库存没了，用户抽中这个奖品时就会变成未中奖，不会把概率分给其他奖品。总体概率是在有奖品库存为空时，把剩余奖品按照剩余概率重新分布，让用户仍然在有库存的奖品中抽。单项概率更适合严格控制运营成本，总体概率更适合提升中奖体验。

项目结合点：

- 单项概率：`SingleRateRandomDrawAlgorithm`
- 总体概率：`EntiretyRateRandomDrawAlgorithm`
- 枚举：`Constants.StrategyMode`

### 14. 抽奖算法是怎么保证按概率中奖的？

参考回答：

系统会把每个奖品的中奖概率转换成一个概率范围或概率元组，然后生成随机数，看随机数落在哪个奖品的区间内。比如奖品 A 概率 20%，B 概率 30%，C 概率 50%，就可以把随机区间切成 A、B、C 三段。随机数均匀分布时，落入各区间的比例接近配置概率。

项目结合点：

- 抽象能力：`BaseAlgorithm`
- 算法接口：`IDrawAlgorithm`
- 概率配置表：`strategy_detail.award_rate`

### 15. 抽奖时如何处理奖品库存？

参考回答：

抽奖命中奖品后会扣减策略奖品库存。当前策略奖品库存扣减主要依赖数据库更新条件控制，例如只有剩余库存大于 0 才能扣减成功。活动参与资格的总库存使用 Redis 做预扣减，减少高并发下数据库压力。一个控制活动资格库存，一个控制具体奖品库存，两者配合防止活动和奖品超发。

项目结合点：

- 活动库存 Redis 扣减：`ActivityRepository.subtractionActivityStockByRedis`
- 策略奖品库存扣减：`StrategyRepository`、`IStrategyDetailDao`
- 策略明细表：`strategy_detail`

### 16. 如果用户抽中了一个库存已经没了的奖品怎么办？

参考回答：

这取决于策略模式。单项概率模式下，抽中无库存奖品会返回未中奖，概率不转移。总体概率模式下，算法会从有库存的奖品中重新计算概率，尽量避免命中无库存奖品。最终扣库存时也要以数据库更新结果为准，如果扣减失败，应该按未中奖或重试策略处理。

项目结合点：

- `SingleRateRandomDrawAlgorithm`
- `EntiretyRateRandomDrawAlgorithm`
- `DrawExecImpl`

### 17. 为什么抽奖系统不能只靠前端控制概率？

参考回答：

前端不可信，用户可以改请求、重放请求或绕过页面直接调用接口。中奖概率、库存扣减、次数限制、状态校验都必须在服务端完成。前端最多负责展示和交互，真正的业务判断必须落在领域层和数据库/Redis 的原子操作上。

项目结合点：

- 前端：`static/js/lottery-console.js`
- 后端：`LotteryApiController`、`ActivityDrawProcessImpl`、`DrawExecImpl`

### 18. 怎么防止一个用户重复抽奖？

参考回答：

可以从三个层面控制。第一，用户活动领取次数表记录可参与次数和剩余次数；第二，参与记录表用防重 ID 或唯一索引避免同一用户同一次活动重复记录；第三，发奖单也有唯一业务 ID，避免重复发奖。接口层也可以增加幂等 token，进一步防止重复提交。

项目结合点：

- `user_take_activity_count` 唯一索引：`u_id + activity_id`
- `user_take_activity` 唯一索引：`uuid`
- `user_strategy_export` 唯一索引：`uuid`

### 19. 抽奖系统如何做到可扩展？

参考回答：

抽奖算法通过 `IDrawAlgorithm` 抽象，不同算法作为不同实现注册；奖品发放通过 `IDistributionGoods` 和工厂扩展；规则过滤通过 `LogicFilter` 扩展，比如新增地区、会员等级等过滤条件时，只要新增过滤器并配置到规则引擎；接口层和领域层分离，也便于新增 HTTP 或 Dubbo 调用方式。

项目结合点：

- 算法扩展：`IDrawAlgorithm`
- 发奖扩展：`IDistributionGoods`
- 规则扩展：`LogicFilter`

## 4. 活动状态机类

### 20. 为什么活动要设计状态机？

参考回答：

活动从创建到上线不是随意变化的，需要有严格流程，比如编辑、提审、审核通过、运行、关闭。状态机可以限制非法流转，避免活动未审核就运行、已关闭又被错误抽奖等问题。它把状态和动作集中管理，比在业务代码里到处写 if else 更清晰。

项目结合点：

- 状态入口：`StateHandlerImpl`
- 状态配置：`StateConfig`
- 状态实现：`EditingState`、`PassState`、`DoingState` 等

### 21. 活动有哪些状态？

参考回答：

项目中活动状态定义在 `Constants.ActivityState`，包括编辑、提审、撤审、审核通过、运行中、拒绝、关闭、开启等。实际业务中常见流程是编辑态创建活动，提审后等待审核，审核通过后由定时任务或手动操作进入运行态，活动结束后进入关闭态。

项目结合点：

- `lottery-common/src/main/java/com/windseed/lottery/common/Constants.java`

### 22. 如果活动已经关闭，还能抽奖吗？

参考回答：

不能。用户参与活动时要校验活动状态、活动时间和库存，只有运行中且在有效时间范围内的活动才允许参与和抽奖。关闭状态应该直接拒绝请求。这样可以避免过期活动继续消耗库存或生成发奖单。

项目结合点：

- `BaseActivityPartake.checkActivityBill`
- `IActivityRepository.queryActivityBill`

### 23. 状态机相比简单 if else 有什么好处？

参考回答：

状态机把每个状态允许的动作放到对应状态类中，职责更清楚。新增状态或调整流转时，修改范围更小。相比一个巨大的 if else，状态机更容易测试，也更容易让业务人员理解当前状态能做什么、不能做什么。

项目结合点：

- `AbstractState` 定义默认不可执行行为
- 各 `event/*State.java` 覆盖允许的动作

## 5. 规则引擎类

### 24. 项目里的规则树是用来做什么的？

参考回答：

规则树用于做用户分层和策略选择。比如先判断用户性别，再判断年龄，不同条件命中不同叶子节点，叶子节点可以配置不同抽奖策略。这样运营可以把复杂的人群规则配置成树结构，而不是把条件写死在代码里。

项目结合点：

- 规则树表：`rule_tree`
- 节点表：`rule_tree_node`
- 连线表：`rule_tree_node_line`
- 引擎：`RuleEngineHandle`

### 25. 规则树执行流程是什么？

参考回答：

规则引擎先根据规则树 ID 查询完整树结构，从根节点开始执行。每个节点有一个规则 Key，例如用户年龄或性别。引擎根据规则 Key 找到对应 `LogicFilter`，用用户属性和节点连线条件做比较，命中后进入下一个节点。直到走到果实节点，返回对应的策略 ID 或结果。

项目结合点：

- `EngineBase.engineDecisionMaker`
- `UserAgeFilter`
- `UserGenderFilter`
- `TreeRuleRich`

### 26. 如果要增加“用户城市”规则，怎么改？

参考回答：

可以新增一个 `UserCityFilter` 实现 `LogicFilter` 或继承 `BaseLogic`，实现根据 `valMap` 中城市字段做比较的逻辑；然后在 `EngineConfig` 中把规则 key 和过滤器注册起来；最后在数据库规则树节点配置 `userCity`，并配置节点连线条件即可。这样不需要改抽奖主流程。

项目结合点：

- 新增类位置：`lottery-domain/domain/rule/service/logic/impl`
- 注册位置：`EngineConfig`
- 数据配置：`rule_tree_node.tree_node_rule_key`

### 27. 规则树和策略模式有什么关系？

参考回答：

规则树负责决定用户进入哪个策略，策略模式负责在该策略里怎么抽奖。规则树解决“这个用户应该走哪套规则”，抽奖策略解决“这套规则下奖品如何按概率分配”。两者组合后可以实现不同人群使用不同奖品池或中奖概率。

项目结合点：

- 规则树返回策略 ID
- `DrawExecImpl` 根据策略 ID 查询 `StrategyRich`

## 6. Redis 与并发库存类

### 28. 项目里 Redis 用在什么地方？

参考回答：

Redis 主要用于活动库存的高并发扣减。活动创建时会初始化活动库存使用数量，用户参与活动时先在 Redis 中预占库存，并通过 token key 控制某一次库存占用，减少所有请求直接打到 MySQL 的压力。项目还封装了 `RedisUtil`，支持常见数据结构操作。

项目结合点：

- Redis 配置：`RedisConfig`
- Redis 工具：`RedisUtil`
- 库存扣减：`ActivityRepository.subtractionActivityStockByRedis`

### 29. Redis 扣库存怎么防止超卖？

参考回答：

项目采用 Redis 预扣减思路。先对活动库存使用数做递增，拿到当前已用库存数；如果超过活动总库存，说明库存不足。没超过时，再用 `setNx` 写入库存 token key，相当于占住这一个库存编号。因为 Redis 的递增和 setNx 都是原子操作，所以可以降低并发下多线程同时拿到同一个库存名额的风险。

项目结合点：

- `Constants.RedisKey.KEY_LOTTERY_ACTIVITY_STOCK_COUNT`
- `Constants.RedisKey.KEY_LOTTERY_ACTIVITY_STOCK_COUNT_TOKEN`
- `ActivityRepository.subtractionActivityStockByRedis`

### 30. Redis 扣了库存但数据库失败怎么办？

参考回答：

需要补偿。项目里有 `recoverActivityCacheStockByRedis`，如果后续参与记录保存失败或出现异常，会尝试恢复缓存库存。但在高并发场景中，缓存补偿可能不是 100% 成功，所以还需要定时任务或对账任务做最终一致性修复。面试时可以强调：系统追求不超卖优先，少量库存占用异常可以通过补偿解决。

项目结合点：

- `IActivityRepository.recoverActivityCacheStockByRedis`
- `BaseActivityPartake.doPartake`

### 31. 为什么活动库存用 Redis，奖品库存还用数据库？

参考回答：

活动库存是用户参与资格的总控制，流量高，适合 Redis 做快速预扣减。奖品库存和中奖结果强相关，扣减后要生成发奖单，对一致性要求更高，因此用数据库条件更新或行锁更直观。后续优化可以把奖品库存也放入 Redis，再通过异步任务落库，但要处理一致性、补偿和对账。

项目结合点：

- 活动库存：Redis
- 奖品库存：`strategy_detail.award_surplus_count`

### 32. Redis 分布式锁为什么要设置过期时间？

参考回答：

如果锁没有过期时间，服务异常退出或网络问题可能导致锁永远不释放，库存会永久被占用。项目里的库存 token key 会以活动结束时间作为有效期思路，这样活动结束后锁自然过期，避免长期占用。

项目结合点：

- `ActivityRepository.subtractionActivityStockByRedis`

### 33. Redis 缓存穿透、击穿、雪崩在这个项目里可能怎么处理？

参考回答：

如果查询活动、策略、规则树都走缓存，就要考虑这些问题。缓存穿透可以用空值缓存或布隆过滤器；缓存击穿可以对热点 key 加互斥锁或逻辑过期；缓存雪崩可以给 key 设置随机过期时间，避免同一时间大量失效。当前项目主要用 Redis 控库存，后续如果加活动详情和策略缓存，就需要补这些保护。

项目结合点：

- 可优化对象：活动配置、策略聚合、规则树聚合

## 7. MySQL 与分库分表类

### 34. 项目有哪些核心表？

参考回答：

主库里有活动表 `activity`、奖品表 `award`、策略表 `strategy`、策略明细表 `strategy_detail`、规则树表 `rule_tree`、规则节点表 `rule_tree_node`、规则连线表 `rule_tree_node_line`。分库里有用户参与记录表 `user_take_activity`、用户领取次数表 `user_take_activity_count`、用户中奖发奖单表 `user_strategy_export_000` 到 `003`。

项目结合点：

- SQL：`doc/assets/sql/lottery.sql`
- 分库 SQL：`doc/assets/sql/lottery_01.sql`、`lottery_02.sql`

### 35. 为什么用户参与记录要分库分表？

参考回答：

用户参与记录和中奖发奖单是高增长数据，随着用户量和活动次数增加，单表数据会快速变大，查询和写入性能下降。按用户 ID 分库分表后，可以把数据分散到多个库和表，降低单库单表压力，也方便后续水平扩展。

项目结合点：

- 分库：`lottery_01`、`lottery_02`
- 分表：`user_strategy_export_000` 到 `003`
- 路由字段：`uId`

### 36. 自定义分库分表组件怎么实现的？

参考回答：

组件通过 `@DBRouter` 注解标记需要路由的方法，AOP 切面 `DBRouterJoinPoint` 拦截方法调用，从参数中取出路由字段，调用 Hash 路由策略计算库编号和表编号，再写入 `DBContextHolder`。动态数据源 `DynamicDataSource` 根据上下文选择具体数据库。MyBatis 拦截器 `DynamicMybatisTableInterceptor` 在 SQL 执行前替换真实表名。

项目结合点：

- 注解：`DBRouter`
- 切面：`DBRouterJoinPoint`
- 上下文：`DBContextHolder`
- 动态数据源：`DynamicDataSource`
- 表名替换：`DynamicMybatisTableInterceptor`

### 37. 分库分表为什么使用 ThreadLocal？

参考回答：

一次请求在一个线程里执行，路由信息只对当前线程有效。用 `ThreadLocal` 可以把当前线程要访问的库表编号保存起来，动态数据源和 MyBatis 拦截器在同一线程内读取，不会影响其他并发请求。执行结束后要清理 ThreadLocal，避免线程池复用导致路由串数据。

项目结合点：

- `DBContextHolder`
- `DBRouterJoinPoint`

### 38. 分库分表后怎么做分页查询？

参考回答：

如果查询条件带路由键，比如按用户 ID 查询，可以直接定位到一个库表分页，效率较高。如果不带路由键，需要跨库跨表查询，再合并排序分页，复杂度和成本都比较高。所以设计接口时应尽量让高频查询带上路由键，运营类全局查询则可以走离线统计、搜索引擎或宽表。

项目结合点：

- 用户记录查询适合按 `uId`
- 活动、策略、奖品配置在主库，不需要分库分页

### 39. 分库分表后怎么保证唯一 ID？

参考回答：

不能依赖单库自增 ID 作为全局业务 ID，需要使用全局唯一 ID。项目里提供了雪花算法、短码、随机数字等 ID 生成策略。业务上参与记录、抽奖订单、发奖单都应该使用全局唯一 ID 或业务唯一键，避免跨库冲突。

项目结合点：

- `IIdGenerator`
- `SnowFlake`
- `ShortCode`
- `RandomNumeric`
- `IdContext`

### 40. MySQL 如何防止奖品库存超卖？

参考回答：

数据库层可以使用条件更新，例如 `update strategy_detail set award_surplus_count = award_surplus_count - 1 where strategy_id = ? and award_id = ? and award_surplus_count > 0`。只有更新行数为 1 才表示扣减成功。这个操作在 InnoDB 下是原子的，可以防止多个线程把库存扣成负数。

项目结合点：

- `Strategy_Mapper.xml`
- `StrategyDetail_Mapper.xml`
- `StrategyRepository`

### 41. 数据库索引怎么设计？

参考回答：

核心原则是围绕查询条件和唯一约束设计。活动表用 `activity_id` 唯一索引；奖品表用 `award_id` 唯一索引；策略表用 `strategy_id` 唯一索引；策略明细用 `strategy_id + award_id` 组合索引；用户领取次数用 `u_id + activity_id` 唯一索引；参与记录和发奖单用 `uuid` 防重。

项目结合点：

- SQL 文件中可看到 `UNIQUE KEY` 和组合索引。

## 8. 消息队列与定时任务类

### 42. Kafka 在项目中承担什么职责？

参考回答：

Kafka 用来做异步解耦。抽奖后生成发奖单或活动参与记录，可以先落库再发消息，由消费者异步执行后续发奖或记录处理。这样抽奖接口不用等待所有后续逻辑完成，降低接口响应时间，也能提高系统吞吐。

项目结合点：

- 生产者：`KafkaProducer`
- 消费者：`LotteryInvoiceListener`
- 消费者：`LotteryActivityPartakeRecordListener`

### 43. Kafka 消费失败怎么办？

参考回答：

消费失败不能直接丢消息。可以先记录失败日志，不提交 offset，或者把失败消息转入重试队列/死信队列。业务上要保证消费者幂等，因为 Kafka 可能重复投递。项目里发奖单有 MQ 状态和发奖状态，消费成功后更新状态，失败可以由定时任务扫描重试。

项目结合点：

- MQ 状态枚举：`Constants.MQState`
- 发奖状态枚举：`Constants.AwardState`
- 定时扫描：`LotteryXxlJob`

### 44. 如何保证消息不丢？

参考回答：

常见方案是本地消息表。业务数据和消息记录在同一个数据库事务里落库，之后由定时任务扫描未发送消息并投递 Kafka。Kafka 生产端开启确认机制，消费端处理成功后再提交 offset。这个项目已经有发奖单和 MQ 状态，可以作为本地消息表的基础。

项目结合点：

- `user_strategy_export` 中的 MQ 状态
- `LotteryXxlJob.lotteryOrderMQStateJobHandler`

### 45. 为什么需要 XXL-Job？

参考回答：

定时任务用于处理不适合实时接口完成的后台工作，比如扫描待开始或待关闭活动、补偿发奖消息、重试失败任务。XXL-Job 提供任务调度、执行器注册、日志和失败重试能力，比简单的 Spring `@Scheduled` 更适合分布式部署和运维管理。

项目结合点：

- 配置：`LotteryXxlJobConfig`
- 任务：`LotteryXxlJob`
- SQL：`doc/assets/sql/xxl-job.sql`

### 46. 活动状态扫描任务做什么？

参考回答：

活动状态扫描任务会查询待处理活动。比如活动已审核通过且到了开始时间，就把活动状态改成运行中；活动正在运行但已经过了结束时间，就改成关闭。这样运营只需要提前配置活动时间，系统可以自动上线和下线活动。

项目结合点：

- `LotteryXxlJob.lotteryActivityStateJobHandler`
- `IActivityDeploy.scanToDoActivityList`
- `IStateHandler.doing`
- `IStateHandler.close`

## 9. Dubbo 与接口类

### 47. 为什么项目同时有 HTTP API 和 Dubbo RPC？

参考回答：

HTTP API 主要用于本地 UI 和管理页面调用，方便调试和演示。Dubbo RPC 适合服务间调用，比如其他业务系统需要调用抽奖能力，就可以依赖 `lottery-rpc` 模块，通过 Dubbo 调用抽奖服务。两者面对的调用方不同，但底层复用同一套应用和领域能力。

项目结合点：

- HTTP：`LotteryApiController`
- Dubbo 接口：`ILotteryActivityDeploy`、`ILotteryActivityBooth`
- Dubbo 实现：`LotteryActivityDeploy`、`LotteryActivityBooth`

### 48. RPC 模块为什么只放接口和 DTO？

参考回答：

RPC 模块是服务契约，应该尽量轻量和稳定。消费者只需要知道接口、请求对象和响应对象，不应该依赖服务端内部领域模型、DAO 或实现类。这样服务端内部重构时，只要契约不变，消费者就不受影响。

项目结合点：

- `lottery-rpc`

### 49. MapStruct 在项目里有什么作用？

参考回答：

MapStruct 用于对象转换，比如把领域层的 `ActivityVO` 转成 RPC 层的 `ActivityDTO`，把中奖奖品 `DrawAwardVO` 转成 `AwardDTO`。它在编译期生成代码，比反射 BeanUtils 性能更好，也更容易发现字段映射问题。

项目结合点：

- `IMapping`
- `ActivityMapping`
- `AwardMapping`

## 10. Spring Boot 与配置类

### 50. Spring Boot 启动时加载了哪些核心配置？

参考回答：

项目从 `LotteryApplication` 启动，会加载 Spring Boot 自动配置、MyBatis Mapper、Redis 配置、Dubbo 配置、Kafka 配置、XXL-Job 配置，以及自定义的分库分表 Starter。默认配置在 `application.yml`，本地模式通过 `application-local.yml` 关闭部分外部依赖。

项目结合点：

- `LotteryApplication`
- `application.yml`
- `application-local.yml`
- `spring.factories`

### 51. 本地模式为什么要关闭 Kafka、Nacos、XXL-Job？

参考回答：

为了降低本地运行成本。完整生产环境需要 Kafka、Nacos、XXL-Job 等中间件，但学习和演示时只要 MySQL、Redis 和 Spring Boot 就能跑核心流程。本地配置关闭自动注册、消息消费和任务执行，可以避免因为外部服务没启动导致项目启动失败。

项目结合点：

- `application-local.yml`

### 52. `@Transactional` 在项目里用在哪里？

参考回答：

创建活动时需要同时保存活动、策略、策略明细和奖品配置，这些数据要么全部成功，要么全部回滚，所以使用事务。用户参与活动和发奖等涉及多表更新的场景也适合用事务保护，避免部分成功导致数据不一致。

项目结合点：

- `ActivityDeployImpl.createActivity`

### 53. Spring Bean 注入在项目里怎么使用？

参考回答：

项目大量使用 Spring 管理 Bean。领域服务、仓储实现、规则过滤器、发奖实现、Kafka 生产者、XXL-Job 都通过 `@Service`、`@Repository`、`@Component`、`@Configuration` 注册。接口或服务之间通过 `@Resource` 注入依赖。

项目结合点：

- `@Service("drawExec")`
- `@Service("ruleEngineHandle")`
- `@Repository`
- `@Component`

## 11. 前端 UI 与接口测试类

### 54. 本地 UI 是怎么工作的？

参考回答：

本地 UI 是 Spring Boot 静态资源，访问根路径时加载 `index.html`，页面通过 `lottery-console.js` 调用 `/api/lottery/**` 接口，然后把活动、策略、奖品、规则、用户记录和诊断结果渲染出来。它不是独立前端项目，不需要 Node 环境，跟 Spring Boot 一起启动。

项目结合点：

- `static/index.html`
- `static/css/lottery-console.css`
- `static/js/lottery-console.js`

### 55. 为什么访问 `localhost:8081` 以前会出现 Whitelabel 404？

参考回答：

Spring Boot 的 Whitelabel 404 表示服务启动了，但当前访问路径没有对应 Controller 或静态资源。后来项目加入了 `static/index.html`，访问根路径会加载 UI。如果还是 404，要检查静态资源是否打包进 `target/classes/static`，以及服务是否从正确模块启动。

项目结合点：

- `lottery-interfaces/src/main/resources/static/index.html`

### 56. 怎么测试项目核心功能？

参考回答：

可以分三层测试。第一层用浏览器 UI 做功能验证，比如创建活动、状态流转、抽奖、发奖。第二层用 `smoke-test.ps1` 调接口做冒烟测试。第三层运行 JUnit 测试，比如抽奖算法、规则引擎、DAO、RPC Facade 测试。完整测试还要准备 MySQL、Redis 和初始化 SQL。

项目结合点：

- `scripts/smoke-test.ps1`
- `lottery-interfaces/src/test/java`

## 12. 高并发与一致性类

### 57. 高并发抽奖最大风险是什么？

参考回答：

最大风险是库存超卖、重复中奖、重复发奖、数据库热点和消息重复消费。解决思路是 Redis 原子预扣库存、数据库条件更新防止奖品库存扣成负数、唯一索引保证幂等、MQ 状态机控制消息处理、定时任务做补偿和对账。

项目结合点：

- Redis 库存 token
- `uuid` 唯一索引
- `award_surplus_count > 0`
- `MQState`

### 58. 如何保证接口幂等？

参考回答：

可以使用业务唯一键和数据库唯一索引。比如用户参与活动记录用 `uuid` 防重，发奖单也用 `uuid` 防重；对外接口可以要求调用方传请求流水号，服务端保存流水号并用唯一索引防止重复处理。即使接口被重试，也只会成功处理一次。

项目结合点：

- `user_take_activity.idx_uuid`
- `user_strategy_export.idx_uuid`

### 59. 抽奖结果和发奖结果不一致怎么办？

参考回答：

抽奖命中后先生成发奖单，发奖单记录发奖状态。真正发奖可能同步执行，也可能通过 MQ 异步执行。如果发奖失败，状态会保留为失败或待处理，后续通过定时任务重试。这样抽奖结果和发奖动作之间通过发奖单解耦，最终一致。

项目结合点：

- `UserStrategyExport`
- `DistributionGoodsFactory`
- `LotteryInvoiceListener`
- `LotteryXxlJob`

### 60. 你怎么设计补偿机制？

参考回答：

补偿可以分三类。库存补偿：Redis 预扣成功但数据库失败时恢复库存 token。消息补偿：发奖单落库后 MQ 发送失败，由定时任务扫描未发送状态重新发送。发奖补偿：消费失败或第三方发奖失败，记录失败状态并重试，超过次数后转人工处理。

项目结合点：

- `recoverActivityCacheStockByRedis`
- `lotteryOrderMQStateJobHandler`
- `AwardState`

### 61. 高并发下数据库热点怎么优化？

参考回答：

活动库存和热门策略奖品库存都是热点。活动库存可以用 Redis 预扣减；奖品库存可以提前加载到 Redis，用 Lua 脚本原子扣减，异步落库；用户参与和发奖记录通过分库分表分散写入；只读配置如活动、策略、规则树可以缓存，减少数据库查询。

项目结合点：

- 当前已有活动库存 Redis 化和用户记录分库分表。
- 可优化策略奖品库存 Redis 化。

## 13. 安全与异常类

### 62. 当前接口有什么安全风险？

参考回答：

本地 UI 接口偏演示，没有完整权限控制、登录认证、参数签名和限流。如果放到生产环境，需要加认证授权、接口限流、防重复提交、参数校验、敏感字段脱敏、操作审计和 CSRF/CORS 控制，避免用户伪造请求创建活动或无限抽奖。

项目结合点：

- `LotteryApiController` 当前接口直接暴露本地功能。

### 63. 参数校验怎么做更好？

参考回答：

现在 Controller 中有部分手写校验。更好的方式是使用 Bean Validation，比如 `@NotNull`、`@NotBlank`、`@Min`，配合全局异常处理统一返回错误信息。复杂业务校验仍放在领域层，比如活动状态、库存、用户次数。

项目结合点：

- `LotteryApiController` 内部请求类
- `@ExceptionHandler(Exception.class)`

### 64. Fastjson 有什么风险？

参考回答：

Fastjson 历史上出现过多次反序列化安全漏洞。生产环境要固定安全版本、关闭 autoType 或避免反序列化不可信类型。也可以考虑使用 Jackson 作为默认 JSON 框架。这个项目中 Fastjson 更多用于对象序列化和日志或消息处理，外部输入反序列化要谨慎。

项目结合点：

- 父 POM 依赖：`fastjson 1.2.78`

### 65. 日志应该怎么设计？

参考回答：

关键链路要记录请求 ID、用户 ID、活动 ID、策略 ID、订单 ID、发奖单 ID、错误码和耗时。日志不能打印敏感信息。抽奖和发奖这种链路最好有结构化日志，方便按订单追踪全流程。

项目结合点：

- 当前运行日志：`run-lottery-*.log`
- 可优化：统一 traceId 和结构化日志。

## 14. 项目优化与重构类

### 66. 如果让你优化这个项目，你会怎么做？

参考回答：

我会分阶段优化。第一阶段补基础工程能力：统一编码、参数校验、全局异常、接口鉴权、日志 traceId。第二阶段优化高并发链路：策略和规则缓存、奖品库存 Redis + Lua、消息可靠投递、失败补偿。第三阶段优化工程架构：把本地 UI 和管理接口权限隔离，完善单元测试和集成测试，增加 Docker 一键启动。第四阶段完善运维：监控指标、报警、链路追踪、慢 SQL 分析。

项目结合点：

- 本地 UI 已可用
- 仍可补鉴权、限流、测试、监控

### 67. 目前项目的不足是什么？

参考回答：

不足包括：部分配置和注释存在编码问题；本地 UI 接口偏演示，缺少权限控制；部分外部依赖在本地模式被关闭，和生产环境有差异；抽奖奖品库存主要依赖数据库，极高并发下可能成为热点；测试覆盖不够系统化；消息可靠性还可以用本地消息表和重试机制进一步强化。

项目结合点：

- `application.yml` 有编码异常
- `LotteryApiController` 是本地 UI 聚合接口

### 68. 如果要支持百万级并发抽奖，怎么改？

参考回答：

核心思路是前置削峰、缓存化和异步化。入口层加限流和排队；活动、策略、规则树预热到 Redis 或本地缓存；库存用 Redis Lua 原子扣减；用户请求先写入队列，后端异步消费处理；中奖和发奖通过消息表保证最终一致；数据库按用户或活动维度分库分表；对热点活动可以做独立奖池和库存分片。

项目结合点：

- 已具备 Redis、Kafka、分库分表基础。

### 69. 如果要做灰度发布或多环境配置，怎么处理？

参考回答：

使用 Spring Profile 区分 `local`、`dev`、`test`、`prod`，敏感配置通过环境变量或配置中心管理，不写死在 yml。Dubbo、Nacos、Kafka、MySQL、Redis 地址按环境隔离。发布时可以通过 Nacos 或网关流量控制做灰度。

项目结合点：

- `application-local.yml`
- `application.yml`

### 70. 如果规则树非常复杂，如何优化性能？

参考回答：

规则树配置应该缓存，避免每次抽奖都查数据库。可以把规则树聚合加载到 Redis 或本地 Caffeine 缓存，配置变更时刷新。执行时减少反射和字符串解析，把规则 key 到过滤器的映射预初始化。对复杂规则还可以预编译成决策表或表达式。

项目结合点：

- `RuleRepository.queryTreeRuleRich`
- `EngineConfig`

## 15. Java 基础和框架追问

### 71. HashMap 和 ConcurrentHashMap 在这个项目可能哪里用？

参考回答：

规则引擎中用户属性 `valMap` 可以用普通 HashMap，因为它通常是单次请求内创建和使用，不跨线程共享。Spring Bean 里如果维护全局算法映射、规则过滤器映射，初始化后只读也可以用 Map；如果运行时会动态修改，就需要 ConcurrentHashMap 或加锁保证线程安全。

项目结合点：

- `QuantificationDrawReq.valMap`
- `EngineConfig` 中的过滤器映射思路

### 72. BigDecimal 为什么适合表示中奖概率？

参考回答：

中奖概率涉及小数，如果用 double 可能有二进制精度误差。BigDecimal 可以精确表示十进制小数，适合金额、概率这类需要精确控制的场景。项目里策略明细的中奖概率字段是 decimal，对应 Java 里使用 BigDecimal。

项目结合点：

- `strategy_detail.award_rate decimal(5,2)`
- `AwardRateVO`

### 73. 为什么 DTO 要实现 Serializable？

参考回答：

RPC 调用需要对象在网络上传输，Dubbo 默认会序列化请求和响应对象。DTO、Req、Res 实现 Serializable 可以明确它们是可传输对象，也方便兼容一些 Java 序列化或缓存场景。

项目结合点：

- `ActivityDTO implements Serializable`
- `DrawReq implements Serializable`
- `DrawRes implements Serializable`

### 74. MyBatis Mapper XML 相比注解 SQL 有什么优缺点？

参考回答：

XML 适合复杂 SQL，便于维护动态 SQL、结果映射和 SQL 片段；缺点是接口和 XML 分离，方法名或字段改动时编译期不一定能发现。注解 SQL 简洁，适合简单查询，但复杂 SQL 可读性差。这个项目表较多、SQL 较复杂，所以使用 XML 更合适。

项目结合点：

- `lottery-interfaces/src/main/resources/mybatis/mapper`

### 75. Spring AOP 在分库分表中起什么作用？

参考回答：

AOP 用来无侵入地拦截带 `@DBRouter` 的方法，在方法执行前计算路由并设置上下文，执行后清理上下文。这样业务代码只需要加注解，不需要手动选择数据源或拼分表名。

项目结合点：

- `DBRouterJoinPoint`
- `@DBRouter`

## 16. 场景设计题

### 76. 如果要求每个用户每天只能抽一次，怎么实现？

参考回答：

可以在用户参与记录中增加日期维度，生成唯一键 `uId + activityId + yyyyMMdd`，或者单独建用户日参与次数表。参与活动前先检查当天次数，成功后原子扣减或插入记录。高并发下用数据库唯一索引兜底，也可以用 Redis key `lottery:take:{activityId}:{uId}:{date}` 做快速判断。

项目结合点：

- 当前已有 `user_take_activity_count`
- 可扩展唯一约束或 Redis key

### 77. 如果要求黑名单用户不能抽奖，怎么实现？

参考回答：

可以通过规则树扩展一个黑名单过滤节点，也可以在参与活动前增加风控校验。黑名单数据可以存在 Redis Set 或数据库表中。规则树方式更灵活，运营可配置；硬编码风控方式性能更高，适合强制策略。

项目结合点：

- 新增 `BlackListFilter`
- 注册到 `EngineConfig`

### 78. 如果要支持优惠券、实物、积分多种奖品，怎么扩展？

参考回答：

项目已经有奖品类型枚举和发奖工厂。新增奖品类型时，先在枚举中定义类型，再实现一个新的 `IDistributionGoods`，比如 `PointGoods`，最后在 `GoodsConfig` 中注册映射。抽奖流程不需要改，只要根据奖品类型从工厂拿到对应发奖服务。

项目结合点：

- `Constants.AwardType`
- `IDistributionGoods`
- `DistributionGoodsFactory`

### 79. 如果第三方发奖接口很慢，怎么处理？

参考回答：

不要在抽奖接口里同步等待第三方。抽奖成功后生成发奖单并返回中奖结果，发奖动作异步执行。消费者调用第三方接口时设置超时、重试、熔断和降级。失败后记录状态，由定时任务补偿，必要时转人工处理。

项目结合点：

- `LotteryInvoiceListener`
- `UserStrategyExport`
- `LotteryXxlJob`

### 80. 如果运营修改了活动策略，怎么保证新老配置不互相影响？

参考回答：

可以对活动配置做版本化。用户参与活动时记录使用的策略版本和奖品版本，抽奖和发奖都按当时版本执行。运营修改策略时生成新版本，老用户未完成流程仍使用旧版本。这样可以避免活动运行中修改概率导致历史订单不一致。

项目结合点：

- 当前策略 ID 可作为基础，后续可增加 `strategy_version`。

### 81. 如果需要审计运营操作，怎么做？

参考回答：

所有活动创建、状态流转、奖品修改、策略修改都写操作日志，记录操作人、操作时间、IP、变更前后内容和审批单号。接口层加登录认证，从 token 中获取操作人。重要操作可以加审批流。

项目结合点：

- 当前 `ActivityVO.creator`
- 可新增 `operation_log` 表

### 82. 如果一个活动特别热门，怎么避免单个 Redis key 过热？

参考回答：

可以做库存分片，把一个活动库存拆成多个 Redis key，例如 100 个库存桶，请求随机选择一个桶扣减。每个桶维护独立库存，减少单 key 热点。最终库存等于所有桶剩余库存之和。数据库落库时可以异步汇总。

项目结合点：

- 当前库存 key：`lottery_activity_stock_count_{activityId}`
- 可扩展为：`lottery_activity_stock_count_{activityId}_{bucket}`

### 83. 如果要做接口限流，你会放在哪里？

参考回答：

可以放在网关层和应用层。网关按 IP、用户 ID、活动 ID 做粗粒度限流；应用层对抽奖接口按活动维度和用户维度做细粒度限流。实现方式可以用 Redis 计数器、令牌桶、漏桶，或者 Sentinel、Resilience4j。

项目结合点：

- 入口：`LotteryApiController.draw`
- 活动维度：`activityId`
- 用户维度：`uId`

### 84. 如果要求抽奖结果可追溯，怎么设计？

参考回答：

要记录完整链路：请求流水号、用户 ID、活动 ID、策略 ID、随机数或中奖区间、命中奖品、库存扣减结果、参与记录 ID、发奖单 ID、发奖状态。日志和数据库都要能查到，必要时把随机种子和算法版本也记录下来，方便审计争议。

项目结合点：

- `UserTakeActivity`
- `UserStrategyExport`
- 可增加抽奖明细表

### 85. 如果要做后台运营系统，你会有哪些页面？

参考回答：

至少包括活动管理、活动创建/编辑、活动审核、状态流转、奖品管理、策略管理、规则树管理、用户抽奖记录、发奖单管理、失败补偿任务、数据看板、系统诊断和操作日志。当前本地 UI 已覆盖部分功能，后续可以拆成正式后台并加权限。

项目结合点：

- 当前 UI：`static/index.html`
- 当前 API：`LotteryApiController`

## 17. 面试时的项目亮点表达

### 86. 你怎么用一分钟介绍项目亮点？

参考回答：

这个项目的亮点是用 DDD 拆分了抽奖系统的核心领域，把活动、策略、规则、奖品分开建模；抽奖算法支持单项概率和总体概率两种模式；活动状态使用状态机控制，避免非法流转；高并发参与活动用 Redis 预扣库存，数据库唯一索引保证幂等；用户参与和发奖记录支持分库分表；发奖链路可以通过 Kafka 和 XXL-Job 做异步解耦和失败补偿。

### 87. 面试官问“这个项目难点在哪里”，怎么答？

参考回答：

难点主要有四个。第一是抽奖概率和库存一致性，要既按概率中奖又不能超卖。第二是高并发参与活动，需要 Redis 预扣库存和数据库兜底。第三是规则树人群过滤，要让规则可配置、可扩展。第四是发奖最终一致性，抽奖成功、发奖单生成、MQ 投递、消费发奖之间要能补偿和重试。

### 88. 面试官问“你真实学到了什么”，怎么答？

参考回答：

我学到的不只是 Spring Boot CRUD，而是一个营销抽奖系统如何拆领域、如何控制状态、如何设计抽奖算法、如何处理库存并发、如何用分库分表承载用户流水、如何用 MQ 和定时任务实现最终一致性。同时也认识到生产级系统还要补权限、限流、监控、补偿、灰度和自动化测试。

## 18. 最推荐背熟的 10 个问题

1. 项目的整体业务流程是什么？
2. 为什么使用 DDD 分层？每层职责是什么？
3. 抽奖主流程怎么走？
4. 单项概率和总体概率有什么区别？
5. Redis 如何防止活动库存超卖？
6. 数据库如何防止奖品库存超卖？
7. 分库分表组件怎么实现？
8. 规则树怎么执行，如何扩展新规则？
9. Kafka 和 XXL-Job 在发奖链路中解决什么问题？
10. 如果要支撑百万级并发抽奖，你会怎么优化？

## 19. 面试回答模板

当面试官问到某个技术点时，可以按这个结构回答：

```text
第一，先说业务背景：为什么需要这个能力。
第二，说项目实现：对应哪个模块、哪个类、哪张表。
第三，说关键技术：用了什么设计模式、并发控制或中间件。
第四，说风险和优化：当前方案有什么边界，生产环境怎么增强。
```

示例：

```text
以活动库存为例，抽奖活动会有大量用户同时参与，如果每次都直接扣 MySQL，数据库压力大且容易出现并发竞争。
项目里活动库存使用 Redis 预扣减，核心在 ActivityRepository.subtractionActivityStockByRedis。
它通过 Redis 原子递增和 setNx token key 控制库存占用，数据库再保存用户参与记录，唯一索引保证幂等。
这个方案优先保证不超卖，如果 Redis 预扣成功但数据库失败，会通过 recoverActivityCacheStockByRedis 做补偿；生产环境还可以增加定时对账任务。
```

