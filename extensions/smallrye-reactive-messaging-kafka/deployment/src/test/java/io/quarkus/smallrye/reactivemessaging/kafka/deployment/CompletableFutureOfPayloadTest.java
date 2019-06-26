package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.jandex.DotName;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class CompletableFutureOfPayloadTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Person.class, OutgoingBean2.class)
                    .addAsResource(new File("src/test/resources/kafka-configuration.properties"), "application.properties"));

    @Test
    public void test() {
        Assertions.assertEquals(SmallRyeReactiveMessagingKafkaProcessor.CHANNELS.size(), 1);
        Assertions.assertEquals(
                SmallRyeReactiveMessagingKafkaProcessor.CHANNELS.get(0).getClassNamesToRegisterForReflection().get(0),
                DotName.createSimple(Person.class.getName()));
    }

    @ApplicationScoped
    public static class OutgoingBean2 {

        @Outgoing("out")
        public CompletableFuture<Person> produce() {
            return CompletableFuture.supplyAsync(() -> new Person("Bob", 23));
        }
    }
}
