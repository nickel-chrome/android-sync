/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.reading.test;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.mozilla.gecko.reading.FetchSpec;
import org.mozilla.gecko.reading.ReadingListClient;
import org.mozilla.gecko.reading.ReadingListClient.ReadingListRecordResponse;
import org.mozilla.gecko.reading.ReadingListClient.ReadingListResponse;
import org.mozilla.gecko.reading.ReadingListRecord;
import org.mozilla.gecko.reading.ReadingListRecordDelegate;
import org.mozilla.gecko.reading.ReadingListRecordUploadDelegate;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.BasicAuthHeaderProvider;
import org.mozilla.gecko.sync.net.MozResponse;

public class TestReadingListClient {
  public static final class TestRecordDelegate implements ReadingListRecordDelegate {
    private final CountDownLatch latch;
    public volatile Exception error;
    public volatile MozResponse mozResponse;
    public volatile ReadingListResponse response;
    public volatile ReadingListRecord record;
    public volatile boolean called;

    public TestRecordDelegate(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onRecordReceived(ReadingListRecord record) {
      this.called = true;
      this.record = record;
    }

    @Override
    public void onFailure(Exception error) {
      this.called = true;
      this.error = error;
      latch.countDown();
    }

    @Override
    public void onFailure(MozResponse response) {
      this.called = true;
      this.mozResponse = response;
      latch.countDown();
    }

    @Override
    public void onComplete(ReadingListResponse response) {
      this.called = true;
      this.response = response;
      latch.countDown();
    }
  }

  public static class TestRecordUploadDelegate implements ReadingListRecordUploadDelegate {
    private final CountDownLatch latch;
    public volatile Exception error;
    public volatile MozResponse mozResponse;
    public volatile ReadingListResponse response;
    public volatile ReadingListRecord record;

    public TestRecordUploadDelegate(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onInvalidUpload(ReadingListResponse response) {
      this.response = response;
      latch.countDown();
    }

    @Override
    public void onConflict(ReadingListResponse response) {
      this.response = response;
      latch.countDown();
    }

    @Override
    public void onSuccess(ReadingListRecordResponse response,
                          ReadingListRecord record) {
      this.response = response;
      this.record = record;
      latch.countDown();
    }

    @Override
    public void onBadRequest(MozResponse response) {
      this.mozResponse = response;
      latch.countDown();
    }

    @Override
    public void onFailure(Exception ex) {
      this.error = ex;
      latch.countDown();
    }

    @Override
    public void onFailure(MozResponse response) {
      this.mozResponse = response;
      latch.countDown();
    }
  }

  private static final String DEFAULT_SERVICE_URI = "https://readinglist.dev.mozaws.net/v0/";

  @Test
  public final void test() throws URISyntaxException, InterruptedException, UnsupportedEncodingException {
    CountDownLatch latch;

    final AuthHeaderProvider auth = new BasicAuthHeaderProvider("rnewmantest", "nopassword");
    final ReadingListClient client = new ReadingListClient(new URI(DEFAULT_SERVICE_URI), auth);
    final long ifModifiedSince = -1L;
    final FetchSpec spec = new FetchSpec.Builder().setStatus("0", false).build();

    latch = new CountDownLatch(1);
    final TestRecordDelegate delegate = new TestRecordDelegate(latch);

    client.getAll(spec, delegate, ifModifiedSince);
    latch.await(10000, TimeUnit.MILLISECONDS);

    Assert.assertTrue(delegate.called);
    Assert.assertNull(delegate.error);
    Assert.assertNull(delegate.mozResponse);
    Assert.assertNotNull(delegate.response);

    final long lastServerTimestamp = delegate.response.getLastModified();
    Assert.assertTrue(lastServerTimestamp > -1L);

    // Upload a record.
    latch = new CountDownLatch(1);
    final TestRecordUploadDelegate uploadDelegate = new TestRecordUploadDelegate(latch);
    final ReadingListRecord record = new ReadingListRecord("http://reddit.com", "Reddit", "Test Device");
    Assert.assertEquals(record.url, "http://reddit.com");
    Assert.assertEquals(record.title, "Reddit");
    Assert.assertEquals(record.addedBy, "Test Device");

    client.add(record, uploadDelegate);
    latch.await(5000, TimeUnit.MILLISECONDS);

    Assert.assertNull(uploadDelegate.error);
    Assert.assertNull(uploadDelegate.mozResponse);
    Assert.assertNotNull(uploadDelegate.response);
    Assert.assertNotNull(uploadDelegate.record);
    Assert.assertEquals(record.url, uploadDelegate.record.url);
    Assert.assertEquals(record.title, uploadDelegate.record.title);
    Assert.assertEquals(record.addedBy, uploadDelegate.record.addedBy);
    Assert.assertTrue(uploadDelegate.record.id != -1);
    Assert.assertTrue(lastServerTimestamp < uploadDelegate.record.lastModified);

    // Now fetch from our last timestamp.
  }
}
