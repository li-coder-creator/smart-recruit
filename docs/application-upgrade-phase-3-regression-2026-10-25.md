# 我的投递阶段三回归验收

日期：2026-10-25。范围：投递与初始事件、状态推进与真实事件、相关求职者查询和旧接口兼容。

## 1. 结论与边界

本轮通过 Postman MCP 完成 135 次请求、230 条断言，失败为 0。业务拒绝属于预期响应，不计作失败。
没有发现本轮范围内需要修改后端实现的问题，没有修改 Java 业务代码。
131 条集合请求已持久保存并核对方法、URL、请求体和脚本；并发脚本另发出 4 次子请求，因此实际执行为 135 次。

完整面试排期、新建/改约/取消、近期面试和终态自动取消仍属于阶段四，未在本轮测试。
阶段二查询本轮做了状态、默认 30 天/ALL、关键词转义、分页、权限及详情的相关回归；
没有据此宣称 7/30 天精确时间边界和所有岗位/企业关联缺失组合已全部验收。
并发验证是两组各两个真实重叠请求，不是压力测试，不推导吞吐量或性能提升。

## 2. 执行环境与数据

- 项目：`D:\project\smart-recruit`，main，使用已编译的阶段三代码启动独立实例。
- 隔离数据库：`smart_recruit_application_events_1792914332453`。
- 独立地址：`http://127.0.0.1:18087`；开发服务端口和用户 IDE 进程未操作。
- 从开发库复制表结构，用合成账号、简历和岗位构造数据；没有复制开发库的全部业务行。
- 求职者 101、102、107（107 无投递）；所属企业账号 103，外企 104，未准入企业 105，管理员 106。
- 初始投递为 6 条旧历史，全部 historyComplete=false；包含缺失简历和 has_interviewed=null。
- 合成测试密码经用户明确授权，以 secret 变量保存到 Postman 专用测试环境，不使用真实账号密码。
- 开发库所有业务表数据在测试前后计算 SHA-256，指纹一致；原有 7 条投递未因本轮测试改变。

## 3. Postman 位置与统计

主集合：`SmartRecruit API - Optimized Regression`。
目录：`05 Application / 我的投递真实事件回归 2026-10-25`。
执行副本与主集合请求内容相同，避免执行主集合中其它模块的写入用例。

| 组别 | 内容 | 实际请求数 | 断言数 | 失败 |
| --- | --- | ---: | ---: | ---: |
| A | 功能、权限、历史、查询及旧接口兼容 | 101 | 166 | 0 |
| B | 事件插入失败与原子回滚 | 10 | 18 | 0 |
| C | 故障移除后重试恢复 | 10 | 18 | 0 |
| D | 状态竞争与重复投递竞争 | 14（10 条集合请求和 4 条子请求） | 28 | 0 |
| 合计 | | 135 | 230 | 0 |

用例导出：`docs/postman/application-event-regression.postman_collection.json`。
集合/目录/执行副本 ID：`docs/postman/application-event-regression.manifest.json`。
统计、数据库核对及合成事件事实：`docs/postman/application-event-regression.results.json`，不含测试密码或 JWT。
原始 Postman MCP 执行输出已脱敏，位于本次 target 隔离证据目录的 result-A/B/C/D.json。

## 4. 关键请求、响应与核对结果

下列响应要点由请求断言或数据库比对确认，不将示例当作额外执行记录。

| 请求与场景 | 响应要点 | 核对 |
| --- | --- | --- |
| POST /application，合成本人简历和 ACTIVE 岗位 | HTTP 200，业务码 200 | 回填的主键关联唯一 APPLIED，新标记 true |
| GET /application/mine/{id}，新投递 | status=0，historyComplete=true，events 仅一条 APPLIED | from=null，to=0，actor=JOB_SEEKER，隐私白名单正确 |
| PUT /application/{id}/status，0→1→2→3 | 各次业务码 200 | 三条 STATUS_CHANGED 起止状态真实，最终加上 APPLIED 共四条 |
| PUT /application/{id}/status，0→3 跳级 | 业务码 200 | 仅 APPLIED 与 0→3，不存在伪造筛选或面试事件，面试证据仍 false |
| PUT /application/{id}/status，0→4 | 业务码 200 | 求职者 statusText=未通过，终态后不能再改录用 |
| 重复、回退、终态再变更、非法目标状态 | 业务码 500，data=null | 主记录和事件不增加错误变更 |
| 他人简历、企业/管理员投递、非 ACTIVE 岗位、缺失参数 | 业务码 500，data=null | 没有成功投递或初始事件 |
| 外企、未准入企业、求职者、管理员修改投递 | 业务码 500，data=null | 不发生状态和事件变化 |
| 匿名访问写接口/本人查询 | HTTP 401 | 不发生写入 |
| 越权本人详情和不存在详情 | 相同业务错误“投递不存在或无权访问” | 不泄露其它求职者资源 |
| 旧投递 501：缺少原简历 | 详情仍成功，resumeAvailable=false，resumeTitle=null | 状态推进后仅记录本次事件，历史标记继续 false |
| 旧投递 502：已有 INTERVIEW、旧面试证据 null，推进录用 | 业务码 200 | 记录 2→3，真实面试证据变 true，历史仍不完整 |
| 旧投递 504：录用、旧面试证据 null | 查询兼容 | 未制造面试事实，null 保留 |
| GET /application/mine、/my、/{id} | 业务成功，旧列表仍为数组 | 旧详情没有扩大成带 events 的新响应 |

