package com.recruit.smartrecruit.mapper;
import com.recruit.smartrecruit.constant.ResumeConstants;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ResumeListFilter;
import com.recruit.smartrecruit.vo.ResumeListVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ResumeMapper {
    //添加简历
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id",
            keyColumn = "id"
    )
    @Insert("""
        INSERT INTO resume
        (
            user_id,
            title,
            real_name,
            gender,
            birthday,
            email,
            phone,
            current_city,
            politics_status,
            headline,
            summary,
            is_default,
            is_favorite,
            completion_rate
        )
        VALUES
        (
            #{userId},
            #{title},
            #{realName},
            #{gender},
            #{birthday},
            #{email},
            #{phone},
            #{currentCity},
            #{politicsStatus},
            #{headline},
            #{summary},
            #{isDefault},
            #{isFavorite},
            #{completionRate}
        )
        """)
    void add(Resumebasic resume);
    //获取当前用户所有简历
    @Select("""
        <script>
        SELECT
            r.id,
            r.title,
            r.real_name,
            r.headline,
            r.current_city,
            r.completion_rate,
            r.is_default,
            r.is_favorite,
            r.update_time,
            jp.expected_position,

            (
                SELECT re.school_name
                FROM resume_education re
                WHERE re.resume_id = r.id
                ORDER BY re.end_date DESC, re.id DESC
                LIMIT 1
            ) AS school_name,

            (
                SELECT re.degree
                FROM resume_education re
                WHERE re.resume_id = r.id
                ORDER BY re.end_date DESC, re.id DESC
                LIMIT 1
            ) AS degree,
            
            EXISTS
            (
              SELECT 1
              FROM job_application application
              WHERE application.resume_id = r.id
              AND application.status IN
              <foreach
                  collection="applyingStatuses"
                  item="status"
                  open="("
                  separator=","
                  close=")"
              >
                  #{status}
              </foreach>
          ) AS applying

        FROM resume r

        LEFT JOIN job_preference jp
            ON jp.resume_id = r.id

        WHERE r.user_id = #{userId}

        <if test="keyword != null and keyword != ''">
            AND
            (
                r.title LIKE CONCAT('%', #{keyword}, '%')
                OR r.real_name LIKE CONCAT('%', #{keyword}, '%')
                OR r.headline LIKE CONCAT('%', #{keyword}, '%')
                OR jp.expected_position LIKE CONCAT('%', #{keyword}, '%')
                OR EXISTS
                (
                    SELECT 1
                    FROM resume_education education
                    WHERE education.resume_id = r.id
                    AND education.school_name
                        LIKE CONCAT('%', #{keyword}, '%')
                )
            )
        </if>
        
        <choose>
                <when test="filter != null and filter.name() == 'FAVORITE'">
                    AND r.is_favorite = #{favoriteValue}
                </when>
            
                <when test="filter != null and filter.name() == 'APPLYING'">
                    AND EXISTS
                    (
                        SELECT 1
                        FROM job_application application
                        WHERE application.resume_id = r.id
                        AND application.status IN
                        <foreach
                            collection="applyingStatuses"
                            item="status"
                            open="("
                            separator=","
                            close=")"
                        >
                            #{status}
                        </foreach>
                    )
                </when>
            
                <when test="filter != null and filter.name() == 'INCOMPLETE'">
                    AND r.completion_rate &lt; #{completeRate}
                </when>
        </choose>
        ORDER BY r.update_time DESC, r.id DESC
        </script>
        """)
    List<ResumeListVO> findResumeList(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("filter") ResumeListFilter filter,
            @Param("favoriteValue") Boolean favoriteValue,
            @Param("completeRate") Integer completeRate,
            @Param("applyingStatuses") List<Integer> applyingStatuses
    );
    //查询简历详情
    @Select("""
            SELECT *
            FROM resume
            WHERE id= #{id}
            """)
    Resumebasic findById(Long id);
    //修改简历
    @Update("""
            UPDATE resume
            SET
            title=#{title},
            description=#{description},
            file_url=#{fileUrl},
            real_name=#{realName},
            gender=#{gender},
            birthday=#{birthday},
            email=#{email},
            phone=#{phone},
            current_city=#{currentCity},
            politics_status=#{politicsStatus},
            headline=#{headline},
            summary=#{summary}
            WHERE id=#{id}
            """)
    void update(Resumebasic newResume);
    //删除简历
    @Delete("""
            DELETE
            FROM resume
            WHERE id=#{id}
            """)
    void delete(Long id);

    //添加求职意向
    @Insert("""
            INSERT INTO job_preference
            (
                resume_id,
                expected_position,
                expected_city,
                salary_min,
                salary_max,
                salary_text,
                available_date,
                description
            ) VALUES
            (
                #{resumeId},
                #{jobPreference.expectedPosition},
                #{jobPreference.expectedCity},
                #{jobPreference.salaryMin},
                #{jobPreference.salaryMax},
                #{jobPreference.salaryText},
                #{jobPreference.availableDate},
                #{jobPreference.description}
            )
            """)
    void addJobPreference(@Param("resumeId") Long resumeId, @Param("jobPreference") JobPreference jobPreference);

    //获取求职意向
    @Select("""
            SELECT *
            FROM job_preference
            WHERE resume_id = #{resumeId}
            """)
    JobPreference findJobPreferenceByResumeId(Long resumeId);

    //获取求职意向详情
    @Select("""
            SELECT *
            FROM job_preference
            WHERE id = #{preferenceId}
            """)
    JobPreference findJobPreferenceById(Long preferenceId);

    //修改求职意向
    @Update("""
            UPDATE job_preference
            SET
            expected_position=#{jobPreference.expectedPosition},
            expected_city=#{jobPreference.expectedCity},
            salary_min=#{jobPreference.salaryMin},
            salary_max=#{jobPreference.salaryMax},
            salary_text=#{jobPreference.salaryText},
            available_date=#{jobPreference.availableDate},
            description=#{jobPreference.description}
            WHERE id=#{preferenceId}
            """)
    void updateJobPreference(@Param("preferenceId") Long preferenceId, @Param("jobPreference") JobPreference jobPreference);

    //删除求职意向
    @Delete("""
            DELETE
            FROM job_preference
            WHERE id=#{preferenceId}
            """)
    void deleteJobPreference(@Param("preferenceId") Long preferenceId);

    //添加简历教育经历
    @Insert("""
            INSERT INTO resume_education
            (
                resume_id,
                school_name,
                degree,
                major,
                start_date,
                end_date,
                gpa,
                rank_info,
                description,
                sort_order
            ) VALUES
            (
                #{resumeId},
                #{education.schoolName},
                #{education.degree},
                #{education.major},
                #{education.startDate},
                #{education.endDate},
                #{education.gpa},
                #{education.rankInfo},
                #{education.description},
                #{education.sortOrder}
            )
            """)
    void addResumeEducation(@Param("resumeId") Long resumeId, @Param("education") ResumeEducation resumeEducation);
    //获取简历教育经历
    @Select("""
            SELECT *
            FROM resume_education
            WHERE resume_id= #{resumeId}
            ORDER BY sort_order ASC, start_date DESC, id DESC
            """)
    List<ResumeEducation> getResumeEducation(Long resumeId);
    //修改简历教育经历
    @Update("""
            UPDATE resume_education
            SET
            school_name=#{education.schoolName},
            degree=#{education.degree},
            major=#{education.major},
            start_date=#{education.startDate},
            end_date=#{education.endDate},
            gpa=#{education.gpa},
            rank_info=#{education.rankInfo},
            description=#{education.description},
            sort_order=#{education.sortOrder}
            WHERE id=#{educationId}
            """
    )
    void updateResumeEducation(@Param("educationId") Long educationId, @Param("education") ResumeEducation resumeEducation);
    //删除简历教育经历
    @Delete("""
            DELETE
            FROM resume_education
            WHERE id=#{educationId}
            """)
    void deleteResumeEducation(Long educationId);
    //获取简历教育经历详情
    @Select("""
        SELECT *
        FROM resume_education
        WHERE id = #{educationId}
        """)
    ResumeEducation findEducationById(Long educationId);

    //简历工作经历
    @Insert("""
            INSERT INTO resume_experience
            (
                resume_id,
                company_name,
                position,
                start_date,
                end_date,
                period_text,
                description,
                achievement,
                sort_order
            ) VALUES
            (
                #{resumeId},
                #{experience.companyName},
                #{experience.position},
                #{experience.startDate},
                #{experience.endDate},
                #{experience.periodText},
                #{experience.description},
                #{experience.achievement},
                #{experience.sortOrder}
            )
            """)
    void addResumeExperience(@Param("resumeId") Long resumeId, @Param("experience") ResumeExperience resumeExperience);
    @Select("""
            SELECT *
            FROM resume_experience
            WHERE resume_id= #{resumeId}
            ORDER BY sort_order ASC, start_date DESC, id DESC
            """)
    List<ResumeExperience> getResumeExperience(Long resumeId);
    @Select("""
            SELECT *
            FROM resume_experience
            WHERE id = #{experienceId}
            """)
    ResumeExperience findExperienceById(Long experienceId);
    @Update("""
            UPDATE resume_experience
            SET
            company_name=#{experience.companyName},
            position=#{experience.position},
            start_date=#{experience.startDate},
            end_date=#{experience.endDate},
            period_text=#{experience.periodText},
            description=#{experience.description},
            achievement=#{experience.achievement},
            sort_order=#{experience.sortOrder}
            WHERE id=#{experienceId}
            """
    )
    void updateResumeExperience(@Param("experienceId") Long experienceId, @Param("experience") ResumeExperience resumeExperience);
    @Delete("""
            DELETE
            FROM resume_experience
            WHERE id=#{experienceId}
            """)
    void deleteResumeExperience(@Param("experienceId") Long experienceId);

    //简历项目经历
    @Insert("""
            INSERT INTO resume_project
            (
                resume_id,
                project_name,
                role,
                start_date,
                end_date,
                period_text,
                tech_stack,
                description,
                responsibility,
                achievement,
                sort_order
            ) VALUES
            (
                #{resumeId},
                #{project.projectName},
                #{project.role},
                #{project.startDate},
                #{project.endDate},
                #{project.periodText},
                #{project.techStack},
                #{project.description},
                #{project.responsibility},
                #{project.achievement},
                #{project.sortOrder}
            )
            """)
    void addResumeProject(@Param("resumeId") Long resumeId, @Param("project") ResumeProject resumeProject);
    @Select("""
            SELECT *
            FROM resume_project
            WHERE resume_id= #{resumeId}
            ORDER BY sort_order ASC, start_date DESC, id DESC
            """)
    List<ResumeProject> getResumeProject(Long resumeId);
    @Select("""
            SELECT *
            FROM resume_project
            WHERE id = #{projectId}
            """)
    ResumeProject findProjectById(@Param("projectId") Long projectId);
    @Update("""
            UPDATE resume_project
            SET
            project_name=#{project.projectName},
            role=#{project.role},
            start_date=#{project.startDate},
            end_date=#{project.endDate},
            period_text=#{project.periodText},
            tech_stack=#{project.techStack},
            description=#{project.description},
            responsibility=#{project.responsibility},
            achievement=#{project.achievement},
            sort_order=#{project.sortOrder}
            WHERE id=#{projectId}
            """
    )
    void updateResumeProject(@Param("projectId") Long projectId, @Param("project") ResumeProject resumeProject);
    @Delete("""
            DELETE
            FROM resume_project
            WHERE id=#{projectId}
            """)
    void deleteResumeProject(@Param("projectId") Long projectId);

    //简历技能
    @Insert("""
        INSERT INTO resume_skill
        (
            resume_id,
            skill_name,
            description,
            sort_order
        ) VALUES
        (
            #{resumeId},
            #{skill.skillName},
            #{skill.description},
            #{skill.sortOrder}
        )
        """)
    void addResumeSkill(@Param("resumeId") Long resumeId, @Param("skill") ResumeSkill skill);
    @Select(
            """
            SELECT *
            FROM resume_skill
            WHERE resume_id = #{resumeId}
            ORDER BY sort_order ASC, id DESC
            """
    )
    List<ResumeSkill> getResumeSkill(Long resumeId);
    @Update(
            """
            UPDATE resume_skill
            SET
            skill_name=#{skill.skillName},
            description=#{skill.description},
            sort_order=#{skill.sortOrder}
            WHERE id=#{skillId}
            """
    )
    void updateResumeSkill(@Param("skillId") Long skillId, @Param("skill")   ResumeSkill skill);
    @Delete("""
            DELETE
            FROM resume_skill
            WHERE id=#{skillId}
            """)
    void deleteResumeSkill(Long skillId);
    @Select(
            """
            SELECT *
            FROM resume_skill
            WHERE id = #{skillId}
            """
    )
    ResumeSkill findSkillById(Long skillId);

    //简历证书
    @Insert("""
            INSERT INTO resume_cert
            (
                resume_id,
                cert_name,
                issuer,
                award_date,
                description,
                sort_order
            ) VALUES
            (
                #{resumeId},
                #{cert.certName},
                #{cert.issuer},
                #{cert.awardDate},
                #{cert.description},
                #{cert.sortOrder}
            )
            """)
    void addResumeCert(@Param("resumeId") Long resumeId, @Param("cert") ResumeCert cert);

    @Select("""
            SELECT *
            FROM resume_cert
            WHERE resume_id= #{resumeId}
            ORDER BY sort_order ASC, award_date DESC, id DESC
            """)
    List<ResumeCert> getResumeCert(Long resumeId);

    @Select("""
            SELECT *
            FROM resume_cert
            WHERE id = #{certId}
            """)
    ResumeCert findCertById(Long certId);

    @Update("""
            UPDATE resume_cert
            SET
            cert_name=#{cert.certName},
            issuer=#{cert.issuer},
            award_date=#{cert.awardDate},
            description=#{cert.description},
            sort_order=#{cert.sortOrder}
            WHERE id=#{certId}
            """)
    void updateResumeCert(@Param("certId") Long certId, @Param("cert") ResumeCert cert);

    @Delete("""
            DELETE
            FROM resume_cert
            WHERE id=#{certId}
            """)
    void deleteResumeCert(Long certId);

    //作品链接
    @Insert("""
            INSERT INTO resume_link
            (
                resume_id,
                link_name,
                link_type,
                url,
                description,
                sort_order
            ) VALUES
            (
                #{resumeId},
                #{link.linkName},
                #{link.linkType},
                #{link.url},
                #{link.description},
                #{link.sortOrder}
            )
            """)
    void addResumeLink(@Param("resumeId") Long resumeId, @Param("link") ResumeLink link);

    @Select("""
            SELECT *
            FROM resume_link
            WHERE resume_id= #{resumeId}
            ORDER BY sort_order ASC, create_time DESC, id DESC
            """)
    List<ResumeLink> getResumeLink(Long resumeId);

    @Select("""
            SELECT *
            FROM resume_link
            WHERE id = #{linkId}
            """)
    ResumeLink findLinkById(Long linkId);

    @Update("""
            UPDATE resume_link
            SET
            link_name=#{link.linkName},
            link_type=#{link.linkType},
            url=#{link.url},
            description=#{link.description},
            sort_order=#{link.sortOrder}
            WHERE id=#{linkId}
            """)
    void updateResumeLink(@Param("linkId") Long linkId, @Param("link") ResumeLink link);

    @Delete("""
            DELETE
            FROM resume_link
            WHERE id=#{linkId}
            """)
    void deleteResumeLink( Long linkId);

    //设置默认简历
    @Select("""
            SELECT id
            FROM sys_user
            WHERE id = #{userId}
            FOR UPDATE
            """)
    Long lockUserForDefault(@Param("userId") Long userId);

    @Update("""
            UPDATE resume
            SET is_default = #{notDefaultValue}
            WHERE user_id = #{userId}
              AND is_default = #{defaultValue}
            """)
    int clearDefaultResume(
            @Param("userId") Long userId,
            @Param("notDefaultValue") boolean notDefaultValue,
            @Param("defaultValue") boolean defaultValue
    );

    @Update("""
            UPDATE resume
            SET is_default = #{defaultValue}
            WHERE id = #{resumeId}
              AND user_id = #{userId}
            """)
    int setDefaultResume(
            @Param("resumeId") Long resumeId,
            @Param("userId") Long userId,
            @Param("defaultValue") boolean defaultValue
    );

    //收藏操作
    @Update("""
            UPDATE resume
            SET is_favorite = #{favorite}
            WHERE id = #{resumeId}
              AND user_id = #{userId}
            """)
    int updateFavorite(@Param("resumeId") Long resumeId,
                       @Param("userId") Long userId,
                       @Param("favorite") Boolean favorite);

    //更新简历完成度
    @Update("""
            UPDATE resume
            SET completion_rate = #{completionRate},
                update_time = NOW()
            WHERE id = #{resumeId}
              AND user_id = #{userId}
            """)
    int updateCompletionRate(@Param("resumeId") Long resumeId,
                             @Param("userId") Long userId,
                             @Param("completionRate") int completionRate);

    //删除简历时级联清理各子模块数据，避免产生孤儿数据
    @Delete("DELETE FROM job_preference WHERE resume_id = #{resumeId}")
    void deleteJobPreferenceByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_education WHERE resume_id = #{resumeId}")
    void deleteEducationByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_experience WHERE resume_id = #{resumeId}")
    void deleteExperienceByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_project WHERE resume_id = #{resumeId}")
    void deleteProjectByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_skill WHERE resume_id = #{resumeId}")
    void deleteSkillByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_cert WHERE resume_id = #{resumeId}")
    void deleteCertByResumeId(@Param("resumeId") Long resumeId);

    @Delete("DELETE FROM resume_link WHERE resume_id = #{resumeId}")
    void deleteLinkByResumeId(@Param("resumeId") Long resumeId);
}
