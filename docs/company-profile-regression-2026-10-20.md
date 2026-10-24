# 企业资料与主页回归验收（2026-10-20）

## 测试对象与隔离

覆盖 GET /company、PUT /company、GET /company/preview、
GET /company/{id}/profile、GET /company/{id}/jobs。
基于本次未提交工作区代码，未提交或推送 Git。

- 所有后端 HTTP 请求通过 Postman MCP 的 runCollection 及集合内 pm.sendRequest 执行。
- 主集合：SmartRecruit API - Optimized Regression
  （56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82）。
- 目录：02 Company / 企业资料与主页回归 2026-10-20。
- 文件夹 ID：24f6281a-e58d-b161-f5ed-7f521f8e117b。
- 隔离环境：Smart Recruit Company Profile Isolated Regression
  （56227183-d4177c98-db9e-47c6-bca5-4ba90b7f4717）。
- 测试地址：http://127.0.0.1:18081；数据库 smart_recruit_profile_1792501959115。
- 复制开发库表结构，只构造测试企业、账号、岗位及审核数据；没有执行新迁移。
- JDBC 仅用于夹具构造、受控故障和数据库结果核对，不用于绕过 Postman 测试接口。
- 快照与元数据为夹具，不代表真实工商核验或真实资质文件；未调用未开发的审核、上传接口。

## 身份和初始数据

| 用户 | 角色 | 企业与准入 |
| --- | --- | --- |
| 101 | COMPANY | 201，APPROVED，主测试企业 |
| 102 | COMPANY | 202，PENDING |
| 103 | COMPANY | 203，REJECTED |
| 104 | COMPANY | 204，APPROVED，隔离对照 |
| 105 | COMPANY | 205，APPROVED，无岗位、资料不完整 |
| 106 | JOB_SEEKER | 求职者 |
| 107 | ADMIN | 管理员 |

用户名 cp_fixture_1～cp_fixture_7，只存在于隔离库；密码从现有 Postman 测试环境读取，
存入专用环境 secret 变量，不写入集合导出、文档或 Git。
初始主企业资料版本 0、工商版本 0、UNVERIFIED，福利、照片、资质与审核记录为空。
主企业岗位 901、902、903 招聘中，904 暂停、905 结束、906 草稿；
其他企业招聘中岗位 907，未准入企业招聘中岗位 908。
岗位更新时间一致，用于验证 id DESC 的稳定排序。

## 分组与执行条件

主集合保留全部用例；因 MCP 运行接口只接受集合 ID，同时保留以下阶段运行集合。
不得将阶段运行集合理解为另建的一次性测试，主集合中的请求、脚本均已持久化。

| 阶段 | 覆盖 | 运行集合 ID |
| --- | --- | --- |
| A | 资料读写、校验、权限、清空、工商变化、旧版本 | 56227183-e94f4ff1-b39b-4bf5-9acd-a731e7089b0e |
| B | 福利插入故障及回滚 | 56227183-a77e5dfb-cac8-460e-8943-20a0ae2ae280 |
| C | 审核快照插入故障及回滚 | 56227183-4b205fde-427c-45c4-84e1-26363062c4b3 |
| D | 三个新接口、字段白名单、分页及企业隔离 | 56227183-87335230-f1f8-4b1e-844c-6cc156526e23 |
| E | 已核验展示、普通编辑保留、工商变化失效 | 56227183-51f4ea2e-1a7c-4eba-8e7b-b89c64a664ee |
| M | 核验版本不匹配、执照移除 | 56227183-99f664c4-a168-4b44-b78f-a762c1ed00a2 |
| N | 两个同版本保存请求真实并发 | 56227183-4c6df9ef-38b5-4dc3-9ab6-0b997d008d66 |

顺序为 A → B → C → D → E → M → N。
环境需要 runCompanyProfileRegression=true、profileIsolatedFixtureReady=true、
cpHost=http://127.0.0.1:18081。未满足时脚本跳过，不得把跳过当作验收通过。

B 前在隔离库安装 BEFORE INSERT ON company_benefit 的 SIGNAL 故障触发器；
C 前改为 BEFORE INSERT ON company_review_record 的同类触发器。
每组执行后移除触发器，并对比主表、福利、审核记录与组前快照。
不能在日常开发库安装故障触发器。

E 前通过隔离夹具构造当前工商版本已核验、有效已审核营业执照和一张照片的元数据。
M 前故意构造核验版本不匹配以及执照已移除的状态。
N 使用集合内 pm.sendRequest 同时发出两个 PUT，提交相同旧版本和不同简介，
断言一个成功、一个业务拒绝，再 GET 核对只增加一个资料版本。

## 请求及响应检查

