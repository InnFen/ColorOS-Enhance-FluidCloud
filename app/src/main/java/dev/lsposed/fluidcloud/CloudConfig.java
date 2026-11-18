package dev.lsposed.fluidcloud;

import java.util.Map;

/** Independent user scene choices; firmware hard exclusions remain in the adapter. */
public record CloudConfig(boolean enabled, SceneChoice fullscreen,
                          SceneChoice recents, SceneChoice other, boolean keepPausedMusic,
                          boolean unbindStatusBar, boolean keepShade, boolean collapseShadeOnOpen,
                          boolean blockCapsuleClickTop, Landscape landscape, boolean landscapeFollowsPortrait,
                          boolean cameraCardExpansion, Extras extras) {
    public static final String GROUP = "cloud";
    public record Extras(boolean hideStroke, boolean hideArrows, boolean skipEmpty,
                         boolean dimEnabled, int dimPercent, boolean realtimeMediaWave, MediaWaveOptions waveOptions,
                         boolean capsuleOnTop, boolean keepDuringLaunch, CloudCorners corners,
                         CapsuleSize size, boolean selectedCardFirst) {
        static final Extras OEM = new Extras(false,false,false,false,30);
        public Extras(boolean hideStroke,boolean hideArrows,boolean skipEmpty,boolean dimEnabled,int dimPercent){
            this(hideStroke,hideArrows,skipEmpty,dimEnabled,dimPercent,false);
        }
        public Extras(boolean hideStroke,boolean hideArrows,boolean skipEmpty,boolean dimEnabled,int dimPercent,boolean realtimeMediaWave){
            this(hideStroke,hideArrows,skipEmpty,dimEnabled,dimPercent,realtimeMediaWave,MediaWaveOptions.DEFAULTS,
                    false,true,CloudCorners.OEM);
        }
        public Extras(boolean hideStroke,boolean hideArrows,boolean skipEmpty,boolean dimEnabled,int dimPercent,
                      boolean realtimeMediaWave,MediaWaveOptions waveOptions,boolean capsuleOnTop,
                      boolean keepDuringLaunch,CloudCorners corners) {
            this(hideStroke,hideArrows,skipEmpty,dimEnabled,dimPercent,realtimeMediaWave,waveOptions,
                    capsuleOnTop,keepDuringLaunch,corners,CapsuleSize.OEM,false);
        }
        public Extras { dimPercent = Math.max(0,Math.min(100,dimPercent)); }
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade,boolean collapseShadeOnOpen,
                       boolean blockCapsuleClickTop,Landscape landscape,boolean landscapeFollowsPortrait,boolean cameraCardExpansion) {
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,
                blockCapsuleClickTop,landscape,landscapeFollowsPortrait,cameraCardExpansion,Extras.OEM);
    }

    public record SceneChoice(boolean keep, boolean animate) {
        CloudSceneMode mode(boolean enabled) {
            if (!enabled) return CloudSceneMode.OEM;
            return keep ? CloudSceneMode.KEEP : animate ? CloudSceneMode.ANIMATE : CloudSceneMode.OEM;
        }
    }
    public record Landscape(SceneChoice fullscreen, SceneChoice recents, SceneChoice other,
                            boolean keepShade, boolean collapseShadeOnOpen) {
        static final Landscape OEM = new Landscape(new SceneChoice(false,false),
                new SceneChoice(false,false),new SceneChoice(false,false),false,false);
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade,boolean collapseShadeOnOpen,
                       boolean blockCapsuleClickTop,Landscape landscape,boolean landscapeFollowsPortrait){
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,
                blockCapsuleClickTop,landscape,landscapeFollowsPortrait,false);
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade,boolean collapseShadeOnOpen,
                       boolean blockCapsuleClickTop,Landscape landscape){
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,
                blockCapsuleClickTop,landscape,false,false);
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade,boolean collapseShadeOnOpen,
                       boolean blockCapsuleClickTop){
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,
                blockCapsuleClickTop,Landscape.OEM);
    }
    CloudConfig forOrientation(boolean isLandscape) {
        if (!isLandscape || landscapeFollowsPortrait) return this;
        return new CloudConfig(enabled,landscape.fullscreen(),landscape.recents(),landscape.other(),
                keepPausedMusic,unbindStatusBar,landscape.keepShade(),landscape.collapseShadeOnOpen(),
                blockCapsuleClickTop,landscape,false,cameraCardExpansion,extras);
    }
    CloudConfig withCapsuleOnTop(boolean onTop) {
        if (extras.capsuleOnTop()==onTop) return this;
        return new CloudConfig(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,
                blockCapsuleClickTop,landscape,landscapeFollowsPortrait,cameraCardExpansion,
                new Extras(extras.hideStroke(),extras.hideArrows(),extras.skipEmpty(),extras.dimEnabled(),extras.dimPercent(),
                        extras.realtimeMediaWave(),extras.waveOptions(),onTop,extras.keepDuringLaunch(),extras.corners(),
                        extras.size(),extras.selectedCardFirst()));
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade,boolean collapseShadeOnOpen){
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,collapseShadeOnOpen,false);
    }
    public CloudConfig(boolean enabled,SceneChoice fullscreen,SceneChoice recents,SceneChoice other,
                       boolean keepPausedMusic,boolean unbindStatusBar,boolean keepShade){
        this(enabled,fullscreen,recents,other,keepPausedMusic,unbindStatusBar,keepShade,false);
    }

    public CloudConfig(boolean enabled, boolean fullscreen, boolean recents) {
        this(enabled, fullscreen, recents, false);
    }

    public CloudConfig(boolean enabled, boolean fullscreen, boolean recents, boolean recentsShrink) {
        this(enabled, new SceneChoice(fullscreen, false),
                new SceneChoice(recents, recentsShrink), new SceneChoice(false, false), false, true, false);
    }

    public CloudConfig(boolean enabled, SceneChoice fullscreen, SceneChoice recents,
                       SceneChoice other, boolean keepPausedMusic) {
        this(enabled, fullscreen, recents, other, keepPausedMusic, true, false);
    }

    public static CloudConfig from(Map<String, ?> values) {
        boolean enabled = Boolean.TRUE.equals(values.get("enabled"));
        return new CloudConfig(enabled,
                new SceneChoice(Boolean.TRUE.equals(values.get("fullscreen")),
                        Boolean.TRUE.equals(values.get("fullscreen_animation"))),
                new SceneChoice(Boolean.TRUE.equals(values.get("recents")),
                        Boolean.TRUE.equals(values.get("recents_shrink"))),
                new SceneChoice(Boolean.TRUE.equals(values.get("other_keep")),
                        Boolean.TRUE.equals(values.get("other_animation"))),
                Boolean.TRUE.equals(values.get("keep_paused_music")),
                Boolean.TRUE.equals(values.get("unbind_status_bar")),
                Boolean.TRUE.equals(values.get("shade_keep")),
                Boolean.TRUE.equals(values.get("shade_collapse_on_open")),
                Boolean.TRUE.equals(values.get("block_capsule_click_top")),
                new Landscape(choice(values,"landscape_fullscreen","landscape_fullscreen_animation"),
                        choice(values,"landscape_recents","landscape_recents_shrink"),
                        choice(values,"landscape_other_keep","landscape_other_animation"),
                        Boolean.TRUE.equals(values.get("landscape_shade_keep")),
                        Boolean.TRUE.equals(values.get("landscape_shade_collapse_on_open"))),
                Boolean.TRUE.equals(values.get("landscape_follow_portrait")),
                Boolean.TRUE.equals(values.get("camera_card_expansion")),
                new Extras(Boolean.TRUE.equals(values.get("hide_capsule_stroke")),
                        Boolean.TRUE.equals(values.get("hide_capsule_arrows")),
                        Boolean.TRUE.equals(values.get("skip_empty_capsule")),
                        Boolean.TRUE.equals(values.get("card_dim_enabled")),
                        values.get("card_dim_percent") instanceof Integer number ? number : 30,
                        Boolean.TRUE.equals(values.get("realtime_media_wave")),MediaWaveOptions.from(values),
                        Boolean.TRUE.equals(values.get("capsule_on_top")),
                        !(values.get("capsule_keep_during_launch") instanceof Boolean keep) || keep,CloudCorners.from(values),
                        CapsuleSize.from(values),Boolean.TRUE.equals(values.get("selected_card_first"))));
    }
    private static SceneChoice choice(Map<String,?> values,String keep,String animate) {
        return new SceneChoice(Boolean.TRUE.equals(values.get(keep)),Boolean.TRUE.equals(values.get(animate)));
    }

    boolean retainPausedMusic() { return enabled && keepPausedMusic; }
    boolean scenesEnabled() { return enabled && unbindStatusBar; }
    boolean retainInShade() { return scenesEnabled() && keepShade; }
    boolean collapseBeforeOpen() { return retainInShade() && collapseShadeOnOpen; }
    boolean capsuleOnTop() { return scenesEnabled() && extras.capsuleOnTop(); }
    boolean keepCapsuleDuringLaunch() { return scenesEnabled() && extras.keepDuringLaunch(); }
    CloudCorners corners() { return enabled ? extras.corners() : CloudCorners.OEM; }
    boolean preventCapsuleClickTop() { return enabled && blockCapsuleClickTop; }
    boolean expandFromCamera() { return enabled && cameraCardExpansion; }
    boolean hideCapsuleStroke() { return enabled && extras.hideStroke(); }
    boolean hideCapsuleArrows() { return enabled && extras.hideArrows(); }
    boolean skipEmptyCapsule() { return enabled && extras.skipEmpty(); }
    CapsuleSize capsuleSize() { return enabled ? extras.size() : CapsuleSize.OEM; }
    boolean selectedCardFirst() { return enabled && extras.selectedCardFirst(); }
    boolean dimCardBackground() { return enabled && extras.dimEnabled(); }
    boolean realtimeMediaWave() { return enabled && extras.realtimeMediaWave(); }

    CloudSceneMode mode(CloudScene scene) {
        return switch (scene) {
            case FULLSCREEN -> fullscreen.mode(scenesEnabled());
            case RECENTS -> recents.mode(scenesEnabled());
            case OTHER -> other.mode(scenesEnabled());
        };
    }

}
