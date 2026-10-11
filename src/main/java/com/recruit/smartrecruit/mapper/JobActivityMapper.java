package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.JobActivity;
import com.recruit.smartrecruit.vo.JobActivityVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface JobActivityMapper {
    // 在写入时保存展示名称快照，后续修改昵称不改变旧记录。
    @Insert("""
            INSERT INTO job_activity
                (job_id, operator_user_id, operator_name, operation)
            SELECT
                #{jobId}, u.id,
                COALESCE(NULLIF(TRIM(u.nickname), ''), u.username),
                #{operation}
            FROM sys_user u
            WHERE u.id = #{operatorUserId}
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(JobActivity activity);

    @Select("""
            SELECT id, operation, operator_name, create_time
            FROM job_activity
            WHERE job_id = #{jobId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<JobActivityVO> findRecentByJobId(@Param("jobId") Long jobId,
                                        @Param("limit") int limit);
}
