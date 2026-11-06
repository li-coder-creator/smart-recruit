# 岗位推荐：结构化字段契约草案

设计日期：2026-10-25。2026-10-31已完成迁移、枚举、实体和独立DTO/VO，保存业务尚未接入；见[阶段交付记录](job-recommendation-input-stage-2026-10-31.md)。
2026-11-01更新：简历声明、经历、技能和意向已接入并通过回归，见[接入报告](job-recommendation-resume-integration-2026-11-01.md)；岗位能力保存及评分仍待接入。下文“待接入”描述保留原阶段设计背景，以最新报告为准。
依据：[五维规则](job-recommendation-rules-v1.md)、[存量数据盘点](job-recommendation-data-audit-2026-10-24.md)。
配套：[迁移SQL草案](sql/2026-10-25-job-recommendation.draft.sql)、[实施与影响清单](job-recommendation-migration-plan.md)。

## 1. 目标、事实和选择

业务目标：为所选简历提供可解释的五维推荐，允许实习和自然语言能力完整展示，区分已知不满足、信息未知和不适用。
学历执行100/0；技能执行名称、别名、语义三层匹配。分数不证明真实能力或学历认证。

2026-10-25重新检查实际工作区及配置连接的smart_recruit元数据：MySQL 8.0.13、InnoDB、utf8mb4_0900_ai_ci。
本次只查询元数据，未重新盘点业务行；业务数量仍以2026-10-24快照为准。
元数据证据保存在忽略目录target/recommendation-schema-probe-2026-10-25.json，读取会话transaction_read_only=1。

| 当前实现 | 本次选择 | 原因 |
| --- | --- | --- |
| resume没有求职阶段或正式经历确认 | 直接给resume新增两列 | 与简历一对一，无需另建空画像表 |
| resume_experience已有日期与period_text | 新增经历类型和是否至今 | 保留原文；不从空结束日期推断至今 |
| resume_skill已有skill_name、description，一条一行 | 复用原字段，仅新增可选proficiency | 当前已能保存自由能力名称与说明，无需重复建技能表 |
| job.required_skills为VARCHAR(500) | 新增job_required_ability子表及来源、版本列 | 逐条能力要求作为新来源；兼容旧展示字符串 |
| category和expected_position自由填写 | job和job_preference各新增direction_code | 将筛选方向与展示名称分开，不把“技术”猜成后端 |
| 尚无Embedding模型及评分校准 | 先用版本化资源文件管理规则与别名 | 暂不建全量技能词库、向量库或评分结果表 |

本草案增量为5张现有表共9列、1张新子表。教育、薪资和城市本期复用现字段，由特征构建层规范化。

## 2. 字段表

### 2.1 resume：求职阶段与正式经历确认

| SQL字段 / JSON字段 | SQL / Java类型 | 默认、空值及写入规则 | 含义 |
| --- | --- | --- | --- |
| career_stage / careerStage | VARCHAR(16) / CareerStage | NOT NULL DEFAULT UNKNOWN；新请求可不填；显式null不接受，用UNKNOWN重置 | STUDENT在校、NEW_GRAD应届、SOCIAL社会求职、UNKNOWN未确认 |
| formal_experience_status / formalExperienceStatus | VARCHAR(16) / FormalExperienceStatus | NOT NULL DEFAULT UNKNOWN；显式null不接受 | NONE明确无正式经历、HAS_EXPERIENCE申报有、UNKNOWN未确认 |

- 身份由用户明确申报，不根据年龄、毕业日期或空经历列表推断。
- NONE必须没有FORMAL或UNKNOWN类型的经历；允许有INTERNSHIP和明确非正式的OTHER。
- HAS_EXPERIENCE不等于可立即算出年限；没有有效正式经历或有影响结论的未知经历，评分仍UNKNOWN。
- 添加FORMAL经历时，若状态NONE则拒绝并提示先修改确认状态；若UNKNOWN，可在同一事务更新为HAS_EXPERIENCE，返回最新简历状态。
- 删除或改分类后不再存在FORMAL经历，原HAS_EXPERIENCE重置为UNKNOWN；不能自动改为NONE。用户可再次明确确认。
- 对声明NONE、增删改经历等操作，统一先锁resume父行，再检查和写入，防止并发产生“确认零经验＋正式经历”。

