# 企业资料字段契约（模型阶段）

2026-10-16，基于已落库的企业资料结构，补齐 DTO、校验常量、管理 VO 和公开 VO。
本阶段没有调整 Controller / Service / Mapper，没有执行接口测试，也没有提交 Git。
当前旧接口仍接收/返回 Company；本契约在后续读写链路接入后生效。

> 以上为 2026-10-16 模型阶段记录。当前 GET/PUT /company 已分别接入 CompanyProfileVO、
> CompanyProfileUpdateDTO，Service 保存及派生字段计算已实现；旧 POST/DELETE /company 已停用。
> 下方字段表继续作为保存契约；当前接口入口与验收记录见 company-backend-integration-guide.md。

## 对象与职责

| 对象 | 用途 |
| --- | --- |
| CompanyProfileUpdateDTO | 完整资料保存请求，只含可编辑字段及客户端预期版本 |
| CompanyValidationConstants | 与数据库容量和业务约定一致的具名校验上限 |
| CompanyDisplayVO | 管理和公开响应共用的公开展示白名单 |
| CompanyProfileVO | 企业本人管理响应，增加准入状态、版本、待完善字段和系统时间 |
| CompanyPublicProfileVO | 求职者主页及本人预览的公开响应，增加更新时间和办公照片列表 |

DTO 没有 applyTo 或全实体复制方法：字段保存、版本递增、工商变更判断及事务由下一阶段 Service 实现。
公共 VO 不继承管理 VO；不要在共同父类加入审核意见、归属账号或内部文件引用。
VO 不加保存请求的必填校验，因为旧企业资料可能尚未完善。

## 可编辑字段表

下表全部为可公开的资料字段，管理和公开 VO 均能承接。
“触发重审”指归一化后与数据库旧值确有不同，实际判断尚待 Service 接入。
枚举字段 JSON 使用英文枚举名称，不使用中文 description；数据库使用相同代码。

| 字段 | Java 类型 | 保存必填 | 校验 / 上限 | 触发工商重审 | 数据库存储 |
| --- | --- | --- | --- | --- | --- |
| `name`（企业简称 / 品牌） | `String` | 是 | 100字符 | 否 | `company.name` |
| `fullName`（公司全称） | `String` | 是 | 200字符 | 是 | `company.full_name` |
| `industry`（行业） | `CompanyIndustry` | 是 | 仅接受已定义枚举 | 否 | `company.industry` |
| `employeeSize`（人员规模） | `CompanyEmployeeSize` | 是 | 仅接受已定义枚举 | 否 | `company.employee_size` |
| `financingStage`（融资阶段） | `CompanyFinancingStage` | 否 | 仅接受已定义枚举 | 否 | `company.financing_stage` |
| `companyNature`（企业性质） | `CompanyNature` | 否 | 仅接受已定义枚举 | 否 | `company.company_nature` |
| `website`（企业官网） | `String` | 否 | 255字符；URL格式及 HTTP/HTTPS 协议 | 否 | `company.website` |
| `city`（城市 / 区域） | `String` | 是 | 50字符 | 否 | `company.city` |
| `address`（实际办公地址） | `String` | 是 | 255字符 | 否 | `company.address` |
| `commuteDescription`（交通与到访说明） | `String` | 否 | 500字符 | 否 | `company.commute_description` |
| `description`（企业简介） | `String` | 是 | 2000字符 | 否 | `company.description` |
| `foundedDate`（成立日期） | `LocalDate` | 否 | 不能晚于当天 | 是 | `company.founded_date` |
| `legalRepresentative`（法定代表人） | `String` | 否 | 100字符 | 是 | `company.legal_representative` |
| `registeredCapital`（注册资本及币种） | `String` | 否 | 100字符 | 是 | `company.registered_capital` |
| `businessStatus`（经营状态） | `CompanyBusinessStatus` | 否 | 仅接受已定义枚举 | 是 | `company.business_status` |
| `creditCode`（统一社会信用代码） | `String` | 否 | 18字符；大写字母或数字 | 是 | `company.credit_code` |
| `registeredAddress`（注册地址） | `String` | 否 | 255字符 | 是 | `company.registered_address` |
| `businessScope`（经营范围） | `String` | 否 | 2000字符 | 是 | `company.business_scope` |
| `workStartTime`（上班时间） | `LocalTime` | 否 | 两个时间同时填写或同时留空，允许跨日 | 否 | `company.work_start_time` |
| `workEndTime`（下班时间） | `LocalTime` | 否 | 两个时间同时填写或同时留空，允许跨日 | 否 | `company.work_end_time` |
| `restPolicy`（休息制度） | `CompanyRestPolicy` | 否 | 仅接受已定义枚举 | 否 | `company.rest_policy` |
| `overtimePolicy`（加班情况） | `CompanyOvertimePolicy` | 否 | 仅接受已定义枚举 | 否 | `company.overtime_policy` |
| `benefits`（企业福利） | `List<CompanyBenefitCode>` | 是 | 非 null；最多12项；元素非 null、不能重复；[] 清空 | 否 | `company_benefit.benefit_code` |
| `workNotes`（工作制度与福利说明） | `String` | 否 | 2000字符 | 否 | `company.work_notes` |
| `productName`（产品名称） | `String` | 否 | 200字符 | 否 | `company.product_name` |
| `productDescription`（产品介绍） | `String` | 否 | 2000字符 | 否 | `company.product_description` |
| `teamName`（团队或管理者名称） | `String` | 否 | 200字符 | 否 | `company.team_name` |
| `teamRole`（介绍类型或职务） | `String` | 否 | 100字符 | 否 | `company.team_role` |
| `teamBio`（团队或管理者简介） | `String` | 否 | 2000字符 | 否 | `company.team_bio` |

