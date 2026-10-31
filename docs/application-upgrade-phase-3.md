# 我的投递升级：阶段三真实事件与事务

日期：2026-10-25。

## 1. 背景、范围与当前状态

当前状态只能说明“现在到哪里”，不能证明走过哪些阶段。求职者详情需要读取真实发生的事件，
并区分从创建起有完整记录的新投递和缺少早期证据的旧投递。

本阶段完成：事件实体、只追加 Mapper、事务内事件组件，以及两个现有写接口增强。
影响文件：Application、ApplicationEvent、ApplicationMapper、ApplicationEventMapper、
ApplicationEventRecorder、ApplicationServiceImpl、ApplicationConstants。
Controller、请求 DTO 和旧接口返回结构保留。

已完成静态复查，`mvn -o -Dmaven.repo.local=... -DskipTests compile` 编译成功，165 个源文件。
实现交付时未运行测试。后续应用户“测试”要求，已在隔离环境通过 Postman MCP 完成当前阶段回归：
135 次请求、230 条断言，全部通过，131 条集合请求已持久加入 SmartRecruit API - Optimized Regression。
验证了事件失败回滚、恢复、两组并发竞争以及相关查询；没有修改开发库存量投递。
详细请求、响应和限制见 [阶段三回归记录](application-upgrade-phase-3-regression-2026-10-25.md)。
排期全链路等剩余范围仍按阶段四/五推进。

本阶段不新增数据库变更，复用阶段二已落地的字段和表。
阶段三交付时没有排期写入口；后续面试创建、改约、取消、近期面试和终态联动已在阶段四实现并回归，
见 [阶段四验收记录](application-upgrade-phase-4-regression-2026-10-25.md)。

## 2. 开始前只读核对

实际工作区：`D:\project\smart-recruit`，分支 main。
既有阶段一/二和岗位推荐文件保留；本阶段只修改相关投递文件，不提交 Git。

2026-10-25 14:44 的数据库只读盘点：

- job_application、application_event、application_interview 均为 InnoDB，阶段二结构存在。
- 原有 7 条投递，history_recorded_from_start 均 false。
- 状态分布：待处理 1、筛选中 0、面试 2、录用 3、拒绝 1。
- 事件和排期均为 0 条。本轮没有执行任何 INSERT / UPDATE / DELETE 或 DDL。
- 本地盘点证据位于 `target/application-phase-three-inventory-20261025-144419.json`。

## 3. 接口增强与验收标准

| 接口 | 新增行为 | 需要满足的标准 |
| --- | --- | --- |
| POST /application | 保存投递并回填 ID，完整性标记 true，追加 APPLIED | 投递、标记和初始事件同事务；失败不留下半条业务 |
| PUT /application/{id}/status | 校验归属，锁定主记录，更新状态和面试证据，追加 STATUS_CHANGED | 事件 from/to 与真实变更一致；失败全部回滚；不接受回退或重复状态 |
| GET /application/mine/{id} | 复用阶段二查询真实事件 | 新记录返回实际事件；旧记录即使有后续事件，historyComplete 仍为 false |

请求、权限、Result<Void> 成功返回和现有业务错误包装保持兼容。
新投递只能使用本人的简历、ACTIVE 岗位；企业状态处理仍要求准入通过且岗位属于本企业。
历史投递的处理不额外要求岗位仍 ACTIVE，也不依赖旧简历当前存在。

## 4. 新投递的具体调用链

1. Controller 的 @Valid 检查请求，并从 ThreadLocal 取真实 userId。
2. ApplicationServiceImpl.apply 的 @Transactional 开启业务事务，完成原有身份、岗位、简历及重复检查。
3. 用统一的 applicationClock 取得服务器时间，生成新的 Application，显式设置 historyRecordedFromStart=true。
4. ApplicationMapper.apply 插入并通过 @Options(useGeneratedKeys=true) 将数据库生成的主键写回对象。
5. 检查影响行数为 1、主键已回填，然后调用 eventRecorder.recordApplied。
6. 事件组件填 APPLIED、JOB_SEEKER、toStatus=PENDING；fromStatus、interviewId、detailJson 为空。
7. 只有整个事务成功才提交；事件保存异常向外传播，投递和完整性标记一起回滚。

主键回填不是再查询 MAX(id)，不能把别的并发请求的 ID 关联到本次事件。
应用层“先查后插”仅用于友好提示；uk_application_user_job 唯一约束才是并发防重复的数据库保障。
主投递插入的 DuplicateKeyException 转为“请勿重复投递同一岗位”，仍通过异常退出事务。
事件写入异常不被这个 catch 吞掉，也不被误报为重复投递。

新主表 create_time/update_time 取同一次服务器时间的秒精度；事件 occurred_at 保存毫秒精度。
这是匹配原 DATETIME 与新 DATETIME(3) 的存储精度，没有将发布时间写成当天零点。

## 5. 状态推进与并发处理

