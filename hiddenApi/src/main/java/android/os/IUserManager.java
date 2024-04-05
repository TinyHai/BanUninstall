package android.os;

public interface IUserManager extends IInterface {

    int[] getProfileIds(int userId, boolean enabledOnly);

    abstract class Stub extends android.os.Binder implements IUserManager {
        public static IUserManager asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
