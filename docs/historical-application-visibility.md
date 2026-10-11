# 历史投递可见性：实现与验收

2026-10-11，按用户授权实现投递查询修复并通过 Postman MCP 回归。

## 业务目标与范围

简历删除后，真实存在的投递继续出现在企业列表、求职者列表和投递详情中。
关联简历的缺失只影响资料展示，不影响投递本身的存在性、状态或招聘统计。

涉及 ApplicationMapper、ApplicationServiceImpl 和 ApplicationVO 契约。
用户此前已添加 resumeAvailable，本次保留该字段，未重复添加或调整其位置。
Controller 路由、返回包装、数据库结构、简历删除规则和投递状态白名单保持现有实现。
本次没有修改前端，没有提交或推送 Git，没有修改开发数据库记录。

## 接口契约

| 接口 | 权限 | 返回 |
| --- | --- | --- |
| GET /application/my | 已审核企业，只看本企业岗位收到的投递 | List<ApplicationVO> |
| GET /application/mine | 求职者，只看自己的投递 | List<ApplicationVO> |
| GET /application/{id} | 投递所属求职者，或岗位所属已审核企业 | ApplicationVO |

响应仍使用 Result 包装。正常响应业务码为 200；既有业务拒绝使用 HTTP 200、业务码 500。
未登录仍由拦截器返回 HTTP 401。

| 字段 | 简历存在 | 简历缺失 |
| --- | --- | --- |
| resumeId | 投递表保存的原简历 ID | 保留原简历 ID |
| resumeAvailable | true | false |
| resumeTitle | 当前简历标题，可以为空字符串或空白字符串 | null |
| id / userId / jobId / status / createTime / updateTime | 来自投递记录 | 来自投递记录 |

resumeAvailable 是关联查询计算字段，不是新增数据库列，不代表拥有投递时的简历快照。
标题为空不等于简历不存在。当前枚举的 INTERVIEW 文案为“面试”，HIRED 文案为“录用”。
前端后续应根据 resumeAvailable=false 提示资料不可用并禁用原简历入口，保留投递进度展示。

## 实现说明

### Mapper

三个展示查询增加 `(r.id IS NOT NULL) AS resume_available`，MyBatis 下划线转驼峰映射至 Boolean 字段。
简历、账号、企业名称使用 LEFT JOIN，资料缺失时返回 null，不伪造历史内容。

企业列表保留 JOIN job 和 WHERE j.company_id = companyId，以岗位归属限定可见数据。
求职者列表按 job_application.user_id 限定归属，其他关联采用 LEFT JOIN。
详情从 job_application 出发，展示关联采用 LEFT JOIN，权限由 Service 先校验。
列表按 create_time DESC、id DESC 排序，在相同投递时间下保持确定顺序。
三个方法使用具名 @Param；原始 findById 复用已有方法。

### Service

求职者列表增加 requireJobSeeker，避免其他角色使用求职者接口。
详情流程：查询原始 Application → 检查记录存在 → 读取当前账号角色 → 检查投递/岗位归属 →
查询展示 VO → 使用原投递状态转换状态文字。

只有原始投递不存在才返回“该投递不存在”。企业访问时岗位缺失仍拒绝，不能在归属无法确认时放行。
通过权限后若展示查询异常返回 null，返回“投递详情读取失败，请刷新后重试”。
详情使用 @Transactional(readOnly=true, isolation=REPEATABLE_READ)，使该请求内的普通查询在
MySQL InnoDB 中使用同一事务快照。本次没有执行并发删改压力实验。

### Controller

沿用原参数、登录 userId 和 Result 包装，无需增加 SQL、权限或资料缺失判断。

## 编译与检查

`mvn -o -Dmaven.repo.local=C:\Users\李\.m2\repository -DskipTests compile`：BUILD SUCCESS，89 个 Java 源文件。
AdminController 原有 unchecked 警告未在本次范围内处理。
git diff --check 通过；Git 的 CRLF 提示属于换行转换提示。

## Postman 验收

所有 HTTP 请求通过本地 Postman MCP runCollection 执行，没有使用鼠标控制 Postman或其他 HTTP 客户端。

| 最终分组 | 请求 | 断言 | 结果 |
| --- | ---: | ---: | --- |
| A 企业列表、详情、资料标记、计数、基本权限 | 17 | 30 | 全通过 |
| B 求职者列表、详情、实际删除简历前后链路 | 15 | 27 | 全通过 |
| C 删除后的企业链路、统计与状态白名单 | 22 | 40 | 全通过 |
| D 求职者最终状态、其他求职者隔离 | 10 | 18 | 全通过 |
| E 其他企业、未审核企业、管理员 | 13 | 21 | 全通过 |
| 合计 | 77 | 136 | 全通过 |

