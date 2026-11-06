# 岗位能力输入接入与验收（2026-11-01）

## 1. 业务目标与交付范围

推荐算法需要逐条比较岗位要求与简历能力。旧岗位仅有requiredSkills整段文本，不能用“子表没有行”判断是否回退旧文本：新草稿也可能主动清空列表。

本阶段接通既有迁移、枚举、实体、DTO和VO；保留原文，并明确唯一来源、版本和事务边界。没有执行新的源库迁移，没有改前端，没有提交或推送Git。

| 入口 | 请求或响应变化 | 验收重点 |
| --- | --- | --- |
| POST /job | JobContentSaveDTO；仍返回Result<Void> | 直接发布校验、能力与主表一起保存 |
| POST /job/drafts | JobContentSaveDTO；仍返回Result<Void> | 允许结构化空数组草稿 |
| PUT /job/{id} | JobContentSaveDTO；仍返回Result<Void> | 父行锁、版本检查、来源保护 |
| PUT /job/{id}/publish | 请求不变 | 旧文本可发布；结构化空草稿不可发布 |
| GET /job/{id} | Result<JobContentDetailVO> | 求职者仍只能看招聘中岗位 |
| GET /job/company/{id} | Result<JobContentDetailVO> | 企业归属校验与完整能力列表 |

暂停、恢复、结束、删除及分页路由保持既有职责；回归覆盖生命周期及删除子表级联。普通岗位字段继续采用原全量PUT表单语义，不能把本接口当作任意字段PATCH。

## 2. 文件与职责

- `controller/JobController.java`：接收新DTO、执行Bean Validation、提取当前身份、返回既有Result。业务来源不能由请求任意指定。
- `service/JobService.java`：声明新的输入与详情输出类型。
- `service/impl/JobServiceImpl.java`：审核企业权限、岗位归属、父行锁、生命周期、事务、写入操作记录与提交后缓存失效。
- `service/impl/JobAbilityContentPolicy.java`：计算本次允许保存的能力来源、文本、版本和方向；不操作数据库或调用模型。
- `mapper/JobMapper.java`：显式写入direction_code、required_abilities_source和required_abilities_version。
- `mapper/JobRequiredAbilityMapper.java`：按岗位查子表、删除旧列表、参数化插入条目；按sort_order、id稳定读取。
- `service/impl/JobContentSnapshotReader.java`：在独立Spring Bean的只读REPEATABLE_READ事务中加载主表和子表，再构造详情VO。

## 3. 保存调用链

### 3.1 创建岗位

1. Controller校验原有标题、长度以及新增的数组、枚举、版本和展示长度。
2. Service校验当前登录用户是已审核企业，companyId从权限查询获得，不取请求体。
3. 创建空Job，由策略设置内容。新岗位能力预期版本为0。
4. 直接发布执行现有完整性校验；草稿可缺少发布必填项。数组非空且合法时，展示文本由rawText按换行连接生成。
5. 插入主表取得生成的主键，再按数组顺序写入子表，最后写岗位操作记录。
6. 三者同一事务提交，提交后失效详情缓存。任意一步抛运行时异常，业务行全部回滚。

### 3.2 更新已有岗位

```text
校验已审核企业
  → SELECT job ... FOR UPDATE
  → 校验companyId归属
  → 检查能力来源与预期版本
  → 应用允许保存的内容
  → 活跃/暂停岗位执行发布完整性校验
  → UPDATE job
  → 必要时DELETE旧能力、INSERT新能力
  → INSERT job_activity
  → COMMIT
  → afterCommit删除详情缓存
```

父行锁由现有requireOwnedJob统一获取，能力替换、发布、删除等写路径采用同一个岗位锁。锁会保持到事务提交或回滚，不能把SELECT FOR UPDATE移到事务之外。

新增和内容更新、草稿发布采用READ_COMMITTED：锁定父行后按最新提交状态检查和保存。模型调用不进入写事务。

### 3.3 为什么行锁和版本号都需要

行锁让两个写请求顺序执行，防止替换子表时交叉删除、插入。但第二个请求等待完成后，仍可能提交它在旧页面看到的列表。

能力版本用于拒绝这个旧页面。两个请求都携带版本0：第一个锁定岗位、校验0、保存为1并提交；第二个随后拿到锁，读到1，与请求0不一致，拒绝保存。

本实现是在父行锁保护下比较请求版本，并由服务端递增。不是靠客户端赋值版本，也不是整份岗位内容的通用乐观锁。没有提供数组时，版本不会替代普通岗位内容的并发规则。

## 4. 新旧来源与清空规则

| 当前情况 | 输入 | 结果 |
| --- | --- | --- |
| 新岗位 | 不提供数组或数组为null | LEGACY_TEXT，初始版本0 |
| 任意来源 | 数组与正确版本 | 替换子表，STRUCTURED，版本+1 |
| STRUCTURED | 数组省略/null，旧文本省略或原样回传 | 保留原子表、文本、来源及能力版本 |
| STRUCTURED | 没提供数组，却修改/清空旧文本 | 拒绝，提示使用逐条能力编辑 |
| 已有LEGACY_TEXT | 旧文本确实改变，包括明确null | 更新旧文本，版本+1 |
| 草稿 | []及正确版本 | STRUCTURED、空子表、展示文本null |
| 发布或编辑招聘中/暂停岗位 | 清空必备能力 | 拒绝并回滚 |

省略和null数组都不替换，[]才是明确清空。旧requiredSkills通过DTO自定义setter记录“字段是否出现”，因此可以区分未传与明确null。

