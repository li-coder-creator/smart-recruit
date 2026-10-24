# 资质材料审核：接口和实现讲解

## 接口契约

| 业务 | 请求 | data | 权限 |
| --- | --- | --- | --- |
| 审核列表 | GET /admin/company/qualification-reviews | PageResult<CompanyQualificationReviewVO> | ADMIN |
| 审核详情 | GET /admin/company/qualification-reviews/{reviewId} | CompanyQualificationReviewDetailVO | ADMIN |
| 读取原件 | GET /admin/company/qualification-reviews/{reviewId}/file | 文件字节 | ADMIN |
| 审核决定 | PUT /admin/company/qualification-reviews/{reviewId}/decision | null | ADMIN |
| 本人审核历史 | GET /company/qualification-reviews | PageResult<CompanyQualificationReviewHistoryVO> | 当前企业 |

管理员列表：reviewStatus默认PENDING，qualificationType可选，page默认1、pageSize默认10。
企业历史：reviewStatus可选，page/pageSize相同。分页边界复用已有常量：page>=1、pageSize为1～100。
JSON响应沿用Result业务码200/500；未登录HTTP401。文件错误仍可能返回HTTP200的Result JSON，前端Blob请求需检查Content-Type。

审批DTO只接受decision、reviewComment。决定复用APPROVE/REJECT名称枚举，拒绝数字下标；
驳回意见必填、最多500字符、首尾空白归一化。审核人和时间由服务端设置。
每次上传产生独立材料ID和审核记录，路径reviewId已经定位一次提交，不提交工商registrationVersion。

## 响应和历史边界

列表只加载材料元数据、企业简称、审核状态、removed、reviewable及提交/审核时间，不读取文件字节或完整快照。
详情增加审核人、意见、结构化snapshot和受保护downloadUrl。
snapshot包含qualificationId、qualificationType、originalFilename、contentType、fileSize、sha256。
企业历史包含自己的材料元数据、移除标记、结果、意见和时间，不包含审核人账号或磁盘路径。
历史在同一SQL中关联材料，无逐条加载材料的N+1查询。

PENDING队列只列未移除且材料和审核记录均PENDING的记录；APPROVED/REJECTED列表保留包含已移除材料的历史。
管理员列表按submittedAt、id升序；企业历史倒序。空结果items=[]，超末页仍保留total。
已移除且未审核的记录保留真实PENDING，可经历史ID查看详情，reviewable=false，不伪造驳回结果。

## 逐个接口的实现

1. list：Controller接收筛选参数，Service确认ADMIN和分页边界，再开启PageHelper，Mapper查询摘要。
   SQL始终限制reviewType=QUALIFICATION，并关联同企业的材料，避免混入工商审核或跨企业错误关联。
2. detail：读取审核记录与同企业材料，显式组装VO。removed表示移除事实；reviewable需要未移除且两处均待审。
   snapshot从提交时JSON解析，下载地址以审核记录ID构造，不返回storageKey。
3. readFile：确认管理员，按企业→材料→审核记录顺序加锁。允许读取已移除原件用于审核追溯。
   有界读取最多10MiB，核对快照元数据、实际大小和SHA-256，返回attachment、UTF-8文件名、no-store、nosniff。
   锁在字节读入内存后释放，不持有到浏览器传输结束；文件目录没有静态公开映射。
4. decide：同样按父行到子行顺序锁定并重新读取最新状态。已移除或已处理即拒绝；APPROVE先核对原件，REJECT可在原件不可用时给出原因。
   先写审核记录，再写材料状态，SQL均带归属、类型、PENDING条件并要求影响行数为1；任一失败抛异常共同回滚。
5. history：由requireCompany确定本人企业ID，查询参数不能切换企业。只查询该企业材料审核反馈，返回专用VO。

只读查询使用REPEATABLE_READ，分页在身份查询之后开始，finally清理。
写入及有锁文件读取使用外层事务；首次普通查询只用于定位企业，决策依据加锁后的当前读。

## 状态、并发和文件完整性

通过时company_qualification和company_review_record同时变成APPROVED；驳回时同时REJECTED。
企业准入和工商核验分别管理。businessLicenseVerified仍由未移除且已通过的营业执照EXISTS计算，不额外写冗余标记。
有多份有效执照时，移除一份不应清除其余材料的效力；唯一有效执照移除后标记失效。

移除先完成：审批锁内发现deleted，拒绝。审批先完成：企业随后仍可移除，保留原审核结果与原件，展示标记按当前有效材料计算。
两名管理员竞争：后到者取得父锁后发现已处理，不得改判或覆盖首次证据。

SHA-256证明读到的内容与上传快照一致；它不证明证照真实性。
元数据不匹配、原件丢失、内容摘要变化时，读取及通过审核被拒绝，仍可对未移除待审材料驳回并说明原因。
管理员通过历史记录读取已移除原件是明确的审核权限；企业自己的旧下载入口继续排除已移除材料。
原件沿用现有保留策略，实际保留期限和多实例对象存储迁移另行设计。

## 阅读代码和面试

- CompanyQualificationReviewController：五个路由和文件HTTP响应。
- CompanyQualificationReviewDecisionDTO：格式及组合校验。
- CompanyQualificationReviewServiceImpl：身份、关联、状态、锁、摘要及事务。
- CompanyQualificationReviewMapper：元数据分页、同企业关联、当前读和条件更新。
- 三个展示VO及一个快照VO：输出字段白名单。

需要能解释：登录与资源授权的区别；为什么逻辑删除不抹去历史；为什么读取原件也需要事务协调；
为什么先锁父行；数据库事务为何能回滚两张表，但不能修复磁盘上被外部改写的文件。
本阶段不新增数据库表或迁移，保留此前工商审核和文件管理行为。
