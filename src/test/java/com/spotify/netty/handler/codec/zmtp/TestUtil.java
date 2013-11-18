package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Assert;

/**
 * Reused static methods.
 */
class TestUtil {

  public static byte[] bytes(int ...bytes) {
    byte[] bs = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      bs[i] = (byte)bytes[i];
    }
    return bs;
  }

  public static ChannelBuffer buf(int ...bytes) {
    ChannelBuffer cb = ChannelBuffers.dynamicBuffer(bytes.length);
    cb.writeBytes(bytes(bytes));
    return cb;
  }

  public static void cmp(ChannelBuffer buf, int... bytes) {
    cmp(buf, buf(bytes));
  }

  /**
   * Compare ChannelBuffers left and right and raise an Assert.fail() if there are differences
   *
   * @param expected the ChannelBuffer you expect
   * @param actual the ChannelBuffer you actually got
   */
  public static void cmp(ChannelBuffer expected, ChannelBuffer actual) {
    if (expected.readableBytes() != actual.readableBytes()) {
      Assert.fail(String.format("Expected same number of readable bytes in buffers (%d != %d)",
          expected.readableBytes(), actual.readableBytes()));
    }
    final int readableBytes = expected.readableBytes();
    for (int i = 0; i < readableBytes; i++) {
      byte lb = expected.readByte();
      byte rb = actual.readByte();
      if (lb != rb) {
        Assert.fail(String.format("Pos %d: (0x%02x != 0x%02x)", i, lb, rb));
      }
    }
  }
}
