package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.common.Result;
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
    @Update("""
            UPDATE company
            SET
            name=#{name},
            description=#{description},
            logo=#{logo},
            city=#{city},
            address=#{address},
            status=#{status}
            WHERE id=#{id};
            """)
    void update(Company newCompany);

    @Delete("""
            DELETE
            FROM company
            WHERE id=#{companyId}
            """)
    void delete(Long companyId);
    @Select("""
             SELECT *
            FROM COMPANY
            WHERE status=#{status}
            ORDER BY create_time ASC;
            """)
    List<Company> findByStatus(CompanyStatus status);
    @Update("""
            UPDATE company
            SET
            status=#{status}
            WHERE id=#{id};
            """)
    void updateStatus(Long id,CompanyStatus status);
}
