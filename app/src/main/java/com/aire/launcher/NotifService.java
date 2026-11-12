package com.aire.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/** Cuenta las notificaciones activas por app para las insignias. */
public class NotifService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        refresh();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refresh();
    }

    private void refresh() {
        try {
            Map<String, Integer> m = new HashMap<>();
            StatusBarNotification[] list = getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification s : list) {
                    if (s.isOngoing()) continue;
                    if ((s.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    String p = s.getPackageName();
                    Integer c = m.get(p);
                    m.put(p, c == null ? 1 : c + 1);
                }
            }
            Badges.counts.clear();
            Badges.counts.putAll(m);
            Runnable r = Badges.listener;
            if (r != null) r.run();
        } catch (Throwable ignored) {
        }
    }
}

final class Badges {
    static final Map<String, Integer> counts = new HashMap<>();
    static volatile Runnable listener;

    static int get(String pkg) {
        Integer c = counts.get(pkg);
        return c == null ? 0 : c;
    }
}
