# 投递页面升级：阶段一数据盘点

## 取证范围

- 日期：2026-10-25；后端 D:/project/smart-recruit，main，基线 0966c0a。
- 前端 D:/project/smart-recruit-web，main，当前提交 5982dcd。
- 原型：C:/Users/李/.codex/visualizations/2026/10/24/01a16c02-2ca6-76a2-8ad2-5e8ca015b841/applications-prototype/index.html。
- 数据源：当前 application.yml 所配置的 smart_recruit；不代表运行服务一定未使用外部配置覆盖。
- MySQL 8.0.13；实际会话 transaction_read_only=1、REPEATABLE-READ，当前数据库 UTC 偏移 +480 分钟。
- 辅助程序仅允许 SELECT；读取后 rollback。未执行迁移、数据修正、接口请求或业务代码编译。
- 输出：target/application-phase-one-audit-2026-10-25.json；辅助源码：target/ApplicationPhaseOneAudit.java。
- 只读取结构、索引、关联完整性、状态及计数；不读取密码、联系方式、简历正文或会议链接。

工作区原有两份岗位推荐文档、无关 PPTX，以及前端已暂存的文档均保留。

## 一、实际投递数据

| 项目 | 数量 / 结果 |
| --- | --- |
| 投递总数 | 7 |
| 求职者数量 | 2 |
| 关联岗位数量 | 7 |
| 待处理 PENDING / 0 | 1 |
| 筛选中 SCREENING / 1 | 0 |
| 面试 INTERVIEW / 2 | 2 |
| 录用 HIRED / 3 | 3 |
| 未通过 REJECTED / 4 | 1 |
| 非法 / 空状态 | 0 |
| 简历缺失 | 1 |
| 岗位、企业、求职者账号缺失 | 均为 0 |
| 现存简历归属与投递用户不一致 | 0 |
| 重复 user_id + job_id 分组 | 0 |
| 创建时间缺失、更新时间早于创建、未来创建时间 | 均为 0 |
| 最早 / 最晚投递 | 2026-08-20 18:02:11 / 2026-10-12 16:18:23 |

用户 ID 8 的 6 条投递：待处理 1、面试 1、录用 3、未通过 1；用户 ID 36 有 1 条面试投递。
这是盘点时的开发库数据，不是接口验收结果或生产规模。

按 2026-10-25 自然日口径：用户 8 的最近 30 天为 5 条、最近 7 天为 0 条；
用户 36 的最近 30 天和 7 天均为 0 条。默认筛选为空不等于没有历史投递。

### 面试历史证据

- 当前面试 2 条，has_interviewed 均为 true。
- 录用 3 条中，2 条为 true，1 条为 false。
- 未通过 1 条为 true；待处理 1 条为 false。
- 当前样本没有 has_interviewed=null，后续仍保留历史未知的兼容规则。

已有直接录用且未进入面试的记录，不能通过“当前已录用”反推曾经筛选、面试。

## 二、展示字段与岗位状态

| 项目 | 结果 | 对设计的影响 |
| --- | --- | --- |
| 关联招聘中岗位 | 2 条投递 | 可按当前岗位访问权限判断是否提供跳转 |
| 关联已结束岗位 | 5 条投递 | 我的投递不能限定岗位必须招聘中 |
| 企业准入 | 全部 APPROVED | 不构成对未准入企业历史展示的验收 |
| 企业 Logo 为空 | 7 条投递 | 前端提供中性占位，不复制原型品牌 Logo |
| 用工类型为空 | 2 条投递 | 返回 null，不能默认“校招” |
| 岗位标题、企业名称、城市为空 | 均为 0 | 查询仍需保留缺失兼容 |
| 薪资单位 / 薪资边界缺失 | 当前关联样本均为 0 | 面议及空值仍要在隔离回归构造 |

当前模型没有年薪月份数、独立招聘类型、投递展示快照。
“20–30K·15薪”“校招”不作为本轮可承诺的真实字段。

## 三、实际表结构与索引

job_application 有 8 列：

| 列 | 实际类型 | 约束 / 默认值 |
| --- | --- | --- |
| id | bigint，带符号 | 主键、自增 |
| user_id、job_id、resume_id | bigint，带符号 | NOT NULL |
| status | tinyint | NOT NULL，默认 0 |
| create_time | datetime | NOT NULL，默认 CURRENT_TIMESTAMP |
| update_time | datetime | NOT NULL，默认 CURRENT_TIMESTAMP，ON UPDATE CURRENT_TIMESTAMP |
| has_interviewed | tinyint(1) | 允许 NULL，默认 0 |

实际已有索引：

- PRIMARY(id)。
- uk_application_user_job(user_id, job_id)，唯一。
- idx_application_job_status_time(job_id, status, create_time)。

实际外键 fk_application_job 指向 job(id)，删除 RESTRICT；未发现投递表触发器。
没有 application_event、application_interview 表，也没有名字包含 interview 的业务表。
新表外键必须与现有带符号 BIGINT 保持一致。

建议新增按用户时间、用户状态时间的索引；需后续通过实际查询与 EXPLAIN 验证，不承诺模糊关键词一定走索引。

## 四、现有代码可复用范围

- POST /application 已有事务、角色、岗位状态、简历归属及重复投递校验。
- ApplicationMapper.apply 没有自增 ID 回填配置，新增初始事件前需要补齐。
- PUT /application/{id}/status 已有显式迁移白名单、事务、原状态条件更新和影响行数检查。
- 状态与 has_interviewed 同 SQL 更新；现阶段没有流转事件插入。
- GET /application/mine、/my 返回 List<ApplicationVO>，没有求职者分页与筛选。
- 当前共享 VO 缺企业 Logo、城市、用工类型和薪资等页面字段。
- 求职者列表和详情使用 LEFT JOIN，简历缺失仍保留投递；详情先判断原记录及权限。
- 当前前端列表和详情由求职者 / 企业共同使用，新增求职者页面时避免改变企业返回结构。

## 五、阶段一结论

确定需要：求职者分页 / 概览 / 专用详情、真实事件、企业面试排期和求职者最近面试查询。
数据库草案增加两张表、一个历史完整性标记和两个用户查询索引。
旧记录不批量补造历史、不设置面试时间、不改投递状态或 has_interviewed。

实际数据没有覆盖筛选中、未知历史、多场面试和并发情形；后续在隔离环境构造回归。
本阶段交付为设计与只读盘点；接口功能和 SQL 迁移尚未实现 / 执行。

入口：[阶段一业务与实现计划](application-upgrade-phase-1.md)、[字段契约](application-upgrade-field-contract.md)、
[注释保护的 SQL 草案](sql/2026-10-25-application-upgrade.draft.sql)。
