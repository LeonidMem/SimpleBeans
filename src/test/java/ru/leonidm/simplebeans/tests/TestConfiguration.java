package ru.leonidm.simplebeans.tests;

import ru.leonidm.simplebeans.beans.Bean;
import ru.leonidm.simplebeans.beans.Configuration;

import java.util.Arrays;

@Configuration
public class TestConfiguration {

    @Bean
    public String someString(TestComponent testComponent) {
        return "string<" + testComponent.toString() + ">";
    }

    @Bean
    public FooBean fooBean() {
        return new FooBean() {
            @Override
            public String toString() {
                return "ru.leonidm.simplebeans.tests.FooBean@";
            }
        };
    }

    @Bean(id = "foo")
    public String fooBean(FooBean fooBean) {
        return "foo-string<" + fooBean + ">";
    }

    @Bean
    public Object[] someObjects() {
        return new Object[]{1, "df", 3f};
    }

    @Bean(id = "objects")
    public String objectsString(Object[] objects) {
        return "objects<" + Arrays.toString(objects) + ">";
    }
}
