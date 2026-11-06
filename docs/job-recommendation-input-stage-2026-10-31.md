# 岗位推荐：迁移、枚举、实体与DTO/VO交付记录

日期：2026-10-31。工作区：D:/project/smart-recruit，main，修改前基线f2e1960。
本文件保留该日阶段快照；2026-11-01简历相关保存、重读及Postman回归已完成，后续状态见[接入报告](job-recommendation-resume-integration-2026-11-01.md)。
本阶段由用户授权执行迁移并编写枚举、实体和DTO/VO；未提交或推送Git，未修改前端。

## 1. 完成与尚未完成的边界

| 工作 | 状态 |
| --- | --- |
| 开发库smart_recruit迁移：5张原表新增9列、1张能力子表 | 已执行，默认值及旧列一致性核验通过 |
| 6个业务枚举、5个现有实体扩展、JobRequiredAbility实体 | 已实现，数据库字符串与枚举名称对应 |
| 7个DTO、7个VO、字段存在性支持及严格整数版本解析 | 已实现并通过纯契约测试 |
| 新DTO替换Controller参数、新增字段写入与子表读写 | 未接入，属于下一阶段Mapper/Service/Controller任务 |
| 声明与经历一致性、岗位能力来源切换和版本事务 | 尚未实现，不能只换Controller参数就开放填写 |
| 五维计算器、语义模型与校准、推荐接口及前端 | 尚未实现 |

本阶段新增DTO使用独立名称，复用原有基础表单DTO，保留当前Controller参数类型。
这样需要新增业务规则的请求不会因为本次增加DTO就被当作已支持；现有接口仍只承诺原保存字段。
实体新增属性用于读库和后续持久化，旧Mapper的显式INSERT/UPDATE尚未新增这些写入列。
禁止在业务接入前让前端向旧接口提交新字段并把业务码200解释为新字段已经落库；下一阶段必须验证保存后重读结果。

## 2. 数据库执行记录

迁移前核对配置连接的smart_recruit、MySQL 8.0.13、InnoDB及新列不存在。
执行时未发现本机Java服务进程；迁移前后两次检查没有其它活动数据库查询，并逐行核验旧数据。不声称这等同于永久阻止外部客户端写入。
使用迁移专用GET_LOCK防止同类迁移并发，不将它当作应用写入锁；DDL锁等待限制10秒。

实际执行[原草案](sql/2026-10-25-job-recommendation.draft.sql)B部分的5个ALTER及1个CREATE，未执行任何业务数据回填。
按开始声明、经历、技能、意向、岗位及新表的顺序逐步记录结果，最终manifest状态COMPLETE。

| 表 | 新字段 |
| --- | --- |
| resume | career_stage、formal_experience_status |
| resume_experience | experience_type、is_current |
| resume_skill | proficiency |
| job_preference | direction_code |
| job | direction_code、required_abilities_source、required_abilities_version |
| job_required_ability（新表） | id、job_id、raw_text、ability_name、proficiency、sort_order、create_time、update_time |

旧记录默认UNKNOWN或NULL，旧岗位来源LEGACY_TEXT、版本0；能力子表为空。
未把无经历解释为零经验，未拆分旧技能字符串，未推断求职身份或方向。

### 备份与一致性

私有备份持久归档至storage/migration-backups/2026-10-31-recommendation-inputs/，受/storage/忽略规则保护，不提交版本库。
包含schema-before.sql、data-before.sql、old-columns.json、old-rows.json和manifest.json；含私人数据，不放入Postman导出或公开报告。
原运行副本保存在target/recommendation-migration-2026-10-31-attempt-2/；持久归档避免target清理导致唯一备份丢失。
备份的结构及INSERT是恢复资料，不可直接导入现有行；恢复前仍需确定目标和外键依赖。

| 原表 | 迁移前行数 | 原列核验 |
| --- | ---: | --- |
| resume | 6 | 全部原列逐行一致 |
| resume_experience | 0 | 原列列表和空结果一致 |
| resume_skill | 0 | 原列列表和空结果一致 |
| job_preference | 0 | 原列列表和空结果一致 |
| job | 13 | 全部原列逐行一致 |

一致性比较按原列清单与主键排序，不把新增默认列加入旧指纹；同时记录SHA-256摘要，而非只比较COUNT(*)。
首次运行停在备份日期序列化，未创建隔离库或执行源库DDL；失败记录保留于target/recommendation-migration-2026-10-31/。
修正TemporalAccessor处理后第二次完整执行成功，随后再次核验原列指纹一致。

### 隔离验证

隔离库smart_recruit_recommendation_migration_20261031保留供复查；父表以CREATE TABLE LIKE克隆并复制当时行，新增DDL与源库相同。
LIKE克隆不代表复制了所有原表外键，故此验证不能冒充原业务调用链或全部约束回归。
先验证迁移默认值与旧列一致，再在隔离事务中创建临时能力、经历、技能和意向验证映射；全部事务回滚。
验证新子表CASCADE删除、无父岗位插入被外键拒绝；源库只读取，未使用业务HTTP接口。
验证后确认源库和隔离库旧列摘要均与备份一致，子表仍空、新字段仍为迁移默认。

可审查工具：[迁移执行器](sql/RecommendationInputMigration.java)、[实体映射与外键验证器](sql/RecommendationInputVerification.java)。
执行器硬编码源库/隔离库，检查已有备份和目标列，拒绝重复迁移；不是通用生产迁移框架。
DDL有隐式提交，失败时保留完成步骤，不提供自动DROP回滚。

## 3. 枚举与实体

