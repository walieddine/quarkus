package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.Config;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;
import io.quarkus.kafka.client.serialization.JsonbSerializer;

public class KafkaChannels {

    private static final Pattern CONNECTOR_KEY = Pattern.compile("mp\\.messaging\\.(outgoing|incoming)\\.(.*)\\.connector");

    static List<KafkaChannelUsingJsonB> getKafkaChannelUsingJsonB(Config config) {
        // Get the channels using Kafka
        Map<String, String> channels = getKafkaChannels(config);

        // Get the channels using JSONB serializers/deserializers
        return getChannelsUsingJsonB(config, channels);
    }

    private static Map<String, String> getKafkaChannels(Config config) {
        Map<String, String> channels = new HashMap<>();

        config.getPropertyNames().forEach(s -> {
            Matcher matcher = CONNECTOR_KEY.matcher(s);
            if (matcher.matches() && config.getValue(s, String.class).equalsIgnoreCase("smallrye-kafka")) {
                channels.put(matcher.group(2), matcher.group(1));
            }
        });
        return channels;
    }

    private static List<KafkaChannelUsingJsonB> getChannelsUsingJsonB(Config config, Map<String, String> channels) {
        List<KafkaChannelUsingJsonB> selected = new ArrayList<>();
        for (Map.Entry<String, String> channel : channels.entrySet()) {
            KafkaChannelUsingJsonB c = new KafkaChannelUsingJsonB(channel.getKey(), channel.getValue());
            if (channel.getValue().equalsIgnoreCase("incoming")) {
                // Check for the key and value deserializers
                Optional<String> maybeKey = config
                        .getOptionalValue("mp.messaging.incoming." + channel.getKey() + ".key.deserializer", String.class);
                Optional<String> maybeValue = config
                        .getOptionalValue("mp.messaging.incoming." + channel.getKey() + ".value.deserializer", String.class);
                Optional<String> maybeKeyClassName = config
                        .getOptionalValue("mp.messaging.incoming." + channel.getKey() + ".key.classname", String.class);
                Optional<String> maybeValueClassName = config
                        .getOptionalValue("mp.messaging.incoming." + channel.getKey() + ".value.classname", String.class);

                if (maybeKey.isPresent() && maybeKey.get().equalsIgnoreCase(JsonbDeserializer.class.getName())) {
                    if (maybeKeyClassName.isPresent()) {
                        c.register(maybeKeyClassName.get());
                    } else {
                        c.markKey();
                    }
                }

                if (maybeValue.isPresent() && maybeValue.get().equalsIgnoreCase(JsonbDeserializer.class.getName())) {
                    if (maybeValueClassName.isPresent()) {
                        c.register(maybeValueClassName.get());
                    } else {
                        c.markValue();
                    }

                }

            } else { // outgoing
                // Check for the key and value serializers
                Optional<String> maybeKey = config
                        .getOptionalValue("mp.messaging.outgoing." + channel.getKey() + ".key.serializer", String.class);
                Optional<String> maybeValue = config
                        .getOptionalValue("mp.messaging.outgoing." + channel.getKey() + ".value.serializer", String.class);

                if (maybeKey.isPresent() && maybeKey.get().equalsIgnoreCase(JsonbSerializer.class.getName())) {
                    c.markKey();
                }

                if (maybeValue.isPresent() && maybeValue.get().equalsIgnoreCase(JsonbSerializer.class.getName())) {
                    c.markValue();
                }
            }
            if (c.isKeyUseJsonB() || c.isValueUseJsonB() || !c.getClassNamesToRegisterForReflection().isEmpty()) {
                selected.add(c);
            }
        }
        return selected;
    }

}
