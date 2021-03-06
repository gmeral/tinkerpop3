package com.tinkerpop.gremlin.structure.io.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultStreamFactory;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.tinkerpop.gremlin.process.Path;
import com.tinkerpop.gremlin.process.PathTraverser;
import com.tinkerpop.gremlin.process.SimpleTraverser;
import com.tinkerpop.gremlin.process.T;
import com.tinkerpop.gremlin.process.computer.traversal.TraversalCounters;
import com.tinkerpop.gremlin.process.computer.traversal.TraversalPaths;
import com.tinkerpop.gremlin.structure.Contains;
import com.tinkerpop.gremlin.structure.Direction;
import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.io.util.IoEdge;
import com.tinkerpop.gremlin.structure.io.util.IoVertex;
import com.tinkerpop.gremlin.structure.util.detached.DetachedPath;
import org.javatuples.Pair;
import org.javatuples.Triplet;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public final class GremlinKryo {
    static final byte[] GIO = "gio".getBytes();
    private final List<Triplet<Class, Serializer, Integer>> serializationList;
    private final HeaderWriter headerWriter;
    private final HeaderReader headerReader;

    public static final byte DEFAULT_EXTENDED_VERSION = Byte.MIN_VALUE;

    private GremlinKryo(final List<Triplet<Class, Serializer, Integer>> serializationList,
                        final HeaderWriter headerWriter,
                        final HeaderReader headerReader) {
        this.serializationList = serializationList;
        this.headerWriter = headerWriter;
        this.headerReader = headerReader;
    }

    public Kryo createKryo() {
        final Kryo kryo = new Kryo(new GremlinClassResolver(), new MapReferenceResolver(), new DefaultStreamFactory());
        kryo.addDefaultSerializer(Map.Entry.class, new EntrySerializer());
        kryo.setRegistrationRequired(true);
        serializationList.forEach(p -> {
            final Serializer serializer = Optional.ofNullable(p.getValue1()).orElse(kryo.getDefaultSerializer(p.getValue0()));
            kryo.register(p.getValue0(), serializer, p.getValue2());
        });
        return kryo;
    }

    public HeaderWriter getHeaderWriter() {
        return headerWriter;
    }

    public HeaderReader getHeaderReader() {
        return headerReader;
    }

    @FunctionalInterface
    public interface HeaderReader {
        public void read(final Kryo kryo, final Input input) throws IOException;
    }

    @FunctionalInterface
    public interface HeaderWriter {
        public void write(final Kryo kryo, final Output output) throws IOException;
    }

    /**
     * Use a specific version of Gremlin Kryo.
     */
    public static Builder create(final Version version) {
        return version.getBuilder();
    }

    /**
     * Use the most current version of Gremlin Kryo.
     */
    public static Builder create() {
        return Version.V_1_0_0.getBuilder();
    }

    public static interface Builder {
        /**
         * Add custom classes to serializes with kryo using standard serialization.
         */
        public Builder addCustom(final Class... custom);

        /**
         * Add custom classes to serializes with custom serialization.
         */
        public Builder addCustom(final Pair<Class, Serializer>... custom);

        /**
         * If using custom classes it might be useful to tag the version stamped to the serialization with a custom
         * value, such that Kryo serialization at 1.0.0 would have a fourth byte for an extended version.  The
         * user supplied fourth byte can then be used to ensure the right deserializer is used to read the data.
         * If this value is not supplied then it is written as {@link Byte#MIN_VALUE}. The value supplied here
         * should be greater than or equal to zero.
         */
        public Builder extendedVersion(final byte extendedVersion);

        /**
         * By default the {@link #extendedVersion(byte)} is checked against what is read from an input source and
         * if those values are equal the version being read is considered "compliant".  To alter this behavior,
         * supply a custom compliance {@link Predicate} to evaluate the value read from the input source (i.e. first
         * argument) and the value marked in the {@code GremlinKryo} instance {i.e. second argument}.  Supplying
         * this function is useful when versions require backward compatibility or other more complex checks.  This
         * function is only used if the {@link #extendedVersion(byte)} is set to something other than its default.
         */
        public Builder compliant(final BiPredicate<Byte, Byte> compliant);

        public GremlinKryo build();
    }

    public enum Version {
        V_1_0_0(BuilderV1d0.class);

        private final Class<? extends Builder> builder;

        private Version(final Class<? extends Builder> builder) {
            this.builder = builder;
        }

        Builder getBuilder() {
            try {
                return builder.newInstance();
            } catch (Exception x) {
                throw new RuntimeException("GremlinKryo Builder implementation cannot be instantiated", x);
            }
        }
    }

    public static class BuilderV1d0 implements Builder {

        /**
         * Map with one entry that is used so that it is possible to get the class of LinkedHashMap.Entry.
         */
        private static final LinkedHashMap m = new LinkedHashMap() {{
            put("junk", "dummy");
        }};

        private static final Class linkedHashMapEntryClass = m.entrySet().iterator().next().getClass();

        /**
         * Note that the following are pre-registered boolean, Boolean, byte, Byte, char, Character, double, Double,
         * int, Integer, float, Float, long, Long, short, Short, String, void. Current max is DetachedPath=60.
         */
        private final List<Triplet<Class, Serializer, Integer>> serializationList = new ArrayList<Triplet<Class, Serializer, Integer>>() {{
            add(Triplet.<Class, Serializer, Integer>with(byte[].class, null, 25));
            add(Triplet.<Class, Serializer, Integer>with(char[].class, null, 26));
            add(Triplet.<Class, Serializer, Integer>with(short[].class, null, 27));
            add(Triplet.<Class, Serializer, Integer>with(int[].class, null, 28));
            add(Triplet.<Class, Serializer, Integer>with(long[].class, null, 29));
            add(Triplet.<Class, Serializer, Integer>with(float[].class, null, 30));
            add(Triplet.<Class, Serializer, Integer>with(double[].class, null, 31));
            add(Triplet.<Class, Serializer, Integer>with(String[].class, null, 32));
            add(Triplet.<Class, Serializer, Integer>with(Object[].class, null, 33));
            add(Triplet.<Class, Serializer, Integer>with(ArrayList.class, null, 10));
            add(Triplet.<Class, Serializer, Integer>with(BigInteger.class, null, 34));
            add(Triplet.<Class, Serializer, Integer>with(BigDecimal.class, null, 35));
            add(Triplet.<Class, Serializer, Integer>with(Calendar.class, null, 39));
            add(Triplet.<Class, Serializer, Integer>with(Class.class, null, 41));
            add(Triplet.<Class, Serializer, Integer>with(Collection.class, null, 37));
            add(Triplet.<Class, Serializer, Integer>with(Collections.EMPTY_LIST.getClass(), null, 51));
            add(Triplet.<Class, Serializer, Integer>with(Collections.EMPTY_MAP.getClass(), null, 52));
            add(Triplet.<Class, Serializer, Integer>with(Collections.EMPTY_SET.getClass(), null, 53));
            add(Triplet.<Class, Serializer, Integer>with(Collections.singleton(null).getClass(), null, 54));
            add(Triplet.<Class, Serializer, Integer>with(Collections.singletonList(null).getClass(), null, 24));
            add(Triplet.<Class, Serializer, Integer>with(Collections.singletonMap(null, null).getClass(), null, 23));
            add(Triplet.<Class, Serializer, Integer>with(Contains.class, null, 49));
            add(Triplet.<Class, Serializer, Integer>with(Currency.class, null, 40));
            add(Triplet.<Class, Serializer, Integer>with(Date.class, null, 38));
            add(Triplet.<Class, Serializer, Integer>with(Direction.class, null, 12));
            add(Triplet.<Class, Serializer, Integer>with(Edge.class, new ElementSerializer.EdgeSerializer(), 19));
            add(Triplet.<Class, Serializer, Integer>with(EdgeTerminator.class, null, 14));
            add(Triplet.<Class, Serializer, Integer>with(EnumSet.class, null, 46));
            add(Triplet.<Class, Serializer, Integer>with(HashMap.class, null, 11));
            add(Triplet.<Class, Serializer, Integer>with(HashMap.Entry.class, null, 16));
            add(Triplet.<Class, Serializer, Integer>with(IoEdge.class, null, 21));
            add(Triplet.<Class, Serializer, Integer>with(IoVertex.class, null, 20));
            add(Triplet.<Class, Serializer, Integer>with(KryoSerializable.class, null, 36));
            add(Triplet.<Class, Serializer, Integer>with(LinkedHashMap.class, null, 47));
            add(Triplet.<Class, Serializer, Integer>with(linkedHashMapEntryClass, null, 15));
            add(Triplet.<Class, Serializer, Integer>with(Locale.class, null, 22));
            add(Triplet.<Class, Serializer, Integer>with(StringBuffer.class, null, 43));
            add(Triplet.<Class, Serializer, Integer>with(StringBuilder.class, null, 44));
            add(Triplet.<Class, Serializer, Integer>with(T.class, null, 48));
            add(Triplet.<Class, Serializer, Integer>with(TimeZone.class, null, 42));
            add(Triplet.<Class, Serializer, Integer>with(TreeMap.class, null, 45));
            add(Triplet.<Class, Serializer, Integer>with(TreeSet.class, null, 50));
            add(Triplet.<Class, Serializer, Integer>with(UUID.class, new UUIDSerializer(), 17));
            add(Triplet.<Class, Serializer, Integer>with(Vertex.class, new ElementSerializer.VertexSerializer(), 18));
            add(Triplet.<Class, Serializer, Integer>with(VertexTerminator.class, null, 13));

            // recently added for GraphTraversal in OLAP
            add(Triplet.<Class, Serializer, Integer>with(SimpleTraverser.class, null, 55));
            add(Triplet.<Class, Serializer, Integer>with(PathTraverser.class, null, 56));
            add(Triplet.<Class, Serializer, Integer>with(TraversalCounters.class, null, 57));
            add(Triplet.<Class, Serializer, Integer>with(TraversalPaths.class, null, 58));
            add(Triplet.<Class, Serializer, Integer>with(Path.class, null, 59));
            add(Triplet.<Class, Serializer, Integer>with(DetachedPath.class, null, 60));
        }};

        private static final byte major = 1;
        private static final byte minor = 0;
        private static final byte patchLevel = 0;

        private byte extendedVersion = DEFAULT_EXTENDED_VERSION;
        private BiPredicate<Byte, Byte> compliant = (readExt, serExt) -> readExt.equals(serExt);

        /**
         * Starts numbering classes for Kryo serialization at 65536 to leave room for future usage by TinkerPop.
         */
        private final AtomicInteger currentSerializationId = new AtomicInteger(65536);

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder addCustom(final Class... custom) {
            if (custom != null && custom.length > 0)
                serializationList.addAll(Arrays.asList(custom).stream()
                        .map(c -> Triplet.<Class, Serializer, Integer>with(c, null, currentSerializationId.getAndIncrement()))
                        .collect(Collectors.<Triplet<Class, Serializer, Integer>>toList()));
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder addCustom(final Pair<Class, Serializer>... custom) {
            if (custom != null && custom.length > 0) {
                serializationList.addAll(Arrays.asList(custom).stream()
                        .map(c -> Triplet.<Class, Serializer, Integer>with(c.getValue0(), c.getValue1(), currentSerializationId.getAndIncrement()))
                        .collect(Collectors.<Triplet<Class, Serializer, Integer>>toList()));
            }

            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder extendedVersion(final byte extendedVersion) {
            if (extendedVersion > DEFAULT_EXTENDED_VERSION && extendedVersion < 0)
                throw new IllegalArgumentException("The extendedVersion must be greater than zero");

            this.extendedVersion = extendedVersion;
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder compliant(final BiPredicate<Byte, Byte> compliant) {
            if (null == compliant)
                throw new IllegalArgumentException("compliant");

            this.compliant = compliant;
            return this;
        }

        @Override
        public GremlinKryo build() {
            return new GremlinKryo(serializationList, this::writeHeader, this::readHeader);
        }

        private void writeHeader(final Kryo kryo, final Output output) throws IOException {
            // 32 byte header total
            output.writeBytes(GIO);

            // some space for later
            output.writeBytes(new byte[25]);

            // version x.y.z
            output.writeByte(major);
            output.writeByte(minor);
            output.writeByte(patchLevel);
            output.writeByte(extendedVersion);
        }

        private void readHeader(final Kryo kryo, final Input input) throws IOException {
            if (!Arrays.equals(GIO, input.readBytes(3)))
                throw new IOException("Invalid format - first three bytes of header do not match expected value");

            // skip the next 25 bytes in v1
            input.readBytes(25);

            // final three bytes of header are the version which should be 1.0.0
            final byte[] version = input.readBytes(3);
            final byte extension = input.readByte();

            // direct match on version for now
            if (version[0] != major || version[1] != minor || version[2] != patchLevel)
                throw new IOException(String.format(
                        "The version [%s.%s.%s] in the stream cannot be understood by this reader",
                        version[0], version[1], version[2]));

            if (extendedVersion >= 0 && !compliant.test(extension, extendedVersion))
                throw new IOException(String.format(
                        "The extension [%s] in the input source is not compliant with this configuration of GremlinKryo - [%s]",
                        extension, extendedVersion));
        }
    }
}
