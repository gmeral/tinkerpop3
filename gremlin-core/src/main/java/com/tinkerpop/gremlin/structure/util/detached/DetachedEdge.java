package com.tinkerpop.gremlin.structure.util.detached;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.graph.GraphTraversal;
import com.tinkerpop.gremlin.process.graph.step.map.EdgeVertexStep;
import com.tinkerpop.gremlin.structure.Direction;
import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.util.StringFactory;
import com.tinkerpop.gremlin.util.StreamFactory;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DetachedEdge extends DetachedElement implements Edge {

    DetachedVertex outVertex;
    DetachedVertex inVertex;

    private DetachedEdge() {

    }

    public DetachedEdge(final Object id, final String label,
                        final Map<String, Object> properties,
                        final Map<String, Object> hiddenProperties,
                        final Pair<Object, String> outV,
                        final Pair<Object, String> inV) {
        super(id, label, properties, hiddenProperties);
        this.outVertex = new DetachedVertex(outV.getValue0(), outV.getValue1());
        this.inVertex = new DetachedVertex(inV.getValue0(), inV.getValue1());
    }

    private DetachedEdge(final Edge edge) {
        super(edge);
        this.outVertex = DetachedVertex.detach(edge.outV().next());
        this.inVertex = DetachedVertex.detach(edge.inV().next());
    }

    @Override
    public GraphTraversal<Edge, Vertex> toV(final Direction direction) {
        final GraphTraversal traversal = this.start();
        traversal.addStep(new DetachedEdgeVertexStep(traversal, direction));
        return traversal;
    }

    public String toString() {
        return StringFactory.edgeString(this);
    }

    public Edge attach(final Vertex hostVertex) {
        return StreamFactory.stream((Iterator<Edge>) hostVertex.outE(this.label))
                .filter(e -> e.id().equals(this.id))
                .findFirst().orElseThrow(() -> new IllegalStateException("The detached edge could not be be found incident to the provided vertex: " + this));
    }

    public Edge attach(final Graph graph) {
        return graph.e(this.id);
    }

    public static DetachedEdge detach(final Edge edge) {
        if (null == edge) throw Graph.Exceptions.argumentCanNotBeNull("edge");
        return new DetachedEdge(edge);
    }

    class DetachedEdgeVertexStep extends EdgeVertexStep {
        public DetachedEdgeVertexStep(final Traversal traversal, final Direction direction) {
            super(traversal, direction);
            this.setFunction(traverser -> {
                final List<Vertex> vertices = new ArrayList<>();
                if (direction.equals(Direction.OUT) || direction.equals(Direction.BOTH))
                    vertices.add(outVertex);
                if (direction.equals(Direction.IN) || direction.equals(Direction.BOTH))
                    vertices.add(inVertex);

                return vertices.iterator();
            });
        }
    }
}