### 2.2 resume_experience：工作与实习经历

| SQL字段 / JSON字段 | SQL / Java类型 | 默认与兼容 | 校验 |
| --- | --- | --- | --- |
| experience_type / experienceType | VARCHAR(16) / ExperienceType | NOT NULL DEFAULT UNKNOWN | FORMAL正式、INTERNSHIP实习、OTHER明确非正式的其他经历、UNKNOWN待确认 |
| is_current / isCurrent | TINYINT(1) / Boolean | NULL DEFAULT NULL | true至今、false已结束、null未确认；不能用boolean丢失未知状态 |
| start_date / startDate（已有） | DATE / LocalDate | 旧值保留 | 新填写且类型已明确的经历需有开始日期，不晚于asOfDate |
| end_date / endDate（已有） | DATE / LocalDate | 旧值保留 | isCurrent=true必须为空；false必须填写且不早于开始、不晚于asOfDate |
| period_text / periodText（已有） | VARCHAR(50) / String | 保留原展示文本 | 不作为计算工龄的证据，不自动将“至今”写成true |

新前端新增经历需明确类型（FORMAL/INTERNSHIP/OTHER）和是否至今；日期按上表校验。
兼容入口仍允许旧请求缺少新字段：创建时UNKNOWN/null，更新时未传新字段保留原值。
旧UNKNOWN经历可以只修改展示内容并保留不完整日期；一旦明确类型，就应补齐类型对应的日期信息。
更新时先合并未传字段，再校验完整对象。例如把已结束改为至今，必须显式传isCurrent=true、endDate=null。
旧客户端对已明确类型的记录漏传新字段可以兼容，但不能绕过已有类型的日期或NONE约束。
只将FORMAL有效区间计入正式工龄；实习、其他经历和项目继续展示。重叠区间合并等算法沿用五维规则。

### 2.3 resume_skill：自由能力输入

| SQL字段 / JSON字段 | 类型与限制 | 来源及边界 |
| --- | --- | --- |
| skill_name / skillName（已有） | DB VARCHAR(100)；现有API非空、最多50字符 | 用户输入的能力名称或短描述；允许“熟悉Java”“跨部门沟通”，不强迫只输入标准词 |
| description / description（已有） | VARCHAR(500)；可空 | 用户补充能力、职责和成果原文；与名称一起作为匹配证据 |
| proficiency / proficiency（新增） | VARCHAR(16) / AbilityProficiency；NULL DEFAULT NULL | AWARE了解、FAMILIAR熟悉、PROFICIENT熟练、EXPERT精通；null未填 |
| sort_order / sortOrder（已有） | INT，非负 | 沿用当前默认排序与校验 |

不把skill_name批量改成标准名称，不新增长度更窄的列；现有DB/API长度差异保留并记录。
旧数据中超过现有API50字符的名称继续展示，后续编辑按现有校验处理；本阶段不截断、不批量重写。
如名称“不了解Java”与proficiency=EXPERT矛盾，不能用选项覆盖否定；新增写入拒绝可确定的矛盾，其余提取不确定项返回待确认。
程度选项是申报属性，不直接映射加分，也不通过enum.ordinal比较。其可比规则在语义校准阶段明确。
类型技术/通用可在派生特征中标注，本期不新增必填分类列，避免通用能力被拒绝保存。

### 2.4 方向代码

| SQL字段 / JSON字段 | 所属表 | 类型与空值 | 规则 |
| --- | --- | --- | --- |
| direction_code / directionCode | job | VARCHAR(32) NULL / JobDirection | 本期接受BACKEND、FRONTEND；null未确认，不表示不限 |
| direction_code / directionCode | job_preference | VARCHAR(32) NULL / JobDirection | 与岗位使用同一套代码，不根据任意职位文本猜测 |

