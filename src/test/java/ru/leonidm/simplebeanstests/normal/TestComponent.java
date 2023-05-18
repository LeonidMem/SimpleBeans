package ru.leonidm.simplebeanstests.normal;

import ru.leonidm.simplebeans.beans.Component;

@Component
public class TestComponent {

    public TestComponent() {

    }

    @Override
    public String toString() {
        return getClass().getName();
    }
}
