package com.tinkerpop.gremlin.groovy.loaders

import com.tinkerpop.gremlin.groovy.GremlinLoader
import com.tinkerpop.gremlin.process.Traverser
import com.tinkerpop.gremlin.structure.Graph
import com.tinkerpop.gremlin.structure.Vertex

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class GraphLoader {

    public static void load() {

        Graph.metaClass.propertyMissing = { final String name ->
            if (GremlinLoader.isStep(name)) {
                return delegate."$name"();
            } else {
                throw new MissingPropertyException(name, delegate.getClass());
            }
        }

        Graph.metaClass.methodMissing = { final String name, final def args ->
            if (GremlinLoader.isStep(name)) {
                return delegate."$name"(*args);
            } else {
                throw new MissingMethodException(name, delegate.getClass());
            }
        }

        Vertex.metaClass.propertyMissing = { final String name ->
            if (GremlinLoader.isStep(name)) {
                return delegate."$name"();
            } else {
                throw new MissingPropertyException(name, delegate.getClass());
            }
        }

        // This allows the use to not specify get() to get at the object contained in the Traverser
        Traverser.metaClass.methodMissing = { final String name, final def args ->
            return ((Traverser) delegate).get()."$name"(*args);
        }

        Traverser.metaClass.propertyMissing = { final String name ->
            return ((Traverser) delegate).get()["$name"];
        }
    }
}
