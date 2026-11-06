# 岗位推荐：存量数据盘点（2026-10-24）

## 1. 范围与取证方式

- 工作区：D:/project/smart-recruit，main，基线提交 0966c0a。
- 数据源：当前 application.yml 配置连接的 smart_recruit；未查询旧回归隔离库。
- 服务端会话确认 transaction_read_only=1、REPEATABLE-READ；仅执行 SELECT，读取后 rollback。
- 只选推荐特征、业务记录 ID、日期、元数据和计数；未读取姓名、电话、邮箱、密码、政治面貌或简历正文。
- 读取 job、company、resume、job_preference、resume_skill、resume_education、resume_experience、job_application。
- 未执行迁移、修改业务代码、补写数据、删除旧数据或调用业务接口。
- 原始特征快照：target/recommendation-data-audit-2026-10-24.json；汇总：target/recommendation-data-audit-summary-2026-10-24.json。
- 审计辅助源码：target/RecommendationDataAudit.java；这些 target 文件为本地忽略文件。

本报告是这个开发库在读取时刻的数据快照，不代表其它实例或浏览器连接的数据。
若页面可见更多技能或经历，应核对浏览器代理目标、运行服务及其外部数据源配置，不能直接认定数据丢失。

## 2. 总量

| 表 / 范围 | 数量 |
| --- | ---: |
| 企业 company | 19 |
| 岗位 job | 13 |
| 招聘中且企业准入 APPROVED | 5 |
| 已结束且企业准入 APPROVED | 8 |
| 简历 resume | 6 |
| 求职意向 job_preference | 0 |
| 简历技能 resume_skill | 0 |
| 教育经历 resume_education | 1 |
| 工作经历 resume_experience | 0 |
| 投递 job_application | 7 |

简历 ID 为 5、7、9、10、13、29；所有简历均无求职意向、技能和工作经历。
仅简历 13 有一条教育记录。不能把这些空表解释为“所有人零经验”“没有任何技能”或“学历不限”。
四张简历子表本次未发现指向不存在简历的孤儿记录；空表也不构成对复杂关联数据的验证。

## 3. 五个在招岗位

| 岗位 ID | 城市 | 类别 | 经验 | 学历 | 必备技能 | 薪资（元/月） |
| --- | --- | --- | --- | --- | --- | --- |
| 13 | Shanghai | 空 | 1-3 | Bachelor | 空 | 10000～20000 |
| 14 | 上海 | 后端开发 | 空 | 空 | Java, Spring Boot | 10000～20000 |
| 15 | 上海 | 后端开发 | 空 | 空 | Java, Spring Boot | 面议 |
| 17 | 上海 | 后端开发 | 空 | 空 | Java, Spring Boot | 10000～20000 |
| 31 | 东莞 | 技术研发 | 空 | 空 | 会vue3 | 面议 |

- 在招岗位经验、学历各缺 4/5；必备技能缺 1/5；类别缺 1/5。
- 不能因岗位 14、15、17、31 的要求为空，就给学历、经验“明确不限”的满分。
- 岗位 13 的经验 1-3 需要明确兼容为年限范围，而不是误读成 1～3 个月。
- 岗位 31 的技能是自然语言，不宜未经确认就自动映射为 Vue 3；建议后续由企业规范填写。
- 目前在招数据没有非月薪样本；日薪、年薪分支需要将来使用隔离验收数据覆盖。

## 4. 全部岗位的实际取值

| 字段 | 取值与数量 |
| --- | --- |
| experience | 应届生 1；1-3 1；NULL 11 |
| education | 本科 1；Bachelor 1；NULL 11 |
| category | 后端开发 3；技术 7；技术研发 1；NULL 2 |
| city | 上海 10；Shanghai 1；深圳 1；东莞 1 |
| salary_unit | MONTH 13 |
| negotiable | false 11；true 2 |
| required_skills | Java, Spring Boot 3；Java,Spring Boot 5；Java 2；会vue3 1；NULL 2 |

