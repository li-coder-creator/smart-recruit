-- Smart Recruit 岗位模块结构迁移（旧版 job 表 -> 岗位模块新版结构）
-- 本脚本依据 2026-09-29 开发库的最终表结构重建，用于新环境迁移和代码审查。
-- 开发库 smart_recruit 已完成本次结构变更，切勿在该库重复执行。
-- 执行前选择目标数据库、备份 job 与 job_application，并确认以下检查结果。
-- MySQL 8.0.13 不执行 CHECK 约束，因此状态合法性必须由应用层校验。
-- 既有状态 0=招聘中、1=已暂停保持不变；既有状态 2 认定为已结束；新增 3=草稿。
-- 旧 description 字段保留，不推断为新职责或任职要求；旧记录的发布时间、结束时间保留 NULL。

-- 一、执行前检查。若有未识别状态、重复投递或孤儿投递，先处理数据再执行 DDL。
SELECT VERSION() AS mysql_version, DATABASE() AS current_schema;

SELECT status, COUNT(*) AS job_count
FROM job
GROUP BY status
ORDER BY status;

SELECT id, status
FROM job
WHERE status NOT IN (0, 1, 2);

SELECT user_id, job_id, COUNT(*) AS duplicate_count
FROM job_application
GROUP BY user_id, job_id
HAVING COUNT(*) > 1;

SELECT a.id, a.job_id
FROM job_application a
LEFT JOIN job j ON j.id = a.job_id
WHERE j.id IS NULL;

-- 二、岗位字段和索引。保持原有 salary_min、salary_max、description 及历史数据不变。
ALTER TABLE job
  ADD COLUMN category VARCHAR(50) NULL COMMENT '岗位类别',
  ADD COLUMN team VARCHAR(100) NULL COMMENT '所属团队',
  ADD COLUMN headcount INT NOT NULL DEFAULT 1 COMMENT '招聘人数',
  ADD COLUMN employment VARCHAR(20) NULL COMMENT '用工类型',
  ADD COLUMN work_mode VARCHAR(20) NULL COMMENT '办公方式',
  ADD COLUMN address VARCHAR(255) NULL COMMENT '详细地址',
  ADD COLUMN salary_unit VARCHAR(16) NOT NULL DEFAULT 'MONTH'
    COMMENT '薪资单位：MONTH/DAY/YEAR；旧薪资按月处理',
  ADD COLUMN negotiable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否薪资面议',
  ADD COLUMN summary VARCHAR(255) NULL COMMENT '一句话介绍',
  ADD COLUMN responsibilities TEXT NULL COMMENT '岗位职责',
  ADD COLUMN requirements TEXT NULL COMMENT '任职要求',
  ADD COLUMN bonus TEXT NULL COMMENT '加分项',
  ADD COLUMN required_skills VARCHAR(500) NULL COMMENT '必备技能展示文本',
  ADD COLUMN preferred_skills VARCHAR(500) NULL COMMENT '加分技能展示文本',
  ADD COLUMN benefits VARCHAR(255) NULL COMMENT '福利亮点',
  ADD COLUMN deadline DATE NULL COMMENT '计划截止日期',
  ADD COLUMN published_at DATETIME NULL COMMENT '发布时间',
  ADD COLUMN ended_at DATETIME NULL COMMENT '结束时间',
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 3
    COMMENT '0招聘中，1已暂停，2已结束，3草稿',
  ADD INDEX idx_job_company_status_updated
    (company_id, status, update_time, id),
  ADD INDEX idx_job_status_updated
    (status, update_time, id);

-- 三、投递约束与统计索引。外键禁止物理删除已有投递的岗位。
ALTER TABLE job_application
  ADD UNIQUE KEY uk_application_user_job (user_id, job_id),
  ADD INDEX idx_application_job_status_time
    (job_id, status, create_time),
  ADD CONSTRAINT fk_application_job
    FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE RESTRICT;

-- 四、执行后核对。还应检查 SHOW CREATE TABLE 中的默认值、唯一索引和外键。
SHOW CREATE TABLE job;
SHOW CREATE TABLE job_application;

SELECT status, COUNT(*) AS job_count
FROM job
GROUP BY status
ORDER BY status;

-- 回滚说明：MySQL DDL 会隐式提交。本脚本不提供直接 DROP 列的回滚语句，
-- 因为迁移后的新列可能已有业务数据。需要回退时，先停止写入，
-- 使用执行前备份恢复数据和结构，并同步回退对应的应用版本。
-- 数据库默认值 3 不能代替 Service 赋值：旧 JobMapper 的 INSERT 显式写入 status。