1. 完成企业身份、投递存在性和岗位归属定位。
2. 根据 id + jobId 以 SELECT ... FOR UPDATE 锁定父投递行，当前读取得最新状态。
3. 如果锁后状态与本请求最初读取的状态不同，提示“投递状态已变化，请刷新后重试”。
4. 使用锁后状态作为真实 fromStatus，以 canTransitionTo 校验目标状态。
5. 持锁后取得事件时间；保留 UPDATE 的原状态条件和影响行数检查。
6. 在同一事务内更新 status、has_interviewed、update_time，然后追加一条 STATUS_CHANGED。
7. 提交后释放锁。回滚时状态、累计面试证据、更新时间和事件一起撤销。

锁后对比保留原来的冲突拒绝语义。例如两个请求都读到待处理，其中一个先提交面试，另一个原本想改录用：
后者会提示刷新，不会在等待后悄悄以新的面试状态继续执行。这是场景说明，不是已执行的并发测试结果。
若后一个请求开始读取时前一个已经提交，则它按自己实际读取到的新状态正常校验。

MyBatis 锁定查询禁用二级查询缓存，并清理会话查询缓存，避免用相同参数的旧查询结果替代当前读。
FOR UPDATE 和普通 RR 快照读取的职责不同；即便已有行锁，原状态 WHERE 条件和受影响行数仍作为防御检查保留。
后续排期写入统一从同一父行开始加锁，锁顺序为投递行、排期行、追加事件。

## 6. 为什么单独使用 ApplicationEventRecorder

事件的业务字段由服务端组装，Controller 不接收 eventType、操作者、历史标记或任意 detailJson。
组件提供明确的 recordApplied / recordStatusChanged 方法，状态事件不能任意指定操作者角色。
Mapper 只有 INSERT，没有更新或删除事件的方法；事件事实不作为资料编辑接口的一部分。

组件是独立 Spring Bean，使用 Propagation.MANDATORY：

- 调用方有事务时加入同一事务，不单独提交。
- 调用方没有事务时直接拒绝，防止后来有人遗漏业务事务却单独写入事件。
- 不能改成 REQUIRES_NEW，否则可能出现业务回滚而事件先提交的错误事实。
- 公开方法通过另一个 Bean 调用，事务代理能够生效；不是在同一个对象内部调用自己的事务方法。

BusinessException 是 RuntimeException；MyBatis/Spring 数据访问异常也向外传播，触发默认事务回滚。
事件插入影响行数不为 1 或未回填 ID 时，同样抛异常，不返回成功。

## 7. 跳级、面试证据与旧数据

待处理直接录用只产生一条待处理到录用事件；新记录另有真实 APPLIED，不补筛选或面试事件。
新投递默认 has_interviewed=false，只有进入或离开实际 INTERVIEW 时置 true。
旧 has_interviewed=null 在没有新增面试事实时继续保留，不能因录用就认定曾面试。

historyRecordedFromStart 只在新投递插入时设置，状态 UPDATE 不修改它。
因此旧记录后续进入面试会产生真实的新事件，但 historyComplete 仍为 false。
不根据当前状态、修改时间或事件条数重新认定完整历史，不批量补造 APPLIED。

求职者详情只返回 ApplicationEventVO 白名单，操作者账号 ID 和原始 JSON 不公开。
状态事件不携带面试排期快照；当前工作没有向 detailJson 写入任意用户内容。

## 8. 阶段五需要执行的回归

下表保留完整验收要求；已执行范围与未覆盖组合以阶段三回归记录为准，不能据本表推断全部组合已执行。

| 场景 | 预期 |
| --- | --- |
| 新投递 | 一条主记录、true 标记、一条 APPLIED，ID 对应且时间来自服务端 |
| 事件插入失败 | 主记录和标记不提交；状态变更时 status、has_interviewed、update_time 不提交 |
| 连续状态推进 | from/to 链与实际变更一致，事件按时间和 ID 稳定展示 |
| 直接跳级 | 只有实际起点和终点，不存在伪造中间阶段事件 |
| 同状态、回退、终态再修改 | 拒绝且不追加事件 |
| 同原状态的并发处理 | 一方成功，冲突方被拒绝；不存在来自旧状态的第二条错误事件 |
| 同用户同岗位并发投递 | 唯一约束有效，成功投递仅一份对应初始事件 |
| 旧投递后续变化 | 新事件正常追加，历史完整性标记仍为 false |
| 旧面试证据为 null | 非面试跳级不改变未知证据；真实进入面试后才变 true |
| 简历已删除、岗位已结束 | 所属企业可按原规则处理，真实事件仍可读取 |
| 越权、未登录、错误目标状态 | 拒绝且主记录与事件均不变 |

故障和并发验证须使用隔离数据与明确方案，不能破坏开发库旧投递或对其补造历史。
只允许通过 Postman MCP 执行接口回归，持久写入指定集合。

## 9. 学习与面试表达

需要掌握：主键回填、唯一约束与先查后插的竞争、事务传播、默认回滚规则、
Spring 代理与自调用限制、RR 快照读与当前读、行锁与 CAS 条件配合、状态证据与当前状态的区别。

可准确描述“实现投递与真实事件同事务写入，使用行锁和原状态条件防止并发状态覆盖”。
当前已完成本轮隔离环境的并发/回滚回归，可按实际结果表达；不能声称面试排期已完成或性能提升百分比。
