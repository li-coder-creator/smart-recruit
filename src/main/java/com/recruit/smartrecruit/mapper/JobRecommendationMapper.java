package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.dto.RecommendationJobRow;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface JobRecommendationMapper {
    @Select("SELECT id,user_id,title,career_stage,formal_experience_status FROM resume WHERE id=#{resumeId} AND user_id=#{userId}")
    Resumebasic findOwnedResume(@Param("resumeId") Long resumeId,@Param("userId") Long userId);
    @Select("SELECT expected_position,expected_city,salary_min,salary_max,direction_code FROM job_preference WHERE resume_id=#{resumeId}")
    JobPreference preference(Long resumeId);
    @Select("SELECT id,degree,start_date,end_date FROM resume_education WHERE resume_id=#{resumeId} ORDER BY id")
    List<ResumeEducation> education(Long resumeId);
    @Select("SELECT id,start_date,end_date,experience_type,is_current FROM resume_experience WHERE resume_id=#{resumeId} ORDER BY id")
    List<ResumeExperience> experience(Long resumeId);
    @Select("SELECT id,skill_name,description,proficiency FROM resume_skill WHERE resume_id=#{resumeId} ORDER BY sort_order,id")
    List<ResumeSkill> skills(Long resumeId);
    @Select("""
            <script>
            SELECT j.id,j.company_id,j.title,j.category,j.city,j.employment,j.team,
                   j.salary_min,j.salary_max,j.salary_unit,j.negotiable,j.summary,j.benefits,
                   j.required_skills,j.preferred_skills,j.experience,j.education,j.deadline,j.published_at,
                   j.direction_code,j.required_abilities_source,j.required_abilities_version,
                   c.name AS company_name,c.logo AS company_logo,
                   EXISTS(SELECT 1 FROM job_application a WHERE a.user_id=#{userId} AND a.job_id=j.id) AS applied
            FROM job j JOIN company c ON c.id=j.company_id
            WHERE j.status=#{active.code} AND c.status=#{approved}
            <if test="excludeExpired">AND (j.deadline IS NULL OR j.deadline &gt;= #{asOfDate})</if>
            <if test="excludeApplied">
              AND NOT EXISTS(SELECT 1 FROM job_application a WHERE a.user_id=#{userId} AND a.job_id=j.id)
            </if>
            ORDER BY j.id
            </script>
            """)
    List<RecommendationJobRow> jobs(@Param("userId") Long userId,@Param("active") JobStatus active,
            @Param("approved") CompanyStatus approved,@Param("asOfDate") LocalDate asOfDate,
            @Param("excludeApplied") boolean excludeApplied,@Param("excludeExpired") boolean excludeExpired);
    @Select("""
            <script>
            SELECT id,job_id,raw_text,ability_name,proficiency,sort_order FROM job_required_ability WHERE job_id IN
            <foreach collection="jobIds" item="jobId" open="(" separator="," close=")">#{jobId}</foreach>
            ORDER BY job_id,sort_order,id
            </script>
            """)
    List<JobRequiredAbility> abilities(@Param("jobIds") List<Long> jobIds);
}
