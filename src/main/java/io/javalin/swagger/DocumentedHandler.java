package io.javalin.swagger;

import io.javalin.Context;
import io.javalin.Handler;
import io.swagger.v3.oas.models.Operation;

public class DocumentedHandler implements Handler {

    private Handler handler;
    private Operation operation;

    private DocumentedHandler(Operation operation, Handler handler) {
        this.operation = operation;
        this.handler = handler;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        handler.handle(ctx);
    }

    Operation getOperation() {
        return operation;
    }

    public static DocumentedHandler documented(Operation operation, Handler handler) {
        return new DocumentedHandler(operation, handler);
    }
}
