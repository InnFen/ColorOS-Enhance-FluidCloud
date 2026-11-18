$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Enter-AndroidEnvironment.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $moduleRoot 'app/build/policy-tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$junit = Get-ChildItem -LiteralPath (Join-Path $env:GRADLE_USER_HOME 'caches/modules-2/files-2.1/junit/junit/4.13.2') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$hamcrest = Get-ChildItem -LiteralPath (Join-Path $env:GRADLE_USER_HOME 'caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$cp = "$junit;$hamcrest"
$cloudFiles = @('main/java/dev/lsposed/fluidcloud/CloudConfig.java','test/java/dev/lsposed/fluidcloud/CloudConfigTest.java') | ForEach-Object { Join-Path $moduleRoot "app/src/$_" }
$files = @('main/java/com/howard/batterypercentweight/TypographySettings.java','main/java/com/howard/batterypercentweight/WeightConfig.java','main/java/com/howard/batterypercentweight/TextAdjustmentConfig.java','main/java/dev/howard/aodenhance/Config.java','main/java/dev/howard/aodenhance/AodPolicy.java','test/java/com/howard/batterypercentweight/BatteryDefaultOffTest.java','test/java/dev/howard/aodenhance/AodSwitchTest.java') | ForEach-Object { Join-Path $moduleRoot "app/src/$_" }
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $cp -d $classes @files @cloudFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp "$classes;$cp" org.junit.runner.JUnitCore com.howard.batterypercentweight.BatteryDefaultOffTest dev.howard.aodenhance.AodSwitchTest dev.lsposed.fluidcloud.CloudConfigTest
exit $LASTEXITCODE
