package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class KafkaChannelUsingJsonB extends MultiBuildItem {
    private String name;
    private String direction;
    private boolean valueUseJsonB;
    private boolean keyUseJsonB;
    private List<DotName> classNamesToRegisterForReflection = new ArrayList<>();
    private DotName classnameAttribute;

    public KafkaChannelUsingJsonB(String name, String direction) {
        this.name = name;
        this.direction = direction;
    }

    public String getName() {
        return name;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isValueUseJsonB() {
        return valueUseJsonB;
    }

    public boolean isKeyUseJsonB() {
        return keyUseJsonB;
    }

    public List<DotName> getClassNamesToRegisterForReflection() {
        return classNamesToRegisterForReflection;
    }

    public void register(DotName name) {
        classNamesToRegisterForReflection.add(name);
    }

    public void setClassNameAttribute(DotName name) {
        this.classnameAttribute = name;
    }

    public void register(String name) {
        classNamesToRegisterForReflection.add(DotName.createSimple(name));
    }

    public void markKey() {
        keyUseJsonB = true;
    }

    public void markValue() {
        valueUseJsonB = true;
    }
}
