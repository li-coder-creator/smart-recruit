# 资质材料审核验收（2026-10-21）

## 结论

五个接口本轮通过Postman MCP回归。主集合保存144条请求，最终成功分组实际执行148次HTTP请求、225条断言，全部通过。
额外4次来自两组并发子请求；数量包含登录、身份核对、权限拒绝等，不代表功能数、覆盖率或性能。
Java编译成功，142个业务源文件；git diff --check通过。
没有迁移数据库，没有提交或推送Git；本轮源数据库指纹保持一致。

## 持久集合与隔离数据

- 集合：SmartRecruit API - Optimized Regression，56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
- 文件夹：02 Company / 资质材料审核回归 2026-10-21，92768da6-4d84-5df4-8f26-460c759d23ef。
- 环境：56227183-df5acf38-3127-43d7-b1ae-bba4d23b0794。
- 服务：http://127.0.0.1:18085；数据库smart_recruit_profile_1792573762416。
- 存储：target/company-qualification-review-1792573762416。
- cp_fixture_1～5是企业；6是求职者；7、8是管理员。每组先登录并核对用户ID。
- 密码从已有测试环境复用，仅放在secret环境和夹具进程环境，不写入集合导出。
- 沿用基础夹具的7001已通过执照及8001材料记录是元数据夹具，没有真实原件；不将其作为真实认证或下载验证。
- 本轮新增材料均通过上传接口提交真实PDF/JPG/PNG，生成文件、材料记录及摘要快照。
- runQualificationReviewRegression=true、qualificationReviewFixtureReady=true、rrHost=http://127.0.0.1:18085是预执行保护；跳过不算通过。

## 最终通过分组

| 分组 | 范围 | 实际请求 | 断言 |
| --- | --- | ---: | ---: |
| A | 五接口、真实下载、角色与企业隔离、输入、反馈、执照标记 | 82 | 117 |
| B | 两管理员并发决定，一成一败 | 12 | 21 |
| C | 审批先持锁、移除随后到达，两者成功，最终移除 | 12 | 21 |
| F0 | 事务故障准备 | 6 | 9 |
| F | 第二张表更新失败，第一张表回滚 | 7 | 12 |
| G | 故障解除后正常通过 | 6 | 9 |
| T0 | 准备完整性检查材料 | 6 | 9 |
| T | 篡改原件后拒绝读取及批准 | 8 | 13 |
| M | 原件缺失拒绝读取及批准，仍可用500字意见驳回 | 9 | 14 |
| 合计 | | 148 | 225 |

所有接口测试通过Postman MCP runCollection执行，未使用鼠标或其他HTTP客户端。
主集合144条请求和精确脚本均在最终读回验证；按同内容分组集合执行，避免重跑主集合中其他业务和不同数据前提。

## 关键结果

- 管理员原件下载：HTTP200，attachment、no-store、nosniff、长度和SHA-256与上传文件一致。
- 非管理员访问管理列表/详情/原件/审批，非企业访问本人历史：业务码500、data=null；未登录五入口HTTP401。
- 数字枚举、未知决定、空驳回意见、501字意见拒绝；500字意见允许。
- 审核通过时记录与材料同步APPROVED，审核人和时间由服务端设置；重复改判拒绝。
- 企业能看到驳回原因；查询传其他companyId仍只能看到自己的历史，超末页items=[]而total保留。
- 管理员可追溯已移除原件；企业旧下载入口拒绝已移除材料，已移除材料不可审批。
- PENDING队列不包含已移除记录，支持材料类型与状态筛选；响应校验明确白名单，无storageKey或磁盘路径。
- 多份有效执照移除其中一份，标记仍true；企业205唯一通过执照移除后标记false。工商与企业准入独立保持。
- B通过隔离库的QUALIFICATION更新延时制造实际重叠，两个管理员返回恰好[200,500]。
- C审批持锁时移除等待，两个请求返回[200,200]；最终removed=true，历史APPROVED，原件可由管理员追溯。
- F在company_qualification第二次更新前触发数据库错误；完整企业、审核、福利、资质基线与故障前一致。
- T/M在独立目录暂存原件后模拟篡改/缺失；通过审核被拒绝且记录保持PENDING、无审核人。最后恢复所有真实原件。

## 工具与夹具问题

首次B的延时触发器只匹配REGISTRATION，没有延迟材料更新，请求重叠断言失败。
该次不计入最终通过结果；改为明确匹配QUALIFICATION后，B重新执行通过。
首次运行产生的一份已处理材料留在隔离库，因此最终数据不等同于仅从成功分组推算的数量。
初次最终审计的日期序列化配置缺失，补齐JavaTime模块后重新审计成功；未修改业务行为。

最终审计确认材料和审核状态一致、已处理记录有审核证据，所有真实原件大小和SHA-256正确；
原件已恢复，测试触发器已移除，源smart_recruit核心表指纹未变。
本轮隔离服务验收后关闭；数据库、原件及证据保留。

## 交付与重跑

- docs/postman/company-qualification-review-regression.postman_collection.json：无凭据用例导出。
- docs/testing/CompanyQualificationReviewFixture.java：依赖既有CompanyProfileFixture、CompanyRegistrationReviewFixture、CompanyFilesFixture。
- target/qualification-review-state.json：环境及分组集合ID。
- target/qualification-review-result-*.json：已脱敏结果；B-harness-failed保留失败夹具记录。
- target/qualification-review-summary.json：最终成功分组统计。
- target/qualification-review-final-evidence.json：最终数据。

使用全新隔离库setup并生成附件；office.png复制为tamper.png；独立端口和存储启动后执行A。
B前delay-material204，C前delay-material205，组后fault-off；F0后baseline、fault-material，再F，fault-off和verify-rollback后G。
T0后tamper→T→restore；missing→M→restore；最后audit和audit-material。
复跑使用新隔离库，不能直接在已处理数据上从A开始。前端管理页面及Blob响应处理仍需联调。
