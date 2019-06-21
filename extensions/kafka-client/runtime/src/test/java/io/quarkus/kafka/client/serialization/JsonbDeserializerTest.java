package io.quarkus.kafka.client.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JsonbDeserializerTest {

    private static JsonbSerializer<Person> serializer;
    private static JsonbDeserializer<Person> deserializer;

    @BeforeAll
    static void prepare() {
        serializer = new JsonbSerializer<>();
        deserializer = new JsonbDeserializer<>();
        Map<String, String> config = new HashMap<>();
        config.put("value.classname", Person.class.getName());
        deserializer.configure(config, false);
    }

    @AfterAll
    static void close() {
        serializer.close();
        deserializer.close();
    }

    @Test
    void testWithoutFriends() {
        Person expected = new Person(1L, "neo", 2, null);
        byte[] bytes = serializer.serialize(null, expected);
        Person person = deserializer.deserialize(null, bytes);
        assertThat(person.id).isEqualTo(expected.id);
        assertThat(person.name).isEqualTo(expected.name);
        assertThat(person.age).isEqualTo(expected.age);
        assertThat(person.friends).isEqualTo(expected.friends);
    }

    @Test
    void testWithFriends() {
        Person friend = new Person(2L, "toad", 5, null);
        Person expected = new Person(1L, "neo", 2, Collections.singletonList(friend));
        byte[] bytes = serializer.serialize(null, expected);
        Person person = deserializer.deserialize(null, bytes);
        assertThat(person.id).isEqualTo(expected.id);
        assertThat(person.name).isEqualTo(expected.name);
        assertThat(person.age).isEqualTo(expected.age);
        assertThat(person.friends.size()).isEqualTo(1);
        assertThat(person.friends.get(0).name).isEqualTo("toad");
    }

    public static class Person {
        long id;
        String name;
        int age;
        List<Person> friends;

        public Person() {

        }

        public Person(long id, String name, int age, List<Person> friends) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.friends = friends;
        }

        public long getId() {
            return id;
        }

        public Person setId(long id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Person setName(String name) {
            this.name = name;
            return this;
        }

        public int getAge() {
            return age;
        }

        public Person setAge(int age) {
            this.age = age;
            return this;
        }

        public List<Person> getFriends() {
            return friends;
        }

        public Person setFriends(List<Person> friends) {
            this.friends = friends;
            return this;
        }
    }

}
