# 企业招聘总览接口

## 业务与接口契约

`GET /job/company/overview` 返回 `Result<CompanyJobOverviewVO>`。
企业身份及 companyId 由登录用户和 PermissionService 确定，不接受客户端指定企业 ID。
本接口使用 requireCompany，与现有企业岗位管理接口保持一致，不额外要求企业审核通过。

| 字段 | 统计口径 |
| --- | --- |
| jobCounts | 当前企业全部岗位以及 ACTIVE、PAUSED、DRAFT、ENDED 各状态岗位数量 |
| applicationCount | 当前企业 ACTIVE、PAUSED、ENDED 岗位下全部投递记录数量 |
| pendingApplicationCount | 上述范围内当前 PENDING 的投递记录数量 |
| currentInterviewApplicationCount | 上述范围内当前 INTERVIEW 的投递记录数量 |
| interviewApplicationCount | 上述范围内曾实际进入 INTERVIEW 的投递记录数量；有未核实旧历史时为 null |
| interviewConversionRate | interviewApplicationCount / applicationCount × 100，HALF_UP 保留两位小数；分母为零或历史未知时为 null |

统计投递记录，不跨岗位按求职者去重。所有指标不受岗位列表关键词、状态筛选和分页影响。
无岗位时 jobCounts 各字段为 0；无投递时所有投递数量为 0、转化率为 null。

## 调用链与数据行为

Controller 读取登录用户 ID 并包装响应；Service 校验企业身份、组织两次聚合查询并计算转化率。
JobMapper 只统计岗位，ApplicationMapper 只关联 job 与 job_application 统计投递。
不关联账号和简历，避免统计依赖这些关联记录是否仍存在；不加载完整投递列表。
Service 使用只读 REPEATABLE_READ 事务，使两次统计读取同一份数据快照。
不缓存总览；后续请求读取最新已提交的数据。

has_interviewed 保存累计面试证据：新增投递为 false，进入面试时设为 true，离开面试后保留 true。
标记与状态在同一条 UPDATE 中写入，不会出现状态已修改但面试标记遗漏的中间结果。
直接录用不自动记为曾进入面试。投递状态白名单后续已实现并通过独立验收，
结果见 application-status-whitelist-regression-2026-10-04.md。
单岗位进展现已实现，见 docs/job-recruitment-progress.md；两处共用同一个转化率计算方法。
最近投递与岗位操作记录已实现，Postman 联合回归通过；结果见 job-panels-regression-2026-10-04.md，操作记录实现见 job-activities.md。

## 数据库迁移

先执行 `docs/sql/2026-10-03-company-job-overview.sql`，再启动包含此次改动的后端。
迁移前检查确认 has_interviewed 列不存在；执行期间暂停投递新增与状态修改。
旧记录仅对当前 INTERVIEW 的投递确认标记为 true，其余保持 null，待历史证据核实。
不能通过当前 HIRED、REJECTED 或数值比较推测历史面试经历。
开发库已于 2026-10-03 执行迁移。迁移前有 2 条投递，均处于 INTERVIEW，已标记为 true。
回填显式保留 update_time，避免迁移影响业务更新时间。
迁移前表结构和记录备份保存在本机 TEMP 目录的
smart-recruit-job-application-before-overview-1791012622431.sql。
新增列前运行新总览、投递新增和状态更新接口会因缺少该列失败。

## Postman 回归验收

已将 42 条用例持久加入集合 SmartRecruit API - Optimized Regression 的
03 Job / 企业招聘总览回归 2026-10-03，并通过本机 Postman MCP 执行。
默认跳过该数据写入回归文件夹；手动执行时在测试环境设 runOverviewRegression=true。
分为企业准备、求职者投递、企业状态更新阶段，分别使用现有企业及求职者环境。
旧历史未知用例需要独立测试投递准备并设 overviewUnknownFixtureReady=true，正常流程默认跳过。
测试运行器使用独立 8082 服务；主集合用例使用既有 host 变量。
未复制密码到新环境或集合；登录请求仅引用原测试环境的凭据变量。

| 执行阶段 | 实际请求数 | 断言结果 |
| --- | --- | --- |
| 企业准备及总览基线 | 11 | 19/19 通过 |
| 求职者权限及三次投递 | 7 | 12/12 通过 |
| 企业状态及统计变化（初次） | 19 | 37/38 通过；收尾删除请求的草稿 ID 未传入 |
| 修正测试脚本后收尾 | 6 | 11/11 通过 |
| 旧面试历史未知分支 | 2 | 5/5 通过，测试后恢复原历史标记 |

首次收尾失败原因是测试辅助脚本的 Java 字符编码导致中文草稿后缀匹配失败，
请求发送了未替换的 {{ovJobDraft}}。修正 UTF-8 编码后只重跑收尾步骤，未回退终态投递。
共实际执行 45 次请求、85 个断言；初次失败的 1 个测试脚本断言在相关步骤重跑后通过。
42 条持久用例最终均已通过，编译与 git diff --check 也通过。

测试企业 companyId=27，测试前有 6 个岗位、0 条投递；其他企业的 2 条面试投递未混入。
本轮独立岗位 26、27、28 均已结束，草稿 25 已删除；测试简历 29 与投递 12、13、14 保留供复查。
投递 12 为面试后录用（has_interviewed=true），13 为直接录用（false），14 为面试后拒绝（true）。
最终总览响应业务码为 200，jobCounts={all:9,active:3,paused:0,draft:0,ended:6}，
applicationCount=3、pendingApplicationCount=0、currentInterviewApplicationCount=0、
interviewApplicationCount=2、interviewConversionRate=66.67。
模拟旧历史未知时，后两项为 null，前三项不变；已恢复测试投递 12 的原 true 标记。

| 场景 | 验证状态 |
| --- | --- |
| 企业查询总览 | 已验证业务码 200 与统计字段 |
| 其他企业数据隔离及草稿岗位数量 | 已验证 |
| 无投递 | 已验证数量为 0、转化率为 null |
| 面试后录用或拒绝 | 已验证当前数减少、历史数保留 |
| 跳过面试直接录用 | 已验证不增加累计面试 |
| 旧投递面试历史未知 | 已验证累计面试数及转化率为 null |
| 1/3、2/3 转化率 | 已验证 33.33、66.67 |
| 切换列表筛选和分页 | 已验证总览范围保持全企业 |
| 求职者或未登录 | 已验证拒绝访问 |
| 同一岗位多个求职者投递 | SQL 结构已审查，本轮未创建第二求职者进行运行验证 |
| 企业完全没有岗位、草稿存在异常投递 | 本轮未额外造数，未进行运行验证 |
