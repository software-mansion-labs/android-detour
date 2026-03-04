# Detour SDK consumer ProGuard rules
# These rules are automatically included when consumers enable R8/ProGuard.

# Keep Gson model classes used for API serialization/deserialization.
# Gson uses reflection to access @SerializedName fields at runtime.
-keep class com.detour.sdk.models.ProbabilisticFingerprint { *; }
-keep class com.detour.sdk.models.DeterministicFingerprint { *; }
-keep class com.detour.sdk.models.LocaleInfo { *; }
-keep class com.detour.sdk.models.LinkMatchResponse { *; }
-keep class com.detour.sdk.models.ShortLinkResponse { *; }
