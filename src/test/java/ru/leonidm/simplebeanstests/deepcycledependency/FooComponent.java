package ru.leonidm.simplebeanstests.deepcycledependency;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class FooComponent {

    public FooComponent(@NotNull BarComponent barComponent) {
        System.out.println("[FooComponent:10] " + barComponent);
    }
}