查询还覆盖：五个状态桶及空用户、45 天前记录默认不在最近 30 天、ALL、同时间 ID 倒序、
超末页 total 保留且 items=[]、字面 `%_!` 搜索、空白/超长关键词、非法页码/状态/日期枚举。

## 5. 失败回滚与恢复

只在隔离库 application_event 上安装 BEFORE INSERT 故障触发器，主动抛出 SQL 错误。
安装前保存 job_application 和 application_event 的全部业务行作为基线。

1. 新投递插入后，APPLIED 插入被故障触发器拒绝；接口业务码 500，按岗位查询不存在这条投递。
2. 现有新投递 0→2 更新后，STATUS_CHANGED 插入被拒绝；接口业务码 500。
3. 详情比较排除每次变化的 serverTime，其余业务字段和事件与故障前一致。
4. JDBC 比对完整业务行确认 status、has_interviewed、history_recorded_from_start、create_time、update_time 和事件均未改变。
5. 移除触发器后，重试新投递和状态变更均成功，并分别产生真实事件。

该核对不要求 AUTO_INCREMENT 连续。事务回滚可能消耗自增号，不能以自增号有缺口判断业务残留。

## 6. 并发结果

仅在隔离库事件插入处加入 2 秒延时，延长第一个事务持锁时间，让另一个请求在提交前读到原状态。
通过 Postman 脚本并行发送请求，并用客户端开始/结束时间断言两请求确实重叠。

- 同一待处理投递分别请求进入面试和录用：一方业务码 200，另一方 500 并提示刷新；
  最终实际进入面试，只有一条 0→2 状态事件，另一条竞争请求没有事件。
- 同一求职者、同一岗位同时投递：一方 200，另一方 500；唯一投递对应唯一 APPLIED，历史标记 true。

最终隔离库共 13 条投递、17 条事件；7 条新投递历史完整、6 条旧投递仍不完整。
逐条核对 APPLIED 唯一性、状态事件合法性、真实操作者角色和 has_interviewed 的 true/false/null 口径。
本轮两次竞争成功不代表所有交错时序均已覆盖，也不代表生产并发容量。

## 7. 收尾与复跑

- 失败和延时触发器均已移除，最终 information_schema 核对无残留触发器。
- 本轮创建的独立后端已停止。
- Postman 专用环境的 runApplicationEventRegression 和 applicationEventFixtureReady 已关闭，防止误触发。
- 隔离数据库和证据保留供复查；没有删除数据库、提交或推送 Git。

已将工具源文件保存到项目，避免依赖会被 mvn clean 清除的一次性脚本：

- `docs/postman/ApplicationEventFixture.java`：独立夹具、故障/延时开关、数据与开发库指纹核对。
- `docs/postman/application-event-regression.cjs`：通过 Postman MCP 保存、核对、执行各组请求。
- `docs/postman/postman-mcp.cjs`：MCP stdio 客户端，不使用 curl/fetch 直接测试接口。

复跑必须新建空的隔离库，不在这次已完成的数据上直接重复 A 组。
在项目根目录编译上述夹具到 target，classpath 使用 target/classes 与项目依赖；
MCP 运行时默认位于 `D:\project\.mcp\postman\node_modules`，可用 POSTMAN_MCP_RUNTIME 指定已有安装目录。
本机必须已配置 POSTMAN_API_KEY，不能写进脚本或集合导出。

设置 APPLICATION_EVENT_STATE_FILE 为 target 内一个新的状态文件名后，顺序为：
setup-local、setup-environment、start、prepare、verify、A、baseline、fault-on、B、
verify-rollback、fault-off、C、delay-on、D、delay-off、audit。
setup-environment 保存合成密码到 Postman 专用环境；复跑者应知晓该外部保存行为。
夹具只操作严格命名的隔离库；执行 B/D 前核对触发器状态，异常退出也要清除本次触发器并关闭独立后端。
Manifest 复用现有持久用例 ID，避免每次重新创建同名目录。未来修改用例后必须重新同步和核对集合。

## 8. 可用于面试的真实成果

可以准确说明：在隔离环境通过 Postman MCP 验证投递和事件同事务写入，注入事件失败验证回滚，
并以两个重叠请求验证状态竞争和唯一约束防重复；本次 135 次请求、230 条断言通过。
这些数字是本轮回归结果，不是系统业务规模或性能指标。
