package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.dto.CompanyProfileUpdateDTO;
import com.recruit.smartrecruit.entity.CompanyReviewRecord;
import com.recruit.smartrecruit.entity.enums.CompanyBenefitCode;
import com.recruit.smartrecruit.entity.enums.CompanyQualificationType;
import com.recruit.smartrecruit.entity.enums.CompanyRegistrationReviewStatus;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 企业资料专用 SQL；完整表单允许将可选字段更新为 NULL。 */
@Mapper
public interface CompanyProfileMapper {
    @Select("""
            SELECT benefit_code FROM company_benefit
            WHERE company_id = #{companyId}
            ORDER BY sort_order, id
            """)
    List<CompanyBenefitCode> findBenefits(Long companyId);

    @Select("SELECT COUNT(*) FROM company_photo WHERE company_id = #{companyId}")
    long countPhotos(Long companyId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM company_qualification
                WHERE company_id = #{companyId}
                  AND qualification_type = #{type}
                  AND review_status = #{status}
                  AND deleted = FALSE
            )
            """)
    boolean existsApprovedQualification(@Param("companyId") Long companyId,
                                        @Param("type") CompanyQualificationType type,
                                        @Param("status") CompanyReviewStatus status);

    // 不写入归属、准入状态、logo 和历史核验版本。
    // 工商未变化时保留数据库中的当前审核结果，避免覆盖并发审核。
    @Update("""
            UPDATE company SET
                name = #{dto.name},
                full_name = #{dto.fullName},
                industry = #{dto.industry},
                employee_size = #{dto.employeeSize},
                financing_stage = #{dto.financingStage},
                company_nature = #{dto.companyNature},
                website = #{dto.website},
                city = #{dto.city},
                address = #{dto.address},
                commute_description = #{dto.commuteDescription},
                description = #{dto.description},
                founded_date = #{dto.foundedDate},
                legal_representative = #{dto.legalRepresentative},
                registered_capital = #{dto.registeredCapital},
                business_status = #{dto.businessStatus},
                credit_code = #{dto.creditCode},
                registered_address = #{dto.registeredAddress},
                business_scope = #{dto.businessScope},
                work_start_time = #{dto.workStartTime},
                work_end_time = #{dto.workEndTime},
                rest_policy = #{dto.restPolicy},
                overtime_policy = #{dto.overtimePolicy},
                work_notes = #{dto.workNotes},
                product_name = #{dto.productName},
                product_description = #{dto.productDescription},
                team_name = #{dto.teamName},
                team_role = #{dto.teamRole},
                team_bio = #{dto.teamBio},
                profile_version = profile_version + 1,
                registration_version = registration_version
                    + CASE WHEN #{registrationChanged} THEN 1 ELSE 0 END,
                registration_review_status =
                    CASE WHEN #{registrationChanged} THEN #{pendingStatus}
                         ELSE registration_review_status END,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{companyId}
              AND user_id = #{userId}
              AND profile_version = #{dto.profileVersion}
            """)
    int updateProfile(@Param("companyId") Long companyId,
                      @Param("userId") Long userId,
                      @Param("dto") CompanyProfileUpdateDTO dto,
                      @Param("registrationChanged") boolean registrationChanged,
                      @Param("pendingStatus") CompanyRegistrationReviewStatus pendingStatus);

    @Delete("DELETE FROM company_benefit WHERE company_id = #{companyId}")
    void deleteBenefits(Long companyId);

    @Insert("""
            <script>
            INSERT INTO company_benefit (company_id, benefit_code, sort_order) VALUES
            <foreach collection="benefits" item="benefit" index="position" separator=",">
                (#{companyId}, #{benefit}, #{position})
            </foreach>
            </script>
            """)
    int insertBenefits(@Param("companyId") Long companyId,
                       @Param("benefits") List<CompanyBenefitCode> benefits);

    @Insert("""
            INSERT INTO company_review_record
                (company_id, review_type, registration_version,
                 snapshot_json, review_status, submitted_at)
            VALUES
                (#{companyId}, #{reviewType}, #{registrationVersion},
                 #{snapshotJson}, #{reviewStatus}, #{submittedAt})
            """)
    int insertRegistrationReview(CompanyReviewRecord record);
}

