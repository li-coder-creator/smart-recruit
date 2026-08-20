package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.Job;
import org.apache.ibatis.annotations.*;

import java.util.List;
@Mapper
public interface JobMapper {
    @Insert("""
            INSERT INTO job
            (
                company_id,
                title,
                description,
                salary_min,
                salary_max,
                city,
                experience,
                education,
                status
            )VALUES
            (   #{companyId},
                #{title},
                #{description},
                #{salaryMin},
                #{salaryMax},
                #{city},
                #{experience},
                #{education},
                #{status}
            )
            """)
    void add(Job job);
    @Select("""
            SELECT *
            FROM job
            """)
    List<Job> findAllJob();
    @Select("""
            SELECT *
            FROM job WHERE id=#{id}
            """)
    Job findById(Long id);
    @Update("""
            UPDATE job
            SET 
            title=#{title},
            description=#{description},
            salary_min=#{salaryMin},
            salary_max=#{salaryMax},
            city=#{city},
            experience=#{experience},
            education= #{education},
            status=#{status}
            WHERE id=#{id}
            """)
    void update(Job newJob);
    @Delete("""
            DELETE
            FROM job
            WHERE id=#{id}
            """)
    void delete(Long id);
    @Select("""
            SELECT *
            FROM job
            WHERE company_id=#{companyId};
            """)
    List<Job> findByCompanyId(Long companyId);
}
