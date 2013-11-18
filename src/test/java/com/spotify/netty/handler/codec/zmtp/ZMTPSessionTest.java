package com.spotify.netty.handler.codec.zmtp;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests ZMTPSession.
 */
public class ZMTPSessionTest {

  @Test
  public void testGetProtocolVersion() {
    final ZMTPSession session = new ZMTPSession(ZMTPConnectionType.Addressed);
    Runnable sleepAndWriteVersion = new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(50);
          session.setProtocolVersion(1);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    new Thread(sleepAndWriteVersion).start();

    Assert.assertEquals("getProtocolVersion() should block", 1, session.getProtocolVersion());
  }

  @Test
  public void testGetProtocolVersionInterrupted() {
    final ZMTPSession session = new ZMTPSession(ZMTPConnectionType.Addressed);
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        // this will block
        session.getProtocolVersion();
      }
    });
    t.setDaemon(true);
    t.start();
    Assert.assertTrue(t.isAlive());
    t.interrupt();
    Assert.assertTrue(t.isAlive());
  }
}
