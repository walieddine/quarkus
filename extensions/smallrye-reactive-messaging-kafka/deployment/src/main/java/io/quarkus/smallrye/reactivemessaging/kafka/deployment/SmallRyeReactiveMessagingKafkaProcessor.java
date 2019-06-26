package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.reactivestreams.Publisher;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.reactivex.Flowable;
import io.smallrye.reactive.messaging.kafka.KafkaMessage;

public class SmallRyeReactiveMessagingKafkaProcessor {

    public static final DotName OUTGOING_DOT_NAME = DotName.createSimple(Outgoing.class.getName());
    public static final DotName INCOMING_DOT_NAME = DotName.createSimple(Incoming.class.getName());

    // For testing purpose only.
    public static volatile List<KafkaChannelUsingJsonB> CHANNELS = new CopyOnWriteArrayList<>();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FeatureBuildItem.SMALLRYE_REACTIVE_MESSAGING_KAFKA);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void analyze(CombinedIndexBuildItem index, BuildProducer<KafkaChannelUsingJsonB> producer) {
        Config config = ConfigProvider.getConfig();
        List<KafkaChannelUsingJsonB> selected = KafkaChannels.getKafkaChannelUsingJsonB(config);

        for (KafkaChannelUsingJsonB channel : selected) {
            if (channel.getDirection().equalsIgnoreCase("outgoing")) {
                List<MethodInfo> methods = getOutgoingMethod(index, channel.getName());
                for (MethodInfo method : methods) {
                    processOutgoingMethod(method, channel);
                }
            } else {
                List<MethodInfo> methods = getIncomingMethod(index, channel.getName());
                for (MethodInfo method : methods) {
                    processIncomingMethod(method, channel);
                }
            }
        }

        selected.forEach(producer::produce);
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void process(List<KafkaChannelUsingJsonB> channels, BuildProducer<ReflectiveClassBuildItem> producer) {
        for (KafkaChannelUsingJsonB channel : channels) {
            channel.getClassNamesToRegisterForReflection()
                    .forEach(dn -> producer.produce(new ReflectiveClassBuildItem(true, true, true, dn.toString())));
        }

        CHANNELS.clear();
        CHANNELS.addAll(channels);
    }

    private void processOutgoingMethod(MethodInfo method, KafkaChannelUsingJsonB channel) {
        Type type = method.returnType();
        DotName typeName = type.name();
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE
                && (typeName.equals(DotName.createSimple(CompletionStage.class.getName()))
                        || typeName.equals(DotName.createSimple(CompletableFuture.class.getName()))
                        || typeName.equals(DotName.createSimple(Publisher.class.getName()))
                        || typeName.equals(DotName.createSimple(PublisherBuilder.class.getName()))
                        || typeName.equals(DotName.createSimple(Flowable.class.getName())))) {
            // Extract the type of the parameter.
            List<Type> params = type.asParameterizedType().arguments();
            if (!params.isEmpty()) {
                Type param = params.get(0);
                extractType(channel, param);
            }
        } else {
            extractType(channel, type);
        }
    }

    private void processIncomingMethod(MethodInfo method, KafkaChannelUsingJsonB channel) {
        // TODO Deinfe the classname?
        List<Type> parameters = method.parameters();
        if (parameters.size() == 1) {
            // Message, Payload or Stream
            DotName typeName = parameters.get(0).name();
            if (parameters.get(0).kind() == Type.Kind.PARAMETERIZED_TYPE
                    && (typeName.equals(DotName.createSimple(Publisher.class.getName()))
                            || typeName.equals(DotName.createSimple(PublisherBuilder.class.getName()))
                            || typeName.equals(DotName.createSimple(Flowable.class.getName())))) {
                // Extract the type of the parameter.
                List<Type> params = parameters.get(0).asParameterizedType().arguments();
                if (!params.isEmpty()) {
                    Type param = params.get(0);
                    extractType(channel, param);
                }
            } else {
                extractType(channel, parameters.get(0));
            }
        }
    }

    private void extractType(KafkaChannelUsingJsonB channel, Type param) {
        if (param.kind() == Type.Kind.PARAMETERIZED_TYPE
                && param.name().equals(DotName.createSimple(Message.class.getName()))) {
            channel.register(param.asParameterizedType().arguments().get(0).name());
        } else if (param.kind() == Type.Kind.PARAMETERIZED_TYPE
                && param.name().equals(DotName.createSimple(KafkaMessage.class.getName()))) {
            List<Type> types = param.asParameterizedType().arguments();
            // Expect to get key and value
            if (types.get(0).kind() == Type.Kind.CLASS) {
                channel.register(types.get(0).name());
            }
            if (types.get(1).kind() == Type.Kind.CLASS) {
                channel.register(types.get(1).name());
            }
        } else {
            // Payload
            channel.register(param.name());
        }
    }

    private List<MethodInfo> getOutgoingMethod(CombinedIndexBuildItem index, String channel) {
        return index.getIndex().getAnnotations(OUTGOING_DOT_NAME).stream()
                .filter(ai -> channel.equalsIgnoreCase(ai.value().asString()))
                .map(ai -> ai.target().asMethod())
                .collect(Collectors.toList());
    }

    private List<MethodInfo> getIncomingMethod(CombinedIndexBuildItem index, String channel) {
        return index.getIndex().getAnnotations(INCOMING_DOT_NAME).stream()
                .filter(ai -> channel.equalsIgnoreCase(ai.value().asString()))
                .map(ai -> ai.target().asMethod())
                .collect(Collectors.toList());
    }

}
