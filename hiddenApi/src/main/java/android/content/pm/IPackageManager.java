package android.content.pm;

import android.annotation.TargetApi;
import android.os.Build;

public interface IPackageManager extends android.os.IInterface {

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    PackageInfo getPackageInfo(String packageName, long flags, int userId);

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    ApplicationInfo getApplicationInfo(String packageName, long flags, int userId);

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);

    abstract class Stub extends android.os.Binder implements android.content.pm.IPackageManager {
        public static android.content.pm.IPackageManager asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}