package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class ParceledListSlice<T extends Parcelable> extends BaseParceledListSlice<T>  {

    @Override
    public int describeContents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        throw new UnsupportedOperationException();
    }
}
