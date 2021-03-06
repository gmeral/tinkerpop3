package com.tinkerpop.gremlin.giraph.structure.io.kryo;

import com.tinkerpop.gremlin.giraph.structure.util.GiraphInternalVertex;
import com.tinkerpop.gremlin.structure.Direction;
import com.tinkerpop.gremlin.structure.io.kryo.KryoWriter;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class KryoRecordWriter extends RecordWriter<NullWritable, GiraphInternalVertex> {

    private final DataOutputStream out;
    private final KryoWriter kryoWriter;

    public KryoRecordWriter(final DataOutputStream out) {
        this.out = out;
        this.kryoWriter = KryoWriter.create().build();
    }

    @Override
    public void write(final NullWritable key, final GiraphInternalVertex vertex) throws IOException {
        if (null != vertex) {
            kryoWriter.writeVertex(out, vertex.getTinkerVertex(), Direction.BOTH);
        }
    }

    @Override
    public synchronized void close(TaskAttemptContext context) throws IOException {
        out.close();
    }
}
