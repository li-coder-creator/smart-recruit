package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CompanyMapper {
    @Insert("""
            INSERT INTO COMPANY
            (
                user_id,
                name,
                description,
                logo,
                city,
                address,
                status
            )VALUES
            (
                #{userId},
                #{name},
                #{description},
                #{logo},
                #{city},
                #{address},
                #{status}      
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(Company company);

    @Select("""
            SELECT *
            FROM COMPANY
            WHERE user_id=#{userId}
            """)
    Company findByUserId(Long userId);
    @Select("""
             SELECT *
            FROM COMPANY
            WHERE status=#{status}
            ORDER BY create_time ASC
            """)
    List<Company> findByStatus(CompanyStatus status);
    @Update("""
            UPDATE company
            SET
            status=#{status}
            WHERE id=#{id}
            """)
    void updateStatus(@Param("id") Long id, @Param("status") CompanyStatus status);
    @Select("""
            SELECT *
            FROM COMPANY
            WHERE id=#{companyId}
            """)
    Company findCompanyById(Long companyId);
    /** InnoDB当前读；在投递事务中阻止企业准入状态在校验后、提交前改变。 */
    @Select("SELECT * FROM company WHERE id = #{companyId} FOR SHARE")
    Company findCompanyByIdForShare(Long companyId);
    @Select("""
            <script>
            SELECT *
            FROM COMPANY
            <where>
                <if test="companyStatus != null">
                    status = #{companyStatus}
                </if>
            </where>
            ORDER BY create_time DESC
            </script>
            """)
    List<Company> findAllCompany(CompanyStatus companyStatus);
}
