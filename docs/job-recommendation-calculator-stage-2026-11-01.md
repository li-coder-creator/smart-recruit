# 推荐特征与确定性计算层（2026-11-01）

## 1. 业务目标、范围与结论

本阶段把已保存的简历、岗位信息转成可追溯特征，计算技能、薪资、城市、经验、学历五个分项，再按相对权重汇总。结果属于偏好匹配程度，不是录用概率或投递资格。

已交付：不可变特征、精确规范化资源、名称/别名匹配、语义扩展接口与未配置回退、五维计算、缺失值与权重处理、稳定排序比较器及独立单元测试。

本阶段没有新增HTTP路由、Mapper查询、数据库迁移或写入，也没有改前端、提交或推送Git。此前未提交的简历与岗位改动保留。计算输入是调用方已经加载的实体列表；未来应用服务负责权限、读取快照与岗位资格过滤。

语义模型及程度比较尚未选型、校准，实际提供者是DisabledSemanticAbilityMatcher。需要语义或未校准程度判断时返回UNKNOWN，模型与校准版本为null。这里只认定确定性计算子阶段完成，不认定完整三层技能匹配或推荐接口已上线。

## 2. 代码入口与职责

代码位于`src/main/java/com/recruit/smartrecruit/recommendation/`：

| 对象 | 职责 |
| --- | --- |
| RecommendationFeatures | 内部不可变快照，保留必要原文、日期、确认状态、来源与版本 |
| RecommendationFeatureBuilder | 把现有实体转换成候选人和岗位特征，不查库、不写库 |
| FeatureNormalizer | 正式工龄区间合并、学历背景、经验/学历要求解析 |
| AbilityFeatureExtractor | 保守提取简单能力名称、否定和程度；复杂表达保留待确认 |
| RuleDictionary | 启动读取版本化JSON，构建不可修改的精确别名映射 |
| SkillMatcher | 约束检查、NAME/ALIAS与语义扩展调用、逐项证据、去重及汇总 |
| SemanticAbilityMatcher | 未来真实语义提供者的边界，分数与相似度分开 |
| DimensionCalculator | 薪资、城市、经验、学历四项计算 |
| WeightedScoreCalculator | 处理三种状态、归一化、覆盖率、有效权重与贡献 |
| RecommendationCalculator | 编排单个岗位的完整计算，返回规则版本及asOfDate |
| RecommendationScore | 结果与按未舍入分数、发布时间、ID排序的比较器 |
| RecommendationWeights / RecommendationRules | 三套预设、合法自定义权重、命名公式常量和精度 |

控制层未来负责请求校验和身份入口，应用Service负责业务资格与数据加载，Mapper只查所需数据；上述计算器承担可独立验证的领域规则。不能把评分公式塞进Controller，或在Mapper中混合身份、状态迁移与模型调用。

## 3. 输入如何变成统一特征

### 正式工龄

只累计FORMAL；INTERNSHIP、OTHER保留在简历展示，计算正式月数时排除。UNKNOWN经历可能影响结论时，actualMonths=null，但保留已确认的confirmedMonths、区间和警告。

先校验开始、结束、isCurrent与统一asOfDate，再把结束日转换为包含当天的半开区间。排序合并重叠或首尾相接区间，最后逐区间使用完整自然月数，零散天数不跨空档拼接。

例如2024-01-01～2024-12-31和2024-07-01～2025-06-30合并为18个月。岗位最低36个月，差18个月：`max(0,100-18×45/12)=32.50`。

NONE且没有正式/未分类记录时才明确为0；无记录或仅实习但未确认NONE时不能推断为0。NONE与正式经历冲突时为UNKNOWN。periodText“至今”不能替代isCurrent声明。

### 学历背景

使用明确业务rank，不用ordinal。选择已开始、日期有效的最高可识别学历；未来计划教育不参与。在读、填写日期已结束、阶段未知分别保留，不能据此声称毕业证已核验。

未确认记录保留可能达到的等级：已知本科且硕士开始日期缺失，要求本科仍可100；要求硕士为UNKNOWN；要求博士则不能仅凭这条“最多硕士”的未知记录改成可能达标。

岗位学历明确不限时100；可确认达到要求100、不足0；关键证据未知为null。已取得学历、必须毕业等额外条件不被静默删掉。

### 能力、方向与城市

