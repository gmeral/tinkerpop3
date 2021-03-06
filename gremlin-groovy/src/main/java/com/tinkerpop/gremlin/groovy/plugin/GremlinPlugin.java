package com.tinkerpop.gremlin.groovy.plugin;

import java.util.Optional;
import java.util.Set;

/**
 * Those wanting to extend Gremlin can implement this interface to provide custom imports and extension
 * methods to the language itself.  Gremlin uses ServiceLoader to install plugins.  It is necessary for
 * projects to include a com.tinkerpop.gremlin.groovy.plugin.GremlinPlugin file in META-INF/services of their
 * packaged project which includes the full class names of the implementations of this interface to install.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public interface GremlinPlugin {
    /**
     * The name of the plugin.  This name should be unique as naming clashes will prevent proper plugin operations.
     */
    public String getName();

    /**
     * Implementors will typically execute imports of classes within their project that they want available in the
     * console or they may use meta programming to introduce new extensions to the Gremlin.
     */
    public void pluginTo(final PluginAcceptor pluginAcceptor);

    /**
     * Some plugins may require a restart of the plugin host for the classloader to pick up the features.  This is
     * typically true of plugins that rely on {@code Class.forName()} to dynamically instantiate classes from the
     * root classloader (e.g. JDBC drivers that instantiate via @{code DriverManager}).
     */
    public default boolean requireRestart() {
        return false;
    }

    /**
     * Dependency managers in a plugin host will derive dependency information from the pom.xml.  If the scope for
     * dependencies in the pom.xml are {@code provided} then such dependencies will not be pulled in.  These provided
     * dependencies should be specified here so that the plugin host can grab them too.
     */
    public default Optional<Set<Artifact>> additionalDependencies() {
        return Optional.empty();
    }

    /**
     * Allows a plugin to utilize features of the {@code :remote} and {@code :submit} commands of the Gremlin Console.
     * This method does not need to be implemented if the plugin is not meant for the Console for some reason or
     * if it does not intend to take advantage of those commands.
     */
    public default Optional<RemoteAcceptor> remoteAcceptor() {
        return Optional.empty();
    }
}
