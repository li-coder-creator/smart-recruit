package com.recruit.smartrecruit.service;

import com.recruit.smartrecruit.dto.CompanyFileContent;
import com.recruit.smartrecruit.dto.CompanyQualificationUploadDTO;
import com.recruit.smartrecruit.vo.CompanyPhotoVO;
import com.recruit.smartrecruit.vo.CompanyQualificationVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CompanyFileService {
    List<CompanyPhotoVO> listPhotos(Long userId);
    CompanyPhotoVO uploadPhoto(Long userId, MultipartFile file);
    void deletePhoto(Long userId, Long photoId);
    CompanyFileContent readPhoto(Long userId, Long photoId);
    List<CompanyQualificationVO> listQualifications(Long userId);
    CompanyQualificationVO uploadQualification(Long userId, CompanyQualificationUploadDTO dto);
    CompanyFileContent downloadQualification(Long userId, Long qualificationId);
    void removeQualification(Long userId, Long qualificationId);
}
