/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty_voltpatches.handler.codec;

import io.netty_voltpatches.channel.ChannelHandlerContext;
import io.netty_voltpatches.channel.ChannelOutboundHandler;
import io.netty_voltpatches.channel.ChannelOutboundHandlerAdapter;
import io.netty_voltpatches.channel.ChannelPipeline;
import io.netty_voltpatches.channel.ChannelPromise;
import io.netty_voltpatches.util.ReferenceCountUtil;
import io.netty_voltpatches.util.ReferenceCounted;
import io.netty_voltpatches.util.internal.RecyclableArrayList;
import io.netty_voltpatches.util.internal.StringUtil;
import io.netty_voltpatches.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * {@link ChannelOutboundHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer}&gt; {
 *
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} message, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(message.toString());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageEncoder} will call
 * {@link ReferenceCounted#release()} on encoded messages.
 */
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToMessageEncoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageEncoder.class, "I");
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match and so encode
     */
    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        RecyclableArrayList out = null;
        try {
            if (acceptOutboundMessage(msg)) {
                out = RecyclableArrayList.newInstance();
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    encode(ctx, cast, out);
                } finally {
                    ReferenceCountUtil.release(cast);
                }

                if (out.isEmpty()) {
                    out.recycle();
                    out = null;

                    throw new EncoderException(
                            StringUtil.simpleClassName(this) + " must produce at least one message.");
                }
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            if (out != null) {
                final int sizeMinusOne = out.size() - 1;
                if (sizeMinusOne >= 0) {
                    for (int i = 0; i < sizeMinusOne; i ++) {
                        ctx.write(out.get(i));
                    }
                    ctx.write(out.get(sizeMinusOne), promise);
                }
                out.recycle();
            }
        }
    }

    /**
     * Encode from one message to an other. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @param out           the {@link List} into which the encoded msg should be added
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception;
}