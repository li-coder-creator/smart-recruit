# 我的投递升级：字段与对象契约

日期：2026-10-25；配套 [业务基线](application-upgrade-phase-1.md)。具体实现进度见下方说明。
已有 ApplicationVO 保留给旧接口，不继续增加求职者、企业和排期的所有字段。

实现进度：MyApplicationListVO、MyApplicationOverviewVO、MyApplicationDetailVO 及详情必需的
ApplicationEventVO、ApplicationInterviewVO、InterviewChangeVO 和五个响应依赖枚举已落地。
阶段二已补 MyApplicationQueryDTO、ApplicationDateRange、投递常量与统一 Clock，
以及 /application/mine/page、/overview、/{id} 的 Controller / Service / Mapper 查询链路。
阶段二详情已读取真实事件和排期。
正式迁移已于 2026-10-25 14:05 在本地 smart_recruit 执行：完整性标记、两个索引和两张表均已落地，
旧 7 条投递原八列逐行一致、原约束保留；详情的结构依赖已满足。
阶段三已补 Application.historyRecordedFromStart、ApplicationEvent、ApplicationEventMapper 和事务内
ApplicationEventRecorder；POST /application 与 PUT /application/{id}/status 已接入真实事件写入，
详情复用阶段二读取链路。具体事务、并发语义与待验收项见 [阶段三说明](application-upgrade-phase-3.md)。
阶段四已补排期相关 DTO、实体、VO、五个接口和终态取消联动，并完成隔离回归。
阶段三后续已执行隔离回归，135 次请求、230 条断言通过，范围与限制见
[阶段三回归记录](application-upgrade-phase-3-regression-2026-10-25.md)。
后续排期新回归 224 次请求/394 条断言，第三阶段复跑 135 次请求/230 条断言均通过，
详见 [阶段四验收记录](application-upgrade-phase-4-regression-2026-10-25.md)。
第五阶段在此契约上补充完整新投递流程与精确查询边界，最新交付状态见 [第五阶段验收记录](application-upgrade-phase-5-regression-2026-10-28.md)。

## 1. 通用约定

- JSON 均使用 Result：成功 code=200，普通业务拒绝 code=500 / data=null；未登录 HTTP 401。
- ID 为 Long，数据库有符号 BIGINT；状态码沿用 Integer 0～4。
- 新枚举传英文名称，拒绝未知名称与数字序号；不传中文说明。
- 所有日期时间网络格式 yyyy-MM-dd'T'HH:mm:ss[.SSS]，统一解释为 Asia/Shanghai。
- DTO / 实体时间为 LocalDateTime；响应元数据 timeZone 固定 Asia/Shanghai，不能交给浏览器按任意时区猜测。
- 当需要跨地区时区时另行扩展 OffsetDateTime 契约；本期不混用带 offset 与不带 offset 的输入。
- 计数 Long；Boolean 标记返回明确 true / false；列表无数据 []；关联资料缺失为 null。
- 字符串 strip 后空白归一为 null。必填字段在归一化后再次校验，长度使用具名常量。
- 请求不能写归属、审核结果、历史标记、取消来源、operator、状态或创建时间等服务端字段。

## 2. 请求 DTO

### 2.1 MyApplicationQueryDTO

用于 GET /application/mine/page。

| 字段 | 类型 | 默认 / 校验 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 默认 1，最小 1 | 页码 |
| pageSize | Integer | 默认 10，1～100 | 每页条数 |
| status | Integer | 可空；有值为 0～4 | 不传即全部 |
| keyword | String | 可空；最多 100 字符 | 岗位标题或企业名称，空白不筛选 |
| dateRange | ApplicationDateRange | 默认 LAST_30_DAYS | LAST_7_DAYS / LAST_30_DAYS / ALL |

不包含 userId、companyId、startTime、endTime 或自定义排序。
页码边界可复用项目已有分页常量，默认值、关键词上限和日期窗口用命名清晰的投递常量。
Service 取得一次 now 后计算起点，不能依赖调用者给时间范围下界绕过契约。