STRUCTURED只使用实际加载的子表，空数组不会回退requiredSkills。未加载子表传null属于调用方错误，避免把未查询当成空数据。LEGACY_TEXT只对明确名称枚举拆分；自然语言复合句或异常列表保留原文待确认。

原文不改写，规范名称作为派生字段。提取器可以处理“会Java”“熟悉Java”、新技术同名输入、C++、C#，不会按空格拆开Spring Boot，也不会用Java子串命中JavaScript。

方向只采用明确代码或“后端开发/前端开发”精确映射；宽泛“技术研发”不猜方向。代码与明确文本冲突时输出未知及警告。期望城市来自所选简历的求职意向，不从currentCity补值。

精确资源为`src/main/resources/recommendation/normalization-v1.json`，含版本、城市、方向、技能别名和待确认技术关系。当前城市表覆盖上海、深圳、东莞、北京、广州、杭州、南京、苏州、成都、武汉、西安及明确别名；其它值UNKNOWN，不宣称具备全国地址解析能力。

## 4. 技能怎样匹配

1. 先检查确定的否定、矛盾和附加约束。明确“不会Java”对Java要求为0，语义层不能覆盖；同一简历既说会Java又说不会Java时为UNKNOWN。
2. 安全规范化后完整名称相同，且没有未解决约束，NAME=100；名称未入词典也可以命中。
3. 经资源确认的别名，例如SpringBoot与Spring Boot、Vue3与Vue 3，ALIAS=100。
4. 明确不同能力可判0；相关但未确定可比关系的Java/Spring Boot、JavaScript/TypeScript、Vue 3/JavaScript等保留UNKNOWN。Vue不能自动视为Vue 3。
5. 未解决项调用语义端口。当前禁用提供者返回SEMANTIC_NOT_CONFIGURED；提供者异常返回SEMANTIC_UNAVAILABLE，不伪装成技能不足。

岗位“精通Java”与候选人“了解Java”涉及未校准的程度关系，不能靠同名直接100；即使双方都写“熟悉Java”，本阶段也不替代尚未确定的程度规则。候选人明确不会Java时，无需程度校准即可确认其不满足精通Java。

对子项按规范能力与实际约束去重，合并时保留全部原始要求证据。不同程度或约束不误合并。选择每项的最佳可确认匹配：100分证据可排除其它无关未知项的影响；没有100且未知候选可能改变最佳结果时仍UNKNOWN。

所有必备项可判断才输出其平均分。任一关键项未知，技能总分null，已知逐项结果仍保留。空技能列表是UNKNOWN，不是0。

每项返回岗位原文、所选候选证据、匹配方式、decision、score、similarity与reason。相似度不能直接乘100；只有模型和校准版本齐备的语义提供者才可以贡献语义分数。日志仅记录提供者异常类型，不记录私密原文或外部异常消息。

## 5. 其它分项及加权计算

薪资以元/月比较：岗位月薪中点除以期望上限，最高100。面议且没有数值、或有效日薪/年薪明确不可比时NOT_APPLICABLE；区间缺失、倒置、面议与数值冲突、单位未知为UNKNOWN。

城市规范代码一致100，不同0；无法识别为UNKNOWN。经验按正式月数缺口扣分，应届/在校要求采用明确careerStage，不能由年龄或年限推断。

```text
D = 非NOT_APPLICABLE维度的请求权重总和
总分 = sum(已知分数 × 请求权重) / D
覆盖率 = SCORED维度权重 / D × 100
排除权重比例 = NOT_APPLICABLE权重 / 全部请求权重 × 100
```

UNKNOWN保留分母，暂贡献0；NOT_APPLICABLE才排除权重。技能正权重且未知时总分null，原因CORE_SKILLS_UNKNOWN。D=0为NO_COMPARABLE_DIMENSIONS，没有正权重已知分项为NO_SCORED_DIMENSIONS；两者不能冒充明确0分。

权重0的维度仍保留实际评估状态，标注ignored，不参与贡献和覆盖率。自定义五项必须完整、每项0～100整数、总和大于0。相对权重不必恰好凑成100。

内部采用BigDecimal与DECIMAL128精度，最后显示分数保留2位HALF_UP；权重比例显示6位、贡献显示4位。总分不使用已舍入比例或贡献再次累加。三个三分之一贡献显示合计99.9999时，原始总分仍可为100.00。

