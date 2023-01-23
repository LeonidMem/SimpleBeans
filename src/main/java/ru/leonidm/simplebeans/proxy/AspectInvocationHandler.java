package ru.leonidm.simplebeans.proxy;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.proxy.aspects.PointCutType;
import ru.leonidm.simplebeans.proxy.aspects.WrappedPointCut;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public final class AspectInvocationHandler implements InvocationHandler {

    private final Object realObject;
    private final ApplicationContext context;

    public AspectInvocationHandler(@NotNull ApplicationContext context, @NotNull Object realObject) {
        this.realObject = realObject;
        this.context = context;
    }

    @NotNull
    public Object getRealObject() {
        return realObject;
    }

    @NotNull
    public ApplicationContext getContext() {
        return context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        context.getPointCuts(method, PointCutType.BEFORE).forEach(wrappedPointCut -> wrappedPointCut.run(method, args, null));
        Object result = method.invoke(realObject, args);
        for (WrappedPointCut wrappedPointCut : context.getPointCuts(method, PointCutType.AFTER)) {
            Object pointCutResult = wrappedPointCut.run(method, args, result);
            if (!wrappedPointCut.isVoid()) {
                result = pointCutResult;
            }
        }

        return result;
    }
}
