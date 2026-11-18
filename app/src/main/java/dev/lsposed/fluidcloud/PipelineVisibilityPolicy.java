package dev.lsposed.fluidcloud;

/** The ColorOS 17 pipeline's input provenance, independent of equal visibility outputs. */
final class PipelineVisibilityPolicy {
    record Flags(boolean clock,boolean notifications,boolean systemInfo) {}
    record Base(boolean home,boolean chips,Flags flags) {}
    record Capsule(boolean canShowChips,boolean mini,boolean privacy,Flags flags) {}
    record Decision(int visibility,boolean overviewHidden,boolean overviewAnimation,
                    CloudScene scene,boolean entering,boolean protectedInput) {}

    static Decision classify(int first,int second,Base base,Capsule capsule,int nativeVisibility) {
        boolean complete=first>=0 && second>=0;
        boolean safe=safe(base,capsule);
        boolean protectedInput=!complete || !safe || nativeVisibility==8;
        if(protectedInput) return new Decision(nativeVisibility,false,false,null,false,true);
        Flags hidden=new Flags(false,false,false);
        boolean hiddenOutput=nativeVisibility==4 && base.flags().equals(hidden);
        boolean recents=hiddenOutput && first==0x00920000 && second==0;
        boolean other=hiddenOutput && !recents && (first!=0 || second!=0);
        CloudScene scene=recents?CloudScene.RECENTS:other?CloudScene.OTHER:null;
        return new Decision(nativeVisibility,false,false,scene,scene!=null,false);
    }
    static Decision classifyTransition(int first,int second,Base base,Capsule capsule,
            int nativeVisibility,int previousVisibility,CloudScene activeScene) {
        // Raw disable and the two Flow inputs are not published atomically. A
        // recognized recents request can precede either half of the hidden flags.
        // Keep hard protections intact, but do not release valid output in that gap.
        if(first==0x00920000 && second==0 && (nativeVisibility==0 || nativeVisibility==4)
                && (previousVisibility==0 || activeScene==CloudScene.RECENTS)
                && unprotected(base,capsule) && endpointFlags(base.flags()) && endpointFlags(capsule.flags()))
            return new Decision(nativeVisibility,false,false,CloudScene.RECENTS,true,false);
        Decision decision=classify(first,second,base,capsule,nativeVisibility);
        if(decision.scene()!=CloudScene.OTHER || previousVisibility==0
                || activeScene==CloudScene.OTHER) return decision;
        return new Decision(nativeVisibility,false,false,null,false,decision.protectedInput());
    }
    static boolean recovered(int first,int second,Base base,Capsule capsule,int nativeVisibility) {
        Flags shown=new Flags(true,true,true);
        return first==0 && second==0 && nativeVisibility==0 && safe(base,capsule)
                && base.flags().equals(shown) && capsule.flags().equals(shown);
    }


    static boolean waitingForExit(int first,int second,Base base,Capsule capsule,int nativeVisibility) {
        return first==0 && second==0 && (nativeVisibility==0 || nativeVisibility==4) && unprotected(base,capsule)
                && endpointFlags(base.flags()) && endpointFlags(capsule.flags());
    }
    private static boolean safe(Base base,Capsule capsule) {
        return unprotected(base,capsule) && base.flags().equals(capsule.flags());
    }
    private static boolean unprotected(Base base,Capsule capsule) {
        return base!=null && capsule!=null && base.home() && !base.chips()
                && capsule.canShowChips() && !capsule.mini() && !capsule.privacy()
                && base.flags()!=null && capsule.flags()!=null;
    }
    private static boolean endpointFlags(Flags flags) {
        return flags.clock()==flags.notifications() && flags.notifications()==flags.systemInfo();
    }
}