排序比较器按未舍入分数降序，再按发布时间和ID降序，null分数与null发布时间置末。它只提供比较规则；未来查询服务需要先对全部合资格候选评分、排序，再分页，不能误把先查一页再评分称为全局推荐。

## 6. 验收结果

| 测试类 | 用例数 | 结果 |
| --- | ---: | --- |
| FeatureNormalizerTest（含参数化样例） | 40 | 全部通过 |
| SkillMatcherTest（含参数化样例） | 39 | 全部通过 |
| RecommendationCalculatorTest | 28 | 全部通过 |
| RecommendationInputContractTest（已有） | 11 | 全部通过 |
| JobAbilityContentPolicyTest（已有） | 10 | 全部通过 |
| 合计 | 128 | 无失败、异常或跳过 |

新增计算层107项，已有契约/策略21项。最终Maven测试构建成功。另用独立Spring容器验证组件装配，容器不创建DataSource或RedisTemplate，默认语义提供者不调用模型。

已对齐的手算例：全项100→100.00；75/75/100/55/0→70.50；同例薪资日薪→69.00、排除权重25%；同例薪资缺失→51.75、覆盖率75%；技能未知且权重为正→总分null。重叠工龄18个月、缺18个月得32.50，在读硕士、计划教育、学历二值规则、否定优先、别名去重、显示分同分但原始分不同的排序也已覆盖。

测试中的语义stub只验证100/80/60平均得80的算术及扩展接口约束，未部署或调用真实模型，不能当成语义效果验证。没有新的HTTP接口，本阶段没有Postman运行结果；下一阶段接口用例按约定加入指定持久回归集合。

机器可读汇总见`job-recommendation-calculator-test-results-2026-11-01.json`；本地Surefire原始证据位于忽略的`target/recommendation-build/target/surefire-reports/`。

复现命令（工程根目录，使用本机已有离线依赖和独立构建目录）：

```powershell
& 'D:/apache-maven-3.9.16-bin/apache-maven-3.9.16/bin/mvn.cmd' -o '-Dmaven.repo.local=C:/Users/李/.m2/repository' -f target/recommendation-build/pom.xml '-Dtest=RecommendationInputContractTest,JobAbilityContentPolicyTest,FeatureNormalizerTest,SkillMatcherTest,RecommendationCalculatorTest' test
```

其他环境可以在标准pom.xml上执行同样的测试类选择；独立target构建配置是本地验证辅助文件，不是必须提交的业务配置。

## 7. 面试理解与准确表达

- 为什么UNKNOWN和0不同：0说明有证据不满足；UNKNOWN说明证据不足或服务不可用。错误归零会误导排序和用户提示。
- 为什么只有不可比较项重分配：缺失资料仍保留分母，避免少填资料反而抬分；日薪对月薪则本来就无法直接比较。
- 为什么工龄先合并：重叠工作不能重复累计；相接区间应先拼成连续时间再算完整月。
- 为什么保留原文和版本：派生名称、别名和模型会迭代；需要能解释某个分数来自哪条证据和哪套规则。
- 为什么独立计算层：不连接数据库也能固定日期验证业务算术；事务和权限留在应用服务，外部模型离开读取事务后调用。

可以准确描述：实现可解释的五维规则计算与名称/别名能力匹配，区分缺失和不可比较状态，使用107项计算测试覆盖规则与边界。代码由助手编写，用户复查学习，不能称为本人独立完成；不能写推荐准确率提升、性能提升或真实语义模型上线。

## 8. 下一阶段依赖

1. 确定推荐接口契约：指定简历、本次偏好覆盖、strictCity、预设/自定义权重、分页，明确已投递排除与deadline规则。
2. 应用Service校验求职者身份与简历归属，在同一读取快照中批量加载必要特征；仅选择ACTIVE岗位和APPROVED企业，明确方向与可选城市过滤。
3. 结束读取事务后构建特征、计算、全局稳定排序，再分页；偏好覆盖仅作用于本次请求，不写回简历。
4. 返回解释、覆盖率、原文证据、日期和版本；使用Postman MCP持久验收成功、权限、过滤、空数据与计算样例。
5. 真实语义分支单独做模型选型、程度/范围规则、人工样例标注与阈值校准，验收后再启用，不能用演示分替代。
