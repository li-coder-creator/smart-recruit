package com.recruit.smartrecruit.recommendation;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.recommendation.SkillMatchResult.*;
import static com.recruit.smartrecruit.recommendation.RecommendationRules.*;

@Component
@Slf4j
public class SkillMatcher {
    private final RuleDictionary dictionary;
    private final SemanticAbilityMatcher semantic;
    public SkillMatcher(RuleDictionary dictionary, SemanticAbilityMatcher semantic) {
        this.dictionary = dictionary; this.semantic = semantic;
    }
    public SkillMatchResult match(List<AbilityFeature> requirements, boolean requirementsComplete, List<AbilityFeature> candidates) {
        Objects.requireNonNull(requirements); Objects.requireNonNull(candidates);
        if (!requirementsComplete || requirements.isEmpty()) return new SkillMatchResult(
                DimensionAssessment.unknown(Reason.ABILITY_REQUIREMENTS_UNKNOWN, "岗位必备能力缺失或来源未确认"), List.of());
        Map<List<Object>, List<AbilityFeature>> groups = new LinkedHashMap<>();
        for (AbilityFeature requirement : requirements) {
            // 同义的无附加条件要求才共用分母；不同程度或原文约束保留为不同项。
            List<Object> key = Arrays.asList(requirement.canonicalName(), requirement.polarity(), requirement.proficiency(),
                    requirement.levelRequirement(), requirement.constrained(), requirement.constrained()
                            ? RuleDictionary.token(requirement.rawText()) : null);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(requirement);
        }
        List<RequirementMatch> matches = new ArrayList<>();
        for (List<AbilityFeature> equivalents : groups.values()) matches.add(best(equivalents, candidates));
        Optional<RequirementMatch> unknown = matches.stream().filter(m -> m.match().decision() == MatchDecision.UNKNOWN).findFirst();
        DimensionAssessment assessment;
        if (unknown.isPresent()) {
            assessment = DimensionAssessment.unknown(unknown.get().match().reason(), "至少一项必备要求尚不能判断，保留已知分项与原文证据");
        } else {
            BigDecimal score = matches.stream().map(m -> m.match().score()).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(matches.size()), CALCULATION_CONTEXT);
            assessment = DimensionAssessment.scored(score, Reason.CALCULATED, score.compareTo(MAX_SCORE) == 0, List.of());
        }
        return new SkillMatchResult(assessment, matches);
    }
    private RequirementMatch best(List<AbilityFeature> equivalents, List<AbilityFeature> candidates) {
        AbilityFeature requirement = equivalents.get(0);
        if (candidates.isEmpty()) return new RequirementMatch(equivalents, null,
                new PairMatch(MatchMethod.NONE, MatchDecision.UNKNOWN, null, null, Reason.ABILITY_LIST_MISSING));
        boolean positive = false, negative = false, uncertainRelevant = false;
        for (AbilityFeature candidate : candidates) {
            if (requirement.canonicalName() != null && requirement.canonicalName().equals(candidate.canonicalName())) {
                positive |= candidate.polarity() == Polarity.POSITIVE && !candidate.constrained();
                negative |= candidate.polarity() == Polarity.NEGATIVE && !candidate.constrained();
            }
            uncertainRelevant |= candidate.constrained() && mentions(requirement, candidate);
        }
        if ((positive && negative) || (positive && uncertainRelevant)) return new RequirementMatch(equivalents, null,
                new PairMatch(MatchMethod.CONSTRAINT, MatchDecision.UNKNOWN, null, null, Reason.CONFLICTING_EVIDENCE));
        PairMatch best = null, unknown = null; AbilityFeature bestEvidence = null, unknownEvidence = null;
        for (AbilityFeature candidate : candidates) {
            PairMatch result = pair(requirement, candidate);
            if (result.decision() == MatchDecision.UNKNOWN) {
                if (unknown == null) { unknown = result; unknownEvidence = candidate; }
            } else if (best == null || result.score().compareTo(best.score()) > 0) {
                best = result; bestEvidence = candidate;
            }
        }
        if (best != null && best.score().compareTo(MAX_SCORE) == 0) return new RequirementMatch(equivalents, bestEvidence, best);
        if (unknown != null) return new RequirementMatch(equivalents, unknownEvidence, unknown);
        return new RequirementMatch(equivalents, bestEvidence, Objects.requireNonNull(best));
    }
    private boolean mentions(AbilityFeature requirement, AbilityFeature candidate) {
        if (requirement.canonicalName() == null) return false;
        if (Objects.equals(requirement.canonicalName(), candidate.canonicalName())) return true;
        String text = RuleDictionary.token(candidate.rawText()) + " " + RuleDictionary.token(candidate.description());
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(requirement.normalizedName()) + "(?![a-z0-9])").matcher(text).find();
    }
    private PairMatch pair(AbilityFeature requirement, AbilityFeature candidate) {
        // 明确不具备目标能力，或完整名称确认为不同能力时，后续语义层不得覆盖。
        if (!requirement.constrained() && !candidate.constrained() && requirement.polarity() == Polarity.POSITIVE) {
            if (candidate.polarity() == Polarity.NEGATIVE && requirement.canonicalName() != null
                    && requirement.canonicalName().equals(candidate.canonicalName()))
                return new PairMatch(MatchMethod.CONSTRAINT, MatchDecision.NO_MATCH, MIN_SCORE, null, Reason.EXPLICIT_NEGATION);
            if (candidate.polarity() == Polarity.POSITIVE && dictionary.distinctNamedSkills(requirement.canonicalName(), candidate.canonicalName()))
                return new PairMatch(MatchMethod.NAME, MatchDecision.NO_MATCH, MIN_SCORE, null, Reason.DISTINCT_NAMED_SKILLS);
        }
        if (requirement.constrained() || requirement.levelRequirement() || candidate.constrained()
                || requirement.polarity() != Polarity.POSITIVE || candidate.polarity() == Polarity.UNKNOWN)
            return semanticMatch(requirement, candidate);
        if (requirement.canonicalName() != null && requirement.canonicalName().equals(candidate.canonicalName())) {
            boolean sameName = Objects.equals(requirement.normalizedName(), candidate.normalizedName());
            return new PairMatch(sameName ? MatchMethod.NAME : MatchMethod.ALIAS, MatchDecision.MATCH, MAX_SCORE, null,
                    sameName ? Reason.NAME_MATCH : Reason.ALIAS_MATCH);
        }
        return semanticMatch(requirement, candidate);
    }
    private PairMatch semanticMatch(AbilityFeature requirement, AbilityFeature candidate) {
        try {
            PairMatch result = semantic.match(requirement, candidate);
            if (result == null || result.method() != MatchMethod.SEMANTIC)
                return PairMatch.unknown(Reason.SEMANTIC_UNAVAILABLE);
            if (result.score() != null && (semantic.modelVersion() == null || semantic.modelVersion().isBlank()
                    || semantic.calibrationVersion() == null || semantic.calibrationVersion().isBlank()))
                return PairMatch.unknown(Reason.SEMANTIC_NOT_CONFIGURED);
            return result;
        } catch (RuntimeException exception) {
            // 不记录原文、模型地址、请求体或异常消息，避免外部服务错误携带私密数据。
            log.warn("语义匹配不可用，异常类型={}", exception.getClass().getSimpleName());
            return PairMatch.unknown(Reason.SEMANTIC_UNAVAILABLE);
        }
    }
    public SemanticAbilityMatcher semanticMatcher() { return semantic; }
}
