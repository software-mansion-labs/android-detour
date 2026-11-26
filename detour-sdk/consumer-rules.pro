# Detour SDK consumer ProGuard rules
# These rules are automatically included when consumers enable R8/ProGuard.

# Keep Gson model classes used for API serialization/deserialization.
# Gson uses reflection to access @SerializedName fields at runtime.
-keep class com.swmansion.detour.models.SignalMatchRequest { *; }
-keep class com.swmansion.detour.models.DirectMatchRequest { *; }
-keep class com.swmansion.detour.models.LocaleInfo { *; }
-keep class com.swmansion.detour.models.InstallReferrerDetails { *; }
-keep class com.swmansion.detour.models.ReferrerUtm { *; }
-keep class com.swmansion.detour.models.MetaInstallReferrer { *; }
-keep class com.swmansion.detour.models.AdvertisingId { *; }
-keep class com.swmansion.detour.models.AppSetId { *; }
-keep class com.swmansion.detour.identifiers.DeviceIdentifiersReporter$Sent { *; }
-keep class com.swmansion.detour.models.LinkMatchResponse { *; }
-keep class com.swmansion.detour.models.ShortLinkResponse { *; }
# Gson reads and writes the sdk config and its cache by field name.
-keep class com.swmansion.detour.consent.** { *; }
# The app adds play-services-ads-identifier only when it uses the advertising ID.
-dontwarn com.google.android.gms.ads.identifier.**
-dontwarn com.google.android.gms.appset.**
-dontwarn com.google.android.gms.tasks.**
