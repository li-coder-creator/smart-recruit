package com.recruit.smartrecruit.mapper;

import com.recruit.smartrecruit.entity.JobRequiredAbility;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface JobRequiredAbilityMapper {
    @Select("SELECT * FROM job_required_ability WHERE job_id=#{jobId} ORDER BY sort_order,id")
    List<JobRequiredAbility> findByJobId(Long jobId);

    @Delete("DELETE FROM job_required_ability WHERE job_id=#{jobId}")
    int deleteByJobId(Long jobId);

    @Insert("""
            INSERT INTO job_required_ability(job_id,raw_text,ability_name,proficiency,sort_order)
            VALUES(#{jobId},#{rawText},#{abilityName},#{proficiency},#{sortOrder})
            """)
    int add(JobRequiredAbility ability);
}
