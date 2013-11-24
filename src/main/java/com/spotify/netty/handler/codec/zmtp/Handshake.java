package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Encapsulates the functionality required to properly do a ZMTP handshake. It holds state and
 * is expected to be used once per connection.
 */
abstract class Handshake {
  abstract ChannelBuffer inputOutput(final ChannelBuffer buffer);
  abstract ChannelBuffer onConnect();

  protected HandshakeListener listener;

  public void setListener(HandshakeListener listener) {
    this.listener = listener;
  }


}