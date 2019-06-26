package io.quarkus.smallrye.reactivemessaging.kafka.deployment;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.jandex.DotName;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class CompletableFutureOfMessageTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Cat.class, OutgoingBean.class)
                    .addAsResource(new File("src/test/resources/kafka-configuration.properties"), "application.properties"));

    @Test
    public void test() {
        Assertions.assertEquals(SmallRyeReactiveMessagingKafkaProcessor.CHANNELS.size(), 1);
        Assertions.assertEquals(
                SmallRyeReactiveMessagingKafkaProcessor.CHANNELS.get(0).getClassNamesToRegisterForReflection().get(0),
                DotName.createSimple(Cat.class.getName()));
    }

    @ApplicationScoped
    public static class OutgoingBean {

        @Outgoing("out")
        public CompletableFuture<Message<Cat>> produce() {
            return CompletableFuture.supplyAsync(() -> Message.of(new Cat().setName("garfield")));
        }
    }
}
