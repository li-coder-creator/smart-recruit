package com.recruit.smartrecruit.service.impl;

import com.recruit.smartrecruit.constant.*;
import com.recruit.smartrecruit.dto.*;
import com.recruit.smartrecruit.entity.enums.*;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.permission.PermissionService;
import com.recruit.smartrecruit.recommendation.*;
import com.recruit.smartrecruit.service.JobRecommendationService;
import com.recruit.smartrecruit.vo.RecommendationApiVO.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.*;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.service.impl.RecommendationSnapshotReader.ResumeInput;

@Service
public class JobRecommendationServiceImpl implements JobRecommendationService {
    private final RecommendationSnapshotReader reader;
    private final RecommendationFeatureBuilder builder;
    private final RecommendationCalculator calculator;
    private final RuleDictionary dictionary;
    private final PermissionService permission;
    private final Clock clock;
    public JobRecommendationServiceImpl(RecommendationSnapshotReader reader,RecommendationFeatureBuilder builder,
            RecommendationCalculator calculator,RuleDictionary dictionary,PermissionService permission,@Qualifier("applicationClock") Clock clock) {
        this.reader=reader;this.builder=builder;this.calculator=calculator;this.dictionary=dictionary;this.permission=permission;this.clock=clock;
    }
    @Override public Options options(Long userId) {
        permission.requireJobSeeker(userId);
        return new Options(Arrays.stream(JobDirection.values()).map(d->new Choice<>(d,d.getDescription())).toList(),
                dictionary.cityLabels().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Choice<>(e.getKey(),e.getValue())).toList(),
                Arrays.stream(JobRecommendationQueryDTO.Preset.values()).map(p->new PresetOption(p,switch(p){case BALANCED->"综合均衡";case SALARY_FIRST->"薪资优先";case CITY_FIRST->"同城优先";},
                        RecommendationWeights.Preset.valueOf(p.name()).weights().values())).toList(),
                List.of(new Choice<>(JobRecommendationQueryDTO.Sort.MATCH,"按匹配度排序"),new Choice<>(JobRecommendationQueryDTO.Sort.SALARY_DESC,"按月薪上限排序")),
                List.of(new Choice<>(EvaluationState.SCORED,"可计算"),new Choice<>(EvaluationState.UNKNOWN,"待确认"),new Choice<>(EvaluationState.NOT_APPLICABLE,"本次不可比较")),
                RecommendationWeights.Preset.BALANCED.weights().values(),RecommendationQueryConstants.DEFAULT_STRICT_CITY,
                RecommendationQueryConstants.DEFAULT_EXCLUDE_APPLIED,RecommendationQueryConstants.DEFAULT_EXCLUDE_EXPIRED,
                RecommendationQueryConstants.DEFAULT_PAGE,RecommendationQueryConstants.DEFAULT_SIZE,JobValidationConstants.MAX_PAGE_SIZE,"元/月",false,versions());
    }
    @Override public Context context(Long userId,Long resumeId) {
        LocalDate asOfDate=LocalDate.now(clock);ResumeInput input=reader.context(userId,resumeId);
        Candidate candidate=candidate(input,input.preference(),asOfDate);
        List<String> warnings=warnings(candidate);
        if(candidate.preference().direction()==null) warnings.add("请确认推荐方向；未根据宽泛职位文本猜测方向");
        return new Context(input.resume().getId(),input.resume().getTitle(),asOfDate,candidate.careerStage(),input.resume().getFormalExperienceStatus(),
                candidate.experience().actualMonths(),candidate.experience().confirmedMonths(),candidate.education().highest(),
                candidate.abilities().stream().map(a->new SkillSummary(a.rawText(),a.description(),a.proficiency())).toList(),preferences(candidate.preference()),
                candidate.preference().direction()==null,List.copyOf(warnings));
    }
    /** 不允许外层事务把未来的模型调用包进数据库事务。读取器经独立代理开启并结束只读事务。 */
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public QueryResult query(Long userId,JobRecommendationQueryDTO request) {
        LocalDate asOfDate=LocalDate.now(clock);
        var snapshot=reader.load(userId,request.getResumeId(),asOfDate,request.getExcludeApplied(),request.getExcludeExpired());
        Candidate candidate=candidate(snapshot.resume(),merge(snapshot.resume().preference(),request.getPreferences()),asOfDate);
        validatePreferences(candidate.preference(),request.getStrictCity(),request.getPreferences());
        RecommendationWeights weights=weights(request);
        List<Ranked> results=new ArrayList<>();
        for(RecommendationJobRow row:snapshot.jobs()) {
            JobProfile job=builder.job(row,snapshot.abilities().get(row.getId()),asOfDate);
            if(job.direction()!=candidate.preference().direction()) continue;
            if(request.getStrictCity()&&!Objects.equals(candidate.preference().cityCode(),job.cityCode())) continue;
            results.add(new Ranked(row,job,calculator.calculate(candidate,job,weights)));
        }
        Comparator<Ranked> order=Comparator.comparing(Ranked::score,RecommendationScore.ranking());
        if(request.getSort()==JobRecommendationQueryDTO.Sort.SALARY_DESC)
            order=Comparator.comparing((Ranked r)->comparableMonthlyMaximum(r.row()),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(order);
        results.sort(order);
        long start=(long)(request.getPage()-1)*request.getSize();
        List<Item> items=start>=results.size()?List.of():results.subList((int)start,(int)Math.min((long)results.size(),start+request.getSize()))
                .stream().map(r->item(r,asOfDate)).toList();
        return new QueryResult(candidate.resumeId(),asOfDate,preferences(candidate.preference()),weights.values(),request.getStrictCity(),
                request.getExcludeApplied(),request.getExcludeExpired(),request.getSort(),request.getPage(),request.getSize(),results.size(),items,false,versions());
    }
    private record Ranked(RecommendationJobRow row,JobProfile profile,RecommendationScore score) {}
    private Candidate candidate(ResumeInput input,com.recruit.smartrecruit.entity.JobPreference preference,LocalDate date) {
        return builder.candidate(input.resume(),preference,input.education(),input.experience(),input.skills(),date);
    }
    private com.recruit.smartrecruit.entity.JobPreference merge(com.recruit.smartrecruit.entity.JobPreference original,RecommendationPreferencesDTO override) {
        var merged=new com.recruit.smartrecruit.entity.JobPreference();
        if(original!=null) {merged.setExpectedPosition(original.getExpectedPosition());merged.setDirectionCode(original.getDirectionCode());merged.setExpectedCity(original.getExpectedCity());merged.setSalaryMin(original.getSalaryMin());merged.setSalaryMax(original.getSalaryMax());}
        if(override!=null) {
            if(override.wasProvided("directionCode")) {
                merged.setDirectionCode(override.getDirectionCode());
                // 本次明确方向优先；旧默认职位文本不得冲突或在显式清空后重新映射回来。
                merged.setExpectedPosition(null);
            }
            if(override.wasProvided("city")) merged.setExpectedCity(override.getCity());
            if(override.wasProvided("salaryMin")) merged.setSalaryMin(override.getSalaryMin());
            if(override.wasProvided("salaryMax")) merged.setSalaryMax(override.getSalaryMax());
        }
        return merged;
    }
    private void validatePreferences(Preference preference,boolean strictCity,RecommendationPreferencesDTO override) {
        if(preference.direction()==null) throw new BusinessException("请提供明确推荐方向，或完善所选简历的求职意向");
        if(strictCity&&preference.cityCode()==null) throw new BusinessException("仅看期望城市时，请提供可识别的期望城市名称");
        boolean changedSalary=override!=null&&(override.wasProvided("salaryMin")||override.wasProvided("salaryMax"));
        if(changedSalary&&preference.salaryMinimum()!=null&&preference.salaryMaximum()!=null&&preference.salaryMinimum()>preference.salaryMaximum())
            throw new BusinessException("期望月薪下限不能高于上限");
    }
    private RecommendationWeights weights(JobRecommendationQueryDTO request) {
        if(request.getWeights()!=null) {
            var w=request.getWeights();return new RecommendationWeights(Map.of(Dimension.SKILL,w.getSkill(),Dimension.SALARY,w.getSalary(),Dimension.CITY,w.getCity(),Dimension.EXPERIENCE,w.getExperience(),Dimension.EDUCATION,w.getEducation()));
        }
        return RecommendationWeights.Preset.valueOf(request.getPreset()==null?"BALANCED":request.getPreset().name()).weights();
    }
    private Integer comparableMonthlyMaximum(RecommendationJobRow row) {
        return row.getSalaryUnit()==JobSalaryUnit.MONTH&&Boolean.FALSE.equals(row.getNegotiable())&&row.getSalaryMin()!=null&&row.getSalaryMax()!=null
                &&row.getSalaryMin()>0&&row.getSalaryMax()>=row.getSalaryMin()?row.getSalaryMax():null;
    }
    private Preferences preferences(Preference preference) {return new Preferences(preference.direction(),preference.rawCity(),preference.cityCode(),preference.salaryMinimum(),preference.salaryMaximum());}
    private List<String> warnings(Candidate candidate) {
        List<String> result=new ArrayList<>(candidate.warnings());result.addAll(candidate.experience().warnings());result.addAll(candidate.education().warnings());
        if(candidate.preference().cityCode()==null)result.add("期望城市未填写或无法识别，城市项待确认");return result;
    }
    private RecommendationScore.Versions versions() {return new RecommendationScore.Versions(RecommendationRules.SCORING_VERSION,dictionary.normalizationVersion(),dictionary.aliasVersion(),dictionary.extractorVersion(),null,null);}
    private AbilityEvidence evidence(AbilityFeature feature) {return feature==null?null:new AbilityEvidence(feature.sourceRef(),feature.rawText(),feature.description(),feature.normalizedName(),feature.canonicalName(),feature.proficiency(),feature.polarity(),feature.warnings());}
    private Item item(Ranked ranked,LocalDate date) {
        var row=ranked.row();var result=ranked.score();var aggregate=result.score();
        List<DimensionBreakdown> dimensions=Arrays.stream(Dimension.values()).map(d->{var w=aggregate.dimensions().get(d);var a=w.assessment();return new DimensionBreakdown(d,a.state(),
                a.score()==null?null:a.score().setScale(RecommendationRules.SCORE_SCALE,RecommendationRules.DISPLAY_ROUNDING),a.meetsRequirement(),a.reason(),w.requestedWeight(),w.ignored(),w.effectiveWeightRatio(),w.contribution(),a.warnings());}).toList();
        List<AbilityMatch> matches=result.skills().requirements().stream().map(r->new AbilityMatch(r.equivalentRequirements().stream().map(this::evidence).toList(),evidence(r.candidateEvidence()),
                r.match().method(),r.match().decision(),r.match().score()==null?null:r.match().score().setScale(RecommendationRules.SCORE_SCALE,RecommendationRules.DISPLAY_ROUNDING),r.match().similarity(),r.match().reason())).toList();
        return new Item(row.getId(),row.getTitle(),row.getCompanyId(),row.getCompanyName(),row.getCompanyLogo(),row.getCity(),row.getExperience(),row.getEducation(),row.getEmployment(),row.getTeam(),
                row.getSalaryMin(),row.getSalaryMax(),row.getSalaryUnit(),row.getNegotiable(),row.getSummary(),row.getBenefits(),row.getRequiredSkills(),row.getPreferredSkills(),ranked.profile().direction(),
                row.getPublishedAt(),row.getDeadline(),Boolean.TRUE.equals(row.getApplied()),row.getDeadline()!=null&&row.getDeadline().isBefore(date),row.getRequiredAbilitiesSource(),row.getRequiredAbilitiesVersion(),
                aggregate.totalScore(),aggregate.dataCoverage(),aggregate.excludedWeight(),aggregate.reason(),dimensions,matches,ranked.profile().warnings());
    }
}
