# 投递状态白名单实现说明

## 实现与验证状态

已按用户授权完成枚举规则、原始投递查询、Service 事务/权限/迁移校验和原状态条件更新。
Maven 跳过测试的 compile 已成功，Git diff 空白检查通过。
实现阶段按“先实现并讲解”的要求没有执行测试。随后 2026-10-04 经用户授权通过 Postman MCP 验收：
白名单 110 个实际请求、218 个断言全部通过；另 2 个诊断请求确认缺失简历时既有详情查询错误。
数据、响应、首次测试账号问题及重跑记录见 application-status-whitelist-regression-2026-10-04.md。
没有执行数据库迁移、修改存量记录、改变状态码、提交或推送 Git。

接口仍为 PUT /application/{id}/status，入参 status 为 Integer。
Controller 保留 @Valid DTO 校验，响应沿用 Result；成功 code=200，业务拒绝 code=500。

## 状态规则

| 当前状态 | 允许的目标 |
| --- | --- |
| PENDING 待处理 | SCREENING、INTERVIEW、HIRED、REJECTED |
| SCREENING 筛选中 | INTERVIEW、HIRED、REJECTED |
| INTERVIEW 面试 | HIRED、REJECTED |
| HIRED 录用 | 无 |
| REJECTED 拒绝 | 无 |

ApplicationStatus.canTransitionTo 使用 switch(this) 和枚举比较实现白名单，不依赖状态数字大小。
target 为 NULL 时返回 false；所有同状态和终态变更也返回 false。
HIRED 和 REJECTED 是两个终态，不允许录用再拒绝，也不允许拒绝后恢复。
switch 显式覆盖所有现有状态，增加新状态时需要明确补充迁移规则。

Service 对同状态单独返回“投递已处于目标状态”；其他非法迁移返回“不能从当前状态转为目标状态”。
DTO 只校验输入非空及范围，无法回答“这个投递现在能不能变成这个状态”，因此迁移校验放在 Service。
当前 fromCode 对未知状态抛异常；旧数据存在非法状态时不会被猜测成某个有效状态。

## 调用链与权限

1. Controller 取得经过登录拦截器验证的 userId，调用公开 Service 方法。
2. requireApprovedCompany 确认企业角色、企业存在且审核通过。
3. ApplicationMapper.findById 读取 job_application 的原始实体。
4. 读取关联岗位并检查岗位存在性、companyId 与当前企业一致。
5. 解析 currentStatus 和 targetStatus，同状态拒绝后调用白名单判断。
6. 合法时调用原状态条件更新，受影响行数必须为 1。

权限校验先于状态规则，其他企业不能修改这条投递。
原始查询明确选择 id、user_id、job_id、resume_id、status、has_interviewed、create_time、update_time。
它不依赖简历或用户 JOIN，展示资料缺失不会被误判为原投递不存在。
投递详情的既有展示查询继续使用 ApplicationVO。
岗位结束后的历史投递仍沿用既有可处理行为，白名单只控制投递状态迁移。

## 原子更新与历史标记

Mapper.updateStatus 返回 int，并接收 jobId、expectedStatus、targetStatus 和 interviewStatus。

```sql
UPDATE job_application
SET has_interviewed = CASE
        WHEN #{targetStatus.code} = #{interviewStatus.code}
            OR status = #{interviewStatus.code} THEN TRUE
        ELSE has_interviewed
    END,
    status = #{targetStatus.code}
WHERE id = #{id}
    AND job_id = #{jobId}
    AND status = #{expectedStatus.code}
```

WHERE 限定原先读到的岗位和投递状态。已有状态被其他请求更新时，当前请求影响 0 行。
Service 抛出“投递状态已变化，请刷新后重试”，不自动用新状态重试或覆盖其他操作。

SQL 先计算历史标记，最后赋值 status，保留 MySQL 单表 SET 赋值顺序所需的旧状态证据。
进入面试置 TRUE；原状态为面试、转录用或拒绝时同样置 TRUE。
直接录用/拒绝不会凭结果创造面试经历。其他路径保留原 TRUE、FALSE 或 NULL。
has_interviewed 和 status 在同一条 UPDATE 中修改，不存在两条 SQL 中途失败导致两字段不一致的问题。
拒绝迁移时不执行 UPDATE；条件未匹配时也不更新状态、历史标记或更新时间。
沿用既有数据库更新时间机制，原始 create_time 不修改，因此最近投递排序规则不变。

## 事务与并发的区别

公开 updateStatus 使用 @Transactional，由 Controller 调用 Spring Service 代理开启事务。
BusinessException 是 RuntimeException，默认导致事务回滚。
事务提供同一业务操作的提交/回滚边界；它不会自动阻止其他事务读取同一旧状态。
并发防覆盖依赖 WHERE status=expectedStatus，属于基于业务状态的乐观并发控制。
读取投递没有增加 FOR UPDATE；数据库执行 UPDATE 时仍会取得必要的行锁。

例如两个请求都读到 SCREENING：A 转 INTERVIEW 成功后，B 带 SCREENING 条件转 HIRED 匹配不到，报刷新重试。
如果 B 是在 A 提交后才读到 INTERVIEW，则 INTERVIEW → HIRED 合法，B 也可能成功。
应说“同一原状态的竞争更新最多一个成功”，不能说“任何两次并发请求最多一个成功”。
同状态请求采用明确拒绝策略；这不是重复请求返回成功的幂等契约。
该实现限制通过应用接口的迁移，不会阻止绕过接口直接执行数据库 SQL 的状态修改。

## 统计影响与下一步验收

招聘总览、单岗位进展和最近投递的字段可以复用。
面试转录用/拒绝后当前面试数减少，累计面试证据保留；直接录用不增加已知累计面试。
历史未知记录继续保持 NULL 口径。投递状态修改不写岗位操作记录表。

以下用例现已加入 SmartRecruit API - Optimized Regression 并执行，详情及范围以验收报告为准：

- 25 种当前/目标组合：9 种合法、16 种拒绝，包括 5 种同状态。
- 企业审核、角色、资源归属、不存在投递和非法入参。
- 同原状态竞争的条件更新冲突及明确错误文案。
- 拒绝请求的数据不变；进入/离开面试的 TRUE 保留；直接录用的 FALSE 或 NULL 保留。
- 展示关联缺失时的原记录查找、统计及最近投递展示。

面试应掌握：输入校验与业务校验的区别、显式状态机及终态、枚举身份比较、事务与乐观并发控制的不同职责、
受影响行数、原子更新，以及当前状态和历史事实的区别。
