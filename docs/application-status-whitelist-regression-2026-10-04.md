# 投递状态白名单验收记录

## 结论与发现

2026-10-04，当前工作区代码的白名单回归通过：110 个实际 HTTP 请求、218 个断言全部通过。
另执行 2 个诊断请求、4 个断言，确认一个既有问题：简历缺失时，投递详情返回“该投递不存在”。
该诊断断言通过表示问题成功复现，不表示详情接口业务正确。

最终分组合计 112 个实际请求、222 个断言。
第一次 B 分组因隔离企业用户名超过 26 位而在登录处中断，执行了 17 个请求、33 个断言，其中 2 个失败。
修正隔离账号和持久用例后，完整 B 分组重新执行通过；保留首次失败记录，不将它合并为全通过。
含首次中断运行，实际执行总数为 129 个请求、255 个断言，最终验收以修正后的完整分组为准。

本轮没有修改业务 Java 源码、开发库业务记录、前端文件，也没有 Git 提交或推送。
编译阶段成功；离线 Maven dependency:build-classpath 因插件依赖未缓存失败，
随后复用已有 Spring Boot classpath 参数启动服务。所有功能结论来自真实 HTTP 与数据库核对。

## 集合、环境与身份

- 所有后端 HTTP 请求通过 Postman MCP runCollection 或其中的 pm.sendRequest 执行。
- 主集合：SmartRecruit API - Optimized Regression。
- 主集合 UID：56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
- 文件夹：05 Application / 投递状态白名单回归 2026-10-04。
- 文件夹 ID：9c596e4f-5f59-f4b0-629f-060009e7e67b。
- 110 个集合请求已持久保存，其中白名单用例 108 个、诊断用例 2 个。
- 并发场景另外由前置脚本发出 2 个请求，所以最终实际请求数为 112。
- 主集合新增请求默认跳过，只有 runApplicationWhitelistRegression=true 且
  whitelistIsolatedFixtureReady=true 才运行。须先准备独立数据库、wlHost 与相应投递 ID。
- 执行分组是主集合用例的持久副本，用于 MCP 仅支持按集合运行时分阶段选择环境。
  登录令牌只在运行时变量中保存，密码仅引用既有环境变量。

隔离服务 localhost:8082，数据库 smart_recruit_panels_1791094776600，Redis 数据库 15。
数据库复制开发库结构及必要种子身份，测试岗位/投递 ID 从 920000000 开始。
已审核企业 userId=43、companyId=27；求职者 userId=44；其他企业 companyId=11；
隔离待审核企业 userId=50；隔离管理员 userId=51。
管理员账号从企业密码校验数据复制，仅在隔离库改变角色，使用原企业环境密码变量登录。

企业阶段使用 Smart Recruit Job Regression，求职者阶段使用 Smart Recruit Local。
不是要求 Postman 账号具有企业身份；这里的企业身份指 Smart Recruit 系统中的测试用户。

## 分阶段结果

| 分组 | 内容 | 实际请求 | 通过断言 |
| --- | --- | ---: | ---: |
| A | 身份、未知总览、25 种迁移、详情落库、9 种合法迁移的进展统计 | 62 | 124/124 |
| B（修正后） | 入参、权限、终态数据保护、未知历史及缺失简历 | 40 | 79/79 |
| C | 同原状态并发、进展、最近投递及岗位活动隔离 | 6（4 个集合请求 + 2 个内部请求） | 11/11 |
| D | 求职者登录及修改权限拒绝 | 2 | 4/4 |
| 白名单验收小计 | | 110 | 218/218 |
| E（诊断） | 企业登录及缺失简历的既有详情错误复现 | 2 | 4/4 |
| 最终分组合计 | | 112 | 222/222 |

## 请求、预期与实际响应

接口 PUT /application/{id}/status，JSON 示例 {"status":2}。
成功和业务错误沿用项目既有规范：HTTP 200，业务码分别 200/500；未登录 HTTP 401。

