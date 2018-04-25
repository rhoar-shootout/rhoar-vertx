package com.redhat.labs.rhoar.vertx.launcher;

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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * An implementation of {@link Launcher} which can handle detecting a Kubernetes/OpenShift cluster and configure
 * the Vert.x application appropriately
 */
public class CustomLauncher extends Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(CustomLauncher.class);
    public static final String TOKEN_FILE_PATH = "/run/secrets/kubernetes.io/serviceaccount/token";

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("org.jboss.logging.provider", "slf4j");
        System.setProperty("jgroups.send_on_all_interfaces", "true");
        try {
            Enumeration<URL> resources = CustomLauncher.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                Attributes mainAttr = manifest.getMainAttributes();
                mainAttr.keySet().forEach(key -> {
                    LOG.warn(key + ": " + mainAttr.getValue((Attributes.Name) key));
                });
                Optional<String> mainVerticle = mainAttr.keySet().stream()
                        .filter(k -> k.toString().startsWith("Main-Verticle"))
                        .map(k -> mainAttr.getValue((Attributes.Name)k))
                        .findFirst();
                String[] updatedArgs = (String[])Arrays.asList("run", mainVerticle.get(), args).toArray();
                if (mainVerticle.isPresent()) {
                    new CustomLauncher().dispatch(updatedArgs);
                } else {
                    LOG.fatal("Failed to load Main-Verticle value from META-INF/MANIFEST.MF A");
                }
            }
        } catch (IOException ioe) {
            LOG.fatal("Failed to load Main-Verticle value from META-INF/MANIFEST.MF B");
        }
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
