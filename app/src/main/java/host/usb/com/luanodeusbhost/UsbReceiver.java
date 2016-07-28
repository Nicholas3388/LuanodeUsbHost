package host.usb.com.luanodeusbhost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

/**
 * Created by Administrator on 2016/7/27.
 */
public class UsbReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            if (usbReceiverListener != null) { usbReceiverListener.onDeviceAttached(); }

            synchronized (this) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if(device != null){
                        Toast.makeText(context, "Device attached and permission granted!", Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    Toast.makeText(context, "Request permission", Toast.LENGTH_SHORT).show();
                    if (this.usbReceiverListener != null) {
                        usbReceiverListener.onRequestPermission(device);
                    }
                }

            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            Toast.makeText(context, "Device detached!", Toast.LENGTH_SHORT).show();
            if (usbReceiverListener != null) { usbReceiverListener.onDeviceDetached(); }
        } else if (Constants.ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        Toast.makeText(context, "Permission granted!", Toast.LENGTH_SHORT).show();
                        if (usbReceiverListener != null) {
                            usbReceiverListener.onPermissionGranted();
                        }
                    }
                }
            }
        }
    }

    protected static UsbReceiverListener usbReceiverListener;
    public void setUsbReceiverListener(UsbReceiverListener usbReceiverListener) {
        this.usbReceiverListener = usbReceiverListener;
    }
    public interface UsbReceiverListener {
        void onRequestPermission(UsbDevice device);
        void onPermissionGranted();
        void onDeviceDetached();
        void onDeviceAttached();
        void onUsbReceiverLog(String msg);
    }
}
