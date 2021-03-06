package com.tinkerpop.gremlin.console.plugin;

import com.tinkerpop.gremlin.driver.Cluster;
import com.tinkerpop.gremlin.driver.exception.ConnectionException;
import com.tinkerpop.gremlin.driver.message.RequestMessage;
import com.tinkerpop.gremlin.driver.ser.SerTokens;
import com.tinkerpop.gremlin.groovy.plugin.AbstractGremlinPlugin;
import com.tinkerpop.gremlin.groovy.plugin.Artifact;
import com.tinkerpop.gremlin.groovy.plugin.PluginAcceptor;
import com.tinkerpop.gremlin.groovy.plugin.RemoteAcceptor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class DriverGremlinPlugin extends AbstractGremlinPlugin {

    private static final Set<String> IMPORTS = new HashSet<String>() {{
        add(IMPORT + Cluster.class.getPackage().getName() + DOT_STAR);
        add(IMPORT + ConnectionException.class.getPackage().getName() + DOT_STAR);
        add(IMPORT + RequestMessage.class.getPackage().getName() + DOT_STAR);
        add(IMPORT + SerTokens.class.getPackage().getName() + DOT_STAR);
    }};

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public void pluginTo(final PluginAcceptor pluginAcceptor) {
        super.pluginTo(pluginAcceptor);
        pluginAcceptor.addImports(IMPORTS);
    }

    @Override
    public Optional<RemoteAcceptor> remoteAcceptor() {
        return Optional.of(new DriverRemoteAcceptor(shell));
    }
}
