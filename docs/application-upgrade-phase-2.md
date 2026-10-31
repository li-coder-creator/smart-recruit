# 我的投递升级：阶段二查询实现

日期：2026-10-25。

本页记录阶段二的交付状态。后续写链路增强见 [阶段三说明](application-upgrade-phase-3.md)。

## 1. 业务目标、范围与交付状态

让求职者按条件查询本人投递，读取全部历史的当前状态概览，并查看本人详情。
影响模块：新增求职者查询 Controller / Service / Mapper、QueryDTO、日期枚举、常量与 Clock。
旧投递 Controller、写 Service 和 ApplicationVO 均未修改。

已完成查询代码和本地正式迁移；Maven 跳过测试编译通过（162 个源文件）。
阶段二交付时未执行 Postman 回归；后续相关查询已纳入
[阶段三隔离回归](application-upgrade-phase-3-regression-2026-10-25.md)，完整查询边界仍按验收清单继续补齐。
最新的精确日期边界和关联资料组合验收见 [第五阶段验收报告](application-upgrade-phase-5-regression-2026-10-28.md)；本页后续“尚未运行”等描述保留阶段二当时的交付语境。
用户确认停止后端后，已核对 8081 无监听并执行增量 DDL；新详情依赖的结构已满足。
后端保持停止，用户可以在 IDE 重新启动以加载阶段二代码。

## 2. 接口契约

| 请求 | 参数 | data | 权限与口径 |
| --- | --- | --- | --- |
| GET /application/mine/page | page、pageSize、status、keyword、dateRange | PageResult<MyApplicationListVO> | 仅当前求职者，条件影响列表和 total |
| GET /application/mine/overview | 无 | MyApplicationOverviewVO | 仅当前求职者，全部历史当前状态，不受列表条件影响 |
| GET /application/mine/{id} | 投递 ID | MyApplicationDetailVO | 仅本人，缺失与越权统一拒绝 |

page 默认 1，pageSize 默认 10、最大 100。超出末页保留 total，items=[]。
status 省略为全部，仅接受 ApplicationStatus 中的状态码；不支持 -1。
keyword 去首尾空白，空白等同不筛选，最多 100 字符；只搜索岗位标题或企业名。
`!`、`%`、`_` 使用 `ESCAPE '!'` 按字面搜索，所有输入使用绑定参数。
dateRange 默认 LAST_30_DAYS，仅支持 LAST_7_DAYS、LAST_30_DAYS、ALL。

统一 Asia/Shanghai Clock：7 天含今天从前 6 天零点起，30 天从前 29 天零点起。
所有范围均限制 create_time <= 本请求 now；ALL 仅移除下限。
排序固定 create_time DESC、id DESC。分页权限查询在 PageHelper 之前，finally 清理分页上下文。

## 3. 实现调用链

1. Controller 绑定白名单查询字段并执行 @Valid；从 ThreadLocal 取登录 userId，不接收切换归属的参数。
2. Service 检查求职者身份、业务状态码及日期窗口，控制只读 REPEATABLE_READ 事务，组装展示字段。
3. Mapper 在 SQL 中始终限制 ja.user_id，读取展示白名单；只在详情加载事件和面试场次。
4. 概览用单条聚合 SQL 直接统计 job_application；无记录 COUNT 返回 0，不读取完整投递列表。

分页 count/items、详情主记录/事件/安排处于各自同一读事务；不同 HTTP 请求之间不承诺同一快照。
这避免一次请求内部多个 SQL 读到不同提交版本，并不锁定企业的正常状态处理。

## 4. 历史与隐私边界

- LEFT JOIN 保留岗位、企业或简历展示信息缺失时的历史投递。
- 简历关联同时检查所属求职者；不存在或不再属于本人时 resumeAvailable=false。
- 已结束岗位仍返回历史内容，jobAvailable 与 jobViewable 分开；后者要求 ACTIVE 且企业 APPROVED。
- 读取当前资料，不表示投递时快照；缺失信息不从原型填充。
- REJECTED 在新求职者响应显示“未通过”，不改变旧企业枚举文案。
- 概览分组数量合计与 total 不符时记录异常并拒绝，不将非法状态算进其它桶。
- historyComplete 来自真实数据库标记；旧记录不根据状态、更新时间或事件条数推断完整性。
- 事件使用内部查询投影转换成公开 VO，原始 detail_json 和操作者 ID 不返回。
- 面试变更仅反序列化契约中的 InterviewChangeVO 白名单；原始 JSON 不写日志。
- 面试派生状态统一使用本次服务器时间；到期只表示 EXPIRED，不推断参加或完成。

本阶段只读取已有真实事件/排期。阶段三开始补初始事件与状态事件事务；阶段四实现排期写入。
新表为空时 events=[]、interviews=[]，不能补造历史。

