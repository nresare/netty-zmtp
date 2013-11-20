package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import org.junit.Assert;
import org.junit.Test;

import static com.spotify.netty.handler.codec.zmtp.TestUtil.cmp;

/**
 * These tests has a full pipeline setup.
 */
public class PipelineTests {

  /**
   * First let's just exercise the PipelineTester a bit.
   */
  @Test
  public void testPipelineTester() {
    final ChannelBuffer buf = ChannelBuffers.wrappedBuffer("Hello, world".getBytes());
    ChannelPipeline pipeline = Channels.pipeline(new SimpleChannelUpstreamHandler() {

      @Override
      public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
          throws Exception {
        e.getChannel().write(buf);
        ctx.sendUpstream(e);
      }
    });
    PipelineTester pipelineTester = new PipelineTester(pipeline);
    Assert.assertEquals(buf, pipelineTester.readClient());

    ChannelBuffer another = ChannelBuffers.wrappedBuffer("foo".getBytes());
    pipelineTester.writeClient(TestUtil.clone(another));

    cmp(another,(ChannelBuffer) pipelineTester.readServer());

    another = ChannelBuffers.wrappedBuffer("bar".getBytes());
    pipelineTester.writeServer(TestUtil.clone(another));
    cmp(another,pipelineTester.readClient());
  }


  /*
  public void testPipelineModification() throws Exception {
    ZMTPSession session = new ZMTPSession(ZMTPConnectionType.Addressed, bytes(0x61));

    TestUtil.TestHandler tuh = new TestUtil.TestHandler();

    ChannelPipeline pipeline = Channels.pipeline(
        new ZMTPFramingDecoder(session, ZMTPMode.ZMTP_20_INTEROP, ZMTPSocketType.REQ),
        new ZMTPFramingEncoder(session),
        tuh);


    InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 12345);
    ChannelStateEvent connectEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED,
        remoteAddress);
    pipeline.sendUpstream(connectEvent);

    checkBufferReceived(channel, buf(0xff,0,0,0,0,0,0,0,2,0x7f));

    ChannelBuffer cb = buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 1, 4, 0, 1, 0x63);
    pipeline.sendUpstream(new UpstreamMessageEvent(channel, cb, remoteAddress));

    checkBufferReceived(channel, buf(1, 3, 0, 1, 0x61));
    assertEquals(tuh.getLatestConnectEvent(), connectEvent);

    // try sending an enveloped message and verify that it gets through

    cb = buf(1, 1, 0x65, 1, 0, 0, 1, 0x62);
    pipeline.sendUpstream(new UpstreamMessageEvent(channel, cb, remoteAddress));


    ZMTPIncomingMessage m = tuh.getLatestIncomingMessage();
    List<ZMTPFrame> envelope = m.getMessage().getEnvelope();
    Assert.assertEquals(1, envelope.size());
    Assert.assertArrayEquals(bytes(0x65), envelope.get(0).getData());

    List<ZMTPFrame> body = m.getMessage().getContent();
    Assert.assertEquals(1, body.size());
    Assert.assertArrayEquals(bytes(0x62), body.get(0).getData());

    // send a message and verify that it got correctly encoded
    tuh.sendMessage(new ZMTPMessage(
        asList(ZMTPFrame.create("e0")),
        asList(ZMTPFrame.create("c0"))));

    checkBufferReceived(channel, buf(1, 2, 0x65, 0x30, 1, 0, 0, 2, 0x63, 0x30));
  }



  private InOrder checkBufferReceivedOrder = null;

  private void checkBufferReceived(final Channel mockedChannel, ChannelBuffer expected) {
    if (checkBufferReceivedOrder == null) {
      checkBufferReceivedOrder = inOrder(mockedChannel);
    }
    ArgumentCaptor<ChannelBuffer> bufferCaptor = ArgumentCaptor.forClass(ChannelBuffer.class);
    checkBufferReceivedOrder.verify(mockedChannel).write(bufferCaptor.capture());
    cmp(expected,bufferCaptor.getValue());
  }

  */


}
