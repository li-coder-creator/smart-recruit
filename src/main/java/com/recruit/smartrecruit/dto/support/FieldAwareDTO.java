package com.recruit.smartrecruit.dto.support;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

/** 用于尚未接入接口的新契约。只跟踪需要区分省略/null的字段，不接受身份或派生字段。 */
public abstract class FieldAwareDTO {
    @JsonIgnore
    private final Set<String> suppliedFields = new HashSet<>();

    protected final void markProvided(String field) {
        suppliedFields.add(field);
    }

    @JsonIgnore
    public final boolean wasProvided(String field) {
        return suppliedFields.contains(field);
    }

    @JsonAnySetter
    public final void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("不支持的请求字段：" + field);
    }
}
