package com.howard.simpinautounlock;

import android.app.Application;
import com.howard.simpinautounlock.management.SimManagementController;
import com.howard.simpinautounlock.pending.PendingPinRepository;
import com.howard.simpinautounlock.pending.RemotePendingPinRepositoryFactory;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModuleApplication extends Application {
    private final CopyOnWriteArrayList<RemoteRepositoryListener> listeners =
            new CopyOnWriteArrayList<>();
    private volatile PendingPinRepository remoteRepository;
    private volatile XposedService boundService;
    private SimManagementController simManagement;

    public synchronized SimManagementController simManagement() {
        if (simManagement == null) {
            simManagement = new SimManagementController(this);
        }
        return simManagement;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public synchronized void onServiceBind(XposedService service) {
                boundService = service;
                remoteRepository = null;
                onFrameworkServiceChanged(service);
                listeners.forEach(RemoteRepositoryListener::onUnavailable);
                PendingPinRepository repository;
                try {
                    boolean channelReady = service.getRemotePreferences(
                                    RemotePendingPinRepositoryFactory.META_GROUP)
                            .edit()
                            .putInt(
                                    RemotePendingPinRepositoryFactory.CHANNEL_VERSION_KEY,
                                    RemotePendingPinRepositoryFactory.CHANNEL_VERSION)
                            .commit();
                    if (!channelReady) {
                        return;
                    }
                    repository = RemotePendingPinRepositoryFactory.create(
                            service.getRemotePreferences(
                                    RemotePendingPinRepositoryFactory.DATA_GROUP),
                            service.getRemotePreferences(
                                    RemotePendingPinRepositoryFactory.KEY_GROUP));
                } catch (RuntimeException error) {
                    return;
                }
                remoteRepository = repository;
                listeners.forEach(listener -> listener.onAvailable(repository));
            }

            @Override
            public synchronized void onServiceDied(XposedService service) {
                if (boundService != service) {
                    return;
                }
                boundService = null;
                remoteRepository = null;
                onFrameworkServiceChanged(null);
                listeners.forEach(RemoteRepositoryListener::onUnavailable);
            }
        });
    }

    public XposedService frameworkService() {
        return boundService;
    }

    protected void onFrameworkServiceChanged(XposedService service) {
        // Unified application subclasses observe framework availability independently of staging.
    }

    public Registration observeRemoteRepository(RemoteRepositoryListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
        PendingPinRepository current = remoteRepository;
        if (current != null) {
            listener.onAvailable(current);
        }
        return () -> listeners.remove(listener);
    }

    public interface RemoteRepositoryListener {
        void onAvailable(PendingPinRepository repository);
        void onUnavailable();
    }

    public interface Registration {
        void close();
    }
}