服务端按数组位置生成sort_order，保留原文和重复描述；不接收子项id、jobId、sortOrder或canonicalCode。批量替换后的子项ID可以变化，前端不能依赖其稳定。

最多20条；rawText按换行拼接后总计不超过500字符，恰好500允许，超出拒绝。不能静默截断用户要求。数组与同时传入的requiredSkills不一致也会拒绝。

方向代码与文本分开保存。明确“后端开发”与FRONTEND等矛盾组合拒绝；宽泛类别可由用户明确选择方向。旧客户端改变category且未传方向时清除旧代码，同类别保持，明确null可以清空。不在保存时猜测任意文本方向。

## 5. 详情读取与缓存

新VO在旧详情上增加directionCode、requiredAbilitiesSource、requiredAbilitiesVersion、requiredAbilities。

- LEGACY_TEXT：requiredAbilities为null，表示未切换来源。
- STRUCTURED：从子表读取，明确空列表返回[]；即使为空也不回退旧字符串。

主表和子表在同一只读可重复读事务中读取，避免读到“主表新版本、子表旧列表”的混合响应。读取器使用独立Bean，调用经过Spring事务代理；不能给本类私有自调用方法加@Transactional就认为已经生效。

新缓存前缀为`job:detail:content:v1:`，锁前缀也同步变更，避免将旧JobDetailVO缓存误当成新响应类型。Redis读写不包在上述数据库读取事务中。缓存失效沿用afterCommit与延迟二次删除，回滚不执行提交回调；新增岗位也会失效此前可能缓存的不存在结果。

缓存仍是既有TTL与尽力失效策略，不提供数据库与Redis的强一致保证。数据库提交后的缓存删除失败会记录日志，不会把已成功提交的操作伪装成能够回滚的失败。部署时需重启后端加载新代码，当前验收使用独立端口，未重启用户的8081服务。

## 6. 回归结果与数据保护

所有接口请求通过Postman MCP执行，并已核对137条请求的方法、URL、请求体和断言脚本与指定主集合一致：

`SmartRecruit API - Optimized Regression / 03 Job / 岗位能力接入回归 2026-11-01`

| 阶段 | 实际HTTP请求 | 断言 | 结果 |
| --- | ---: | ---: | --- |
| A 保存、兼容、长度、枚举、权限、发布与缓存 | 109 | 146 | 全部通过 |
| B 操作记录故障回滚 | 9 | 13 | 全部通过 |
| C 子表第二项写入故障回滚 | 6 | 8 | 全部通过 |
| D 相同版本并发保存 | 6 | 11 | 全部通过 |
| E 故障恢复、缓存刷新及级联删除 | 9 | 13 | 全部通过 |
| 合计 | 139 | 191 | 零失败请求、零失败断言 |

D包含4条集合请求和2条pm.sendRequest并发子请求。隔离库插入延迟确保请求实际重叠；断言一次业务200、一次业务500，最终版本只加1且列表没有混合内容。

成功写入返回HTTP200、业务code=200；参数、业务版本或权限拒绝按项目既有约定返回HTTP200、业务code=500及data=null；未登录返回HTTP401。故障阶段断言真实数据库异常，并另外用数据库快照验证主表内容、时间戳、子表、版本与岗位操作记录全部不变。事务回滚后自增ID出现空洞是正常行为，不代表业务记录残留。

源库smart_recruit仅执行只读盘点与指纹核对。回归使用新建隔离库`smart_recruit_job_abilities_1793519054874`与端口18090。Redis选择原本为空的15号库，加运行标记，清理时校验岗位ID和键前缀，只删除本次所有权明确的键，没有执行FLUSHDB。

最终审计确认：源库全部业务行指纹未变；触发器已移除；结构化展示文本与子项原文、顺序一致；无孤儿子项；Postman执行开关已关闭并重读确认；自建服务已停止；专用缓存已清理。隔离数据库保留供复查。

SHA-256：`e59930d9da0a40319c9706d5a9de269c7fb245a28695b66edbe2b682fcefa00e`。

额外运行11项输入契约测试和新增10项策略单元测试，21项全部通过。编译与测试使用target内独立构建目录；辅助classpath插件因离线缺依赖未运行成功，后改用Surefire已解析的classpath，最终Maven测试构建成功。

交付物：本说明、字段契约更新、实现代码、JobAbilityContentPolicyTest、JobAbilitiesFixture.java、job-abilities-regression.cjs及对应集合导出、manifest和results.json。凭据及原始运行证据只在忽略的target目录，不进入这些交付文件。

## 7. 面试表达与下一阶段

需要掌握：InnoDB行锁与事务生命周期、为什么串行写入仍要检查旧页面版本、全量替换子表如何回滚、DTO字段存在性与null/[]区别、事务代理自调用问题、数据库提交与Redis失效的边界。

可如实描述本次成果：

> 为岗位能力要求设计并实现结构化保存与旧文本兼容，通过父行锁、预期版本和同事务操作记录避免并发覆盖；使用Postman持久回归覆盖权限、故障回滚与并发场景。

本次实现由助手编写，用户复查学习；不能表述为本人独立完成。没有基准测试，不写性能提升比例，也不能说推荐算法或向量匹配已经完成。

下一阶段进入推荐特征构建：先将结构化与旧文本证据转换为统一特征、处理UNKNOWN/NOT_APPLICABLE，再实现可单测的五维计算器。技能名称和别名先行；语义模型、版本与校准未确定时保持未启用，不能伪造语义命中。