| 请求场景 | 响应与数据断言 |
| --- | --- |
| 合法 PUT /company | HTTP 200、code 200；GET 内容与提交一致，版本递增 |
| 必填缺失、非法枚举、时间不成对、重复福利等 | code 500、data=null；所有失败后资料完全未变 |
| 旧 profileVersion | code 500，提示资料已更新，不能覆盖 |
| 未登录访问五个接口 | HTTP 401 |
| 非企业读写或预览 | code 500，拒绝角色越权 |
| 求职者查看待审核、拒绝或不存在企业 | code 500，企业不存在或暂不可查看 |
| 预览与公开主页 | 与管理资料共同字段一致，响应属性严格等于公开白名单 |
| GET /company/201/jobs?page=1&pageSize=2 | total=3，items ID=[903,902]，均为招聘中 |
| 第二页 / 超出末页 | ID=[901] / []，total 均为 3 |
| 企业 204 / 企业 205 的岗位 | [907]、total=1 / []、total=0 |
| 工商版本更新 | 版本递增、PENDING、新快照；历史核验版本保留，公开核验标记为 false |
| 两类注入故障 | code 500；主表、福利及审核记录与故障前一致 |
| 同版本并发保存 | 业务码恰好 [200,500]，资料版本只增加 1 |

项目现有全局异常处理将未知枚举、非数字分页等绑定错误返回通用 code 500；
本轮验证了拒绝和无写入，没有重构全局异常提示。
本轮不是负载测试，不据此宣称吞吐量、性能提升或大规模并发能力。

## 实际验收结果

最终代码重新执行 A 后，依序执行 B、C、D、E、M、N，全部通过。

| 阶段 | 实际请求数 | 断言数 | 失败断言 |
| --- | ---: | ---: | ---: |
| A | 57 | 84 | 0 |
| B | 5 | 8 | 0 |
| C | 5 | 8 | 0 |
| D | 54 | 89 | 0 |
| E | 12 | 27 | 0 |
| M | 7 | 16 | 0 |
| N | 6 | 10 | 0 |
| 合计 | 146 | 242 | 0 |

主集合与无凭据导出均为 144 条持久请求；N 包含两条额外 pm.sendRequest 并发子请求，
因此实际 HTTP 请求总数为 146。失败业务场景是预期拒绝，不能把它们算成回归失败。
这些数量包含登录和身份核对，不代表 144 个独立业务功能或完整覆盖率。

数据库补充核对：

- A 完成后主企业 profileVersion=6、registrationVersion=1；审核记录总计 3 条，
  分别为企业 201、202、203 的工商版本 1。普通修改、重复提交、失败请求没有额外审核记录。
- B、C 的主表、福利、审核快照均与各自故障前基线一致；故障触发器已移除。
  自增 ID 可能有间隙，不作为回滚失败依据。
- 最终主企业 profileVersion=9、registrationVersion=2；
  企业 201 有版本 1、2 两份八字段快照，最新快照是第二版公司全称。
- 其他企业福利仍分别属于企业 202、203，没有被主企业的清空操作影响。
- M 为负向用例故意保留“状态 VERIFIED、核验版本不匹配”和“执照已移除”的测试数据，
  只存在于隔离库；公开核验标记均为 false。
- 源开发库核心表的 SHA-256 数据指纹与测试前一致，原开发数据未被测试写入。
- Java 编译通过，112 个源文件；git diff --check 通过。

本轮隔离测试服务在验收后关闭；日常开发需启动自己的 8081 服务加载最新代码。

## 可重复使用的交付物

- docs/postman/company-profile-regression.postman_collection.json：无凭据的 144 条主用例导出。
- docs/testing/CompanyProfileFixture.java：隔离夹具、故障与数据库核对源代码。
- docs/company-profile-api-guide.md：接口规则及实现拆解。
- target/profile-result-*.json：本机脱敏原始运行输出，target 为忽略目录。
- target/profile-final-db-evidence.json：本机数据库验收快照。

夹具需先使用项目运行 classpath 编译到 target；本机 classpath 文件为 target/profile-classpath.txt。
例如 java -cp "target;<项目运行classpath>" CompanyProfileFixture <模式> <隔离库名>。
setup/reset 需要通过进程环境变量 PROFILE_FIXTURE_PASSWORD 提供测试密码；
只允许 smart_recruit_profile_数字 格式的隔离库名，source-unchanged 校验开发库指纹。
故障相关模式为 baseline、fault-benefit / fault-review、fault-off、verify-rollback；
其他模式为 facts、verified、mismatch、remove-license。
不要重复执行会插入元数据的 verified；重跑整套用例先 reset 隔离夹具，再按阶段恢复条件。
