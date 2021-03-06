package com.tinkerpop.gremlin.server.handler;

import com.codahale.metrics.Meter;
import com.tinkerpop.gremlin.driver.MessageSerializer;
import com.tinkerpop.gremlin.driver.message.ResponseMessage;
import com.tinkerpop.gremlin.driver.message.ResultCode;
import com.tinkerpop.gremlin.driver.ser.MessageTextSerializer;
import com.tinkerpop.gremlin.server.GremlinServer;
import com.tinkerpop.gremlin.server.util.MetricManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class WsGremlinResponseEncoder extends MessageToMessageEncoder<ResponseMessage> {
    private static final Logger logger = LoggerFactory.getLogger(WsGremlinResponseEncoder.class);
    static final Meter errorMeter = MetricManager.INSTANCE.getMeter(name(GremlinServer.class, "errors"));

    @Override
    protected void encode(final ChannelHandlerContext channelHandlerContext, final ResponseMessage o, final List<Object> objects) throws Exception {
        final MessageSerializer serializer = channelHandlerContext.channel().attr(StateKey.SERIALIZER).get();
        final boolean useBinary = channelHandlerContext.channel().attr(StateKey.USE_BINARY).get();

        try {
            if (useBinary) {
                if (o.getCode().isSuccess())
                    objects.add(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(o, channelHandlerContext.alloc())));
                else {
                    objects.add(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(o, channelHandlerContext.alloc())));
                    final ResponseMessage terminator = ResponseMessage.create(o.getRequestId()).code(ResultCode.SUCCESS_TERMINATOR).build();
                    objects.add(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(terminator, channelHandlerContext.alloc())));
                    errorMeter.mark();
                }
            } else {
                // the expectation is that the GremlinTextRequestDecoder will have placed a MessageTextSerializer
                // instance on the channel.
                final MessageTextSerializer textSerializer = (MessageTextSerializer) serializer;
                if (o.getCode().isSuccess())
                    objects.add(new TextWebSocketFrame(true, 0, textSerializer.serializeResponseAsString(o)));
                else {
                    objects.add(new TextWebSocketFrame(true, 0, textSerializer.serializeResponseAsString(o)));
                    final ResponseMessage terminator = ResponseMessage.create(o.getRequestId()).code(ResultCode.SUCCESS_TERMINATOR).build();
                    objects.add(new TextWebSocketFrame(true, 0, textSerializer.serializeResponseAsString(terminator)));
                    errorMeter.mark();
                }
            }
        } catch (Exception ex) {
            errorMeter.mark();
            logger.warn("The result [{}] in the request {} could not be serialized and returned.", o.getResult(), o.getRequestId(), ex);
            final String errorMessage = String.format("Error during serialization: %s",
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            final ResponseMessage error = ResponseMessage.create(o.getRequestId())
                    .result(errorMessage)
                    .code(ResultCode.SERVER_ERROR_SERIALIZATION).build();
            if (useBinary) {
                channelHandlerContext.write(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(error, channelHandlerContext.alloc())));
                final ResponseMessage terminator = ResponseMessage.create(o.getRequestId()).code(ResultCode.SUCCESS_TERMINATOR).build();
                channelHandlerContext.writeAndFlush(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(terminator, channelHandlerContext.alloc())));
            } else {
                final MessageTextSerializer textSerializer = (MessageTextSerializer) serializer;
                channelHandlerContext.write(new TextWebSocketFrame(textSerializer.serializeResponseAsString(error)));
                final ResponseMessage terminator = ResponseMessage.create(o.getRequestId()).code(ResultCode.SUCCESS_TERMINATOR).build();
                channelHandlerContext.writeAndFlush(new TextWebSocketFrame(textSerializer.serializeResponseAsString(terminator)));
            }
        }
    }
}
