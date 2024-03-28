package android.content.pm;

public interface IPackageManager extends android.os.IInterface {

    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    abstract class Stub extends android.os.Binder implements android.content.pm.IPackageManager {
        public static android.content.pm.IPackageManager asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}