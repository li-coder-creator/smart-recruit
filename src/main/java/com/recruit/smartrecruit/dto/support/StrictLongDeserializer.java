package com.recruit.smartrecruit.dto.support;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class StrictLongDeserializer extends JsonDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return (Long) context.handleUnexpectedToken(Long.class, parser);
        return parser.getLongValue();
    }
}
