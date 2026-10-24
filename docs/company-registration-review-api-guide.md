# 工商资料审核接口与实现说明

## 业务边界

企业通过 PUT /company 修改八个工商字段后，已有保存逻辑生成新工商版本和待审核快照。
本阶段补齐管理员查询、审批及企业本人查询审核反馈，沿用 company 与 company_review_record，无数据库迁移。
任何准入状态企业均可完善及核验工商资料；工商核验、企业准入、营业执照材料审核分别管理。
本阶段提供人工审核工作流，不连接外部工商核验服务；后续材料审核及管理员原件读取见 company-qualification-review-api-guide.md。

## 四个接口

| 业务 | 请求 | 参数 | data | 权限 |
| --- | --- | --- | --- | --- |
| 工商审核分页 | GET /admin/company/registration-reviews | reviewStatus 默认 PENDING，page 默认1，pageSize 默认10 | PageResult<CompanyRegistrationReviewVO> | ADMIN |
| 工商审核详情 | GET /admin/company/registration-reviews/{reviewId} | 正整数审核记录 ID | CompanyRegistrationReviewDetailVO | ADMIN |
| 作出审核决定 | PUT /admin/company/registration-reviews/{reviewId}/decision | @Valid CompanyRegistrationReviewDecisionDTO | null | ADMIN |
| 自己的工商审核历史 | GET /company/registration-reviews | reviewStatus 可选，page/pageSize | PageResult<CompanyRegistrationReviewHistoryVO> | 当前企业 |

分页范围 page>=1，pageSize 为1～100，复用项目已有分页常量。
响应沿用 Result 成功业务码200、失败业务码500；未登录或令牌失效为 HTTP401。
业务失败一般是 HTTP200 的 Result JSON，客户端需要检查业务码。

审核决定示例：

```json
{
  "decision": "REJECT",
  "registrationVersion": 2,
  "reviewComment": "请核实注册地址"
}
```

decision 仅接受 APPROVE、REJECT 名称，不接受数字枚举下标；版本必填且非负。
意见先 strip，空白变 null；最多500个 Java String 字符，REJECT 时必须提供非空意见。
审核人、时间、最终状态及企业 ID 均由服务端确定。未知 JSON 字段即使被框架忽略，也不会参与系统字段赋值。

## 列表、详情和历史字段

管理员列表：id、companyId、companyName、registrationVersion、currentRegistrationVersion、
reviewStatus、currentVersion、reviewable、submittedAt、reviewedAt。
列表 SQL 只查上述摘要，不加载 snapshot_json，也没有逐条再查企业的 N+1 查询。

管理员详情在列表字段上增加 reviewerUserId、reviewComment、snapshot。
companyName 为当前企业简称；snapshot 中的 fullName 等才是该次提交的证据。

企业历史：id、registrationVersion、currentRegistrationVersion、currentVersion、reviewStatus、
reviewComment、submittedAt、reviewedAt、snapshot。不返回管理员账号、其他企业记录或存储引用。

snapshot 使用 CompanyRegistrationSnapshotVO，仅包含八项：fullName、foundedDate、
legalRepresentative、registeredCapital、businessStatus、creditCode、registeredAddress、businessScope。
不会把当前 company 的工商字段填入旧快照，也不直接返回原始 JSON 字符串。

## 当前版本与历史记录

- currentVersion：审核记录版本是否等于企业当前 registrationVersion。
- reviewable：当前版本，审核记录 PENDING，企业工商状态 PENDING，同时满足才为 true。
- 默认 PENDING 管理队列只列当前可处理记录，按 submittedAt ASC、id ASC 排序。
- APPROVED、REJECTED 管理列表包含对应历史结果，使用同样的稳定顺序。
- 过期且未处理的记录保留真实 PENDING，详情 currentVersion=false、reviewable=false；不伪造驳回。
- 企业历史包括所有工商版本，按 submittedAt DESC、id DESC 排序；可按状态筛选，空结果为 total=0、items=[]。
- 超出末页时仍返回条件匹配的 total，items=[]。
- 企业历史的归属从登录用户获取；客户端额外传 companyId 无法切换目标。

驳回后沿用已有提交规则：实际修改工商字段才产生新版本和新记录；原样保存不会重新提交。
本阶段没有新增原样重新提交接口，也不会允许管理员重复改判已经处理的同一条记录。

## 审核状态和事务

| 决定 | 审核记录 reviewStatus | 企业 registrationReviewStatus | verifiedRegistrationVersion |
| --- | --- | --- | --- |
| APPROVE | APPROVED | VERIFIED | 当前被审核的版本 |
| REJECT | REJECTED | REJECTED | 保留之前通过的历史版本，可能为 null |

审核不会修改企业准入 status、工商内容、profileVersion、registrationVersion 或资质材料结果。
主表 updateTime 更新为本次操作时间；reviewedAt 由服务器实际时间生成。

事务执行顺序：

1. requireAdmin 从数据库确认当前身份，取得真实审核人。
2. 普通查询审核记录，仅用于定位企业，且限制 reviewType=REGISTRATION。
3. SELECT company ... FOR UPDATE，先锁企业父行。
4. SELECT company_review_record ... FOR UPDATE，重新读取审核记录的最新已提交状态。
5. 核对客户端版本、记录版本、当前工商版本，以及两处待审状态。
6. 带类型、版本和 PENDING 条件更新审核记录，影响行数必须为1。
7. 带当前工商版本和 PENDING 条件更新企业核验结果，影响行数必须为1。
8. 提交事务；任何异常向外传播，两个更新共同回滚。

先锁企业再锁审核记录，与企业资料保存的主表更新顺序协调。
第二次记录查询是当前读，不能沿用等待父行锁之前的 RR 快照。
两个管理员同时审批时，后到者获得父锁后看到已处理记录，不能覆盖首次意见、审核人或时间。

企业先提交新版时，旧审核等待后将因版本不匹配失败。
管理员先完成旧版审批时，企业后续编辑会产生新版本并重新 PENDING；旧版通过不能让新版被标为已核验。
普通字段更新的 SQL 使用数据库当前工商状态作为 ELSE 分支，保留并发审核结果。

三个查询方法使用 readOnly、REPEATABLE_READ，让记录、版本和结果来自同一快照。
PageHelper 只在权限查询完成后开启，紧接着执行分页 SQL，并在 finally 清理线程上下文。

## 代码入口及阅读顺序

1. CompanyRegistrationReviewController：四个路由、参数接收、@Valid、Result 包装。
2. CompanyRegistrationReviewDecisionDTO：基本输入与驳回意见组合校验。
3. CompanyRegistrationReviewServiceImpl：身份、业务规则、快照解析和事务。
4. CompanyRegistrationReviewMapper：列表投影、父子行锁及有版本/状态条件的更新。
5. 四个 VO：管理员摘要、管理员详情、企业反馈、八字段快照。

CompanyReviewDecision 的 JsonCreator 使用名称解析，防止 JSON 数字0默认为第一个审核决定。
这不改变枚举落库的英文名称。

## 面试理解

- reviewId 定位一次提交，registrationVersion 确认该提交仍对应当前工商资料。
- DTO 校验解决输入是否合法；Service 解决此时是否允许处理；SQL 条件和锁防止并发窗口。
- 主表与审核记录共同事务，避免界面显示待审但历史已通过的部分成功。
- 版本核验结果与企业准入分别管理，避免一次资料核验意外开放招聘权限。
- 审核快照保留提交事实；历史通过版本与当前版本是否通过需要分别表达。
- 人工审核记录、版本保护及事务一致性可以据实写入简历；不据功能回归宣称吞吐量或并发规模。
