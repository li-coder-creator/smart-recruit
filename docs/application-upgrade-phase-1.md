# 我的投递升级：阶段一业务基线与实现计划

日期：2026-10-25。状态：阶段一设计交付，以下新增接口、对象、表结构均待实现。
本轮只有只读数据盘点和文档 / SQL 草案；没有修改 Java 业务代码、执行迁移或接口回归。

以上为阶段一交付记录。后续阶段二实现与当前迁移 / 验证状态见
[阶段二说明](application-upgrade-phase-2.md)，不要将阶段一的“待实现”表述作为当前代码状态。
后续真实事件写入的实现与待验收项见 [阶段三说明](application-upgrade-phase-3.md)。
面试排期和终态联动的实现与回归见 [阶段四说明](application-upgrade-phase-4.md)。

- [实际数据盘点](application-upgrade-data-audit-2026-10-25.md)
- [DTO / VO / 实体字段契约](application-upgrade-field-contract.md)
- [数据库草案](sql/2026-10-25-application-upgrade.draft.sql)：DDL 整段处于注释中，不能当正式迁移执行。

## 1. 业务目标和范围

对齐 applications-prototype 的求职者“我的投递”：状态概览、状态标签、岗位 / 企业搜索、
7 天 / 30 天 / 全部时间、分页、详情抽屉、真实进展、近期面试和日历入口。
企业侧补齐排期管理，使求职者看到的时间、地址和链接来自真实安排。

受影响模块：投递查询、投递创建 / 状态处理、两张新表、求职者页面及企业投递详情中的排期入口。
现有岗位推荐文档属于独立任务，不在本轮修改。

本轮按当前关联资料展示，不引入投递时的岗位 / 简历快照。
不新增录用概率、自动录用、短信 / 邮件通知、求职者接受邀请、会议平台集成、出席签到、面试评价。
薪资按现有 MONTH / DAY / YEAR / negotiable 展示；“15 薪”和独立“校招”类型另立字段任务，不伪造。

## 2. 原型到数据来源的映射

| 页面内容 | 数据来源 / 行为 |
| --- | --- |
| 顶部和全部状态标签数字 | 新 overview 聚合接口 |
| 当前条件下记录数和页码 | 新 page 接口的 data.total / data.items |
| 企业 Logo、城市、薪资、用工类型 | 当前 company / job，缺失返回 null，前端显示中性占位 |
| 所投简历名称 | 当前仍属于投递用户的 resume，缺失明确标记 |
| 当前进度节点 | job_application.status，不能作为全部历史证据 |
| 抽屉时间线 | application_event，仅展示真实记录 |
| 面试时间、方式和地址 / 链接 | application_interview，由企业设置 |
| 距离面试多久 | 根据服务器时间和真实排期在前端计算，不保存“还有 2 天” |
| 添加日历 | 前端读取授权排期后生成，不新增后端下载接口 |
| 检查我的简历 | 前端进入已有简历管理，不新增接口 |
| 投递编号 | 原投递 ID；不复制原型固定 SR202610 编号 |

## 3. 查询与统计口径

### 3.1 当前用户边界

所有 mine 接口必须先 requireJobSeeker，再按登录 userId 查询；不接收用于切换归属的 userId。
记录缺失与非本人资源统一返回“投递不存在或无权访问”。管理员和企业不能访问 mine 接口。

历史投递不要求岗位 ACTIVE 或企业 APPROVED。岗位结束、企业状态变化、简历删除只影响关联展示和跳转。
列表以 job_application 为主表，展示关联使用 LEFT JOIN；概览直接聚合原投递表，不关联简历。

### 3.2 概览与标签

顶部与所有标签均取本人全部历史投递的当前状态，不受关键词、日期、选中状态或分页影响。
概览返回六个计数，其中 rejectedCount 支撑“未通过”标签；顶部可按原型只显示前五项。
totalCount = pendingCount + screeningCount + interviewCount + hiredCount + rejectedCount。
没有记录时所有计数为 0。非法状态不归入任意已知状态，返回明确数据异常并记录日志。

interviewCount 表示当前 INTERVIEW 数量，不用 has_interviewed 统计累计面试。
筛选后的列表 total 可以小于顶部累计数量；页面明确区分“累计投递”和“当前筛选”。

### 3.3 分页、搜索和日期