## 5. 正式迁移步骤与执行结果

脚本：`docs/sql/2026-10-25-application-query.sql`，与阶段一草案的三个 DDL 一致。

1. 停止后端和其它投递写入；确认目标库 smart_recruit。
2. 重新核对旧八列、索引、外键、合法状态及新对象尚不存在；部分迁移环境禁止直接重跑。
3. 备份 job_application 全部数据和结构、原索引/外键，保存旧八列按 ID 排序的投影供比对。
4. 逐条执行：增加完整性标记和两个查询索引，建立 application_interview，再建立 application_event。
5. 核对原八列逐行一致、原唯一约束和外键保留、历史标记全 false、新表均为空。
6. 编译后启动新版后端。阶段五统一使用 Postman MCP 进行持久回归。

DDL 隐式提交，不通过业务事务回滚。失败时记录已成功步骤并停止；不自动删列、删表或清数据。
旧版本仍可写入，新增标记默认为 false；只有阶段三的初始事件同事务写入后才显式设 true。

### 本地执行记录

2026-10-25 14:05，目标 smart_recruit，三条 DDL 已逐条成功执行。当前库禁止再次直接执行该脚本。

| 核对项 | 结果 |
| --- | --- |
| 原投递数据 | 7 条，原八列逐行一致，包括 status、has_interviewed、create_time、update_time |
| 原列、索引、外键 | 完整保留，包含 uk_application_user_job 和 fk_application_job |
| 新查询索引 | idx_application_user_time、idx_application_user_status_time，列顺序符合脚本 |
| history_recorded_from_start | 7 条均为 false，未补造完整历史 |
| application_event | 0 条 |
| application_interview | 0 条 |
| 状态分布 | 待处理 1、筛选中 0、面试 2、录用 3、未通过 1，迁移前后不变 |

迁移前后原八列投影的 SHA-256 均为：
`da19bf5747a1b1f4b960e4ac19f9718eea20fe23ba8d4e1ff227c04c2c2ca16c`。

永久本地备份目录：
`D:\project\smart-recruit-backups\application-query-migration-20261025-140549-342`。
其中 before.json 保存原结构、索引、外键及全部旧行；job_application-backup.sql 保存原表建表语句和全部数据；
after.json 保存执行后结构及核对结果；journal.txt 记录每条 DDL 完成状态；migration.sql 为实际执行脚本副本。
这 5 个文件已从 target 复制到仓库外，逐个核对 SHA-256 一致，不受 mvn clean 影响，也未加入 Git。
SQL 备份只供恢复方案参考，不可直接导入已有表。

首次尝试在备份序列化 JDBC LocalDateTime 时停止，尚未执行任何 DDL；修正日期归一化后，
重新核对旧结构并完成本次迁移。失败尝试的记录保留在 target，未执行自动删表或回滚 DDL。
以上是迁移完整性核对，不代替 Postman 接口回归。

## 6. 验收标准与待验证边界

以下为验收要求，尚未运行：

- 五种状态和空数据概览正确，interviewCount 表示当前面试。
- 最近 7/30 自然日边界、ALL、未来时间上限与稳定排序符合契约。
- 字面关键词、空白、超长、错误枚举、非整数页码/状态、大于末页等输入得到约定结果。
- 企业、管理员、未登录和其它求职者不能读取本人查询资源。
- 岗位结束、企业状态变化和原简历删除不会使历史投递消失；跳转标记准确。
- 旧 historyComplete=false、空历史数组与真实已有事件/安排展示准确，不返回原始内部字段。
- 旧 GET /application/mine、/my、/{id} 及原写接口兼容。

接口用例将在阶段五加入 SmartRecruit API - Optimized Regression，不使用临时 HTTP 脚本替代。

## 7. 面试考点

- 为什么概览独立聚合、不能遍历完整分页列表？分页范围和概览口径不同；聚合可减少传输和对象装配。
- LEFT JOIN 的条件放在哪里？归属必须约束主表；ACTIVE 等条件不能放在 WHERE 导致历史记录被过滤。
- RR 有什么作用？同一事务普通读使用一致性快照；不保证不同 HTTP 请求读到同一时刻。
- SQL 绑定是否足以防止关键词扩大搜索？绑定防止 SQL 结构注入；LIKE 通配符仍需单独转义以符合字面搜索语义。
- 为什么时间到期不是已完成？时间派生状态不是出席事实，需要独立业务证据。
- 为什么不能把旧状态补成完整事件？当前状态无法还原经过的阶段与发生时间，跳级使这种推断更不可靠。

当前可准确描述“实现本人投递分页、SQL 聚合概览与历史兼容详情查询”。
尚不能描述“完成全链路投递升级、通过回归或实现面试排期”。无性能测试数据，不写性能提升百分比。
