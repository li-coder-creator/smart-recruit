-- 岗位推荐结构化输入：迁移草案，2026-10-25。
-- 历史草案：B部分6条DDL已于2026-10-31在隔离库验证后执行到开发库smart_recruit。
-- 适配MySQL 8.0.13；已迁移环境禁止重复执行，实际结果见job-recommendation-input-stage-2026-10-31.md。
-- 配套：docs/job-recommendation-field-contract.md 和 job-recommendation-migration-plan.md。
-- 适用前提：下列5张原表存在，9个目标新列及job_required_ability表均不存在。
-- 执行前备份结构与数据，核对工作区、运行服务数据源和当前库；迁移窗口暂停写入。
-- DDL会隐式提交，START TRANSACTION/ROLLBACK不能撤销这组迁移。
-- 8.0.13不依赖CHECK约束执行校验；枚举、非负值、日期组合由应用严格验证。
-- 非幂等脚本；禁止使用忽略错误/强制继续模式，失败后核对结构按未完成步骤续做。
-- 不批量回填类型、方向、工龄或能力子项；不改写旧文本，不关闭外键检查。

-- A. 只读前置核对：预期smart_recruit、8.0.13或经单独确认的兼容版本。
SELECT DATABASE() AS current_database, VERSION() AS mysql_version;

SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('resume', 'resume_experience', 'resume_skill', 'job_preference', 'job')
ORDER BY table_name;

-- 必须返回0行；已有任一新列说明该库状态不同，不得继续整份执行。
SELECT table_name, column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'resume' AND column_name IN ('career_stage', 'formal_experience_status'))
    OR (table_name = 'resume_experience' AND column_name IN ('experience_type', 'is_current'))
    OR (table_name = 'resume_skill' AND column_name = 'proficiency')
    OR (table_name = 'job_preference' AND column_name = 'direction_code')
    OR (table_name = 'job' AND column_name IN
      ('direction_code', 'required_abilities_source', 'required_abilities_version'))
  );

-- 必须返回0行；父job.id必须为有符号BIGINT主键，与新表job_id一致。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'job_required_ability';

SELECT table_name, column_name, column_type, column_key
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('resume', 'resume_experience', 'resume_skill', 'job_preference', 'job')
  AND column_name = 'id'
ORDER BY table_name;

-- B. 增量DDL。仅在A核对通过、备份完成且批准执行迁移后逐步运行。
ALTER TABLE resume
  ADD COLUMN career_stage VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    COMMENT 'STUDENT/NEW_GRAD/SOCIAL/UNKNOWN，用户申报求职阶段',
  ADD COLUMN formal_experience_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    COMMENT 'NONE/HAS_EXPERIENCE/UNKNOWN，正式经历确认';

ALTER TABLE resume_experience
  ADD COLUMN experience_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    COMMENT 'FORMAL/INTERNSHIP/OTHER/UNKNOWN，旧经历不猜测分类',
  ADD COLUMN is_current TINYINT(1) NULL DEFAULT NULL
    COMMENT '1至今/0已结束/NULL未确认，不从end_date推断';

ALTER TABLE resume_skill
  ADD COLUMN proficiency VARCHAR(16) NULL DEFAULT NULL
    COMMENT 'AWARE/FAMILIAR/PROFICIENT/EXPERT，NULL未填，非能力认证';

ALTER TABLE job_preference
  ADD COLUMN direction_code VARCHAR(32) NULL DEFAULT NULL
    COMMENT 'BACKEND/FRONTEND，NULL未确认，不从任意职位文本猜测';

ALTER TABLE job
  ADD COLUMN direction_code VARCHAR(32) NULL DEFAULT NULL
    COMMENT 'BACKEND/FRONTEND，NULL未确认',
  ADD COLUMN required_abilities_source VARCHAR(16) NOT NULL DEFAULT 'LEGACY_TEXT'
    COMMENT 'LEGACY_TEXT旧字符串/STRUCTURED逐条要求，服务端控制',
  ADD COLUMN required_abilities_version BIGINT NOT NULL DEFAULT 0
    COMMENT '能力要求版本，服务端递增，写入检查预期版本';

CREATE TABLE job_required_ability (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '岗位必备能力条目ID',
  job_id BIGINT NOT NULL COMMENT '所属岗位ID',
  raw_text VARCHAR(500) NOT NULL COMMENT '单条能力要求原文，不被派生标准名称覆盖',
  ability_name VARCHAR(50) NULL DEFAULT NULL COMMENT '用户明确的能力名称，可空',
  proficiency VARCHAR(16) NULL DEFAULT NULL COMMENT '可选程度AWARE/FAMILIAR/PROFICIENT/EXPERT',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '服务端按数组顺序生成，非负',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_job_required_ability_order (job_id, sort_order, id),
  CONSTRAINT fk_job_required_ability_job FOREIGN KEY (job_id)
    REFERENCES job(id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='岗位逐条必备能力，STRUCTURED模式唯一评分来源';

-- C. 只读后置核对。迁移窗口内没有业务写入时，下方默认状态应覆盖所有旧行。
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'resume' AND column_name IN ('career_stage', 'formal_experience_status'))
    OR (table_name = 'resume_experience' AND column_name IN ('experience_type', 'is_current'))
    OR (table_name = 'resume_skill' AND column_name = 'proficiency')
    OR (table_name = 'job_preference' AND column_name = 'direction_code')
    OR (table_name = 'job' AND column_name IN
      ('direction_code', 'required_abilities_source', 'required_abilities_version'))
  )
ORDER BY table_name, column_name;

SELECT COUNT(*) AS unexpected_rows
FROM resume
WHERE career_stage <> 'UNKNOWN' OR formal_experience_status <> 'UNKNOWN';

SELECT COUNT(*) AS unexpected_rows
FROM resume_experience
WHERE experience_type <> 'UNKNOWN' OR is_current IS NOT NULL;

SELECT COUNT(*) AS unexpected_rows FROM resume_skill WHERE proficiency IS NOT NULL;
SELECT COUNT(*) AS unexpected_rows FROM job_preference WHERE direction_code IS NOT NULL;
SELECT COUNT(*) AS unexpected_rows
FROM job
WHERE direction_code IS NOT NULL
   OR required_abilities_source <> 'LEGACY_TEXT'
   OR required_abilities_version <> 0;
SELECT COUNT(*) AS new_ability_rows FROM job_required_ability;

SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'job_required_ability'
ORDER BY index_name, seq_in_index;

SELECT constraint_name, update_rule, delete_rule, referenced_table_name
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE() AND table_name = 'job_required_ability';

-- 还需以迁移前导出的旧列清单逐行比较备份：上方计数不能证明旧内容全部未变。
-- 本文件不包含自动DROP或回滚SQL；应用回退优先停用新能力、保留新增结构和用户数据。
-- 已写入结构化要求后，旧应用不得恢复写入岗位能力，否则会失去来源一致性检查。
