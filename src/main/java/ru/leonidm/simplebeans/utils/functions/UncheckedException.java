package ru.leonidm.simplebeans.utils.functions;

public class UncheckedException extends IllegalStateException {

    UncheckedException(Throwable cause) {
        super(cause);
    }
}
