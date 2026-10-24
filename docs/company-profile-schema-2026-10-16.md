# 企业资料：数据库、实体与枚举阶段记录

## 本次完成范围

2026-10-16，在用户明确授权后执行开发库 smart_recruit 的企业资料增量迁移，并补齐实体及枚举。
数据库 MySQL 8.0.13；工作区 D:/project/smart-recruit，main 分支；开始前工作区干净。

- company 从 10 列扩展到 38 列，description 从 VARCHAR(500) 改为 TEXT。
- 新建 company_benefit、company_photo、company_qualification、company_review_record。
- 扩展 Company，新增 CompanyBenefit、CompanyPhoto、CompanyQualification、CompanyReviewRecord。
- 新增 13 个企业资料及审核枚举，原 CompanyStatus 保持原定义。
- 已保存迁移 SQL：sql/2026-10-16-company-profile.sql。开发库已执行，禁止重复运行。

本阶段没有实现 DTO/VO、资料保存、上传存储、审核及公开查询，也没有执行接口回归或提交 Git。

## 备份与执行

执行前读取并保存 company 原始 DDL 和全部 19 条记录的 10 个原字段。
备份副本位于：D:/project/.local-db-backups/smart-recruit/company-profile-20261016-143914。
该路径位于后端仓库和 target 目录之外，避免 Maven clean 清理或随业务代码提交。

备份内容：

- company-before.sql：原始建表语句。
- company-before.json：原始记录，各列以字符串或 null 保存。
- schema-after.json：迁移后的五张表 SHOW CREATE TABLE。
- migration-result.json：已完成 DDL、逐字段比较结果和执行状态。

原始执行目录也保留于 target/db-backups/company-profile-20261016-143914。
target/company-profile-migration-result.json 记录两处位置。

执行前校验主表仍为原 10 列且四张子表均不存在。随后执行 1 条 ALTER TABLE、4 条 CREATE TABLE。
DDL 不能通过普通事务回滚，因此逐条记录完成状态；本次五条全部成功。
备份不是可以直接在当前库重跑的恢复脚本。恢复需先停止相关写入，根据备份、外键关系及新增数据情况制定方案，
不能通过自动删表删列覆盖后续数据。

## 数据与结构核对结果

- 19 条原企业记录的全部 10 个原字段，迁移前后逐字段比较完全一致，含准入状态与原时间字段。
- 原准入状态仍为 APPROVED 7、PENDING 11、REJECTED 1。
- 旧记录 profile_version=0、registration_version=0、registration_review_status=UNVERIFIED，
  verified_registration_version 为 NULL，没有生成工商核验通过的假证据。
- 四张新表均为 0 条记录，不写入原型示例照片、材料或福利。
- 原 uk_user_id 唯一索引和 job 到 company 的外键保留。
- 主子表企业外键使用 RESTRICT，资质逻辑删除保留审核关联。
- 审核记录的组合外键保证材料与审核记录的 company_id 一致。
- 资料快照为 JSON，实体使用 String 承接，后续 Service 负责生成与解析。
- 五个实体字段名转换为下划线后，与数据库列名逐项一致：38、4、8、11、11 列。

已有异常记录 companyId=14、userId=17，所属账号角色 ADMIN、企业状态 PENDING、无岗位，保持原样，待用户核实。

## 枚举契约

枚举名称作为持久化和请求代码，getDescription() 返回中文说明；本阶段没有增加字典查询接口。

| 枚举 | 用途 |
| --- | --- |
| CompanyIndustry | 行业 |
| CompanyEmployeeSize | 人员规模，20 人以下与 20–99 人不重叠 |
| CompanyFinancingStage | 融资阶段 |
| CompanyNature | 企业性质 |
| CompanyBusinessStatus | 经营状态 |
| CompanyRestPolicy | 休息制度 |
| CompanyOvertimePolicy | 加班情况 |
| CompanyBenefitCode | 原型十二项福利 |
| CompanyRegistrationReviewStatus | UNVERIFIED/PENDING/VERIFIED/REJECTED |
| CompanyReviewStatus | PENDING/APPROVED/REJECTED，用于审核记录及材料 |
| CompanyReviewType | REGISTRATION/QUALIFICATION |
| CompanyQualificationType | BUSINESS_LICENSE/INDUSTRY_CERTIFICATE/OTHER |
| CompanyReviewDecision | APPROVE/REJECT，供后续审核 DTO 使用 |

不得将准入 CompanyStatus.APPROVED 当作工商或材料已核验。
工商审核记录 APPROVED 与 company.registration_review_status=VERIFIED、通过版本号的同步，需要在后续同一事务内实现。

## 编译与下一阶段

执行 mvn -o -Dmaven.repo.local=C:\Users\李\.m2\repository -DskipTests compile，106 个 Java 源文件编译成功。
AdminController 原有 unchecked 提示未在本阶段处理。git diff --check 通过。
本阶段没有运行单元测试、接口测试、文件上传或审核竞争测试；编译和结构核对不代表业务已验收。

下一阶段先编写 CompanyProfileUpdateDTO、管理与公开 VO，并收口直接接收/返回 Company 实体的旧接口。
当前旧 Mapper 的 INSERT/UPDATE 仍只保存原基础字段，新字段尚未接入保存业务。
MySQL 8.0.13 不执行 CHECK，审核对象字段组合、文件上限、版本条件、状态迁移和字段权限必须由 Service 保证。
