package com.kindle.photodrop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Kindle 重启后自动恢复接收服务。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent service = new Intent(context, UploadService.class);
            service.setAction(UploadService.ACTION_START);
            context.startService(service);
        }
    }
}