“技术”“技术研发”属于宽泛类别，不能直接等同于 BACKEND 或 FRONTEND。
可将明确的“后端开发”映射为 BACKEND；其它方向识别需使用明确映射及人工确认，保留未知结果。
旧岗位处于结束状态不参与推荐，但仍需保留，以免影响历史投递。

## 5. 学历与经验事实

唯一教育记录：id=2、resume_id=13、degree=Bachelor、2020-09-01～2024-06-30。
起止均非空、顺序有效、没有晚于 2026-10-24 的日期。
可兼容映射为 BACHELOR；填写日期已结束不构成毕业、学位或学历认证证明。

工作经历没有任何记录，因此本次无法用真实数据验证：

- 日期重叠、至今状态、实习与正式工作区分；
- 零经验确认、未来日期、缺失日期及经验累计；
- 在校生、应届生等身份约束。

前端学历选项使用“大专、本科、硕士研究生、博士研究生”，岗位选项使用“不限、大专、本科、硕士、博士”。
还需兼容数据库实际存在的 Bachelor。不能只为中文选项写分支。
岗位经验选项含“经验不限、在校生、应届生、1–3年、3–5年、5年以上”，必须与旧值 1-3 一并建映射。

## 6. 数据结构与索引

已具备的数据：

- job：自由文本经验、学历、类别、城市、技能；数值薪资及 MONTH/DAY/YEAR 单位。
- job_preference：期望职位、城市、薪资上下限、薪资说明；没有方向代码和薪资单位。
- resume_education：学历文本、开始 / 结束日期；没有明确在读 / 已取得学历状态。
- resume_experience：开始 / 结束日期及 period_text；没有经历类型和是否在职状态。
- resume：没有求职阶段、明确确认零正式工作经验的字段。

相关现有索引：

| 表 | 相关索引 |
| --- | --- |
| job | (status,update_time,id)；(company_id,status,update_time,id) |
| job_application | UNIQUE(user_id,job_id)；(job_id,status,create_time) |
| job_preference | UNIQUE(resume_id) |
| resume_skill / education / experience | resume_id 索引 |

可利用 user_id、job_id 唯一索引执行已投递排除。是否补方向索引需在规范化方案及实际查询确定后决定；
本次 13 个岗位不足以支持性能结论，不因字段将来会查询就为所有维度建立索引。

## 7. 对下一阶段的直接影响

1. 目前没有一份简历能直接支撑默认五维评分。应返回缺失信息，不能将演示简历灌入真实响应。
2. 经验维度必须支持 UNKNOWN，后续需要结构化经历类型、是否至今、求职阶段及零经验确认。
3. 学历先实现明确别名映射，并将“申报学历背景（含在读）”与“已取得学历证明”区分。
4. 城市至少兼容 Shanghai→上海；技能至少兼容明确英文技能的空格、大小写和逗号差异。
5. 代表性验收简历需要后续在隔离环境创建，不能将“开发库无记录”算作已覆盖算法边界。
6. 默认偏好取所选简历的实际数据；缺失保持缺失，不自动设为 Java、杭州或 15000 元。

对应规则、状态、公式及手算样例见 [五维规则 V1](job-recommendation-rules-v1.md)。

## 8. 复核查询示例

```sql
SELECT j.status, c.status AS company_status, COUNT(*)
FROM job j LEFT JOIN company c ON c.id = j.company_id
GROUP BY j.status, c.status;

SELECT experience, COUNT(*) FROM job GROUP BY experience;
SELECT education, COUNT(*) FROM job GROUP BY education;
SELECT degree, COUNT(*) FROM resume_education GROUP BY degree;

SELECT COUNT(*) FROM job_preference;
SELECT COUNT(*) FROM resume_skill;
SELECT COUNT(*) FROM resume_experience;
```

复核也应先确认目标库并建立只读事务；不要执行夹具 setup、reset 或任何补数据模式。
