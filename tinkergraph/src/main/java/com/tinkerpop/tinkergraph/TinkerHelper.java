package com.tinkerpop.tinkergraph;

import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.gremlin.structure.util.ElementHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class TinkerHelper {

    protected static String getNextId(final TinkerGraph graph) {
        return Stream.generate(() -> ((Long) (++graph.currentId)).toString()).filter(id -> !graph.vertices.containsKey(id) && !graph.edges.containsKey(id)).findFirst().get();
    }

    protected static Edge addEdge(final TinkerGraph graph, final TinkerVertex outVertex, final TinkerVertex inVertex, final String label, final Object... keyValues) {
        if (label == null)
            throw Edge.Exceptions.edgeLabelCanNotBeNull();
        ElementHelper.legalPropertyKeyValueArray(keyValues);

        Object idString = ElementHelper.getIdValue(keyValues).orElse(null);

        final Edge edge;
        if (null != idString) {
            if (graph.edges.containsKey(idString.toString()))
                throw Graph.Exceptions.edgeWithIdAlreadyExist(idString);
        } else {
            idString = TinkerHelper.getNextId(graph);
        }

        edge = new TinkerEdge(idString.toString(), outVertex, label, inVertex, graph);
        ElementHelper.attachProperties(edge, keyValues);
        graph.edges.put(edge.getId().toString(), edge);
        TinkerHelper.addOutEdge(outVertex, label, edge);
        TinkerHelper.addInEdge(inVertex, label, edge);
        return edge;

    }

    protected static void addOutEdge(final TinkerVertex vertex, final String label, final Edge edge) {
        Set<Edge> edges = vertex.outEdges.get(label);
        if (null == edges) {
            edges = new HashSet<>();
            vertex.outEdges.put(label, edges);
        }
        edges.add(edge);
    }

    protected static void addInEdge(final TinkerVertex vertex, final String label, final Edge edge) {
        Set<Edge> edges = vertex.inEdges.get(label);
        if (null == edges) {
            edges = new HashSet<>();
            vertex.inEdges.put(label, edges);
        }
        edges.add(edge);
    }
}