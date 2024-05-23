// ITransactor.aidl
package cn.tinyhai.ban_uninstall.transact;

import rikka.parcelablelist.ParcelableListSlice;
import android.content.pm.ApplicationInfo;

// Declare any non-default types here with import statements

interface ITransactor {
    ParcelableListSlice<PackageInfo> getPackages();

    void banPackage(in List<String> packageNames, out List<String> bannedPackages);

    void freePackage(in List<String> packageNames, out List<String> freedPackages);

    List<String> getAllBannedPackages();

    String sayHello(String hello);

    oneway void onAppLaunched();

    oneway void reloadPrefs();

    IBinder getAuth();

    int getActiveMode();

    boolean syncPrefs(in Map prefs);

    ApplicationInfo getApplicationInfoAsUser(String packageName, int userId);
}