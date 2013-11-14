/*
 * Copyright (c) 2012-2013 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * Netty FrameDecoder for zmtp protocol
 *
 * Decodes ZMTP frames into a ZMTPMessage - will return a ZMTPMessage as a message event
 */
public class ZMTPFramingDecoder extends FrameDecoder {

  private final ZMTPMessageParser parser;
  private final ZMTPSession session;
  private final ZMTPMode mode;
  private final ZMTPSocketType type;
  private Handshake handshake;
  private ChannelFuture handshakeFuture;

  public ZMTPFramingDecoder(final ZMTPSession session, final ZMTPSocketType type) {
    this(session, ZMTPMode.ZMTP_10, type);
  }

  /**
   * Creates a new decoder
   */
  public ZMTPFramingDecoder(final ZMTPSession session, ZMTPMode mode, ZMTPSocketType type) {
    this.mode = mode;
    this.session = session;
    this.parser = new ZMTPMessageParser(session.isEnveloped());
    this.type = type;
  }

  /**
   * Responsible for decoding incoming data to zmtp frames
   */
  @Override
  protected Object decode(
      final ChannelHandlerContext ctx, final Channel channel, final ChannelBuffer buffer)
      throws Exception {

    if (!handshake.isDone()) {
      try {
        buffer.markReaderIndex();
        final ChannelBuffer toSend = handshake.inputOutput(buffer);
        if (toSend != null) {
          channel.write(toSend);
        }
      } catch (IndexOutOfBoundsException e) {
        if (handshakeFuture != null) {
          handshakeFuture.setFailure(e);
        }
        buffer.resetReaderIndex();
      }
      return null;
    }

    // Parse incoming frames
    final ZMTPMessage message = parser.parse(buffer);
    if (message == null) {
      return null;
    }

    return new ZMTPIncomingMessage(session, message);
  }

  @Override
  public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {

    handshakeFuture = Channels.future(e.getChannel());
    handshakeFuture.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(final ChannelFuture future) throws Exception {
        ctx.sendUpstream(e);
      }
    });

    handshake = new Handshake(mode, session, type);
    Channel channel = e.getChannel();
    handshake.setFuture(handshakeFuture);
    channel.write(handshake.onConnect());
    this.session.setChannel(e.getChannel());
  }

}
