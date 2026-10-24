# 企业后端收尾验收（2026-10-21）

## 结论与范围

完成旧创建、物理删除入口停用及闲置旧写入链路清理，执行企业模块集成回归。
最终 88 次请求、121 条断言，全部通过；Java 142 个源文件编译成功。
数量包含登录、身份核对和拒绝场景，不表示覆盖率、功能数或性能指标。

本阶段后端可进入前端开发与联调。前端完整表单、文件管理、审核反馈及企业主页仍需浏览器验收。
本轮没有重跑所有历史并发 / 故障注入用例；其证据保留在原阶段报告，不累加为本轮结果。

## 需求、影响与交付

- 背景：旧 POST /company 接收完整实体，有绕过统一注册、写入系统状态的风险；物理删除破坏关联及历史追溯。
- 目标：旧入口明确停用，注册仍固定 COMPANY、PENDING，新资料与两类审核流程正常。
- 模块：CompanyController、CompanyService、CompanyServiceImpl、CompanyMapper，相关文档与 Postman 集合。
- 验收：登录后两个旧入口均 HTTP 410 / code=500 / data=null；匿名仍 HTTP 401；不执行业务写入。
- 边界：不同角色、目标企业有无岗位、伪造系统字段、畸形 JSON、注册事务失败、资料版本冲突、旧版本审批、原件移除。
- 交付：代码、持久回归、无凭据导出、[联调契约](company-backend-integration-guide.md)及本文。

## 实现说明

只读确认当前前端仅调用 GET /company、PUT /company、POST /company/register，没有旧创建 / 删除调用方。
两个旧路由保留为 HTTP 410 Gone，不绑定实体、不调用 Service；未登录仍经原拦截器返回 401。
移除 Service.add/update/delete 旧方法及实现、Mapper 无版本 update 和物理 delete。
保留 Mapper.add，因为 UserServiceImpl.registerCompany 仍需要在事务中插入企业。

注册 DTO 只承接注册参数，角色 COMPANY、准入 PENDING 由服务端设置；账号和企业在同一事务中写入。
新版资料仍由 Service 负责权限、归一化、版本与事务，Mapper 使用版本条件更新 SQL。
没有新增企业注销生命周期或替代删除接口。

## 测试前提与持久位置

- 工具：全部接口测试经 Postman MCP runCollection，无鼠标或其他 HTTP 客户端。
- 主集合：SmartRecruit API - Optimized Regression，56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
- 文件夹：02 Company / 企业模块收尾回归 2026-10-21，d1d41ff2-9ffe-3c84-847c-68863d05e5e2。
- A/B 共 88 条请求，执行前后均读回核对完整断言；实际运行内容相同的分组集合，避免重跑其它业务。
- 环境：56227183-d72af285-8eeb-4f20-9502-de418010ea2b。
- 服务：http://127.0.0.1:18086；数据库 smart_recruit_profile_1792583602265。
- 原件目录：target/company-company-close-1792583602265。
- 预执行保护：runCompanyCloseRegression=true、companyCloseFixtureReady=true、rrHost 精确匹配隔离地址。
- cp_fixture_1 为已准入企业，2 初始待准入，6 求职者，7 管理员；每组先登录、核对 ID。
- B 新注册独立企业，初始 PENDING。密码复用已有测试 secret，不写入导出或报告。
- 基础夹具 7001 / 8001 是无真实原件的元数据样本，不作为下载证明；本轮新企业实际上传 PNG 和 PDF。

夹具复用 docs/testing/CompanyProfileFixture.java、CompanyRegistrationReviewFixture.java、
CompanyFilesFixture.java、CompanyQualificationReviewFixture.java，只克隆表结构并创建测试账号。

## 请求、响应与结果

| 分组 | 关键请求及预期 | 请求 | 断言 | 结果 |
| --- | --- | ---: | ---: | --- |
| A 旧入口 | 四种登录身份 POST/DELETE 为 HTTP 410；匿名 HTTP 401；原资料仍可读取 | 24 | 29 | 通过 |
| B 完整流程 | 注册、资料、文件、准入、两类审核、主页和历史追溯成功；冲突和越权业务拒绝 | 64 | 92 | 通过 |
| 合计 | 无失败请求、无失败断言 | 88 | 121 | 全部通过 |

