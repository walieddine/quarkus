package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

public class Cat {

    String name;

    public String getName() {
        return name;
    }

    public Cat setName(String name) {
        this.name = name;
        return this;
    }
}
