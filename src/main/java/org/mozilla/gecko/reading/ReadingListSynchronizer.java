/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.reading;

import org.mozilla.gecko.background.common.PrefsBranch;

import android.content.ContentProviderClient;

/**
 * This class implements the multi-phase synchronizing approach described
 * at <https://github.com/mozilla-services/readinglist/wiki/Client-phases>.
 */
public class ReadingListSynchronizer {
  private final PrefsBranch prefs;
  private final ContentProviderClient client;

  public ReadingListSynchronizer(final PrefsBranch prefs, final ContentProviderClient client) {
    this.prefs = prefs;
    this.client = client;
  }

  public void uploadStatusChanges(final ReadingListSynchronizerDelegate delegate) {

  }

  public void uploadNewItems(final ReadingListSynchronizerDelegate delegate) {
  }

  public void uploadOutgoing(final ReadingListSynchronizerDelegate delegate) {
  }

  public void sync(final ReadingListSynchronizerDelegate delegate) {
  }
}
