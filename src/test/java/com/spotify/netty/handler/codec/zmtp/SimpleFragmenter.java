package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A fragmenter that, instead of outputting all possible fragmentation variants
 * (for a 23 byte message, that translates to 4.1m variants) returns one fragmentation
 * that fragments each byte individually and all variatns where the message is fragmented
 * into two parts.
 */
class SimpleFragmenter implements Iterable<List<ChannelBuffer>> {
  private List<List<ChannelBuffer>> l;

  private SimpleFragmenter(ChannelBuffer buffer) {
    l = new ArrayList<List<ChannelBuffer>>();
    l.add(maximallyFragmented(buffer));
    for (int i = 1; i < buffer.readableBytes() - 1; i++) {
      l.add(split(buffer, i));
    }
  }

  private List<ChannelBuffer> maximallyFragmented(ChannelBuffer buffer) {
    List<ChannelBuffer> l = new ArrayList<ChannelBuffer>();
    for (int i = 0; i < buffer.readableBytes(); i++) {
      l.add(buffer.slice(i, 1));
    }
    return l;
  }

  private List<ChannelBuffer> split(ChannelBuffer buffer, int idx) {
    List<ChannelBuffer> l = new ArrayList<ChannelBuffer>();
    l.add(buffer.slice(0, idx));
    l.add(buffer.slice(idx, buffer.readableBytes() - idx));
    return l;
  }

  public static Iterable<List<ChannelBuffer>> generator(final ChannelBuffer buffer) {
    return new SimpleFragmenter(buffer);
  }

  @Override
  public Iterator<List<ChannelBuffer>> iterator() {
    return l.iterator();
  }


}
