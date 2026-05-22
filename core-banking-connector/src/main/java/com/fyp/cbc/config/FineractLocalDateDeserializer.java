package com.fyp.cbc.config;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for dates returned by Apache Fineract.
 *
 * Fineract REST API returns dates in three possible forms:
 *   1. Integer array:  [2024, 4, 20]                (most common in GET responses)
 *   2. Display string: "20 April 2024"              (dd MMMM yyyy)
 *   3. ISO string:     "2024-04-20"
 *
 * Java's built-in LocalDate deserializer only handles ISO strings, so it throws on
 * Fineract array-format dates.  This deserializer handles all three.
 */
public class FineractLocalDateDeserializer extends StdDeserializer<LocalDate> {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    public FineractLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonToken token = p.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        // [year, month, day]
        if (token == JsonToken.START_ARRAY) {
            int year = 0, month = 0, day = 0;
            JsonToken next = p.nextToken();
            if (next == JsonToken.VALUE_NUMBER_INT) {
                year = p.getIntValue();
                next = p.nextToken();
            }
            if (next == JsonToken.VALUE_NUMBER_INT) {
                month = p.getIntValue();
                next = p.nextToken();
            }
            if (next == JsonToken.VALUE_NUMBER_INT) {
                day = p.getIntValue();
                p.nextToken(); // END_ARRAY
            } else if (next == JsonToken.END_ARRAY) {
                // partial / empty array — skip
            }
            if (year == 0 || month == 0 || day == 0) return null;
            return LocalDate.of(year, month, day);
        }

        // "dd MMMM yyyy" or "yyyy-MM-dd"
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) return null;
            try {
                return LocalDate.parse(text, DISPLAY_FMT);
            } catch (Exception ignored) {
                // ignored
            }
            try {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
                // ignored
            }
            return null;
        }

        // Numbers are occasionally used for epoch days in some Fineract builds
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return LocalDate.ofEpochDay(p.getLongValue());
        }

        return null;
    }
}
