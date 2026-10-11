# 企业岗位分页统计：实现与验收

## 业务与接口契约

2026-10-11，按用户授权完成 GET /job/company 的列表招聘统计。
返回 Result<PageResult<CompanyJobListVO>>，参数、data.total 和 data.items 结构保持原有契约。
原岗位展示字段保留，每条新增：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| applicationCount | Long | 本岗位全部状态的投递记录数量 |
| pendingApplicationCount | Long | 当前待处理投递数量，即列表的“新” |
| currentInterviewApplicationCount | Long | 当前面试中的投递数量，不是累计面试 |

无投递返回 0，草稿三项全部为 0，暂停与已结束岗位保留历史投递计数。
“新”不是未读，不新增已读标记。统计投递记录，不跨岗位按用户去重。
求职者 GET /job 仍使用原 JobListVO，不返回企业计数字段。
本任务不新增或迁移数据库字段，不保存冗余计数，不修改前端，不提交或推送 Git。

## 实现说明

### VO 与内部 DTO

CompanyJobListVO extends JobListVO，复用共同的展示字段；只在子类定义三项统计，默认 0L。
父类继续供求职者使用；MyBatis 可设置继承的属性，Jackson 可序列化继承的 getter，已通过接口测试确认。
Lombok EqualsAndHashCode(callSuper=true) 将父类字段纳入子类相等比较。

JobApplicationCountDTO 是 Mapper 返回给 Service 的内部聚合结果，只含 jobId 和三项计数。
它不作为请求 DTO，也不直接对外返回：Service 用 jobId 将它与分页中的企业 VO 对应起来。
这两个对象字段有交集，但分别承担完整接口展示和内部查询传输职责。

### Controller / Service / Mapper

Controller 读取分页、关键词、状态筛选和登录 userId，包装 Service 返回值。
仅企业查询的泛型调整为 CompanyJobListVO，没有加入 SQL 或业务判断。
JobService 接口、实现和 JobMapper.findCompanyJob 的返回类型同步调整。
岗位查询原 SELECT 字段、企业过滤、关键词搜索与排序保持原实现。

Service.findCompanyJob 的步骤：

1. requireCompany 检查企业角色及企业存在，取得服务端 companyId。
2. 校验分页并规范化关键词。
3. PageHelper.startPage 紧邻岗位分页 Mapper 调用。
4. 立即构建 PageInfo，保存岗位 total 与当前页列表。
5. 提取当前页非草稿岗位 ID；没有 ID 时不执行聚合，避免 IN ()。
6. 一次 countApplicationsByJobIds 批量查询，以 jobId 构建 Map。
7. 遍历当前页 VO 填充计数；没有聚合结果保留默认零值。
8. 返回原 PageInfo.total 和列表，total 始终表示岗位数。

ApplicationMapper 的聚合 SELECT 使用 COUNT(*) 和 COUNT(CASE WHEN ... THEN 1 END)，GROUP BY ja.job_id。
WHERE 同时限定当前企业、当前页岗位 ID、ACTIVE/PAUSED/ENDED 岗位状态。
岗位和投递的状态参数使用既有枚举，没有在业务代码散落数字状态码。
SQL 不关联 sys_user 或 resume，简历缺失不影响真实投递数量。
Service 已排除草稿，SQL 仍限定已发布状态，异常草稿即使有旧投递也不计入。

### 为什么先分页再统计

一个岗位可有多条投递。直接 JOIN 明细后分页可能将一个岗位拆成多行，导致岗位重复、分页 total 放大。
当前实现先确定岗位页，再聚合这些 ID，分页对象与统计对象不会混在一起。
页面有多个岗位时仍只执行一次统计 SELECT，避免在循环中逐岗位查询。
PageHelper 的岗位分页通常包含 COUNT 与列表 SELECT；不能把整个接口描述为“只执行一条 SQL”。
权限读取也有其自身查询，本次验证的是投递统计查询不随页面岗位数量逐个增加。

### 事务与权限

Service 使用 Transactional(readOnly=true,isolation=REPEATABLE_READ)。
权限、岗位 COUNT/分页与投递聚合在同一数据库事务快照内读取，不使用岗位详情 Redis 缓存。
方法没有修改数据。只读事务不等同于锁住全部数据，也不要求两个独立 HTTP 请求共享快照。
本次没有专门做并发写入下的快照实验，事务配置依据 MySQL InnoDB 的既有读取机制。

沿用企业列表原 requireCompany 规则，不额外要求审核通过；写操作仍使用原审核规则。
公司 ID 来源于登录用户，客户端伪造 companyId 参数不会改变归属。
聚合 SQL 再次限定 companyId，禁止依赖不受信任的岗位 ID 获得跨企业统计。

## 编译与 Postman 验收

Maven -DskipTests compile 通过，89 个 Java 源文件编译；git diff --check 通过。
没有新增 JUnit；所有后端 HTTP 请求通过 Postman MCP runCollection 执行。
最终 49 个请求、103 个断言全部通过，无失败请求或断言。

