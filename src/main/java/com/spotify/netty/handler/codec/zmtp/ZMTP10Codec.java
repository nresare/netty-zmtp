package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class ZMTP10Codec extends CodecBase {

  public ZMTP10Codec(byte[] localIdentity) {
    super(localIdentity);
  }

  @Override
  ChannelBuffer onConnect() {
    return makeZMTP1Greeting(localIdentity);
  }

  public ChannelBuffer inputOutput(final ChannelBuffer buffer)
      throws IndexOutOfBoundsException {
    byte[] remoteIdentity = readZMTP1RemoteIdentity(buffer);
    if (listener != null) {
      listener.handshakeDone(1, remoteIdentity);
    }
    return null;
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
