package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.Job;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.JobService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class JobServiceImpl implements JobService {
   //注入
    private  final JobMapper jobMapper;
    private final PermissionService permissionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService cacheDelayExecutor;
    private final CompanyMapper companyMapper;
    private final String CACHE_NULL = "NOT_FOUND";

    public JobServiceImpl(JobMapper jobMapper, PermissionService permissionService, RedisTemplate<String, Object> redisTemplate, ScheduledExecutorService cacheDelayExecutor, CompanyMapper companyMapper) {
        this.jobMapper = jobMapper;
        this.permissionService = permissionService;
        this.redisTemplate = redisTemplate;
        this.cacheDelayExecutor = cacheDelayExecutor;
        this.companyMapper = companyMapper;
    }

    @Override
    public void add(Job job, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     Long companyId=company.getId();
     job.setCompanyId(companyId);
     jobMapper.add(job);
    }

    @Override
    public List<Job> findAllJob() {
     return jobMapper.findAllJob();
    }

    @Override
    public Job findById(long id) {
     return findById(id, 0);
    }

    private Job findById(long id, int retryCount) {

     final int MAX_RETRY = 3;

     String key = "job:" + id;
     String lockKey = "lock:job:" + id;
     String uuid = UUID.randomUUID().toString();
     Long randomMinutes= ThreadLocalRandom.current().nextLong(0,6);
     Long ttl=10+randomMinutes;

     // 第一次查询 Redis
     Object cache = redisTemplate.opsForValue().get(key);

     if (cache != null) {
      if (CACHE_NULL.equals(cache)) {
       throw new BusinessException("岗位不存在");
      }
      return (Job) cache;
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
        return (Job) cacheAgain;
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

       // 查询成功，写入 Redis
       redisTemplate.opsForValue().set(
               key,
               job,
               ttl,
               TimeUnit.MINUTES
       );

       return job;

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
    public List<Job> findCompanyJob(Long userId) {
     Company company=permissionService.requireCompany(userId);
     return jobMapper.findByCompanyId(company.getId());
    }

    @Override
    public void update(Job newJob, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     //权限校验
     Job job=jobMapper.findById(newJob.getId());
     if(job == null){
      throw new BusinessException("岗位不存在");
     }
     if(!job.getCompanyId().equals(company.getId())){
      throw new BusinessException("没有修改权限");
     }

     // 删除岗位缓存
     String key = "job:" + newJob.getId();

     // 第一次删除缓存
     redisTemplate.delete(key);

     // 更新数据库
     jobMapper.update(newJob);

     // 延迟 100ms 后再次删除缓存
     cacheDelayExecutor.schedule(
             () -> redisTemplate.delete(key),
             100,
             TimeUnit.MILLISECONDS
     );

    }

    @Override
    public void delete(long id, Long userId) {
     Company company=permissionService.requireApprovedCompany(userId);
     //权限校验
     Job job=jobMapper.findById(id);
     if(job == null){
      throw new BusinessException("岗位不存在");
     }
     if(!job.getCompanyId().equals(company.getId())){
      throw new BusinessException("没有删除权限");
     }
     jobMapper.delete(id);
     // 删除岗位缓存
     String key = "job:" + id;
     redisTemplate.delete(key);
    }
}
