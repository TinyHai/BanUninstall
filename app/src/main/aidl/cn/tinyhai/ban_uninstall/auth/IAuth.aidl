// IAuth.aidl
package cn.tinyhai.ban_uninstall.auth;

import cn.tinyhai.ban_uninstall.auth.entities.OpRecord;

// Declare any non-default types here with import statements

interface IAuth {
    boolean hasPwd();
    void setPwd(String newSha256);
    void clearPwd();
    boolean authenticate(String sha256);
    oneway void agree(int opId);
    oneway void prevent(int opId);

    List<OpRecord> getAllOpRecord();
    void clearAllOpRecord();
}