### 2.2 ApplicationInterviewCreateDTO

用于 POST /application/{id}/interviews。

| 字段 | 类型 | 校验 | 数据库存储 |
| --- | --- | --- | --- |
| startTime | LocalDateTime | 必填；持锁后检查 > now | start_time |
| endTime | LocalDateTime | 必填；> startTime | end_time |
| mode | InterviewMode | 必填 ONLINE / OFFLINE | mode |
| platform | String | ONLINE 必填；最多 50 字符 | platform |
| meetingUrl | String | ONLINE 必填；http / https；最多 1000 字符 | meeting_url |
| address | String | OFFLINE 必填；最多 255 字符 | address |
| candidateNote | String | 可空；最多 500 字符 | candidate_note |

ONLINE 时 address 必须为空，OFFLINE 时 platform / meetingUrl 必须为空；归一化后的冲突输入拒绝，
不能静默保留之前的会议链接或地址。字段名 candidateNote 表示求职者可见说明，禁止放内部评价。
路径 id 是投递 ID；body 不接受 applicationId、companyId、userId、status、version、operator。
面试链接只保存和返回授权用户，不由服务端访问或调用会议平台。

### 2.3 ApplicationInterviewUpdateDTO

用于 PUT /application/{id}/interviews/{interviewId}。
包含 CreateDTO 全部可编辑字段，并增加 version：Long、必填、>=0。
为完整替换，省略可选字段即清空；所有必填字段仍须提供。
Service 检查场次属于路径投递、尚未开始且有效、当前投递 INTERVIEW，并执行 version 条件更新。

### 2.4 ApplicationInterviewCancelDTO

用于 PUT /application/{id}/interviews/{interviewId}/cancel。

| 字段 | 类型 | 校验 |
| --- | --- | --- |
| version | Long | 必填、>=0 |
| reason | String | 必填非空白、最多 500 字符，求职者可见 |

状态已经取消、已过期、版本不匹配或投递不允许操作时明确拒绝，不伪装取消成功。
终态自动取消使用服务端原因和来源，不使用此 DTO 接收系统字段。

### 2.5 原有请求

ApplicationApplyDTO、ApplicationStatusUpdateDTO 的外部字段不增加；仍返回 Result<Void>。
history_recorded_from_start、事件类型、fromStatus、操作者和取消来源只在 Service 内决定。

## 3. 求职者响应 VO

### 3.1 MyApplicationListVO

用于 PageResult<MyApplicationListVO> 的 items。

| 字段 | 类型 | 来源 / 空值规则 |
| --- | --- | --- |
| id | Long | 投递 ID，必有 |
| jobId | Long | 原投递 job_id，保留 |
| companyId | Long | 当前关联 job.company_id；岗位缺失可空 |
| jobTitle | String | 当前岗位标题，可空 |
| companyName | String | 当前企业名称，可空 |
| companyLogo | String | 当前企业 logo，可空；不造品牌 URL |
| city | String | 当前岗位城市，可空 |
| employment | String | 当前用工类型，可空，不推断“校招” |
| salaryMin / salaryMax | Integer | 当前岗位薪资上下限，可空 |
| salaryUnit | JobSalaryUnit | MONTH / DAY / YEAR，可空；接口不自行换算金额 |
| negotiable | Boolean | 岗位存在时按当前值；缺失可空，不因缺失假定面议 |
| jobStatus | Integer | 当前岗位状态，可空 |
| jobAvailable | Boolean | 当前岗位是否存在 |
| jobViewable | Boolean | 当前是否可从求职者岗位入口查看 |
| companyAvailable | Boolean | 当前企业是否存在 |
| resumeId | Long | 原投递 resume_id，保留 |
| resumeTitle | String | 当前归属本求职者的简历名称，可空 |
| resumeAvailable | Boolean | 当前简历存在且 user_id 与投递求职者相同 |
| status | Integer | 当前投递状态 0～4 |
| statusText | String | 待处理 / 筛选中 / 面试 / 录用 / 未通过 |
| createTime / updateTime | LocalDateTime | 原投递创建 / 更新时间 |

