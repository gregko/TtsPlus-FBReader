
set apkfile=TtsPlus-FBReader
del *.apk

copy ..\build\outputs\apk\release\%apkfile%-release.apk .\%apkfile%.apk

zip -d %apkfile%.apk META-INF/*
unzip %apkfile%.apk AndroidManifest.xml

REM ------------------- armeabi-v7a ---------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/arm64-v8a/* lib/x86/*
aminc AndroidManifest.xml 2
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_armeabi-v7a.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore \users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_armeabi-v7a.apk hyperionics
zipalign 4 %apkfile%_armeabi-v7a.apk %apkfile%_armeabi-v7a-aligned.apk
del %apkfile%_armeabi-v7a.apk
ren %apkfile%_armeabi-v7a-aligned.apk %apkfile%_armeabi-v7a.apk

REM ------------------- arm64-v8a ------------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/armeabi-v7a/* lib/x86/*
aminc AndroidManifest.xml 1
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_arm64-v8a.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore \users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_arm64-v8a.apk hyperionics
zipalign 4 %apkfile%_arm64-v8a.apk %apkfile%_arm64-v8a-aligned.apk
del %apkfile%_arm64-v8a.apk
ren %apkfile%_arm64-v8a-aligned.apk %apkfile%_arm64-v8a.apk

REM ------------------- x86 ---------------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/arm64-v8a/* lib/armeabi-v7a/*
aminc AndroidManifest.xml 8
zip -f %apkfile%.zip
ren %apkfile%.zip %apkfile%_x86.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore \users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%_x86.apk hyperionics
zipalign 4 %apkfile%_x86.apk %apkfile%_x86-aligned.apk
del %apkfile%_x86.apk
ren %apkfile%_x86-aligned.apk %apkfile%_x86.apk

del AndroidManifest.xml

REM ----------------- All but arm64-v8a -----------------
copy/y %apkfile%.apk %apkfile%.zip
zip -d %apkfile%.zip lib/arm64-v8a/*
del %apkfile%.apk
ren %apkfile%.zip %apkfile%.apk
jarsigner -sigalg SHA1withRSA -digestalg SHA1 -keystore \users\greg\.android\Hyperionics.keystore -storepass HyperDroidek %apkfile%.apk hyperionics
zipalign 4 %apkfile%.apk %apkfile%-aligned.apk
del %apkfile%.apk
ren %apkfile%-aligned.apk %apkfile%.apk


:done

