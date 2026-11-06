# 岗位推荐接口契约 V1

日期：2026-11-01。适用当前求职者按指定简历推荐岗位。计算规则见`job-recommendation-rules-v1.md`；实现及回归结果见`job-recommendation-query-stage-2026-11-01.md`。

## 1. 三个新增接口

| 业务 | 请求 | 参数 | 响应data | 权限 |
| --- | --- | --- | --- | --- |
| 推荐选项、权重预设和默认筛选规则 | GET /job/recommendations/options | 无 | Options | 当前登录求职者 |
| 所选简历的默认推荐上下文 | GET /job/recommendations/context | resumeId，正整数 | Context | 当前求职者且简历属于本人 |
| 推荐查询 | POST /job/recommendations | JobRecommendationQueryDTO JSON | QueryResult | 当前求职者且简历属于本人 |

沿用Result：成功HTTP200/code200；参数、归属、业务规则拒绝HTTP200/code500；未登录或无效令牌HTTP401。请求不接受userId、岗位状态、企业审核状态、asOfDate、规则版本或预置分数。

没有新增匹配解释详情路由：每个列表项已含五维分项和能力证据，前端可展开。查看岗位继续使用GET /job/{id}，全部岗位Tab继续使用既有GET /job；这些接口属于独立契约，推荐接口不会改变它们的过滤规则。

## 2. 查询请求

```json
{
  "resumeId": 901,
  "preferences": {"directionCode": "BACKEND", "city": "上海", "salaryMax": 20000},
  "preset": "BALANCED",
  "strictCity": false,
  "excludeApplied": true,
  "excludeExpired": true,
  "sort": "MATCH",
  "page": 1,
  "size": 10
}
```

示例ID仅示意，调用时从本人的简历列表选择，不提供“自动生成演示简历”行为。最小请求为`{"resumeId":本人简历ID}`，前提是该简历默认意向能确定方向。

| 字段 | 类型 | 省略时 | 校验与含义 |
| --- | --- | --- | --- |
| resumeId | Long | 必填 | JSON正整数，禁止字符串或小数强转 |
| preferences | 对象/null | 使用简历求职意向 | 只影响本次查询，未传子字段继承，明确null清空可选值 |
| preferences.directionCode | BACKEND/FRONTEND/null | 使用明确默认代码或精确职位映射 | 显式方向优先于旧职位文本；清空后无法确定方向则拒绝查询 |
| preferences.city | String/null，最多50字符 | 默认期望城市 | 支持options中的code或label及明确别名；不从居住城市填充 |
| preferences.salaryMin/Max | Integer/null，正数，元/月 | 默认期望范围 | 不接受15表示15K；max=20000表示20000元；显式覆盖后区间必须有序 |
| preset | BALANCED/SALARY_FIRST/CITY_FIRST/null | 未提供自定义权重时BALANCED | 与非null weights互斥，禁止数字枚举序号 |
| weights | 对象/null | 使用预设 | 五项完整、0～100整数、总和大于0；相对权重不要求和为100 |
| strictCity | Boolean | false | true时必须有可识别期望城市；false时未知城市作为UNKNOWN参与计算 |
| excludeApplied | Boolean | true | 排除当前用户有任何投递记录的岗位，独立于所选简历和投递状态 |
| excludeExpired | Boolean | true | 排除deadline早于asOfDate的岗位；当天及NULL不排除 |
| sort | MATCH/SALARY_DESC | MATCH | 匹配度或合法月薪上限降序 |
| page | Integer | 1 | 至少1；超过总页数返回空items，保留total |
| size | Integer | 10 | 1～100 |

布尔开关必须是JSON true/false，显式null、数字或字符串拒绝。分页、薪资、自定义权重也不做小数截断或数字字符串转换。未知字段拒绝。

自定义权重示例，省略preset：

```json
{"resumeId":901,"weights":{"skill":35,"salary":25,"city":20,"experience":10,"education":10}}
```

同时接受options及requestedWeights返回的大写维度码SKILL/SALARY/CITY/EXPERIENCE/EDUCATION，便于原样回传。金额和方向仍从本人的实际默认意向或本次明确输入取得。

历史默认意向中的非法薪资值按UNKNOWN处理；本次提交的非法数值/覆盖后倒置区间拒绝，不把错误输入伪装成合法面议。缺少期望薪资或城市不阻止非严格城市推荐，但会保留缺失状态和实际覆盖率。

