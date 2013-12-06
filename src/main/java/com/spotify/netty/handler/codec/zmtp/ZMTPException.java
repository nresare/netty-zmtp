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

/**
 * Exception base for all netty ZMTP classes
 */
public class ZMTPException extends Exception {

  private static final long serialVersionUID = -7283717734572821817L;

  public ZMTPException() {
  }

  public ZMTPException(final String message) {
    super(message);
  }

  public ZMTPException(final String message, final Throwable exception) {
    super(message, exception);
  }
}
