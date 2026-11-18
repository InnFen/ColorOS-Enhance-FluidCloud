package dev.howard.colorosenhance.installer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import java.lang.reflect.Field;
import java.util.List;

/** Used only when the native package-restricted resolver reports ambiguous handlers. */
final class InstallerHandlerQuery {
    @SuppressWarnings("unchecked")
    static List<ResolveInfo> query(Object supervisor,Intent candidate,String resolvedType,int user) throws ReflectiveOperationException {
        long identity=Binder.clearCallingIdentity();
        try {
            Object service=field(supervisor,"mService");
            Context context=(Context)field(service,"mContext");
            PackageManager pm=context.getPackageManager();
            Intent probe=new Intent(candidate);
            if(resolvedType!=null)probe.setDataAndType(candidate.getData(),resolvedType);
            return (List<ResolveInfo>)pm.getClass().getMethod("queryIntentActivitiesAsUser",Intent.class,int.class,int.class)
                    .invoke(pm,probe,PackageManager.MATCH_DEFAULT_ONLY,user);
        } finally {Binder.restoreCallingIdentity(identity);}
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private InstallerHandlerQuery() {}
}