初始精确兼容映射仅“后端开发”→BACKEND、“前端开发”→FRONTEND；宽泛“技术”“技术研发”保持未知。
无存储代码时可在读取特征时使用版本化精确映射，不执行本次SQL批量回填。
本次查询临时选择方向优先于简历默认；两者均无法确认时要求补选，不偷偷默认BACKEND。
现有category、expected_position用于展示，不删除。旧客户端修改这两个文本且未传directionCode时，应清空原代码后重新按精确映射构建特征，避免保留过期方向；文本未变则保留。
显式directionCode可与宽泛类别同时存在；发现明确类别与代码矛盾则拒绝，不任意取其一。
新增方向需同时扩展枚举、options与映射版本，不能将所有不认识的方向并到OTHER参与匹配。

### 2.5 job：能力要求来源与并发版本

| SQL字段 / JSON字段 | 类型 | 默认与权限 |
| --- | --- | --- |
| required_abilities_source / requiredAbilitiesSource | VARCHAR(16) / RequiredAbilitiesSource | NOT NULL DEFAULT LEGACY_TEXT；LEGACY_TEXT或STRUCTURED，由服务端控制 |
| required_abilities_version / requiredAbilitiesVersion | BIGINT / Long | NOT NULL DEFAULT 0；服务端递增，请求只用于提交预期版本，不允许任意赋值 |
| required_skills / requiredSkills（已有） | VARCHAR(500) / String | 旧模式保存用户原文；新模式保存服务端生成的兼容展示文本 |

不直接以“子表没有行”推断旧模式，因为结构化草稿可能明确清空列表。
版本号用于能力要求保存的并发检查，不代表整份岗位已有完整乐观锁；普通岗位内容并发控制维持现有范围。

### 2.6 新表job_required_ability

| SQL字段 / JSON字段 | SQL / Java类型 | 规则 |
| --- | --- | --- |
| id / id | BIGINT / Long | 自增服务端生成；批量替换后可能变化，客户端不依赖稳定ID |
| job_id / jobId | BIGINT / Long | 非空外键；取已鉴权岗位ID，不接收请求体归属 |
| raw_text / rawText | VARCHAR(500) / String | 必填、trim后非空；保存该条岗位要求原文，不以模型输出覆盖 |
| ability_name / abilityName | VARCHAR(50) / String | 可空，用户明确的能力名称；不能绕过rawText中的否定或范围约束 |
| proficiency / proficiency | VARCHAR(16) / AbilityProficiency | 可空；要求程度，不直接作为分数 |
| sort_order / sortOrder | INT / Integer | 服务端按数组顺序生成0起序号，不信任客户端顺序值 |
| create_time / createTime | DATETIME / LocalDateTime | 服务端数据库写入时间 |
| update_time / updateTime | DATETIME / LocalDateTime | 服务端数据库更新时间 |

新请求字段requiredAbilities为数组；每项rawText必填，abilityName/proficiency可选。
单个岗位最多20项，建议命名REQUIRED_ABILITY_MAX_ITEMS；兼容展示为rawText按换行连接，合计不得超过现有SKILLS_MAX_LENGTH=500，超限明确拒绝，禁止静默截断。
重复或明确别名项在评分特征层去重，存储时保留用户描述；不同程度或约束的两项不能只因同名就误合并。
查询索引(job_id,sort_order,id)；不建立文本唯一索引，也不为本期未使用的向量查询建索引。
外键引用job(id)，ON DELETE CASCADE：草稿按既有规则物理删除时一并删除纯从属要求，不给旧删除路径增加RESTRICT故障。
不改变草稿可删、已发布岗位保留历史的既有业务规则。

## 3. 新旧岗位能力的唯一来源规则

新请求中requiredAbilities省略或null表示不替换；[]表示明确清空。服务端不得把null默认成[]。
旧requiredSkills字段也要保留“未传”和“明确null”的区别。

