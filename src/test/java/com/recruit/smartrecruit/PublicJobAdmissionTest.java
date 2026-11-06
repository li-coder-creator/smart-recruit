package com.recruit.smartrecruit;

import com.recruit.smartrecruit.dto.ApplicationApplyDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.*;
import com.recruit.smartrecruit.permission.*;
import com.recruit.smartrecruit.service.*;
import com.recruit.smartrecruit.service.impl.*;
import com.recruit.smartrecruit.vo.JobContentDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicJobAdmissionTest {
    final CompanyMapper companies = mock(CompanyMapper.class);
    final PermissionServiceImpl policy = new PermissionServiceImpl(companies, mock(UserMapper.class));

    @ParameterizedTest
    @EnumSource(value = CompanyStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void nonApprovedCompanyIsRejected(CompanyStatus status) {
        Company company = new Company(); company.setStatus(status);
        when(companies.findCompanyByIdForShare(201L)).thenReturn(company);
        assertThrows(BusinessException.class, () -> policy.requireApprovedJobCompany(201L));
    }

    @Test void approvedCompanyIsAcceptedUsingCurrentLockingRead() {
        Company company = new Company(); company.setStatus(CompanyStatus.APPROVED);
        when(companies.findCompanyByIdForShare(201L)).thenReturn(company);
        assertDoesNotThrow(() -> policy.requireApprovedJobCompany(201L));
        verify(companies).findCompanyByIdForShare(201L);
    }

    @Test void missingCompanyAndNullStatusAreRejected() {
        assertThrows(BusinessException.class, () -> policy.requireApprovedJobCompany(null));
        assertThrows(BusinessException.class, () -> policy.requireApprovedJobCompany(201L));
        when(companies.findCompanyByIdForShare(201L)).thenReturn(new Company());
        assertThrows(BusinessException.class, () -> policy.requireApprovedJobCompany(201L));
    }

    @Test void cachedJobStillChecksCurrentCompanyAdmission() {
        PermissionService permission = mock(PermissionService.class);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        JobContentDetailVO cached = new JobContentDetailVO();
        cached.setId(1001L); cached.setCompanyId(201L); cached.setStatus(JobStatus.ACTIVE.getCode());
        when(values.get(anyString())).thenReturn(cached);
        JobServiceImpl service = new JobServiceImpl(mock(JobMapper.class), permission, redis,
                mock(ScheduledExecutorService.class), companies, mock(ApplicationMapper.class),
                mock(JobActivityMapper.class), mock(JobRequiredAbilityMapper.class),
                mock(JobAbilityContentPolicy.class), mock(JobContentSnapshotReader.class));
        assertSame(cached, service.findById(1001L, Long.valueOf(101L)));
        doThrow(new BusinessException("企业未通过审核")).when(permission).requireApprovedJobCompany(201L);
        assertThrows(BusinessException.class, () -> service.findById(1001L, Long.valueOf(101L)));
        verify(permission, times(2)).requireApprovedJobCompany(201L);
    }

    @Test void rejectedApplicationDoesNotReadResumeOrWriteApplicationOrEvent() {
        PermissionService permission = mock(PermissionService.class);
        JobMapper jobs = mock(JobMapper.class);
        ResumeMapper resumes = mock(ResumeMapper.class);
        ApplicationMapper applications = mock(ApplicationMapper.class);
        ApplicationEventRecorder events = mock(ApplicationEventRecorder.class);
        Job job = new Job(); job.setId(1001L); job.setCompanyId(201L); job.setStatus(JobStatus.ACTIVE.getCode());
        when(jobs.findById(1001L)).thenReturn(job);
        doThrow(new BusinessException("企业未通过审核")).when(permission).requireApprovedJobCompany(201L);
        ApplicationServiceImpl service = new ApplicationServiceImpl(applications, jobs, resumes,
                permission, mock(UserMapper.class), events, Clock.systemUTC(), mock(ApplicationInterviewLifecycle.class));
        assertThrows(BusinessException.class, () -> service.apply(new ApplicationApplyDTO(1001L, 901L), 101L));
        verifyNoInteractions(resumes, applications, events);
    }
}