不返回用户账号名、邮箱、电话、操作者 ID、会议链接或内部历史完整性列。
无简历、企业或岗位不能让该投递消失；关键词确实不匹配时才按搜索条件排除。
同一记录可 jobAvailable=true、jobViewable=false，例如岗位已结束。
这些字段均为当前关联展示，不承诺投递时快照。列表不装载完整 ResumeDetailVO 或岗位正文。

### 3.2 MyApplicationOverviewVO

用于 GET /application/mine/overview。

| 字段 | 类型 | 口径 |
| --- | --- | --- |
| totalCount | Long | 本人全部投递 |
| pendingCount | Long | 当前 PENDING |
| screeningCount | Long | 当前 SCREENING |
| interviewCount | Long | 当前 INTERVIEW |
| hiredCount | Long | 当前 HIRED |
| rejectedCount | Long | 当前 REJECTED，求职者显示未通过 |

所有计数不受列表筛选影响；不以 has_interviewed 计算 interviewCount。
无数据全部为 0，不用 null。此 VO 与企业招聘总览职责不同，不重复返回企业岗位计数。

### 3.3 MyApplicationDetailVO

复用 ListVO 的明确展示字段，新增：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| historyComplete | Boolean | history_recorded_from_start；旧 false，新完整记录 true |
| events | List<ApplicationEventVO> | 完整已记录事件，occurredAt DESC、id DESC，无记录 [] |
| interviews | List<ApplicationInterviewVO> | 本条投递场次，startTime DESC、id DESC，无记录 [] |
| serverTime | LocalDateTime | 本次响应统一的服务器 now |
| timeZone | String | Asia/Shanghai |

详情首版不截断历史并宣称完整；historyComplete=false 时，即使已有新事件也须提示旧段缺失。
未来记录量增加需要历史分页时，另行扩展接口，不静默删减响应。
详情不返回企业内部意见；企业排期管理另外走企业读取接口。

### 3.4 ApplicationEventVO

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| id | Long | 真实事件 ID |
| eventType | ApplicationEventType | APPLIED / STATUS_CHANGED / 三种面试变更 |
| fromStatus / toStatus | Integer | APPLIED 只有 to=0；状态变更两者均有；排期事件两者空 |
| actorType | ApplicationActorType | JOB_SEEKER / COMPANY / SYSTEM |
| occurredAt | LocalDateTime | 真实事件发生时间 |
| summary | String | 服务端根据类型与白名单信息生成，不复制内部日志 |
| interviewId | Long | 排期事件关联 ID，其它为空 |
| interviewChange | InterviewChangeVO | 排期事件白名单快照，其它为空 |

InterviewChangeVO 允许 previousStartTime、previousEndTime、startTime、endTime、previousMode、mode、cancelReason。
只返回这些已定义键；不直接把 detail_json 作为任意 Map 返回。
不在历史快照复制会议 URL、地址、内部说明或账号信息；本期不承诺追溯旧会议链接 / 地址内容。
APPLIED 和 STATUS_CHANGED 的 interviewId / interviewChange 必须为空。

### 3.5 ApplicationInterviewVO

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| id / applicationId | Long | 场次与投递 ID |
| startTime / endTime | LocalDateTime | 保存的安排时间 |
| mode | InterviewMode | ONLINE / OFFLINE |
| platform / meetingUrl | String | ONLINE 信息；OFFLINE 空 |
| address | String | OFFLINE 地址；ONLINE 空 |
| candidateNote | String | 求职者可见说明，可空 |
| status | InterviewStatus | SCHEDULED / CANCELLED，存储状态 |
| timingStatus | InterviewTimingStatus | UPCOMING / ONGOING / EXPIRED / CANCELLED，服务端按统一 now 派生 |
| version | Long | 当前版本，从 0 起 |
| cancelReason | String | 已取消时的可见原因，其它空 |
| cancelledAt | LocalDateTime | 取消时间，其它空 |
| createTime / updateTime | LocalDateTime | 排期记录时间 |