### 预期版本

| 字段 | 类型 | 请求规则 | 保存行为 |
| --- | --- | --- | --- |
| profileVersion | Long | 必填，最小 0 | 作为 WHERE 条件匹配旧版本，服务端自行递增，不直接用它覆盖新版本 |

DTO 不接受 id、userId、status、registrationVersion、registrationReviewStatus、verifiedRegistrationVersion、
审核人、审核时间、logo、存储引用。logo 本阶段只复用读取，Service 保存其他资料时必须保留原值。

DTO 字段白名单不等于未知 JSON 字段一定被 HTTP 拒绝：项目 Jackson 对未知字段的策略由框架配置决定。
真正的保护来自后续 Service 只复制允许的业务字段，不接收客户端审核结果或归属信息。

## 空值、长度与组合约定

1. Jackson 调用 DTO 的 String setter 时先执行 strip，可选空白字符串变为 null；必填字段变为 null 后由 NotBlank 拒绝。
2. DTO 是完整表单：可选字段省略或传 null 都表示清空。前端必须提交整份可编辑表单，不能拿它当局部更新。
3. benefits 省略或传 null 拒绝，空数组表示清空；不自动吞掉重复福利。
4. DTO 没有给福利列表设置默认空数组，避免漏传被当作主动清空。
5. workStartTime、workEndTime 同时为空或同时提供；不比较先后大小，以支持夜班。
6. productDescription 非空要求 productName；teamRole 或 teamBio 非空要求 teamName。
   单独提供名称、暂不填写介绍允许保存。
7. website 使用 Hibernate Validator URL 校验和 HTTP/HTTPS 协议限制，不主动访问网站。
8. creditCode 空白转 null；非空仅做18位大写字母或数字格式筛查，不代表真实性或完整的工商核验。
9. LocalDate 使用 ISO 日期，如 2018-06-12；LocalTime 请求建议使用 09:30，具体序列化沿用项目 JavaTime 配置。
10. 本稿的长度按 Java Bean Validation Size 处理（Java String 长度），不声明为按 Unicode 可见字形计数。
11. 组合校验 getter 使用 JsonIgnore，只参与 Bean Validation，不成为额外请求/响应字段。
12. 当前福利上限12项与 CompanyBenefitCode 对应，未来扩展枚举时需同步审查该常量。

## 管理与公开响应字段

### CompanyDisplayVO：共同字段

