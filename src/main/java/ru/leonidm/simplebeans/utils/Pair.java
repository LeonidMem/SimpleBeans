package ru.leonidm.simplebeans.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Pair<A, B> {

    private A a;
    private B b;

    private Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<>(a, b);
    }

    public A getLeft() {
        return a;
    }

    public void setLeft(A a) {
        this.a = a;
    }

    public B getRight() {
        return b;
    }

    public void setRight(B b) {
        this.b = b;
    }

    @NotNull
    public ImmutablePair<A, B> asImmutable() {
        return new ImmutablePair<>(a, b);
    }

    @Override
    public String toString() {
        return "Pair{" + a + ", " + b + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(a, pair.a) && Objects.equals(b, pair.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
