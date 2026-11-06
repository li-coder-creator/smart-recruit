package com.recruit.smartrecruit;

import com.github.pagehelper.PageHelper;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.*;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.impl.*;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅验证服务和 SQL 构造；HTTP 回归统一由 Postman MCP 执行。 */
class JobBrowseQueryTest {
    final JobMapper mapper=mock(JobMapper.class);
    final PermissionService permission=mock(PermissionService.class);
    final JobServiceImpl service=new JobServiceImpl(mapper,permission,mock(RedisTemplate.class),
            mock(ScheduledExecutorService.class),mock(CompanyMapper.class),mock(ApplicationMapper.class),
            mock(JobActivityMapper.class),mock(JobRequiredAbilityMapper.class),mock(JobAbilityContentPolicy.class),
            mock(JobContentSnapshotReader.class));

    @AfterEach void clearPage(){PageHelper.clearPage();}

    @Test void roleCheckRunsBeforeMapperAndPageSetup(){
        doThrow(new BusinessException("仅求职者")).when(permission).requireJobSeeker(101L);
        assertThrows(BusinessException.class,()->service.findJob(101L,1,10,"Java",JobBrowseSort.SALARY_DESC));
        verifyNoInteractions(mapper);assertNull(PageHelper.getLocalPage());
    }

    @ParameterizedTest @CsvSource({"0,10","-1,10","1,0","1,-1","1,101"})
    void invalidPageIsRejectedBeforeQuery(int page,int size){
        assertThrows(BusinessException.class,()->service.findJob(101L,page,size,null,JobBrowseSort.UPDATED_DESC));
        verify(permission).requireJobSeeker(101L);verifyNoInteractions(mapper);assertNull(PageHelper.getLocalPage());
    }

    @Test void defaultsAndTrimAreAppliedBeforeStartingPage(){
        when(mapper.findJob("Java",JobStatus.ACTIVE,CompanyStatus.APPROVED,JobBrowseSort.UPDATED_DESC,
                JobSalaryUnit.MONTH,RequiredAbilitiesSource.LEGACY_TEXT,RequiredAbilitiesSource.STRUCTURED))
                .thenAnswer(call->{assertNotNull(PageHelper.getLocalPage());return List.of();});
        var result=service.findJob(101L,1,10,"  Java  ",null);
        assertEquals(0,result.getTotal());assertTrue(result.getItems().isEmpty());
        verify(mapper).findJob("Java",JobStatus.ACTIVE,CompanyStatus.APPROVED,JobBrowseSort.UPDATED_DESC,
                JobSalaryUnit.MONTH,RequiredAbilitiesSource.LEGACY_TEXT,RequiredAbilitiesSource.STRUCTURED);
        assertNull(PageHelper.getLocalPage());
    }

    @Test void failedQueryAlwaysClearsThreadLocalPage(){
        when(mapper.findJob("",JobStatus.ACTIVE,CompanyStatus.APPROVED,JobBrowseSort.SALARY_DESC,
                JobSalaryUnit.MONTH,RequiredAbilitiesSource.LEGACY_TEXT,RequiredAbilitiesSource.STRUCTURED))
                .thenThrow(new BusinessException("模拟查询失败"));
        assertThrows(BusinessException.class,()->service.findJob(101L,1,10,null,JobBrowseSort.SALARY_DESC));
        assertNull(PageHelper.getLocalPage());
    }

    BoundSql bound(String keyword,JobBrowseSort sort){
        Configuration cfg=new Configuration();cfg.addMapper(JobMapper.class);
        return cfg.getMappedStatement(JobMapper.class.getName()+".findJob").getBoundSql(Map.of(
                "normalizedKeyword",keyword,"jobStatus",JobStatus.ACTIVE,"companyStatus",CompanyStatus.APPROVED,
                "sort",sort,"monthlyUnit",JobSalaryUnit.MONTH,"legacySource",RequiredAbilitiesSource.LEGACY_TEXT,
                "structuredSource",RequiredAbilitiesSource.STRUCTURED));
    }

    @Test void keywordsRemainBoundAndNeverBecomeSqlSyntax(){
        var statement=bound("' OR 1=1 --",JobBrowseSort.UPDATED_DESC);
        String sql=statement.getSql().replaceAll("\\s+"," ");
        assertFalse(sql.contains("' OR 1=1"));assertTrue(sql.contains("AND c.status=?"));
        assertTrue(sql.contains("AND ( j.title LIKE"));assertTrue(sql.contains("OR c.name LIKE"));
        assertTrue(sql.contains("EXISTS ( SELECT 1 FROM job_required_ability"));
        assertFalse(sql.contains("JOIN job_required_ability"));assertTrue(sql.contains("j.preferred_skills LIKE"));
    }

    @Test void salaryOrderUsesFixedBranchAndChecksMonthlyRange(){
        String sql=bound("",JobBrowseSort.SALARY_DESC).getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("CASE WHEN j.salary_unit=? AND j.negotiable=FALSE"));
        assertTrue(sql.contains("j.salary_min > 0 AND j.salary_max >= j.salary_min"));
        assertTrue(sql.contains("THEN j.salary_max END DESC, j.update_time DESC, j.id DESC"));
        assertFalse(sql.contains(" LIKE "));
    }

    @Test void defaultOrderRetainsDeterministicTieBreaker(){
        String sql=bound("",JobBrowseSort.UPDATED_DESC).getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("ORDER BY j.update_time DESC, j.id DESC"));assertFalse(sql.contains("CASE WHEN"));
    }
}
