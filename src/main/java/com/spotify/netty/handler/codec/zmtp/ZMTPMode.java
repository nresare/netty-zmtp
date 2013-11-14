package com.spotify.netty.handler.codec.zmtp;

/**
 * This enum represents the different connection modes that the ZMTPFramingDecoder can be
 * configured to use.
 */
public enum ZMTPMode {
  /**
   * The connection will use the ZMTP/1.0 framing protocol only.
   */
  ZMTP_10,
  /**
   * The connection should use the backwards interoperability mode of ZMTP/2.0 to detect
   * legacy nodes. Note that this a slight performance impact on connection setup (
   */
  ZMTP_20_INTEROP,
  /**
   * The connection will use the ZMTP/2.0 without backwards interoperability with legacy nodes.
   */
  ZMTP_20
}
