package com.redhat.labs.rhoar.vertx.launcher;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.hawkular.VertxHawkularOptions;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Manifest;

/**
 * An implementation of {@link Launcher} which can handle detecting a Kubernetes/OpenShift cluster and configure
 * the Vert.x application appropriately
 */
public class CustomLauncher extends Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(CustomLauncher.class);
    private static final String TOKEN_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/token";

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("org.jboss.logging.provider", "slf4j");
        System.setProperty("jgroups.send_on_all_interfaces", "true");
        try {
            List<URL> resources = Collections.list(CustomLauncher.class.getClassLoader().getResources("META-INF/MANIFEST.MF"));
            Observable.fromIterable(resources)
                    .flatMapMaybe(r -> Maybe.just(new Manifest(r.openStream())))
                    .flatMapMaybe(maybe -> Maybe.just(maybe.getMainAttributes()))
                    .flatMap(a -> Observable.fromIterable(a.entrySet()))
                    .filter(e -> e.getKey().toString().startsWith("Main-Verticle"))
                    .take(1)
                    .map(e -> e.getValue().toString())
                    .map(v -> Arrays.asList("run", v))
                    .map(newArgs -> concatenateArguments(args, newArgs))
                    .map(finalArgs -> finalArgs.toArray(new String[finalArgs.size()]))
                    .doOnError(err -> LOG.fatal("Error while reading META-INF/MANIFEST.MF from classpath", err))
                    .subscribe(a -> new CustomLauncher().dispatch(a));
        } catch (IOException ioe) {
            LOG.fatal("Failed to load Main-Verticle value from META-INF/MANIFEST.MF");
        }
    }

    private static List<String> concatenateArguments(String[] args, List<String> newArgs) {
        List<String> fArgs = new ArrayList<>();
        fArgs.addAll(newArgs);
        if (args != null && args.length > 0) {
            fArgs.addAll(Arrays.asList(args));
        }
        return fArgs;
    }

    @Override
    protected String getCommandFromManifest() {
        return "run";
    }

    @Override
    protected String getDefaultCommand() {
        return "run";
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        // Enable clustering using Infinispan
        options.setClustered(true);
        ClusterManager clusterManager = new InfinispanClusterManager();
        options.setClusterManager(clusterManager);

        // Check to see if we are running inside of Kubernetes/OpenShift
        File tokenFile = new File(TOKEN_FILE_PATH);
        String namespace = System.getenv("KUBERNETES_NAMESPACE");
        if (tokenFile.canRead() && namespace!=null && !namespace.isEmpty()) {
            // Since we ARE running in Kubernetes/OpenShift, configure the Metrics service
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

            // Also, set up Infinispan clustering to work in Kubernetes/OpenShift
            System.setProperty("jgroups.tcp.address", "NON_LOOPBACK");
            System.setProperty("vertx.jgroups.config", "default-configs/default-jgroups-kubernetes.xml");
        }
    }
}
