package io.quarkus.kafka.client.serialization;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonStructure;

import org.apache.kafka.common.serialization.Deserializer;

/**
 * A {@link Deserializer} that deserializes JSON structures using JSON-P.
 * The resulting structure can be a JSON Object or a JSON Array.
 */
public class JsonDeserializer implements Deserializer<JsonStructure> {

    @Override
    public void configure(Map<String, ?> map, boolean b) {

    }

    @Override
    public JsonStructure deserialize(String s, byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            throw new IllegalArgumentException("Invalid JSON payload");
        }

        String str = new String(bytes, StandardCharsets.UTF_8);
        StringReader reader = new StringReader(str);
        try (JsonReader jsonReader = Json.createReader(reader)) {
            return jsonReader.read();
        }
    }

    @Override
    public void close() {

    }
}
