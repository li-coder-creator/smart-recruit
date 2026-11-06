# 普通岗位列表卡片字段契约

日期：2026-11-01。业务目标：为原型“全部岗位”卡片提供企业、技能、简介及时间字段。

## 接口与范围

| 场景 | 接口 | 分页参数 | 权限 |
| --- | --- | --- | --- |
| 普通岗位列表 | GET /job | page、size、keyword、sort | 当前登录求职者 |
| 企业公开主页在招岗位 | GET /company/{id}/jobs | page、pageSize | 求职者；目标企业审核通过 |
| 企业岗位管理 | GET /job/company | page、size、keyword、filter | 当前企业，仅本企业岗位 |

响应仍为 `Result<PageResult<JobListVO>>`；企业管理列表使用继承的 `CompanyJobListVO`，保留投递数、待处理数和当前面试数。分页数据从 `data.items` 读取，数量从 `data.total` 读取。

## 新增字段

| 字段 | Java 类型 | 说明与空值 |
| --- | --- | --- |
| companyId | Long | 岗位所属企业 ID |
| companyName | String | 企业当前展示名称；关联缺失时 null |
| companyLogo | String | 企业 Logo 地址；未设置时 null，前端展示默认头像 |
| summary | String | 岗位简介，允许 null |
| benefits | String | 福利展示文本，允许 null |
| requiredSkills | String | 必备能力展示文本，允许 null |
| preferredSkills | String | 加分能力展示文本，允许 null |
| directionCode | JobDirection | BACKEND / FRONTEND；历史未填写时 null |
| requiredAbilitiesSource | RequiredAbilitiesSource | LEGACY_TEXT / STRUCTURED，说明展示文本来源 |
| requiredAbilitiesVersion | Long | 能力内容版本号 |
| publishedAt | LocalDateTime | 实际发布时间；历史未记录或草稿时可为 null |
| deadline | LocalDate | 截止日期；未设置时 null |

保留原有 `id、title、category、team、employment、status、city、experience、education、salaryMin、salaryMax、salaryUnit、negotiable、updateTime`。

## 展示规则与边界

- 技能字段保持数据库原文。正常结构化保存时 `requiredSkills` 是各项 rawText 的换行投影；旧文本原样展示。列表没有额外解析技能标签，前端须保留复杂自然语言的完整含义。结构化证据以岗位详情的能力列表为准。
- `benefits`、`preferredSkills` 同样是字符串；null 表示没有可展示的值，后端不生成虚构内容。
- 薪资沿用数值、`MONTH / YEAR / DAY` 单位和 `negotiable`；前端按单位展示，面议优先，不统一拼接为“k/月”。
- `publishedAt` 返回 ISO 本地时间，例如 `2026-10-01T10:00:00`；`deadline` 返回 `yyyy-MM-dd`。前端使用项目约定的中国本地业务时间语义。
- 普通列表默认 `sort=UPDATED_DESC`，按 `update_time DESC, id DESC` 排序；`sort=SALARY_DESC` 按有效月薪上限降序，其他薪资记录置后，各组同值按更新时间和ID降序。规则详见[查询契约](job-browse-query-contract.md)。
- 普通列表关键词已覆盖标题、类别、城市、部门、企业名、必备能力和加分能力；结构化必备能力按能力子表查询，旧文本按 requiredSkills 查询。
- 普通列表现在同时限制岗位为 ACTIVE、企业为 APPROVED。普通浏览仍保留已投递及过期岗位，与推荐接口的默认排除开关保持各自业务语义。

## 实现与验收

Controller 接收参数，Service 处理权限、分页和企业统计，Mapper 明确选择公开卡片字段。普通列表通过 `JOIN company` 并限制企业审核状态；企业主页及管理投影沿用 `LEFT JOIN company`。企业主键唯一，因此关联后一个岗位仍对应一行。结构化能力搜索使用 EXISTS 子查询，避免一对多关联放大 total；没有逐岗位查询企业的 N+1 问题。

不需要数据库迁移。企业公开主页与企业管理复用了 VO，同步补齐三处投影，防止新增字段仅在一个入口有值。

验收覆盖：新旧字段、真实空值、技能原文、结构化多条能力的一岗一行、薪资单位与面议、普通及企业分页、关键词搜索、草稿状态筛选、角色权限，以及源库和隔离业务数据前后一致。测试结果见本阶段回归报告。

面试考点：VO 与实体的职责、SQL 别名到驼峰字段的映射、多对一与一对多关联的分页区别、LEFT JOIN 的空值语义，以及公开投影如何避免企业内部审核字段泄露。
