/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.net;

import java.security.GeneralSecurityException;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.BasicHttpContext;


/**
 * An <code>AuthHeaderProvider</code> generates HTTP Authorization headers for
 * HTTP requests.
 */
public interface AuthHeaderProvider {
  /**
   * Generate an HTTP Authorization header.
   *
   * @param request HTTP request.
   * @param context HTTP context.
   * @param client HTTP client.
   * @return HTTP Authorization header.
   * @throws GeneralSecurityException usually wrapping a more specific exception.
   */
  Header getAuthHeader(HttpUriRequest request, BasicHttpContext context, HttpClient client)
    throws GeneralSecurityException;
}
