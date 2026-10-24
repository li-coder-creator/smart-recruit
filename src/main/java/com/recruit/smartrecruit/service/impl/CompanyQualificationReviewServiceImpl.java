package com.recruit.smartrecruit.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.*;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.constant.*;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.*;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.CompanyQualificationReviewService;
import com.recruit.smartrecruit.storage.CompanyFileStorage;
import com.recruit.smartrecruit.vo.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class CompanyQualificationReviewServiceImpl implements CompanyQualificationReviewService {
    private final PermissionService permission;
    private final CompanyQualificationReviewMapper mapper;
    private final CompanyMapper companies;
    private final CompanyFileStorage storage;
    private final ObjectMapper json;
    public CompanyQualificationReviewServiceImpl(PermissionService permission,CompanyQualificationReviewMapper mapper,
            CompanyMapper companies,CompanyFileStorage storage,ObjectMapper json) {
        this.permission=permission;this.mapper=mapper;this.companies=companies;this.storage=storage;this.json=json;
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResult<CompanyQualificationReviewVO> list(Long userId,CompanyReviewStatus status,CompanyQualificationType type,int page,int pageSize) {
        permission.requireAdmin(userId);validatePage(page,pageSize);
        if(status==null)throw new BusinessException("请选择审核状态");
        try {PageHelper.startPage(page,pageSize);
            var records=mapper.list(CompanyReviewType.QUALIFICATION,status,CompanyReviewStatus.PENDING,type);
            return new PageResult<>(new PageInfo<>(records).getTotal(),records);
        } finally {PageHelper.clearPage();}
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CompanyQualificationReviewDetailVO detail(Long userId,Long id) {
        permission.requireAdmin(userId);
        var r=requireReview(id);var q=mapper.findQualification(r.getQualificationId(),r.getCompanyId());
        requireLink(r,q);var c=companies.findCompanyById(r.getCompanyId());
        if(c==null)throw new BusinessException("企业不存在");
        var vo=new CompanyQualificationReviewDetailVO();
        vo.setId(r.getId());vo.setCompanyId(c.getId());vo.setCompanyName(c.getName());
        vo.setQualificationId(q.getId());vo.setQualificationType(q.getQualificationType());
        vo.setOriginalFilename(q.getOriginalFilename());vo.setContentType(q.getContentType());vo.setFileSize(q.getFileSize());
        vo.setRemoved(q.getDeleted());vo.setReviewStatus(r.getReviewStatus());
        vo.setReviewable(!Boolean.TRUE.equals(q.getDeleted())&&q.getReviewStatus()==CompanyReviewStatus.PENDING&&r.getReviewStatus()==CompanyReviewStatus.PENDING);
        vo.setSubmittedAt(r.getSubmittedAt());vo.setReviewedAt(r.getReviewedAt());
        vo.setReviewerUserId(r.getReviewerUserId());vo.setReviewComment(r.getReviewComment());
        vo.setSnapshot(snapshot(r));vo.setDownloadUrl("/admin/company/qualification-reviews/"+r.getId()+"/file");
        return vo;
    }
    private record Locked(CompanyReviewRecord review,CompanyQualification qualification) {}
    private Locked lock(Long id) {
        var located=requireReview(id);
        if(mapper.lockCompany(located.getCompanyId())==null)throw new BusinessException("企业不存在");
        var q=mapper.lockQualification(located.getQualificationId(),located.getCompanyId());
        var r=mapper.lockReview(id,located.getCompanyId(),CompanyReviewType.QUALIFICATION);
        requireLink(r,q);return new Locked(r,q);
    }
    @Override
    @Transactional(rollbackFor=Exception.class)
    public CompanyFileContent readFile(Long userId,Long id) {
        permission.requireAdmin(userId);var locked=lock(id);
        var q=locked.qualification();
        // 管理员可追溯已移除原件；父子行锁持有到有界字节读取和摘要检查结束。
        return new CompanyFileContent(verifiedBytes(locked.review(),q),q.getContentType(),q.getOriginalFilename());
    }
    @Override
    @Transactional(rollbackFor=Exception.class)
    public void decide(Long userId,Long id,CompanyQualificationReviewDecisionDTO dto) {
        var reviewer=permission.requireAdmin(userId);var locked=lock(id);
        var r=locked.review();var q=locked.qualification();
        if(Boolean.TRUE.equals(q.getDeleted()))throw new BusinessException("材料已移除，不能继续审核");
        if(r.getReviewStatus()!=CompanyReviewStatus.PENDING||q.getReviewStatus()!=CompanyReviewStatus.PENDING)
            throw new BusinessException("该材料审核记录已处理");
        if(dto.getDecision()==null)throw new BusinessException("请选择审核结果");
        boolean approved=dto.getDecision()==CompanyReviewDecision.APPROVE;
        if(approved)verifiedBytes(r,q);
        else if(dto.getReviewComment()==null||dto.getReviewComment().isBlank())throw new BusinessException("驳回时必须填写审核意见");
        var result=approved?CompanyReviewStatus.APPROVED:CompanyReviewStatus.REJECTED;
        if(mapper.decide(id,q.getCompanyId(),q.getId(),CompanyReviewType.QUALIFICATION,CompanyReviewStatus.PENDING,
            result,reviewer.getId(),dto.getReviewComment(),LocalDateTime.now())!=1)throw new BusinessException("材料审核状态已变化");
        if(mapper.updateQualification(q.getId(),q.getCompanyId(),CompanyReviewStatus.PENDING,result)!=1)
            throw new BusinessException("材料已变化或已移除");
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResult<CompanyQualificationReviewHistoryVO> history(Long userId,CompanyReviewStatus status,int page,int pageSize) {
        var c=permission.requireCompany(userId);validatePage(page,pageSize);
        try {PageHelper.startPage(page,pageSize);var records=mapper.history(c.getId(),CompanyReviewType.QUALIFICATION,status);
            return new PageResult<>(new PageInfo<>(records).getTotal(),records);
        } finally {PageHelper.clearPage();}
    }
    private CompanyReviewRecord requireReview(Long id) {
        if(id==null||id<=0)throw new BusinessException("审核记录ID必须大于0");
        var r=mapper.find(id,CompanyReviewType.QUALIFICATION);
        if(r==null)throw new BusinessException("材料审核记录不存在");return r;
    }
    private void requireLink(CompanyReviewRecord r,CompanyQualification q) {
        if(r==null||q==null||!Objects.equals(r.getCompanyId(),q.getCompanyId())||!Objects.equals(r.getQualificationId(),q.getId()))
            throw new BusinessException("材料与审核记录关联无效");
    }
    private CompanyQualificationSnapshotVO snapshot(CompanyReviewRecord r) {
        try {var s=json.readValue(r.getSnapshotJson(),CompanyQualificationSnapshotVO.class);
            if(s==null)throw new BusinessException("材料审核快照不可用");return s;
        } catch(JsonProcessingException e){throw new BusinessException("材料审核快照不可用");}
    }
    private byte[] verifiedBytes(CompanyReviewRecord r,CompanyQualification q) {
        var s=snapshot(r);
        if(!Objects.equals(s.getQualificationId(),q.getId())||s.getQualificationType()!=q.getQualificationType()
            ||!Objects.equals(s.getOriginalFilename(),q.getOriginalFilename())||!Objects.equals(s.getContentType(),q.getContentType())
            ||!Objects.equals(s.getFileSize(),q.getFileSize())||s.getSha256()==null||!s.getSha256().matches("[0-9a-f]{64}"))
            throw new BusinessException("材料元数据与提交快照不一致");
        byte[] bytes=storage.read(q.getStorageKey(),CompanyFileConstants.MAX_QUALIFICATION_BYTES);
        try {String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(bytes.length!=q.getFileSize()||!hash.equals(s.getSha256()))throw new BusinessException("材料原件与提交摘要不一致");
        } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        return bytes;
    }
    private void validatePage(int page,int pageSize) {
        if(page<JobValidationConstants.MIN_PAGE_NUMBER)throw new BusinessException("页码必须大于等于"+JobValidationConstants.MIN_PAGE_NUMBER);
        if(pageSize<JobValidationConstants.MIN_PAGE_SIZE||pageSize>JobValidationConstants.MAX_PAGE_SIZE)
            throw new BusinessException("每页条数必须在"+JobValidationConstants.MIN_PAGE_SIZE+"到"+JobValidationConstants.MAX_PAGE_SIZE+"之间");
    }
}
