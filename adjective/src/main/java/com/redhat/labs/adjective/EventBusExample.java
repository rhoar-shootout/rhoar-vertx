package com.redhat.labs.adjective;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EventBusExample extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(EventBusExample.class);

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new EventBusExample());
    }

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer("some.arbitrary.address", msg -> {
            LOG.warn(msg.body());
            msg.reply("PONG");
        });

        vertx.setPeriodic(2000, t -> {
            vertx.eventBus().send("some.arbitrary.address", "PING", reply -> {
                LOG.warn(reply.result().body());
            });
        });
    }
}
