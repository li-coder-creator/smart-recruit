package com.recruit.smartrecruit.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.JobConstants;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.dto.JobSaveDTO;
import com.recruit.smartrecruit.dto.JobApplicationCountDTO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.entity.JobActivity;
import com.recruit.smartrecruit.entity.enums.ApplicationStatus;
import com.recruit.smartrecruit.entity.enums.JobListFilter;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.entity.enums.JobOperation;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.ApplicationMapper;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.mapper.JobActivityMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.JobService;
import com.recruit.smartrecruit.vo.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class JobServiceImpl implements JobService {
   //注入
    private  final JobMapper jobMapper;
    private final PermissionService permissionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService cacheDelayExecutor;
    private final CompanyMapper companyMapper;
    private final ApplicationMapper applicationMapper;
    private final JobActivityMapper jobActivityMapper;
    private final String CACHE_NULL = "NOT_FOUND";
    // 岗位详情缓存 key 前缀（读写、失效统一使用，避免 key 不一致导致脏缓存）
    private static final String JOB_DETAIL_CACHE_PREFIX = "job:detail:";
    private static final String JOB_DETAIL_LOCK_PREFIX = "lock:job:detail:";

    public JobServiceImpl(JobMapper jobMapper, PermissionService permissionService, RedisTemplate<String, Object> redisTemplate, ScheduledExecutorService cacheDelayExecutor, CompanyMapper companyMapper, ApplicationMapper applicationMapper, JobActivityMapper jobActivityMapper) {
        this.jobMapper = jobMapper;
        this.permissionService = permissionService;
        this.redisTemplate = redisTemplate;
        this.cacheDelayExecutor = cacheDelayExecutor;
        this.companyMapper = companyMapper;
        this.applicationMapper = applicationMapper;
        this.jobActivityMapper = jobActivityMapper;
    }

    // 添加并发布岗位
    // 权限校验
    // 设置岗位状态为招聘中
    private void validateForPublish(Job job) {
     if (Boolean.TRUE.equals(job.getNegotiable())) {
      // 明确约定：面议时薪资上下限应为空
      if (job.getSalaryMax() != null || job.getSalaryMin() != null) {
       throw new BusinessException("薪资范围和面议不能同时存在");
      }
     } else {
      if (job.getSalaryMax() == null || job.getSalaryMin() == null) {
       throw new BusinessException("薪资范围和面议不能同时为空");
      }
      if (job.getSalaryMin() > job.getSalaryMax()) {
       throw new BusinessException("薪资下限不能大于薪资上限");
      }
     }

     if(!StringUtils.hasText(job.getCity())) {
      throw new BusinessException("城市不能为空");
     }
     if(!StringUtils.hasText(job.getCategory())) {
      throw new BusinessException("类别不能为空");
     }
     if(!StringUtils.hasText(job.getEmployment())) {
      throw new BusinessException("用工类型不能为空");
     }
     if(!StringUtils.hasText(job.getWorkMode())) {
      throw new BusinessException("工作模式不能为空");
     }
     if(!StringUtils.hasText(job.getAddress())) {
      throw new BusinessException("地址不能为空");
     }
     if(!StringUtils.hasText(job.getResponsibilities())) {
      throw new BusinessException("职责不能为空");
     }
     if(!StringUtils.hasText(job.getRequirements())) {
      throw new BusinessException("要求不能为空");
     }
     if(!StringUtils.hasText(job.getRequiredSkills())) {
      throw new BusinessException("必备技能不能为空");
     }
     if(job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
      throw new BusinessException("截止日期不能早于当前日期");
     }
    }
    @Override
    @Transactional
    public void add(JobSaveDTO jobSaveDTO , Long userId) {
        Company company = permissionService.requireApprovedCompany(userId);
        Job job = new Job();
        // 设置公司ID
        job.setCompanyId(company.getId());
        //dto转entity
        jobSaveDTO.applyTo(job);
        validateForPublish(job);
        // 设置岗位状态为招聘中
        job.setStatus(JobStatus.ACTIVE.getCode());
        job.setPublishedAt(LocalDateTime.now()) ;
        requireJobWriteSucceeded(jobMapper.add(job));
        recordJobActivity(job.getId(), userId, JobOperation.PUBLISH);
    }
    @Override
    @Transactional
    public void saveDraft(JobSaveDTO jobSaveDTO, Long userId) {
        Company company = permissionService.requireApprovedCompany(userId);
        Job job = new Job();
        // 设置公司ID
        job.setCompanyId(company.getId());
        //dto转entity
        jobSaveDTO.applyTo(job);
        // 设置岗位状态为草稿
        job.setStatus(JobStatus.DRAFT.getCode());
        requireJobWriteSucceeded(jobMapper.add(job));
        recordJobActivity(job.getId(), userId, JobOperation.CREATE_DRAFT);
    }

    /**
     * 校验岗位归属权限：确认岗位存在且属于当前用户所在公司，校验通过返回该岗位
     */
    private Job requireOwnedJob(long id, Long userId, String noPermissionMessage) {
     Company company = permissionService.requireApprovedCompany(userId);
     Job job = jobMapper.findByIdForUpdate(id);
     if (job == null) {
      throw new BusinessException("岗位不存在");
     }
     if (!job.getCompanyId().equals(company.getId())) {
      throw new BusinessException(noPermissionMessage);
     }
     return job;
    }

    /** 岗位详情缓存 key */
    private String jobDetailCacheKey(long id) {
        return JOB_DETAIL_CACHE_PREFIX + id;
    }

    /** 提交后失效缓存；回滚时不执行。延迟二次删除仅降低旧读取回填的风险。 */
    private void evictJobDetailCache(long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateJobDetailCache(id);
                }
            });
        } else {
            invalidateJobDetailCache(id);
        }
    }

    private void invalidateJobDetailCache(long id) {
        String key = jobDetailCacheKey(id);
        deleteJobDetailCacheSafely(key);
        try {
            cacheDelayExecutor.schedule(
                    () -> deleteJobDetailCacheSafely(key),
                    JobConstants.JOB_DETAIL_CACHE_SECOND_EVICTION_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            // 数据库已经提交，缓存回调失败不能将成功业务伪装成可回滚的失败。
            log.error("岗位缓存二次删除调度失败 jobId={}", id, exception);
        }
    }

    private void deleteJobDetailCacheSafely(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.error("岗位缓存删除失败 key={}", key, exception);
        }
    }

    private void requireJobWriteSucceeded(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException("岗位状态已变化，请刷新后重试");
        }
    }

    private void recordJobActivity(Long jobId, Long userId, JobOperation operation) {
        if (jobId == null) {
            throw new BusinessException("岗位主键未生成，操作已回滚");
        }
        JobActivity activity = new JobActivity();
        activity.setJobId(jobId);
        activity.setOperatorUserId(userId);
        activity.setOperation(operation);
        if (jobActivityMapper.add(activity) != 1) {
            throw new BusinessException("岗位操作记录保存失败，操作已回滚");
        }
    }

    @Transactional
    @Override
    public void publishDraft(long id, Long userId) {
     Job job = requireOwnedJob(id, userId, "没有发布权限");
     if(!job.getStatus().equals(JobStatus.DRAFT.getCode())) {
      throw new BusinessException("已发布");
     }
     validateForPublish(job);
     // 发布：写入招聘中状态与发布时间（落库）
     LocalDateTime publishedAt = LocalDateTime.now();
     requireJobWriteSucceeded(jobMapper.publishJob(id, JobStatus.ACTIVE, publishedAt, job.getStatus()));
     recordJobActivity(id, userId, JobOperation.PUBLISH);
     // 失效详情缓存
     evictJobDetailCache(id);
    }

    @Override
    @Transactional
    public void pauseJob(long id, Long userId) {
     Job job = requireOwnedJob(id, userId, "没有暂停权限");
     if(!job.getStatus().equals(JobStatus.ACTIVE.getCode())) {
      throw new BusinessException("只有在招聘中的岗位才能暂停");
     }
     requireJobWriteSucceeded(jobMapper.updateJobStatus(id, JobStatus.PAUSED, job.getStatus()));
     recordJobActivity(id, userId, JobOperation.PAUSE);
     evictJobDetailCache(id);
    }

    @Override
    @Transactional
    public void resumeJob(long id, Long userId) {
     Job job = requireOwnedJob(id, userId, "没有恢复权限");
     if(!job.getStatus().equals(JobStatus.PAUSED.getCode())){
      throw new BusinessException("只有在暂停中的岗位才能恢复");
     }
     requireJobWriteSucceeded(jobMapper.updateJobStatus(id, JobStatus.ACTIVE, job.getStatus()));
     recordJobActivity(id, userId, JobOperation.RESUME);
     evictJobDetailCache(id);
    }

    @Override
    @Transactional
    public void endJob(long id, Long userId) {
     Job job = requireOwnedJob(id, userId, "没有结束权限");
     if(job.getStatus().equals(JobStatus.ENDED.getCode()) ){
      throw new BusinessException("已结束");
     }
      if(!job.getStatus().equals(JobStatus.ACTIVE.getCode())
              && !job.getStatus().equals(JobStatus.PAUSED.getCode())) {
      throw new BusinessException("只有在招聘中或暂停中的岗位才能结束招聘");
     }
     // 结束招聘：写入已结束状态与结束时间（落库）
     requireJobWriteSucceeded(jobMapper.endJob(id, JobStatus.ENDED, LocalDateTime.now(), job.getStatus()));
     recordJobActivity(id, userId, JobOperation.END);
     evictJobDetailCache(id);
    }

    @Override
    @Transactional
    public void update(JobSaveDTO jobSaveDTO,Long id, Long userId) {
        Job job = requireOwnedJob(id, userId, "没有修改权限");
        jobSaveDTO.applyTo(job);
        if (JobStatus.ACTIVE.getCode() == job.getStatus()
             || JobStatus.PAUSED.getCode() == job.getStatus()) {
      validateForPublish(job);
     }
     // 更新数据库
     requireJobWriteSucceeded(jobMapper.update(job));
     recordJobActivity(job.getId(), userId, JobOperation.UPDATE);
     // 失效详情缓存
     evictJobDetailCache(job.getId());
    }

    @Override
    @Transactional
    public void delete(long id, Long userId) {
     Job job = requireOwnedJob(id, userId, "没有删除权限");
     if(!job.getStatus().equals(JobStatus.DRAFT.getCode())) {
      throw new BusinessException("只能删除草稿");
     }
     requireJobWriteSucceeded(jobMapper.delete(id, JobStatus.DRAFT));
     // 失效详情缓存
     evictJobDetailCache(id);
    }

    private void validatePage(int pageNum, int pageSize) {
     if (pageNum < JobValidationConstants.MIN_PAGE_NUMBER) {
      throw new BusinessException("页码必须大于等于" + JobValidationConstants.MIN_PAGE_NUMBER);
     }
     if (pageSize < JobValidationConstants.MIN_PAGE_SIZE
             || pageSize > JobValidationConstants.MAX_PAGE_SIZE) {
      throw new BusinessException("每页条数必须在"
              + JobValidationConstants.MIN_PAGE_SIZE + "到"
              + JobValidationConstants.MAX_PAGE_SIZE + "之间");
     }
    }
    @Override
    public PageResult<JobListVO> findJob(Long userId, int pageNum, int pageSize ,String keyword) {
     permissionService.requireJobSeeker(userId);
     validatePage(pageNum, pageSize);
     //规范化关键字
     String normalizedKeyword = keyword == null ? "" : keyword.trim();
     PageHelper.startPage(pageNum,pageSize);
     List<JobListVO> jobListVOs = jobMapper.findJob(normalizedKeyword,
                                                    JobStatus.ACTIVE);
     PageInfo<JobListVO> pageInfo = new PageInfo<>(jobListVOs);
     return new PageResult<>(pageInfo.getTotal(), pageInfo.getList()) ;
    }

    @Override
    public JobDetailVO findById(long id, Long userId) {
     permissionService.requireJobSeeker(userId);
     JobDetailVO job = findById(id, 0);
     if (!job.getStatus().equals(JobStatus.ACTIVE.getCode() )) {
       throw new BusinessException("没有查看权限");
     }
     return job;
    }

    private JobDetailVO findById(long id, int retryCount) {

     final int MAX_RETRY = 3;

     String key = jobDetailCacheKey(id);
     String lockKey = JOB_DETAIL_LOCK_PREFIX + id;
     String uuid = UUID.randomUUID().toString();
     Long randomMinutes= ThreadLocalRandom.current().nextLong(0,6);
     Long ttl=10+randomMinutes;

     // 第一次查询 Redis
     Object cache = redisTemplate.opsForValue().get(key);

     if (cache != null) {
      if (CACHE_NULL.equals(cache)) {
       throw new BusinessException("岗位不存在");
      }
      return (JobDetailVO) cache;
     }

     // Redis 没有缓存，尝试获取互斥锁
     Boolean locked = redisTemplate.opsForValue()
             .setIfAbsent(lockKey, uuid, 10, TimeUnit.SECONDS);

     // 成功获取锁
     if (Boolean.TRUE.equals(locked)) {
      try {
       // 获取锁以后，再次查询 Redis
       Object cacheAgain = redisTemplate.opsForValue().get(key);

       if (cacheAgain != null) {
        if (CACHE_NULL.equals(cacheAgain)) {
         throw new BusinessException("岗位不存在");
        }
        return (JobDetailVO) cacheAgain;
       }

       // Redis 还是没有，查询数据库
       Job job = jobMapper.findById(id);

       if (job == null) {
        // 缓存空值，防止缓存穿透
        redisTemplate.opsForValue().set(
                key,
                CACHE_NULL,
                2,
                TimeUnit.MINUTES
        );

        throw new BusinessException("岗位不存在");
       }
       // 将查询结果转换为 JobDetailVO
       JobDetailVO jobDetailVO = new JobDetailVO(job);

       // 查询成功，写入 Redis
       redisTemplate.opsForValue().set(
               key,
               jobDetailVO,
               ttl,
               TimeUnit.MINUTES
       );

       return jobDetailVO;

      }finally {
       // 使用 Lua 脚本原子释放锁
       String script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

       DefaultRedisScript<Long> redisScript =
               new DefaultRedisScript<>(script, Long.class);

       redisTemplate.execute(
               redisScript,
               Collections.singletonList(lockKey),
               uuid
       );
      }
     }

     // 没有获取到锁
     if (retryCount >= MAX_RETRY) {
      throw new BusinessException("系统繁忙，请稍后重试");
     }

     try {
      Thread.sleep(50);
     } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException("系统繁忙，请稍后重试");
     }

     // 等待之后重新查询，重试次数 + 1
     return findById(id, retryCount + 1);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CompanyJobListVO> findCompanyJob(Long userId, int pageNum, int pageSize ,String keyword, JobListFilter filter) {
     Company company=permissionService.requireCompany(userId);
     validatePage(pageNum, pageSize);
     //规范化关键字
     String normalizedKeyword = keyword == null ? "" : keyword.trim();
     PageHelper.startPage(pageNum,pageSize);
     List<CompanyJobListVO> jobListVOs = jobMapper.findCompanyJob(company.getId(), normalizedKeyword, filter.getStatus());
     // 在聚合查询前保存岗位分页信息，total 始终表示岗位数量。
     PageInfo<CompanyJobListVO> pageInfo = new PageInfo<>(jobListVOs);
     List<Long> jobIds = jobListVOs.stream()
             .filter(job -> !job.getStatus().equals(JobStatus.DRAFT.getCode()))
             .map(CompanyJobListVO::getId)
             .toList();
     if (!jobIds.isEmpty()) {
         Map<Long, JobApplicationCountDTO> countsByJobId = applicationMapper.countApplicationsByJobIds(
                 company.getId(), jobIds,
                 List.of(JobStatus.ACTIVE, JobStatus.PAUSED, JobStatus.ENDED),
                 ApplicationStatus.PENDING, ApplicationStatus.INTERVIEW)
                 .stream().collect(Collectors.toMap(JobApplicationCountDTO::getJobId, count -> count));
         for (CompanyJobListVO job : jobListVOs) {
             JobApplicationCountDTO counts = countsByJobId.get(job.getId());
             if (counts != null) {
                 job.setApplicationCount(counts.getApplicationCount());
                 job.setPendingApplicationCount(counts.getPendingApplicationCount());
                 job.setCurrentInterviewApplicationCount(counts.getCurrentInterviewApplicationCount());
             }
         }
     }
     return new PageResult<>(pageInfo.getTotal(), pageInfo.getList()) ;
    }

    @Override
    public JobDetailVO findByCompanyId(long id, Long userId) {
     JobDetailVO job = findById(id, 0);
     Company company = permissionService.requireCompany(userId);
     if (!job.getCompanyId().equals(company.getId())) {
      throw new BusinessException("没有查看权限");
     }
     return job;
    }

    @Override
    //isolation = Isolation.REPEATABLE_READ：总览涉及两条独立聚合 SQL（投递统计 + 岗位统计），
    // 可重复读隔离级别保证两次查询在同一数据快照上，避免出现"岗位数是新、投递数是旧"的不一致。
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyJobOverviewVO companyOverview(Long userId) {
        Company company = permissionService.requireCompany(userId);
        CompanyJobOverviewVO overview = applicationMapper.countCompanyApplications(
                company.getId(),
                //不可变集合
                List.of(JobStatus.ACTIVE, JobStatus.PAUSED, JobStatus.ENDED),
                ApplicationStatus.PENDING,
                ApplicationStatus.INTERVIEW);
        overview.setJobCounts(jobMapper.countCompanyJobs(
                company.getId(), JobStatus.ACTIVE, JobStatus.PAUSED,
                JobStatus.DRAFT, JobStatus.ENDED));

        overview.setInterviewConversionRate(calculateInterviewConversionRate(
                overview.getApplicationCount(), overview.getInterviewApplicationCount()));
        return overview;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JobRecruitmentProgressVO findJobRecruitmentProgressById(long id, Long userId) {
        Company company = permissionService.requireCompany(userId);
        // 从数据库读取岗位，避免岗位详情缓存中的旧状态影响进展统计。
        Job job = jobMapper.findById(id);
        if (job == null) {
            throw new BusinessException("岗位不存在");
        }
        if (!job.getCompanyId().equals(company.getId())) {
            throw new BusinessException("没有查看权限");
        }

        if (job.getStatus().equals(JobStatus.DRAFT.getCode())) {
            JobRecruitmentProgressVO progress = new JobRecruitmentProgressVO();
            progress.setJobId(job.getId());
            progress.setJobStatus(job.getStatus());
            progress.setApplicationCount(0L);
            progress.setPendingApplicationCount(0L);
            progress.setCurrentInterviewApplicationCount(0L);
            progress.setInterviewApplicationCount(0L);
            progress.setInterviewConversionRate(null);
            return progress;
        }

        JobRecruitmentProgressVO progress = applicationMapper.countJobApplications(
                id,
                ApplicationStatus.PENDING,
                ApplicationStatus.INTERVIEW);
        progress.setJobId(job.getId());
        progress.setJobStatus(job.getStatus());
        progress.setInterviewConversionRate(calculateInterviewConversionRate(
                progress.getApplicationCount(), progress.getInterviewApplicationCount()));
        return progress;
    }

    // 企业总览与单岗位进展共用相同的百分数及舍入规则。
    private BigDecimal calculateInterviewConversionRate(Long applicationCount,
                                                       Long interviewApplicationCount) {
        if (applicationCount == 0L || interviewApplicationCount == null) {
            return null;
        }
        return BigDecimal.valueOf(interviewApplicationCount)
                .multiply(JobConstants.INTERVIEW_CONVERSION_PERCENT_MULTIPLIER)
                .divide(BigDecimal.valueOf(applicationCount),
                        JobConstants.INTERVIEW_CONVERSION_RATE_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RecentApplicationVO> findJobRecentById(long id, Long userId, Integer limit) {
        int effectiveLimit = limit == null ? JobConstants.DEFAULT_RECENT_APPLICATION_LIMIT : limit;
        if (effectiveLimit < JobValidationConstants.MIN_RECENT_APPLICATION_LIMIT
                || effectiveLimit > JobValidationConstants.MAX_RECENT_APPLICATION_LIMIT) {
            throw new BusinessException("最近投递条数必须在"
                    + JobValidationConstants.MIN_RECENT_APPLICATION_LIMIT + "到"
                    + JobValidationConstants.MAX_RECENT_APPLICATION_LIMIT + "之间");
        }

        Company company = permissionService.requireCompany(userId);
        Job job = jobMapper.findById(id);
        if (job == null) {
            throw new BusinessException("岗位不存在");
        }
        if (!job.getCompanyId().equals(company.getId())) {
            throw new BusinessException("没有查看权限");
        }
        if (job.getStatus().equals(JobStatus.DRAFT.getCode())) {
            return List.of();
        }

        List<RecentApplicationVO> items = applicationMapper.findRecentByJobId(id, effectiveLimit);
        for (RecentApplicationVO item : items) {
            item.setStatusText(ApplicationStatus.fromCode(item.getStatus()).getDescription());
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<JobActivityVO> findJobActivityById(long id, Long userId, Integer limit) {
        int effectiveLimit = limit == null ? JobConstants.DEFAULT_JOB_ACTIVITY_LIMIT : limit;
        if (effectiveLimit < JobValidationConstants.MIN_JOB_ACTIVITY_LIMIT
                || effectiveLimit > JobValidationConstants.MAX_JOB_ACTIVITY_LIMIT) {
            throw new BusinessException("岗位操作记录条数必须在"
                    + JobValidationConstants.MIN_JOB_ACTIVITY_LIMIT + "到"
                    + JobValidationConstants.MAX_JOB_ACTIVITY_LIMIT + "之间");
        }
        Company company = permissionService.requireCompany(userId);
        Job job = jobMapper.findById(id);
        if (job == null) {
            throw new BusinessException("岗位不存在");
        }
        if (!job.getCompanyId().equals(company.getId())) {
            throw new BusinessException("没有查看权限");
        }
        List<JobActivityVO> items = jobActivityMapper.findRecentByJobId(id, effectiveLimit);
        for (JobActivityVO item : items) {
            item.setOperationText(JobOperation.fromCode(item.getOperation()).getDescription());
        }
        return items;
    }

}