- 可编辑字段表中的29项资料（不包含 profileVersion），以及 id、logo。
- basicProfileComplete：后续 Service 按新版必填项计算，不能依据旧准入已通过直接设为 true。
- registrationReviewStatus：当前工商核验状态。
- registrationVerified：仅当当前工商版本审核通过且版本一致时为 true。
- businessLicenseVerified：依据未移除且审核通过的当前营业执照材料计算。
- photoCount：查询得到的有效办公照片数，不作为主表冗余列。

### CompanyProfileVO：管理追加字段

| 字段 | 含义 |
| --- | --- |
| status | 企业准入状态，沿用 CompanyStatus |
| profileVersion | 客户端下一次保存需要提交的当前预期版本 |
| registrationVersion | 当前工商版本 |
| verifiedRegistrationVersion | 最近核验通过的版本，可能为空 |
| missingRequiredFields | 待完善的 DTO 字段名列表；完整时为空数组 |
| createTime / updateTime | 系统时间 |

### CompanyPublicProfileVO：公开追加字段

追加 updateTime 和 photos（List<CompanyPhotoVO>）。照片按 sortOrder、id 升序，
无记录返回 []；photoCount 与列表长度一致，previewUrl 读取仍需身份与权限校验。
没有账号ID、内部版本、准入管理字段、审核意见、资质原件或存储引用。
“公开”表示面向求职者的展示契约，本阶段不新增匿名访问白名单。

两个子类各自声明 updateTime，以保持共同父类只承载资料及安全展示结果；不为一个时间字段增加额外继承层次。

### 后续组装要求

- 枚举和普通资料允许 null，以兼容旧数据；不要用原型示例值补齐旧企业。
- 读取完成后，benefits 无记录应返回 []。
- derived 标记及 photoCount 未初始化为“成功”或“零”，避免把尚未查询误当作实际结果。
  接入 Service 时必须查询、计算并明确赋值；没有照片才能填 0。
- 本阶段没有添加照片、资质及审核记录列表 VO，不使用实体或 Map 作为占位。
  文件和审核阶段分别新增明确的 CompanyPhotoVO、CompanyQualificationVO 等类型。
- 字段源数据缺失与资料未完善应正常展示；用户提交新版完整保存时仍需满足 DTO 必填要求。

## 接入时的验收清单（本阶段未执行）

- 正常完整资料、旧资料展示，以及字段长度的边界。
- 必填缺失/全空白、可选空字符串、清空福利与遗漏福利。
- 官网格式、非法协议、信用代码格式、未来成立日期。
- 夜班时间、单边时间、产品介绍无名称、团队说明无名称。
- 福利重复、null元素、未知枚举值和负数版本。
- 管理响应能读取版本；公开响应没有内部版本、审核意见和文件引用。
- Service 按预期版本更新，冲突不覆盖；主表与福利更新失败一起回滚。
- 工商实际变更才进入待审，普通资料编辑不改变企业准入权限。
- 通过 Postman MCP 将接口回归加入 SmartRecruit API - Optimized Regression，不把模型编译当作接口验收。

## DTO / VO 阶段交付记录（2026-10-16）

仅新增 DTO、常量、三个 VO 和本文档；保留上一阶段数据库/实体/枚举的未提交改动。
未执行新迁移、未改旧接口签名与查询、未创建测试或执行接口测试。
编译结果：mvn -o -Dmaven.repo.local=C:\Users\李\.m2\repository -DskipTests compile 成功，111 个 Java 源文件。
git diff --check 通过；原 AdminController unchecked 提示仍存在。
下一阶段为企业资料读取与保存闭环。

## 资料读取与保存实现（2026-10-20）

- 沿用当前 Controller 的 GET /company 和 PUT /company，以及 CompanyService 中的两个新声明。
- CompanyServiceImpl 实现 getMyProfile、updateProfile；新增 CompanyProfileMapper，保留原 Mapper 的旧调用。
- GET 使用只读、可重复读事务组装 CompanyProfileVO，校验企业身份但不要求准入通过。
  福利按顺序返回，无记录为 []；照片数使用 COUNT，执照核验使用 EXISTS；
  工商核验要求当前状态为 VERIFIED 且核验版本等于当前工商版本。
