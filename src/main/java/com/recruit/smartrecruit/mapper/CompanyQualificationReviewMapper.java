package com.recruit.smartrecruit.mapper;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.vo.*;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CompanyQualificationReviewMapper {
    @Select("SELECT * FROM company_review_record WHERE id=#{id} AND review_type=#{type}")
    CompanyReviewRecord find(@Param("id") Long id,@Param("type") CompanyReviewType type);
    @Select("SELECT * FROM company WHERE id=#{id} FOR UPDATE")
    Company lockCompany(Long id);
    @Select("SELECT * FROM company_qualification WHERE id=#{id} AND company_id=#{companyId} FOR UPDATE")
    CompanyQualification lockQualification(@Param("id") Long id,@Param("companyId") Long companyId);
    @Select("SELECT * FROM company_qualification WHERE id=#{id} AND company_id=#{companyId}")
    CompanyQualification findQualification(@Param("id") Long id,@Param("companyId") Long companyId);
    @Select("SELECT * FROM company_review_record WHERE id=#{id} AND company_id=#{companyId} AND review_type=#{type} FOR UPDATE")
    CompanyReviewRecord lockReview(@Param("id") Long id,@Param("companyId") Long companyId,@Param("type") CompanyReviewType type);
    @Select("""
        <script>
        SELECT r.id,r.company_id,c.name AS company_name,r.qualification_id,q.qualification_type,
          q.original_filename,q.content_type,q.file_size,q.deleted AS removed,r.review_status,
          (q.deleted=FALSE AND q.review_status=#{pending} AND r.review_status=#{pending}) AS reviewable,
          r.submitted_at,r.reviewed_at
        FROM company_review_record r JOIN company_qualification q ON q.id=r.qualification_id AND q.company_id=r.company_id
        JOIN company c ON c.id=r.company_id
        WHERE r.review_type=#{type} AND r.review_status=#{status}
        <if test="status == pending">AND q.deleted=FALSE AND q.review_status=#{pending}</if>
        <if test="qualificationType != null">AND q.qualification_type=#{qualificationType}</if>
        ORDER BY r.submitted_at ASC,r.id ASC
        </script>
        """)
    List<CompanyQualificationReviewVO> list(@Param("type") CompanyReviewType type,@Param("status") CompanyReviewStatus status,
        @Param("pending") CompanyReviewStatus pending,@Param("qualificationType") CompanyQualificationType qualificationType);
    @Select("""
        <script>
        SELECT r.id,r.qualification_id,q.qualification_type,q.original_filename,q.content_type,q.file_size,
          q.deleted AS removed,r.review_status,r.review_comment,r.submitted_at,r.reviewed_at
        FROM company_review_record r JOIN company_qualification q ON q.id=r.qualification_id AND q.company_id=r.company_id
        WHERE r.company_id=#{companyId} AND r.review_type=#{type}
        <if test="status != null">AND r.review_status=#{status}</if>
        ORDER BY r.submitted_at DESC,r.id DESC
        </script>
        """)
    List<CompanyQualificationReviewHistoryVO> history(@Param("companyId") Long companyId,@Param("type") CompanyReviewType type,@Param("status") CompanyReviewStatus status);
    @Update("""
        UPDATE company_review_record SET review_status=#{result},reviewer_user_id=#{reviewer},review_comment=#{comment},reviewed_at=#{time}
        WHERE id=#{id} AND company_id=#{companyId} AND qualification_id=#{qualificationId} AND review_type=#{type} AND review_status=#{pending}
        """)
    int decide(@Param("id") Long id,@Param("companyId") Long companyId,@Param("qualificationId") Long qualificationId,
        @Param("type") CompanyReviewType type,@Param("pending") CompanyReviewStatus pending,@Param("result") CompanyReviewStatus result,
        @Param("reviewer") Long reviewer,@Param("comment") String comment,@Param("time") LocalDateTime time);
    @Update("""
        UPDATE company_qualification SET review_status=#{result},update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND company_id=#{companyId} AND deleted=FALSE AND review_status=#{pending}
        """)
    int updateQualification(@Param("id") Long id,@Param("companyId") Long companyId,@Param("pending") CompanyReviewStatus pending,@Param("result") CompanyReviewStatus result);
}
