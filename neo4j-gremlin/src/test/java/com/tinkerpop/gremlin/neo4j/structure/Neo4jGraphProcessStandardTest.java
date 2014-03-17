package com.tinkerpop.gremlin.neo4j.structure;

import com.tinkerpop.gremlin.AbstractGraphProvider;
import com.tinkerpop.gremlin.process.ProcessStandardSuite;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.gremlin.structure.StructureStandardSuite;
import org.apache.commons.configuration.Configuration;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Executes the Standard Gremlin Structure Test Suite using Neo4j.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@RunWith(ProcessStandardSuite.class)
@StructureStandardSuite.GraphProviderClass(Neo4jGraphProcessStandardTest.class)
public class Neo4jGraphProcessStandardTest extends AbstractGraphProvider {

    @Override
    public Map<String, Object> getBaseConfiguration(final String graphName) {
        return new HashMap<String, Object>() {{
            put("gremlin.graph", Neo4jGraph.class.getName());
            put("gremlin.neo4j.directory", "/tmp/" + graphName);  // todo: better job with directory setup?  see tp2
        }};
    }

    @Override
    public void clear(final Graph g, final Configuration configuration) throws Exception {
        if (null != g) {
            if (g.getFeatures().graph().supportsTransactions())
                g.tx().rollback();
            g.close();
        }

        if (configuration.containsKey("gremlin.neo4j.directory")) {
            // this is a non-in-memory configuration so blow away the directory
            final File graphDirectory = new File(configuration.getString("gremlin.neo4j.directory"));
            deleteDirectory(graphDirectory);
        }
    }
}