| 枚举 | 代码 |
| --- | --- |
| CareerStage | STUDENT / NEW_GRAD / SOCIAL / UNKNOWN |
| FormalExperienceStatus | NONE / HAS_EXPERIENCE / UNKNOWN |
| ExperienceType | FORMAL / INTERNSHIP / OTHER / UNKNOWN |
| AbilityProficiency | AWARE / FAMILIAR / PROFICIENT / EXPERT |
| JobDirection | BACKEND / FRONTEND |
| RequiredAbilitiesSource | LEGACY_TEXT / STRUCTURED |

JSON输出name字符串，输入通过@JsonCreator精确识别，拒绝数字ordinal和未知代码。
MyBatis默认EnumTypeHandler按名称读取新列；异常存储值的读取降级仍需后续特征查询设计，不能假定EnumTypeHandler会自动返回UNKNOWN。
本次新列来自受控默认值，未发现非法代码。实体isCurrent使用Boolean保留null/false/true，不设置Java层默认假装完成读库。

## 4. DTO/VO实际文件与职责

| 新DTO | 设计对应项 |
| --- | --- |
| ResumeProfileCreateDTO extends ResumeCreateDTO | 创建资料增加声明，默认UNKNOWN，显式null拒绝 |
| ResumeProfileUpdateDTO extends ResumeBasicUpdateDTO | 未传保留，显式UNKNOWN重置；标记字段存在性 |
| ResumeExperienceSaveDTO | 类型、日期和至今标记；完整对象合并校验留给Service |
| ResumeSkillSaveDTO | 允许名称/自然语言＋说明＋可选程度；保留原文 |
| JobPreferenceSaveDTO | 复用旧意向字段并增加可空方向 |
| JobRequiredAbilityDTO | 只接受原文、可选名称/程度，不接受归属或派生code |
| JobContentSaveDTO extends JobSaveDTO | 能力数组、方向、预期版本；校验项数、合并长度和文本一致性 |

新DTO统一拒绝不支持字段，避免客户端伪造归属、来源、模型结果或内部存在性标记。
FieldAwareDTO以及少数继承旧DTO的存在性集合，保证省略、null、false与[]可分别判断；存在性集合不序列化输出。
requiredAbilities=null不替换，[]明确清空且必须有预期版本；版本只接收JSON整数，不接受小数截断、数字字符串、越界值，负数由Bean Validation拒绝。
单条及整体限长复用既有常量；列表上限20、换行符及初始版本定义于RecommendationInputConstants。
具体日期组合、零经验冲突、否定语义、方向变化及数据库版本比较仍须在Service处理，DTO通过不等于允许入库。
JobContentSaveDTO继承的applyTo不能替代能力事务处理，接入时须按来源规则合并，不能直接照搬旧保存路径。

新VO：ResumeBasicVO、ResumeExperienceVO、ResumeSkillVO、JobPreferenceVO、JobRequiredAbilityVO、ResumeProfileDetailVO、JobContentDetailVO。
响应保留旧JSON层级和原文，基础VO只复制已读取的实体。LEGACY_TEXT的能力数组为null；STRUCTURED加载后明确无项为[]，不凭空生成旧文本子项。
VO构造器不访问数据库、不推断学历、工龄或求职身份；新聚合VO尚未替换旧接口返回类型。

## 5. 编译与验证结果

- Maven独立目录编译205个main源码文件成功。
- RecommendationInputContractTest：11项，失败0、错误0；仅此测试类，不启动Spring或连接业务数据库。
- 实体映射验证9项通过，新增外键级联与孤儿拒绝通过；隔离夹具回滚且源库旧列指纹未变。
- 未执行业务HTTP请求或Postman回归；本阶段未接入新接口行为，不宣称推荐功能或新字段保存已验收。

构建说明：默认Maven仓库错误指向C:/.m2，显式指定现有用户缓存后可解析依赖；原target/classes中存在不可写产物，因此使用target/recommendation-build的临时POM指定原源码和独立输出。
未删除原构建目录、修改ACL、修改主pom.xml或下载替换依赖。首次契约测试中内部字段伪造实际被拒绝，修正了“应被忽略”的测试预期后11项通过。

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/李/.m2/repository' -f target/recommendation-build/pom.xml -Dtest=RecommendationInputContractTest test
```

临时POM和surefire报告位于target/recommendation-build/，不提交。SQL验证输出位于target/recommendation-mapping-verification-2026-10-31.json。

## 6. 下一阶段任务及验收

建议下一次只推进“简历声明、经历、技能与意向的Mapper/Service接入”，再单独推进“岗位能力来源和版本事务”。

1. 先补Mapper新增字段读写与父行锁，更新操作必须按存在性合并；受客户端控制的字段采用白名单。
2. 统一求职者归属检查、NONE冲突、正式经历增删后的状态更新、类型/日期与至今校验。
3. 这些规则与完成度刷新同一事务，成功后重读应与输入一致；旧请求省略新字段不清空已确认数据。
4. 再替换对应Controller参数及聚合VO；字段存在性错误和解析异常应给明确错误，不能只接受请求而丢失字段。
5. 通过Postman MCP将成功、校验、越权、事务及并发用例加入SmartRecruit API - Optimized Regression，记录身份、请求、响应和重读结果。

学习重点：枚举字符串与ordinal、Boolean三态、DTO存在性与数据库默认值、DTO与实体的职责、DDL与业务事务的区别、独立契约验证与接口验收的区别。
目前可准确写“完成推荐模块结构化输入迁移与契约定义，核验旧数据兼容性”；尚不能写推荐算法上线、模型准确率提升或接口全量回归通过。