| 当前来源 | 请求 | 保存行为 |
| --- | --- | --- |
| 新岗位或LEGACY_TEXT | 未提供requiredAbilities | 按原requiredSkills保存，保持LEGACY_TEXT；新岗位初始版本0 |
| 任意来源 | 提供requiredAbilities数组及正确预期版本 | 锁job父行；替换子表、生成兼容文本、切换STRUCTURED、版本+1，同一事务提交 |
| STRUCTURED | 未提供数组，requiredSkills未传或与当前兼容文本完全一致 | 保留子表、来源和能力版本；可更新其它允许内容 |
| STRUCTURED | 未提供数组，但requiredSkills明确更改或清空 | 拒绝并提示使用新版能力编辑，防止旧页面改坏唯一来源 |
| LEGACY_TEXT | 已有岗位的旧文本发生更改 | 在父行锁内更新文本并版本+1，防止过期页面用旧版本切换结构化来源 |
| 任意来源 | 数组与同时提交的requiredSkills不一致 | 拒绝；客户端可以省略requiredSkills，交由服务端生成 |

- 新岗位携带数组时预期版本为0；已有岗位先读详情取得版本。数组替换缺少预期版本或版本过期均拒绝。
- 来源STRUCTURED时从子表构建特征，即使为空也不回退旧字符串；LEGACY_TEXT才解析旧字符串。
- 草稿可保存空数组，此时兼容文本NULL、技能维度UNKNOWN；直接发布和草稿发布必须具有至少一条非空必备要求，不能退化现有发布校验。
- 草稿发布不强制一次性改成结构化；旧文本合法时仍可按旧发布规则发布，不伪造已解析能力。
- 保存数组与岗位主表内容、版本和操作记录必须在同一事务；模型调用不放在写事务内。失败则全部回滚，提交后失效岗位详情缓存。
- 旧模式复杂原文无法完整解析时返回UNKNOWN。明确列表“Java, Spring Boot”可在特征层解析，不在迁移中按逗号批量生成子表。

## 4. 原始、规范化与模型派生字段

以下属于后续特征对象/匹配响应与配置，不由本次SQL创建业务列：

| 对象 | 字段建议 | 约束 |
| --- | --- | --- |
| AbilityFeature | sourceType、sourceId、rawText、name、canonicalCode、proficiency、constraints、warnings | 服务端构建；canonicalCode允许空，新能力可比较；保留原文片段 |
| AbilityMatchVO | requirementText、candidateText、method、decision、matchScore、similarity、reason、evidence | 不确定score=null；相似度和匹配分分开；证据必须来自输入 |
| RuleVersionVO | scoringRuleVersion、normalizationVersion、aliasVersion、extractorVersion、embeddingModelVersion、calibrationVersion | 服务端返回；未启用语义时模型/校准版本可空并说明，不伪造版本 |
| 规则资源 | 精确别名、方向映射、明确不兼容项 | 随代码版本提交，启动加载为不可变配置；不提供公众修改接口 |
| 语义配置 | provider/model、固定模型版本、超时、批次、校准映射 | 外部密钥仅放环境配置；模型及映射未选定前语义开关关闭 |
| 可选派生缓存 | 必要文本哈希、预处理版本、模型版本→向量 | 本阶段不落库、不存总分；评分缓存还须包含规则/校准版本 |

名称/别名已确定的结果无需调用模型；语义未配置只影响需要该层的条目，不能使全部名称匹配失败。
需要语义判断但服务未就绪时UNKNOWN，并标注配置或服务原因；技能正权重下总分null。
评分基于同一读取快照构建的特征，离开数据库事务后再调用模型；响应包含asOfDate与各版本，不承诺跨请求固定实时数据。

## 5. DTO、VO与实体任务及阶段命名

