# 单岗位最近投递接口

## 接口契约

`GET /job/company/{id}/recent-applications?limit=5` 返回 `Result<List<RecentApplicationVO>>`。
省略 limit 时默认 5 条，允许范围 1～20；非法范围抛出业务异常，不静默截断。
默认值放在 JobConstants，范围放在 JobValidationConstants。

当前登录用户必须具有企业角色并存在对应企业记录，岗位必须属于该企业。
沿用招聘进展接口的 requireCompany，不额外要求企业审核通过。
不存在的岗位报“岗位不存在”，其他企业岗位报“没有查看权限”。
草稿返回空数组；招聘中、暂停、已结束岗位查询全部投递状态，不只查询待处理。
岗位没有投递时同样返回空数组，不返回分页总数。

| 字段 | 来源与含义 |
| --- | --- |
| applicationId | job_application.id，供进入投递详情 |
| applicantName | 简历 real_name → 账号 nickname → “未填写姓名”，忽略空白字符串 |
| status | 当前投递状态码 |
| statusText | ApplicationStatus 对应 description |
| createTime | 原始投递时间，按项目既有时间序列化配置返回 |

排序固定为 create_time DESC, id DESC。状态更新不会使旧投递重新排到最前面。
姓名读取关联简历的当前值，不是投递时的姓名快照。
前端负责“今天”“昨天”等时间展示，原型里的“待筛选”文案应与当前枚举“待处理”对齐。

## 实现职责

Controller 已有正确路由，读取 ThreadLocal 中的 userId 后调用 Service 并包装响应。
Service 校验条数、企业身份、岗位存在性和归属；草稿提前返回，其他岗位调用 Mapper。
Mapper 只选择展示字段，用 LEFT JOIN 关联简历和用户，关联信息缺失时仍保留投递。
COALESCE 与 NULLIF(TRIM(...), '') 实现空值和空白姓名回退。
WHERE job_id 限定查询范围，ORDER BY 使用第二排序键保证同时间记录顺序确定，LIMIT 使用参数绑定。
Service 通过已有枚举补齐 statusText，不在 SQL 另建状态文字映射。
遗留非法状态不能被默认为待处理，当前枚举 fromCode 会拒绝无效状态并由既有异常处理返回失败。

整个方法使用 readOnly + REPEATABLE_READ，岗位归属检查和投递查询使用同一数据库读取快照。
本接口不写数据库，不新增缓存，也不新增数据库字段或索引。
现有索引草案 (job_id, status, create_time) 含有本查询未筛选的 status，不能据此认定排序已被索引完整支持。
需要后续实测 EXPLAIN 才能决定是否增加 (job_id, create_time, id)，本次未做性能量化。

## 验证状态及后续验收

实现阶段只做静态检查和跳过测试的 Maven 编译。
`mvn -o -Dmaven.repo.local=C:\Users\李\.m2\repository -DskipTests compile` 已通过，Git diff 空白检查通过。
后续已通过 Postman MCP 完成联合回归，实际响应、权限、排序和姓名回退均通过。
详细数据、用例位置和范围限制见 job-panels-regression-2026-10-04.md。
用例已加入 SmartRecruit API - Optimized Regression，验收范围包括：

- 默认条数、显式条数、上限、0、负数、超过上限和非整数输入。
- 没有投递、草稿、暂停、已结束岗位。
- 多条记录时间相同，验证 ID 倒序；条数超过默认值时验证截断。
- 简历姓名为空、空白，昵称回退和全部缺失时的默认姓名。
- 当前状态文字正确；旧投递更新状态后不因更新时间改变排序。
- 未登录、求职者、其他企业、岗位不存在。

非整数 limit 由 Spring 参数绑定处理，当前全局异常处理未专门映射类型转换异常，可能返回通用系统异常文案；本次不修改全局规则。
