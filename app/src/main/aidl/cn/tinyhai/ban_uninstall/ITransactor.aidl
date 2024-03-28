// ITransactor.aidl
package cn.tinyhai.ban_uninstall;

import rikka.parcelablelist.ParcelableListSlice;

// Declare any non-default types here with import statements

interface ITransactor {
    ParcelableListSlice<PackageInfo> getPackages();

    boolean banPackage(in List<String> packageNames, out List<String> bannedPackages);

    boolean freePackage(in List<String> packageNames, out List<String> freedPackages);

    List<String> getAllBannedPackages();

    String sayHello(String hello);

    oneway void onAppLaunched();

    oneway void reloadPrefs();
}