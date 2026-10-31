package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.dto.ApplicationEventQueryRow;
import com.recruit.smartrecruit.dto.MyApplicationQueryDTO;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.MyApplicationDetailVO;
import com.recruit.smartrecruit.vo.MyApplicationListVO;
import com.recruit.smartrecruit.vo.MyApplicationOverviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 求职者专用查询，所有入口均在 SQL 中限定投递归属。 */
@Mapper
public interface MyApplicationMapper {
    String DISPLAY_COLUMNS = """
            ja.id, ja.job_id, j.company_id, j.title AS job_title,
            c.name AS company_name, NULLIF(TRIM(c.logo), '') AS company_logo,
            j.city, j.employment, j.salary_min, j.salary_max, j.salary_unit, j.negotiable,
            j.status AS job_status, (j.id IS NOT NULL) AS job_available,
            COALESCE(j.status = #{activeJobStatus.code}
                AND c.status = #{approvedCompanyStatus}, FALSE) AS job_viewable,
            (c.id IS NOT NULL) AS company_available,
            ja.resume_id, r.title AS resume_title, (r.id IS NOT NULL) AS resume_available,
            ja.status, ja.create_time, ja.update_time
            """;
    String DISPLAY_JOINS = """
            FROM job_application ja
            LEFT JOIN job j ON j.id = ja.job_id
            LEFT JOIN company c ON c.id = j.company_id
            LEFT JOIN resume r ON r.id = ja.resume_id AND r.user_id = ja.user_id
            """;

    @Select("<script> SELECT " + DISPLAY_COLUMNS + DISPLAY_JOINS + """
            WHERE ja.user_id = #{userId} AND ja.create_time &lt;= #{now}
            <if test="from != null">AND ja.create_time &gt;= #{from}</if>
            <if test="query.status != null">AND ja.status = #{query.status}</if>
            <if test="keywordPattern != null">
                AND (j.title LIKE #{keywordPattern} ESCAPE '!'
                    OR c.name LIKE #{keywordPattern} ESCAPE '!')
            </if>
            ORDER BY ja.create_time DESC, ja.id DESC
            </script>
            """)
    List<MyApplicationListVO> findPage(@Param("userId") Long userId,
            @Param("query") MyApplicationQueryDTO query,
            @Param("from") LocalDateTime from, @Param("now") LocalDateTime now,
            @Param("keywordPattern") String keywordPattern,
            @Param("activeJobStatus") JobStatus activeJobStatus,
            @Param("approvedCompanyStatus") CompanyStatus approvedCompanyStatus);

    @Select("""
            SELECT COUNT(*) AS total_count,
                COUNT(CASE WHEN status = #{pending.code} THEN 1 END) AS pending_count,
                COUNT(CASE WHEN status = #{screening.code} THEN 1 END) AS screening_count,
                COUNT(CASE WHEN status = #{interview.code} THEN 1 END) AS interview_count,
                COUNT(CASE WHEN status = #{hired.code} THEN 1 END) AS hired_count,
                COUNT(CASE WHEN status = #{rejected.code} THEN 1 END) AS rejected_count
            FROM job_application WHERE user_id = #{userId}
            """)
    MyApplicationOverviewVO countOverview(@Param("userId") Long userId,
            @Param("pending") ApplicationStatus pending,
            @Param("screening") ApplicationStatus screening,
            @Param("interview") ApplicationStatus interview,
            @Param("hired") ApplicationStatus hired,
            @Param("rejected") ApplicationStatus rejected);

    @Select("SELECT " + DISPLAY_COLUMNS + ", ja.history_recorded_from_start AS history_complete "
            + DISPLAY_JOINS + "WHERE ja.id = #{id} AND ja.user_id = #{userId}")
    MyApplicationDetailVO findDetail(@Param("id") Long id, @Param("userId") Long userId,
            @Param("activeJobStatus") JobStatus activeJobStatus,
            @Param("approvedCompanyStatus") CompanyStatus approvedCompanyStatus);

    @Select("""
            SELECT e.id, e.event_type, e.from_status, e.to_status, e.actor_type,
                e.occurred_at, e.interview_id, e.detail_json
            FROM application_event e
            JOIN job_application ja ON ja.id = e.application_id
            WHERE e.application_id = #{id} AND ja.user_id = #{userId}
            ORDER BY e.occurred_at DESC, e.id DESC
            """)
    List<ApplicationEventQueryRow> findEvents(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT i.id, i.application_id, i.start_time, i.end_time, i.mode, i.platform,
                i.meeting_url, i.address, i.candidate_note, i.status, i.version,
                i.cancel_reason, i.cancelled_at, i.create_time, i.update_time
            FROM application_interview i
            JOIN job_application ja ON ja.id = i.application_id
            WHERE i.application_id = #{id} AND ja.user_id = #{userId}
            ORDER BY i.start_time DESC, i.id DESC
            """)
    List<ApplicationInterviewVO> findInterviews(@Param("id") Long id, @Param("userId") Long userId);
}
