package com.tinkerpop.gremlin.process.graph.strategy;

import com.tinkerpop.gremlin.process.TraversalStrategy;
import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.graph.marker.TraverserSource;
import com.tinkerpop.gremlin.process.util.TraversalHelper;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TraverserSourceStrategy implements TraversalStrategy.FinalTraversalStrategy {

    public void apply(final Traversal traversal) {
        final boolean trackPaths = TraversalHelper.trackPaths(traversal);
        traversal.getSteps().forEach(step -> {
            if (step instanceof TraverserSource)
                ((TraverserSource) step).generateTraverserIterator(trackPaths);
        });
    }

    public static <S, E> void doPathTracking(final Traversal<S, E> traversal) {
        traversal.getSteps().forEach(step -> {
            if (step instanceof TraverserSource)
                ((TraverserSource) step).generateTraverserIterator(true);
        });
    }
}
