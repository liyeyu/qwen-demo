# 消费端 R8/ProGuard 规则：宿主开启 minifyEnabled 后自动生效，无需宿主手动 copy。
# Gson 按字段名反射反序列化，混淆后字段名会变，必须保留这些模型类的字段名。
-keepclassmembers class com.qianwen.demo.data.ChatMessage { <fields>; }
-keepclassmembers class com.qianwen.demo.data.Conversation { <fields>; }
-keepclassmembers class com.qianwen.demo.data.NewsItem { <fields>; }
-keepclassmembers class com.qianwen.demo.data.ChatStreamEvent { <fields>; }
-keepclassmembers class com.qianwen.demo.data.LocalSnapshot { <fields>; }
-keepclassmembers class com.qianwen.demo.data.LocalSnapshot$** { <fields>; }
-keepclassmembers class com.qianwen.demo.data.ApiModels$** { <fields>; }

# OkHttp/Okio 在 Android 上缺失的可选依赖
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
