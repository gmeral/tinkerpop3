package com.tinkerpop.gremlin.driver.ser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinkerpop.gremlin.structure.io.graphson.GraphSONObjectMapper;

import java.nio.ByteBuffer;

/**
 * Serialize results to JSON with version 1.0.x schema.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class JsonMessageSerializerGremlinV1d0 extends AbstractJsonMessageSerializerV1d0 {

    private static final String MIME_TYPE = SerTokens.MIME_JSON_V1D0;

    private static final ObjectMapper mapper = GraphSONObjectMapper.create()
            .customModule(new JsonMessageSerializerV1d0.GremlinServerModule())
            .embedTypes(true)
            .build();

    private static byte[] header;

    static {
        final ByteBuffer buffer = ByteBuffer.allocate(MIME_TYPE.length() + 1);
        buffer.put((byte) MIME_TYPE.length());
        buffer.put(MIME_TYPE.getBytes());
        header = buffer.array();
    }

    @Override
    public String[] mimeTypesSupported() {
        return new String[]{MIME_TYPE};
    }

    @Override
    byte[] obtainHeader() {
        return header;
    }

    @Override
    ObjectMapper obtainMapper() {
        return mapper;
    }
}
