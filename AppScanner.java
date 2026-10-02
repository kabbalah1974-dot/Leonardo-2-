package it.leonardo.antivirus;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guarda le app installate dall'utente (non quelle di sistema) e dà un punteggio di rischio
 * in base a da dove arrivano e a cosa possono fare. Non legge dentro le app: Android non lo permette.
 */
public final class AppScanner {

    public static final class Result {
        public final int totalUserApps;
        public final List<AppInfo> apps;

        Result(int totalUserApps, List<AppInfo> apps) {
            this.totalUserApps = totalUserApps;
            this.apps = apps;
        }
    }

    private static final Set<String> STORES = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia",
            "com.huawei.appmarket",
            "com.hihonor.appmarket",
            "com.heytap.market",
            "com.bbk.appstore",
            "com.xiaomi.mipicks",
            "org.fdroid.fdroid",
            "org.fdroid.basic"));

    private final Context context;

    public AppScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public Result scan() {
        PackageManager pm = context.getPackageManager();
        Set<String> accessibility = enabledAccessibilityPackages();
        Set<String> admins = adminPackages();
        Set<String> listeners = notificationListenerPackages();

        List<AppInfo> apps = new ArrayList<>();
        int total = 0;
        for (PackageInfo p : installedPackages(pm)) {
            ApplicationInfo ai = p.applicationInfo;
            if (ai == null) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (p.packageName.equals(context.getPackageName())) continue;
            total++;

            Set<String> permissions = new HashSet<>();
            Set<String> granted = new HashSet<>();
            if (p.requestedPermissions != null) {
                permissions.addAll(Arrays.asList(p.requestedPermissions));
                for (int i = 0; i < p.requestedPermissions.length; i++) {
                    boolean ok = p.requestedPermissionsFlags != null && i < p.requestedPermissionsFlags.length
                            && (p.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    if (ok) granted.add(p.requestedPermissions[i]);
                }
            }
            String installer = installerOf(pm, p.packageName);
            boolean fromStore = installer != null && STORES.contains(installer);

            AppFacts facts = new AppFacts.Builder()
                    .fromStore(fromStore)
                    .accessibilityOn(accessibility.contains(p.packageName))
                    .deviceAdmin(admins.contains(p.packageName))
                    .notificationListener(listeners.contains(p.packageName))
                    .readsSms(granted.contains("android.permission.READ_SMS")
                            || granted.contains("android.permission.RECEIVE_SMS"))
                    .overlay(specialAllowed(pm, permissions, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            "android.permission.SYSTEM_ALERT_WINDOW", p.packageName, ai.uid))
                    .canInstallApps(specialAllowed(pm, permissions, "android:request_install_packages",
                            "android.permission.REQUEST_INSTALL_PACKAGES", p.packageName, ai.uid))
                    .allFilesAccess(specialAllowed(pm, permissions, "android:manage_external_storage",
                            "android.permission.MANAGE_EXTERNAL_STORAGE", p.packageName, ai.uid))
                    .debuggable((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                    .hasLauncher(pm.getLaunchIntentForPackage(p.packageName) != null)
                    .targetSdk(ai.targetSdkVersion)
                    .build();
            Assessment verdict = RiskPolicy.assess(facts);

            apps.add(new AppInfo(
                    p.packageName,
                    Sanitize.label(String.valueOf(pm.getApplicationLabel(ai))),
                    ai.sourceDir == null ? "" : ai.sourceDir,
                    fromStore,
                    verdict.score,
                    verdict.reasons));
        }
        Collections.sort(apps, (a, b) -> Integer.compare(b.score, a.score));
        return new Result(total, apps);
    }

    /**
     * Permessi "speciali" (sovrapposizione, installazione di app, tutti i file): contano solo se l'app li
     * dichiara E l'utente li ha concessi. Se Android non risponde si presume concesso (prudenza).
     */
    private boolean specialAllowed(PackageManager pm, Set<String> declared, String op, String permission,
                                   String packageName, int uid) {
        if (!declared.contains(permission)) return false;
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return true;
            int mode = ops.unsafeCheckOpNoThrow(op, uid, packageName);
            if (mode == AppOpsManager.MODE_ALLOWED) return true;
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @SuppressWarnings("deprecation")
    private List<PackageInfo> installedPackages(PackageManager pm) {
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
        }
        return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
    }

    @SuppressWarnings("deprecation")
    private String installerOf(PackageManager pm, String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                return pm.getInstallSourceInfo(packageName).getInstallingPackageName();
            }
            return pm.getInstallerPackageName(packageName);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> enabledAccessibilityPackages() {
        Set<String> out = new HashSet<>();
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return out;
        for (AccessibilityServiceInfo info : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                out.add(info.getResolveInfo().serviceInfo.packageName);
            }
        }
        return out;
    }

    private Set<String> adminPackages() {
        Set<String> out = new HashSet<>();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return out;
        List<ComponentName> admins = dpm.getActiveAdmins();
        if (admins != null) {
            for (ComponentName cn : admins) out.add(cn.getPackageName());
        }
        return out;
    }

    private Set<String> notificationListenerPackages() {
        Set<String> out = new HashSet<>();
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return out;
        for (String part : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null) out.add(cn.getPackageName());
        }
        return out;
    }
}
