# 工商资料审核回归验收（2026-10-21）

## 结论

四个接口已通过本轮 Postman MCP 回归：管理员列表、详情、审批，企业自己的审核历史。
主集合持久保存156条请求，实际执行162次HTTP请求、286条断言，全部通过。
额外6次请求来自B/C/D三组真实并发子请求；统计包含登录、身份核对和拒绝场景，不代表功能数量或覆盖率。
没有通过鼠标、curl或其他HTTP客户端执行接口验收。

Java编译成功，133个业务源文件；本轮没有数据库迁移。
源数据库核心表指纹与测试前相同；测试服务结束后关闭，独立数据库和证据保留。

## 环境、身份和数据前提

- 主集合：SmartRecruit API - Optimized Regression，56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
- 文件夹：02 Company / 工商资料审核回归 2026-10-21，b40568db-7bd1-4776-14c4-af36151aa1ca。
- 环境：Smart Recruit Registration Review Isolated Regression，56227183-c1ad52c0-03a3-4319-8224-d08ded61e296。
- 独立服务：http://127.0.0.1:18084。
- 独立数据库：smart_recruit_profile_1792572274217。
- 独立存储目录：target/company-registration-review-1792572274217。
- cp_fixture_1～5为企业，企业ID201～205；6为求职者；7、8为管理员，用户ID107、108。
- 企业201/204/205准入APPROVED，202为PENDING，203为REJECTED；初始工商UNVERIFIED、版本0，无工商审核记录。
- 初始材料7001和审核8001仅为APPROVED营业执照元数据夹具，用于验证材料审核不会混入工商列表、工商审批不改变执照标记。
  没有将该夹具当作真实证照，也没有测试材料原件下载或材料管理员审核。
- 工商记录全部通过PUT /company实际提交生成，覆盖版本1～4，未直接伪造工商审核结果。
- 密码复用已有测试环境配置，仅存于环境secret及夹具进程环境，不写入导出集合。

预执行保护要求runRegistrationReviewRegression=true、registrationReviewFixtureReady=true、
rrHost=http://127.0.0.1:18084；跳过请求不能算通过。

## 分组执行结果

| 分组 | 内容 | 实际HTTP请求 | 通过断言 |
| --- | --- | ---: | ---: |
| A | 四接口、角色/归属、分页、快照版本、输入、通过/驳回和反馈 | 106 | 177 |
| B | 两名管理员竞争一条待审核记录 | 13 | 26 |
| C | 企业先编辑、旧审批等待后拒绝 | 12 | 23 |
| D | 旧版先通过、后续新版仍待审 | 11 | 22 |
| F0 | 创建第二张表故障的待审版本 | 6 | 11 |
| F | 企业表更新失败，审核记录回滚 | 7 | 14 |
| G | 解除故障后正常提交 | 7 | 13 |
| 合计 | | 162 | 286 |

执行前已从主集合读回完整模型，核对156条请求与精确脚本。
实际通过Postman MCP runCollection执行相同内容的分组集合，避免重跑整个主集合中的其他业务或不同数据前提。

分组集合ID：

- A：56227183-1f5ffd5f-9d84-4801-b6e4-26b15bc49344。
- B：56227183-704ae878-47a6-4902-9c26-1bec1d2a1976。
- C：56227183-921c8db0-c2cd-4e6f-8cee-2a09b60384c8。
- D：56227183-aaa56cab-e071-4d9d-b76c-18c2165d51d0。
- F0：56227183-d00ba820-fa27-460c-9982-1cf05b6911d2。
- F：56227183-96d12aa0-d0b9-45c1-ba6f-9f27435356a4。
- G：56227183-ae59bae4-c95d-434f-a5f7-7f41833e8c56。

## 请求、响应和结果

