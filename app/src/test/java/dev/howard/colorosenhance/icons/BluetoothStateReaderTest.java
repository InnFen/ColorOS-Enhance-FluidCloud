package dev.howard.colorosenhance.icons;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothStateReaderTest {
    @Test public void onlyCurrentConnectionsQualifyAndZeroBatteryIsValid() {
        Policy p=new Policy();p.bluetoothConnected=true;
        p.bluetoothController.devices=List.of(new Cached(false,90,0));
        assertEquals(Boolean.FALSE,BluetoothStateReader.read(p).qualified());
        p.bluetoothController.devices=List.of(new Cached(true,0,0));
        assertEquals(Boolean.TRUE,BluetoothStateReader.read(p).qualified());
        p.bluetoothConnected=false;
        assertFalse(BluetoothStateReader.read(p).connected());
    }
    @Test public void headphonesWithoutBatteryQualifyButOrdinaryUnknownBatteryDevicesDoNot() {
        Policy p=new Policy();p.bluetoothConnected=true;
        p.bluetoothController.devices=List.of(new Cached(true,-1,0x418));
        assertEquals(Boolean.TRUE,BluetoothStateReader.read(p).qualified());
        p.bluetoothController.devices=List.of(new Cached(true,-1,0x580));
        assertEquals(Boolean.FALSE,BluetoothStateReader.read(p).qualified());
    }
    @Test public void splitEarbudBatteryAndLeAudioAreSupported() {
        Policy p=new Policy();p.bluetoothConnected=true;
        p.bluetoothController.devices=List.of(new SplitBattery());
        assertEquals(Boolean.TRUE,BluetoothStateReader.read(p).qualified());
        p.bluetoothController.devices=List.of(new LeAudio());
        assertEquals(Boolean.TRUE,BluetoothStateReader.read(p).qualified());
    }
    @Test public void missingContractPreservesUnknownInsteadOfHidingConnections() {
        assertFalse(BluetoothStateReader.read(new Object()).known());
        Policy p=new Policy();p.bluetoothConnected=true;p.bluetoothController.devices=List.of(new Object());
        assertTrue(BluetoothStateReader.read(p).known());assertNull(BluetoothStateReader.read(p).qualified());
    }
    static class Policy {boolean bluetoothConnected;Controller bluetoothController=new Controller();}
    static class Controller {List<?> devices=List.of();public List<?> getConnectedDevices(){return devices;}}
    static class Cached {
        boolean connected;int battery,classification;
        Cached(boolean connected,int battery,int classification){this.connected=connected;this.battery=battery;this.classification=classification;}
        public boolean isConnected(){return connected;}
        public int getBatteryLevel(){return battery;}
        public Device getDevice(){return new Device(classification);}
    }
    static class Device {int c;Device(int c){this.c=c;}public Classification getBluetoothClass(){return new Classification(c);}}
    static class Classification {int c;Classification(int c){this.c=c;}public int getDeviceClass(){return c;}}
    static class SplitBattery extends Cached {
        SplitBattery(){super(true,-1,0);}
        public Levels getBatteryLevelsInfo(){return new Levels();}
    }
    static class Levels {
        public int getLeftBatteryLevel(){return 42;}
        public int getRightBatteryLevel(){return -1;}
        public int getCaseBatteryLevel(){return -1;}
        public int getOverallBatteryLevel(){return -1;}
    }
    static class LeAudio extends Cached {LeAudio(){super(true,-1,0);}public boolean isConnectedLeAudioDevice(){return true;}}
}
