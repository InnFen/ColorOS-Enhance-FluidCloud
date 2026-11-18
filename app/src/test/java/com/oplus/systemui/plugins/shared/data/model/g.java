package com.oplus.systemui.plugins.shared.data.model;

import android.graphics.RectF;
import java.util.ArrayList;

/** Handwritten reflection-contract fixture, not a vendor implementation.
 * The obfuscated type, fields and copy signature are the interface under test.
 * A generic slot table supplies the test-only copy/mask behavior. */
public final class g {
    public final boolean a,b,c,f,g,h,i,j,k,m,n,q,r,s,t,v,x,y,C,F,G,H,I;
    public final RectF d;
    public final float e;
    public final int l,o,p,u,w,A,D,E;
    public final ArrayList<?> z;
    public final String B;

    public g(Object... slots) {
        if(slots.length!=32)throw new IllegalArgumentException("Fixture requires 32 slots");
        a=(boolean)slots[0]; b=(boolean)slots[1]; c=(boolean)slots[2];
        d=(RectF)slots[3]; e=(float)slots[4];
        f=(boolean)slots[5]; g=(boolean)slots[6]; h=(boolean)slots[7];
        i=(boolean)slots[8]; j=(boolean)slots[9]; k=(boolean)slots[10];
        l=(int)slots[11]; m=(boolean)slots[12]; n=(boolean)slots[13];
        o=(int)slots[14]; p=(int)slots[15]; q=(boolean)slots[16];
        r=(boolean)slots[17]; s=(boolean)slots[18]; t=(boolean)slots[19];
        u=(int)slots[20]; v=(boolean)slots[21]; w=(int)slots[22];
        x=(boolean)slots[23]; y=(boolean)slots[24]; z=(ArrayList<?>)slots[25];
        A=(int)slots[26]; B=(String)slots[27]; C=(boolean)slots[28];
        D=(int)slots[29]; E=(int)slots[30]; F=(boolean)slots[31];
        G=(E&1)!=0; H=(E&2)!=0; I=(E&4)!=0;
    }

    public static g a(g source,boolean p0,boolean p1,boolean p2,RectF p3,float p4,
            boolean p5,boolean p6,boolean p7,boolean p8,boolean p9,boolean p10,int p11,
            boolean p12,boolean p13,int p14,int p15,boolean p16,boolean p17,boolean p18,
            boolean p19,int p20,boolean p21,int p22,boolean p23,boolean p24,ArrayList<?> p25,
            int p26,boolean p28,int p29,int p30,boolean p31,int mask) {
        Object[] overrides={p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10,p11,p12,p13,p14,p15,
                p16,p17,p18,p19,p20,p21,p22,p23,p24,p25,p26,source.B,p28,p29,p30,p31};
        String fieldNames="abcdefghijklmnopqrstuvwxyzABCDEF";
        Object[] slots=new Object[32];
        for(int slot=0;slot<slots.length;slot++) {
            try {
                slots[slot]=(mask&(1<<slot))!=0 || slot==27
                        ? g.class.getField(fieldNames.substring(slot,slot+1)).get(source)
                        : overrides[slot];
            } catch(ReflectiveOperationException invalidFixture) {
                throw new AssertionError(invalidFixture);
            }
        }
        return new g(slots);
    }
}
