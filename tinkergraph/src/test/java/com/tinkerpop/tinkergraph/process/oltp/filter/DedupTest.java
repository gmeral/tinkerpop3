package com.tinkerpop.tinkergraph.process.oltp.filter;

import com.tinkerpop.gremlin.process.oltp.ComplianceTest;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.tinkergraph.TinkerFactory;
import org.junit.Test;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DedupTest extends com.tinkerpop.gremlin.process.oltp.filter.DedupTest {

    final Graph g = TinkerFactory.createClassic();

    @Test
    public void testCompliance() {
        ComplianceTest.testCompliance(this.getClass());
    }

    @Test
    public void g_V_both_dedup_name() {
        super.g_V_both_dedup_name(g.V().both().dedup().value("name"));
    }

    @Test
    public void g_V_both_dedupXlangX_name() {
        super.g_V_both_dedupXlangX_name(g.V().both().dedup(v -> v.getProperty("lang").orElse(null)).value("name"));
    }
}