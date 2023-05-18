package ru.leonidm.simplebeanstests.deepcycledependency;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class BarComponent {

    public BarComponent(@NotNull FooBarComponent fooBarComponent) {
        System.out.println("[BarComponent:10] " + fooBarComponent);
    }
}
