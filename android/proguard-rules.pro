# libGDX loads its backends and natives by reflection.
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
    <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}
-keepclassmembers class com.badlogic.gdx.physics.box2d.World { *; }

# kotlinx.serialization: keep generated serializers for the save file.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.mursal.dewfield.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mursal.dewfield.**$$serializer { *; }