- 完整度检查 name、fullName、industry、employeeSize、city、address、description；
  不把允许为空数组的福利或并发参数 profileVersion 算作缺失资料。
- PUT 是完整表单更新，可选字段 null 会清空；SQL 明确列出允许更新的字段，
  不修改 userId、status、logo、verifiedRegistrationVersion。
- 保存先检查预期版本，再使用企业 ID、用户 ID、profileVersion 作为 UPDATE 条件，
  成功时 profileVersion 自增，影响行数不是 1 则抛出业务异常。
- 福利采用删除后按请求顺序批量重建；空列表只删除。
- 八个工商字段规范化比较，实际变化才递增 registrationVersion、置为 PENDING、
  插入该工商版本的 JSON 快照；相同工商资料再次保存不新增审核记录。
  普通字段保存保留数据库中的工商审核状态，避免覆盖并发审核结果。
- 主表、福利、工商审核记录处于同一保存事务，任何异常向外传播触发回滚。
- PUT 成功仍返回 Result<Void>，调用方随后 GET 获取最新 profileVersion。
- 本阶段没有实现照片上传、资质管理、管理端审核或公开企业主页。
- 本阶段没有执行数据库迁移或接口测试；上述验收清单仍需通过 Postman MCP 回归。

## 主页展示与分页阶段（2026-10-20）

- 新增 GET /company/preview、GET /company/{id}/profile、GET /company/{id}/jobs。
- 预览由当前企业身份确定归属；两个求职者接口要求目标企业准入 APPROVED，仍需登录。
- 管理资料和公开展示复用共同字段组装；公开响应直接构建 CompanyPublicProfileVO，不包含管理版本或私有存储引用。
- 岗位分页参数为 page、pageSize，默认 1、10，pageSize 最大 100；SQL 限定企业和招聘中状态。
- 实现拆解见 company-profile-api-guide.md；持久回归见 Postman 主集合 02 Company 下的“企业资料与主页回归 2026-10-20”。
- 后续本阶段的实际回归结论以 company-profile-regression-2026-10-20.md 为准；上面的“未测试”仅记录上一交付阶段状态。

## 文件管理阶段（2026-10-20）

照片三项管理接口、资质四项管理接口及受保护图片读取已实现。
新增 CompanyPhotoVO、CompanyQualificationVO、CompanyQualificationUploadDTO，
资料已有 photoCount、businessLicenseVerified 会随文件操作按数据库状态计算。
文件规则见 company-files-api-guide.md，实际验收见 company-files-regression-2026-10-20.md。
该阶段管理端审核仍为后续任务；后续主页照片和工商审核接入见下文。

## 主页办公照片列表（2026-10-21）

- GET /company/preview、GET /company/{id}/profile 的 data 增加 photos。
- 复用 company_photo、CompanyFileMapper.listPhotos 和 CompanyPhotoVO，不新增数据库迁移或路由。
- 查询使用已通过入口校验的企业 ID，沿用外层只读、REPEATABLE_READ 事务。
- CompanyPhotoConverter 明确返回 id、originalFilename、contentType、fileSize、sortOrder、createTime、previewUrl，
  管理列表、上传响应与主页共用规则，不返回 storageKey、companyId 或文件字节。
- 无照片为 []；数量与 photoCount 一致；删除后下一次主页查询同步更新。
- 任意准入状态企业可预览自己；求职者只能查看准入 APPROVED 企业；未登录 HTTP401。

## 工商审核与企业反馈（2026-10-21）

- 新增管理员工商审核列表、详情、审批，以及企业自己的审核历史四个接口。
- CompanyRegistrationReviewDecisionDTO 只接受 decision、registrationVersion、reviewComment。
- 新增摘要、详情、企业历史与八字段快照 VO，字段和权限契约见 company-registration-review-api-guide.md。
- 当前版本核验、并发审批和主表/审核记录事务更新由专用 Service、Mapper 实现。
- 沿用现有表和审核枚举；CompanyReviewDecision 请求按名称解析，拒绝数字下标。
- 资质材料审核仍待后续阶段。
