package com.tinkerpop.blueprints.tinkergraph;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Property;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.ElementHelper;
import com.tinkerpop.blueprints.util.ExceptionFactory;
import com.tinkerpop.blueprints.util.StringFactory;

import java.io.Serializable;
import java.util.Set;


/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class TinkerEdge extends TinkerElement implements Edge, Serializable {

    private final String label;
    private final Vertex inVertex;
    private final Vertex outVertex;

    protected TinkerEdge(final String id, final Vertex outVertex, final Vertex inVertex, final String label, final TinkerGraph graph) {
        super(id, graph);
        this.label = label;
        this.outVertex = outVertex;
        this.inVertex = inVertex;
        this.graph.edgeIndex.autoUpdate(StringFactory.LABEL, this.label, null, this);
    }

    public <T> Property<T, Edge> getProperty(final String key) {
        final Property<T, Edge> t = this.properties.get(key);
        return (null == t) ? Property.<T, Edge>empty() : t;
    }

    public <T> Property<T, Edge> setProperty(final String key, final T value) {
        ElementHelper.validateProperty(this, key, value);
        final Property<T, Edge> oldValue = this.properties.put(key, new TinkerProperty<>(key, value, this));
        this.graph.edgeIndex.autoUpdate(key, value, oldValue, this);
        return oldValue;
    }

    public <T> Property<T, Edge> removeProperty(final String key) {
        final Property<T, Edge> oldValue = this.properties.remove(key);
        this.graph.edgeIndex.autoRemove(key, oldValue, this);
        return oldValue;
    }

    public String getLabel() {
        return this.label;
    }

    public Vertex getVertex(final Direction direction) throws IllegalArgumentException {
        if (direction.equals(Direction.IN))
            return this.inVertex;
        else if (direction.equals(Direction.OUT))
            return this.outVertex;
        else
            throw ExceptionFactory.bothIsNotSupported();
    }

    public void remove() {
        final TinkerVertex outVertex = (TinkerVertex) this.getVertex(Direction.OUT);
        final TinkerVertex inVertex = (TinkerVertex) this.getVertex(Direction.IN);
        if (null != outVertex && null != outVertex.outEdges) {
            final Set<Edge> edges = outVertex.outEdges.get(this.getLabel());
            if (null != edges)
                edges.remove(this);
        }
        if (null != inVertex && null != inVertex.inEdges) {
            final Set<Edge> edges = inVertex.inEdges.get(this.getLabel());
            if (null != edges)
                edges.remove(this);
        }

        this.graph.edgeIndex.removeElement(this);
        this.graph.edges.remove(this.getId());
    }

    public String toString() {
        return StringFactory.edgeString(this);
    }
}