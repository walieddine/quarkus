package io.quarkus.jackson.deployment;

import static org.jboss.jandex.AnnotationTarget.Kind.CLASS;
import static org.jboss.jandex.AnnotationTarget.Kind.FIELD;
import static org.jboss.jandex.AnnotationTarget.Kind.METHOD;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveHierarchyBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.jackson.ObjectMapperProducer;
import io.quarkus.jackson.spi.JacksonModuleBuildItem;

public class JacksonProcessor {

    private static final DotName JSON_DESERIALIZE = DotName.createSimple(JsonDeserialize.class.getName());
    private static final DotName JSON_SERIALIZE = DotName.createSimple(JsonSerialize.class.getName());
    private static final DotName BUILDER_VOID = DotName.createSimple(Void.class.getName());

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyClass;

    @Inject
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @Inject
    List<IgnoreJsonDeserializeClassBuildItem> ignoreJsonDeserializeClassBuildItems;

    @BuildStep(providesCapabilities = Capabilities.JACKSON)
    void register() {
        addReflectiveClass(true, false,
                "com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector",
                "com.fasterxml.jackson.databind.ser.std.SqlDateSerializer");

        IndexView index = combinedIndexBuildItem.getIndex();

        // TODO: @JsonDeserialize is only supported as a class annotation - we should support the others as well

        Set<DotName> ignoredDotNames = new HashSet<>();
        for (IgnoreJsonDeserializeClassBuildItem ignoreJsonDeserializeClassBuildItem : ignoreJsonDeserializeClassBuildItems) {
            ignoredDotNames.add(ignoreJsonDeserializeClassBuildItem.getDotName());
        }

        // handle the various @JsonDeserialize cases
        for (AnnotationInstance deserializeInstance : index.getAnnotations(JSON_DESERIALIZE)) {
            AnnotationTarget annotationTarget = deserializeInstance.target();
            if (CLASS.equals(annotationTarget.kind())) {
                DotName dotName = annotationTarget.asClass().name();
                if (!ignoredDotNames.contains(dotName)) {
                    addReflectiveHierarchyClass(dotName);
                }

                AnnotationValue annotationValue = deserializeInstance.value("builder");
                if (null != annotationValue && AnnotationValue.Kind.CLASS.equals(annotationValue.kind())) {
                    DotName builderClassName = annotationValue.asClass().name();
                    if (!BUILDER_VOID.equals(builderClassName)) {
                        addReflectiveHierarchyClass(builderClassName);
                    }
                }
            } else if (FIELD.equals(annotationTarget.kind()) || METHOD.equals(annotationTarget.kind())) {
                AnnotationValue usingValue = deserializeInstance.value("using");
                if (usingValue != null) {
                    // the Deserializers are constructed internally by Jackson using a no-args constructor
                    reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, usingValue.asClass().toString()));
                }
            }
        }

        // handle the various @JsonSerialize cases
        for (AnnotationInstance serializeInstance : index.getAnnotations(JSON_SERIALIZE)) {
            AnnotationTarget annotationTarget = serializeInstance.target();
            if (FIELD.equals(annotationTarget.kind()) || METHOD.equals(annotationTarget.kind())) {
                AnnotationValue usingValue = serializeInstance.value("using");
                if (usingValue != null) {
                    // the Deserializers are constructed internally by Jackson using a no-args constructor
                    reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, usingValue.asClass().toString()));
                }
            }
        }

        // this needs to be registered manually since the runtime module is not indexed by Jandex
        additionalBeans.produce(new AdditionalBeanBuildItem(ObjectMapperProducer.class));
    }

    private void addReflectiveHierarchyClass(DotName className) {
        Type jandexType = Type.create(className, Type.Kind.CLASS);
        reflectiveHierarchyClass.produce(new ReflectiveHierarchyBuildItem(jandexType));
    }

    private void addReflectiveClass(boolean methods, boolean fields, String... className) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(methods, fields, className));
    }

    // Generate a ObjectMapperCustomizer bean that registers each serializer / deserializer with ObjectMapper
    @BuildStep
    void generateCustomizer(BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            List<JacksonModuleBuildItem> jacksonModules) {

        if (jacksonModules.isEmpty()) {
            return;
        }

        ClassOutput classOutput = new ClassOutput() {
            @Override
            public void write(String name, byte[] data) {
                generatedBeans.produce(new GeneratedBeanBuildItem(name, data));
            }
        };

        try (ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className("io.quarkus.jackson.customizer.RegisterSerializersAndDeserializersCustomizer")
                .interfaces(ObjectMapperCustomizer.class.getName())
                .build()) {
            classCreator.addAnnotation(Singleton.class);

            try (MethodCreator customize = classCreator.getMethodCreator("customize", void.class, ObjectMapper.class)) {
                ResultHandle objectMapper = customize.getMethodParam(0);

                for (JacksonModuleBuildItem jacksonModule : jacksonModules) {
                    if (jacksonModule.getItems().isEmpty()) {
                        continue;
                    }

                    /*
                     * Create code similar to the following:
                     *
                     * SimpleModule module = new SimpleModule("somename");
                     * module.addSerializer(Foo.class, new FooSerializer());
                     * module.addDeserializer(Foo.class, new FooDeserializer());
                     * objectMapper.registerModule(module);
                     */
                    ResultHandle module = customize.newInstance(
                            MethodDescriptor.ofConstructor(SimpleModule.class, String.class),
                            customize.load(jacksonModule.getName()));

                    for (JacksonModuleBuildItem.Item item : jacksonModule.getItems()) {
                        ResultHandle targetClass = customize.loadClass(item.getTargetClassName());

                        String serializerClassName = item.getSerializerClassName();
                        if (serializerClassName != null && !serializerClassName.isEmpty()) {
                            ResultHandle serializer = customize.newInstance(
                                    MethodDescriptor.ofConstructor(serializerClassName));
                            customize.invokeVirtualMethod(
                                    MethodDescriptor.ofMethod(SimpleModule.class, "addSerializer", SimpleModule.class,
                                            Class.class, JsonSerializer.class),
                                    module, targetClass, serializer);
                        }

                        String deserializerClassName = item.getDeserializerClassName();
                        if (deserializerClassName != null && !deserializerClassName.isEmpty()) {
                            ResultHandle deserializer = customize.newInstance(
                                    MethodDescriptor.ofConstructor(deserializerClassName));
                            customize.invokeVirtualMethod(
                                    MethodDescriptor.ofMethod(SimpleModule.class, "addDeserializer", SimpleModule.class,
                                            Class.class, JsonDeserializer.class),
                                    module, targetClass, deserializer);
                        }
                    }

                    customize.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(ObjectMapper.class, "registerModule", ObjectMapper.class, Module.class),
                            objectMapper, module);
                }

                customize.returnValue(null);
            }
        }
    }
}
