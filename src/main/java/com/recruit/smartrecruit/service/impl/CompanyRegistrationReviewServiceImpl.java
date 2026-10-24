package com.recruit.smartrecruit.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.dto.CompanyRegistrationReviewDecisionDTO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.CompanyReviewRecord;
import com.recruit.smartrecruit.entity.User;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.CompanyRegistrationReviewMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.CompanyRegistrationReviewService;
import com.recruit.smartrecruit.vo.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyRegistrationReviewServiceImpl implements CompanyRegistrationReviewService {
    private final PermissionService permissionService;
    private final CompanyRegistrationReviewMapper mapper;
    private final CompanyMapper companyMapper;
    private final ObjectMapper objectMapper;

    public CompanyRegistrationReviewServiceImpl(PermissionService permissionService,
            CompanyRegistrationReviewMapper mapper, CompanyMapper companyMapper, ObjectMapper objectMapper) {
        this.permissionService = permissionService;
        this.mapper = mapper;
        this.companyMapper = companyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CompanyRegistrationReviewVO> listReviews(Long userId, CompanyReviewStatus status,
                                                               int page, int pageSize) {
        permissionService.requireAdmin(userId);
        validatePage(page, pageSize);
        if (status == null) throw new BusinessException("请选择审核状态");
        try {
            PageHelper.startPage(page, pageSize);
            List<CompanyRegistrationReviewVO> records = mapper.listReviews(CompanyReviewType.REGISTRATION,
                    status, CompanyReviewStatus.PENDING, CompanyRegistrationReviewStatus.PENDING);
            return new PageResult<>(new PageInfo<>(records).getTotal(), records);
        } finally { PageHelper.clearPage(); }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyRegistrationReviewDetailVO getDetail(Long userId, Long reviewId) {
        permissionService.requireAdmin(userId);
        CompanyReviewRecord record = requireReview(reviewId);
        Company company = companyMapper.findCompanyById(record.getCompanyId());
        if (company == null) throw new BusinessException("企业不存在");
        CompanyRegistrationReviewDetailVO vo = new CompanyRegistrationReviewDetailVO();
        boolean current = Objects.equals(record.getRegistrationVersion(), company.getRegistrationVersion());
        vo.setId(record.getId());
        vo.setCompanyId(company.getId());
        vo.setCompanyName(company.getName());
        vo.setRegistrationVersion(record.getRegistrationVersion());
        vo.setCurrentRegistrationVersion(company.getRegistrationVersion());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setCurrentVersion(current);
        vo.setReviewable(current && record.getReviewStatus() == CompanyReviewStatus.PENDING
                && company.getRegistrationReviewStatus() == CompanyRegistrationReviewStatus.PENDING);
        vo.setSubmittedAt(record.getSubmittedAt());
        vo.setReviewedAt(record.getReviewedAt());
        vo.setReviewerUserId(record.getReviewerUserId());
        vo.setReviewComment(record.getReviewComment());
        vo.setSnapshot(readSnapshot(record));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long userId, Long reviewId, CompanyRegistrationReviewDecisionDTO dto) {
        User reviewer = permissionService.requireAdmin(userId);
        // 第一次读取仅定位企业；所有决策依据随后在父行锁内用当前读重新取得。
        CompanyReviewRecord located = requireReview(reviewId);
        Company company = mapper.lockCompany(located.getCompanyId());
        if (company == null) throw new BusinessException("企业不存在");
        CompanyReviewRecord record = mapper.lockReview(reviewId, company.getId(), CompanyReviewType.REGISTRATION);
        if (record == null) throw new BusinessException("工商审核记录不存在");
        if (!Objects.equals(record.getRegistrationVersion(), dto.getRegistrationVersion())
                || !Objects.equals(record.getRegistrationVersion(), company.getRegistrationVersion())) {
            throw new BusinessException("工商资料版本已变化，请刷新审核详情");
        }
        if (record.getReviewStatus() != CompanyReviewStatus.PENDING) {
            throw new BusinessException("该工商审核记录已处理");
        }
        if (company.getRegistrationReviewStatus() != CompanyRegistrationReviewStatus.PENDING) {
            throw new BusinessException("当前工商资料不处于待审核状态");
        }
        if (dto.getDecision() == null) throw new BusinessException("请选择审核结果");
        boolean approved = dto.getDecision() == CompanyReviewDecision.APPROVE;
        if (!approved && (dto.getReviewComment() == null || dto.getReviewComment().isBlank())) {
            throw new BusinessException("驳回时必须填写审核意见");
        }
        CompanyReviewStatus result = approved ? CompanyReviewStatus.APPROVED : CompanyReviewStatus.REJECTED;
        CompanyRegistrationReviewStatus companyResult = approved
                ? CompanyRegistrationReviewStatus.VERIFIED : CompanyRegistrationReviewStatus.REJECTED;
        if (mapper.decideReview(reviewId, company.getId(), CompanyReviewType.REGISTRATION,
                record.getRegistrationVersion(), CompanyReviewStatus.PENDING, result, reviewer.getId(),
                dto.getReviewComment(), LocalDateTime.now()) != 1) {
            throw new BusinessException("工商审核记录已变化，请刷新后重试");
        }
        if (mapper.updateCompanyResult(company.getId(), record.getRegistrationVersion(),
                CompanyRegistrationReviewStatus.PENDING, companyResult, approved) != 1) {
            throw new BusinessException("企业工商资料已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CompanyRegistrationReviewHistoryVO> listMyHistory(Long userId, CompanyReviewStatus status,
                                                                        int page, int pageSize) {
        Company company = permissionService.requireCompany(userId);
        validatePage(page, pageSize);
        try {
            PageHelper.startPage(page, pageSize);
            List<CompanyReviewRecord> records = mapper.listHistory(company.getId(), CompanyReviewType.REGISTRATION, status);
            long total = new PageInfo<>(records).getTotal();
            List<CompanyRegistrationReviewHistoryVO> items = records.stream().map(record -> {
                CompanyRegistrationReviewHistoryVO vo = new CompanyRegistrationReviewHistoryVO();
                vo.setId(record.getId());
                vo.setRegistrationVersion(record.getRegistrationVersion());
                vo.setCurrentRegistrationVersion(company.getRegistrationVersion());
                vo.setCurrentVersion(Objects.equals(record.getRegistrationVersion(), company.getRegistrationVersion()));
                vo.setReviewStatus(record.getReviewStatus());
                vo.setReviewComment(record.getReviewComment());
                vo.setSubmittedAt(record.getSubmittedAt());
                vo.setReviewedAt(record.getReviewedAt());
                vo.setSnapshot(readSnapshot(record));
                return vo;
            }).toList();
            return new PageResult<>(total, items);
        } finally { PageHelper.clearPage(); }
    }

    private CompanyReviewRecord requireReview(Long reviewId) {
        if (reviewId == null || reviewId <= 0) throw new BusinessException("审核记录ID必须大于0");
        CompanyReviewRecord record = mapper.findReview(reviewId, CompanyReviewType.REGISTRATION);
        if (record == null) throw new BusinessException("工商审核记录不存在");
        return record;
    }

    private CompanyRegistrationSnapshotVO readSnapshot(CompanyReviewRecord record) {
        try {
            CompanyRegistrationSnapshotVO snapshot = objectMapper.readValue(record.getSnapshotJson(),
                    CompanyRegistrationSnapshotVO.class);
            if (snapshot == null) throw new IllegalStateException("工商审核快照为空");
            return snapshot;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工商审核快照读取失败", exception);
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < JobValidationConstants.MIN_PAGE_NUMBER) {
            throw new BusinessException("页码必须大于等于" + JobValidationConstants.MIN_PAGE_NUMBER);
        }
        if (pageSize < JobValidationConstants.MIN_PAGE_SIZE || pageSize > JobValidationConstants.MAX_PAGE_SIZE) {
            throw new BusinessException("每页条数必须在" + JobValidationConstants.MIN_PAGE_SIZE
                    + "到" + JobValidationConstants.MAX_PAGE_SIZE + "之间");
        }
    }
}
