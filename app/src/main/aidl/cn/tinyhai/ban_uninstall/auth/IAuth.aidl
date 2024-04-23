// IAuth.aidl
package cn.tinyhai.ban_uninstall.auth;

// Declare any non-default types here with import statements

interface IAuth {
    boolean hasPwd();
    void setPwd(String newSha256);
    void clearPwd();
    boolean authenticate(String sha256);
    oneway void agree(int opId);
    oneway void prevent(int opId);
}