package ru.leonidm.simplebeanstests.cycledependency;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class BarComponent {

    public BarComponent(@NotNull FooComponent fooComponent) {

    }
}
