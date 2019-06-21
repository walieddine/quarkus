package io.quarkus.kafka.client.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JsonSerializerTest {

    private static JsonSerializer serializer;
    private static JsonDeserializer deserializer;

    @BeforeAll
    static void prepare() {
        serializer = new JsonSerializer();
        deserializer = new JsonDeserializer();
    }

    @AfterAll
    static void close() {
        serializer.close();
        deserializer.close();
    }

    @Test
    void testWithObject() {
        JsonObject expected = Json.createObjectBuilder().add("id", 1L).add("name", "neo").build();
        byte[] bytes = serializer.serialize(null, expected);
        JsonStructure structure = deserializer.deserialize(null, bytes);
        assertThat(structure).isInstanceOf(JsonObject.class);
        JsonObject json = structure.asJsonObject();
        assertThat(json.getString("name")).isEqualTo(expected.getString("name"));
        assertThat(json.getJsonNumber("id").longValue()).isEqualTo(expected.getJsonNumber("id").longValue());
    }

    @Test
    void testWithArray() {
        JsonObject item1 = Json.createObjectBuilder().add("id", 1L).add("name", "neo").build();
        JsonObject item2 = Json.createObjectBuilder().add("id", 2L).add("name", "toad").build();
        JsonArray expected = Json.createArrayBuilder().add(item1).add(item2).build();
        byte[] bytes = serializer.serialize(null, expected);
        JsonStructure structure = deserializer.deserialize(null, bytes);
        assertThat(structure).isInstanceOf(JsonArray.class);
        JsonArray json = structure.asJsonArray();
        assertThat(json).hasSize(2);
        assertThat(json.getJsonObject(0).getString("name")).isEqualTo(item1.getString("name"));
        assertThat(json.getJsonObject(0).getJsonNumber("id").longValue()).isEqualTo(item1.getJsonNumber("id").longValue());

        assertThat(json.getJsonObject(1).getString("name")).isEqualTo(item2.getString("name"));
        assertThat(json.getJsonObject(1).getJsonNumber("id").longValue()).isEqualTo(item2.getJsonNumber("id").longValue());
    }

}
