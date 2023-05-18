package ru.leonidm.simplebeanstests.cycledependency;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.leonidm.simplebeans.applications.Application;
import ru.leonidm.simplebeans.applications.SimpleApplication;

@Application
public class CycleDependencyApplicationTest {

    @Test
    public void main() {
        Exception exception = assertThrowsExactly(IllegalStateException.class, () -> {
            SimpleApplication.run(CycleDependencyApplicationTest.class);
        });

        assertTrue(exception.getMessage().startsWith("Cycle dependency: "));
    }
}
