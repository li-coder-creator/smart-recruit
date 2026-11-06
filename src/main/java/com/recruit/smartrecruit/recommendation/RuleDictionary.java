package com.recruit.smartrecruit.recommendation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.recruit.smartrecruit.entity.enums.JobDirection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

/** 有限的精确别名资源，不作为全量技能或全国城市识别服务。 */
@Component
public class RuleDictionary {
    private final Map<String, String> cities;
    private final Map<String, String> cityLabels;
    private final Map<String, String> aliases;
    private final Map<String, JobDirection> directions;
    private final Set<String> independentSkills;
    private final Set<Set<String>> unresolvedSkillPairs;
    private final String normalizationVersion, aliasVersion, extractorVersion;

    public RuleDictionary() {
        try (var input = new ClassPathResource("recommendation/normalization-v1.json").getInputStream()) {
            JsonNode root = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(input);
            normalizationVersion = version(root, "normalizationVersion");
            aliasVersion = version(root, "aliasVersion"); extractorVersion = version(root, "extractorVersion");
            cities = aliases(root.required("cities")); aliases = aliases(root.required("skills"));
            Map<String,String> labels = new HashMap<>();
            root.required("cities").properties().forEach(e -> labels.put(e.getKey(),e.getValue().get(0).asText()));
            cityLabels = Map.copyOf(labels);
            Map<String, JobDirection> directionMap = new HashMap<>();
            root.required("directions").properties().forEach(e -> directionMap.put(token(e.getKey()), JobDirection.valueOf(e.getValue().asText())));
            directions = Map.copyOf(directionMap);
            Set<String> names = new HashSet<>();
            root.required("independentSkills").forEach(n -> {
                if (!aliases.containsValue(token(n.asText()))) throw new IllegalArgumentException("独立能力缺少明确名称");
                names.add(token(n.asText()));
            });
            independentSkills = Set.copyOf(names);
            Set<Set<String>> pairs = new HashSet<>();
            root.required("unresolvedSkillPairs").forEach(pair -> {
                if (!pair.isArray() || pair.size() != 2) throw new IllegalArgumentException("待确认技术关系必须是两个名称");
                Set<String> values = Set.of(token(pair.get(0).asText()), token(pair.get(1).asText()));
                if (!independentSkills.containsAll(values)) throw new IllegalArgumentException("待确认技术关系引用了未定义名称");
                pairs.add(values);
            });
            unresolvedSkillPairs = Set.copyOf(pairs);
        } catch (IOException exception) { throw new IllegalStateException("推荐规则资源读取失败", exception); }
    }
    private static String version(JsonNode root, String name) {
        String value = root.required(name).asText();
        if (value.isBlank()) throw new IllegalArgumentException("规则版本不能为空");
        return value;
    }
    private static Map<String, String> aliases(JsonNode object) {
        Map<String, String> result = new HashMap<>();
        object.properties().forEach(e -> e.getValue().forEach(n -> {
            String old = result.putIfAbsent(token(n.asText()), e.getKey());
            if (old != null && !old.equals(e.getKey())) throw new IllegalArgumentException("别名含有冲突");
        }));
        return Map.copyOf(result);
    }
    public static String token(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip()
                .replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }
    public String city(String raw) {
        String value=cities.get(token(raw));
        if(value!=null) return value;
        String code=token(raw).toUpperCase(Locale.ROOT);
        return cityLabels.containsKey(code)?code:null;
    }
    public Map<String,String> cityLabels() { return cityLabels; }
    public JobDirection direction(String raw) { return directions.get(token(raw)); }
    public String canonicalAbility(String name) { return aliases.getOrDefault(token(name), token(name)); }
    public boolean distinctNamedSkills(String a, String b) {
        return a != null && b != null && !a.equals(b) && independentSkills.contains(a) && independentSkills.contains(b)
                && !unresolvedSkillPairs.contains(Set.of(a, b));
    }
    public String normalizationVersion() { return normalizationVersion; }
    public String aliasVersion() { return aliasVersion; }
    public String extractorVersion() { return extractorVersion; }
}
