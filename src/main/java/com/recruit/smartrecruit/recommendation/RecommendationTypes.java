package com.recruit.smartrecruit.recommendation;

/** 推荐内部类型；不以ordinal表示业务等级、状态或分数。 */
public final class RecommendationTypes {
    private RecommendationTypes() {}
    public enum Dimension { SKILL, SALARY, CITY, EXPERIENCE, EDUCATION }
    public enum EvaluationState { SCORED, UNKNOWN, NOT_APPLICABLE }
    public enum Reason {
        CALCULATED, REQUIREMENT_MET, REQUIREMENT_NOT_MET, EXPLICITLY_UNLIMITED,
        DATA_MISSING, UNRECOGNIZED_VALUE, INVALID_DATA, UNCONFIRMED_EXPERIENCE,
        CONFLICTING_EVIDENCE, EDUCATION_UNCERTAIN, DIFFERENT_SALARY_UNIT, NEGOTIABLE,
        ABILITY_REQUIREMENTS_UNKNOWN, ABILITY_LIST_MISSING, CONSTRAINT_UNRESOLVED,
        SEMANTIC_NOT_CONFIGURED, SEMANTIC_UNAVAILABLE, NAME_MATCH, ALIAS_MATCH, EXPLICIT_NEGATION,
        DISTINCT_NAMED_SKILLS, CORE_SKILLS_UNKNOWN, NO_COMPARABLE_DIMENSIONS, NO_SCORED_DIMENSIONS
    }
    public enum EducationLevel {
        ASSOCIATE(1), BACHELOR(2), MASTER(3), DOCTOR(4);
        private final int rank;
        EducationLevel(int rank) { this.rank = rank; }
        public int rank() { return rank; }
    }
    public enum RequirementKind { ANY, MINIMUM, STUDENT, NEW_GRAD, UNKNOWN }
    public enum StudyStage { IN_PROGRESS, END_DATE_PASSED, STAGE_UNKNOWN, PLANNED, INVALID }
    public enum Polarity { POSITIVE, NEGATIVE, UNKNOWN }
    public enum MatchMethod { NAME, ALIAS, CONSTRAINT, SEMANTIC, NONE }
    public enum MatchDecision { MATCH, PARTIAL, NO_MATCH, UNKNOWN }
}
