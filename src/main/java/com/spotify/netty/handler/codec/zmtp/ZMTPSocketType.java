package com.spotify.netty.handler.codec.zmtp;

/**
 * Enumerates the different socket types, used to make sure that connecting
 * both peers in a socket pair has compatible socket types.
 *
 */
public enum ZMTPSocketType {
  PAIR,
  SUB,
  PUB,
  REQ,
  REP,
  DEALER,
  ROUTER,
  PULL,
  PUSH
}
