package dev.howard.colorosenhance.installer;

import android.content.Intent;
import java.lang.reflect.Field;

/** Binds one native resolver call to its owning ActivityStarter request. */
final class InstallerRequestScope {
    @FunctionalInterface interface Operation { Object run() throws Throwable; }
    private final ThreadLocal<Object> current = new ThreadLocal<>();
    private final Field intentField,userField,realUidField,callingUidField,packageField,pendingField;
    InstallerRequestScope(Class<?> requestClass) throws ReflectiveOperationException {
        intentField=field(requestClass,"intent"); userField=field(requestClass,"userId");
        realUidField=field(requestClass,"realCallingUid"); callingUidField=field(requestClass,"callingUid");
        packageField=field(requestClass,"callingPackage"); pendingField=field(requestClass,"originatingPendingIntent");
    }
    Object within(Object request,Operation operation) throws Throwable {
        Object previous=current.get(); current.set(request);
        try { return operation.run(); }
        finally { if(previous==null) current.remove(); else current.set(previous); }
    }
    InstallerRouting.Origin originFor(Intent intent,int user,int flags,int filterUid) throws ReflectiveOperationException {
        Object request=current.get();
        // The nonzero-flags path is the framework's locked/stopped-profile retry.
        if(request==null || flags!=0 || intentField.get(request)!=intent || userField.getInt(request)!=user) return null;
        int uid=realUidField.getInt(request),callingUid=callingUidField.getInt(request);
        if(filterUid!=uid || (callingUid!=-1 && callingUid!=uid)) return null;
        return new InstallerRouting.Origin(uid,(String)packageField.get(request),pendingField.get(request)!=null);
    }
    private static Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        Field field=owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
