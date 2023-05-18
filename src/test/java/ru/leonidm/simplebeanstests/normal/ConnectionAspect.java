package ru.leonidm.simplebeanstests.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ru.leonidm.simplebeans.proxy.AdvancedProxy;
import ru.leonidm.simplebeans.proxy.ProxyClass;
import ru.leonidm.simplebeans.proxy.aspects.After;
import ru.leonidm.simplebeans.proxy.aspects.Aspect;
import ru.leonidm.simplebeans.proxy.aspects.Before;
import ru.leonidm.simplebeans.proxy.aspects.arguments.Args;
import ru.leonidm.simplebeans.proxy.aspects.arguments.Origin;
import ru.leonidm.simplebeans.proxy.aspects.arguments.Result;
import ru.leonidm.simplebeans.proxy.aspects.arguments.This;

import java.lang.reflect.Method;

@Aspect
public class ConnectionAspect {

    private boolean beforeAdviceCalled = false;
    private boolean afterAdviceCalled = false;

    @Before("**.SomeStatement.execute*")
    public void advice(@Origin Method method, @Args Object [] args) {
        beforeAdviceCalled = true;

        Method method1;
        try {
            method1 = SomeStatement.class.getMethod("executeUpdate", String.class);
        } catch (Exception e) {
            fail();
            return;
        }

        assertEquals(method1, method);
        assertEquals(1, args.length);
        assertEquals("HELLO, world!", args[0]);

        args[0] = ((String) args[0]).repeat(2);
    }

    @After("**.SomeStatement.execute*")
    public String advice(@This Object instance, @Result Object result) {
        afterAdviceCalled = true;

        assertTrue(AdvancedProxy.isProxyClass(instance.getClass()));
        assertInstanceOf(ProxyClass.class, instance);

        assertEquals("HELLO, world!HELLO, world!", result);

        return ((String) result).toLowerCase();
    }

    public boolean isBeforeAdviceCalled() {
        return beforeAdviceCalled;
    }

    public boolean isAfterAdviceCalled() {
        return afterAdviceCalled;
    }
}
