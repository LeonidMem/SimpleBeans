package ru.leonidm.simplebeans.tests;

import ru.leonidm.simplebeans.beans.Autowired;
import ru.leonidm.simplebeans.beans.Component;

@Component
public class SomeConnection {

    @Autowired
    private Object[] objects;
    private TestComponent testComponent;
    private FooBean fooBean;

    public SomeConnection(TestComponent testComponent) {
        this.testComponent = testComponent;
    }

    @Autowired
    private void setFooBean(FooBean fooBean) {
        this.fooBean = fooBean;
    }

    public Object[] getObjects() {
        return objects;
    }

    public TestComponent getTestComponent() {
        return testComponent;
    }

    public FooBean getFooBean() {
        return fooBean;
    }

    public SomeStatement createStatement() {
        return new SomeStatement(true);
    }
}
