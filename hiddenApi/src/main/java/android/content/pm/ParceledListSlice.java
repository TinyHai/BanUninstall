package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class ParceledListSlice<T extends Parcelable> extends BaseParceledListSlice<T>  {

    @Override
    public int describeContents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new UnsupportedOperationException();
    }
}