- 默认 page=1、pageSize=10；page>=1、1<=pageSize<=100；超出末页 total 保留，items=[]。
- status 不传表示全部；传入只接受现有 0～4，不新增“全部=-1”。
- keyword 去首尾空白，空白等同不筛选；最长 100 字符；只搜索岗位标题和企业名称，二者 OR。
- LIKE 的百分号、下划线和转义符按字面字符处理，使用绑定参数，不直接拼入 SQL。
- dateRange 只接受 LAST_7_DAYS、LAST_30_DAYS、ALL，默认 LAST_30_DAYS。
- Asia/Shanghai 自然日：最近 7 天从当天往前 6 天的 00:00 起，最近 30 天往前 29 天；含今天。
- 同一次请求只取一次服务器 now，create_time >= 起点 AND create_time <= now；ALL 只取消下限。
- 后端使用统一 Asia/Shanghai 的 Clock / ZoneId；部署时核对 JDBC、数据库会话与服务配置，不能只加 timeZone 响应字段。
- 日期过滤使用 create_time，不用 update_time；按 create_time DESC、id DESC 排序。
- 获取权限后才设置 PageHelper，紧接业务列表查询，finally 清理线程分页上下文。

例如 2026-10-25：7 天起点 2026-10-19，30 天起点 2026-09-26。
当前数据里用户 36 有历史投递，但默认最近 30 天为空；前端应显示“当前筛选无记录”，不能说从未投递。

### 3.4 展示与历史兼容

沿用状态码：0 待处理、1 筛选中、2 面试、3 录用、4 REJECTED。
求职者专用响应把 4 的 statusText 显示为“未通过”，不修改企业端枚举文案和存储。
缺少岗位 / 企业 / 简历时返回 null 及可用性标记，不从原型补品牌、薪资或简历名称。
resumeAvailable 表示当前简历存在且仍属于投递用户，不代表有投递快照。
jobAvailable 表示关联岗位存在；jobViewable 还要求该岗位当前可供求职者查看。
已结束岗位可展示历史记录，jobViewable=false。现阶段求职者跳转采用 ACTIVE 且企业准入 APPROVED 的条件。

## 4. 状态与事件基线

沿用现有 canTransitionTo：允许前进和跳级；同状态、回退、HIRED / REJECTED 终态变更拒绝。
详情不能把未发生的筛选或面试显示为已经完成；直接录用只显示投递成功和直接录用事件。

新增 history_recorded_from_start：旧数据默认 false，新创建投递只有在初始事件同事务落库时显式设 true。
VO historyComplete 直接表达这个标记，不用事件条数或 update_time 推断。
旧记录不批量补造事件，不修改 status、has_interviewed、create_time 或 update_time。
旧记录未来发生的真实变更正常追加，但 historyComplete 继续为 false。
旧详情 events=[] 时显示“历史流转未记录”；投递时间仍来自主记录，不能伪装成所有历史阶段时间。

事件类型：APPLIED、STATUS_CHANGED、INTERVIEW_SCHEDULED、INTERVIEW_RESCHEDULED、INTERVIEW_CANCELLED。
状态事件记录真实 fromStatus / toStatus；面试事件记录场次与白名单时间变更信息，不能靠读取可变排期还原旧时间。
事件只追加，不提供用户修改 / 删除接口；公开 VO 不返回操作者账号 ID、内部备注或完整 JSON 实体。

## 5. 面试排期基线

### 5.1 操作身份和流程

企业账号必须通过准入，投递关联岗位必须属于自己；管理员不作为排期操作身份。
求职者只能读取本人安排，不能自行改时间、取消企业安排或操作其它投递。
企业先沟通，再把投递推进到 INTERVIEW，随后创建安排；INTERVIEW 允许暂时没有排期。
首版不引入候选人确认状态，也不把安排成功称为“已参加面试”。

岗位暂停 / 结束不自动删除或拒绝既有投递。对既有 INTERVIEW 投递的排期权限沿用现有企业处理规则，
不额外要求岗位仍 ACTIVE；新投递仍按原有岗位状态规则处理。

### 5.2 时间、方式和有效安排

- 新建、改约要求投递当前为 INTERVIEW；时间统一按 Asia/Shanghai。
- 新开始时间必须严格晚于持锁后重新取得的 now，结束时间严格晚于开始时间。
- 允许跨自然日；首版不设“最多几天后”或面试时长等未经业务确认的限制。
- ONLINE 必填平台和 http/https 会议链接，清空线下地址；OFFLINE 必填地址，清空平台和链接。
- 同一投递最多一场 status=SCHEDULED 且 endTime>now 的有效安排，包含正在进行的场次。
- 该限制通过统一锁定投递主记录后检查并写入实现，不靠仅按时间判断的普通唯一索引。
- 已过期或取消场次保留；无有效安排时允许创建新场次，首版不自动命名为“一面 / 二面”。

