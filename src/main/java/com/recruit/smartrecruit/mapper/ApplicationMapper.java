package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Application;
import com.recruit.smartrecruit.dto.JobApplicationCountDTO;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.vo.ApplicationVO;
import com.recruit.smartrecruit.vo.CompanyJobOverviewVO;
import com.recruit.smartrecruit.vo.JobRecruitmentProgressVO;
import com.recruit.smartrecruit.vo.RecentApplicationVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApplicationMapper {
    // 仅聚合当前企业、当前页已发布岗位；不关联账号或简历展示表。
    @Select("""
            <script>
            SELECT ja.job_id,
                COUNT(*) AS application_count,
                COUNT(CASE WHEN ja.status = #{pendingStatus.code} THEN 1 END)
                    AS pending_application_count,
                COUNT(CASE WHEN ja.status = #{interviewStatus.code} THEN 1 END)
                    AS current_interview_application_count
            FROM job_application ja
            JOIN job j ON j.id = ja.job_id
            WHERE j.company_id = #{companyId}
                AND j.id IN
                <foreach collection="jobIds" item="jobId" open="(" separator="," close=")">
                    #{jobId}
                </foreach>
                AND j.status IN
                <foreach collection="publishedStatuses" item="jobStatus" open="(" separator="," close=")">
                    #{jobStatus.code}
                </foreach>
            GROUP BY ja.job_id
            </script>
            """)
    List<JobApplicationCountDTO> countApplicationsByJobIds(
            @Param("companyId") Long companyId,
            @Param("jobIds") List<Long> jobIds,
            @Param("publishedStatuses") List<JobStatus> publishedStatuses,
            @Param("pendingStatus") ApplicationStatus pendingStatus,
            @Param("interviewStatus") ApplicationStatus interviewStatus);

    // 总览只统计投递记录，不依赖求职者账号或简历当前是否存在。
    @Select("""
            <script>
            SELECT
                COUNT(*) AS application_count,
                COUNT(CASE WHEN ja.status = #{pendingStatus.code} THEN 1 END)
                    AS pending_application_count,
                COUNT(CASE WHEN ja.status = #{interviewStatus.code} THEN 1 END)
                    AS current_interview_application_count,
                CASE
                    WHEN COUNT(CASE WHEN ja.has_interviewed IS NULL
                        AND ja.status != #{interviewStatus.code} THEN 1 END) > 0
                    THEN NULL
                    ELSE COUNT(CASE WHEN ja.has_interviewed = TRUE
                        OR ja.status = #{interviewStatus.code} THEN 1 END)
                END AS interview_application_count
            FROM job_application ja
            JOIN job j ON j.id = ja.job_id
            WHERE j.company_id = #{companyId}
                AND j.status IN
                <foreach collection="publishedStatuses" item="jobStatus" open="(" separator="," close=")">
                    #{jobStatus.code}
                </foreach>
            </script>
            """)
    CompanyJobOverviewVO countCompanyApplications(
            @Param("companyId") Long companyId,
            @Param("publishedStatuses") List<JobStatus> publishedStatuses,
            @Param("pendingStatus") ApplicationStatus pendingStatus,
            @Param("interviewStatus") ApplicationStatus interviewStatus);

    // Service 已在同一事务内验证岗位存在及企业归属，此处只统计该岗位的投递记录。
    @Select("""
            SELECT
                COUNT(*) AS application_count,
                COUNT(CASE WHEN status = #{pendingStatus.code} THEN 1 END)
                    AS pending_application_count,
                COUNT(CASE WHEN status = #{interviewStatus.code} THEN 1 END)
                    AS current_interview_application_count,
                CASE
                    WHEN COUNT(CASE WHEN has_interviewed IS NULL
                        AND status != #{interviewStatus.code} THEN 1 END) > 0
                    THEN NULL
                    ELSE COUNT(CASE WHEN has_interviewed = TRUE
                        OR status = #{interviewStatus.code} THEN 1 END)
                END AS interview_application_count
            FROM job_application
            WHERE job_id = #{jobId}
            """)
    JobRecruitmentProgressVO countJobApplications(
            @Param("jobId") Long jobId,
            @Param("pendingStatus") ApplicationStatus pendingStatus,
            @Param("interviewStatus") ApplicationStatus interviewStatus);

    // Service 已验证岗位归属；关联信息缺失时仍保留投递，并回退展示姓名。
    @Select("""
            SELECT
                ja.id AS application_id,
                COALESCE(
                    NULLIF(TRIM(r.real_name), ''),
                    NULLIF(TRIM(u.nickname), ''),
                    '未填写姓名'
                ) AS applicant_name,
                ja.status,
                ja.create_time
            FROM job_application ja
            LEFT JOIN resume r ON r.id = ja.resume_id
            LEFT JOIN sys_user u ON u.id = ja.user_id
            WHERE ja.job_id = #{jobId}
            ORDER BY ja.create_time DESC, ja.id DESC
            LIMIT #{limit}
            """)
    List<RecentApplicationVO> findRecentByJobId(@Param("jobId") Long jobId,
                                              @Param("limit") int limit);

    // 状态修改读取原始投递，不依赖简历、账号等展示关联信息是否存在。
    @Select("""
            SELECT id, user_id, job_id, resume_id, status, has_interviewed, create_time, update_time
            FROM job_application
            WHERE id=#{id}
            """)
    Application findById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM job_application
            WHERE user_id=#{userId} AND
            job_id=#{jobId}
            """)
    Application findByUserIdAndJobId(@Param("userId") Long userId, @Param("jobId") Long jobId);
    @Insert("""
            INSERT INTO job_application
            (
                user_id,
                job_id,
                resume_id,
                status,
                has_interviewed
            )VALUES
            (
                #{userId},
                #{jobId},
                #{resumeId},
                #{status},
                FALSE
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
            (r.id IS NOT NULL) AS resume_available,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        LEFT JOIN sys_user u
            ON ja.user_id = u.id
        JOIN job j
            ON ja.job_id = j.id
        LEFT JOIN company c
            ON j.company_id = c.id
        LEFT JOIN resume r
            ON ja.resume_id = r.id
        WHERE j.company_id = #{companyId}
        ORDER BY ja.create_time DESC, ja.id DESC
        """)
    List<ApplicationVO> findCompanyApplication(@Param("companyId") Long companyId);
    // 先根据旧状态保留面试证据，再修改状态；原状态条件拒绝校验后的并发变化。
    @Update("""
            UPDATE job_application
            SET
            has_interviewed = CASE
                WHEN #{targetStatus.code} = #{interviewStatus.code}
                    OR status = #{interviewStatus.code} THEN TRUE
                ELSE has_interviewed
            END,
            status=#{targetStatus.code}
            WHERE id=#{id}
                AND job_id=#{jobId}
                AND status=#{expectedStatus.code}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("jobId") Long jobId,
                     @Param("expectedStatus") ApplicationStatus expectedStatus,
                     @Param("targetStatus") ApplicationStatus targetStatus,
                     @Param("interviewStatus") ApplicationStatus interviewStatus);
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
            (r.id IS NOT NULL) AS resume_available,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        LEFT JOIN sys_user u
            ON ja.user_id = u.id
        LEFT JOIN job j
            ON ja.job_id = j.id
        LEFT JOIN company c
            ON j.company_id = c.id
        LEFT JOIN resume r
            ON ja.resume_id = r.id
        WHERE ja.user_id = #{userId}
        ORDER BY ja.create_time DESC, ja.id DESC
        """)
    List<ApplicationVO> findJobseekerApplication(@Param("userId") Long userId);
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
            (r.id IS NOT NULL) AS resume_available,
            ja.status,
            ja.create_time,
            ja.update_time
        FROM job_application ja
        LEFT JOIN sys_user u
            ON ja.user_id = u.id
        LEFT JOIN job j
            ON ja.job_id = j.id
        LEFT JOIN company c
            ON j.company_id = c.id
        LEFT JOIN resume r
            ON ja.resume_id = r.id
        WHERE ja.id = #{id}
        """)
    ApplicationVO findApplicationDetail(@Param("id") Long id);

}
