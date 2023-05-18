package ru.leonidm.simplebeanstests.cycledependency;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class FooComponent {

    public FooComponent(@NotNull BarComponent barComponent) {

    }
}
