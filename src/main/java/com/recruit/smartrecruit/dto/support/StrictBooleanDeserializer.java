package com.recruit.smartrecruit.dto.support;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
    @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE))
            return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
        return parser.getBooleanValue();
    }
}
