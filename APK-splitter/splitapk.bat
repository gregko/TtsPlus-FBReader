@echo off
set apkfile=TtsPlus-FBReader
del *.apk

copy ..\%apkfile%.apk

zip -d %apkfile%.apk META-INF/*

REM ------------------- armeabi ------------------------
unzip %apkfile%.apk AndroidManifest.xml
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/armeabi-v7a/* lib/x86/* lib/mips/*
aminc AndroidManifest.xml 1
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_armeabi.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore d:\users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_armeabi.apk hyperionics
zipalign 4 %apkfile%_armeabi.apk %apkfile%_armeabi-aligned.apk
del %apkfile%_armeabi.apk
ren %apkfile%_armeabi-aligned.apk %apkfile%_armeabi.apk

REM ------------------- armeabi-v7a ---------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/armeabi/* lib/x86/* lib/mips/*
aminc AndroidManifest.xml 1
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_armeabi-v7a.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore d:\users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_armeabi-v7a.apk hyperionics
zipalign 4 %apkfile%_armeabi-v7a.apk %apkfile%_armeabi-v7a-aligned.apk
del %apkfile%_armeabi-v7a.apk
ren %apkfile%_armeabi-v7a-aligned.apk %apkfile%_armeabi-v7a.apk

REM ------------------- x86 ---------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/armeabi/* lib/armeabi-v7a/* lib/mips/*
aminc AndroidManifest.xml 9
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_x86.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore d:\users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_x86.apk hyperionics
zipalign 4 %apkfile%_x86.apk %apkfile%_x86-aligned.apk
del %apkfile%_x86.apk
ren %apkfile%_x86-aligned.apk %apkfile%_x86.apk

REM ------------------- MIPS ---------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/armeabi/* lib/armeabi-v7a/* lib/x86/*
aminc AndroidManifest.xml 10
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_mips.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore d:\users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_mips.apk hyperionics
zipalign 4 %apkfile%_mips.apk %apkfile%_mips-aligned.apk
del %apkfile%_mips.apk
ren %apkfile%_mips-aligned.apk %apkfile%_mips.apk


del AndroidManifest.xml

REM ----------------- All but MIPS -----------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/mips/*
del %apkfile%.apk
ren %apkfile%.zip %apkfile%.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore d:\users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%.apk hyperionics
zipalign 4 %apkfile%.apk %apkfile%-aligned.apk
del %apkfile%.apk
ren %apkfile%-aligned.apk %apkfile%.apk


:done

