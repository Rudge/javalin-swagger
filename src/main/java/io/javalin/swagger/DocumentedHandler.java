package io.javalin.swagger;

import io.javalin.Context;
import io.javalin.Handler;

public class DocumentedHandler implements Handler {

    private Handler handler;
    private Route route;

    private DocumentedHandler(Route route, Handler handler) {
        this.route = route;
        this.handler = handler;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        handler.handle(ctx);
    }

    Route getRoute() {
        return route;
    }

    public static DocumentedHandler documented(Route route, Handler handler) {
        return new DocumentedHandler(route, handler);
    }
}
