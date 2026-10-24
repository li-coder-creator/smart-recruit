package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.CompanyFileContent;
import com.recruit.smartrecruit.dto.CompanyQualificationUploadDTO;
import com.recruit.smartrecruit.service.CompanyFileService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.CompanyPhotoVO;
import com.recruit.smartrecruit.vo.CompanyQualificationVO;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/company")
public class CompanyFileController {
    private final CompanyFileService service;

    public CompanyFileController(CompanyFileService service) { this.service = service; }

    @GetMapping("/photos")
    public Result<List<CompanyPhotoVO>> listPhotos() {
        return Result.success(service.listPhotos(ThreadLocalUtil.getUserId()));
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CompanyPhotoVO> uploadPhoto(@RequestPart("file") MultipartFile file) {
        return Result.success(service.uploadPhoto(ThreadLocalUtil.getUserId(), file));
    }

    @DeleteMapping("/photos/{photoId}")
    public Result<Void> deletePhoto(@PathVariable Long photoId) {
        service.deletePhoto(ThreadLocalUtil.getUserId(), photoId);
        return Result.success();
    }

    /** 配套图片读取接口，保护未准入企业照片，不添加匿名白名单。 */
    @GetMapping("/photos/{photoId}/file")
    public ResponseEntity<byte[]> readPhoto(@PathVariable Long photoId) {
        return fileResponse(service.readPhoto(ThreadLocalUtil.getUserId(), photoId), true);
    }

    @GetMapping("/qualifications")
    public Result<List<CompanyQualificationVO>> listQualifications() {
        return Result.success(service.listQualifications(ThreadLocalUtil.getUserId()));
    }

    @PostMapping(value = "/qualifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CompanyQualificationVO> uploadQualification(
            @ModelAttribute @Valid CompanyQualificationUploadDTO dto) {
        return Result.success(service.uploadQualification(ThreadLocalUtil.getUserId(), dto));
    }

    @GetMapping("/qualifications/{qualificationId}/file")
    public ResponseEntity<byte[]> downloadQualification(@PathVariable Long qualificationId) {
        return fileResponse(service.downloadQualification(ThreadLocalUtil.getUserId(), qualificationId), false);
    }

    @DeleteMapping("/qualifications/{qualificationId}")
    public Result<Void> removeQualification(@PathVariable Long qualificationId) {
        service.removeQualification(ThreadLocalUtil.getUserId(), qualificationId);
        return Result.success();
    }

    private ResponseEntity<byte[]> fileResponse(CompanyFileContent file, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }
}
