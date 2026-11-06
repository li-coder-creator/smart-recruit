# 岗位推荐：简历结构化输入接入与回归

日期：2026-11-01。工作区D:/project/smart-recruit，基线f2e1960；保留之前迁移、模型和契约的未提交改动。
本阶段完成简历声明、工作/实习经历、技能和求职意向的Mapper、Service、Controller及响应接入，并通过Postman MCP回归。
未修改前端、未提交或推送Git；岗位逐条能力保存、推荐评分及语义模型仍属于后续阶段。

## 1. 现有路径与接口行为

| 请求 | 本次实现 |
| --- | --- |
| POST /resume | 使用ResumeProfileCreateDTO，写入careerStage与formalExperienceStatus；旧请求默认UNKNOWN |
| PUT /resume/{id}/basic | 使用ResumeProfileUpdateDTO；未传新声明保留，显式UNKNOWN重置，null拒绝 |
| GET /resume/{id} | ResumeBasicVO返回声明及原有基础资料字段 |
| POST /resume/{id}/experience | 使用ResumeExperienceSaveDTO，保存类型、日期、至今；未知旧请求仍可保存展示内容 |
| PUT /resume/experience/{id} | 按字段存在性合并类型、日期和Boolean；锁父行后重读子项再校验 |
| DELETE /resume/experience/{id} | 同一父行锁；删除最后正式经历时将HAS_EXPERIENCE重置UNKNOWN |
| GET /resume/{id}/experience | 返回ResumeExperienceVO数组，保留null/false/true及原文 |
| POST /resume/{id}/skill、PUT /resume/skill/{id} | 使用ResumeSkillSaveDTO，持久化可选proficiency；旧更新省略时保留，显式null清空 |
| GET /resume/{id}/skill | 返回ResumeSkillVO数组，保留自由输入的名称和说明 |
| POST /resume/{id}/preference、PUT /resume/preference/{id} | 使用JobPreferenceSaveDTO，写入或清空directionCode；沿用薪资区间校验 |
| GET /resume/{id}/preference | 返回JobPreferenceVO，无意向仍成功返回null |
| GET /resume/{id}/detail | ResumeProfileDetailVO保持原JSON层级，以只读REPEATABLE_READ读取一致快照 |
| 子模块删除与DELETE /resume/{id} | 沿用原业务路径；本次相关删除与写入统一锁父行，简历删除仍清理原子表 |

新增字段本次只在简历相关入口开放；JobContentSaveDTO、JobRequiredAbility及岗位来源/版本事务尚未接入JobController。
完成度仍使用原业务口径，未把它替换成推荐dataCoverage，也未新增评分结果字段。

## 2. 代码职责与关键行为

### Controller：请求与返回契约

ResumeController改用专用DTO接收声明、经历、技能和意向，使用@Valid进行字段格式与长度校验。
详情与子项返回VO，保持resume、jobPreference、experiences、skills等原有层级；不会暴露内部suppliedFields。
归属取登录身份、路径及数据库，不把请求体当作所有权证明。

核对前端实际代码后发现toPreferencePayload会展开表单中的id。为兼容已有页面，JobPreferenceSaveDTO仅增加旧id的丢弃setter；它不能改变更新目标或归属。
resumeId、userId等不支持字段仍拒绝；回归用请求体id=999999证实实际更新目标仍是路径记录。
GlobalExceptionHandler补充JSON解析错误处理，枚举、非法null和未知字段返回明确的参数错误业务结果，不再统一显示系统异常。
沿用HTTP200/业务200成功、HTTP200/业务500拒绝以及未登录HTTP401，未新增另一套业务码。

### Service：跨记录规则和事务

1. 新增正式经历：UNKNOWN可自动变为HAS_EXPERIENCE；NONE遇到正式或未分类经历时拒绝，整个写入回滚。
2. 明确声明NONE前，检查所有现有经历；FORMAL或UNKNOWN阻止声明，只有实习/明确其他经历可以确认NONE。
3. 只有删除或改分类移除了最后正式记录，才把HAS_EXPERIENCE重置UNKNOWN；新增实习不会清掉用户“有正式经历但尚未补全记录”的申报。
4. 已分类经历必须有合法开始日期和明确至今状态；至今不允许结束日期，已结束必须有合法结束日期；工作日期不得晚于项目上海时钟的今天。
5. 旧UNKNOWN记录允许缺日期，periodText“至今”不自动推断Boolean。旧更新省略新增字段保留，明确清空按契约处理。
6. 技能保留输入原文，仅对单条明确“不会/不懂/不了解”与非空掌握程度的矛盾作确定性拦截；复杂语义不在本阶段猜测或评分。
7. 职位未变且方向省略时保留代码；旧客户端改变职位时清除过期代码。明确“前端开发/后端开发”与相反代码冲突时拒绝；宽泛职位不被自动猜成后端。

