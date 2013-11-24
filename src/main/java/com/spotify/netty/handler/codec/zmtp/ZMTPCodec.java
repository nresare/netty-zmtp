package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * A ZMTPCodec instance is a ChannelUpstreamHandler that, when placed in a ChannelPipeline,
 * will perform a ZMTP handshake with the connected peer and replace itself with the proper
 * pipeline components to encode and decode ZMTP frames.
 */
public class ZMTPCodec extends FrameDecoder {

  private final Handshake handshake;
  private ZMTPSession session;
  private final boolean enveloped;


  /**
   * Construct a ZMTPCodec with the specified mode, localIdentity and socketType
   * @param mode indicates which protocol versions should be supported.
   * @param localIdentity the local identity of this peer, or null if this is an anonymous peer
   * @param socketType the socket type of this peer
   */
  public ZMTPCodec(ZMTPMode mode, byte[] localIdentity, ZMTPSocketType socketType) {
    if (mode == ZMTPMode.ZMTP_10) {
      handshake = new Handshake10(localIdentity);
    } else {
      handshake = new Handshake20(socketType, localIdentity, mode == ZMTPMode.ZMTP_20_INTEROP);
    }
    enveloped = localIdentity != null;
    session = new ZMTPSession(enveloped ? ZMTPConnectionType.Addressed : ZMTPConnectionType.Broadcast);
  }

  @Override
  public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {

    handshake.setListener(new HandshakeListener() {
      @Override
      public void handshakeDone(int protocolVersion, byte[] remoteIdentity) {
        session.setRemoteIdentity(remoteIdentity);
        session.setProtocolVersion(protocolVersion);
        updatePipeline(ctx.getPipeline(), protocolVersion, session, enveloped);
        ctx.sendUpstream(e);
      }
    });

    Channel channel = e.getChannel();
    channel.write(handshake.onConnect());
    this.session.setChannel(e.getChannel());
  }

  private void updatePipeline(ChannelPipeline pipeline, int version,
                                     ZMTPSession session, boolean enveloped) {
    pipeline.replace(this, "zmtpDecoder", new ZMTPFramingDecoder(version, enveloped, session));
    pipeline.addAfter("zmtpDecoder", "zmtpEncoder", new ZMTPFramingEncoder(version, enveloped));
  }



  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
      throws Exception {
    try {
      buffer.markReaderIndex();
      ChannelBuffer toSend = handshake.inputOutput(buffer);
      while (toSend != null) {
        ctx.getChannel().write(toSend);
        toSend = handshake.inputOutput(buffer);
      }
    } catch (IndexOutOfBoundsException e) {
      buffer.resetReaderIndex();
    }
    return null;
  }

}