排期存储状态只有 SCHEDULED、CANCELLED；时间派生状态是 UPCOMING、ONGOING、EXPIRED、CANCELLED。
SCHEDULED 的时间过去不自动写成 COMPLETED；“过期”不等于确实参加或完成面试。
不提供针对其它企业的全局候选人日历占用功能。

### 5.3 改约、取消和终态

改约仅允许尚未开始的 SCHEDULED 场次，完整提交可编辑字段及当前 version；成功 version+1。
手动取消允许未结束的有效场次，必须提交 version 和取消原因；不物理删除，不回退投递状态。
取消后可以重新创建未来场次；已取消 / 已过期场次不允许改约或重复取消。

投递转为 HIRED / REJECTED 时，同事务取消该投递所有未结束的有效安排，version+1，
cancelSource=APPLICATION_STATUS，原因由服务端填写“投递流程已结束，安排失效”。
本阶段明确包括正在进行的场次，旧时间和历史事件保留；取消不宣称候选人没有参加过。
已过期场次保持原记录；不会因为时间结束自行录用或拒绝投递。

### 5.4 近期面试

只查询本人、投递当前 INTERVIEW、场次 SCHEDULED、endTime>now，按 startTime ASC、id ASC 取第一条。
正在进行的场次可优先因较早开始时间返回；前端显示“进行中”，不显示负数倒计时。
没有安排时 data.interview=null，仍返回服务器时间和 timeZone。
卡片不受投递列表的关键词、日期或状态筛选影响。

## 6. 接口草案（8 个新增、2 个增强）

| 请求 | 权限 | 返回 data | 用途 |
| --- | --- | --- | --- |
| GET /application/mine/page | 求职者 | PageResult<MyApplicationListVO> | 分页筛选 |
| GET /application/mine/overview | 求职者 | MyApplicationOverviewVO | 全历史当前状态计数 |
| GET /application/mine/{id} | 本人投递求职者 | MyApplicationDetailVO | 专用详情，包含事件和排期 |
| GET /application/mine/interviews/next | 求职者 | MyNextInterviewVO | 近期面试，允许 interview=null |
| GET /application/{id}/interviews | 所属已审核企业 | ApplicationInterviewListVO | 企业刷新后读取并管理排期 |
| POST /application/{id}/interviews | 所属已审核企业 | ApplicationInterviewVO | 新建，version=0 |
| PUT /application/{id}/interviews/{interviewId} | 所属已审核企业 | ApplicationInterviewVO | 改约，含新 version |
| PUT /application/{id}/interviews/{interviewId}/cancel | 所属已审核企业 | null | 取消 |
| POST /application | 原权限 | null | 增强：新投递、完整性标记、初始事件原子提交 |
| PUT /application/{id}/status | 原权限 | null | 增强：状态、历史证据、事件、有效安排失效原子提交 |

旧 GET /application/mine、/my、/{id} 返回契约保留；现有企业列表和详情不依赖新求职者 VO。
新求职者页面明确切换到新接口，待调用方全部迁移后再单独评估旧接口退役。
企业新增排期组件使用 GET /application/{id}/interviews，不需要扩大旧 ApplicationVO。

Result 成功 code=200，普通业务错误 HTTP 200 / code=500 / data=null，未登录 HTTP 401。
版本冲突是明确的业务拒绝，不自动刷新版本后覆盖重试；角色、归属和时间校验由服务端执行。

## 7. 事务、锁和调用链

Controller：@Valid 接收专用 DTO、ThreadLocal 取 userId、调用 Service、包装 Result。
Service：身份和归属、时间范围、白名单、排期约束、字段归一化、事务及 VO 组装。
Mapper：只读必要字段、聚合、带条件更新、事件追加；不承担角色判断。

读：page 的 count / items、详情的主表 / 事件 / 排期使用同一只读 REPEATABLE_READ 事务。
列表不逐条加载历史或面试、不逐条查询企业；概览直接聚合 job_application。
独立 HTTP 请求之间不承诺同一快照；刷新页面时重新读取概览、列表和近期面试。

