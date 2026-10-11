# 单岗位招聘进展接口

## 接口与统计口径

`GET /job/company/{id}/progress` 返回 `Result<JobRecruitmentProgressVO>`。
企业 ID 来自登录用户对应的企业，不接受客户端传入 companyId。
Controller 接收岗位 ID、读取当前用户 ID、调用 Service 并包装响应；现有路由无需修改。

| 字段 | 含义 |
| --- | --- |
| jobId | 被查询的岗位 ID |
| jobStatus | 岗位状态：0招聘中、1暂停、2结束、3草稿 |
| applicationCount | 该岗位全部状态的投递记录数 |
| pendingApplicationCount | 当前 PENDING 的投递记录数 |
| currentInterviewApplicationCount | 当前 INTERVIEW 的投递记录数 |
| interviewApplicationCount | 曾实际进入面试的投递记录数；旧历史无法核实时为 null |
| interviewConversionRate | 累计面试数 / 累计投递数 × 100，HALF_UP 保留两位小数；无投递或历史未知时为 null |

沿用总览的 requireCompany：用户必须是企业角色且存在对应企业记录，不额外要求审核通过。
岗位不存在返回业务错误“岗位不存在”；其他企业的岗位返回“没有查看权限”。
求职者返回“仅企业用户可访问”，未登录请求由登录拦截器返回 HTTP 401。
项目的业务异常仍按既有 Result.error 返回业务码 500，这次未调整全局响应规范。

草稿返回岗位身份和状态、全部计数 0、转化率 null；前端可显示“尚未发布”。
招聘中、暂停、已结束岗位正常统计投递，暂停与结束不会抹去历史。

## 实现与原理

Service 首先调用 requireCompany，再直接从数据库读取岗位并比较 companyId。
既有 requireOwnedJob 用于岗位写操作，包含审核通过的要求，因此本查询采用与总览一致的只读权限校验。
不从岗位详情 Redis 缓存读取状态，避免缓存状态与数据库投递统计来自不同时间。

通过权限检查后，草稿直接构造零计数 VO；其他状态调用 ApplicationMapper.countJobApplications。
Mapper 用 WHERE job_id = #{jobId} 限定一个岗位，不加载投递明细，不关联用户或简历表。
在同一只读 REPEATABLE_READ 事务中校验岗位归属并聚合投递，两次读取使用同一数据快照。
查询不会把统计写入数据库，也不会缓存进展；后续请求读取最新已提交数据的快照。

COUNT(*) 统计全部记录，COUNT(CASE WHEN 条件 THEN 1 END) 统计满足条件的记录。
条件不成立时 CASE 默认为 NULL，COUNT 表达式只统计非 NULL。
没有 GROUP BY 时，即使该岗位没有投递也返回一行零计数，Service 不会把“无投递”误判为“无岗位”。
岗位存在性必须先由 jobMapper.findById 判断，不能通过聚合计数是否为零来判断。

has_interviewed=true 表示已有历史面试证据，false 表示未进入过面试，null 表示旧历史未知。
当前 INTERVIEW 自身也是证据，因此历史计数包含当前面试以及已离开面试且标记为 true 的记录。
若该岗位有历史未知且当前不在面试的记录，完整累计面试数无法确定，返回 null。
未知只影响本岗位，不影响其他岗位进展；当前投递和状态计数仍可返回明确结果。
直接录用的 has_interviewed=false，不能因当前是 HIRED 就推断经历过面试。

Mapper 返回的 VO 先填充计数字段，Service 再补 jobId、jobStatus 和转化率。
总览与单岗位进展调用同一个 calculateInterviewConversionRate 私有方法，复用现有百分数和精度常量。
方法首先处理分母为零、历史计数未知的边界，再用 BigDecimal 乘 100、除以投递总数、保留两位小数。
本次复用了已迁移的 has_interviewed 列，没有新增数据库变更。
最近投递与岗位操作记录已实现，Postman 联合回归通过；投递状态白名单后续也已通过独立运行时验收，
说明见 application-status-whitelist.md，结果见 application-status-whitelist-regression-2026-10-04.md。

## Postman 回归记录

38 条持久用例已加入 SmartRecruit API - Optimized Regression / 03 Job /
单岗位招聘进展回归 2026-10-03，通过本机 Postman MCP 执行。
执行前只读核对了工作区、企业和求职者环境、已有样本、简历 29 与 has_interviewed 列。
使用原企业环境 Smart Recruit Job Regression，以及原求职者环境 Smart Recruit Local；没有复制或上传密码。

| 阶段 | 请求数 | 通过断言 |
| --- | --- | --- |
| 企业权限、既有终态样本、草稿和无投递 | 15 | 29/29 |
| 求职者权限及新增投递 | 4 | 6/6 |
| 状态变化、暂停恢复结束、草稿删除、总览回归 | 17 | 33/33 |
| 旧历史未知及其他岗位隔离 | 3 | 7/7 |
| 合计 | 39 | 75/75 |

未知历史阶段重复登录企业，因此实际请求数比持久用例数多一条。
测试执行接口都通过 Postman MCP；SQL 仅用于只读数据核对及本轮独立样本的历史未知准备与恢复。
旧历史未知只临时修改本轮测试投递 15 的标记，保留 update_time，测试后已恢复为 true。
Postman 用例元数据更新时遇到集合 UID 的 parent 错误，改用该集合 UUID 后完成更新，不涉及后端缺陷。

本轮企业 companyId=27、求职者 userId=8；只新增草稿 29、已发布岗位 30 和投递 15，使用已有测试简历 29。
草稿 29 已删除，岗位 30 已结束，投递 15 为面试后录用，历史标记保留 true，供后续复查。
既有岗位 26、27、28 均未修改：分别验证面试后录用、直接录用、面试后拒绝的统计。

最终 `GET /job/company/30/progress` 的预期字段均经实际响应断言核对：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "jobId": 30,
    "jobStatus": 2,
    "applicationCount": 1,
    "pendingApplicationCount": 0,
    "currentInterviewApplicationCount": 0,
    "interviewApplicationCount": 1,
    "interviewConversionRate": 100.00
  }
}
```

模拟历史未知时，interviewApplicationCount 和 interviewConversionRate 为 null，其他字段仍保持上述结果。
同时岗位 27 继续返回累计面试 0、转化率 0，验证不存在跨岗位混算。
总览回归验证从累计投递 3 / 累计面试 2 / 66.67% 变为 4 / 3 / 75.00%。

默认跳过本文件夹的数据写入回归，手动执行需设 runJobProgressRegression=true。
按企业准备、求职者投递、企业状态更新分阶段运行，切换到对应已有环境；主集合使用 host 变量。
固定样本前提是测试企业拥有岗位 26、27、28，求职者拥有简历 29，岗位 12 属于其他企业。
未知历史用例还需本轮独立数据准备及 jobProgressUnknownFixtureReady=true，普通流程默认跳过。
运行器使用独立 8082 服务，运行结束已关闭；开发服务需要重新启动以加载新代码。

本次还通过 Maven compile 与 git diff --check。
本轮未创建第二求职者验证同一岗位多条投递，也未通过并发请求测试快照隔离；这些边界仅做了代码审查。

## 面试表达

需要能解释企业角色校验与岗位归属校验的区别、无岗位与无投递的区别、COUNT 条件聚合、
可重复读事务的数据快照、详情缓存与统计一致性、当前状态与历史证据、BigDecimal 的舍入规则。
单岗位统计范围通过 job_id 参数限制；企业总览通过企业归属和岗位状态范围限制，两者共用业务定义但查询范围不同。
