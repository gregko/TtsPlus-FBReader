@echo off
echo This procedure updates only XML resource files.
echo Manually update text assets and other files (e.g. html)
if NOT EXIST voice-aloud-reader.zip (
  echo No translations to update. Exiting.
  goto :EOF
)
echo Updating translations...
rd /s /q tmp_trans 2> nul
unzip -o -q voice-aloud-reader.zip -d tmp_trans
if ERRORLEVEL 1 (
  echo Error in unzip operation, aborting!
  goto :EOF
)

cd tmp_trans
REM call :CPXML bg bg
REM call :CPXML de de
REM call :CPXML es-ES es
REM call :CPXML fr fr
call :CPXML pl pl
call :CPXML ru ru
REM call :CPXML zh-TW zh-rTW
cd ..
rd /s /q tmp_trans 2> nul
echo All done.
goto :EOF

:CPXML
echo Copying %1 to %2
cd %1
cd FBReader-TTSplus
mkdir ..\..\..\..\TtsPlus-FBReader\res\values-%2 2> nul
copy /y *.xml ..\..\..\..\TtsPlus-FBReader\res\values-%2
cd ..\TtsSetup
mkdir ..\..\..\..\TtsSetup\res\values-%2 2> nul
copy/y *.xml ..\..\..\..\TtsSetup\res\values-%2
cd ..\..
exit /B 0


