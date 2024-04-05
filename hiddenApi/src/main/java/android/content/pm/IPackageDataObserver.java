package android.content.pm;

public interface IPackageDataObserver {
    void onRemoveCompleted(String packageName, boolean succeeded);
}
