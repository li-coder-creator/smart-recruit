-- 我的投递查询兼容迁移，2026-10-25。
-- 执行前停止业务写入、备份 job_application 数据和结构并核对目标库。
-- 仅适用于尚无 history_recorded_from_start、新索引和两张新表的 smart_recruit。
-- DDL 隐式提交；逐条执行并记录结果。发生错误立即停止，不重跑、不自动删除对象。
-- 保留现有八列、唯一索引及外键，不修改或补造旧业务记录。

ALTER TABLE job_application
  ADD COLUMN history_recorded_from_start TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '从投递创建起即完整记录事件；旧0，新链路显式1',
  ADD INDEX idx_application_user_time (user_id, create_time, id),
  ADD INDEX idx_application_user_status_time (user_id, status, create_time, id);

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
