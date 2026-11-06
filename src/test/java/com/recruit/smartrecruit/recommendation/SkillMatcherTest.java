package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.*;
import static com.recruit.smartrecruit.recommendation.SkillMatchResult.*;

class SkillMatcherTest {
    final RuleDictionary dictionary=new RuleDictionary();
    final AbilityFeatureExtractor extractor=new AbilityFeatureExtractor(dictionary);
    final SkillMatcher matcher=new SkillMatcher(dictionary,new DisabledSemanticAbilityMatcher());
    AbilityFeature requirement(String text) {return extractor.extract("job:1",text,null,null,null,true);}
    AbilityFeature candidate(String text) {return extractor.extract("skill:1",text,null,null,null,false);}
    SkillMatchResult compare(String requirement,String candidate) {return matcher.match(List.of(requirement(requirement)),true,List.of(candidate(candidate)));}
    static void score(String expected, SkillMatchResult result) {
        assertEquals(EvaluationState.SCORED,result.assessment().state());assertEquals(0,new BigDecimal(expected).compareTo(result.assessment().score()));
    }
    @ParameterizedTest @CsvSource({"Java,java,NAME", "会Java,熟悉Java,NAME", "Spring Boot,SpringBoot,ALIAS", "Vue 3,Vue3,ALIAS", "JavaScript,JS,ALIAS", "C++,c++,NAME", "C#,c#,NAME", "NewTech,熟悉NewTech,NAME", "跨部门沟通,跨部门沟通,NAME"})
    void namesAliasesAndNewCapabilities(String required,String actual,MatchMethod method) {
        var result=compare(required,actual);score("100",result);assertEquals(method,result.requirements().get(0).match().method());
    }
    @Test void javaIsNotJavaScript() {score("0",compare("Java","JavaScript"));}
    @Test void exactNegationIsZeroNotNameHit() {
        var result=compare("会Java","不会Java");score("0",result);assertEquals(Reason.EXPLICIT_NEGATION,result.requirements().get(0).match().reason());
    }
    @Test void contradictoryCandidateClaimsAreUnknown() {
        var result=matcher.match(List.of(requirement("Java")),true,List.of(candidate("Java"),candidate("不会Java")));
        assertEquals(EvaluationState.UNKNOWN,result.assessment().state());assertEquals(Reason.CONFLICTING_EVIDENCE,result.assessment().reason());
    }
    @Test void unresolvedNegationCannotBeHiddenByAnotherNameHit() {
        var result=matcher.match(List.of(requirement("Java")),true,List.of(candidate("Java"),candidate("不会Java，但是学过Python")));
        assertEquals(Reason.CONFLICTING_EVIDENCE,result.assessment().reason());
    }
    @Test void descriptionNegationCannotBeIgnored() {
        var c=extractor.extract("skill:1","Java","不会Java，只写过JavaScript",null,null,false);
        assertEquals(EvaluationState.UNKNOWN,matcher.match(List.of(requirement("Java")),true,List.of(c)).assessment().state());
    }
    @Test void highProficiencyCannotOverrideNegativeRawText() {
        var c=extractor.extract("skill:1","不会Java",null,null,AbilityProficiency.EXPERT,false);
        assertEquals(EvaluationState.UNKNOWN,matcher.match(List.of(requirement("Java")),true,List.of(c)).assessment().state());
    }
    @Test void uncalibratedLevelRequirementIsNotAutomaticFullScore() {
        assertEquals(EvaluationState.UNKNOWN,compare("精通Java","了解Java").assessment().state());
        assertEquals(EvaluationState.UNKNOWN,compare("熟悉Java","熟悉Java").assessment().state());
        var r=extractor.extract("job:1","Java",null,"Java",AbilityProficiency.FAMILIAR,true);
        assertEquals(EvaluationState.UNKNOWN,matcher.match(List.of(r),true,List.of(candidate("Java"))).assessment().state());
    }
    @Test void candidateLevelDoesNotAddBonusToPlainNameRequirement() {score("100",compare("Java","精通Java"));}
    @Test void definiteAbsenceOverridesUncalibratedLevelRequirement() {
        score("0",compare("精通Java","不会Java"));
        score("0",compare("精通Java","JavaScript"));
    }
    @Test void relatedTechnologiesAreNotDeclaredIncompatibleByNameAlone() {
        assertNull(compare("JavaScript","TypeScript").assessment().score());
        assertNull(compare("Java","Spring Boot").assessment().score());
        assertNull(compare("JavaScript","Vue 3").assessment().score());
    }
    @Test void compoundRequirementsNeedScopeJudgmentEvenWithSameText() {
        for(String raw:List.of("Java and Python","会Java and Python","Java或Python","独立制定文档规范","Java>=17"))
            assertEquals(EvaluationState.UNKNOWN,compare(raw,raw).assessment().state(),raw);
    }
    @Test void missingListIsUnknownNotZero() {
        var result=matcher.match(List.of(requirement("Java")),true,List.of());assertNull(result.assessment().score());
        assertEquals(Reason.ABILITY_LIST_MISSING,result.assessment().reason());
    }
    @Test void missingRequirementsOrPartialParseCannotGiveFullScore() {
        assertNull(matcher.match(List.of(),true,List.of(candidate("Java"))).assessment().score());
        assertNull(matcher.match(List.of(requirement("Java")),false,List.of(candidate("Java"))).assessment().score());
    }
    @Test void skillSetFormulaAndDeduplicationKeepEvidence() {
        var requirements=List.of(requirement("Java"),requirement("Spring Boot"),requirement("MySQL"),requirement("Redis"),requirement("SpringBoot"));
        var result=matcher.match(requirements,true,List.of(candidate("Java"),candidate("SpringBoot"),candidate("MySQL"),candidate("Java")));
        score("75",result);assertEquals(4,result.requirements().size());assertEquals(2,result.requirements().get(1).equivalentRequirements().size());
    }
    @Test void differentlyConstrainedRequirementsAreNotMerged() {
        var result=matcher.match(List.of(requirement("Java"),requirement("熟悉Java")),true,List.of(candidate("Java")));
        assertEquals(2,result.requirements().size());assertNull(result.assessment().score());
    }
    @Test void unknownItemRetainsKnownItemResultsAndDenominator() {
        var result=matcher.match(List.of(requirement("Java"),requirement("跨部门沟通")),true,List.of(candidate("Java"),candidate("团队内部协作")));
        assertEquals(2,result.requirements().size());assertEquals(MatchDecision.MATCH,result.requirements().get(0).match().decision());
        assertEquals(MatchDecision.UNKNOWN,result.requirements().get(1).match().decision());assertNull(result.assessment().score());
    }
    @Test void fullEvidenceWinsOverUnrelatedUnknownCandidates() {
        score("100",matcher.match(List.of(requirement("Java")),true,List.of(candidate("Java"),candidate("善于沟通"))));
    }
    @Test void zeroEvidenceWithPossibleUnknownMatchStaysUnknown() {
        assertNull(matcher.match(List.of(requirement("Java")),true,List.of(candidate("JavaScript"),candidate("JVM生态开发"))).assessment().score());
    }
    @Test void vueWithoutVersionDoesNotAutomaticallyEqualVue3() {assertNull(compare("Vue 3","Vue").assessment().score());}
    @Test void legacyEnumerationPreservesSpringBootAndSymbols() {
        var result=extractor.legacyRequirements("Java, Spring Boot；C++、C#\nMySQL");assertEquals(5,result.size());
        assertEquals("spring boot",result.get(1).normalizedName());assertEquals("c++",result.get(2).normalizedName());
    }
    @Test void naturalLanguageCommaDoesNotDropDifficultRequirements() {
        var r=extractor.legacyRequirements("熟悉Java，能独立设计接口");assertEquals(1,r.size());assertTrue(r.get(0).constrained());
        assertNull(matcher.match(r,true,List.of(candidate("Java"))).assessment().score());
    }
    @Test void malformedLegacyEnumerationCannotBecomeAnAtomicNameMatch() {
        var r=extractor.legacyRequirements("Java\nSpring Boot\n");assertEquals(1,r.size());
        assertNull(matcher.match(r,true,List.of(candidate("Java Spring Boot"))).assessment().score());
    }
    @Test void explicitAbilityNameCannotReplaceContradictoryRawMeaning() {
        var r=extractor.extract("job:1","不要求Java",null,"Java",null,true);
        assertNull(matcher.match(List.of(r),true,List.of(candidate("Java"))).assessment().score());
    }
    @Test void disabledSemanticReturnsServiceReasonAndNoInventedSimilarity() {
        var result=compare("规范编写过程文档","按模板编写操作流程和交付文档");
        assertEquals(Reason.SEMANTIC_NOT_CONFIGURED,result.assessment().reason());assertNull(result.requirements().get(0).match().similarity());
    }
    @Test void semanticFailureDoesNotTurnIntoMismatchOrBreakNameMatching() {
        SemanticAbilityMatcher failing=new SemanticAbilityMatcher() {
            public PairMatch match(AbilityFeature r,AbilityFeature c){throw new IllegalStateException("test timeout");}
            public String modelVersion(){return "test-model";} public String calibrationVersion(){return "test-calibration";}
        };
        SkillMatcher m=new SkillMatcher(dictionary,failing);
        assertEquals(Reason.SEMANTIC_UNAVAILABLE,m.match(List.of(requirement("沟通")),true,List.of(candidate("协作"))).assessment().reason());
        score("100",m.match(List.of(requirement("Java")),true,List.of(candidate("Java"))));
    }
    @Test void calibratedPartialScoresCanBeAggregatedWithoutTreatingSimilarityAsScore() {
        SemanticAbilityMatcher stub=new SemanticAbilityMatcher() {
            public PairMatch match(AbilityFeature r,AbilityFeature c) {
                String value=r.rawText().equals("要求甲")?"100":r.rawText().equals("要求乙")?"80":"60";
                return new PairMatch(MatchMethod.SEMANTIC,value.equals("100")?MatchDecision.MATCH:MatchDecision.PARTIAL,new BigDecimal(value),new BigDecimal("0.9"),Reason.CALCULATED);
            }
            public String modelVersion(){return "test-only-model";}public String calibrationVersion(){return "test-only-calibration";}
        };
        score("80",new SkillMatcher(dictionary,stub).match(List.of(requirement("要求甲"),requirement("要求乙"),requirement("要求丙")),true,List.of(candidate("候选证据"))));
        // 此stub只验证汇总算术，没有部署模型，也不定义0.9对应多少匹配分。
    }
    @Test void semanticScoresWithoutModelAndCalibrationVersionsAreRejected() {
        SemanticAbilityMatcher unversioned=new DisabledSemanticAbilityMatcher() {
            @Override public PairMatch match(AbilityFeature r,AbilityFeature c) {
                return new PairMatch(MatchMethod.SEMANTIC,MatchDecision.MATCH,new BigDecimal("100"),null,Reason.CALCULATED);
            }
        };
        assertNull(new SkillMatcher(dictionary,unversioned).match(List.of(requirement("沟通")),true,List.of(candidate("协作"))).assessment().score());
    }
    @Test void semanticProviderCannotOverrideHardNegationOrKnownDistinctName() {
        SemanticAbilityMatcher overconfident=new SemanticAbilityMatcher() {
            public PairMatch match(AbilityFeature r,AbilityFeature c){fail("明确否定或不同能力不应调用语义层");return null;}
            public String modelVersion(){return "test-model";}public String calibrationVersion(){return "test-calibration";}
        };
        SkillMatcher m=new SkillMatcher(dictionary,overconfident);
        score("0",m.match(List.of(requirement("精通Java")),true,List.of(candidate("不会Java"))));
        score("0",m.match(List.of(requirement("Java")),true,List.of(candidate("JavaScript"))));
    }
    @Test void blankCriticalItemIsNotSilentlyDropped() {
        var result=matcher.match(List.of(requirement("Java"),requirement("  ")),true,List.of(candidate("Java")));
        assertEquals(2,result.requirements().size());assertNull(result.assessment().score());
    }
    @Test void invalidPairScoreAndDecisionCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class,()->new PairMatch(MatchMethod.NAME,MatchDecision.MATCH,BigDecimal.ZERO,null,Reason.NAME_MATCH));
        assertThrows(IllegalArgumentException.class,()->new PairMatch(MatchMethod.SEMANTIC,MatchDecision.UNKNOWN,BigDecimal.ONE,null,Reason.CALCULATED));
    }
}
