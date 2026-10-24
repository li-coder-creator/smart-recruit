# 企业资料：管理读写、主页预览与在招岗位

## 范围

本阶段提供五个接口，沿用 Result 成功业务码 200、失败业务码 500；
未登录或令牌无效由登录拦截器返回 HTTP 401。没有增加匿名白名单。

| 接口 | 访问身份 | 参数 | 响应 data |
| --- | --- | --- | --- |
| GET /company | 当前企业，准入状态不限 | 无 | CompanyProfileVO |
| PUT /company | 当前企业，准入状态不限 | 完整 CompanyProfileUpdateDTO | null |
| GET /company/preview | 当前企业，准入状态不限 | 无 | CompanyPublicProfileVO |
| GET /company/{id}/profile | 求职者 | 正整数企业 ID | CompanyPublicProfileVO |
| GET /company/{id}/jobs | 求职者 | 企业 ID、page、pageSize | PageResult<JobListVO> |

公开主页和岗位列表均要求目标企业准入状态 APPROVED。工商核验不替代企业准入，
工商重新待审不自动取消原有招聘权限。不存在、未通过准入统一返回“企业不存在或暂不可查看”。
企业本人通过 preview 预览；企业账号及管理员不使用求职者主页接口。
旧企业资料不完整仍可展示，不使用原型示例值填充真实资料。

## 调用链与职责

Controller 读取 ThreadLocal 中的当前用户 ID，PUT 使用 Valid 校验 DTO，
只负责路由、请求接收和 Result 包装。身份和企业归属校验始终在 Service 中执行。

CompanyServiceImpl：

- getMyProfile 使用 requireCompany 获取自己的企业，返回管理字段和缺失字段。
- previewProfile 使用 requireCompany 确定企业，不接收可供切换归属的企业 ID。
- getPublicProfile 先 requireJobSeeker，再 requireVisibleCompany 检查目标企业准入。
- listActiveJobs 执行相同访问检查后再开启 PageHelper 分页，紧接着查询岗位。
- populateDisplay 明确组装 CompanyDisplayVO 的字段，管理和公开视图复用同一计算规则。
  管理视图另外补充准入状态、内部版本、缺失字段和创建时间。
- buildPublicProfile 直接新建 CompanyPublicProfileVO，并按已校验的目标企业 ID 查询照片，
  经 CompanyPhotoConverter 转为照片 VO，不把管理 VO 或实体转换成公开响应。

CompanyProfileMapper 负责福利查询、COUNT 照片、EXISTS 有效已核验营业执照，
以及带版本条件的主表更新、福利替换、工商快照插入。
JobMapper.findActiveJobsByCompany 负责目标企业的招聘中岗位查询，不加载企业招聘统计。

## 主页字段边界

公开响应和预览响应包括 CompanyDisplayVO 中的展示字段、updateTime 和 photos。
不返回 userId、企业准入 status、profileVersion、registrationVersion、
verifiedRegistrationVersion、missingRequiredFields、审核意见、原件路径或存储 key。
registrationReviewStatus 是已约定的展示状态；registrationVerified 才表示当前版本核验通过。

派生标记：

- basicProfileComplete：七个业务必填字段齐全；不是直接复制企业准入结果。
- registrationVerified：当前状态 VERIFIED，并且核验通过版本等于当前工商版本。
- businessLicenseVerified：当前企业存在未移除且审核通过的 BUSINESS_LICENSE。
- photoCount：数据库 COUNT 的真实结果，暂无照片时为 0。
- benefits：按 sortOrder、id 排序，无数据为 []。
- photos：List<CompanyPhotoVO>，按 sortOrder、id 升序，无数据为 []；
  字段为 id、originalFilename、contentType、fileSize、sortOrder、createTime、previewUrl。
  previewUrl 为 /company/photos/{id}/file，读取时仍需登录和权限检查。
  只返回照片元数据，不加载文件字节；同一响应 photoCount 等于 photos 的长度。

以上读取使用只读、REPEATABLE_READ 事务，让主表、福利、照片和核验状态来自同一快照。

## 岗位分页

示例：GET /company/201/jobs?page=1&pageSize=2

- page 默认 1，最小 1；pageSize 默认 10，范围 1～100。
- 复用项目 JobValidationConstants 的分页边界。
- SQL 同时限制 company_id 和 JobStatus.ACTIVE.code，不能先分页再在 Java 中过滤状态。
- 排序为 update_time DESC、id DESC，时间相同时依然确定顺序。
- data.total 是符合企业及状态条件的总数；data.items 是本页。
- 无岗位返回 total=0、items=[]；超出末页保留 total，items=[]。
- PageHelper 在权限查询之后设置，finally 清理，避免分页错误作用于权限 SQL。
- 返回 JobListVO，不包含投递数、待处理数等企业内部统计。

## 完整保存、版本与事务

PUT 的字段及空值约定见 company-profile-field-contract.md。
客户端只提交可编辑资料和预期 profileVersion，保存成功后重新 GET 最新版本。

Service 先比较旧对象与预期版本；Mapper UPDATE 再以 id、user_id、profile_version 作为条件，
更新成功后 profile_version 自增。检查影响行数为 1，避免检查后发生并发覆盖。
只在 Java 中先查版本再无条件 UPDATE，不能防止并发。

福利最多十二项，删除旧福利后按请求顺序批量插入；[] 只删除。
SQL 明确更新所有可编辑字段，可选值为 null 时真正清空。
主表更新不修改 user_id、准入 status、logo 和历史核验通过版本。

工商比较仅包含 fullName、foundedDate、legalRepresentative、registeredCapital、
businessStatus、creditCode、registeredAddress、businessScope。
旧字符串按与 DTO 相同的规则去首尾空白、空白变 null 后比较。
真实变化才增加工商版本、置 PENDING，并保存该版本的八字段 JSON 快照。
普通更新在 SQL 中保留数据库当前工商审核状态，避免覆盖并发管理员审核结果。

主表、福利、工商快照位于同一保存事务；异常向外传播，所有写入回滚。
私有辅助方法加入的是外层事务，不能依赖自身私有方法上的 Transactional 创建事务。

## 后续阶段与旧入口收尾

本文件记录资料与主页阶段；后续照片管理和资质私有文件管理已完成，
接口与文件事务规则见 company-files-api-guide.md，回归见 company-files-regression-2026-10-20.md。
公开主页和本人预览的照片列表已于 2026-10-21 接入，复用现有文件读取入口。
管理端工商审核与企业审核历史已实现，见 company-registration-review-api-guide.md；
材料审核与管理员原件读取已接入，见 company-qualification-review-api-guide.md。
旧阶段测试中的已核验资料与材料元数据由隔离夹具构造，实际工商审批验收见新阶段记录。
2026-10-21 已停用旧 POST /company、DELETE /company：登录后统一 HTTP 410、业务码 500，
不解析旧实体、不执行写入；未登录仍 HTTP 401。创建统一走 POST /company/register，
删除暂不提供替代入口，避免破坏岗位、投递及审核历史。旧内部无版本更新和物理删除链路已移除。
当前联调入口见 company-backend-integration-guide.md；本阶段不实现企业注销生命周期。