上述声明、经历、技能、意向写入与完成度刷新处于同一事务。校验失败或完成度SQL失败时，不留下子项或声明的部分更新。
项目时间复用现有applicationClock，避免在不同阶段混用服务器默认时区。

### Mapper：字段落库与锁后重读

ResumeMapper显式增加新列INSERT/UPDATE，枚举按字符串名称映射，Boolean保留三态。
写入统一先按登录归属SELECT resume ... FOR UPDATE，再查询/锁定真实子项；相关事务使用READ_COMMITTED。
对子项的初次查询只用于定位父简历，等待父锁后用独立FOR UPDATE语句重读，避免继续使用事务开始时的旧对象及MyBatis会话缓存结果。
零经验声明查询在取得父锁后执行，防止“检查时没有正式经历、提交时另一请求已添加”的跨表竞争。
Mapper不负责猜测身份、计算工龄、调用模型或判断业务状态。

## 3. 测试前核对与持久化位置

- 测试对象：本阶段简历相关入口，包含成功、拒绝、重读、权限、兼容、回滚和并发。
- 数据库：新建smart_recruit_resume_inputs_1793500077192，复制开发库表结构，用户及简历均为合成夹具；不复制真实业务行作为回归输入。
- 独立服务：http://127.0.0.1:18089，使用本次独立编译产物；未操作用户日常服务。
- 身份：101、102两个求职者，103企业角色、104管理员；每阶段通过登录及/user/info确认身份。
- 故障与并发夹具仅允许写入受名称白名单限制的隔离库；触发器不在开发库创建。
- 源库smart_recruit所有基础表在回归前后计算规范化数据SHA-256，对比一致。

所有133条请求均保存于SmartRecruit API - Optimized Regression / 04 Resume / 推荐输入接入回归 2026-11-01。
执行前通过MCP读取主集合，逐条核对请求方法、URL、JSON请求体和断言脚本与执行集合完全一致。
运行用分阶段执行集合承载相同请求；没有用curl、fetch或鼠标操作代替Postman MCP接口测试。

| 阶段 | 请求数 | 断言数 | 结果 |
| --- | ---: | ---: | --- |
| A 功能、参数、权限与旧请求兼容 | 115 | 146 | 全部通过 |
| B 完成度更新故障回滚 | 7 | 11 | 全部通过，JDBC再次核对故障前后记录一致 |
| C 故障解除后的恢复保存 | 5 | 7 | 全部通过 |
| D 父锁并发与锁后重读 | 10 | 17 | 全部通过，含4次pm.sendRequest并发子请求 |
| 合计 | 137 | 181 | 失败请求0、失败断言0 |

D阶段持久请求本身6条，另有4次脚本内HTTP调用，故133条持久请求实际产生137次HTTP调用，不重复计算这些子请求。
另有11项RecommendationInputContractTest通过，未启动Spring或数据库；不与181条Postman断言混合计算。

首轮A运行前12个请求的19个断言通过，第13个请求发生传输中断，断言失败0、请求失败1；服务日志同一时段有线程饥饿/时钟跳变警告。
保留该轮记录，完整重跑A后115个请求全部通过。上表仅统计最终通过的完整运行，不掩盖首轮中断，也不将其算作业务断言通过。

## 4. 已核验的请求与响应字段摘录

以下响应为通过断言核验的字段摘录，不是原始完整响应转储；完整请求及预期脚本见集合导出。

```json
{
  "title": "推荐输入回归",
  "careerStage": "NEW_GRAD",
  "formalExperienceStatus": "NONE"
}
```

POST /resume返回HTTP200、业务200及自增ID；随后GET /resume/{id}/detail核验：

