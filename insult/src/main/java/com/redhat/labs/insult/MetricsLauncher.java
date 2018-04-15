package com.redhat.labs.insult;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.hawkular.VertxHawkularOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class MetricsLauncher extends Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsLauncher.class);
    public static final String TOKEN_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/token";



    @Override
    public void beforeStartingVertx(VertxOptions options) {
        File tokenFile = new File(TOKEN_FILE_PATH);
        String namespace = System.getenv("KUBERNETES_NAMESPACE");
        if (tokenFile.canRead() && namespace!=null && !namespace.isEmpty()) {
            try {
                String token = new String(Files.readAllBytes(tokenFile.toPath()));
                options.setMetricsOptions(
                    new VertxHawkularOptions()
                        .setEnabled(true)
                        .setTenant(namespace)
                        .setHttpHeaders(new JsonObject()
                            .put("Authorization", "Bearer "+token)
                        )
                );
            } catch (IOException ioe) {
                LOG.warn("Unable to configure metrics by retrieving token", ioe);
            }
        }
    }
}
