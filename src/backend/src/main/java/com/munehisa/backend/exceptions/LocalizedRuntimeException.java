package com.munehisa.backend.exceptions;

import java.util.Arrays;

public abstract class LocalizedRuntimeException extends RuntimeException {

    private final String messageCode;
    private final transient Object[] messageArgs;

    protected LocalizedRuntimeException(String messageCode, Object... messageArgs) {
        this(messageCode, null, messageArgs);
    }

    protected LocalizedRuntimeException(String messageCode, Throwable cause, Object... messageArgs) {
        super(technicalMessage(messageCode, messageArgs), cause);
        this.messageCode = messageCode;
        this.messageArgs = stringify(messageArgs);
    }

    public final String getMessageCode() {
        return messageCode;
    }

    public final Object[] getMessageArgs() {
        return messageArgs.clone();
    }

    private static String technicalMessage(String code, Object[] args) {
        return args.length == 0 ? code : code + " " + Arrays.toString(stringify(args));
    }

    private static Object[] stringify(Object[] args) {
        return Arrays.stream(args).map(String::valueOf).toArray();
    }
}
