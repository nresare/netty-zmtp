package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Implements the ZMTP/1.0 handshake protocol.
 */
class Handshake10 extends Handshake {
  private final byte[] localIdentity;

  /**
   * Construct a Handshake with the specified mode and associated session. The session is used
   * for the local identity needed for the greeting part of the handshake process and to store the
   * identity of the remote peer. mode is used  to indicate the protocol version behaviour of this
   * Handshake instance.
   *
   * @param localIdentity the identity octets of this peer
   */
  public Handshake10(byte[] localIdentity) {
    this.localIdentity = localIdentity != null ? localIdentity : new byte[0];
  }

  /**
   * Provides a ChannelBuffer to be sent to the remote peer directly after a socket connection
   * is established.
   *
   * @return the ChannelBuffer to send.
   */
  public ChannelBuffer onConnect() {
      return makeZMTP1Greeting(localIdentity);
  }

  /**
   * The ZMTP handshake may need more than one round trip to the peer. This method gets called
   * when a new buffer is received and is used to move the handshake process forward.
   *
   * @param buffer contains the data received from the peer
   * @return a buffer to send to the peer if any, else null.
   * @throws IndexOutOfBoundsException if the input is fragmented, else
   */
  public ChannelBuffer inputOutput(final ChannelBuffer buffer)
      throws IndexOutOfBoundsException {
    byte[] remoteIdentity = readZMTP1RemoteIdentity(buffer);
    if (listener != null) {
      listener.handshakeDone(1, remoteIdentity);
    }
    return null;
  }

  /**
   * Parse and return the remote identity octets from a ZMTP/1.0 greeting.
   */
  static byte[] readZMTP1RemoteIdentity(final ChannelBuffer buffer) {
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
    // skip the flags byte
    buffer.skipBytes(1);

    if (len == 1) {
      return null;
    }
    final byte[] identity = new byte[(int)len - 1];
    buffer.readBytes(identity);
    return identity;
  }

  /**
   * Create and return a ChannelBuffer containing an ZMTP/1.0 greeting based on on the constructor
   * provided session.
   *
   * @return a ChannelBuffer with a greeting
   */
  private static ChannelBuffer makeZMTP1Greeting(byte[] localIdentity) {
    ChannelBuffer out = ChannelBuffers.dynamicBuffer();
    ZMTPUtils.encodeLength(localIdentity.length + 1, out);
    out.writeByte(0x00);
    out.writeBytes(localIdentity);
    return out;
  }
}