```json
{
  "code": 200,
  "data": {
    "resume": {"careerStage": "NEW_GRAD", "formalExperienceStatus": "NONE"},
    "experiences": [],
    "skills": []
  }
}
```

| 请求场景 | 响应及重读结果 |
| --- | --- |
| NONE下POST正式经历 | 业务500、data=null；重读完整简历与操作前一致 |
| 实习type=INTERNSHIP、isCurrent=false | 保存成功，GET返回INTERNSHIP及false，未误作null或正式经历 |
| 正式经历改至今且endDate=null | 保存成功，GET返回true与null；只改至今而保留结束日期被拒绝 |
| 技能proficiency省略 / 显式null | 前者重读保留FAMILIAR，后者重读为null |
| 旧意向id伪造及省略方向 | 仍修改路径记录；职位不变保留BACKEND，改宽泛职位后方向null |
| 完成度更新被触发器拒绝 | 技能新增、正式经历新增及自动HAS更新均回滚；记录与更新时间与基线一致 |
| 故障解除后重试 | 业务200；技能和经历各一条，HAS_EXPERIENCE，完成度20 |
| FORMAL与NONE实际重叠竞争 | 一次业务200、一次业务500；最终HAS与一条FORMAL一致 |
| 同一经历实际重叠删除 | 一次成功、一次拒绝；最后无经历，声明UNKNOWN |

并发用例以隔离触发器延迟制造实际重叠，并断言请求时间区间重叠；不是将两个串行请求称为并发。
数据审计还检查不存在NONE与正式/未分类经历共存的记录。

## 5. 证据、清理与复跑

- [Postman集合导出](postman/resume-input-regression.postman_collection.json)：无真实密码或JWT，使用环境变量引用。
- [回归结果](postman/resume-input-regression.results.json)及[集合标识](postman/resume-input-regression.manifest.json)。
- [MCP执行脚本](postman/resume-input-regression.cjs)及[隔离夹具](postman/ResumeInputFixture.java)。
- 本机明细：target/resume-input-fixture-1793500077192/，保存各阶段结果、首轮A中断结果、回滚基线、源库指纹及隔离服务日志；JWT已脱敏。
- Postman测试环境的runResumeInputRegression和rsFixtureReady已关闭并通过MCP重读确认。
- 故障和延迟触发器全部移除；隔离服务21772在命令行核对库名与端口后停止；开发库数据指纹不变。

隔离库保留供复查。合成密码在忽略目录和专用Postman环境中，不在文档或集合导出内公布。
准备集合和启动服务曾并行写状态文件，清理时从启动日志恢复PID；脚本已改为合并状态，停止服务前仍核对实际Java命令行，避免影响其它进程。

复跑须先创建新隔离夹具，不能在C已写入后的903记录上重复运行B并误判回滚。
脚本支持RESUME_INPUT_STATE_FILE指定新的target内状态路径，复用manifest里的持久请求；先prepare和verify，再start，避免并行操作同一状态文件。
顺序：setup-local → setup-environment → prepare → verify → start → A → baseline → fault-on → B → verify-rollback → fault-off → C → delay-on → D → delay-off → audit → guard-off → stop。
执行HTTP阶段必须调用MCP；普通Node网络受沙箱限制时需在授权环境运行本地MCP，不能因此切换到直接HTTP测试。
本次继续使用target/recommendation-build独立构建目录，未删除原target/classes产物、改ACL或主pom.xml。

## 6. 当前阶段结论和面试重点

简历结构化输入的后端保存与重读已接通，旧请求兼容、角色与归属、故障回滚及两组并发均通过本次验收。
前端新增求职阶段、类型/至今、程度、方向控件尚未改造；本次不能认定前后端联调已完成。
岗位逐条能力来源/版本事务、五维计算器、Embedding模型及校准、推荐查询接口仍待实现。

可据此练习解释：为什么未传和null不同、为什么先锁父行再重读子项、READ_COMMITTED与读详情快照各自解决什么问题、完成度更新失败为什么必须回滚全部写入。
可准确描述为“实现推荐所需的简历结构化输入，使用父行锁维护跨表声明一致性，并通过Postman持久回归覆盖权限、兼容、回滚和并发”；没有推荐效果数据，不填写准确率提升或吞吐量指标。
