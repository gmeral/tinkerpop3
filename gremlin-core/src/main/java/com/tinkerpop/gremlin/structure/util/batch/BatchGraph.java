package com.tinkerpop.gremlin.structure.util.batch;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.Traverser;
import com.tinkerpop.gremlin.process.computer.GraphComputer;
import com.tinkerpop.gremlin.process.graph.GraphTraversal;
import com.tinkerpop.gremlin.structure.Direction;
import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Element;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.gremlin.structure.Property;
import com.tinkerpop.gremlin.structure.Transaction;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.util.ElementHelper;
import com.tinkerpop.gremlin.structure.util.batch.cache.VertexCache;
import com.tinkerpop.gremlin.util.function.SConsumer;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * BatchGraph is a wrapper that enables batch loading of a large number of edges and vertices by chunking the entire
 * load into smaller batches and maintaining a memory-efficient vertex cache so that the entire transactional state can
 * be flushed after each chunk is loaded.
 * <br />
 * BatchGraph is ONLY meant for loading data and does not support any retrieval or removal operations.
 * That is, BatchGraph only supports the following methods:
 * - {@link #addVertex(Object...)} for adding vertices
 * - {@link Vertex#addEdge(String, com.tinkerpop.gremlin.structure.Vertex, Object...)} for adding edges
 * - {@link #v(Object)} to be used when adding edges
 * - Property getter, setter and removal methods for vertices and edges.
 * <br />
 * An important limitation of BatchGraph is that edge properties can only be set immediately after the edge has been added.
 * If other vertices or edges have been created in the meantime, setting, getting or removing properties will throw
 * exceptions. This is done to avoid caching of edges which would require a great amount of memory.
 * <br />
 * BatchGraph can also automatically set the provided element ids as properties on the respective element. Use
 * {@link Builder#vertexIdKey(String)} and {@link Builder#edgeIdKey(String)} to set the keys
 * for the vertex and edge properties respectively. This allows to make the loaded baseGraph compatible for later
 * operation with {@link com.tinkerpop.gremlin.structure.strategy.IdGraphStrategy}.
 *
 * @author Matthias Broecheler (http://www.matthiasb.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class BatchGraph<T extends Graph> implements Graph {
    /**
     * Default buffer size
     */
    public static final long DEFAULT_BUFFER_SIZE = 10000;

    private final T baseGraph;

    private final String vertexIdKey;
    private final String edgeIdKey;
    private final boolean incrementalLoading;
    private final boolean baseSupportsSuppliedVertexId;
    private final boolean baseSupportsSuppliedEdgeId;
    private final boolean baseSupportsTransactions;
    private final BiConsumer<Element, Object[]> existingVertexStrategy;
    private final BiConsumer<Element, Object[]> existingEdgeStrategy;

    private final VertexCache cache;

    private final long bufferSize;
    private long remainingBufferSize;

    private BatchEdge currentEdge = null;
    private Edge currentEdgeCached = null;

    private Object previousOutVertexId = null;

    private final BatchFeatures batchFeatures;

    private final Transaction batchTransaction;

    /**
     * Constructs a BatchGraph wrapping the provided baseGraph, using the specified buffer size and expecting vertex
     * ids of the specified IdType. Supplying vertex ids which do not match this type will throw exceptions.
     *
     * @param graph      Graph to be wrapped
     * @param type       Type of vertex id expected. This information is used to apply the vertex cache
     *                   memory footprint.
     * @param bufferSize Defines the number of vertices and edges loaded before starting a new transaction. The
     *                   larger this value, the more memory is required but the faster the loading process.
     */
    private BatchGraph(final T graph, final VertexIdType type, final long bufferSize, final String vertexIdKey,
                       final String edgeIdKey, final boolean incrementalLoading,
                       final BiConsumer<Element, Object[]> existingVertexStrategy,
                       final BiConsumer<Element, Object[]> existingEdgeStrategy) {
        this.baseGraph = graph;
        this.batchTransaction = new BatchTransaction();
        this.batchFeatures = new BatchFeatures(graph.getFeatures());
        this.bufferSize = bufferSize;
        this.cache = type.getVertexCache();
        this.remainingBufferSize = this.bufferSize;
        this.vertexIdKey = vertexIdKey;
        this.edgeIdKey = edgeIdKey;
        this.incrementalLoading = incrementalLoading;
        this.baseSupportsSuppliedEdgeId = this.baseGraph.getFeatures().edge().supportsUserSuppliedIds();
        this.baseSupportsSuppliedVertexId = this.baseGraph.getFeatures().vertex().supportsUserSuppliedIds();
        this.baseSupportsTransactions = this.baseGraph.getFeatures().graph().supportsTransactions();
        this.existingEdgeStrategy = existingEdgeStrategy;
        this.existingVertexStrategy = existingVertexStrategy;
    }

    private void nextElement() {
        currentEdge = null;
        currentEdgeCached = null;
        if (remainingBufferSize <= 0) {
            if (this.baseSupportsTransactions) baseGraph.tx().commit();
            cache.newTransaction();
            remainingBufferSize = bufferSize;
        }
        remainingBufferSize--;
    }

    private Vertex retrieveFromCache(final Object externalID) {
        final Object internal = cache.getEntry(externalID);
        if (internal instanceof Vertex) {
            return (Vertex) internal;
        } else if (internal != null) { //its an internal id
            final Vertex v = baseGraph.v(internal);
            cache.set(v, externalID);
            return v;
        } else return null;
    }

    private Vertex getCachedVertex(final Object externalID) {
        final Vertex v = retrieveFromCache(externalID);
        if (v == null) throw new IllegalArgumentException("Vertex for given ID cannot be found: " + externalID);
        return v;
    }

    @Override
    public Vertex addVertex(final Object... keyValues) {
        final Object id = ElementHelper.getIdValue(keyValues).orElseThrow(() -> new IllegalArgumentException("Vertex id value cannot be null"));
        if (!incrementalLoading && retrieveFromCache(id) != null)
            throw new IllegalArgumentException("Vertex id already exists");
        nextElement();

        // if the vertexIdKey is not the Element.ID then append it as a name/value pair.  this will overwrite what
        // is present in that field already
        final Object[] keysVals = Element.ID.equals(vertexIdKey) ? keyValues : ElementHelper.upsert(keyValues, vertexIdKey, id);

        // if the graph doesn't support vertex ids or the vertex id is not the Element.ID then remove that key
        // value pair as it will foul up insertion (i.e. an exception for graphs that don't support it and the
        // id will become the value of the vertex id which might not be expected.
        final Optional<Object[]> kvs = this.baseSupportsSuppliedVertexId && Element.ID.equals(vertexIdKey) ?
                Optional.ofNullable(keyValues) : ElementHelper.remove(Element.ID, keysVals);

        Vertex currentVertex;
        if (!incrementalLoading)
            currentVertex = kvs.isPresent() ? baseGraph.addVertex(kvs.get()) : baseGraph.addVertex();
        else {
            final Traversal<Vertex, Vertex> traversal = baseGraph.V().has(vertexIdKey, id);
            if (traversal.hasNext()) {
                final Vertex v = traversal.next();
                if (traversal.hasNext())
                    throw new IllegalStateException(String.format("There is more than one vertex identified by %s=%s", vertexIdKey, id));

                // let the caller decide how to handle conflict
                kvs.ifPresent(keyvals -> existingVertexStrategy.accept(v, keyvals));
                currentVertex = v;
            } else
                currentVertex = kvs.isPresent() ? baseGraph.addVertex(kvs.get()) : baseGraph.addVertex();
        }

        cache.set(currentVertex, id);

        return new BatchVertex(id);
    }

    /**
     * {@inheritDoc}
     * <br/>
     * If the input data are sorted, then out vertex will be repeated for several edges in a row.
     * In this case, bypass cache and instead immediately return a new vertex using the known id.
     * This gives a modest performance boost, especially when the cache is large or there are
     * on average many edges per vertex.
     */
    @Override
    public Vertex v(final Object id) {
        if ((previousOutVertexId != null) && (previousOutVertexId.equals(id)))
            return new BatchVertex(previousOutVertexId);
        else {
            Vertex v = retrieveFromCache(id);
            if (null == v) {
                if (!incrementalLoading) return null;
                else {
                    final Iterator<Vertex> iter = baseGraph.V().has(vertexIdKey, id);
                    if (!iter.hasNext()) return null;
                    v = iter.next();
                    if (iter.hasNext())
                        throw new IllegalStateException("There are multiple vertices with the provided id in the database: " + id);
                    cache.set(v, id);
                }
            }
            return new BatchVertex(id);
        }
    }

    @Override
    public Edge e(final Object id) {
        throw retrievalNotSupported();
    }

    @Override
    public GraphTraversal<Vertex, Vertex> V() {
        throw retrievalNotSupported();
    }

    @Override
    public GraphTraversal<Edge, Edge> E() {
        throw retrievalNotSupported();
    }

    @Override
    public <T extends Traversal> T traversal(final Class<T> traversalClass) {
        throw retrievalNotSupported();
    }

    @Override
    public <C extends GraphComputer> C compute(final Class<C>... graphComputerClass) {
        throw Exceptions.graphComputerNotSupported();
    }

    @Override
    public Transaction tx() {
        return this.batchTransaction;
    }

    @Override
    public <M extends Variables> M variables() {
        throw Exceptions.variablesNotSupported();
    }

    @Override
    public Features getFeatures() {
        return this.batchFeatures;
    }

    @Override
    public void close() throws Exception {
        baseGraph.close();

        // call reset after the close in case the close behavior fails
        reset();
    }

    private void reset() {
        currentEdge = null;
        currentEdgeCached = null;
        remainingBufferSize = 0;
    }

    public static <T extends Graph> Builder create(final T g) {
        return new Builder<>(g);
    }

    private class BatchTransaction implements Transaction {
        private final boolean supportsTx;

        public BatchTransaction() {
            supportsTx = baseGraph.getFeatures().graph().supportsTransactions();
        }

        @Override
        public Transaction onClose(final Consumer<Transaction> consumer) {
            throw new UnsupportedOperationException("Transaction behavior cannot be altered in batch mode - set the behavior on the base graph");
        }

        @Override
        public Transaction onReadWrite(final Consumer<Transaction> consumer) {
            throw new UnsupportedOperationException("Transaction behavior cannot be altered in batch mode - set the behavior on the base graph");
        }

        @Override
        public void close() {
            if (supportsTx) baseGraph.tx().close();

            // call reset after the close in case the close behavior fails
            reset();
        }

        @Override
        public void readWrite() {
            if (supportsTx) baseGraph.tx().readWrite();
        }

        @Override
        public boolean isOpen() {
            return !supportsTx || baseGraph.tx().isOpen();
        }

        @Override
        public <G extends Graph> G create() {
            throw new UnsupportedOperationException("Cannot start threaded transaction during batch loading");
        }

        @Override
        public <R> Workload<R> submit(final Function<Graph, R> work) {
            throw new UnsupportedOperationException("Cannot submit a workload during batch loading");
        }

        @Override
        public void rollback() {
            throw new UnsupportedOperationException("Cannot issue a rollback during batch loading");
        }

        @Override
        public void commit() {
            if (supportsTx) baseGraph.tx().commit();

            // call reset after the close in case the close behavior fails
            reset();
        }

        @Override
        public void open() {
            if (supportsTx) baseGraph.tx().open();
        }
    }

    private class BatchVertex implements Vertex {

        private final Object externalID;

        BatchVertex(final Object id) {
            if (id == null) throw new IllegalArgumentException("External id may not be null");
            externalID = id;
        }

        @Override
        public Edge addEdge(final String label, final Vertex inVertex, final Object... keyValues) {
            if (!BatchVertex.class.isInstance(inVertex))
                throw new IllegalArgumentException("Given element was not created in this baseGraph");
            nextElement();

            final Vertex ov = getCachedVertex(externalID);
            final Vertex iv = getCachedVertex(inVertex.id());

            previousOutVertexId = externalID;  //keep track of the previous out vertex id

            if (!incrementalLoading) {
                final Optional<Object[]> kvs = baseSupportsSuppliedEdgeId && Element.ID.equals(edgeIdKey) ?
                        Optional.ofNullable(keyValues) : ElementHelper.remove(Element.ID, keyValues);
                currentEdgeCached = kvs.isPresent() ? ov.addEdge(label, iv, kvs.get()) : ov.addEdge(label, iv);
            } else {
                final Optional<Object> id = ElementHelper.getIdValue(keyValues);
                // if the edgeIdKey is not the Element.ID then append it as a name/value pair.  this will overwrite what
                // is present in that field already
                final Object[] keysVals = id.isPresent() && Element.ID.equals(edgeIdKey) ? keyValues :
                        id.isPresent() ? ElementHelper.upsert(keyValues, edgeIdKey, id.get()) : keyValues;

                // if the graph doesn't support edge ids or the edge id is not the Element.ID then remove that key
                // value pair as it will foul up insertion (i.e. an exception for graphs that don't support it and the
                // id will become the value of the edge id which might not be expected.
                final Optional<Object[]> kvs = baseSupportsSuppliedEdgeId && Element.ID.equals(edgeIdKey) ?
                        Optional.ofNullable(keyValues) : ElementHelper.remove(Element.ID, keysVals);

                if (id.isPresent()) {
                    final Traversal<Edge, Edge> traversal = baseGraph.E().has(edgeIdKey, id.get());
                    if (traversal.hasNext()) {
                        final Edge e = traversal.next();
                        // let the user decide how to handle conflict
                        kvs.ifPresent(keyvals -> existingEdgeStrategy.accept(e, keyvals));
                        currentEdgeCached = e;
                    } else
                        currentEdgeCached = kvs.isPresent() ? ov.addEdge(label, iv, kvs.get()) : ov.addEdge(label, iv);
                } else {
                    currentEdgeCached = kvs.isPresent() ? ov.addEdge(label, iv, kvs.get()) : ov.addEdge(label, iv);
                }
            }

            currentEdge = new BatchEdge();

            return currentEdge;
        }

        @Override
        public Object id() {
            return this.externalID;
        }

        @Override
        public String label() {
            return getCachedVertex(externalID).label();
        }

        @Override
        public void remove() {
            throw removalNotSupported();
        }

        @Override
        public Set<String> keys() {
            return getCachedVertex(externalID).keys();
        }

        @Override
        public Map<String, Property> properties() {
            return getCachedVertex(externalID).properties();
        }

        @Override
        public Map<String, Property> hiddens() {
            return getCachedVertex(externalID).hiddens();
        }

        @Override
        public <V> Property<V> property(final String key) {
            return getCachedVertex(externalID).property(key);
        }

        @Override
        public <V> Property<V> property(final String key, final V value) {
            return getCachedVertex(externalID).property(key, value);
        }

        @Override
        public void properties(final Object... keyValues) {
            getCachedVertex(externalID).properties(keyValues);
        }

        @Override
        public <V> V value(final String key) throws NoSuchElementException {
            return getCachedVertex(externalID).value(key);
        }

        @Override
        public GraphTraversal<Vertex, Vertex> with(final Object... variableValues) {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Vertex> sideEffect(final SConsumer<Traverser<Vertex>> consumer) {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Edge> toE(final Direction direction, final int branchFactor, final String... labels) {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Vertex> to(final Direction direction, final int branchFactor, final String... labels) {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Vertex> start() {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Vertex> as(final String as) {
            throw retrievalNotSupported();
        }

        @Override
        public GraphTraversal<Vertex, Vertex> identity() {
            throw retrievalNotSupported();
        }
    }

    private class BatchEdge implements Edge {

        @Override
        public GraphTraversal<Edge, Vertex> toV(final Direction direction) {
            return getWrappedEdge().toV(direction);
        }

        @Override
        public Object id() {
            return getWrappedEdge().label();
        }

        @Override
        public String label() {
            return getWrappedEdge().label();
        }

        @Override
        public void remove() {
            throw removalNotSupported();
        }

        @Override
        public Map<String, Property> properties() {
            return getWrappedEdge().properties();
        }

        @Override
        public Map<String, Property> hiddens() {
            return getWrappedEdge().hiddens();
        }

        @Override
        public <V> Property<V> property(final String key) {
            return getWrappedEdge().property(key);
        }

        @Override
        public <V> Property<V> property(final String key, final V value) {
            return getWrappedEdge().property(key, value);
        }

        @Override
        public Set<String> keys() {
            return getWrappedEdge().keys();
        }

        @Override
        public void properties(final Object... keyValues) {
            getWrappedEdge().properties(keyValues);
        }

        @Override
        public <V> V value(final String key) throws NoSuchElementException {
            return getWrappedEdge().value(key);
        }

        private Edge getWrappedEdge() {
            if (this != currentEdge) {
                throw new UnsupportedOperationException("This edge is no longer in scope");
            }
            return currentEdgeCached;
        }
    }

    private static UnsupportedOperationException retrievalNotSupported() {
        return new UnsupportedOperationException("Retrieval operations are not supported during batch loading");
    }

    private static UnsupportedOperationException removalNotSupported() {
        return new UnsupportedOperationException("Removal operations are not supported during batch loading");
    }

    public static class Builder<T extends Graph> {
        private final T graphToLoad;
        private boolean incrementalLoading = false;
        private String vertexIdKey = Element.ID;
        private String edgeIdKey = Element.ID;
        private long bufferSize = DEFAULT_BUFFER_SIZE;
        private VertexIdType vertexIdType = VertexIdType.OBJECT;
        private BiConsumer<Element, Object[]> existingVertexStrategy = Exists.IGNORE;
        private BiConsumer<Element, Object[]> existingEdgeStrategy = Exists.IGNORE;

        private Builder(final T g) {
            if (null == g) throw new IllegalArgumentException("Graph may not be null");
            if (g instanceof BatchGraph)
                throw new IllegalArgumentException("BatchGraph cannot wrap another BatchGraph instance");
            this.graphToLoad = g;
        }

        /**
         * Sets the key to be used when setting the vertex id as a property on the respective vertex. If this
         * value is not set it defaults to {@link Element#ID}.
         *
         * @param key Key to be used.
         */
        public Builder vertexIdKey(final String key) {
            if (null == key) throw new IllegalArgumentException("Key cannot be null");
            this.vertexIdKey = key;
            return this;
        }

        /**
         * Sets the key to be used when setting the edge id as a property on the respective edge.
         * If the key is null, then no property will be set.
         *
         * @param key Key to be used.
         */
        public Builder edgeIdKey(final String key) {
            if (null == key) throw new IllegalArgumentException("Optional value for key cannot be null");
            this.edgeIdKey = key;
            return this;
        }

        public Builder bufferSize(long bufferSize) {
            if (bufferSize <= 0) throw new IllegalArgumentException("BufferSize must be positive");
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Sets the type of the id used for the vertex which in turn determines the cache type that is used.
         */
        public Builder vertexIdType(final VertexIdType type) {
            if (null == type) throw new IllegalArgumentException("Type may not be null");
            this.vertexIdType = type;
            return this;
        }

        /**
         * Sets whether the graph loaded through this instance of {@link BatchGraph} is loaded from scratch
         * (i.e. the wrapped graph is initially empty) or whether graph is loaded incrementally into an
         * existing graph.
         * <p>
         * In the former case, BatchGraph does not need to check for the existence of vertices with the wrapped
         * graph but only needs to consult its own cache which can be significantly faster. In the latter case,
         * the cache is checked first but an additional check against the wrapped graph may be necessary if
         * the vertex does not exist.
         * <p>
         * By default, BatchGraph assumes that the data is loaded from scratch.
         */
        public Builder incrementalLoading(final boolean incrementalLoading) {
            this.incrementalLoading = incrementalLoading;
            return this;
        }

        /**
         * Sets whether the graph loaded through this instance of {@link BatchGraph} is loaded from scratch
         * (i.e. the wrapped graph is initially empty) or whether graph is loaded incrementally into an
         * existing graph.
         * <p>
         * In the former case, BatchGraph does not need to check for the existence of vertices with the wrapped
         * graph but only needs to consult its own cache which can be significantly faster. In the latter case,
         * the cache is checked first but an additional check against the wrapped graph may be necessary if
         * the vertex does not exist.
         * <p>
         * By default, BatchGraph assumes that the data is loaded from scratch.
         */
        public Builder incrementalLoading(final boolean incrementalLoading,
                                          final BiConsumer<Element, Object[]> existingVertexStrategy,
                                          final BiConsumer<Element, Object[]> existingEdgeStrategy) {
            this.incrementalLoading = incrementalLoading;
            this.existingVertexStrategy = existingVertexStrategy;
            this.existingEdgeStrategy = existingEdgeStrategy;
            return this;
        }

        public BatchGraph<T> build() {
            return new BatchGraph<>(graphToLoad, vertexIdType, bufferSize, vertexIdKey, edgeIdKey,
                    incrementalLoading, this.existingVertexStrategy, this.existingEdgeStrategy);
        }
    }
}