写：apply 补 @Options 主键回填；新标记和初始事件随原投递插入一起回滚。
状态推进、排期新建 / 改约 / 取消均统一按“投递行 → 排期行 → 追加事件”顺序处理。
先完成身份 / 归属定位，再 FOR UPDATE 读取投递当前状态并复核，避免用普通快照旧状态决策。
保留原状态条件、排期 version 条件以及影响行数=1 检查；持锁后重新取 now 校验时间。
BusinessException / SQL 异常必须向事务代理外传播，不捕获后返回成功。

## 8. 数据库增量

- job_application 新增 history_recorded_from_start NOT NULL DEFAULT 0。
- 保留状态码、has_interviewed、已有唯一约束和岗位删除 RESTRICT 外键。
- 新增 application_interview，再创建引用它的 application_event。
- 新增 (user_id, create_time, id) 与 (user_id, status, create_time, id) 普通索引。
- 新表 ID 和关联 ID 使用与现存表一致的有符号 BIGINT，Engine=InnoDB。
- 不依赖数据库 CHECK 完成枚举、时间和状态机校验；不对账号建立会级联删除历史的外键。
- 不执行旧历史补写、不从已修改的当前资料回填“投递时快照”。

正式执行前需要重新核对结构和数据，备份并停止投递写入，提取草案形成正式迁移。
DDL 不作为可由业务事务整体回滚的操作；失败后按实际已完成步骤恢复，禁止直接重跑或清表。
新表 / 标记先作为兼容性增量部署，原应用未升级时新增记录仍默认不完整；新写链路上线后才显式设 true。

## 9. 后续开发顺序与阶段验收

1. 阶段二：正式迁移准备、QueryDTO / 三个查询 VO、列表 / 概览 / 基础详情，先形成查询闭环。
2. 阶段三：事件实体、写入与真实时间线；验证失败回滚、同原状态竞争、跳级和旧历史不完整。
3. 阶段四：面试读写、版本、锁和终态联动；验证两个企业 / 两个求职者隔离。
4. 阶段五：统一 Postman MCP 回归，持久加入 SmartRecruit API - Optimized Regression。
5. 阶段六：企业排期入口、求职者页面、抽屉、日历、浏览器联调。
6. 阶段七：全链路验收、README、提交说明；不将只读盘点宣称为功能已实现。

最新状态：阶段五HTTP完整流程与查询边界验收已执行，2026-10-31完成收尾，见 [第五阶段报告](application-upgrade-phase-5-regression-2026-10-28.md)。
README的本期后端说明已同步；阶段六、七的前端实现及浏览器业务联调仍是后续工作。

阶段二首批对象为 MyApplicationQueryDTO、MyApplicationListVO、MyApplicationOverviewVO、MyApplicationDetailVO；
详情中的事件 / 排期类型按字段契约设计，在对应阶段接入真实查询。
新表空时返回空集合、historyComplete=false；不能用演示数组临时填充。

## 10. 回归设计（尚未执行）

| 范围 | 必测条件 |
| --- | --- |
| 查询 | 五种状态、无数据、关键词转义、空白、超长、7 / 30 天起点与 now、同时间排序、超末页 |
| 权限 | 未登录、其它角色、其它求职者、其它企业、未准入企业、interviewId 不属于路径投递 |
| 历史 | 原简历删除、岗位结束、展示缺失、旧 historyComplete=false、直达录用无假面试 |
| 原子性 | 投递后事件插入失败、状态后事件失败、取消排期后事件失败，相关写入全部回滚 |
| 排期 | ONLINE / OFFLINE 互斥字段、非法协议、过去时间、相等 / 反转时间、跨日、无排期返回 null |
| 并发 | 同投递同时创建排期、同时改约、改约与取消、创建 / 改约与录用 / 拒绝交错 |
| 兼容 | 旧列表 / 详情和企业状态机、has_interviewed 的 true / false / null 口径 |

固定 Asia/Shanghai 时间基准构造隔离数据；请求前核对身份、状态和预期。
不对开发库制造并发、故障或删除数据；回归报告明确请求、响应、结果和工具限制。

## 11. 学习与面试交付

需要能解释：聚合与分页、LEFT JOIN 保留历史、状态机与事件事实、主键回填、事务原子性、
RR 普通读取和 FOR UPDATE 当前读取、乐观版本与行锁的职责、为什么到期不能推断出席。
只有接口和回归完成后，才可在简历描述相关实现；阶段一仅可描述需求分析和契约设计。
