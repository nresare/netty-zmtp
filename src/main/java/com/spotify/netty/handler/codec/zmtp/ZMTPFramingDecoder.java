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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.nio.ByteOrder;

import static com.spotify.netty.handler.codec.zmtp.ZMTPUtils.FINAL_FLAG;

/**
 * Netty FrameDecoder for zmtp protocol
 *
 * Decodes ZMTP frames into a ZMTPMessage - will return a ZMTPMessage as a message event
 */
public class ZMTPFramingDecoder extends FrameDecoder {

  private final ZMTPMessageParser parser;
  private final ZMTPSession session;
  private final ZMTPConnectionMode mode;
  private HandshakeState state;
  private ChannelFuture handshakeFuture;

  enum HandshakeState {
    BEFORE_HANDSHAKE, VERSION_DETECTED, DONE
  }

  public ZMTPFramingDecoder(final ZMTPSession session) {
    this(session, ZMTPConnectionMode.ZMTP_10);
  }

  /**
   * Creates a new decoder
   */
  public ZMTPFramingDecoder(final ZMTPSession session, ZMTPConnectionMode mode) {
    if (mode == ZMTPConnectionMode.ZMTP_20) {
      throw new UnsupportedOperationException("connection mode ZMTP_20 not yet supported");
    }
    this.mode = mode;
    this.session = session;
    this.parser = new ZMTPMessageParser(session.isEnveloped());
    this.state = HandshakeState.BEFORE_HANDSHAKE;
  }

  private void sendInteroperabilitySignature(final Channel channel) {
    int len = session.useLocalIdentity() ? session.getLocalIdentity().length + 1 : 1;
    ChannelBuffer msg = ChannelBuffers.dynamicBuffer(10);
    msg.writeByte(0xff);
    msg.writeLong(msg.order() == ByteOrder.BIG_ENDIAN ? len : ChannelBuffers.swapLong(len));
    msg.writeByte(0x7f);
    channel.write(msg);
  }

  /**
   * Sends my local identity
   */
  private void sendIdentity(final Channel channel) {
    final ChannelBuffer msg;

    if (session.useLocalIdentity()) {
      // send session current identity
      msg = ChannelBuffers.dynamicBuffer(2 + session.getLocalIdentity().length);

      ZMTPUtils.encodeLength(1 + session.getLocalIdentity().length, msg);
      msg.writeByte(FINAL_FLAG);
      msg.writeBytes(session.getLocalIdentity());
    } else {
      msg = ChannelBuffers.dynamicBuffer(2);
      // Anonymous identity
      msg.writeByte(1);
      msg.writeByte(FINAL_FLAG);
    }

    // Send identity message
    channel.write(msg);
  }

  /**
   * Parses the remote zmtp identity received
   */
  private void handleRemoteIdentity(final ChannelBuffer buffer) {
    buffer.markReaderIndex();

    final long len = ZMTPUtils.decodeLength(buffer);
    if (len > 256) {
      // spec says the ident string can be up to 255 chars
      throw new ZMTPException("Remote identity longer than the allowed 255 octets");
    }

    // Bail out if there's not enough data
    if (len == -1 || buffer.readableBytes() < len) {
      buffer.resetReaderIndex();
      throw new IndexOutOfBoundsException("not enough data");
    }

    final int flags = buffer.readByte();

    if (len == 1) {
      // Anonymous identity
      session.setRemoteIdentity(null);
    } else {
      // Read identity from remote
      final byte[] identity = new byte[(int) len - 1];
      buffer.readBytes(identity);

      // Anonymous identity
      session.setRemoteIdentity(identity);
    }

    handshakeFuture.setSuccess();
  }

  /**
   * Read enough bytes from buffer to deduce the remote protocol version. If the protocol
   * version is determined to be ZMTP/1.0, reset the buffer to the beginning of buffer. If
   * version is determined to be a later version, the buffer is not reset.
   *
   * @param buffer the buffer of data to determine version from
   * @return false if not enough data is available, else true
   * @throws IndexOutOfBoundsException if there are not enough data available in buffer
   */
  static int detectProtocolVersion(final ChannelBuffer buffer) {
    if (buffer.readByte() != (byte)0xff) {
      return 1;
    }
    buffer.skipBytes(8);
    if ((buffer.readByte() & 0x01) == 0) {
      return 1;
    }
    return 2;
  }

  /**
   * Responsible for decoding incoming data to zmtp frames
   */
  @Override
  protected Object decode(
      final ChannelHandlerContext ctx, final Channel channel, final ChannelBuffer buffer)
      throws Exception {

    if (buffer.readableBytes() < 2) {
      return null;
    }
    try {
      if (state == HandshakeState.BEFORE_HANDSHAKE) {
        if (mode == ZMTPConnectionMode.ZMTP_20_INTEROP) {
          session.setProtocolVersion(detectProtocolVersion(buffer));
          state = HandshakeState.VERSION_DETECTED;
          sendZMTP2Greeting(channel, false);
        } else { // mode == ZMTP_10
          handleRemoteIdentity(buffer);
          state = HandshakeState.DONE;
        }
      } else if (state == HandshakeState.VERSION_DETECTED) {
        if (session.getProtocolVersion() == 1) {
          handleRemoteIdentity(buffer);
          state = HandshakeState.DONE;
        } else { // session.getProtocolVersion() == 2)

          handleZMTP2Greeting(buffer);
        }
      }
    } catch (IndexOutOfBoundsException e) {
      return null;
    }


    // Parse incoming frames
    final ZMTPMessage message = parser.parse(buffer);
    if (message == null) {
      return null;
    }

    return new ZMTPIncomingMessage(session, message);
  }

  private void sendZMTP2Greeting(Channel channel, boolean includeSignature) {
    ChannelBuffer cb = ChannelBuffers.dynamicBuffer();
    if (includeSignature) {
      cb.writeByte(0xff);
      cb.writeLong(0L);
      cb.writeByte(0x7f);
    }
    // version
    cb.writeByte(0x01);

  }

  private void handleZMTP2Greeting(ChannelBuffer buffer) {
    // according to the section Version Negotiation in the ZMTP/3.0 spec
    // conforming implementations need to accept all higher protocol versions than
    // the supported one (0x01, in this case). 0x00 however, would be invalid
    if (buffer.readByte() == 0x00) {
      throw new ZMTPException("Invalid protocol revision in greeting (0x00)");
    }
    // ignore the socket type for now
    buffer.skipBytes(1);
    if (buffer.readByte() != 0x00) {
      throw new ZMTPException("Expected 0x00 as octet 13 in ZMTP/2.0 greeting");
    }
    byte[] identity = new byte[buffer.readByte()];
    buffer.readBytes(identity);
    session.setRemoteIdentity(identity);
  }

  @Override
  public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    // Store channel in the session
    this.session.setChannel(e.getChannel());

    handshake(ctx.getChannel()).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(final ChannelFuture future) throws Exception {
        ctx.sendUpstream(e);
      }
    });
  }

  private ChannelFuture handshake(final Channel channel) {
    handshakeFuture = Channels.future(channel);

    if (this.mode == ZMTPConnectionMode.ZMTP_20_INTEROP) {
      // in the interop case, just send the signature. When we know the remote protocol version
      // (in the decode method) we will send the identity in the proper format.
      sendInteroperabilitySignature(channel);
    } else { // mode == ZMTP_10
      // Send our identity
      sendIdentity(channel);
    }

    return handshakeFuture;
  }
}
