package android.content.pm;

import android.os.Parcelable;

import java.util.List;

abstract class BaseParceledListSlice<T> implements Parcelable {
    public List<T> getList() {
        throw new UnsupportedOperationException();
    }
}
