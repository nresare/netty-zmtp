package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the functionality required to properly do a ZMTP handshake. It holds state and
 * is expected to be used once per connection.
 */
class Handshake {
  private final ZMTPSession session;
  private final ZMTPMode mode;
  private HandshakeState state;
  private final ZMTPSocketType type;
  private ChannelFuture future;

  enum HandshakeState {
    BEFORE_HANDSHAKE, VERSION_DETECTED, DONE
  }

  /**
   * Construct a Handshake with the specified mode and associated session. The session is used
   * for the local identity needed for the greeting part of the handshake process and to store the
   * identity of the remote peer. mode is used  to indicate the protocol version behaviour of this
   * Handshake instance.
   *
   * @param mode the mode of the handshake to be preformed by this instance.
   * @param session the session associated with this handshake.
   * @param type the type of this socket
   */
  public Handshake(ZMTPMode mode, ZMTPSession session, ZMTPSocketType type) {
    this.state = HandshakeState.BEFORE_HANDSHAKE;
    this.mode = mode;
    this.session = session;
    this.type = type;
  }

  /**
   * A future to call when this handshake process has been successfully performed.
   *
   * @param future the future to call.
   */
  public void setFuture(ChannelFuture future) {
    this.future = future;
  }

  /**
   * Returns true if the handshake associated with this instance has been executed or not.
   * @return true if handshake has been done, else false
   */
  public boolean isDone() {
    return this.state == HandshakeState.DONE;
  }

  /**
   * Provides a ChannelBuffer to be sent to the remote peer directly after a socket connection
   * is established.
   *
   * @return the ChannelBuffer to send.
   */
  public ChannelBuffer onConnect() {
    if (mode == ZMTPMode.ZMTP_10) {
      return makeZMTP1Greeting();
    } else if (mode == ZMTPMode.ZMTP_20_INTEROP) {
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
    if (state == HandshakeState.VERSION_DETECTED) {
      parseZMTP2Greeting(buffer, false);
      state = HandshakeState.DONE;
      notifySuccess();
      return null;
    }

    if (mode == ZMTPMode.ZMTP_10) {
      session.setRemoteIdentity(readZMTP1RemoteIdentity(buffer));
      session.setProtocolVersion(1);
      state = HandshakeState.DONE;
      notifySuccess();
    } else if (mode == ZMTPMode.ZMTP_20_INTEROP) {
      buffer.markReaderIndex();
      int version = detectProtocolVersion(buffer);
      session.setProtocolVersion(version);
      if (version == 1) {
        buffer.resetReaderIndex();
        session.setRemoteIdentity(readZMTP1RemoteIdentity(buffer));
        state = HandshakeState.DONE;
        notifySuccess();
        // when a ZMTP/1.0 peer is detected, just send the identity bytes. Together
        // with the compatibility signature it makes for a valid ZMTP/1.0 greeting.
        return ChannelBuffers.wrappedBuffer(session.getLocalIdentity());
      } else { // version == 2
        if (buffer.readableBytes() > 0) {
          parseZMTP2Greeting(buffer, false);
          state = HandshakeState.DONE;
          notifySuccess();
        } else {
          state = HandshakeState.VERSION_DETECTED;
        }
        return makeZMTP2Greeting(false);
      }
    } else { // mode == ZMTP_20
      session.setProtocolVersion(2);
      parseZMTP2Greeting(buffer, true);
      state = HandshakeState.DONE;
      notifySuccess();
    }
    return null;
  }

  private void notifySuccess() {
    if (future != null) {
      future.setSuccess();
    }
  }

  /**
   * Parse and return the remote identity octets from a ZMTP/1.0 greeting.
   */
  static byte[] readZMTP1RemoteIdentity(final ChannelBuffer buffer) {
    // TODO: noa: I don't think mark/reset() is needed. Verify.
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

  void parseZMTP2Greeting(ChannelBuffer buffer, boolean expectSignature) {
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
    session.setRemoteIdentity(identity);
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
    out.writeByte(typeToConst(type));
    // identity
    // the final-short flag octet
    out.writeByte(0x00);
    byte[] localIdentity = session.useLocalIdentity() ? session.getLocalIdentity() : new byte[0];
    out.writeByte(localIdentity.length);
    out.writeBytes(localIdentity);
    return out;
  }

  /**
   * Create and return a ChannelBuffer containing an ZMTP/1.0 greeting based on on the constructor
   * provided session.
   *
   * @return a ChannelBuffer with a greeting
   */
  private ChannelBuffer makeZMTP1Greeting() {
    byte[] localIdentity = session.useLocalIdentity() ? session.getLocalIdentity() : new byte[0];
    ChannelBuffer out = ChannelBuffers.dynamicBuffer();
    ZMTPUtils.encodeLength(localIdentity.length + 1, out);
    out.writeByte(0x00);
    out.writeBytes(localIdentity);
    return out;
  }

  /**
   * Create and return a ChannelBuffer containing the ZMTP/2.0 compatibility detection signature
   * message as specified in the Backwards Compatibility section of http://rfc.zeromq.org/spec:15
   */
  private ChannelBuffer makeZMTP2CompatSignature() {
    byte[] localIdentity = session.useLocalIdentity() ? session.getLocalIdentity() : new byte[0];
    ChannelBuffer out = ChannelBuffers.dynamicBuffer();
    ZMTPUtils.encodeLength(localIdentity.length + 1, out, true);
    out.writeByte(0x7f);
    return out;
  }

  static private Map<ZMTPSocketType,Integer> typeToId = new HashMap<ZMTPSocketType,Integer>();
  static {
    typeToId.put(ZMTPSocketType.PAIR, 0);
    typeToId.put(ZMTPSocketType.SUB, 1);
    typeToId.put(ZMTPSocketType.PUB, 2);
    typeToId.put(ZMTPSocketType.REQ, 3);
    typeToId.put(ZMTPSocketType.REP, 4);
    typeToId.put(ZMTPSocketType.DEALER, 5);
    typeToId.put(ZMTPSocketType.ROUTER, 6);
    typeToId.put(ZMTPSocketType.PULL, 7);
    typeToId.put(ZMTPSocketType.PUSH, 8);
  }

  static char typeToConst(ZMTPSocketType type) {
    // yup, autoboxing is terrible
    try {
      int i = typeToId.get(type);
      return (char)i;
    } catch (NullPointerException e) {
      throw new Error(String.format("Unknown ZMTPSocketType: %s", type));
    }
  }
}
