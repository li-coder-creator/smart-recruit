package com.recruit.smartrecruit.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.dto.CompanyProfileUpdateDTO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.JobValidationConstants;
import com.recruit.smartrecruit.converter.CompanyPhotoConverter;
import com.recruit.smartrecruit.entity.enums.CompanyStatus;
import com.recruit.smartrecruit.entity.enums.JobStatus;
import com.recruit.smartrecruit.mapper.JobMapper;
import com.recruit.smartrecruit.vo.CompanyDisplayVO;
import com.recruit.smartrecruit.vo.CompanyPublicProfileVO;
import com.recruit.smartrecruit.vo.JobListVO;
import com.recruit.smartrecruit.entity.Company;
import com.recruit.smartrecruit.entity.CompanyReviewRecord;
import com.recruit.smartrecruit.entity.enums.CompanyBenefitCode;
import com.recruit.smartrecruit.entity.enums.CompanyQualificationType;
import com.recruit.smartrecruit.entity.enums.CompanyRegistrationReviewStatus;
import com.recruit.smartrecruit.entity.enums.CompanyReviewStatus;
import com.recruit.smartrecruit.entity.enums.CompanyReviewType;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.CompanyProfileMapper;
import com.recruit.smartrecruit.mapper.CompanyFileMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.CompanyService;
import com.recruit.smartrecruit.vo.CompanyProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CompanyServiceImpl implements CompanyService {
    private final CompanyMapper companyMapper;
    private final PermissionService permissionService;
    private final CompanyProfileMapper companyProfileMapper;
    private final ObjectMapper objectMapper;
    private final JobMapper jobMapper;
    private final CompanyFileMapper companyFileMapper;

    public CompanyServiceImpl(CompanyMapper companyMapper, PermissionService permissionService,
                              CompanyProfileMapper companyProfileMapper, ObjectMapper objectMapper,
                              JobMapper jobMapper, CompanyFileMapper companyFileMapper) {
        this.companyMapper = companyMapper;
        this.permissionService = permissionService;
        this.companyProfileMapper = companyProfileMapper;
        this.objectMapper = objectMapper;
        this.jobMapper = jobMapper;
        this.companyFileMapper = companyFileMapper;
    }

    @Override
    public Company findByUserId(Long userId) {
        return companyMapper.findByUserId(userId);
    }

    /** 一次事务内读取主表与子表，避免返回混合版本的资料。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyProfileVO getMyProfile(Long userId) {
        Company company = permissionService.requireCompany(userId);
        CompanyProfileVO profile = new CompanyProfileVO();
        populateDisplay(company, profile);
        profile.setStatus(company.getStatus());
        profile.setProfileVersion(company.getProfileVersion());
        profile.setRegistrationVersion(company.getRegistrationVersion());
        profile.setRegistrationReviewStatus(company.getRegistrationReviewStatus());
        profile.setVerifiedRegistrationVersion(company.getVerifiedRegistrationVersion());
        profile.setCreateTime(company.getCreateTime());
        profile.setUpdateTime(company.getUpdateTime());

        profile.setMissingRequiredFields(findMissingRequiredFields(company));
        return profile;
    }


    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyPublicProfileVO previewProfile(Long userId) {
        return buildPublicProfile(permissionService.requireCompany(userId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyPublicProfileVO getPublicProfile(Long userId, Long companyId) {
        permissionService.requireJobSeeker(userId);
        return buildPublicProfile(requireVisibleCompany(companyId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<JobListVO> listActiveJobs(Long userId, Long companyId, int page, int pageSize) {
        permissionService.requireJobSeeker(userId);
        Company company = requireVisibleCompany(companyId);
        if (page < JobValidationConstants.MIN_PAGE_NUMBER) {
            throw new BusinessException("页码必须大于等于" + JobValidationConstants.MIN_PAGE_NUMBER);
        }
        if (pageSize < JobValidationConstants.MIN_PAGE_SIZE
                || pageSize > JobValidationConstants.MAX_PAGE_SIZE) {
            throw new BusinessException("每页条数必须在" + JobValidationConstants.MIN_PAGE_SIZE
                    + "到" + JobValidationConstants.MAX_PAGE_SIZE + "之间");
        }
        // 权限查询全部完成后再开启分页，紧接着执行真正的岗位查询。
        try {
            PageHelper.startPage(page, pageSize);
            List<JobListVO> jobs = jobMapper.findActiveJobsByCompany(company.getId(), JobStatus.ACTIVE);
            return new PageResult<>(new PageInfo<>(jobs).getTotal(), jobs);
        } finally {
            PageHelper.clearPage();
        }
    }

    private Company requireVisibleCompany(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new BusinessException("企业ID必须大于0");
        }
        Company company = companyMapper.findCompanyById(companyId);
        if (company == null || company.getStatus() != CompanyStatus.APPROVED) {
            throw new BusinessException("企业不存在或暂不可查看");
        }
        return company;
    }

    private CompanyPublicProfileVO buildPublicProfile(Company company) {
        CompanyPublicProfileVO profile = new CompanyPublicProfileVO();
        populateDisplay(company, profile);
        profile.setUpdateTime(company.getUpdateTime());
        // company 已由调用入口完成身份或可见性校验；普通读沿用外层 RR 快照。
        profile.setPhotos(companyFileMapper.listPhotos(company.getId()).stream()
                .map(CompanyPhotoConverter::toVO).toList());
        return profile;
    }

    /** 直接组装共同展示字段，不通过管理 VO 转换，避免内部字段泄露。 */
    private void populateDisplay(Company company, CompanyDisplayVO profile) {
        // 明确列出返回字段，避免实体新增内部字段后意外泄露。
        profile.setId(company.getId());
        profile.setLogo(company.getLogo());
        profile.setName(company.getName());
        profile.setFullName(company.getFullName());
        profile.setIndustry(company.getIndustry());
        profile.setEmployeeSize(company.getEmployeeSize());
        profile.setFinancingStage(company.getFinancingStage());
        profile.setCompanyNature(company.getCompanyNature());
        profile.setWebsite(company.getWebsite());
        profile.setCity(company.getCity());
        profile.setAddress(company.getAddress());
        profile.setCommuteDescription(company.getCommuteDescription());
        profile.setDescription(company.getDescription());
        profile.setFoundedDate(company.getFoundedDate());
        profile.setLegalRepresentative(company.getLegalRepresentative());
        profile.setRegisteredCapital(company.getRegisteredCapital());
        profile.setBusinessStatus(company.getBusinessStatus());
        profile.setCreditCode(company.getCreditCode());
        profile.setRegisteredAddress(company.getRegisteredAddress());
        profile.setBusinessScope(company.getBusinessScope());
        profile.setWorkStartTime(company.getWorkStartTime());
        profile.setWorkEndTime(company.getWorkEndTime());
        profile.setRestPolicy(company.getRestPolicy());
        profile.setOvertimePolicy(company.getOvertimePolicy());
        profile.setWorkNotes(company.getWorkNotes());
        profile.setProductName(company.getProductName());
        profile.setProductDescription(company.getProductDescription());
        profile.setTeamName(company.getTeamName());
        profile.setTeamRole(company.getTeamRole());
        profile.setTeamBio(company.getTeamBio());
        profile.setRegistrationReviewStatus(company.getRegistrationReviewStatus());
        profile.setBenefits(companyProfileMapper.findBenefits(company.getId()));
        profile.setPhotoCount(companyProfileMapper.countPhotos(company.getId()));
        profile.setBusinessLicenseVerified(companyProfileMapper.existsApprovedQualification(
                company.getId(), CompanyQualificationType.BUSINESS_LICENSE,
                CompanyReviewStatus.APPROVED));
        profile.setRegistrationVerified(
                company.getRegistrationReviewStatus() == CompanyRegistrationReviewStatus.VERIFIED
                && company.getRegistrationVersion() != null
                && company.getRegistrationVersion().equals(company.getVerifiedRegistrationVersion()));
        List<String> missingFields = findMissingRequiredFields(company);
        profile.setBasicProfileComplete(missingFields.isEmpty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, CompanyProfileUpdateDTO dto) {
        Company company = permissionService.requireCompany(userId);
        // 先确认用于比较工商资料的旧对象与客户端版本一致。
        // SQL 中仍需再次检查版本，防止检查之后发生并发修改。
        if (!Objects.equals(company.getProfileVersion(), dto.getProfileVersion())) {
            throw new BusinessException("企业资料已更新，请刷新后重新编辑");
        }
        boolean registrationChanged = hasRegistrationChanged(company, dto);
        int affectedRows = companyProfileMapper.updateProfile(
                company.getId(), userId, dto, registrationChanged,
                CompanyRegistrationReviewStatus.PENDING);
        if (affectedRows != 1) {
            throw new BusinessException("企业资料已更新或不可修改，请刷新后重试");
        }

        replaceBenefits(company.getId(), dto.getBenefits());
        if (registrationChanged) {
            // 生成工商资料审核记录(快照)
            createRegistrationReview(company, dto);
        }
    }

    private List<String> findMissingRequiredFields(Company company) {
        List<String> fields = new ArrayList<>();
        if (normalizeText(company.getName()) == null) {
            fields.add("name");
        }
        if (normalizeText(company.getFullName()) == null) {
            fields.add("fullName");
        }
        if (company.getIndustry() == null) {
            fields.add("industry");
        }
        if (company.getEmployeeSize() == null) {
            fields.add("employeeSize");
        }
        if (normalizeText(company.getCity()) == null) {
            fields.add("city");
        }
        if (normalizeText(company.getAddress()) == null) {
            fields.add("address");
        }
        if (normalizeText(company.getDescription()) == null) {
            fields.add("description");
        }
        // 福利允许空数组；profileVersion 是并发参数，不是资料完整度字段。
        return fields;
    }

    private boolean hasRegistrationChanged(Company company, CompanyProfileUpdateDTO dto) {
        return !Objects.equals(normalizeText(company.getFullName()), dto.getFullName())
                || !Objects.equals(company.getFoundedDate(), dto.getFoundedDate())
                || !Objects.equals(normalizeText(company.getLegalRepresentative()), dto.getLegalRepresentative())
                || !Objects.equals(normalizeText(company.getRegisteredCapital()), dto.getRegisteredCapital())
                || !Objects.equals(company.getBusinessStatus(), dto.getBusinessStatus())
                || !Objects.equals(normalizeText(company.getCreditCode()), dto.getCreditCode())
                || !Objects.equals(normalizeText(company.getRegisteredAddress()), dto.getRegisteredAddress())
                || !Objects.equals(normalizeText(company.getBusinessScope()), dto.getBusinessScope());
    }

    private void replaceBenefits(Long companyId, List<CompanyBenefitCode> benefits) {
        companyProfileMapper.deleteBenefits(companyId);
        // 空数组表示清空，不能生成没有 VALUES 的批量 INSERT。
        if (!benefits.isEmpty()
                && companyProfileMapper.insertBenefits(companyId, benefits) != benefits.size()) {
            throw new BusinessException("企业福利保存失败");
        }
    }

    private void createRegistrationReview(Company company, CompanyProfileUpdateDTO dto) {
        // 仅保存八个工商字段，不把账号、完整表单或文件引用带入快照。
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fullName", dto.getFullName());
        snapshot.put("foundedDate", dto.getFoundedDate());
        snapshot.put("legalRepresentative", dto.getLegalRepresentative());
        snapshot.put("registeredCapital", dto.getRegisteredCapital());
        snapshot.put("businessStatus", dto.getBusinessStatus());
        snapshot.put("creditCode", dto.getCreditCode());
        snapshot.put("registeredAddress", dto.getRegisteredAddress());
        snapshot.put("businessScope", dto.getBusinessScope());

        CompanyReviewRecord record = new CompanyReviewRecord();
        record.setCompanyId(company.getId());
        record.setReviewType(CompanyReviewType.REGISTRATION);
        record.setRegistrationVersion(company.getRegistrationVersion() + 1);
        record.setReviewStatus(CompanyReviewStatus.PENDING);
        record.setSubmittedAt(LocalDateTime.now());
        try {
            record.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            // 保留根因供服务端排查，异常向外传播触发整个保存事务回滚。
            throw new IllegalStateException("工商审核快照生成失败", exception);
        }
        if (companyProfileMapper.insertRegistrationReview(record) != 1) {
            throw new BusinessException("工商审核记录保存失败");
        }
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

}