## 3. 不可放宽的资格规则

- 当前角色必须为JOB_SEEKER，先检查角色和简历归属，再读取其子表。
- 岗位为ACTIVE，所属企业为APPROVED；关闭排除开关不能看到草稿、暂停、结束或未审核企业岗位。
- 必须确定本次意向方向。仅使用明确代码或“后端开发/前端开发”精确映射，宽泛“技术研发”不猜测。
- 岗位代码与明确类别相矛盾时不用于该方向推荐。
- 已投递按当前用户判断：换一份简历不能绕过排除；他人的投递不影响本人的结果。已拒绝的投递也算存在投递记录。
- 开关只改变推荐结果，不改变岗位状态、投递状态、是否能够再次投递或已有截止规则。真实投递仍由投递接口重新校验。

## 4. 返回契约

### Options

directions、cities为code/label列表；presets含code/label/weights；sorts和states描述排序与三种维度状态。返回defaultWeights、三个筛选默认值、分页限制、salaryUnit="元/月"、semanticEnabled及versions。

这些选项不包含默认上海、杭州或15000元等原型演示偏好。恢复默认时重新使用所选简历Context，并恢复BALANCED和默认筛选开关。

### Context

resumeId、resumeTitle、asOfDate、careerStage、formalExperienceStatus、actualFormalMonths（可null）、confirmedFormalMonths、highestEducation（可null）、skills原文摘要、preferences、directionRequired和warnings。

preferences含directionCode、city、cityCode、salaryMin、salaryMax。Context即使方向缺失也成功返回，前端可提示补选；Query方向缺失则拒绝。它不返回姓名、邮箱、电话、学校名称或完整工作履历。

### QueryResult

| 字段 | 说明 |
| --- | --- |
| resumeId、asOfDate | 本次所选简历与统一服务端上海日期 |
| appliedPreferences | 实际生效的方向、城市及期望金额，未写回默认意向 |
| requestedWeights | 本次五项原始相对权重 |
| strictCity、excludeApplied、excludeExpired、sort | 实际生效筛选与排序 |
| page、size、total、items | total为全部筛选后岗位数；items为全局评分排序后的分页切片 |
| semanticEnabled、versions | 当前语义能力状态及规则、规范化、别名、提取器、模型和校准版本 |

每个Item包含jobId、title、companyId/name/logo、城市、经验、学历、用工类型、团队、薪资范围/单位/面议、summary、benefits、requiredSkills、preferredSkills、directionCode、publishedAt、deadline、applied、expired、能力来源和版本。

评分部分包含totalScore（可null）、dataCoverage、excludedWeight、scoreReason、dimensions、abilityMatches、warnings，不返回内部未舍入排序分。

dimensions固定五项，各含dimension、state、score、meetsRequirement、reason、requestedWeight、ignored、effectiveWeightRatio、contribution及warnings。score显示两位，比例六位，贡献四位；总分由高精度值计算，不能用显示值自行重算排序。

abilityMatches按去重后的必备要求返回requirements原文证据、candidateEvidence（可null）、method、decision、score、similarity（可null）、reason。能力证据中的规范名称属于服务端派生结果，不能回传后覆盖原始用户数据。

## 5. 排序与空数据

MATCH按未舍入总分降序，null置末，同分按publishedAt和jobId降序，发布时间null置末。SALARY_DESC只比较有效非面议月薪上限，日薪、年薪、面议和异常范围置末，再按匹配排序打破平局。

先对全部合资格候选完成方向/城市筛选、评分与排序，再分页；不会先取数据库的一页再评分。没有匹配岗位返回total=0/items=[]；资料不足导致总分null的岗位仍可返回，不冒充0或权限错误。

当前真实语义未启用，embeddingModelVersion和calibrationVersion为null。需要语义比较的条目为UNKNOWN/SEMANTIC_NOT_CONFIGURED；前端应展示匹配待确认，不能提示“你不会该技能”或笼统要求补资料。技能正权重且UNKNOWN时总分null，仍展示其它分项。

分数是偏好匹配解释，不是录用概率。原型中学历不足50分已按确认规则替换为0分；不沿用示例简历、默认城市或演示薪资填补真实缺失。