| 场景 | 实际响应与检查 |
| --- | --- |
| 默认管理员待审列表 | code=200，仅当前版本且两处PENDING，列表为摘要字段，不混入QUALIFICATION |
| 查看旧版本详情 | code=200，snapshot仍为提交内容，currentVersion=false、reviewable=false |
| 审批旧版本或客户端版本不匹配 | code=500、data=null，不修改当前工商结果 |
| APPROVE | 记录APPROVED、企业VERIFIED、verifiedRegistrationVersion同步；真实审核人107及实际时间 |
| REJECT | 记录与工商状态REJECTED，意见保存；历史通过版本保留，当前核验标记false |
| 驳回意见缺失/纯空白，意见501字符 | code=500；500字符边界允许，首尾空白归一化 |
| decision缺失/null/UNKNOWN/数字0，版本缺失/负数 | code=500，没有消耗待审记录 |
| 请求伪造审核人、时间、企业ID及结果状态 | 系统字段取真实登录身份和服务端时间，无法伪造 |
| 重复审批或另一管理员改判 | code=500，首次审核人、意见、时间完整保留 |
| 原样保存被驳回资料 | 不新建记录；修改工商字段才生成新版本 |
| 普通简介修改 | 不重置已核验版本，不新增工商审核记录 |
| 待审/拒绝准入企业的工商审批 | 可以处理工商；企业准入和执照结果独立保持 |
| 企业历史传companyId=其他企业 | 仍返回登录企业自己的历史，不能切换归属 |
| 历史分页/状态筛选/超末页 | 总数准确，按提交时间及ID倒序，超末页items=[] |
| 非管理员调用管理入口、非企业调用本人历史 | code=500、data=null |
| 四个入口未登录 | HTTP401 |
| 不存在、非正数、材料类型审核ID | 拒绝工商查询或审批 |

详情、列表、历史及八字段snapshot均使用精确白名单断言。
企业历史不返回审核人账号；所有响应都不返回原始snapshotJson、文件存储引用或资质原件。

## 并发证据

B/C/D使用同一Postman沙箱内两个pm.sendRequest JSON请求，间隔100ms发起。
为使竞争可重复，在独立库的相应UPDATE中注入2秒延时，模拟持锁期间另一个请求到达。
每组结束均清除触发器；该延时不用于性能结论，也没有修改业务代码增加测试开关。

Postman断言实际业务码及请求重叠，另从后端RequestTraceFilter日志核对真实服务端区间：

| 分组 | 请求1（北京时间） | 请求2（北京时间） | 业务结果 |
| --- | --- | --- | --- |
| B | 审批8008，16:49:37.255～39.294 | 审批8008，16:49:37.366～39.300 | 恰好[200,500]；首次决定保留 |
| C | 编辑205，16:49:59.399～16:50:01.431 | 审批8009，16:49:59.496～16:50:01.436 | 编辑200、旧审批500；新版2待审 |
| D | 审批8010，16:50:17.885～19.957 | 编辑205，16:50:17.995～19.972 | 两者200；版本2通过，版本3待审 |

并发与汇总证据见target/registration-review-summary.json。
最终企业205为registrationVersion=3、verifiedRegistrationVersion=2、PENDING，不能被展示为当前版本已核验。

## 故障回滚和数据审计

F组在独立企业204的company表UPDATE前触发错误；后端日志确认到达Isolated second-write failure。
此时company_review_record更新已执行，但最终事务回滚。
比较故障前后完整企业、审核记录、福利和资质数据，内容完全一致；记录仍PENDING，审核人/时间/意见均为空。
解除故障后G组可以正常审批，同一版本最终VERIFIED。

最终审计确认：源smart_recruit核心表指纹不变；已核验版本均有对应APPROVED记录；
PENDING记录没有伪造审核人/时间；全部测试触发器已清除。
11条工商记录及1条材料审核夹具保留；企业201旧版本1和企业205旧版本1保留真实未处理事实。

## 重跑与交付

1. 编译docs/testing/CompanyProfileFixture.java与CompanyRegistrationReviewFixture.java，使用现有依赖classpath。
2. 用PROFILE_FIXTURE_PASSWORD提供测试密码，对全新、符合校验的独立数据库执行setup。
3. 启动独立端口18084，覆盖数据源URL及存储根目录；确认服务已启动。
4. 执行A；B前delay-review204、C前delay-profile205、D前delay-review205，每组后fault-off。
5. 执行F0，保存baseline并安装fault-company；执行F后fault-off、verify-rollback；执行G与audit。
6. 关闭仅属于本轮的隔离服务。重跑应使用新隔离库，不能在已审批数据上直接重新执行A。

- docs/postman/company-registration-review-regression.postman_collection.json：不含凭据的持久用例导出。
- docs/testing/CompanyRegistrationReviewFixture.java：隔离建库、第二管理员、延时/故障与审计夹具。
- target/registration-review-state.json：本机环境和运行集合ID。
- target/registration-review-result-*.json：已脱敏的Postman结果。
- target/registration-review-summary.json：各组统计及服务端真实重叠区间。
- target/registration-review-final-evidence.json：最终数据证据。

本轮只对新增审核闭环及关联企业保存/展示行为验收，没有重跑之前所有岗位、简历、照片和材料用例。
资质材料审核、管理员原件访问、前端审核页面联调属于后续阶段。
已有未提交改动、学习注释和暂存DTO均保留；未提交或推送Git。
