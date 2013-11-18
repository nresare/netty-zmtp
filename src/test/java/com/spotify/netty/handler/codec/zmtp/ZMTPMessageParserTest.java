package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.spotify.netty.handler.codec.zmtp.TestUtil.buf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests ZMTPMessageParser
 */
public class ZMTPMessageParserTest {

  private static byte[] LONG_ZERO = new byte[500];
  static {
    Arrays.fill(LONG_ZERO, (byte)0);
  }

  @Test
  public void testParseEnvelopedZMTP1() {
    ZMTPMessageParser parser = new ZMTPMessageParser(true, 1);
    ZMTPMessage m = parser.parse(buf(2, 1, 0x62, 2, 1, 0x63, 1, 1, 2, 0, 0x61));

    List<ZMTPFrame> envelope = m.getEnvelope();
    assertEquals(2, envelope.size());
    assertArrayEquals("b".getBytes(), envelope.get(0).getData());
    assertArrayEquals("c".getBytes(), envelope.get(1).getData());

    List<ZMTPFrame> content = m.getContent();
    assertEquals(1, content.size());

    assertArrayEquals("a".getBytes(), content.get(0).getData());


    ChannelBuffer buf = buf(1, 1, 0xff, 0, 0, 0, 0, 0, 0, 1, 0xf5, 0);
    buf.writeBytes(LONG_ZERO);
    m = parser.parse(buf);
    assertEquals(0, m.getEnvelope().size());

    content = m.getContent();
    assertEquals(1, content.size());
    assertArrayEquals(LONG_ZERO, content.get(0).getData());
  }

  @Test
  public void testParseEnvelopedZMTP2() {
    ZMTPMessageParser parser = new ZMTPMessageParser(true, 2);
    ZMTPMessage m = parser.parse(buf(1, 1, 0x62, 1, 1, 0x63, 1, 0, 0, 1, 0x61));
    List<ZMTPFrame> envelope = m.getEnvelope();
    assertEquals(2, envelope.size());
    assertArrayEquals("b".getBytes(), envelope.get(0).getData());
    assertArrayEquals("c".getBytes(), envelope.get(1).getData());

    List<ZMTPFrame> content = m.getContent();
    assertEquals(1, content.size());

    assertArrayEquals("a".getBytes(), content.get(0).getData());

    ChannelBuffer buf = buf(1, 0, 2, 0, 0, 0, 0, 0, 0, 1, 0xf4, 0);
    buf.writeBytes(LONG_ZERO);
    m = parser.parse(buf);
    assertEquals(0, m.getEnvelope().size());

    content = m.getContent();
    assertEquals(1, content.size());
    assertArrayEquals(LONG_ZERO, content.get(0).getData());


  }


}
