package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Reused static methods.
 */
public class TestUtil {

  public static ChannelBuffer buf(int ...bytes) {
    byte[] bs = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      bs[i] = (byte)bytes[i];
    }
    ChannelBuffer cb = ChannelBuffers.dynamicBuffer(bs.length);
    cb.writeBytes(bs);
    return cb;
  }
}
