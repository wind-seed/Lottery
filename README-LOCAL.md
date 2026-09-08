# Lottery 本地运行说明

这个项目已经补了一套本地运行方式，目标是在 Windows 上少依赖外部中间件，直接用 MySQL + Spring Boot + 本地 UI 跑起来。

## 一键启动

```powershell
cd D:\java\Lottery
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -StopExisting
```

启动成功后访问：

```text
http://localhost:8081/
```

如果只想启动，不重新打包：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -SkipBuild -StopExisting
```

## 停止服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

## 冒烟测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

这个脚本会检查健康接口、数据库诊断、活动、策略、奖品、规则、规则决策和首页。

## 数据库初始化

项目需要 3 个 MySQL 库：

- `lottery`
- `lottery_01`
- `lottery_02`

项目自带 SQL 在：

```text
doc\assets\sql\lottery.sql
doc\assets\sql\lottery_01.sql
doc\assets\sql\lottery_02.sql
```

这些 SQL 包含 `DROP TABLE`，所以初始化脚本默认不会直接覆盖数据。确认要重建表时执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-db.ps1 -Reset -User root -Password 123456
```

如果 MySQL 客户端不在 PATH 里：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-db.ps1 -Reset -Mysql "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -User root -Password 123456
```

## 本地模式说明

`application-local.yml` 已经关闭 Kafka 消费、Kafka 生产、Nacos 注册和 XXL-Job 自动执行，抽奖后的发奖流程会在本地同步完成，更适合学习、演示和调试。

UI 里的“数据库”页会展示当前数据源、分库分表配置、数据量和运行提醒。如果提示“当前没有可直接抽奖的有效活动”，先去“创建”页生成一个未来活动，再把活动状态流转到运行状态。
