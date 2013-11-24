package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Implements the ZMTP/2.0 handshake protocol.
 */
class Handshake20 extends Handshake {
  private final byte[] localIdentity;
  private final ZMTPSocketType type;
  private final boolean interop;
  private boolean splitHandshake;

  /**
   * Construct a Handshake with the specified mode and associated session. The session is used
   * for the local identity needed for the greeting part of the handshake process and to store the
   * identity of the remote peer. mode is used  to indicate the protocol version behaviour of this
   * Handshake instance.
   *
   * @param type the type of this socket
   */
  public Handshake20(ZMTPSocketType type, byte[] localIdentity, boolean interop) {
    this.splitHandshake = false;
    this.interop = interop;
    this.type = type;
    this.localIdentity = localIdentity != null ? localIdentity : new byte[0];

  }

  /**
   * Provides a ChannelBuffer to be sent to the remote peer directly after a socket connection
   * is established.
   *
   * @return the ChannelBuffer to send.
   */
  public ChannelBuffer onConnect() {
    if (interop) {
      return makeZMTP2CompatSignature();
    } else { // mode == ZMTPMode.ZMTP_20
      return makeZMTP2Greeting(true);
    }
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

    if (splitHandshake) {
      done(2, parseZMTP2Greeting(buffer, false));
      return null;
    }

    if (interop) {
      buffer.markReaderIndex();
      int version = detectProtocolVersion(buffer);
      if (version == 1) {
        buffer.resetReaderIndex();
        done(version, Handshake10.readZMTP1RemoteIdentity(buffer));
        // when a ZMTP/1.0 peer is detected, just send the identity bytes. Together
        // with the compatibility signature it makes for a valid ZMTP/1.0 greeting.
        return ChannelBuffers.wrappedBuffer(localIdentity);
      } else {
        splitHandshake = true;
        return makeZMTP2Greeting(false);
      }
    } else {
      done(2, parseZMTP2Greeting(buffer, true));
    }
    return null;
  }

  private void done(int version, byte[] remoteIdentity) {
    if (listener != null) {
      listener.handshakeDone(version, remoteIdentity);
    }
  }

  static byte[] parseZMTP2Greeting(ChannelBuffer buffer, boolean expectSignature) {
    if (expectSignature) {
      if (buffer.readByte() != (byte)0xff) {
        throw new ZMTPException("Illegal ZMTP/2.0 greeting, first octet not 0xff");
      }
      buffer.skipBytes(9);
    }
    // ignoring version number and socket type for now
    buffer.skipBytes(2);
    int val = buffer.readByte();
    if (val != 0x00) {
      String s = String.format("Malfromed greeting. Byte 13 expected to be 0x00, was: 0x%02x", val);
      throw new ZMTPException(s);
    }
    int len = buffer.readByte();
    final byte[] identity = new byte[len];
    buffer.readBytes(identity);
    return identity;
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
   * Make a ChannelBuffer containing a ZMTP/2.0 greeting, possibly leaving out the 10 initial
   * signature octets if includeSignature is false.
   *
   * @param includeSignature true if a full greeting should be sent, false if the initial 10
   *                         octets should be left out
   * @return a ChannelBuffer containing the greeting
   */
  private ChannelBuffer makeZMTP2Greeting(boolean includeSignature) {
    ChannelBuffer out = ChannelBuffers.dynamicBuffer();
    if (includeSignature) {
      ZMTPUtils.encodeLength(0, out, true);
      // last byte of signature
      out.writeByte(0x7f);
      // protocol revision
    }
    out.writeByte(0x01);
    // socket-type
    out.writeByte(type.ordinal());
    // identity
    // the final-short flag octet
    out.writeByte(0x00);
    out.writeByte(localIdentity.length);
    out.writeBytes(localIdentity);
    return out;
  }

  /**
   * Create and return a ChannelBuffer containing the ZMTP/2.0 compatibility detection signature
   * message as specified in the Backwards Compatibility section of http://rfc.zeromq.org/spec:15
   */
  private ChannelBuffer makeZMTP2CompatSignature() {
    ChannelBuffer out = ChannelBuffers.dynamicBuffer();
    ZMTPUtils.encodeLength(localIdentity.length + 1, out, true);
    out.writeByte(0x7f);
    return out;
  }

}
