package com.recruit.smartrecruit.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.constant.CompanyFileConstants;
import com.recruit.smartrecruit.converter.CompanyPhotoConverter;
import com.recruit.smartrecruit.dto.CompanyFileContent;
import com.recruit.smartrecruit.dto.CompanyQualificationUploadDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyFileMapper;
import com.recruit.smartrecruit.mapper.CompanyMapper;
import com.recruit.smartrecruit.mapper.UserMapper;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.service.CompanyFileService;
import com.recruit.smartrecruit.storage.CompanyFileStorage;
import com.recruit.smartrecruit.vo.CompanyPhotoVO;
import com.recruit.smartrecruit.vo.CompanyQualificationVO;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompanyFileServiceImpl implements CompanyFileService {
    private final CompanyFileMapper mapper;
    private final PermissionService permissionService;
    private final CompanyFileStorage storage;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;

    public CompanyFileServiceImpl(CompanyFileMapper mapper, PermissionService permissionService,
                                  CompanyFileStorage storage, ObjectMapper objectMapper,
                                  UserMapper userMapper, CompanyMapper companyMapper) {
        this.mapper = mapper;
        this.permissionService = permissionService;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CompanyPhotoVO> listPhotos(Long userId) {
        Company company = permissionService.requireCompany(userId);
        return mapper.listPhotos(company.getId()).stream().map(CompanyPhotoConverter::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyPhotoVO uploadPhoto(Long userId, MultipartFile file) {
        Company company = permissionService.requireCompany(userId);
        // 验证文件
        var validated = storage.validate(file, true);
        // 上锁
        lockCompany(company.getId());
        // 获取当前公司所有照片
        List<CompanyPhoto> current = mapper.lockPhotos(company.getId());
        // 检查照片数量
        if (current.size() >= CompanyFileConstants.MAX_PHOTO_COUNT) {
            throw new BusinessException("办公照片最多上传6张");
        }
        // 确定新照片的排序顺序
        int nextOrder = current.stream().mapToInt(CompanyPhoto::getSortOrder).max().orElse(-1) + 1;
        // 创建新照片记录
        CompanyPhoto photo = new CompanyPhoto();
        photo.setCompanyId(company.getId());
        photo.setStorageKey(storage.store(validated, true));
        photo.setOriginalFilename(validated.filename());
        photo.setContentType(validated.contentType());
        photo.setFileSize((long) validated.bytes().length);
        photo.setSortOrder(nextOrder);
        if (mapper.insertPhoto(photo) != 1) throw new BusinessException("照片保存失败");
        return CompanyPhotoConverter.toVO(mapper.findPhoto(photo.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePhoto(Long userId, Long photoId) {
        requireId(photoId);
        Company company = permissionService.requireCompany(userId);
        lockCompany(company.getId());
        CompanyPhoto photo = mapper.lockPhoto(photoId);
        if (photo == null || !company.getId().equals(photo.getCompanyId())) {
            throw new BusinessException("照片不存在或无权操作");
        }
        storage.quarantinePhoto(photo.getStorageKey());
        if (mapper.deletePhoto(photoId, company.getId()) != 1) throw new BusinessException("照片移除失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyFileContent readPhoto(Long userId, Long photoId) {
        requireId(photoId);
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getRole() != UserRole.COMPANY && user.getRole() != UserRole.JOB_SEEKER) {
            throw new BusinessException("无权查看照片");
        }
        // 共享锁持有到字节读取完成，避免删除事务中途移动该文件。
        CompanyPhoto photo = mapper.readPhoto(photoId);
        if (photo == null) throw new BusinessException("照片不存在或无权访问");
        if (user.getRole() == UserRole.COMPANY) {
            Company company = permissionService.requireCompany(userId);
            if (!company.getId().equals(photo.getCompanyId())) {
                throw new BusinessException("照片不存在或无权访问");
            }
        } else {
            Company company = companyMapper.findCompanyById(photo.getCompanyId());
            if (company == null || company.getStatus() != CompanyStatus.APPROVED) {
                throw new BusinessException("照片不存在或无权访问");
            }
        }
        return new CompanyFileContent(storage.read(photo.getStorageKey(), CompanyFileConstants.MAX_PHOTO_BYTES),
                photo.getContentType(), photo.getOriginalFilename());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CompanyQualificationVO> listQualifications(Long userId) {
        Company company = permissionService.requireCompany(userId);
        return mapper.listQualifications(company.getId()).stream().map(this::qualificationVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyQualificationVO uploadQualification(Long userId, CompanyQualificationUploadDTO dto) {
        Company company = permissionService.requireCompany(userId);
        if (dto.getQualificationType() == null) throw new BusinessException("请选择资质材料类型");
        var validated = storage.validate(dto.getFile(), false);
        lockCompany(company.getId());
        CompanyQualification qualification = new CompanyQualification();
        qualification.setCompanyId(company.getId());
        qualification.setQualificationType(dto.getQualificationType());
        qualification.setStorageKey(storage.store(validated, false));
        qualification.setOriginalFilename(validated.filename());
        qualification.setContentType(validated.contentType());
        qualification.setFileSize((long) validated.bytes().length);
        qualification.setReviewStatus(CompanyReviewStatus.PENDING);
        qualification.setDeleted(false);
        if (mapper.insertQualification(qualification) != 1) throw new BusinessException("资质材料保存失败");
        createReview(qualification, validated.bytes());
        return qualificationVO(mapper.findQualification(qualification.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyFileContent downloadQualification(Long userId, Long qualificationId) {
        requireId(qualificationId);
        Company company = permissionService.requireCompany(userId);
        CompanyQualification qualification = mapper.readQualification(qualificationId);
        if (qualification == null || !company.getId().equals(qualification.getCompanyId())) {
            throw new BusinessException("资质材料不存在或无权访问");
        }
        return new CompanyFileContent(
                storage.read(qualification.getStorageKey(), CompanyFileConstants.MAX_QUALIFICATION_BYTES),
                qualification.getContentType(), qualification.getOriginalFilename());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQualification(Long userId, Long qualificationId) {
        requireId(qualificationId);
        Company company = permissionService.requireCompany(userId);
        lockCompany(company.getId());
        CompanyQualification qualification = mapper.lockQualification(qualificationId);
        if (qualification == null || !company.getId().equals(qualification.getCompanyId())) {
            throw new BusinessException("资质材料不存在或无权操作");
        }
        if (mapper.removeQualification(qualificationId, company.getId()) != 1) {
            throw new BusinessException("资质材料移除失败");
        }
        // 保留原件和审核快照用于追溯，企业端列表和下载均排除已移除材料。
    }

    private void createReview(CompanyQualification qualification, byte[] bytes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("qualificationId", qualification.getId());
        snapshot.put("qualificationType", qualification.getQualificationType());
        snapshot.put("originalFilename", qualification.getOriginalFilename());
        snapshot.put("contentType", qualification.getContentType());
        snapshot.put("fileSize", qualification.getFileSize());
        try {
            snapshot.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        CompanyReviewRecord review = new CompanyReviewRecord();
        review.setCompanyId(qualification.getCompanyId());
        review.setQualificationId(qualification.getId());
        review.setReviewType(CompanyReviewType.QUALIFICATION);
        review.setReviewStatus(CompanyReviewStatus.PENDING);
        review.setSubmittedAt(LocalDateTime.now());
        try { review.setSnapshotJson(objectMapper.writeValueAsString(snapshot)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("资质快照生成失败", exception); }
        if (mapper.insertQualificationReview(review) != 1) throw new BusinessException("资质审核记录保存失败");
    }

    private void lockCompany(Long companyId) {
        if (mapper.lockCompany(companyId) == null) throw new BusinessException("企业不存在");
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException("文件ID必须大于0");
    }

    private CompanyQualificationVO qualificationVO(CompanyQualification qualification) {
        CompanyQualificationVO vo = new CompanyQualificationVO();
        vo.setId(qualification.getId());
        vo.setQualificationType(qualification.getQualificationType());
        vo.setOriginalFilename(qualification.getOriginalFilename());
        vo.setContentType(qualification.getContentType());
        vo.setFileSize(qualification.getFileSize());
        vo.setReviewStatus(qualification.getReviewStatus());
        vo.setCreateTime(qualification.getCreateTime());
        vo.setUpdateTime(qualification.getUpdateTime());
        vo.setDownloadUrl("/company/qualifications/" + qualification.getId() + "/file");
        return vo;
    }
}
