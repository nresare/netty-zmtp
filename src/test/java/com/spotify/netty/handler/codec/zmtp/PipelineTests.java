package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static com.spotify.netty.handler.codec.zmtp.TestUtil.buf;
import static com.spotify.netty.handler.codec.zmtp.TestUtil.bytes;
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

  @Test
  public void testZMTPPipeline() {
    ChannelPipeline p = Channels.pipeline(new ZMTP20Codec("foo".getBytes(), ZMTPSocketType.REQ,
                                                          true));

    PipelineTester pt = new PipelineTester(p);
    cmp(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 4, 0x7f), pt.readClient());
    pt.writeClient(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 1, 4, 0, 1, 0x63));
    cmp(buf(1, 3, 0, 3, 0x66, 0x6f, 0x6f), pt.readClient());

    pt.writeClient(buf(1, 1, 0x65, 1, 0, 0, 1, 0x62));
    ZMTPIncomingMessage m = (ZMTPIncomingMessage)pt.readServer();

    List<ZMTPFrame> envelope = m.getMessage().getEnvelope();
    Assert.assertEquals(1, envelope.size());
    Assert.assertArrayEquals(bytes(0x65), envelope.get(0).getData());

    List<ZMTPFrame> body = m.getMessage().getContent();
    Assert.assertEquals(1, body.size());
    Assert.assertArrayEquals(bytes(0x62), body.get(0).getData());


  }

  @Test
  public void testZMTPPipelineFragmented() {
    ChannelPipeline p = Channels.pipeline(new ZMTP20Codec(
        "foo".getBytes(), ZMTPSocketType.REQ, true));

    PipelineTester pt = new PipelineTester(p);
    cmp(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 4, 0x7f), pt.readClient());
    pt.writeClient(buf(0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0x7f, 1, 4, 0, 1, 0x63, 1, 1, 0x65, 1));
    cmp(buf(1, 3, 0, 3, 0x66, 0x6f, 0x6f), pt.readClient());

    pt.writeClient(buf(0, 0, 1, 0x62));
    ZMTPIncomingMessage m = (ZMTPIncomingMessage)pt.readServer();

    List<ZMTPFrame> envelope = m.getMessage().getEnvelope();
    Assert.assertEquals(1, envelope.size());
    Assert.assertArrayEquals(bytes(0x65), envelope.get(0).getData());

    List<ZMTPFrame> body = m.getMessage().getContent();
    Assert.assertEquals(1, body.size());
    Assert.assertArrayEquals(bytes(0x62), body.get(0).getData());


  }
}
