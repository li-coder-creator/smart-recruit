package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.CompanyReviewRecord;
import com.recruit.smartrecruit.entity.enums.CompanyRegistrationReviewStatus;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import com.recruit.smartrecruit.entity.enums.CompanyReviewType;
import com.recruit.smartrecruit.vo.CompanyRegistrationReviewVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CompanyRegistrationReviewMapper {
    @Select("""
            <script>
            SELECT r.id, r.company_id, c.name AS company_name, r.registration_version,
                   c.registration_version AS current_registration_version, r.review_status,
                   (r.registration_version = c.registration_version) AS current_version,
                   (r.registration_version = c.registration_version
                    AND r.review_status = #{pendingStatus}
                    AND c.registration_review_status = #{pendingRegistrationStatus}) AS reviewable,
                   r.submitted_at, r.reviewed_at
            FROM company_review_record r JOIN company c ON c.id = r.company_id
            WHERE r.review_type = #{type} AND r.review_status = #{status}
            <if test="status == pendingStatus">
                AND r.registration_version = c.registration_version
                AND c.registration_review_status = #{pendingRegistrationStatus}
            </if>
            ORDER BY r.submitted_at ASC, r.id ASC
            </script>
            """)
    List<CompanyRegistrationReviewVO> listReviews(@Param("type") CompanyReviewType type,
            @Param("status") CompanyReviewStatus status, @Param("pendingStatus") CompanyReviewStatus pendingStatus,
            @Param("pendingRegistrationStatus") CompanyRegistrationReviewStatus pendingRegistrationStatus);

    @Select("SELECT * FROM company_review_record WHERE id = #{id} AND review_type = #{type}")
    CompanyReviewRecord findReview(@Param("id") Long id, @Param("type") CompanyReviewType type);

    @Select("SELECT * FROM company WHERE id = #{id} FOR UPDATE")
    Company lockCompany(Long id);

    // 锁定读重新取得最新状态；不能沿用等待企业锁之前的普通查询结果。
    @Select("""
            SELECT * FROM company_review_record
            WHERE id = #{id} AND company_id = #{companyId} AND review_type = #{type} FOR UPDATE
            """)
    CompanyReviewRecord lockReview(@Param("id") Long id, @Param("companyId") Long companyId,
                                  @Param("type") CompanyReviewType type);

    @Update("""
            UPDATE company_review_record
            SET review_status = #{result}, reviewer_user_id = #{reviewerId},
                review_comment = #{comment}, reviewed_at = #{reviewedAt}
            WHERE id = #{id} AND company_id = #{companyId} AND review_type = #{type}
              AND registration_version = #{version} AND review_status = #{pendingStatus}
            """)
    int decideReview(@Param("id") Long id, @Param("companyId") Long companyId,
            @Param("type") CompanyReviewType type, @Param("version") Long version,
            @Param("pendingStatus") CompanyReviewStatus pendingStatus, @Param("result") CompanyReviewStatus result,
            @Param("reviewerId") Long reviewerId, @Param("comment") String comment,
            @Param("reviewedAt") LocalDateTime reviewedAt);

    @Update("""
            UPDATE company
            SET registration_review_status = #{result},
                verified_registration_version = CASE WHEN #{approved} THEN #{version}
                                                     ELSE verified_registration_version END,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{companyId} AND registration_version = #{version}
              AND registration_review_status = #{pendingStatus}
            """)
    int updateCompanyResult(@Param("companyId") Long companyId, @Param("version") Long version,
            @Param("pendingStatus") CompanyRegistrationReviewStatus pendingStatus,
            @Param("result") CompanyRegistrationReviewStatus result, @Param("approved") boolean approved);

    @Select("""
            <script>
            SELECT id, company_id, registration_version, snapshot_json, review_status,
                   review_comment, submitted_at, reviewed_at
            FROM company_review_record
            WHERE company_id = #{companyId} AND review_type = #{type}
            <if test="status != null">AND review_status = #{status}</if>
            ORDER BY submitted_at DESC, id DESC
            </script>
            """)
    List<CompanyReviewRecord> listHistory(@Param("companyId") Long companyId,
            @Param("type") CompanyReviewType type, @Param("status") CompanyReviewStatus status);
}
