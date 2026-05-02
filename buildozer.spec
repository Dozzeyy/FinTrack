[app]

# (str) Title of your application
title = Expense Tracker

# (str) Package name
package.name = exptracker

# (str) Package domain (usually com.yourname)
package.domain = org.exptracker

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (let empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas

# (list) List of inclusions using pattern matching
#source.include_patterns = assets/*,images/*.png

# (list) Source files to exclude (let empty to exclude nothing)
#source.exclude_exts = spec

# (list) List of directory to exclude (let empty to exclude nothing)
#source.exclude_dirs = tests, bin, venv

# (list) List of exclusions using pattern matching
#source.exclude_patterns = license,images/*/*.jpg

# (str) Application versioning (method 1)
version = 2.4

# (list) Application requirements
# comma separated e.g. requirements = sqlite3,kivy
requirements = python3,kivy,sqlite3

# (str) Custom source folders for requirements
# fill this property with any additional directories containing python source
# that you may want to import in your python package
#source.include_exts = py,png,jpg,kv,atlas

# (list) Garden requirements
#garden_requirements =

# (str) Presplash of the application
#presplash.filename = %(source.dir)s/data/presplash.png

# (str) Icon of the application
#icon.filename = %(source.dir)s/data/icon.png

# (str) Supported orientations (one of landscape, sensorLandscape, portrait or all)
orientation = portrait

# (list) List of service to declare
#services = NAME:ENTRYPOINT_TO_PY,NAME2:ENTRYPOINT_TO_PY

#
# OSX Specific
#

#
# Android specific
#

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 0

# (list) Permissions
android.permissions = WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, INTERNET

# (int) Target Android API, should be as high as possible.
android.api = 31

# (int) Minimum API your APK will support.
android.minapi = 21

# (int) Android SDK version to use
#android.sdk = 20

# (str) Android NDK version to use
#android.ndk = 19b

# (str) Android NDK directory (if empty, it will be automatically downloaded.)
#android.ndk_path =

# (str) Android SDK directory (if empty, it will be automatically downloaded.)
#android.sdk_path =

# (str) ANT directory (if empty, it will be automatically downloaded.)
#android.ant_path =

# (bool) If True, then skip trying to update the Android sdk
# This can be useful to avoid excess download. No more updates will be performed once the SDK is installed.
#android.skip_update = False

# (bool) If True, then automatically accept SDK license
# agreements. This is intended for automation only. If set to False,
# the default, you will be shown the license when installing SDK or NDK
#android.accept_sdk_license = False

# (str) Android entry point, default is to use start.py
#android.entrypoint = main.py

# (str) Android app theme, default is ok for Kivy
#android.apptheme = "@android:style/Theme.NoTitleBar"

# (list) Pattern to whitelist for the libp_py.so
#android.library_includes =

# (list) List of Java files to add to the android project (can be java or a directory containing the files)
#android.add_java_src =

# (list) List of Java libs (.jar) to add to the android project
#android.add_jars =

# (list) List of Java libs (.aar) to add to the android project
#android.add_aars =

# (list) Gradle dependencies
#android.add_dependencies =

# (list) External repo to add (currently only maven is supported)
#android.add_src_archives =

# (list) List of Android libraries to add (currently native libraries only)
#android.add_libs_native =

# (str) Android logcat filters to use
#android.logcat_filters = *:S python:D

# (str) Android additional libraries to copy into libs/armeabi
#android.add_libs_armeabi = libs/android/*.so
#android.add_libs_armeabi_v7a = libs/android-v7/*.so
#android.add_libs_arm64_v8a = libs/android-v8/*.so
#android.add_libs_x86 = libs/android-x86/*.so
#android.add_libs_mips = libs/android-mips/*.so

# (bool) Copy library instead of making a libp_py.so
#android.copy_libs = 1

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
android.archs = arm64-v8a, armeabi-v7a

# (int) overrides automatic versionCode computation (including build #: 0)
# android.numeric_version = 1

# (bool) enables Android auto backup feature (default True)
android.allow_backup = True

# (str) XML file for custom drawable configurations
# android.manifest.xml_drawables =

# (str) XML file for custom manifest configurations
# android.manifest.xml_path =

# (str) Android static libraries to add
# android.add_static_libs =

# (list) Android system libraries to add
# android.add_system_libs =

# (str) Android NDK architecture to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.ndk_archs = armeabi-v7a

# (list) List of Android project dependencies
# android.project_dependencies =

# (str) Android entry point for a service, default is to use main.py
# android.service_entrypoint =

# (list) List of Java classes to add to the android project
# android.add_java_classes =

# (str) Android logcat filters to use
# android.logcat_filters = *:S python:D

# (str) Android additional libraries to copy into libs/armeabi
# android.add_libs_armeabi = libs/android/*.so
# android.add_libs_armeabi_v7a = libs/android-v7/*.so
# android.add_libs_arm64_v8a = libs/android-v8/*.so
# android.add_libs_x86 = libs/android-x86/*.so
# android.add_libs_mips = libs/android-mips/*.so

# (bool) Copy library instead of making a libp_py.so
# android.copy_libs = 1

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.archs = arm64-v8a, armeabi-v7a

# (int) overrides automatic versionCode computation (including build #: 0)
# android.numeric_version = 1

# (bool) enables Android auto backup feature (default True)
# android.allow_backup = True

# (str) XML file for custom drawable configurations
# android.manifest.xml_drawables =

# (str) XML file for custom manifest configurations
# android.manifest.xml_path =

# (str) Android static libraries to add
# android.add_static_libs =

# (list) Android system libraries to add
# android.add_system_libs =

# (str) Android NDK architecture to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.ndk_archs = armeabi-v7a

# (list) List of Android project dependencies
# android.project_dependencies =

# (str) Android entry point for a service, default is to use main.py
# android.service_entrypoint =

# (list) List of Java classes to add to the android project
# android.add_java_classes =

# (bool) If True, then skip trying to update the Android sdk
# android.skip_update = False

# (bool) If True, then automatically accept SDK license
# agreements. This is intended for automation only.
# android.accept_sdk_license = False

# (str) Android entry point, default is to use start.py
# android.entrypoint = main.py

# (str) Android app theme, default is ok for Kivy
# android.apptheme = "@android:style/Theme.NoTitleBar"

# (list) Pattern to whitelist for the libp_py.so
# android.library_includes =

# (list) List of Java files to add to the android project (can be java or a directory containing the files)
# android.add_java_src =

# (list) List of Java libs (.jar) to add to the android project
# android.add_jars =

# (list) List of Java libs (.aar) to add to the android project
# android.add_aars =

# (list) Gradle dependencies
# android.add_dependencies =

# (list) External repo to add (currently only maven is supported)
# android.add_src_archives =

# (list) List of Android libraries to add (currently native libraries only)
# android.add_libs_native =

# (str) Android logcat filters to use
# android.logcat_filters = *:S python:D

# (str) Android additional libraries to copy into libs/armeabi
# android.add_libs_armeabi = libs/android/*.so
# android.add_libs_armeabi_v7a = libs/android-v7/*.so
# android.add_libs_arm64_v8a = libs/android-v8/*.so
# android.add_libs_x86 = libs/android-x86/*.so
# android.add_libs_mips = libs/android-mips/*.so

# (bool) Copy library instead of making a libp_py.so
# android.copy_libs = 1

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.archs = arm64-v8a, armeabi-v7a

# (int) overrides automatic versionCode computation (including build #: 0)
# android.numeric_version = 1

# (bool) enables Android auto backup feature (default True)
# android.allow_backup = True

# (str) XML file for custom drawable configurations
# android.manifest.xml_drawables =

# (str) XML file for custom manifest configurations
# android.manifest.xml_path =

# (str) Android static libraries to add
# android.add_static_libs =

# (list) Android system libraries to add
# android.add_system_libs =

# (str) Android NDK architecture to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.ndk_archs = armeabi-v7a

# (list) List of Android project dependencies
# android.project_dependencies =

# (str) Android entry point for a service, default is to use main.py
# android.service_entrypoint =

# (list) List of Java classes to add to the android project
# android.add_java_classes =

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = off, 1 = on)
warn_on_root = 1

# (str) Path to build artifact storage, closed to the project
# bin_dir = ./bin
