package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.enums.RequiredAbilitiesSource;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.mapper.JobRequiredAbilityMapper;
import com.recruit.smartrecruit.vo.JobContentDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 独立Bean使事务代理生效；缓存读取和模型调用不进入数据库读取事务。 */
@Service
@RequiredArgsConstructor
public class JobContentSnapshotReader {
    private final JobMapper jobMapper;
    private final JobRequiredAbilityMapper abilityMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JobContentDetailVO read(long id) {
        Job job = jobMapper.findById(id);
        if (job == null) return null;
        return new JobContentDetailVO(job, job.getRequiredAbilitiesSource() == RequiredAbilitiesSource.STRUCTURED
                ? abilityMapper.findByJobId(id) : null);
    }
}
