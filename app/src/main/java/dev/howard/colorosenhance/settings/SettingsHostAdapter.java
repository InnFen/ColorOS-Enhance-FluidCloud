package dev.howard.colorosenhance.settings;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.*;

final class SettingsHostAdapter {
    private final SettingsModule module;
    private final HostReflection host;
    private final SettingsEntrance entrance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Object> entranceScreens=Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object,NativeSettingsPage> pages=new IdentityHashMap<>();
    private boolean show=true;
    SettingsHostAdapter(SettingsModule module,ClassLoader loader) {
        this.module=module; host=new HostReflection(loader); entrance=new SettingsEntrance(host);
    }
    void install() throws ReflectiveOperationException {
        Class<?> fragment=host.type(SettingsRoutes.FRAGMENT);
        Class<?> settings=host.type("com.android.settings.SettingsActivity");
        Class<?> system=host.type("com.oplus.settings.feature.othersettings.MoreSystemSettingsFragment");
        for(String name:List.of("com.oplus.settings.widget.preference.SettingsPreferenceCategory",
                "com.oplus.settings.widget.preference.SettingsSimpleJumpPreference",
                "com.coui.appcompat.preference.COUISwitchPreference",
                "com.coui.appcompat.dialog.COUIAlertDialogBuilder","com.coui.appcompat.edittext.COUIEditText")) host.type(name);
        // Resolve every lifecycle boundary before registering the entrance.
        Method create=HostReflection.method(fragment,"onCreatePreferences",Bundle.class,String.class);
        Method start=HostReflection.method(fragment,"onStart");
        Method stop=HostReflection.method(fragment,"onStop");
        Method save=HostReflection.method(fragment,"onSaveInstanceState",Bundle.class);
        Method destroy=HostReflection.method(fragment,"onDestroy");
        Method destroyView=HostReflection.method(fragment,"onDestroyView");
        Method validate=HostReflection.method(settings,"isValidFragment",String.class);
        Method resume=HostReflection.method(system,"onResume");
        after(create,object->{
            String route=route(object);
            if(route==null || pages.containsKey(object)) return;
            NativeSettingsPage page=new NativeSettingsPage(object,host,route);
            page.create(); pages.put(object,page);
        });
        after(start,object->{ NativeSettingsPage page=pages.get(object); if(page!=null) page.start(); });
        try {
            Class<?> preference=host.type("androidx.preference.Preference");
            Class<?> holder=host.type("androidx.preference.PreferenceViewHolder");
            java.lang.reflect.Field itemView=holder.getField("itemView");
            Class<?> nativePreference;
            try{nativePreference=host.type("com.coui.appcompat.preference.COUIPreference");}
            catch(ClassNotFoundException unavailable){nativePreference=null;}
            Class<?> coui=nativePreference;
            for(Class<?> owner:coui==null ? List.of(preference) : List.of(preference,coui)) {
                Method bind=HostReflection.method(owner,"onBindViewHolder",holder);
                module.hook(bind).intercept(chain->{
                    Object result=chain.proceed();
                    // COUI applies typography and card grouping after its base bind.
                    if(owner==preference && coui!=null && coui.isInstance(chain.getThisObject()))return result;
                    safe("内嵌数值滑条",()->{
                        Object view=itemView.get(chain.getArg(0));
                        if(view instanceof android.view.ViewGroup row)
                            for(NativeSettingsPage page:pages.values())page.bindNumber(chain.getThisObject(),row);
                    });return result;
                });
            }
        }catch(ReflectiveOperationException missing){module.warn("数值滑条接口不可用，保留手动输入",missing);}
        before(stop,object->{ NativeSettingsPage page=pages.get(object); if(page!=null) page.stop(); });
        before(save,object->{ NativeSettingsPage page=pages.get(object); if(page!=null) page.clearInputs(); });
        before(destroyView,object->{ NativeSettingsPage page=pages.get(object); if(page!=null) page.releaseView(); });
        before(destroy,object->{ NativeSettingsPage page=pages.remove(object); if(page!=null){page.stop();page.releaseView();} });
        module.hook(validate).intercept(chain->{
            Object target=chain.getThisObject();
            if(target instanceof Activity activity && "com.android.settings.SubSettings".equals(target.getClass().getName())
                    && SettingsRoutes.accepts((String)chain.getArg(0),activity.getIntent())) return true;
            return chain.proceed();
        });
        after(resume,object->{
            if(!"com.oplus.settings.feature.othersettings.MoreSystemSettingsFragment".equals(object.getClass().getName())) return;
            entranceScreens.add(object); entrance.refresh(object,show);
        });
    }
    private String route(Object object) throws ReflectiveOperationException {
        if(!SettingsRoutes.FRAGMENT.equals(object.getClass().getName())) return null;
        Object activity=host.call(object,"getActivity");
        if(!(activity instanceof Activity owner) || !"com.android.settings.SubSettings".equals(owner.getClass().getName())) return null;
        return SettingsHostPolicy.route(object.getClass().getName(),owner.getIntent(),(Bundle)host.call(object,"getArguments"));
    }
    void visibility(boolean value) {
        main.post(()-> {
            show=value;
            for(Object fragment:new ArrayList<>(entranceScreens)) safe("刷新系统设置入口",()->entrance.refresh(fragment,value));
        });
    }
    private interface Action { void run(Object receiver) throws ReflectiveOperationException; }
    private interface Work { void run() throws ReflectiveOperationException; }
    private void after(Method method,Action action) {
        module.hook(method).intercept(chain->{
            Object result=chain.proceed(); safe("内嵌页 "+method.getName(),()->action.run(chain.getThisObject())); return result;
        });
    }
    private void before(Method method,Action action) {
        module.hook(method).intercept(chain->{
            safe("内嵌页 "+method.getName(),()->action.run(chain.getThisObject())); return chain.proceed();
        });
    }
    private void safe(String operation,Work work) {
        try { work.run(); } catch(ReflectiveOperationException | RuntimeException | LinkageError failure) { module.warn(operation,failure); }
    }
}
