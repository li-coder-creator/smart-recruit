package com.recruit.smartrecruit.recommendation;

import com.recruit.smartrecruit.entity.enums.AbilityProficiency;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;
import static com.recruit.smartrecruit.recommendation.RecommendationFeatures.AbilityFeature;
import static com.recruit.smartrecruit.recommendation.RecommendationTypes.Polarity;

/** 仅提取完整、简单的能力表达；复杂语句保留原文并转交待配置的语义层。 */
@Component
public class AbilityFeatureExtractor {
    private static final Pattern TECHNICAL_NAME = Pattern.compile("[a-z][a-z0-9+#. -]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE = Pattern.compile("^(会|了解|熟悉|熟练掌握|熟练使用|熟练|精通|掌握|擅长)\\s*([a-z][a-z0-9+#. -]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE = Pattern.compile("^(不会|不懂)\\s*([a-z][a-z0-9+#. -]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT = Pattern.compile("不|未|无|没有|仅|只|至少|以上|以下|以内|独立|必须|要求|能够|需要|精通|熟练|熟悉|了解|掌握|擅长|和|或|以及|并|[，,;；、/\\n]|(?i:\\b(?:not|no|only|must|without|and|or)\\b)|[<>=≥≤]");
    private static final Pattern CONTEXT_RESTRICTION = Pattern.compile("不|未|无|没有|仅|只|(?i:\\b(?:not|no|only|without)\\b)");
    private final RuleDictionary dictionary;
    public AbilityFeatureExtractor(RuleDictionary dictionary) { this.dictionary = dictionary; }

    public AbilityFeature extract(String sourceRef, String raw, String description, String explicitName,
                                  AbilityProficiency proficiency, boolean requirement) {
        String text = RuleDictionary.token(raw);
        String name = null; Polarity polarity = Polarity.POSITIVE;
        boolean constrained = false, level = requirement && proficiency != null;
        List<String> warnings = new ArrayList<>();
        Matcher positive = POSITIVE.matcher(text), negative = NEGATIVE.matcher(text);
        if (text.isBlank()) {
            polarity = Polarity.UNKNOWN; constrained = true; warnings.add("能力原文为空");
        } else if (negative.matches()) {
            name = negative.group(2).strip(); polarity = Polarity.NEGATIVE;
            if (requirement) constrained = true; // “不要求某技能”不能当成正向必备要求。
        } else if (positive.matches()) {
            name = positive.group(2).strip();
            if (requirement && !positive.group(1).equals("会")) level = true;
            if (CONSTRAINT.matcher(name).find()) constrained = true;
        } else if (CONSTRAINT.matcher(text).find()) {
            constrained = true; polarity = Polarity.UNKNOWN;
        } else {
            name = text; // 未入词典的新技术、完整同名通用能力也可以精确比较。
        }
        if (description != null && CONTEXT_RESTRICTION.matcher(RuleDictionary.token(description)).find()) {
            constrained = true; warnings.add("补充描述包含否定或范围限制，不能只用名称覆盖");
        }
        String canonical = name == null ? null : dictionary.canonicalAbility(name);
        if (explicitName != null && !explicitName.isBlank()
                && !Objects.equals(canonical, dictionary.canonicalAbility(explicitName))) {
            constrained = true; warnings.add("用户名称与原文未形成可确认的等价证据");
        }
        if (level) warnings.add("要求含掌握程度；跨程度比较规则尚未校准");
        if (polarity == Polarity.NEGATIVE && proficiency != null) {
            constrained = true; warnings.add("否定原文与程度属性矛盾，不能覆盖原文");
        }
        if (constrained) warnings.add("存在未解决的能力范围、否定或复合表达");
        return new AbilityFeature(sourceRef, raw, description, name, canonical, proficiency, polarity, constrained, level, warnings);
    }
    public List<AbilityFeature> legacyRequirements(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split("[,，、;；\\r\\n]+", -1);
        // 仅把明确名称枚举拆为列表；复杂自然语言中的逗号不作为无条件拆分边界。
        boolean enumeration = Arrays.stream(parts).allMatch(p -> TECHNICAL_NAME.matcher(RuleDictionary.token(p)).matches()
                && !CONSTRAINT.matcher(RuleDictionary.token(p)).find());
        if (parts.length > 1 && enumeration) {
            List<AbilityFeature> result = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) result.add(extract("legacy:" + i, parts[i], null, null, null, true));
            return List.copyOf(result);
        }
        AbilityFeature feature = extract("legacy:0", raw, null, null, null, true);
        if (parts.length > 1) feature = new AbilityFeature(feature.sourceRef(), feature.rawText(), feature.description(),
                feature.normalizedName(), feature.canonicalName(), feature.proficiency(), Polarity.UNKNOWN, true,
                feature.levelRequirement(), List.of("旧文本包含不能可靠拆分的复合要求，保留整段原文待确认"));
        return List.of(feature);
    }
}
