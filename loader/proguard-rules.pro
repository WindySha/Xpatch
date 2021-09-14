#-keep class com.wind.xposed.entry.XposedModuleEntry {
#    public <init>();
#    public void init();
#}
#-keep class de.robv.android.xposed.**{*;}
#-keep class com.swift.sandhook.**{*;}
#-keep class com.swift.sandhook.xposedcompat.**{*;}
#
#-dontwarn de.robv.android.xposed.XposedHelper
-keep class com.wind.xposed.entry.XposedModuleEntry {
    public <init>();
    public void init();
}
-keep class de.robv.android.xposed.**{*;}

-dontwarn de.robv.android.xposed.XposedHelper