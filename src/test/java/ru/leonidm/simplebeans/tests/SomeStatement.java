package ru.leonidm.simplebeans.tests;

public class SomeStatement {

    private boolean someParameter;

    public SomeStatement(boolean someParameter) {
        this.someParameter = someParameter;
    }

    public boolean isSomeParameter() {
        return someParameter;
    }

    public String executeUpdate(String sql) {
        return sql;
    }
}
