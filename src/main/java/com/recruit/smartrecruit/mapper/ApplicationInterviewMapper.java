package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.ApplicationInterview;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.InterviewStatus;
import com.recruit.smartrecruit.vo.ApplicationInterviewVO;
import com.recruit.smartrecruit.vo.MyNextInterviewItemVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApplicationInterviewMapper {
    String ENTITY_COLUMNS = """
            id, application_id, start_time, end_time, mode, platform, meeting_url, address,
            candidate_note, status, version, cancel_reason, cancel_source, cancelled_at,
            created_by, updated_by, create_time, update_time
            """;
    String VIEW_COLUMNS = """
            i.id, i.application_id, i.start_time, i.end_time, i.mode, i.platform, i.meeting_url,
            i.address, i.candidate_note, i.status, i.version, i.cancel_reason, i.cancelled_at,
            i.create_time, i.update_time
            """;

    @Select("SELECT " + ENTITY_COLUMNS + """
            FROM application_interview
            WHERE id = #{interviewId} AND application_id = #{applicationId}
            FOR UPDATE
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ApplicationInterview findForUpdate(@Param("applicationId") Long applicationId,
                                      @Param("interviewId") Long interviewId);

    @Select("SELECT " + ENTITY_COLUMNS + """
            FROM application_interview
            WHERE application_id = #{applicationId} AND status = #{scheduled}
                AND end_time > #{now}
            ORDER BY id FOR UPDATE
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<ApplicationInterview> findOpenForUpdate(@Param("applicationId") Long applicationId,
            @Param("scheduled") InterviewStatus scheduled, @Param("now") LocalDateTime now);

    @Select("SELECT " + VIEW_COLUMNS + """
            FROM application_interview i
            WHERE i.application_id = #{applicationId}
            ORDER BY i.start_time DESC, i.id DESC
            """)
    List<ApplicationInterviewVO> list(@Param("applicationId") Long applicationId);

    @Select("SELECT " + VIEW_COLUMNS + """
            , a.job_id, j.title AS job_title, j.company_id,
              c.name AS company_name, NULLIF(TRIM(c.logo), '') AS company_logo
            FROM application_interview i
            JOIN job_application a ON a.id = i.application_id
            LEFT JOIN job j ON j.id = a.job_id
            LEFT JOIN company c ON c.id = j.company_id
            WHERE a.user_id = #{userId} AND a.status = #{interviewStatus.code}
                AND i.status = #{scheduled} AND i.end_time > #{now}
            ORDER BY i.start_time ASC, i.id ASC LIMIT 1
            """)
    MyNextInterviewItemVO findNext(@Param("userId") Long userId,
            @Param("interviewStatus") ApplicationStatus interviewStatus,
            @Param("scheduled") InterviewStatus scheduled, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO application_interview
                (application_id, start_time, end_time, mode, platform, meeting_url, address,
                 candidate_note, status, version, created_by, updated_by, create_time, update_time)
            VALUES
                (#{applicationId}, #{startTime}, #{endTime}, #{mode}, #{platform}, #{meetingUrl},
                 #{address}, #{candidateNote}, #{status}, #{version}, #{createdBy}, #{updatedBy},
                 #{createTime}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ApplicationInterview interview);

    @Update("""
            UPDATE application_interview SET
                start_time = #{row.startTime}, end_time = #{row.endTime}, mode = #{row.mode},
                platform = #{row.platform}, meeting_url = #{row.meetingUrl}, address = #{row.address},
                candidate_note = #{row.candidateNote}, version = version + 1,
                updated_by = #{row.updatedBy}, update_time = #{row.updateTime}
            WHERE id = #{row.id} AND application_id = #{row.applicationId}
                AND version = #{expectedVersion} AND status = #{scheduled} AND start_time > #{now}
            """)
    int reschedule(@Param("row") ApplicationInterview interview,
            @Param("expectedVersion") Long expectedVersion,
            @Param("scheduled") InterviewStatus scheduled, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE application_interview SET
                status = #{row.status}, version = version + 1, cancel_reason = #{row.cancelReason},
                cancel_source = #{row.cancelSource}, cancelled_at = #{row.cancelledAt},
                updated_by = #{row.updatedBy}, update_time = #{row.updateTime}
            WHERE id = #{row.id} AND application_id = #{row.applicationId}
                AND version = #{expectedVersion} AND status = #{scheduled} AND end_time > #{now}
            """)
    int cancel(@Param("row") ApplicationInterview interview,
            @Param("expectedVersion") Long expectedVersion,
            @Param("scheduled") InterviewStatus scheduled, @Param("now") LocalDateTime now);
}
