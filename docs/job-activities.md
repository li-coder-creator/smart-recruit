# 岗位操作记录接口实现

## 交付状态

2026-10-04 已实现 GET /job/company/{id}/activities 和岗位成功操作的记录生成。
本地 application.yml 指向的 smart_recruit 开发库已新增 job_activity 表。
创建前核对了 MySQL 8.0.13、InnoDB、父表主键类型及表不存在的状态。
本次只新增空表，不修改原有岗位、用户、投递记录，不补造旧历史。
旧岗位从新版本运行后的首次成功操作开始积累记录。

迁移文件为 docs/sql/2026-10-04-job-activity.sql。本地已执行，切勿重复执行。
其他环境必须在启用新代码前执行迁移，否则新查询报错、岗位写操作因记录无法写入而回滚。
迁移前父表结构和迁移后实际新表结构分别保存在本地忽略目录 target 的
job-schema-before-activity.sql 与 job-activity-schema-applied.sql。

Maven 跳过测试的 compile 已成功，87 个 Java 源文件参与编译。
实现阶段只做编译。后续已通过 Postman MCP 完成两个接口联合回归，108 个实际请求、176 个断言全部通过，
包括回滚与并发；详细结果和范围限制见 job-panels-regression-2026-10-04.md。

## 接口契约

GET /job/company/{id}/activities?limit=5 返回 Result<List<JobActivityVO>>。
limit 未传时默认 5 条，允许 1～20 条，超出范围报业务错误，不静默截断。
默认条数在 JobConstants，范围在 JobValidationConstants，均使用独立具名常量。

| 字段 | 含义 |
| --- | --- |
| id | 记录主键 |
| operation | CREATE_DRAFT/PUBLISH/UPDATE/PAUSE/RESUME/END |
| operationText | JobOperation 枚举描述 |
| operatorName | 实际操作时的展示名称快照 |
| createTime | 数据库生成的实际操作时间 |

企业角色及公司存在性使用 requireCompany，和招聘进展查询一致，不额外要求审核通过。
岗位必须存在且属于当前企业，不接收客户端 companyId 或操作者参数。
草稿也可以查询，旧岗位无历史时返回 []。
按 create_time DESC, id DESC 排序，不返回总数，接口本身不生成业务记录。
业务错误沿用 Result.error 的 code=500；未登录由登录拦截器返回 HTTP 401。
非整数 limit 的 Spring 参数绑定异常仍沿用现有全局处理，没有修改全局错误规范。

## Controller / Service / Mapper

Controller 的既有路由可复用，只读取登录 userId 并包装响应，本次未修改。
Service 校验条数、身份、岗位存在性和企业归属，调用 Mapper 后通过枚举补齐 operationText。
查询使用只读 REPEATABLE_READ，归属检查和记录读取使用同一数据库快照，不使用 Redis。
Mapper 只查询展示字段，不 JOIN 当前用户名称，因为数据库中已保存名字快照。

JobActivity 是存储实体，operation 使用 JobOperation，MyBatis 默认 EnumTypeHandler 按枚举名称写入。
JobActivityVO 的 operation 保持 String，JSON 返回稳定操作编码，与原有 VO 契约一致。
新增枚举编码与描述形成唯一来源，不在前端或 SQL 复制另一套业务文案。

## 记录生成与事务

| 成功操作 | 写入记录 |
| --- | --- |
| 新建草稿 | CREATE_DRAFT |
| 直接发布 | PUBLISH，一条，不额外产生 CREATE_DRAFT |
| 发布已有草稿 | PUBLISH |
| 保存内容，包括草稿编辑 | UPDATE |
| 暂停 | PAUSE |
| 恢复 | RESUME |
| 结束 | END |

上述公开 Service 写方法均开启事务。顺序为：权限/业务校验 → 修改岗位 → 写记录 → 注册提交后缓存失效。
岗位与记录使用同一数据源、同一个事务，任意 SQL 异常或者业务运行时异常都会回滚。
仅当 Mapper 影响一行才继续，未取得新增岗位主键、操作者查询结果不存在或记录未写入都报错回滚。
新岗位使用 useGeneratedKeys 回填 job.id，避免关联 NULL 或用 MAX(id) 推测主键。

