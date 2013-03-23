package com.hyperionics.fbreader.plugin.tts_plus;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

public class InstallInfo {

    private static String myPackageName;
    private static PackageManager myPackageManager;

    private InstallInfo() {}

    public static void init(Context context) {
        myPackageManager = context.getPackageManager();
        myPackageName = context.getPackageName();
    }

    private static String myAmaSig =
            "308202ae30820196a003020102020450d38951300d06092a864886f70d01010505003019311730150603550403130e4b6f6" +
                    "368616e69616b2047726567301e170d3132313232303231353532395a170d3430303530373231353532395a301931173015" +
                    "0603550403130e4b6f6368616e69616b204772656730820122300d06092a864886f70d01010105000382010f003082010a0" +
                    "28201010090f47ad6c7f6440454407a24a5cfbbcea916b51ce38971204fb422183fa3fb8810f7ecbdaf250cd231ab6a4403" +
                    "f49a9e701d664947fefb081b55e10da17f0757315b0c75d953c57a26f4c34d69c6b479e56284698d29766a3faeb9fbc0982" +
                    "226ac77f4a519f22871b73212ecb124e9d910e4b47db715b1f20758927bf5d0911851b9281aaad3d7b54490aafe2d682433" +
                    "a36f733c41a711a818d3953235a681b35e63787479821b80370b526a9bb97ab367cfcb0e5a0052421fd305413716f29f4e0" +
                    "7cfae1cdd892de14537c20c1e78f1d62f50659e6163b3ba81fd18be2096bdb8dc7e1692be745cad6ff29cf9ae424653c7da" +
                    "8b56fc06101c7f3953d69f6d330203010001300d06092a864886f70d01010505000382010100498c4c4fef246deda5e70d3" +
                    "397daa79dc145759ac58187609c82e5269108335eb913a50307db4c07268e3dc67254afebba5fb97d876d60ca2d6fc7246e" +
                    "e657222b2530ab49451925c3950762b80cb27db771f792ea1700727031e4d72e22cf2d4b81e4440066983ecb4272ed1a6a9" +
                    "57ed5c6685b99aba983405598c7e462de34824716d0e1d60090620be57e8f496a4e58021847f4d9ee5b49682e10440e00dd" +
                    "7b3d715cf64b8b0dd9a6db8a0fe4bb9ed296e5f076bfd1cda2a9774469a684f4a4a0df5891541ef048f17931cede30ba2c9" +
                    "f1b8c5465d384d75a5d7d042780c54928cf76a263ae0fb3822793d5214ff627812e93b545c8eb23bc6822e3f9392c";


    public static boolean installedFromAma() {
        return installedFromAma(myPackageName);
    }

    public static boolean installedFromAma(String packageName) {
        try {
            PackageInfo pi = myPackageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            for (Signature sig : pi.signatures) {
                String s = sig.toCharsString();
                if (s.equals(myAmaSig))
                    return true;
            }
        } catch (PackageManager.NameNotFoundException e) {}
        return false;
    }

    public static boolean installedFromGoogle() {
        return installedFromGoogle(myPackageName);
    }

    public static boolean installedFromGoogle(String packageName) {
        String pin = null;
        try {
            pin = myPackageManager.getInstallerPackageName(packageName);
        } catch (Exception e) { } // gets IllegalArgumentException: Unknown package: ... if not installed.

        return pin != null && pin.equals("com.android.vending");
    }

    // may be useful sometimes
    public static PackageManager getPackageManager() {
        return myPackageManager;
    }

    public static String getPackageName() {
        return myPackageName;
    }

}
