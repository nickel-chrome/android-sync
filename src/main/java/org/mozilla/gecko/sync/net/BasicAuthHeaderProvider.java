/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.net;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EncodingUtils;

/**
 * An <code>AuthHeaderProvider</code> that returns an HTTP Basic auth header.
 */
public class BasicAuthHeaderProvider implements AuthHeaderProvider {
  protected final String credentials;

  /**
   * Constructor.
   *
   * @param credentials string in form "user:pass".
   */
  public BasicAuthHeaderProvider(String credentials) {
    this.credentials = credentials;
  }

  /**
   * Constructor.
   *
   * @param user username.
   * @param pass password.
   */
  public BasicAuthHeaderProvider(String user, String pass) {
    this(user + ":" + pass);
  }

  /**
   * Return a Header object representing an Authentication header for HTTP
   * Basic.
   */
  @Override
  public Header getAuthHeader(HttpUriRequest request, BasicHttpContext context, HttpClient client) {	  
    return new BasicHeader("Authorization", "Basic " + Base64.encodeBase64String(EncodingUtils.getBytes(credentials, "UTF-8")));
  }
}
