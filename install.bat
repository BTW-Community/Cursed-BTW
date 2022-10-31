cd "%~dp0"
rd /s /q "./BTW_dev"
call gradlew.bat --no-daemon downloadAssets
mkdir BTW_dev
tar.exe -xf custom_mappings/mappings_152_converted_yarn_intermediates.zip -C custom_mappings
java -jar libs/tiny-remapper-0.8.6+local-fat.jar %~f1 "BTW_dev/%~nx1" custom_mappings/mappings/mappings.tiny intermediary named %userprofile%/.gradle/caches/fabric-loom/minecraft-1.5.2-intermediary-null.unspecified-1.0.jar
tar.exe -xf %userprofile%/.gradle/caches/fabric-loom/1.5.2-mapped-null.unspecified-1.0/minecraft-1.5.2-mapped-null.unspecified-1.0.jar -C BTW_dev
tar.exe -xf "BTW_dev/%~nx1" -C BTW_dev
call gradlew.bat --no-daemon btwJar
PAUSE