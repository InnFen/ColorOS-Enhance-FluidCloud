package dev.howard.colorosenhance.bridge;

import android.content.*;
import android.os.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Asynchronous bridge owned by one native Settings page. */
public final class SettingsBridgeClient {
    public interface Listener {
        void onConnectionChanged(boolean connected);
        void onChanged();
    }
    private static final long TIMEOUT_MS = 5000;
    private static final int MAX_PENDING = 16;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Pending> pending = new LinkedHashSet<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING + 2), runnable -> {
                Thread thread = new Thread(runnable,"SettingsBridgeClient");
                thread.setDaemon(true);
                return thread;
            });
    private Listener listener;
    private Connection connection;
    private IBinder endpoint;
    private Binder watchToken;
    private Runnable watchDeadline;
    private boolean ready;
    private volatile int generation;
    private volatile boolean closed;

    /** The page calls this API on main; only application context is retained. */
    public SettingsBridgeClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void connect() {
        if (closed || connection != null) return;
        Connection next = new Connection();
        connection = next;
        try {
            boolean bound = context.bindService(new Intent().setComponent(
                    new ComponentName(BridgeContract.PACKAGE,BridgeContract.SERVICE)),next,Context.BIND_AUTO_CREATE);
            if (!bound) release(next);
        } catch (RuntimeException failure) { release(next); }
    }

    public void close() {
        if (closed) return;
        closed = true;
        listener = null;
        generation++;
        ready = false;
        cancelWatchDeadline();
        for (Pending operation : new ArrayList<>(pending)) finish(operation,null);
        worker.getQueue().clear();
        IBinder oldEndpoint = endpoint;
        Binder oldToken = watchToken;
        endpoint = null;
        watchToken = null;
        unwatch(oldEndpoint,oldToken);
        if (connection != null) {
            Connection old = connection;
            connection = null;
            unbind(old);
        }
        worker.shutdown();
    }

    public boolean isConnected() { return !closed && ready; }

    public void request(Bundle request, Consumer<Bundle> reply) {
        if (closed) return;
        if (!isConnected() || pending.size() >= MAX_PENDING) {
            reply.accept(status("unavailable"));
            return;
        }
        Pending operation = new Pending(new Bundle(request),reply,endpoint,generation);
        pending.add(operation);
        main.postDelayed(operation.timeout,TIMEOUT_MS);
        worker.execute(operation);
    }

    private void finish(Pending operation,Bundle response) {
        if (!pending.remove(operation)) return;
        main.removeCallbacks(operation.timeout);
        worker.remove(operation);
        operation.cancel();
        Consumer<Bundle> callback = operation.callback;
        operation.callback = null;
        if (!closed && response != null && callback != null) callback.accept(response);
    }

    private void disconnected(boolean removeWatch) {
        IBinder oldEndpoint = endpoint;
        Binder oldToken = watchToken;
        generation++;
        ready = false;
        cancelWatchDeadline();
        endpoint = null;
        watchToken = null;
        for (Pending operation : new ArrayList<>(pending)) finish(operation,status("unavailable"));
        // Pending requests are cancelled above; remove any superseded watch registration too.
        worker.getQueue().clear();
        if (removeWatch && !closed) unwatch(oldEndpoint,oldToken);
        if (!closed && listener != null) listener.onConnectionChanged(false);
    }

    private void cancelWatchDeadline() {
        if (watchDeadline != null) main.removeCallbacks(watchDeadline);
        watchDeadline = null;
    }

    private void unwatch(IBinder service,Binder token) {
        if (service == null || token == null) return;
        Bundle request = command("unwatch",token);
        // Cleanup follows any in-flight registration even if its acknowledgment was lost.
        worker.execute(() -> transact(service,request,new ResultReceiver(null)));
    }

    private void release(Connection target) {
        if (connection != target) return;
        connection = null;
        unbind(target);
        disconnected(true);
    }

    private void unbind(Connection target) {
        try { context.unbindService(target); }
        catch (RuntimeException ignored) { /* Failed binding may already have been released. */ }
    }

    private final class Connection implements ServiceConnection {
        @Override public void onServiceConnected(ComponentName name,IBinder service) {
            if (closed || connection != this) return;
            if (service == null) { release(this); return; }
            if (endpoint != null) disconnected(true);
            if (closed || connection != this) return;
            endpoint = service;
            int version = ++generation;
            Binder token = new Binder();
            watchToken = token;
            watchDeadline = () -> {
                if (!closed && generation == version && !ready) release(this);
            };
            main.postDelayed(watchDeadline,TIMEOUT_MS);
            ResultReceiver watcher = new ResultReceiver(main) {
                @Override protected void onReceiveResult(int code,Bundle result) {
                    if (closed || generation != version || endpoint != service) return;
                    String status = result == null ? null : result.getString("status");
                    if ("ok".equals(status)) {
                        if (ready) return;
                        cancelWatchDeadline();
                        ready = true;
                        if (listener != null) listener.onConnectionChanged(true);
                    } else if ("changed".equals(status)) {
                        if (ready && listener != null) listener.onChanged();
                    } else release(Connection.this);
                }
            };
            worker.execute(() -> {
                if (closed || generation != version) return;
                if (!transact(service,command("watch",token),watcher)) main.post(() -> {
                    if (!closed && generation == version) release(this);
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (!closed && connection == this) disconnected(false);
        }
        @Override public void onBindingDied(ComponentName name) { if (!closed) release(this); }
        @Override public void onNullBinding(ComponentName name) { if (!closed) release(this); }
    }

    private final class Pending implements Runnable {
        private Bundle request;
        private Consumer<Bundle> callback;
        private final IBinder service;
        private final int version;
        private volatile boolean cancelled;
        private final Runnable timeout = () -> finish(this,status("timeout"));
        Pending(Bundle request,Consumer<Bundle> callback,IBinder service,int version) {
            this.request=request;
            this.callback=callback;
            this.service=service;
            this.version=version;
        }
        synchronized void cancel() {
            cancelled = true;
            scrub(request);
            request = null;
        }
        @Override public void run() {
            Bundle outbound;
            synchronized (this) {
                if (cancelled || closed || generation != version) { cancel(); return; }
                outbound=request;
                request=null;
            }
            ResultReceiver receiver = new ResultReceiver(main) {
                @Override protected void onReceiveResult(int code,Bundle response) {
                    if (!closed && generation == version && !cancelled) finish(Pending.this,
                            response == null ? status("unavailable") : response);
                }
            };
            if (!transact(service,outbound,receiver)) main.post(() -> {
                if (!closed && generation == version && !cancelled) finish(this,status("unavailable"));
            });
        }
    }

    private static boolean transact(IBinder endpoint,Bundle request,ResultReceiver receiver) {
        Parcel data=Parcel.obtain(), reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.DESCRIPTOR);
            data.writeBundle(request);
            scrub(request);
            data.writeTypedObject(receiver,0);
            if (!endpoint.transact(BridgeContract.REQUEST,data,reply,0)) return false;
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException failure) { return false; }
        finally { scrub(request); data.recycle(); reply.recycle(); }
    }
    private static void scrub(Bundle request) {
        if (request != null) { request.remove("pin"); request.remove("confirmation"); }
    }
    private static Bundle command(String op,IBinder token) {
        Bundle request = new Bundle(); request.putString("op",op); request.putBinder("token",token); return request;
    }
    private static Bundle status(String value) { Bundle result = new Bundle(); result.putString("status",value); return result; }
}
