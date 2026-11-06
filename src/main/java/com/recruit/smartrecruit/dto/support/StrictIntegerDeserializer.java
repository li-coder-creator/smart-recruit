package com.recruit.smartrecruit.dto.support;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        return parser.getIntValue();
    }
}
