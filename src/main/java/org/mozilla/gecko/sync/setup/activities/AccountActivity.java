/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.setup.activities;

import java.util.Locale;

import org.mozilla.gecko.R;
import org.mozilla.gecko.sync.Logger;
import org.mozilla.gecko.sync.setup.Constants;
import org.mozilla.gecko.sync.setup.InvalidSyncKeyException;
import org.mozilla.gecko.sync.setup.SyncAccounts;

import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Toast;

public class AccountActivity extends AccountAuthenticatorActivity {
  private final static String LOG_TAG        = "AccountActivity";

  private AccountManager      mAccountManager;
  private Context             mContext;
  private String              username;
  private String              password;
  private String              key;
  private String              server = Constants.AUTH_NODE_DEFAULT;

  // UI elements.
  private EditText            serverInput;
  private EditText            usernameInput;
  private EditText            passwordInput;
  private EditText            synckeyInput;
  private CheckBox            serverCheckbox;
  private Button              connectButton;
  private Button              cancelButton;

  private AccountAuthenticator accountAuthenticator;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.SyncTheme);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sync_account);
    mContext = getApplicationContext();
    Logger.debug(LOG_TAG, "AccountManager.get(" + mContext + ")");
    mAccountManager = AccountManager.get(mContext);

    // Find UI elements.
    usernameInput = (EditText) findViewById(R.id.usernameInput);
    passwordInput = (EditText) findViewById(R.id.passwordInput);
    synckeyInput = (EditText) findViewById(R.id.keyInput);
    serverInput = (EditText) findViewById(R.id.serverInput);

    TextWatcher inputValidator = makeInputValidator();

    usernameInput.addTextChangedListener(inputValidator);
    passwordInput.addTextChangedListener(inputValidator);
    synckeyInput.addTextChangedListener(inputValidator);
    serverInput.addTextChangedListener(inputValidator);

    connectButton = (Button) findViewById(R.id.accountConnectButton);
    cancelButton = (Button) findViewById(R.id.accountCancelButton);
    serverCheckbox = (CheckBox) findViewById(R.id.checkbox_server);

    serverCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Logger.info(LOG_TAG, "Toggling checkbox: " + isChecked);
        // Hack for pre-3.0 Android: can enter text into disabled EditText.
        if (!isChecked) { // Clear server input.
          serverInput.setVisibility(View.GONE);
          serverInput.setText("");
        } else {
          serverInput.setVisibility(View.VISIBLE);
          serverInput.setEnabled(true);
        }
        // Activate connectButton if necessary.
        activateView(connectButton, validateInputs());
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    clearCredentials();
    usernameInput.requestFocus();
    enableCredEntry(true);
    cancelButton.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View v) {
        cancelClickHandler(v);
      }

    });
  }
  public void cancelClickHandler(View target) {
    finish();
  }

  public void cancelConnectHandler(View target) {
    if (accountAuthenticator != null) {
      accountAuthenticator.isCanceled = true;
      accountAuthenticator = null;
    }
    enableCredEntry(true);
    activateView(connectButton, true);
    clearCredentials();
    usernameInput.requestFocus();
  }

  private void clearCredentials() {
    // Only clear password. Re-typing the sync key or email is annoying.
    passwordInput.setText("");
  }

  /*
   * Get credentials on "Connect" and write to AccountManager, where it can be
   * accessed by Fennec and Sync Service.
   */
  public void connectClickHandler(View target) {
    Logger.debug(LOG_TAG, "connectClickHandler for view " + target);
    enableCredEntry(false);
    // Validate sync key format.
    try {
      key = ActivityUtils.validateSyncKey(synckeyInput.getText().toString());
    } catch (InvalidSyncKeyException e) {
      enableCredEntry(true);
      // Toast: invalid sync key format.
      Toast toast = Toast.makeText(mContext, R.string.sync_new_recoverykey_status_incorrect, Toast.LENGTH_LONG);
      toast.show();
      return;
    }
    username = usernameInput.getText().toString().toLowerCase(Locale.US);
    password = passwordInput.getText().toString();
    key      = synckeyInput.getText().toString();

    if (serverCheckbox.isChecked()) {
      String userServer = serverInput.getText().toString();
      if (userServer != null) {
        userServer = userServer.trim();
        if (userServer.length() != 0) {
          if (!userServer.startsWith("https://") &&
              !userServer.startsWith("http://")) {
            // Assume HTTPS if not specified.
            userServer = "https://" + userServer;
          }
          server = userServer;
        }
      }
    }
    enableCredEntry(false);
    activateView(connectButton, false);
    cancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelConnectHandler(v);
        // Set cancel click handler to leave account setup.
        cancelButton.setOnClickListener(new OnClickListener() {
          public void onClick(View v) {
            cancelClickHandler(v);
          }
        });
      }
    });

    accountAuthenticator = new AccountAuthenticator(this);
    accountAuthenticator.authenticate(server, username, password);
  }

  /* Helper UI functions */
  private void enableCredEntry(boolean toEnable) {
    usernameInput.setEnabled(toEnable);
    passwordInput.setEnabled(toEnable);
    synckeyInput.setEnabled(toEnable);
    serverCheckbox.setEnabled(toEnable);
    if (!toEnable) {
      serverInput.setEnabled(toEnable);
    } else {
      serverInput.setEnabled(serverCheckbox.isChecked());
    }
  }

  private TextWatcher makeInputValidator() {
    return new TextWatcher() {

      @Override
      public void afterTextChanged(Editable s) {
        activateView(connectButton, validateInputs());
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
          int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    };
  }

  private boolean validateInputs() {
    if (usernameInput.length() == 0 ||
        passwordInput.length() == 0 ||
        synckeyInput.length() == 0  ||
        (serverCheckbox.isChecked() &&
         serverInput.length() == 0)) {
      return false;
    }
    return true;
  }

  /*
   * Callback that handles auth based on success/failure
   */
  public void authCallback(boolean isSuccess) {
    if (isSuccess) {
      // Successful authentication. Create and add account to AccountManager.
      // Note: Sync key may be incorrect!
      Logger.debug(LOG_TAG, "Using account manager " + mAccountManager);
      final Intent intent = SyncAccounts.createAccount(mContext, mAccountManager,
                                       username,
                                        key, password, server);
      if (intent != null) {
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            authSuccess();
          }
        });
        return;
      }
      // TODO: Display error to user, probably will require new strings.
      // For now, fall through and display default failure screen.
    }
    Logger.debug(LOG_TAG, "Authentication failure.");
    // Display default failure screen to user.
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        authFailure();
      }
    });
  }

  private void authFailure() {
  /**
   * Feedback to user of account setup failure.
   */
    enableCredEntry(true);
    Intent intent = new Intent(mContext, SetupFailureActivity.class);
    intent.setFlags(Constants.FLAG_ACTIVITY_REORDER_TO_FRONT_NO_ANIMATION);
    startActivity(intent);
  }

  /**
   * Feedback to user of account setup success.
   */
  public void authSuccess() {
    // Display feedback of successful account setup.
    Intent intent = new Intent(mContext, SetupSuccessActivity.class);
    intent.setFlags(Constants.FLAG_ACTIVITY_REORDER_TO_FRONT_NO_ANIMATION);
    startActivity(intent);
    finish();
  }

  private void activateView(View view, boolean toActivate) {
    view.setEnabled(toActivate);
    view.setClickable(toActivate);
  }
}
