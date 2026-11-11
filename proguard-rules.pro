# R8 / ProGuard Keep Rules for BaobabTD

# Keep models for JSON and Firebase serialization
-keep class td.teladoum.baobab.models.** { *; }
-keepclassmembers class td.teladoum.baobab.models.** { *; }

# Keep Firebase Database, Firestore & Auth
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep AdMob
-keep class com.google.android.gms.ads.** { *; }

# Keep Facebook SDK
-keep class com.facebook.** { *; }

# Keep Glide
-keep class com.bumptech.glide.** { *; }

# Keep CanHub Image Cropper
-keep class com.canhub.cropper.** { *; }
