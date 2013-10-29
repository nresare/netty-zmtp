package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests Handshake
 */
public class HandshakeTest {
  ZMTPSession session;

  @Before
  public void setup() {
    session = new ZMTPSession(ZMTPConnectionType.Addressed, "foo". getBytes());
  }

  @Test
  public void testIsDone() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_10, session, ZMTPSocketType.PUB);
    Assert.assertFalse(h.isDone());
  }

  @Test
  public void testOnConnect() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_10, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0x04, 0x00, 0x66, 0x6f, 0x6f);

    h = new Handshake(
        ZMTPConnectionMode.ZMTP_10, new ZMTPSession(ZMTPConnectionType.Addressed),
        ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0x01, 0x00);

    h = new Handshake(ZMTPConnectionMode.ZMTP_20_INTEROP, session, ZMTPSocketType.SUB);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 4, 0x7f);

    h = new Handshake(ZMTPConnectionMode.ZMTP_20, session, ZMTPSocketType.REQ);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x03, 0x00, 3, 0x66, 0x6f, 0x6f);
  }

  @Test
  public void test1to1Handshake() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_10, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0x04, 0x00, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(buf(0x04, 0x00, 0x62, 0x61, 0x72)));
    Assert.assertArrayEquals("bar".getBytes(), session.getRemoteIdentity());
    Assert.assertTrue(h.isDone());
  }

  @Test
  public void test2InteropTo1Handshake() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_20_INTEROP, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    cmp(h.inputOutput(buf(0x04, 0x00, 0x62, 0x61, 0x72)), 0x66, 0x6f, 0x6f);
    Assert.assertEquals(1, session.getProtocolVersion());
    Assert.assertArrayEquals("bar".getBytes(), session.getRemoteIdentity());
    Assert.assertTrue(h.isDone());
  }

  @Test
  public void test2InteropTo2InteropHandshake() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_20_INTEROP, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    cmp(h.inputOutput(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f)),
        0x01, 0x02, 0x00, 0x03, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(buf(0x01, 0x01, 0x00, 0x03, 0x62, 0x61, 0x72)));
    Assert.assertEquals(2, session.getProtocolVersion());
    Assert.assertArrayEquals("bar".getBytes(), session.getRemoteIdentity());
    Assert.assertTrue(h.isDone());
  }

  @Test
  public void test2InteropTo2Handshake() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_20_INTEROP, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    cmp(h.inputOutput(buf(
        0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x01, 0x00, 0x03, 0x62, 0x61, 0x72)),
        0x01, 0x02, 0x00, 0x03, 0x66, 0x6f, 0x6f);
    Assert.assertEquals(2, session.getProtocolVersion());
    Assert.assertArrayEquals("bar".getBytes(), session.getRemoteIdentity());
    Assert.assertTrue(h.isDone());
  }

  @Test
  public void test2To2InteropHandshake() {
    Handshake h = new Handshake(ZMTPConnectionMode.ZMTP_20, session, ZMTPSocketType.PUB);
    cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x1, 0x2, 0, 0x3, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0x4, 0x7f)));
    Assert.assertNull(h.inputOutput(buf(0x1, 0x1, 0, 0x03, 0x62, 0x61, 0x72)));
    Assert.assertEquals(2, session.getProtocolVersion());
    Assert.assertArrayEquals("bar".getBytes(), session.getRemoteIdentity());
    Assert.assertTrue(h.isDone());
  }

  @Test
  public void testParseZMTP2Greeting() {
    Handshake hs = new Handshake(null, session, null);
    ChannelBuffer b = buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x02, 0x00, 0x01, 0x61);
    hs.parseZMTP2Greeting(b, true);
    Assert.assertArrayEquals("a".getBytes(), session.getRemoteIdentity());
  }

  @Test
  public void testReadZMTP1RemoteIdentity() {
    byte[] bs = Handshake.readZMTP1RemoteIdentity(buf(0x04, 0x00, 0x62, 0x61, 0x72));
    Assert.assertArrayEquals("bar".getBytes(), bs);

    // anonymous handshake
    bs = Handshake.readZMTP1RemoteIdentity(buf(0x01, 0x00));
    Assert.assertNull(bs);

  }

  @Test
  public void testTypeToConst() {
    Assert.assertEquals(8, Handshake.typeToConst(ZMTPSocketType.PUSH));
  }

  @Test
  public void testDetectProtocolVersion() {
    try {
      Handshake.detectProtocolVersion(ChannelBuffers.wrappedBuffer(new byte[0]));
      Assert.fail("Should have thown IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }
    try {
      Handshake.detectProtocolVersion(buf(0xff, 0, 0, 0));
      Assert.fail("Should have thown IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }

    Assert.assertEquals(1, ZMTPFramingDecoder.detectProtocolVersion(buf(0x07)));
    Assert.assertEquals(1, ZMTPFramingDecoder.detectProtocolVersion(
        buf(0xff,0,0,0,0,0,0,0,1,0)));

    Assert.assertEquals(2, ZMTPFramingDecoder.detectProtocolVersion(
        buf(0xff, 0, 0, 0, 0, 0, 0, 0, 1, 1)));

  }


  private void cmp(ChannelBuffer buf, int ... bytes) {
    cmp(buf, buf(bytes));
  }

  private ChannelBuffer buf(int ...bytes) {
    byte[] bs = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      bs[i] = (byte)bytes[i];
    }
    return ChannelBuffers.wrappedBuffer(bs);
  }

  /**
   * Compare ChannelBuffers left and right and raise an Assert.fail() if there are differences
   *
   * @param expected the ChannelBuffer you expect
   * @param actual the ChannelBuffer you actually got
   */
  private void cmp(ChannelBuffer expected, ChannelBuffer actual) {
    if (expected.readableBytes() != actual.readableBytes()) {
      Assert.fail(String.format("Expected same number of readable bytes in buffers (%d != %d)",
          expected.readableBytes(), actual.readableBytes()));
    }
    for (int i = 0; i < expected.readableBytes(); i++) {
      byte lb = expected.readByte();
      byte rb = actual.readByte();
      if (lb != rb) {

        Assert.fail(String.format(": (0x%02x != 0x%02x)", i, lb, rb));
      }
    }
  }
}
