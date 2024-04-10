package android.content.pm;

public interface IPackageDeleteObserver2 {
    void onPackageDeleted(String packageName, int returnCode, String msg);
}
