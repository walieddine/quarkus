package io.quarkus.kafka.client.serialization;

import java.nio.charset.Charset;
import java.util.Map;

import javax.json.JsonStructure;

import org.apache.kafka.common.serialization.Serializer;

/**
 * A {@link Serializer} that serializes JSON-P structures (JSON Objects or JSON Arrays).
 */
public class JsonSerializer implements Serializer<JsonStructure> {
    @Override
    public void configure(Map<String, ?> map, boolean b) {
        // Do nothing.
    }

    @Override
    public byte[] serialize(String topic, JsonStructure data) {
        if (data == null) {
            return new byte[0];
        }
        return data.toString().getBytes(Charset.forName("UTF-8"));
    }

    @Override
    public void close() {
        // Do nothing.
    }
}
