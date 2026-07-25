package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Application;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApplicationMapper {
    @Select("""
            SELECT *
            FROM job_application
            WHERE user_id=#{userId} AND
            job_id=#{jobId}
            """)
    Application findByUserIdAndJobId(Long userId, Long jobId);
    @Insert("""
            INSERT INTO job_application
            (
                user_id,
                job_id,
                resume_id,
                status
            )VALUES
            (
                #{userId},
                #{jobId},
                #{resumeId},
                #{status}
            )
            """)
    void apply(Application newApplication);
    @Select("""
            SELECT ja.*
            FROM job_application ja
            JOIN job j ON ja.job_id = j.id
            WHERE j.company_id = #{companyId}
            """)
    List<Application> findCompanyApplication(Long companyId);
    @Select("""
            SELECT *
            FROM job_application
            WHERE id=#{id}
            """)
    Application findCompanyApplicationById(Long id);
    @Update("""
            UPDATE job_application
            SET
            status=#{status}
            WHERE id=#{id}
            """)
    void updateStatus(Long id, Integer status);
}
