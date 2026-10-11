package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.vo.JobListVO;
import com.recruit.smartrecruit.vo.CompanyJobListVO;
import com.recruit.smartrecruit.vo.JobStatusCountVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface JobMapper {
    // 岗位数量独立聚合，避免关联投递后一个岗位被重复计数。
    @Select("""
            SELECT
                COUNT(*) AS `all`,
                COUNT(CASE WHEN status = #{activeStatus.code} THEN 1 END) AS active,
                COUNT(CASE WHEN status = #{pausedStatus.code} THEN 1 END) AS paused,
                COUNT(CASE WHEN status = #{draftStatus.code} THEN 1 END) AS draft,
                COUNT(CASE WHEN status = #{endedStatus.code} THEN 1 END) AS ended
            FROM job
            WHERE company_id = #{companyId}
            """)
    JobStatusCountVO countCompanyJobs(@Param("companyId") Long companyId,
                                    @Param("activeStatus") JobStatus activeStatus,
                                    @Param("pausedStatus") JobStatus pausedStatus,
                                    @Param("draftStatus") JobStatus draftStatus,
                                    @Param("endedStatus") JobStatus endedStatus);

    @Insert("""
            INSERT INTO job
            (
                company_id,
                title,
                description,
                salary_min,
                salary_max,
                city,
                experience,
                education,
                category,
                team,
                headcount,
                employment,
                work_mode,
                address,
                salary_unit,
                negotiable,
                summary,
                responsibilities,
                requirements,
                bonus,
                required_skills,
                preferred_skills,
                benefits,
                deadline,
                status,
                published_at
            )VALUES
            (   #{companyId},
                #{title},
                #{description},
                #{salaryMin},
                #{salaryMax},
                #{city},
                #{experience},
                #{education},
                #{category},
                #{team},
                #{headcount},
                #{employment},
                #{workMode},
                #{address},
                #{salaryUnit},
                #{negotiable},
                #{summary},
                #{responsibilities},
                #{requirements},
                #{bonus},
                #{requiredSkills},
                #{preferredSkills},
                #{benefits},
                #{deadline},
                #{status},
                #{publishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(Job job);

    @Update("""
            UPDATE job
            SET status=#{status.code}
            WHERE id=#{id} AND status=#{expectedStatus}
            """)
    int updateJobStatus(@Param("id") Long id, @Param("status") JobStatus status,
                        @Param("expectedStatus") Integer expectedStatus);

    // 发布岗位：同时写入发布时间
    @Update("""
            UPDATE job
            SET status=#{status.code},
                published_at=#{publishedAt}
            WHERE id=#{id} AND status=#{expectedStatus}
            """)
    int publishJob(@Param("id") Long id,
                    @Param("status") JobStatus status,
                    @Param("publishedAt") LocalDateTime publishedAt,
                    @Param("expectedStatus") Integer expectedStatus);

    // 结束招聘：同时写入结束时间
    @Update("""
            UPDATE job
            SET status=#{status.code},
                ended_at=#{endedAt}
            WHERE id=#{id} AND status=#{expectedStatus}
            """)
    int endJob(@Param("id") Long id,
                @Param("status") JobStatus status,
                @Param("endedAt") LocalDateTime endedAt,
                @Param("expectedStatus") Integer expectedStatus);

    @Select("""
            SELECT *
            FROM job WHERE id=#{id}
            """)
    Job findById(Long id);

    // 岗位写操作在事务中锁定一行，使同一岗位的修改和时间线顺序串行。
    @Select("""
            SELECT * FROM job WHERE id=#{id} FOR UPDATE
            """)
    Job findByIdForUpdate(Long id);

    @Update("""
            UPDATE job
            SET 
            title=#{newJob.title},
            description=#{newJob.description},
            salary_min=#{newJob.salaryMin},
            salary_max=#{newJob.salaryMax},
            city=#{newJob.city},
            experience=#{newJob.experience},
            education=#{newJob.education},
            category=#{newJob.category},
            team=#{newJob.team},
            headcount=#{newJob.headcount},
            employment=#{newJob.employment},
            work_mode=#{newJob.workMode},
            address=#{newJob.address},
            salary_unit=#{newJob.salaryUnit},
            negotiable=#{newJob.negotiable},
            summary=#{newJob.summary},
            responsibilities=#{newJob.responsibilities},
            requirements=#{newJob.requirements},
            bonus=#{newJob.bonus},
            required_skills=#{newJob.requiredSkills},
            preferred_skills=#{newJob.preferredSkills},
            benefits=#{newJob.benefits},
            deadline=#{newJob.deadline}
            WHERE id=#{newJob.id} AND status=#{newJob.status}
            """)
    int update(@Param("newJob") Job newJob);

    @Delete("""
            DELETE
            FROM job
            WHERE id=#{id} AND status=#{draftStatus.code}
            """)
    int delete(@Param("id") Long id, @Param("draftStatus") JobStatus draftStatus);


    // 求职者浏览岗位：查询指定状态的岗位列表，支持关键字搜索，分页由 PageHelper 处理
    @Select("""
            <script>
            SELECT
                id,
                title,
                category,
                team,
                employment,
                status,
                city,
                experience,
                education,
                salary_min,
                salary_max,
                salary_unit,
                negotiable,
                update_time
            FROM job
            WHERE status=#{jobStatus.code}
            <if test="normalizedKeyword != null and normalizedKeyword != ''">
                AND (
                    title LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR category LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR city LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR team LIKE CONCAT('%', #{normalizedKeyword}, '%')
                )
            </if>
            ORDER BY update_time DESC, id DESC
            </script>
            """)
    List<JobListVO> findJob(@Param("normalizedKeyword") String normalizedKeyword,
                            @Param("jobStatus") JobStatus jobStatus);

    // 企业查看本公司岗位：按公司过滤，支持关键字搜索与状态筛选，分页由 PageHelper 处理
    @Select("""
            <script>
            SELECT
                id,
                title,
                category,
                team,
                employment,
                status,
                city,
                experience,
                education,
                salary_min,
                salary_max,
                salary_unit,
                negotiable,
                update_time
            FROM job
            WHERE company_id=#{companyId}
            <if test="keyword != null and keyword != ''">
                AND (
                    title LIKE CONCAT('%', #{keyword}, '%')
                    OR category LIKE CONCAT('%', #{keyword}, '%')
                    OR city LIKE CONCAT('%', #{keyword}, '%')
                    OR team LIKE CONCAT('%', #{keyword}, '%')
                )
            </if>
            <if test="status != null">
                AND status=#{status.code}
            </if>
            ORDER BY update_time DESC, id DESC
            </script>
            """)
    List<CompanyJobListVO> findCompanyJob(@Param("companyId") Long companyId,
                                   @Param("keyword") String keyword,
                                   @Param("status") JobStatus status);
}
