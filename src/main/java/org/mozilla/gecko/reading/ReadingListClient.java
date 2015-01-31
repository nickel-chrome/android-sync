/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.reading;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.mozilla.gecko.fxa.FxAccountConstants;
import org.mozilla.gecko.reading.ReadingListClient.ReadingListStorageResponse;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.mozilla.gecko.sync.UnexpectedJSONException;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.BaseResource;
import org.mozilla.gecko.sync.net.BaseResourceDelegate;
import org.mozilla.gecko.sync.net.BasicAuthHeaderProvider;
import org.mozilla.gecko.sync.net.MozResponse;
import org.mozilla.gecko.sync.net.Resource;

import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.methods.HttpRequestBase;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;

/**
 * This client exposes an API for the reading list service, documented at
 * https://github.com/mozilla-services/readinglist/
 */
public class ReadingListClient {
  static final String LOG_TAG = ReadingListClient.class.getSimpleName();

  private final String serviceURL;
  private final AuthHeaderProvider auth;

  public static abstract class ReadingListResponse extends MozResponse {
    public ReadingListResponse(HttpResponse res) {
      super(res);
    }

    public long getLastModified() {
      return getLongHeader("Last-Modified");
    }
  }

  // For a single record.
  public static class ReadingListRecordResponse extends ReadingListResponse {
    public static final ResponseFactory<ReadingListRecordResponse> FACTORY = new ResponseFactory<ReadingListRecordResponse>() {
      @Override
      public ReadingListRecordResponse getResponse(HttpResponse r) {
        return new ReadingListRecordResponse(r);
      }
    };

    public ReadingListRecordResponse(HttpResponse res) {
      super(res);
    }

    public ReadingListRecord getRecord() throws IllegalStateException, NonObjectJSONException, IOException, ParseException {
      return new ReadingListRecord(jsonObjectBody());
    }
  }

  // For multiple records.
  public static class ReadingListStorageResponse extends ReadingListResponse {
    public static final ResponseFactory<ReadingListStorageResponse> FACTORY = new ResponseFactory<ReadingListStorageResponse>() {
      @Override
      public ReadingListStorageResponse getResponse(HttpResponse r) {
        return new ReadingListStorageResponse(r);
      }
    };

    public ReadingListStorageResponse(HttpResponse res) {
      super(res);
    }

    public Iterable<ReadingListRecord> getRecords() throws IllegalStateException, IOException, ParseException, UnexpectedJSONException {
      final ExtendedJSONObject body = jsonObjectBody();
      final JSONArray items = body.getArray("items");

      final int expected = getTotalRecords();
      final int actual = items.size();
      if (actual != expected) {
        throw new IllegalStateException("Unexpected number of records. Got " + actual + ", expected " + expected);
      }

      return new Iterable<ReadingListRecord>() {
        @Override
        public Iterator<ReadingListRecord> iterator() {
          return new Iterator<ReadingListRecord>() {
            int position = 0;

            @Override
            public boolean hasNext() {
              return position < actual;
            }

            @Override
            public ReadingListRecord next() {
              final Object o = items.get(position++);
              return new ReadingListRecord(new ExtendedJSONObject((JSONObject) o));
            }

            @Override
            public void remove() {
              throw new RuntimeException("Cannot remove from iterator.");
            }
          };
        }
      };
    }

    public int getTotalRecords() {
      return getIntegerHeader("Total-Records");
    }
  }

  private static interface ResponseFactory<T extends ReadingListResponse> {
    public T getResponse(HttpResponse r);
  }

  private abstract class ReadingListResourceDelegate<T extends ReadingListResponse> extends BaseResourceDelegate {
    private final ReadingListRecordDelegate delegate;
    private final ResponseFactory<T> factory;

    public ReadingListResourceDelegate(Resource resource, ReadingListRecordDelegate recordDelegate, ResponseFactory<T> factory) {
      super(resource);
      this.delegate = recordDelegate;
      this.factory = factory;
    }

    abstract void onSuccess(T response);
    abstract void onNotModified(T resp);
    abstract void onFailure(MozResponse response);

    @Override
    public void handleHttpResponse(HttpResponse response) {
      T resp = factory.getResponse(response);
      if (resp.wasSuccessful()) {
        onSuccess(resp);
      } else {
        if (resp.getStatusCode() == 304) {
          onNotModified(resp);
        } else {
          onFailure(resp);
        }
      }
    }

    @Override
    public void handleTransportException(GeneralSecurityException e) {
      delegate.onFailure(e);
    }

    @Override
    public void handleHttpProtocolException(ClientProtocolException e) {
      delegate.onFailure(e);
    }

