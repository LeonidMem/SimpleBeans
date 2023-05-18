package ru.leonidm.simplebeanstests.deepcycledependency;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class FooBarComponent {

    public FooBarComponent(@NotNull FooComponent fooComponent) {
        System.out.println("[FooBarComponent:11] " + fooComponent);
    }
}