2026-10-31阶段实现使用独立的ResumeProfileCreateDTO、ResumeProfileUpdateDTO、JobContentSaveDTO复用旧DTO，及ResumeProfileDetailVO、JobContentDetailVO扩展响应契约。
下面原设计中的“扩展ResumeCreateDTO / ResumeBasicUpdateDTO / JobSaveDTO及详情VO”由这些独立契约承接。
2026-11-01已接入简历和岗位Controller、Service与Mapper：岗位创建、保存草稿及内容更新使用JobContentSaveDTO；企业与求职者岗位详情返回JobContentDetailVO，路由保持不变。

| 新增或扩展对象 | 职责 |
| --- | --- |
| CareerStage、FormalExperienceStatus、ExperienceType、AbilityProficiency、JobDirection、RequiredAbilitiesSource | 命名枚举；数据库和JSON用明确字符串，不用ordinal；未知客户端代码拒绝 |
| Resumebasic、ResumeExperience、ResumeSkill、JobPreference、Job、JobRequiredAbility | 映射本草案字段；实体后续实现，不直接增加全部请求可写权限 |
| ResumeCreateDTO / ResumeBasicUpdateDTO | 允许写两项简历声明；创建默认UNKNOWN；更新保留未传值 |
| ResumeExperienceSaveDTO | 替换经历接口直接接收实体；只开放业务字段，保留新增字段的请求存在性 |
| ResumeSkillSaveDTO / JobPreferenceSaveDTO | 替换直接接收实体；只允许对应业务字段，不接收归属、时间和派生标准code |
| JobSaveDTO＋JobRequiredAbilityDTO | 增加directionCode、requiredAbilities、requiredAbilitiesVersion；来源字段只读 |
| ResumeDetailVO及子项VO、JobDetailVO | 原JSON结构保持，追加声明、经历类型、程度、能力数组、来源与版本 |

更新新字段约定：未传=保留；明确null仅可清空可空字段，UNKNOWN用于重置声明；[]用于清空列表。
普通nullable DTO无法区分省略与null，后续需用带字段存在性标记的DTO setter等方式实现，不依赖“全部Bean复制”。
沿用原业务码200/500及既有鉴权响应；本草案不发明另一套错误码，后续回归分别断言HTTP与应用层结果。

下面是新增字段的JSON片段，不是可直接提交的完整JobSaveDTO；原有标题等必填字段仍须提供。

```json
{
  "careerStage": "NEW_GRAD",
  "formalExperienceStatus": "NONE"
}
```

上面用于简历基础资料声明；以下用于已有岗位能力替换，其中版本来自最近读取的详情：

```json
{
  "directionCode": "BACKEND",
  "requiredAbilitiesVersion": 0,
  "requiredAbilities": [
    { "rawText": "熟悉Java", "abilityName": "Java", "proficiency": "FAMILIAR" },
    { "rawText": "能够按规范编写过程文档" },
    { "rawText": "能协调产品和研发推进需求" }
  ]
}
```

客户端不提交requiredAbilitiesSource、子项归属、标准code或模型版本。明文能力描述进入现有请求日志时，也需核对日志范围，避免默认记录整份简历。

## 6. 验收口径

- 旧数据文本、日期、归属和主键不变；只增加默认UNKNOWN/null、来源LEGACY_TEXT和版本0。
- 所有枚举、字段长度、显式清空行为与SQL一致；API校验采用已有常量或新增具名常量。
- 只有实习且明确NONE：展示实习，正式经验0；未确认NONE：经验UNKNOWN。
- 学历低于要求为SCORED/0，不因本次结构化工作回退到50分。
- 新技术无需事先入字典；通用能力保留原文；不能用高相似度覆盖否定或明确技术不兼容。
- 结构化空草稿不读取遗留字符串；发布不允许空要求；并发过期版本不得覆盖新列表。
- 简历输入接入验收见`job-recommendation-resume-integration-2026-11-01.md`；岗位能力接入验收见`job-recommendation-job-integration-2026-11-01.md`。本阶段属于推荐输入准备，不代表推荐评分或语义效果已经实现。
