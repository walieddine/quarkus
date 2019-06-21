package io.quarkus.kafka.client.serialization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Deserializer} that deserializes JSON using JSON-B.
 */
public class JsonbDeserializer<T> implements Deserializer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonbDeserializer.class);

    private final Jsonb jsonb;
    private Class<T> type;
    private final boolean jsonbNeedsClosing;

    public JsonbDeserializer() {
        this(null);
    }

    public JsonbDeserializer(Class<T> type) {
        this(type, JsonbBuilder.create(), true);
    }

    public JsonbDeserializer(Class<T> type, Jsonb jsonb) {
        this(type, jsonb, false);
    }

    private JsonbDeserializer(Class<T> type, Jsonb jsonb, boolean jsonbNeedsClosing) {
        this.type = type;
        this.jsonb = jsonb;
        this.jsonbNeedsClosing = jsonbNeedsClosing;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String key = isKey ? "key.classname" : "value.classname";
        String classname = (String) configs.get(key);
        if (classname != null) {
            try {
                type = (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(classname);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Unable to load the class " + classname);
            }
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (type == null) {
            LOGGER.error("Cannot deserialize the record, the classname has not be set. You need to configure the" +
                    " `[key|value].classname` attribute");
            return null;
        }
        if (data == null) {
            return null;
        }

        try (InputStream is = new ByteArrayInputStream(data)) {
            return jsonb.fromJson(is, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (!jsonbNeedsClosing) {
            return;
        }

        try {
            jsonb.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
