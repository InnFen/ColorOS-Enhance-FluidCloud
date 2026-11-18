package dev.howard.colorosenhance.bridge;
import android.os.Bundle;
import android.os.ResultReceiver;
import org.robolectric.annotation.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Robolectric's default shadow calls the receiver locally and drops a parcelled receiver's Binder. */
@Implements(ResultReceiver.class)
public class ParcelResultReceiverShadow {
    @RealObject private ResultReceiver receiver;
    @Implementation protected void send(int code,Bundle data) {
        Shadow.directlyOn(receiver,ResultReceiver.class,"send",ClassParameter.from(int.class,code),ClassParameter.from(Bundle.class,data));
    }
}
