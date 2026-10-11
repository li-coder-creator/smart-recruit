# 投递状态白名单改造计划

## 业务目标和范围

企业可跳过筛选、面试等中间阶段向前处理投递，禁止反复回退或在终态之间切换。
这份文档保留原始改造计划。后续已按用户授权实现 ApplicationStatus、ApplicationServiceImpl 和条件更新 SQL。
代码编译通过，后续 Postman 白名单验收 110 个实际请求、218 个断言通过；
实际实现及讲解见 application-status-whitelist.md，结果见 application-status-whitelist-regression-2026-10-04.md。

## 最终迁移契约

| 当前状态 | 允许的目标状态 |
| --- | --- |
| PENDING 待处理 | SCREENING、INTERVIEW、HIRED、REJECTED |
| SCREENING 筛选中 | INTERVIEW、HIRED、REJECTED |
| INTERVIEW 面试 | HIRED、REJECTED |
| HIRED 录用 | 无，终态 |
| REJECTED 拒绝 | 无，终态 |

同状态请求建议返回“投递已处于目标状态”，不更新状态、时间和历史标记。
禁止筛选中回到待处理，禁止面试回到筛选中或待处理，终态不能再修改。
直接录用不代表曾进入面试；拒绝是各非终态可选择的出口。
不能用 targetCode > currentCode 判断合法性：数字顺序会错误放行 HIRED → REJECTED。
未来撤销录用或拒绝需独立业务规则，不通过当前接口反向修改。

沿用 PUT /application/{id}/status，DTO 仍传 Integer status，正常返回 Result.success。
非法迁移沿用项目业务错误 code=500，并给出“不能从面试转为筛选中”等明确文案。
失败响应不改变任何投递数据。并发冲突文案建议“投递状态已变化，请刷新后重试”。
岗位结束后的历史投递处理沿用现有行为，不在本阶段新增岗位状态限制。

## 分层实现

### Controller / DTO

Controller 继续接收 ID、经过验证的 DTO 和登录 userId，调用 Service。
DTO 的非空、状态码范围只负责输入合法性，不承担迁移规则。
本阶段不修改状态码或数据库存储形式，不把迁移白名单写在 Controller。

### ApplicationStatus

新增 canTransitionTo(ApplicationStatus target) 方法，使用明确的 enum 集合或 switch 维护白名单。
只使用 PENDING、SCREENING、INTERVIEW、HIRED、REJECTED，避免状态数字散落在业务判断中。
集合应保持不可变；文案取已有 description，不新增另一套状态名称。

### ApplicationServiceImpl.updateStatus

建议顺序：

1. requireApprovedCompany 获取企业。
2. 用新增 ApplicationMapper.findById 读取 job_application 原记录。
3. 查岗位，检查存在性和企业归属。
4. 从原记录解析当前状态，从 DTO 解析目标状态。
5. 同状态请求、终态请求、反向请求按契约拒绝。
6. 合法迁移通过带 expectedStatus 的 SQL 原子更新。
7. 受影响行数必须是 1；为 0 时报告状态已变化，不自动重试为其他迁移。

当前使用 findApplicationDetail，带 INNER JOIN 用户、简历等展示表。
业务状态变更应读取原始 Application 实体，避免展示信息缺失被误判为“该投递不存在”。
为公开 updateStatus 方法增加 @Transactional，确保后续业务操作在同一事务内。
新增原记录查询和原子更新，不改动投递详情的展示查询。

### Mapper / 数据库

将 updateStatus 从 void 改成 int，并新增 expectedStatus 参数。
建议 WHERE 同时限定 id、job_id 和 expectedStatus，更新 SQL 示例：

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

前提是 Service 已验证企业归属和迁移白名单；SQL 的原状态条件处理校验后的并发变化。
现有 has_interviewed 更新在 status 赋值之前，要保留这一顺序：MySQL 单表更新通常按赋值顺序计算。
当前面试转录用或拒绝时，即使历史标记是 NULL，也有旧状态证明曾进入面试，因此应置 TRUE。
其他路径保留原 TRUE/FALSE/NULL，不把未知历史猜成 FALSE。
本阶段不新增字段、不修改状态码，也不批量改写旧状态。

## 并发行为

本阶段建议使用条件更新处理状态竞争，不额外对全部投递读取加 FOR UPDATE。
两个请求读取同一旧状态后，只有一个能成功匹配 expectedStatus；另一个返回刷新重试。
请求若在前一个提交后才读取到新状态，则按新状态判断，可能是另一条合法向前迁移。
不能笼统宣称任何两次并发请求都只成功一次；需明确它们读取了哪一个原状态。
同状态请求保持拒绝，对重复点击和网络重试的表现与岗位状态接口一致。

## 统计影响

招聘总览、单岗位进展和最近投递的字段不变，无需新增 VO。
当前面试数量仍统计 INTERVIEW；累计面试数量仍依赖 has_interviewed 及当前面试证据。
从面试转录用/拒绝后，当前面试减少，累计面试保留。
待处理/筛选中直接录用，累计面试不凭录用状态增加。
存在未核实历史时，累计面试和转化率继续按既有规则返回 NULL。
投递状态变更不生成岗位操作时间线事件，两个记录对象的业务范围不同。

## 实施阶段和验收

枚举、原记录查询、带原状态的 SQL、Service 校验、事务及影响行数处理已由助手按后续授权实现。
Postman MCP 验收已完成，用例保存到 SmartRecruit API - Optimized Regression / 05 Application / 投递状态白名单回归 2026-10-04。

验收至少包括：

- 5×5 共 25 种当前/目标状态组合：9 种合法迁移，16 种拒绝（包含 5 种同状态）。
- 未登录、求职者、未审核企业、其他企业、不存在投递和非法目标状态。
- 跳过筛选、跳过面试的合法路径，终态保护和所有反向路径。
- 失败请求的状态、update_time、has_interviewed 不变化。
- 同原状态的并发更新只能成功一个，冲突返回明确文案。
- 进入面试及离开面试保留历史，直接录用不补造面试经历。
- 旧 NULL 历史的保留和统计 NULL 行为。
- 总览、单岗位进展、最近投递展示随状态变化正确，投递时间排序不变。

交付物：枚举规则、Mapper 与 Service 改动、接口契约说明、持久回归用例和结果。
面试重点：状态机显式白名单、终态、输入校验与业务校验的区别、条件更新、并发冲突和历史事实保留。
