package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.vo.ApplicationVO;
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
        SELECT
            ja.id,
            ja.user_id,
            u.username,
            ja.job_id,
            j.title AS job_title,
            c.name AS company_name,
            ja.resume_id,
            r.title AS resume_title,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        JOIN sys_user u
            ON ja.user_id = u.id
        JOIN job j
            ON ja.job_id = j.id
        JOIN company c
            ON j.company_id = c.id
        JOIN resume r
            ON ja.resume_id = r.id
        WHERE j.company_id = #{companyId}
        ORDER BY ja.create_time DESC
        """)
    List<ApplicationVO> findCompanyApplication(Long companyId);
    @Update("""
            UPDATE job_application
            SET
            status=#{status}
            WHERE id=#{id}
            """)
    void updateStatus(Long id, Integer status);
    @Select("""
        SELECT
            ja.id,
            ja.user_id,
            u.username,
            ja.job_id,
            j.title AS job_title,
            c.name AS company_name,
            ja.resume_id,
            r.title AS resume_title,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        JOIN sys_user u
            ON ja.user_id = u.id
        JOIN job j
            ON ja.job_id = j.id
        JOIN company c
            ON j.company_id = c.id
        JOIN resume r
            ON ja.resume_id = r.id
        WHERE ja.user_id = #{userId}
        ORDER BY ja.create_time DESC
        """)
    List<ApplicationVO> findJobseekerApplication(Long userId);
    @Select("""
        SELECT
            ja.id,
            ja.user_id,
            u.username,
            ja.job_id,
            j.title AS job_title,
            c.name AS company_name,
            ja.resume_id,
            r.title AS resume_title,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        JOIN sys_user u
            ON ja.user_id = u.id
        JOIN job j
            ON ja.job_id = j.id
        JOIN company c
            ON j.company_id = c.id
        JOIN resume r
            ON ja.resume_id = r.id
        WHERE ja.id = #{id}
        """)
    ApplicationVO findApplicationDetail(Long id);

}
