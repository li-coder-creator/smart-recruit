package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.CompanyPhoto;
import com.recruit.smartrecruit.entity.CompanyQualification;
import com.recruit.smartrecruit.entity.CompanyReviewRecord;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CompanyFileMapper {
    @Select("SELECT DATABASE()")
    String databaseName();

    @Select("SELECT id FROM company WHERE id = #{companyId} FOR UPDATE")
    Long lockCompany(Long companyId);

    @Select("SELECT * FROM company_photo WHERE company_id = #{companyId} ORDER BY sort_order, id")
    List<CompanyPhoto> listPhotos(Long companyId);

    // 锁定读获取最新已提交记录，不能使用建立于等待企业锁之前的 RR 快照计数。
    @Select("SELECT * FROM company_photo WHERE company_id = #{companyId} ORDER BY sort_order, id FOR UPDATE")
    List<CompanyPhoto> lockPhotos(Long companyId);

    @Select("SELECT * FROM company_photo WHERE id = #{id}")
    CompanyPhoto findPhoto(Long id);

    @Select("SELECT * FROM company_photo WHERE id = #{id} FOR UPDATE")
    CompanyPhoto lockPhoto(Long id);

    @Select("SELECT * FROM company_photo WHERE id = #{id} LOCK IN SHARE MODE")
    CompanyPhoto readPhoto(Long id);

    @Insert("""
            INSERT INTO company_photo
                (company_id, storage_key, original_filename, content_type, file_size, sort_order)
            VALUES (#{companyId}, #{storageKey}, #{originalFilename}, #{contentType}, #{fileSize}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPhoto(CompanyPhoto photo);

    @Delete("DELETE FROM company_photo WHERE id = #{id} AND company_id = #{companyId}")
    int deletePhoto(@Param("id") Long id, @Param("companyId") Long companyId);

    @Select("""
            SELECT * FROM company_qualification
            WHERE company_id = #{companyId} AND deleted = FALSE
            ORDER BY create_time DESC, id DESC
            """)
    List<CompanyQualification> listQualifications(Long companyId);

    @Select("SELECT * FROM company_qualification WHERE id = #{id} AND deleted = FALSE")
    CompanyQualification findQualification(Long id);

    @Select("SELECT * FROM company_qualification WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    CompanyQualification lockQualification(Long id);

    @Select("SELECT * FROM company_qualification WHERE id = #{id} AND deleted = FALSE LOCK IN SHARE MODE")
    CompanyQualification readQualification(Long id);

    @Insert("""
            INSERT INTO company_qualification
                (company_id, qualification_type, storage_key, original_filename,
                 content_type, file_size, review_status, deleted)
            VALUES (#{companyId}, #{qualificationType}, #{storageKey}, #{originalFilename},
                    #{contentType}, #{fileSize}, #{reviewStatus}, FALSE)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertQualification(CompanyQualification qualification);

    @Update("""
            UPDATE company_qualification SET deleted = TRUE, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND company_id = #{companyId} AND deleted = FALSE
            """)
    int removeQualification(@Param("id") Long id, @Param("companyId") Long companyId);

    @Insert("""
            INSERT INTO company_review_record
                (company_id, review_type, qualification_id, snapshot_json, review_status, submitted_at)
            VALUES (#{companyId}, #{reviewType}, #{qualificationId}, #{snapshotJson}, #{reviewStatus}, #{submittedAt})
            """)
    int insertQualificationReview(CompanyReviewRecord review);

    // 已逻辑删除的资质仍保留原件供后续审核追溯，不能当作孤儿文件清理。
    @Select("""
            SELECT (EXISTS(SELECT 1 FROM company_photo WHERE storage_key = #{key})
                OR EXISTS(SELECT 1 FROM company_qualification WHERE storage_key = #{key}))
            """)
    boolean isReferenced(String key);
}
