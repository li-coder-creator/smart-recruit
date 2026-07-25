package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Company;
import org.apache.ibatis.annotations.*;

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
}