| 场景 | 实际结果 |
| --- | --- |
| 待处理 → 筛选/面试/录用/拒绝 | 4 种成功，code=200，data=null |
| 筛选 → 面试/录用/拒绝 | 3 种成功 |
| 面试 → 录用/拒绝 | 2 种成功，累计面试证据保留 |
| 全部 5 种同状态请求 | code=500，message=投递已处于目标状态 |
| 其他回退及终态修改 | code=500，message=不能从当前状态转为目标状态 |
| status 缺失/null | code=500，message=投递状态不能为空 |
| status=-1 / 5 | 对应小于 0 / 大于 4 的校验文案 |
| status="abc" 或 JSON 不完整 | code=500，通用系统异常文案；输入拒绝有效，提示仍应改进 |
| 不存在投递 | code=500，message=该投递不存在 |
| 其他企业投递 | code=500，message=无权修改该投递 |
| 未审核企业 | code=500，message=企业未通过验证 |
| 求职者 / 管理员 | code=500，message=仅企业用户可访问 |
| 未登录 | HTTP 401 |
| 注入的非法旧状态 9 | 失败、不写数据，不猜测为合法状态；通用错误文案 |
| FALSE 待处理直接录用 | has_interviewed=FALSE；累计面试 0，转化率 0 |
| NULL 待处理直接录用/拒绝、进入筛选 | 历史仍 NULL；累计面试数和转化率 NULL |
| NULL 进入面试 | has_interviewed=TRUE；当前面试 1、累计面试 1、转化率 100 |
| NULL 原面试转录用 | has_interviewed=TRUE；当前面试 0、累计面试 1 |
| 既有 TRUE 筛选转录用 | TRUE 保留，不丢失已有历史证据 |
| 简历缺失后修改状态 | 成功，原始 Application 查询不依赖展示 JOIN |
| 简历缺失后最近投递 | 保留该条记录，姓名回退为账号昵称 |
| 简历缺失后投递详情（诊断） | code=500，message=该投递不存在，确认待修复 |

25 种迁移分别使用独立记录，不通过接口回退状态来重置测试数据。
每条迁移之后读取投递详情确认状态及原始投递时间；拒绝迁移同时确认 updateTime 不变。
SQL 独立核对所有矩阵记录的 status、has_interviewed、create_time 与拒绝后的 update_time。

## 并发证据

隔离库辅助事务先锁住投递 920000033 的行；两个 Postman 请求分别尝试 SCREENING → INTERVIEW 和
SCREENING → HIRED。普通 SELECT 不被该锁阻塞，两次 UPDATE 在行锁上等待。
辅助程序通过 MySQL PROCESSLIST 确认两个 UPDATE 均已进入数据库后释放锁。
因此可以确认本次竞争的两个请求都按同一旧状态完成 Service 判断，而不是先后读取不同状态。

响应业务码为 200 与 500，失败文案为“投递状态已变化，请刷新后重试”。
本轮 INTERVIEW 请求胜出，最终 status=2、has_interviewed=TRUE。
单岗位进展、最近投递查询与该结果一致；create_time 未改变，岗位活动仍为空。
辅助事务 finally 释放锁；未创建故障触发器。

这证明本次同原状态竞争的条件更新生效，不代表任何两个并发请求都只能成功一个。
前一个提交后读取新状态的后续请求仍可执行另一条合法迁移。

## 开发库只读盘点

- 岗位共 12 条：招聘中 4 条、已结束 8 条；没有未识别岗位状态。
- 投递共 6 条：当前面试 2 条、录用 3 条、拒绝 1 条；没有未识别投递状态。
- 面试历史：2 条当前面试为 TRUE，2 条录用为 TRUE，1 条录用为 FALSE，1 条拒绝为 TRUE。
- 当前开发库没有 NULL 面试历史；隔离测试仍覆盖 NULL 兼容行为。
- 开发库已有 1 条投递的简历关联缺失，历史可见性问题需要修复。
- 唯一索引 uk_application_user_job(user_id,job_id) 已存在；不能说重复投递毫无数据库保护。
- 现有投递索引 idx_application_job_status_time(job_id,status,create_time) 已存在。

## 交付与复查限制

运行结果（密码及 JWT 已脱敏）保存在本地忽略目录：
target/whitelist-result-A.json、B.json、B-initial.json、C.json、D.json、E.json。
SQL 验证证据：target/whitelist-db-verification.txt、whitelist-final-facts.json。
并发证据：target/whitelist-concurrency-evidence.txt。
测试数据工具：target/WhitelistFixture.java 与 target/JobPanelFixture.java。
Postman MCP 编排工具：D:/project/.mcp/postman/whitelist-regression.cjs、whitelist-concurrency.cjs。

专用测试服务在验收结束后关闭；隔离数据库保留供复查，不删除开发环境记录。
本轮并非完整岗位模块再回归、性能压测、Redis 故障验证或部署验收。
白名单不解决“暂停/结束岗位与新增投递同时竞争”的另一个业务并发边界。