### A 关键验证

伪造 userId、准入及工商核验状态均不能生效；删除携带有岗位或无关联企业 ID 均拒绝；
畸形 JSON 不触发旧实体写入。组前 baseline、组后 verify-rollback，企业、审核、福利、资质四表完整基线一致。

### B 关键验证

1. 企业名称超过数据库容量导致第二次插入失败；随后同用户名正常注册成功，证明账号插入随事务回滚。
   此用例用于故意制造事务失败，不代表注册 DTO 已覆盖全部字段长度校验。
2. 注入 ADMIN、APPROVED、VERIFIED 不生效；新账号角色 COMPANY、企业 PENDING、profileVersion=0，两核验标记 false。
3. 旧资料缺字段可读取；完整保存成功，同版本重试和旧前端不完整表单均被拒绝。
4. 工商生成版本 1 快照，实际上传照片及执照，企业私有读取正常。
5. 待准入本人可预览、求职者不可查看；管理员准入通过后主页 / 照片可读，既有企业岗位分页仅展示招聘中岗位。
6. 管理员独立通过工商、材料审核后两标记 true；其它企业、求职者不能越权读取原件。
7. 工商再次变化生成版本 2，旧审批拒绝；新版驳回保留意见，历史通过版本仍为 1，当前 registrationVerified=false。
8. 移除唯一有效执照后 businessLicenseVerified=false；企业不可下载，管理员仍能追溯，详情 removed=true、reviewable=false。
9. 删除照片后 photos=[]、photoCount=0，原 URL 拒绝；新企业旧 DELETE 仍拒绝，两条工商历史保留。

管理员两次 PDF 下载均核对 SHA-256、Content-Length、attachment、no-store、nosniff。
普通业务拒绝为 HTTP 200 / code=500 / data=null，JSON 成功为 HTTP 200 / code=200；文件成功返回二进制。

## 最终审计与环境恢复

- audit：源 smart_recruit 的 company、company_benefit、company_photo、company_qualification、company_review_record、job、sys_user 指纹未变。
- 工商版本与证据有效；材料 / 审核状态一致，已处理记录有审核人和时间。
- audit-material：真实原件大小及 SHA-256 与快照一致，移除的资质原件仍保留。
- 无测试触发器残留，未对开发库执行迁移。最终编译成功，git diff --check 通过。
- 已核对命令行中的应用、18086 端口及隔离库后关闭本轮 PID 10224。隔离数据库、原件及脱敏结果保留。
- 没有 Git 提交或推送；原先暂存的 DTO、其它未提交改动和无关演示文稿保留。

## 证据与复跑

- docs/postman/company-close-regression.postman_collection.json：无凭据导出。
- target/company-close-state.json：集合、环境、隔离库、端口与原服务 PID。
- target/company-close-result-A.json、company-close-result-B.json：脱敏执行输出。
- target/company-close-disabled-baseline.json：A 组前完整四表基线。
- target/company-close-final-evidence.json、company-close-material-evidence.json：最终数据证据。
- target/company-close-summary.json：本轮统计。

复跑须 setup 全新隔离库、生成附件并启动指向新库和私有目录的独立服务，夹具密码从 secret 环境传入。
就绪后 baseline → A → verify-rollback → B → audit → audit-material。
保持导出保护条件与隔离端口一致；不能直接在已审核 / 移除的数据上重放 B，也不能指向开发库。

## 学习与面试

- 410 路由用于明确停用，拦截器仍负责 401；停用方法不进入写入链路。
- 删除旧 Service / Mapper 写入路径，减少未来内部调用绕过完整表单及版本规则的机会。
- Mapper.add 是统一注册事务的必要依赖，不能一并移除。
- profileVersion 防资料覆盖；registrationVersion 标识审核证据所对应的工商内容。
- 移除改变当前有效材料标记，审核决定和原件仍保留以供追溯。

可准确描述为“实现企业资料版本控制、工商与资质审核、私有原件权限及历史追溯，建立 Postman 持久回归用例”。
实现由协作完成，需真正掌握后再用于简历与面试；没有生产规模、吞吐或上线成果依据。
