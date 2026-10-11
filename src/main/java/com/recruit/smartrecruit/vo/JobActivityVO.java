package com.recruit.smartrecruit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 岗位操作记录。
 * 按时间和 ID 倒序返回，由后端在业务成功时与业务修改同事务生成，不提供前端新增记录的接口。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobActivityVO {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 操作编码：CREATE_DRAFT/PUBLISH/UPDATE/PAUSE/RESUME/END
     */
    private String operation;

    /**
     * 操作文字
     */
    private String operationText;

    /**
     * 操作者展示名称
     */
    private String operatorName;

    /**
     * 操作发生时间
     */
    private LocalDateTime createTime;
}
