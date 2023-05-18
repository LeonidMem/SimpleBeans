package ru.leonidm.simplebeanstests.unreachable;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.leonidm.simplebeans.applications.Application;
import ru.leonidm.simplebeans.applications.SimpleApplication;

@Application
public class UnreachableApplicationTest {

    @Test
    public void main() {
        Exception exception = assertThrowsExactly(IllegalStateException.class, () -> {
            SimpleApplication.run(UnreachableApplicationTest.class);
        });

        assertTrue(exception.getMessage().endsWith(" that is not reachable"));
    }
}
