-- 阶段一数据库变更草案，2026-10-25，基于 smart_recruit / MySQL 8.0.13 的只读盘点。
-- 状态：仅设计，未执行；不是正式迁移。
-- 活动语句只有 SELECT / SHOW；全部 DDL 在块注释中，整段运行不会建表改表。
-- 后续重新核对目标库、备份、停止写入，经授权提取 DDL 为正式迁移，再开发和回归。
-- 不使用 IF NOT EXISTS 掩盖结构差异；已迁移 / 部分迁移环境禁止直接重跑。
-- MySQL DDL 会隐式提交，不能靠 BEGIN / ROLLBACK 撤销整批迁移。

-- 一、正式迁移前的只读核对。
SELECT DATABASE() AS current_database, VERSION() AS mysql_version,
       @@session.time_zone AS session_time_zone,
       TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), CURRENT_TIMESTAMP()) AS utc_offset_minutes;
SHOW CREATE TABLE job_application;
SHOW INDEX FROM job_application;
SELECT table_name, engine FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('job_application', 'application_event', 'application_interview');
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'job_application'
ORDER BY ordinal_position;
SELECT status, has_interviewed, COUNT(*) AS application_count
FROM job_application GROUP BY status, has_interviewed ORDER BY status, has_interviewed;
SELECT COUNT(*) AS missing_resume_count
FROM job_application a LEFT JOIN resume r ON r.id = a.resume_id WHERE r.id IS NULL;
SELECT user_id, job_id, COUNT(*) AS duplicate_count
FROM job_application GROUP BY user_id, job_id HAVING COUNT(*) > 1;

/*
================ 以下为待审阅 DDL，不执行 ================

-- 二、现有八列和约束保留。
-- 原记录与旧版本应用的新记录均默认0；新应用仅在初始事件同事务成功时显式写1。
ALTER TABLE job_application
  ADD COLUMN history_recorded_from_start TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '从投递创建起即完整记录事件；旧0，新链路显式1',
  ADD INDEX idx_application_user_time (user_id, create_time, id),
  ADD INDEX idx_application_user_status_time (user_id, status, create_time, id);

-- 三、先创建面试表，供事件表关联；有符号 BIGINT 与原投递表匹配。
CREATE TABLE application_interview (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '面试场次ID',
  application_id BIGINT NOT NULL COMMENT '所属投递ID',
  start_time DATETIME(3) NOT NULL COMMENT 'Asia/Shanghai 开始时间',
  end_time DATETIME(3) NOT NULL COMMENT 'Asia/Shanghai 结束时间',
  mode VARCHAR(16) NOT NULL COMMENT 'ONLINE/OFFLINE',
  platform VARCHAR(50) NULL COMMENT '线上平台，线下为空',
  meeting_url VARCHAR(1000) NULL COMMENT '仅授权双方可读的 http/https 会议链接',
  address VARCHAR(255) NULL COMMENT '线下面试地址，线上为空',
  candidate_note VARCHAR(500) NULL COMMENT '求职者可见说明，非内部评价',
  status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/CANCELLED',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '成功变更递增的并发版本',
  cancel_reason VARCHAR(500) NULL COMMENT '取消时的可见原因',
  cancel_source VARCHAR(32) NULL COMMENT 'COMPANY/APPLICATION_STATUS，未取消为空',
  cancelled_at DATETIME(3) NULL COMMENT '实际取消时间',
  created_by BIGINT NOT NULL COMMENT '创建操作者账号ID，不设账号级联外键',
  updated_by BIGINT NOT NULL COMMENT '最后变更操作者账号ID',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_interview_application_time (application_id, start_time, id),
  KEY idx_interview_application_validity (application_id, status, end_time),
  CONSTRAINT fk_application_interview_application
    FOREIGN KEY (application_id) REFERENCES job_application (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='投递面试安排，保留取消与历史场次';

-- 不建立 (application_id,status) 唯一索引，它会错误限制多个取消/过期场次。
-- 最多一场 SCHEDULED 且 end_time>now：全部排期写路径统一锁父投递行后检查并写入。
-- 到期不证明实际参加，时间、枚举、跨字段必填、归属及版本由 Service 校验。

-- 四、只追加的真实事件。旧数据不执行 INSERT SELECT 补造历史。
CREATE TABLE application_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  application_id BIGINT NOT NULL COMMENT '所属投递ID',
  event_type VARCHAR(32) NOT NULL
    COMMENT 'APPLIED/STATUS_CHANGED/INTERVIEW_SCHEDULED/INTERVIEW_RESCHEDULED/INTERVIEW_CANCELLED',
  from_status TINYINT NULL COMMENT '真实原状态，非状态事件为空',
  to_status TINYINT NULL COMMENT '真实目标状态，APPLIED为0，面试事件为空',
  interview_id BIGINT NULL COMMENT '排期事件场次ID，其它为空',
  operator_user_id BIGINT NULL COMMENT '服务端操作者，SYSTEM可空',
  actor_type VARCHAR(16) NOT NULL COMMENT 'JOB_SEEKER/COMPANY/SYSTEM',
  occurred_at DATETIME(3) NOT NULL COMMENT '服务端生成的真实事件时间',
  detail_json JSON NULL COMMENT '白名单时间、方式和取消原因，不复制链接/地址',
  PRIMARY KEY (id),
  KEY idx_application_event_time (application_id, occurred_at, id),
  KEY idx_application_event_interview (interview_id),
  CONSTRAINT fk_application_event_application
    FOREIGN KEY (application_id) REFERENCES job_application (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_application_event_interview
    FOREIGN KEY (interview_id) REFERENCES application_interview (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='真实投递流转和面试变更事件，仅追加';

-- Service 必须检查 interview_id 属于相同 application_id。
-- 操作者账号删除不级联清除历史，不能靠当前账号存在性决定事件是否显示。
-- 不依赖数据库 CHECK 代替业务校验；保留原唯一约束和 fk_application_job。

-- 五、提取正式迁移后再执行的核对语句。
SHOW CREATE TABLE job_application;
SHOW CREATE TABLE application_interview;
SHOW CREATE TABLE application_event;
SHOW INDEX FROM job_application;
SELECT history_recorded_from_start, COUNT(*) AS application_count
FROM job_application GROUP BY history_recorded_from_start;
SELECT COUNT(*) AS interview_count FROM application_interview;
SELECT COUNT(*) AS event_count FROM application_event;
-- 没有新写入时：原投递数量不变，历史标记全0，新表为空。
-- 比对迁移前后旧8列投影，状态、has_interviewed、创建和更新时间不应因补历史改变。
-- 新链路上线后：新投递标记1，且同事务存在真实 APPLIED 初始事件。

================ 以上为草案，未执行 ================
*/

-- 六、失败与恢复：
-- 1. 先备份结构、旧8列投影和业务数据，核对目标库。
-- 2. DDL 中途失败时停止继续部署，记录成功语句和实际 SHOW CREATE TABLE。
-- 3. 不自动 DROP 表、删列、清空历史，不直接重跑；无数据才单独评估撤回结构。
-- 4. 旧应用可暂留新增结构；有新事件或排期后，不因回退应用而删除证据。
-- 5. 正式迁移前重查对象不存在、索引不重复、外键类型匹配和存量状态合法。
