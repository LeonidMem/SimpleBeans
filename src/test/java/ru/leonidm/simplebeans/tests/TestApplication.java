package ru.leonidm.simplebeans.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.leonidm.simplebeans.applications.Application;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.applications.SimpleApplication;
import ru.leonidm.simplebeans.proxy.AdvancedProxy;
import ru.leonidm.simplebeans.proxy.ProxyClass;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

@Application
public class TestApplication {

    private static ApplicationContext context;

    @Test
    public void main() {
        context = SimpleApplication.run(TestApplication.class);

        for (Class<?> clazz : List.of(TestConfiguration.class, TestComponent.class, FooBean.class)) {
            Object bean = context.getBean(clazz);

            assertFalse(Proxy.isProxyClass(clazz));
            assertFalse(AdvancedProxy.isProxyClass(clazz));

            if (clazz == FooBean.class) {
                assertTrue(Proxy.isProxyClass(bean.getClass()));
                assertInstanceOf(Proxy.class, bean);
            } else {
                assertFalse(Proxy.isProxyClass(bean.getClass()));
                assertInstanceOf(ProxyClass.class, bean);
            }

            assertTrue(AdvancedProxy.isProxyClass(bean.getClass()));
        }

        String string1 = context.getBean(String.class);
        assertEquals("string<ru.leonidm.simplebeans.tests.TestComponent>", string1);

        String string2 = context.getBean(String.class, "foo");
        assertEquals("foo-string<ru.leonidm.simplebeans.tests.FooBean@>", string2);

        SomeStatement someStatement = context.getBean(SomeConnection.class).createStatement();
        assertTrue(someStatement.isSomeParameter());

        String result = someStatement.executeUpdate("HELLO, world!");
        assertTrue(ConnectionAspect.isBeforeAdviceCalled());
        assertTrue(ConnectionAspect.isAfterAdviceCalled());
        assertEquals("hello, world!hello, world!", result);

        String string3 = context.getBean(String.class, "objects");
        assertEquals("objects<[1, df, 3.0]>", string3);
    }

    @NotNull
    public static ApplicationContext getContext() {
        return Objects.requireNonNull(context, "Not initialized yet");
    }
}
