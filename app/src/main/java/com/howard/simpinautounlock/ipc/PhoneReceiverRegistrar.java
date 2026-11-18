package com.howard.simpinautounlock.ipc;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;

public final class PhoneReceiverRegistrar {
    private PhoneReceiverRegistrar() {}

    public static void register(Context context, PhoneCommandReceiver receiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConfigContract.ACTION_CONFIGURE);
        filter.addAction(ConfigContract.ACTION_SET_ENABLED);
        filter.addAction(ConfigContract.ACTION_CLEAR_SLOT);
        filter.addAction(ConfigContract.ACTION_CLEAR_ALL);
        filter.addAction(ConfigContract.ACTION_QUERY);
        context.registerReceiver(
                receiver,
                filter,
                ConfigContract.PERMISSION,
                new Handler(context.getMainLooper()),
                Context.RECEIVER_EXPORTED);
    }
}