操作记录 Mapper 使用 INSERT ... SELECT 从 sys_user 读取真实操作者名称：昵称非空白则用昵称，否则用用户名。
operator_user_id 来自登录身份，operator_name 是当次写入快照，修改昵称不会改写历史。
时间由 DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) 生成，不依据岗位更新时间推算。
查询、失败请求、重复状态请求不写成功事件。内容保存成功即记录 UPDATE，未做无变化编辑去重。

事务在 Spring 代理调用公开 Service 方法时开启；私有 recordJobActivity 辅助方法在外层已有事务中运行。
不依赖私有方法注解或同类内部调用去新开事务。

## 并发与删除

岗位写操作的 requireOwnedJob 使用 SELECT ... FOR UPDATE 锁定目标岗位。
相同岗位的写操作在数据库行锁上串行，事务结束时释放锁；普通查询仍用非锁定读取。
状态更新 SQL 同时限定 id 和 expectedStatus，Service 检查受影响行数。
结束招聘只允许招聘中或暂停状态，重复发布、暂停、恢复、结束请求不会新增对应事件。
不宣称所有业务重试都有幂等性：新建请求仍可能创建多个岗位，重复内容保存也会记录多次 UPDATE。

删除也放在事务中，DELETE 限定草稿状态，避免发布与删除竞争时删掉已发布岗位。
job_activity.job_id 外键 ON DELETE CASCADE 清理被删除草稿的展示记录。
job_application 现有外键仍保持 RESTRICT，异常草稿已有投递时不能强行删除。
已发布岗位的物理删除仍由 Service 拒绝；当前时间线不承担永久保留删除审计的职责。

## 提交后缓存失效与限制

TransactionSynchronization.afterCommit 执行详情缓存删除，回滚时不执行回调。
沿用二次删除思路，延迟 100ms 现在由具名常量表示，并从提交后开始计时。
Redis 或调度异常单独记录错误，避免数据库已经成功提交后接口却返回可回滚失败的错觉。
该机制是尽力失效，不保证数据库与 Redis 的原子性；当前没有持久重试队列或可靠消息。
删除失败或过长的旧读取仍可能造成短暂旧缓存，缓存 TTL 和后续删除限制其持续时间。
不能将二次删除表述为严格一致性或零脏缓存保证。

## 验收范围

下列清单是原验收计划。已执行的场景、结果及未覆盖限制以 job-panels-regression-2026-10-04.md 为准。
用例已通过 Postman MCP 加入 SmartRecruit API - Optimized Regression：

1. 新建草稿、直接发布、草稿发布、更新、暂停、恢复、结束的编码、名称、时间和条数。
2. 直接发布只生成一条 PUBLISH；读取列表不增加记录。
3. 默认 limit、1/20 上下界、0/负数/超上限；无历史、草稿、暂停、已结束岗位。
4. 未登录、求职者、其他企业、不存在岗位；失败请求不增加事件。
5. 重复状态请求不产生重复事件；同时间记录按 id DESC。
6. 操作者昵称变更后旧记录保留原名称；空白昵称回退用户名。
7. 草稿删除级联清理；已发布岗位删除失败且原记录保留。
8. 记录插入故障时读取岗位确认修改回滚；需采用隔离测试环境的可控故障，不能在共享开发库删表模拟。
9. 并发状态请求只发生一次目标迁移；提交后缓存失效及失败日志路径。
10. 岗位既有写接口、招聘进展和最近投递回归，确认本次事务改造未改变原有契约。

## 学习和面试

能解释为什么业务时间线需要数据库记录、为什么记录和修改要共用事务、主键回填如何实现、
FOR UPDATE 锁何时释放、条件更新为什么检查行数，以及 afterCommit 为什么不能回滚已经提交的数据。
应区分编译检查、结构迁移和实际运行时证据；本轮确已执行回滚及相同暂停请求的并发测试，不能外推为所有并发场景的保证。
