package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.JobBrowseSort;
import com.recruit.smartrecruit.entity.enums.JobSalaryUnit;
import com.recruit.smartrecruit.entity.enums.RequiredAbilitiesSource;
import com.recruit.smartrecruit.vo.JobListVO;
import com.recruit.smartrecruit.vo.CompanyJobListVO;
import com.recruit.smartrecruit.vo.JobStatusCountVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface JobMapper {
    // 企业主页专用：企业归属与招聘中状态同时限制在 SQL 中。
    @Select("""
            SELECT j.id, j.title, j.category, j.team, j.employment, j.status, j.city,
                   j.experience, j.education, j.salary_min, j.salary_max, j.salary_unit,
                   j.negotiable, j.update_time, j.company_id,
                   c.name AS company_name, c.logo AS company_logo,
                   j.summary, j.benefits, j.required_skills, j.preferred_skills,
                   j.direction_code, j.required_abilities_source, j.required_abilities_version,
                   j.published_at, j.deadline
            FROM job j
            LEFT JOIN company c ON c.id = j.company_id
            WHERE j.company_id = #{companyId}
              AND j.status = #{status.code}
            ORDER BY j.update_time DESC, j.id DESC
            """)
    List<JobListVO> findActiveJobsByCompany(@Param("companyId") Long companyId,
                                          @Param("status") JobStatus status);

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
                direction_code,
                required_abilities_source,
                required_abilities_version,
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
                #{directionCode},
                #{requiredAbilitiesSource},
                #{requiredAbilitiesVersion},
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
            direction_code=#{newJob.directionCode},
            required_abilities_source=#{newJob.requiredAbilitiesSource},
            required_abilities_version=#{newJob.requiredAbilitiesVersion},
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


    // 企业关联为多对一，不联查能力子表，避免一岗多行破坏分页和总数。
    // 求职者浏览岗位：查询指定状态的岗位列表，支持关键字搜索，分页由 PageHelper 处理
    @Select("""
            <script>
            SELECT
                j.id, j.title, j.category, j.team, j.employment, j.status, j.city,
                j.experience, j.education, j.salary_min, j.salary_max, j.salary_unit,
                j.negotiable, j.update_time, j.company_id,
                c.name AS company_name, c.logo AS company_logo,
                j.summary, j.benefits, j.required_skills, j.preferred_skills,
                j.direction_code, j.required_abilities_source, j.required_abilities_version,
                j.published_at, j.deadline
            FROM job j
            JOIN company c ON c.id = j.company_id
            WHERE j.status=#{jobStatus.code}
              AND c.status=#{companyStatus}
            <if test="normalizedKeyword != null and normalizedKeyword != ''">
                AND (
                    j.title LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR j.category LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR j.city LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR j.team LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR c.name LIKE CONCAT('%', #{normalizedKeyword}, '%')
                    OR (j.required_abilities_source=#{legacySource}
                        AND j.required_skills LIKE CONCAT('%', #{normalizedKeyword}, '%'))
                    OR (j.required_abilities_source=#{structuredSource}
                        AND EXISTS (
                            SELECT 1 FROM job_required_ability a
                            WHERE a.job_id=j.id
                              AND (a.raw_text LIKE CONCAT('%', #{normalizedKeyword}, '%')
                                   OR a.ability_name LIKE CONCAT('%', #{normalizedKeyword}, '%'))
                        ))
                    OR j.preferred_skills LIKE CONCAT('%', #{normalizedKeyword}, '%')
                )
            </if>
            <choose>
                <when test="sort.name() == 'SALARY_DESC'">
                    ORDER BY
                        CASE WHEN j.salary_unit=#{monthlyUnit} AND j.negotiable=FALSE
                                  AND j.salary_min &gt; 0 AND j.salary_max &gt;= j.salary_min
                             THEN j.salary_max END DESC,
                        j.update_time DESC, j.id DESC
                </when>
                <otherwise>
                    ORDER BY j.update_time DESC, j.id DESC
                </otherwise>
            </choose>
            </script>
            """)
    List<JobListVO> findJob(@Param("normalizedKeyword") String normalizedKeyword,
                           @Param("jobStatus") JobStatus jobStatus,
                           @Param("companyStatus") CompanyStatus companyStatus,
                           @Param("sort") JobBrowseSort sort,
                           @Param("monthlyUnit") JobSalaryUnit monthlyUnit,
                           @Param("legacySource") RequiredAbilitiesSource legacySource,
                           @Param("structuredSource") RequiredAbilitiesSource structuredSource);

    // 企业查看本公司岗位：按公司过滤，支持关键字搜索与状态筛选，分页由 PageHelper 处理
    @Select("""
            <script>
            SELECT
                j.id, j.title, j.category, j.team, j.employment, j.status, j.city,
                j.experience, j.education, j.salary_min, j.salary_max, j.salary_unit,
                j.negotiable, j.update_time, j.company_id,
                c.name AS company_name, c.logo AS company_logo,
                j.summary, j.benefits, j.required_skills, j.preferred_skills,
                j.direction_code, j.required_abilities_source, j.required_abilities_version,
                j.published_at, j.deadline
            FROM job j
            LEFT JOIN company c ON c.id = j.company_id
            WHERE j.company_id=#{companyId}
            <if test="keyword != null and keyword != ''">
                AND (
                    j.title LIKE CONCAT('%', #{keyword}, '%')
                    OR j.category LIKE CONCAT('%', #{keyword}, '%')
                    OR j.city LIKE CONCAT('%', #{keyword}, '%')
                    OR j.team LIKE CONCAT('%', #{keyword}, '%')
                )
            </if>
            <if test="status != null">
                AND j.status=#{status.code}
            </if>
            ORDER BY j.update_time DESC, j.id DESC
            </script>
            """)
    List<CompanyJobListVO> findCompanyJob(@Param("companyId") Long companyId,
                                   @Param("keyword") String keyword,
                                   @Param("status") JobStatus status);
}
