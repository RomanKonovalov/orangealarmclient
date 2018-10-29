package com.romif.securityalarm.androidclient.feature.service;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;

import java.util.concurrent.CompletableFuture;

public class SecurityService {

    private static final String TAG = "SecurityService";

    private static CredentialRequest request = new CredentialRequest.Builder()
            .setPasswordLoginSupported(true)
            .build();

    public static CompletableFuture<Credential> getCredential(Context context) {
        CompletableFuture<Credential> future = new CompletableFuture<>();

        Credentials.getClient(context).request(request).addOnCompleteListener(
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Credential received.");
                        future.complete(task.getResult().getCredential());
                        return;
                    }
                    Exception e = task.getException();
                    if (e instanceof ResolvableApiException) {
                        Log.w(TAG, "Credential resolving required.");
                        future.completeExceptionally(e);
                    } else if (e instanceof ApiException) {
                        Log.e(TAG, "Unsuccessful credential request.", e);
                        future.completeExceptionally(e);
                    } else {
                        Log.e(TAG, "Unsuccessful credential request. Unknown error.", e);
                        future.completeExceptionally(e);
                    }
                });

        return future;
    }

    public static CompletableFuture<Void> logout(Context context) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Credentials.getClient(context).disableAutoSignIn().addOnCompleteListener(
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Logout successful.");
                        future.complete(null);
                        return;
                    }
                    Exception e = task.getException();
                    if (e != null) {
                        Log.e(TAG, "Error while disableAutoSignIn", e);
                        future.completeExceptionally(e);
                    }
                }
        );
        return future;
    }

    public static class SecurityServiceException extends RuntimeException {

        public enum ExceptionType {

        }

    }
}