仅本人求职者 / 所属已审核企业可读；不放入列表卡片、公共岗位详情或企业公开主页。
不返回 createdBy、updatedBy、cancelSource 等内部账号 / 来源字段。

### 3.6 MyNextInterviewVO / MyNextInterviewItemVO

GET /application/mine/interviews/next 的 data 永远是包装对象：

- serverTime：LocalDateTime。
- timeZone：Asia/Shanghai。
- interview：MyNextInterviewItemVO，可为 null。

Item 复用 ApplicationInterviewVO 字段，增加 jobId、jobTitle、companyId、companyName、companyLogo。
所有展示仍取当前关联资料；权限来自原投递 user_id。点击卡片后按 applicationId 读取专用详情。
没有安排不返回假日期；倒计时基于 serverTime，ONGOING 显示进行中。

### 3.7 ApplicationInterviewListVO（企业）

GET /application/{id}/interviews 返回 applicationId、serverTime、timeZone、items。
items 为本条投递的 ApplicationInterviewVO 列表，startTime DESC、id DESC；无数据 []。
这个读取接口用于企业刷新页面后取得场次 ID、当前版本和历史安排，不依赖刚创建时的前端内存。

## 4. 数据实体字段

### 4.1 Application 增量

| Java 字段 | 类型 | 数据库列 | 规则 |
| --- | --- | --- | --- |
| historyRecordedFromStart | Boolean | history_recorded_from_start | NOT NULL DEFAULT 0；新初始事件同事务写入时显式 true |

其余现有实体字段保留。不能把此字段放入外部 ApplyDTO 或由普通资料修改接口控制。

### 4.2 ApplicationInterview

Long：id、applicationId、version、createdBy、updatedBy。
LocalDateTime：startTime、endTime、cancelledAt、createTime、updateTime。
InterviewMode：mode；InterviewStatus：status；InterviewCancelSource：cancelSource（COMPANY / APPLICATION_STATUS）。
String：platform、meetingUrl、address、candidateNote、cancelReason。
cancelSource / cancelledAt / cancelReason 在非取消状态均为空，取消操作和 version 递增同 SQL 提交。

### 4.3 ApplicationEvent

Long：id、applicationId、interviewId、operatorUserId。
ApplicationEventType：eventType；ApplicationActorType：actorType。
Integer：fromStatus、toStatus；LocalDateTime：occurredAt；String：detailJson。
operatorUserId 由服务端取登录身份；SYSTEM 可空，不能由请求指定。
明细 JSON 是内部存储，读取时转成白名单 InterviewChangeVO，不返回原始 JSON。

## 5. 预期命名常量

ApplicationValidationConstants：platform 50、URL 1000、address 255、note / reason 500、版本最小值。
关键词上限沿用 ApplicationConstants.KEYWORD_MAX_LENGTH；同类常量不重复定义。
ApplicationConstants：默认页码 / 每页数、Asia/Shanghai、初始版本和终态自动取消说明；日期默认 LAST_30_DAYS 位于 QueryDTO。
已有状态、分页上限和薪资单位优先复用；不得在 Service 各处散落 0 / 1 / 100 / 500 等业务数字。
保存后返回真实版本；失败后刷新核实，不自动覆盖重试。

## 6. 示例请求与响应形状

```http
GET /application/mine/page?page=1&pageSize=10&status=2&dateRange=LAST_30_DAYS
GET /application/mine/overview
GET /application/mine/123
```

分页响应沿用现有 PageResult：data={"total":0,"items":[]}；不是直接数组。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "serverTime": "2026-10-25T10:40:00",
    "timeZone": "Asia/Shanghai",
    "interview": null
  }
}
```

以上时间与 ID 仅说明形状，不是接口执行结果。创建未来排期时必须使用运行时真实有效时间。
