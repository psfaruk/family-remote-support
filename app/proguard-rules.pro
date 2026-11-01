# ═══════════════════════════════════════════════════════════════════════
# v0.45.0 (audit P6-গ) — R8 keep rules, first wave (release minify ON).
#
# Posture: deliberately CONSERVATIVE. This app is phone-verified, not
# emulator-verified, so the first minified release keeps whole-package
# boundaries for every reflective / JNI-heavy dependency. Size win still
# comes from dead app code + the optimize pass. Loosen later only with a
# device-verified run behind it.
# ═══════════════════════════════════════════════════════════════════════

# ── WebRTC (stream-webrtc-android): JNI callbacks + peer classes are
#    looked up from native code by name; shrinking breaks calls silently
#    mid-session. Keep the whole org.webrtc surface.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── Firebase: listeners (AuthStateListener, EventListener), Firestore
#    custom objects and FCM entry points are reflectively instantiated or
#    dispatched. The BoM ships consumer rules for internals; these keep the
#    surfaces this app actually touches.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── osmdroid: overlays (Marker, CopyrightOverlay, our AccuracyCircleOverlay
#    subclass) are reflectively iterated by the tile engine.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── kotlinx-coroutines: internal volatile fields.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Google Credential Manager / googleid (Google Sign-In): its option
#    bundles go through reflection.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ── ML Kit barcode scanning ships its own consumer rules; zxing is pure
#    java used directly. Nothing extra needed.

# ── Keep our own Services/Receivers/BroadcastReceivers entry points
#    (manifest-declared classes are kept by the AGP rules automatically,
#    listed here for clarity — SessionActionReceiver, PushTokenService,
#    LocationShareService, ScreenShareService, AssistService).
-keep class com.family.remote.support.SessionActionReceiver { *; }
-keep class com.family.remote.support.PushTokenService { *; }
-keep class com.family.remote.support.LocationShareService { *; }
-keep class com.family.remote.support.ScreenShareService { *; }
-keep class com.family.remote.support.AssistService { *; }
