package com.recruit.smartrecruit.controller;

import com.recruit.smartrecruit.annotation.OperationLog;
import com.recruit.smartrecruit.common.PageResult;
import com.recruit.smartrecruit.common.Result;
import com.recruit.smartrecruit.dto.ResumeBasicUpdateDTO;
import com.recruit.smartrecruit.dto.ResumeCreateDTO;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.ResumeListFilter;
import com.recruit.smartrecruit.service.ResumeService;
import com.recruit.smartrecruit.utils.ThreadLocalUtil;
import com.recruit.smartrecruit.vo.ResumeDetailVO;
import com.recruit.smartrecruit.vo.ResumeFavoriteUpdateDTO;
import com.recruit.smartrecruit.vo.ResumeListVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resume")
public class ResumeController {
    private final ResumeService resumeService;
    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    //添加简历
    @OperationLog("添加简历")
    @PostMapping()
    public Result<Long> addResumebasic(@RequestBody @Valid ResumeCreateDTO resumebasic) {
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        Long resumeId = resumeService.add(resumebasic, userId);
        return Result.success(resumeId);
    }
    //获取当前用户所有简历
    @GetMapping()
    public Result<PageResult<ResumeListVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize ,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "ALL")ResumeListFilter filter ){
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        PageResult<ResumeListVO> resumes=resumeService.findResumeList(userId, pageNum, pageSize, keyword, filter);
        return Result.success(resumes);
    }
    //查询简历详情
    @GetMapping("{id}")
    public Result<Resumebasic> info(@PathVariable("id") Long id){
        Long userId = ThreadLocalUtil.getUserId();
        Resumebasic resumebasic=resumeService.findById(id,userId);
        return Result.success(resumebasic);
    }
    //修改简历
    @OperationLog("修改简历")
    @PutMapping("{id}/basic")
    public Result<Void> updateResumebasic(@RequestBody @Valid ResumeBasicUpdateDTO newResume, @PathVariable("id") Long resumeId){
        //获取当前登录用户
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.update(newResume,userId,resumeId);
        return Result.success();
    }
    //删除简历
    @OperationLog("删除简历")
    @DeleteMapping("{id}")
    public Result<Void> deleteResume(@PathVariable("id") Long id){
        //获取用户id
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.delete(id,userId);
        return Result.success();

    }
    //增加求职意向
    @OperationLog("添加求职意向")
    @PostMapping("{resumeId}/preference")
    public Result<Void> addJobPreference(@PathVariable("resumeId") Long resumeId, @RequestBody @Valid JobPreference jobPreference){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addJobPreference(resumeId, jobPreference, userId);
        return Result.success();
    }

    //查看求职意向
    @GetMapping("{resumeId}/preference")
    public Result<JobPreference> getJobPreference(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        JobPreference jobPreference = resumeService.getJobPreference(resumeId, userId);
        return Result.success(jobPreference);
    }

    //修改求职意向
    @OperationLog("修改求职意向")
    @PutMapping("preference/{preferenceId}")
    public Result<Void> updateJobPreference(@PathVariable("preferenceId") Long preferenceId, @RequestBody @Valid JobPreference jobPreference){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateJobPreference(preferenceId, jobPreference, userId);
        return Result.success();
    }

    //删除求职意向
    @OperationLog("删除求职意向")
    @DeleteMapping("preference/{preferenceId}")
    public Result<Void> deleteJobPreference(@PathVariable("preferenceId") Long preferenceId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteJobPreference(preferenceId, userId);
        return Result.success();
    }

    //增加教育经历
    @OperationLog("添加教育经历")
    @PostMapping("{resumeId}/education")
    public Result<Void> addResumeEducation(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeEducation education){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeEducation(resumeId, education, userId);
        return Result.success();
    }

    //查看教育经历
    @GetMapping("{resumeId}/education")
    public Result<List<ResumeEducation>> getResumeEducation(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeEducation> resumeEducation=resumeService.getResumeEducation(resumeId,userId);
        return Result.success(resumeEducation);
    }

    //修改教育经历
    @OperationLog("修改教育经历")
    @PutMapping("education/{educationId}")
    public Result<Void> updateResumeEducation(@PathVariable("educationId") Long educationId , @RequestBody @Valid ResumeEducation education){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeEducation(educationId, education, userId);
        return Result.success();
    }

    //删除教育经历
    @OperationLog("删除教育经历")
    @DeleteMapping("education/{educationId}")
    public Result<Void> deleteResumeEducation(@PathVariable("educationId") Long educationId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeEducation(educationId, userId);
        return Result.success();
    }

    //增加经历
    @OperationLog("添加经历")
    @PostMapping("{resumeId}/experience")
    public Result<Void> addResumeExperience(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeExperience experience){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeExperience(resumeId, experience, userId);
        return Result.success();
    }
    //查看经历
    @GetMapping("{resumeId}/experience")
    public Result<List<ResumeExperience>> getResumeExperience(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeExperience> resumeExperience=resumeService.getResumeExperience(resumeId,userId);
        return Result.success(resumeExperience);
    }
    //修改经历
    @OperationLog("修改经历")
    @PutMapping("experience/{experienceId}")
    public Result<Void> updateResumeExperience(@PathVariable("experienceId") Long experienceId , @RequestBody @Valid ResumeExperience experience){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeExperience(experienceId, experience, userId);
        return Result.success();
    }
    //删除经历
    @OperationLog("删除经历")
    @DeleteMapping("experience/{experienceId}")
    public Result<Void> deleteResumeExperience(@PathVariable("experienceId") Long experienceId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeExperience(experienceId, userId);
        return Result.success();
    }

    //增加项目经历
    @OperationLog("添加项目经历")
    @PostMapping("{resumeId}/project")
    public Result<Void> addResumeProject(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeProject project){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeProject(resumeId, project, userId);
        return Result.success();
    }
    //查看项目经历
    @GetMapping("{resumeId}/project")
    public Result<List<ResumeProject>> getResumeProject(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeProject> resumeProject=resumeService.getResumeProject(resumeId,userId);
        return Result.success(resumeProject);
    }
    //修改项目经历
    @OperationLog("修改项目经历")
    @PutMapping("project/{projectId}")
    public Result<Void> updateResumeProject(@PathVariable("projectId") Long projectId , @RequestBody @Valid ResumeProject project){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeProject(projectId, project, userId);
        return Result.success();
    }
    //删除项目经历
    @OperationLog("删除项目经历")
    @DeleteMapping("project/{projectId}")
    public Result<Void> deleteResumeProject(@PathVariable("projectId") Long projectId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeProject(projectId, userId);
        return Result.success();
    }

    //添加技能
    @OperationLog("添加技能")
    @PostMapping("{resumeId}/skill")
    public Result<Void> addResumeSkill(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeSkill skill){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeSkill(resumeId, skill, userId);
        return Result.success();
    }
    //查看技能
    @GetMapping("{resumeId}/skill")
    public Result<List<ResumeSkill>> getResumeSkill(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeSkill> resumeSkill=resumeService.getResumeSkill(resumeId,userId);
        return Result.success(resumeSkill);
    }
    //修改技能
    @OperationLog("修改技能")
    @PutMapping("skill/{skillId}")
    public Result<Void> updateResumeSkill(@PathVariable("skillId") Long skillId , @RequestBody @Valid ResumeSkill skill){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeSkill(skillId, skill, userId);
        return Result.success();
    }
    //删除技能
    @OperationLog("删除技能")
    @DeleteMapping("skill/{skillId}")
    public Result<Void> deleteResumeSkill(@PathVariable("skillId") Long skillId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeSkill(skillId, userId);
        return Result.success();
    }

    //添加证书
    @OperationLog("添加证书")
    @PostMapping("{resumeId}/cert")
    public Result<Void> addResumeCert(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeCert cert){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeCert(resumeId, cert, userId);
        return Result.success();
    }
    //查看证书
    @GetMapping("{resumeId}/cert")
    public Result<List<ResumeCert>> getResumeCert(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeCert> resumeCertificate=resumeService.getResumeCert(resumeId,userId);
        return Result.success(resumeCertificate);
    }
    //修改证书
    @OperationLog("修改证书")
    @PutMapping("cert/{certId}")
    public Result<Void> updateResumeCert(@PathVariable("certId") Long certId , @RequestBody @Valid ResumeCert cert){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeCert(certId, cert, userId);
        return Result.success();
    }
    //删除证书
    @OperationLog("删除证书")
    @DeleteMapping("cert/{certId}")
    public Result<Void> deleteResumeCert(@PathVariable("certId") Long certId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeCert(certId, userId);
        return Result.success();
    }

    //添加作品链接
    @OperationLog("添加作品链接")
    @PostMapping("{resumeId}/link")
    public Result<Void> addResumeLink(@PathVariable("resumeId") Long resumeId , @RequestBody @Valid ResumeLink Link){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.addResumeLink(resumeId, Link, userId);
        return Result.success();
    }
    //查看作品链接
    @GetMapping("{resumeId}/link")
    public Result<List<ResumeLink>> getResumeLink(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        List<ResumeLink> resumeLink=resumeService.getResumeLink(resumeId,userId);
        return Result.success(resumeLink);
    }
    //修改作品链接
    @OperationLog("修改作品链接")
    @PutMapping("link/{LinkId}")
    public Result<Void> updateResumeLink(@PathVariable("LinkId") Long LinkId , @RequestBody @Valid ResumeLink Link){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateResumeLink(LinkId, Link, userId);
        return Result.success();
    }
    //删除作品链接
    @OperationLog("删除作品链接")
    @DeleteMapping("link/{LinkId}")
    public Result<Void> deleteResumeLink(@PathVariable("LinkId") Long LinkId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.deleteResumeLink(LinkId, userId);
        return Result.success();
    }

    //获取简历全部详情信息
    @GetMapping("{resumeId}/detail")
    public Result<ResumeDetailVO> getResumeDetail(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        ResumeDetailVO resumeDetailVO=resumeService.getResumeDetail(resumeId,userId);
        return Result.success(resumeDetailVO);
    }

    //设置默认简历
    @OperationLog("设置默认简历")
    @PutMapping("{resumeId}/default")
    public Result<Void> setDefaultResume(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.setDefaultResume(resumeId, userId);
        return Result.success();
    }

    //收藏简历
    @OperationLog("收藏简历")
    @PutMapping("/{resumeId}/favorite")
    public Result<Void> updateFavorite(@PathVariable Long resumeId,
                                       @RequestBody @Valid ResumeFavoriteUpdateDTO dto) {
        Long userId = ThreadLocalUtil.getUserId();
        resumeService.updateFavorite(resumeId, userId, dto.getFavorite());
        return Result.success();
    }
    //简历完成度
    @GetMapping("{resumeId}/completionRate")
    public Result<Integer> getCompletionRate(@PathVariable("resumeId") Long resumeId){
        Long userId = ThreadLocalUtil.getUserId();
        Integer completionRate=resumeService.getCompletionRate(resumeId,userId);
        return Result.success(completionRate);
    }

}