| 阶段 | 内容 | 请求数 | 通过断言 |
| --- | --- | ---: | ---: |
| A | 分页、字段、状态、关键词、归属、进展对照、总览范围和参数边界 | 31 | 69/69 |
| B | 投递进入面试/录用、岗位暂停/恢复/结束后的统计变化 | 13 | 25/25 |
| D | 求职者权限与公共岗位列表响应隔离 | 3 | 5/5 |
| E | 没有岗位的未审核企业读取 | 2 | 4/4 |
| 合计 | | 49 | 103/103 |

集合：SmartRecruit API - Optimized Regression，UID 56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
文件夹：03 Job / 企业岗位列表统计回归 2026-10-11，ID 8b784854-d467-8610-6857-c435178ac2b4。
49 条请求已持久保存并回读确认。默认跳过，需要在准备独立数据后设置
runCompanyJobListRegression=true、companyListIsolatedFixtureReady=true，以及 clHost、clApplication 等变量。
执行分组是这些用例的持久副本，因 MCP 只支持按集合运行而用于分阶段选择身份环境。
密码只引用原环境变量，保存的运行结果已脱敏 JWT 和密码。

### 测试环境与数据

独立数据库 smart_recruit_panels_1791700315919，localhost:8082，Redis 数据库 15。
服务运行当前编译代码，复用既有 Spring Boot classpath，验收结束后关闭专用进程。
已审核企业 userId=43/companyId=27，求职者 userId=44，其他企业 companyId=11，隔离空企业 userId=50。
复制开发库结构与必要身份种子，没有修改开发库业务记录；隔离库保留供复查。

本企业六个专用岗位，另一个其他企业岗位，专用 ID 从 930000000 开始：

| 岗位 | 状态 | 原始投递数 | 接口待处理 | 接口当前面试 |
| --- | --- | ---: | ---: | ---: |
| 930000000 | 招聘中 | 5 | 2 | 1 |
| 930000001 | 招聘中 | 0 | 0 | 0 |
| 930000002 | 暂停 | 3 | 1 | 1 |
| 930000003 | 结束 | 2 | 0 | 1 |
| 930000004 | 草稿 | 故意注入异常投递 1 条 | 0 | 0 |
| 930000006 | 招聘中 | 1 | 1 | 0 |

原始投递数是 SQL 核对值；草稿接口 applicationCount 同样为 0。
同一求职者投递不同岗位分别计数。一个已投递简历在隔离库删除，聚合仍统计到它的投递。

### 请求与实际结果

- GET /job/company?keyword=列表统计回归_&size=3&page=1：code=200，total=6，返回 3 个岗位。
- 第二页仍 total=6；第三页为空数组，不生成空 IN SQL。
- 默认无关键词分页包含本企业复制的旧岗位，total=7，没有因 14 条隔离投递放大总数。
- ALL/ACTIVE/PAUSED/ENDED/DRAFT、size=1/100、关键词去空白均正确。
- 伪造 companyId=11 仍只返回当前企业的 6 个匹配岗位。
- 企业总览排除草稿及其他企业投递：applicationCount=11，pending=4，currentInterview=3，未受列表筛选影响。
- 主岗位待处理转面试：三项由 5/2/1 变为 5/1/2；再转录用变为 5/1/1。
- 主岗位暂停与结束后仍返回 5/1/1，与 progress 一致。
- 求职者访问企业列表 code=500，message=仅企业用户可访问；未登录 HTTP 401。
- 求职者 GET /job 只返回招聘中岗位，响应不含三个企业计数字段。
- page=0/-1、size=0/101 被对应业务校验拒绝。
- 空企业 code=200、total=0、items=[]。

### SQL 与数据库证据

SQL 日志共 15 次批量聚合，分属 15 个独立请求，每个请求最多一次。
所有聚合参数都没有包含草稿 930000004 或其他企业岗位 930000005。
第一页只传本页的非草稿 ID 930000006、930000003，第二页只传 930000002、930000001、930000000。
空页、草稿页、没有匹配岗位的查询不执行聚合。
独立 SQL 最终核对：主岗位已结束，投递总数 5、待处理 1、当前面试 1；全隔离库仍有 14 条投递。

结果保存在 target/company-list-result-{A,B,D,E}.json；SQL 参数证据在 target/company-list-sql-evidence.json；
初始与最终 SQL 计数在 target/company-list-fixtures.json、company-list-final-facts.json。
测试编排工具 D:/project/.mcp/postman/company-list-regression.cjs，隔离准备工具 target/CompanyJobListFixture.java。

## 范围限制与后续

这是本接口功能回归，不是整项目回归、性能压测、前端联调或所有并发场景的证明。
批量查询已验证，但没有量化吞吐量和延迟提升。
既有缺失简历时投递详情/完整列表丢记录的问题仍待单独修复。
下一阶段应修复历史投递可见性，再推进岗位前后端联调。

面试应掌握：展示 VO 与内部查询 DTO、继承字段映射、分页总数、条件聚合、N+1、Map 合并补零、
事务快照与跨请求差异。可以准确描述“协作扩展企业岗位分页统计并通过 49 请求、103 断言回归”，
不包装为独立完成，也不宣称未经测量的性能提升。
