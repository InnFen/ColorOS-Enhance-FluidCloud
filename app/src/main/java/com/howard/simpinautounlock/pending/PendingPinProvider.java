package com.howard.simpinautounlock.pending;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileNotFoundException;

public final class PendingPinProvider extends ContentProvider {
    private static final String LOG_TAG = "SIMPinAutoUnlock";
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String READ_PRIVILEGED_PHONE_STATE_PERMISSION =
            "android.permission.READ_PRIVILEGED_PHONE_STATE";
    private static final String MODIFY_PHONE_STATE_PERMISSION =
            "android.permission.MODIFY_PHONE_STATE";
    private static final int UNRESOLVED_UID = -1;

    @Override
    public boolean onCreate() {
        return true;
    }

    @SuppressWarnings("deprecation") // BaseBundle#get preserves raw types for validation.
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        int callingUid = Binder.getCallingUid();
        Context context = getContext();
        int phoneUid = context == null ? UNRESOLVED_UID : resolvePhoneUid(context);
        boolean hasReadPermission = context != null && hasPermission(
                context, READ_PRIVILEGED_PHONE_STATE_PERMISSION, callingUid);
        boolean hasModifyPermission = context != null && hasPermission(
                context, MODIFY_PHONE_STATE_PERMISSION, callingUid);
        if (!PhoneCallerPolicy.allows(
                callingUid, phoneUid, hasReadPermission, hasModifyPermission)) {
            boundary("PROVIDER_AUTH_REJECTED");
            throw new SecurityException("caller not allowed");
        }
        boundary("PROVIDER_AUTHORIZED");

        Object slotValue;
        Object tokenValue;
        try {
            slotValue = extras == null
                    ? null
                    : extras.get(PendingPinContract.EXTRA_SLOT_ID);
            tokenValue = extras == null
                    ? null
                    : extras.get(PendingPinContract.EXTRA_CLAIM_TOKEN);
        } catch (RuntimeException error) {
            return failureBundle();
        }

        PendingPinProviderLogic.Response response;
        try {
            if (PendingPinContract.METHOD_CLAIM.equals(method)) {
                boundary("PROVIDER_CLAIM_ENTERED");
            }
            PendingPinProviderLogic logic = new PendingPinProviderLogic(
                    PendingPinRepositoryFactory.create(context));
            response = logic.invoke(method, slotValue, tokenValue);
        } catch (RuntimeException error) {
            boundary("PROVIDER_CALL_FAILED");
            return failureBundle();
        }
        if (PendingPinContract.METHOD_CLAIM.equals(method)) {
            boundary(switch (response.outcome()) {
                case SUCCESS -> "PROVIDER_CLAIM_RETURNED";
                case EMPTY -> "PROVIDER_CLAIM_EMPTY";
                case INVALID -> "PROVIDER_CLAIM_INVALID";
                case UNAVAILABLE -> "PROVIDER_CLAIM_UNAVAILABLE";
            });
        }
        return toBundle(method, slotValue, response);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw unsupported();
    }

    @Override
    public String getType(Uri uri) {
        throw unsupported();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw unsupported();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw unsupported();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw unsupported();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        throw unsupported();
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        throw unsupported();
    }

    private static int resolvePhoneUid(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager()
                    .getApplicationInfo(
                            PHONE_PACKAGE, PackageManager.ApplicationInfoFlags.of(0));
            return applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return UNRESOLVED_UID;
        }
    }

    private static boolean hasPermission(Context context, String permission, int callingUid) {
        try {
            return context.checkPermission(permission, -1, callingUid)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static Bundle toBundle(
            String method,
            Object slotValue,
            PendingPinProviderLogic.Response response) {
        if (response.outcome() != PendingPinProviderLogic.Outcome.SUCCESS) {
            return failureBundle();
        }

        Bundle result = new Bundle();
        if (PendingPinContract.METHOD_CLAIM.equals(method)) {
            result.putInt(PendingPinContract.EXTRA_SLOT_ID, (Integer) slotValue);
            result.putString(PendingPinContract.EXTRA_PIN, response.pin());
            result.putString(PendingPinContract.EXTRA_CLAIM_TOKEN, response.claimToken());
        } else {
            result.putBoolean(PendingPinContract.EXTRA_SUCCESS, true);
        }
        return result;
    }

    private static Bundle failureBundle() {
        Bundle result = new Bundle();
        result.putBoolean(PendingPinContract.EXTRA_SUCCESS, false);
        return result;
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("unsupported provider operation");
    }

    private static void boundary(String event) {
        Log.i(LOG_TAG, "pending-boundary=" + event);
    }
}