    @Override
    public void handleHttpIOException(IOException e) {
      delegate.onFailure(e);
    }

    @Override
    public String getUserAgent() {
      return FxAccountConstants.USER_AGENT;    // TODO
    }

    @Override
    public AuthHeaderProvider getAuthHeaderProvider() {
      return auth;
    }

    @Override
    public void addHeaders(HttpRequestBase request, DefaultHttpClient client) {
    }
  }

  /**
   * Defines the parameters that can be added to a reading list fetch URI.
   */
  public static class FetchSpec {
    private final String queryString;
    private FetchSpec(final String q) {
      this.queryString = q;
    }

    public URI getURI(String server, String path) throws URISyntaxException {
      return new URI(server + path + "?" + queryString);
    }

    public class Builder {
      final StringBuilder b = new StringBuilder();
      boolean first = true;

      public FetchSpec build() {
        return new FetchSpec(b.toString());
      }

      private void ampersand() {
        if (first) {
          return;
        }
        b.append('&');
        first = false;
      }

      public void setUnread(boolean unread) {
        ampersand();
        b.append("unread=");
        b.append(unread);
      }

      public void setStatus(String status, boolean not) {
        ampersand();
        if (not) {
          b.append("not_");
        }
        b.append("status=");
        b.append(status);     // Escaping unnecessary for now.
      }

      private void qualifyAttribute(String qual, String attr) {
        ampersand();
        b.append(qual);
        b.append(attr);
        b.append('=');
      }

      public void setMinAttribute(String attr, int val) {
        qualifyAttribute("min_", attr);
        b.append(val);
      }

      public void setMaxAttribute(String attr, int val) {
        qualifyAttribute("max_", attr);
        b.append(val);
      }

      public void setNotAttribute(String attr, String val) {
        qualifyAttribute("not_", attr);
        b.append(val);
      }
    }
  }

  /**
   * Use a {@link BasicAuthHeaderProvider} for testing, and an FxA OAuth provider for the real service.
   */
  public ReadingListClient(final String serviceURL, final AuthHeaderProvider auth) {
    this.serviceURL = serviceURL;
    this.auth = auth;
  }

  public void getOne(final int id, final ReadingListRecordDelegate delegate, final long ifModifiedSince) throws URISyntaxException {
    final BaseResource r = new BaseResource(this.serviceURL + "/articles/" + id);
    r.delegate = new ReadingListResourceDelegate<ReadingListRecordResponse>(r, delegate, ReadingListRecordResponse.FACTORY) {
      @Override
      void onSuccess(ReadingListRecordResponse response) {
        final ReadingListRecord record;
        try {
          record = response.getRecord();
        } catch (Exception e) {
          delegate.onFailure(e);
          return;
        }

        delegate.onRecordReceived(record);
        delegate.onComplete();
      }

      @Override
      void onNotModified(ReadingListRecordResponse resp) {
        delegate.onComplete();
      }

      @Override
      void onFailure(MozResponse response) {
        delegate.onFailure(new RuntimeException("TODO"));
      }

      @Override
      public void addHeaders(HttpRequestBase request, DefaultHttpClient client) {
        if (ifModifiedSince != -1L) {
          // TODO: format?
          request.addHeader("If-Modified-Since", "" + ifModifiedSince);
        }
      }
    };
  }

  public void getAll(final FetchSpec spec, final ReadingListRecordDelegate delegate, final long ifModifiedSince) throws URISyntaxException {
    final BaseResource r = new BaseResource(spec.getURI(this.serviceURL, "/articles"));
    r.delegate = new ReadingListResourceDelegate<ReadingListStorageResponse>(r, delegate, ReadingListStorageResponse.FACTORY) {
      @Override
      void onSuccess(ReadingListStorageResponse response) {
        try {
          final Iterable<ReadingListRecord> records = response.getRecords();
          for (ReadingListRecord readingListRecord : records) {
            delegate.onRecordReceived(readingListRecord);
          }
        } catch (Exception e) {
          delegate.onFailure(e);
          return;
        }

        delegate.onComplete();
      }

      @Override
      void onNotModified(ReadingListStorageResponse resp) {
        delegate.onComplete();
      }

      @Override
      void onFailure(MozResponse response) {
        delegate.onFailure(new RuntimeException("TODO"));
      }

      @Override
      public void addHeaders(HttpRequestBase request, DefaultHttpClient client) {
        if (ifModifiedSince != -1L) {
          // TODO: format?
          request.addHeader("If-Modified-Since", "" + ifModifiedSince);
        }
      }
    };
  }
}