首次 E 分组执行 6 请求、11 断言，其中 2 个登录断言失败：隔离未审核企业用户名长度为 27，
超出既有 26 位校验。缩短隔离用户名，更新主集合和执行分组中的登录请求后，完整复跑 E 通过。
该失败属于测试数据准备错误，没有修改应用校验规则。累计执行 83 请求、147 断言，其中首次 2 个失败；
最终完整五组结果为上表的 77 请求、136 断言通过。

### 持久集合

主集合：SmartRecruit API - Optimized Regression

- UID：56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82
- 目录：05 Application / 历史投递可见性回归 2026-10-11
- 目录 ID：bea29a6d-2b7d-a378-1dac-d064dd445a2c
- 77 个请求已保存，并通过 getCollection 读回确认。

主目录中的请求默认跳过，必须在准备隔离数据后显式设置 runHistoryVisibilityRegression=true、
historyIsolatedFixtureReady=true，并设置 haHost。A/C/E 使用企业环境，B/D 使用求职者环境。
必须按 A → B → C → D/E 执行，B 会删除测试简历，C 会更新投递状态。
再次执行需要恢复专用测试数据的初始状态，不能把这些破坏性测试指向开发数据库。

因 MCP runner 只提供 collection 执行参数，另保存对应执行分组；用例同时完整保存在约定主集合中：

- A：56227183-bc895969-cf10-4fd1-ab8b-0f01e3dfe458
- B：56227183-d629735d-ee49-4949-849f-da14687de74c
- C：56227183-ed115bad-cd7c-46cd-a443-372c9a64e0ff
- D：56227183-2e3fea94-0c75-4e45-abed-8531317b7027
- E：56227183-948b025f-0f59-41f9-8dfc-7639eb372b52

### 测试环境与数据

独立数据库 smart_recruit_panels_1791703953186，专用端口 8082，Redis database=15。
已审核企业用户 43 / 企业 27；其他企业用户 14 / 企业 11；求职者 44、45；未审核企业 50；管理员 51。
8 条独立投递：正常简历、通过接口删除的简历、空标题、空白标题、预先缺失的简历、
其他企业岗位、另一求职者以及账号资料缺失。投递时间均为 2026-10-11 08:00:00，用于检查稳定排序和时间保留。

准备阶段尝试创建岗位不存在的投递时，数据库 fk_application_job 外键拒绝插入。
未关闭该约束；该场景没有作为 HTTP 成功用例执行。放弃的首次准备数据库未用于上述验收。

### 请求与响应证据

1. GET /application/my：企业收到 7 条，包含简历缺失、账号资料缺失记录；不包含外企投递。
2. GET /application/mine：求职者 44 收到 6 条，包括自己向另一企业的投递，不含求职者 45 的投递。
3. DELETE /resume/940000001：HTTP 200、code=200、data=null；随后读取原简历业务拒绝“简历不存在”。
4. GET /application/940000001：删除简历后企业和所属求职者均成功，关键字段由断言确认如下：

```json
{
  "code": 200,
  "data": {
    "id": 940000001,
    "resumeId": 940000001,
    "resumeTitle": null,
    "resumeAvailable": false,
    "status": 3,
    "statusText": "录用",
    "createTime": "2026-10-11T08:00:00"
  }
}
```

上述仅列最终录用后已断言的关键字段，省略其他字段；删除后待处理阶段同样成功。

5. PUT /application/940000001/status：待处理直接面试成功，面试回退待处理拒绝；面试录用成功，录用回退面试拒绝。
6. 同岗位 progress、企业 overview 和企业岗位分页计数在各阶段一致，删除不降低投递总数。
7. 其他求职者、其他企业读取该投递返回 code=500、“无权访问该投递”；未审核企业、管理员按现有权限拒绝。
8. 未登录详情 HTTP 401；真实不存在的投递返回“该投递不存在”。

最终 SQL 独立核对：8 条投递全部保留，只有 940000001 从待处理进入面试再录用，has_interviewed=true，
其他投递仍待处理；两条简历缺失的投递保留原 resume_id。

本地证据：target/history-result-{A,B,C,D,E}.json、history-result-E-initial.json、history-final-facts.json、
history-cases.json、history-state.json 和专用服务日志。结果文件中的密码、令牌已脱敏。

## 边界与后续

本阶段恢复投递可见性，不能恢复已删除简历内容，也不能保证实时引用是投递时的内容。
后续需要另行设计投递时简历快照，不能将旧记录的当前简历伪装为已确认的历史快照。
未进行前端联调、并发压力实验或性能基准测试。开发服务需要重启以加载当前编译结果。

面试需掌握：LEFT JOIN 与 WHERE 的过滤行为、业务主体与展示资料分离、原始记录的权限校验、
重复读事务快照、实时引用与历史快照的区别。理解并能维护后可准确描述为“参与招聘投递历史可见性修复，
通过关联查询和归属校验保留缺失简历的投递记录，并完成 Postman 权限与状态回归”；不可声称已实现简历快照。
