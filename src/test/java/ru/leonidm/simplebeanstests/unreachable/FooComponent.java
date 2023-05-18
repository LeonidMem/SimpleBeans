package ru.leonidm.simplebeanstests.unreachable;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;
import ru.leonidm.simplebeanstests.deepcycledependency.BarComponent;

@Component
public class FooComponent {

    public FooComponent(@NotNull BarComponent barComponent) {

    }
}
