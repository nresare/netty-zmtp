package com.spotify.netty.handler.codec.zmtp;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests Handshake
 */
public class HandshakeTest {
  byte[] FOO = "foo".getBytes();
  byte[] BAR = "bar".getBytes();

  @Mock Handshake.HandshakeListener handshakeListener;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testIsDone() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_10, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    verifyNoMoreInteractions(handshakeListener);
  }

  @Test
  public void testOnConnect() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_10, ZMTPSocketType.PUB, FOO);
    TestUtil.cmp(h.onConnect(), 0x04, 0x00, 0x66, 0x6f, 0x6f);

    h = new Handshake(ZMTPMode.ZMTP_10, ZMTPSocketType.PUB, new byte[0]);
    TestUtil.cmp(h.onConnect(), 0x01, 0x00);

    h = new Handshake(ZMTPMode.ZMTP_20_INTEROP, ZMTPSocketType.SUB, FOO);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 4, 0x7f);

    h = new Handshake(ZMTPMode.ZMTP_20, ZMTPSocketType.REQ, FOO);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x03, 0x00, 3, 0x66, 0x6f, 0x6f);
  }

  @Test
  public void test1to1Handshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_10, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0x04, 0x00, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(TestUtil.buf(0x04, 0x00, 0x62, 0x61, 0x72)));
    verify(handshakeListener).handshakeDone(1, BAR);
  }

  @Test
  public void test2InteropTo1Handshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20_INTEROP, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    TestUtil.cmp(h.inputOutput(TestUtil.buf(0x04, 0x00, 0x62, 0x61, 0x72)), 0x66, 0x6f, 0x6f);
    verify(handshakeListener).handshakeDone(1, BAR);
  }

  @Test
  public void test2InteropTo2InteropHandshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20_INTEROP, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    TestUtil.cmp(h.inputOutput(TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f)),
        0x01, 0x02, 0x00, 0x03, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(TestUtil.buf(0x01, 0x01, 0x00, 0x03, 0x62, 0x61, 0x72)));
    verify(handshakeListener).handshakeDone(2, BAR);
  }

  @Test
  public void test2InteropTo2Handshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20_INTEROP, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x7f);
    TestUtil.cmp(h.inputOutput(TestUtil.buf(
        0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x01, 0x00, 0x03, 0x62, 0x61, 0x72)),
        0x01, 0x02, 0x00, 0x03, 0x66, 0x6f, 0x6f);
    verify(handshakeListener).handshakeDone(2, BAR);
  }

  @Test
  public void test2To2InteropHandshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x1, 0x2, 0, 0x3, 0x66, 0x6f, 0x6f);

    try {
      h.inputOutput(TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0x4, 0x7f));
      Assert.fail("not enough data in greeting (because compat mode) shuld have thrown exception");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
    Assert.assertNull(h.inputOutput(TestUtil.buf(
        0xff, 0, 0, 0, 0, 0, 0, 0, 0x4, 0x7f, 0x1, 0x1, 0, 0x03, 0x62, 0x61, 0x72)));
    verify(handshakeListener).handshakeDone(2, BAR);
  }

  @Test
  public void test2To2Handshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20, ZMTPSocketType.PUB, FOO);
    h.setListener(handshakeListener);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x1, 0x2, 0, 0x3, 0x66, 0x6f, 0x6f);
    Assert.assertNull(h.inputOutput(TestUtil.buf(
        0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x1, 0x1, 0, 0x03, 0x62, 0x61, 0x72)));
    verify(handshakeListener).handshakeDone(2, BAR);
  }

  @Test
  public void test2To1Handshake() {
    Handshake h = new Handshake(ZMTPMode.ZMTP_20, ZMTPSocketType.PUB, FOO);
    TestUtil.cmp(h.onConnect(), 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x1, 0x2, 0, 0x3, 0x66, 0x6f, 0x6f);
    try {
      Assert.assertNull(h.inputOutput(TestUtil.buf(0x04, 0, 0x62, 0x61, 0x72)));
      Assert.fail("An ZMTP/1 greeting is invalid in plain ZMTP/2. Should have thrown exception");
    } catch (ZMTPException e) {
      // pass
    }
  }



  @Test
  public void testParseZMTP2Greeting() {
    Handshake hs = new Handshake(null, null, FOO);
    ChannelBuffer b = TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x02, 0x00, 0x01, 0x61);
    Assert.assertArrayEquals("a".getBytes(), hs.parseZMTP2Greeting(b, true));
  }

  @Test
  public void testReadZMTP1RemoteIdentity() {
    byte[] bs = Handshake.readZMTP1RemoteIdentity(TestUtil.buf(0x04, 0x00, 0x62, 0x61, 0x72));
    Assert.assertArrayEquals(BAR, bs);

    // anonymous handshake
    bs = Handshake.readZMTP1RemoteIdentity(TestUtil.buf(0x01, 0x00));
    Assert.assertNull(bs);

  }

  @Test
  public void testTypeToConst() {
    Assert.assertEquals(8, ZMTPSocketType.PUSH.ordinal());
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
      Handshake.detectProtocolVersion(TestUtil.buf(0xff, 0, 0, 0));
      Assert.fail("Should have thown IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // ignore
    }

    Assert.assertEquals(1, Handshake.detectProtocolVersion(TestUtil.buf(0x07)));
    Assert.assertEquals(1, Handshake.detectProtocolVersion(TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 1, 0)));

    Assert.assertEquals(2, Handshake.detectProtocolVersion(TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 1, 1)));

  }

  @Test
  public void testParseZMTP2GreetingMalformed() {
    try {
      ChannelBuffer b = TestUtil.buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 0x01, 0x02, 0xf0, 0x01, 0x61);
      new Handshake(null, null, FOO).parseZMTP2Greeting(b, true);
      Assert.fail("13th byte is not 0x00, should throw exception");
    } catch (ZMTPException e) {
      // pass
    }
  }

}
