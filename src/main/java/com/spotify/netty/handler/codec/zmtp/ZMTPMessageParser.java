/*
 * Copyright (c) 2012-2013 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.spotify.netty.handler.codec.zmtp;

import org.jboss.netty.buffer.ChannelBuffer;

import java.util.ArrayList;
import java.util.List;

import static com.spotify.netty.handler.codec.zmtp.ZMTPUtils.MORE_FLAG;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.jboss.netty.buffer.ChannelBuffers.swapLong;

/**
 * Decodes ZMTP messages from a channel buffer, reading and accumulating frame by frame, keeping
 * state and updating buffer reader indices as necessary.
 */
public class ZMTPMessageParser {

  private static final byte LONG_FLAG = 0x02;
  private final boolean enveloped;
  private final int version;

  private List<ZMTPFrame> envelope = new ArrayList<ZMTPFrame>();
  private List<ZMTPFrame> content = new ArrayList<ZMTPFrame>();

  private List<ZMTPFrame> part;
  private boolean hasMore;

  public ZMTPMessageParser(final boolean enveloped, final int version) {
    this.enveloped = enveloped;
    this.version = version;
    reset();
  }

  private void reset() {
    envelope = new ArrayList<ZMTPFrame>(4);
    content = new ArrayList<ZMTPFrame>();
    part = enveloped ? envelope : content;
    hasMore = true;
  }

  /**
   * Parses as many whole frames from the buffer as possible, until the final frame is encountered.
   * If the message was completed, it returns the frames of the message. Otherwise it returns null
   * to indicate that more data is needed.
   *
   * @param buffer Buffer with data
   * @return A {@link ZMTPMessage} if it was completely parsed, otherwise null.
   */
  public ZMTPMessage parse(final ChannelBuffer buffer) {
    while (buffer.readableBytes() > 0) {
      buffer.markReaderIndex();

      final ZMTPFrame frame = parseFrame(buffer);
      if (frame == null) {
        buffer.resetReaderIndex();
        break;
      }

      // Skip the delimiter
      if (!frame.hasData() && part == envelope) {
        part = content;
        continue;
      }

      part.add(frame);

      if (!hasMore) {
        final ZMTPMessage message = new ZMTPMessage(envelope, content);
        reset();
        return message;
      }
    }

    return null;
  }

  /**
   * Attempt to parse a ZMTP frame.
   *
   * @param buffer Buffer with data.
   * @return A frame if sucessfull, null if more data is needed.
   */
  public ZMTPFrame parseFrame(final ChannelBuffer buffer) {
    // Try to parse a ZMTP frame
    try {
      long len = version == 1 ? parseZMTP1FrameHeader(buffer) : parseZMTP2FrameHeader(buffer);
      if (len == -1) {
        throw new ZMTPException("Received frame with zero length");
      }

      if (len > Integer.MAX_VALUE) {
        throw new ZMTPException("Received too large frame: " + len);
      }

      if ((int) len > buffer.readableBytes()) {
        // Wait for more data to decode
        return null;
      }


      return ZMTPFrame.read(buffer, (int) len);
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  private long parseZMTP2FrameHeader(ChannelBuffer buffer) {
    int flags = buffer.readByte();
    hasMore = (flags & MORE_FLAG) == MORE_FLAG;
    if ((flags & LONG_FLAG) != LONG_FLAG) {
      return buffer.readByte() & 0xff;
    }
    if (buffer.order() == BIG_ENDIAN) {
      return buffer.readLong();
    } else {
      return swapLong(buffer.readLong());
    }
  }

  private long parseZMTP1FrameHeader(final ChannelBuffer buffer) {
    long len = ZMTPUtils.decodeLength(buffer);
    // Read if we have more frames from flag byte
    hasMore = (buffer.readByte() & MORE_FLAG) == MORE_FLAG;
    return len - 1;
  }
}
