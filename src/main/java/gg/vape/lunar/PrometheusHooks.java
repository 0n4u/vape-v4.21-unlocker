package gg.vape.lunar;

import gg.vape.Vape;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


public final class PrometheusHooks {
    private static final String BADGE_SERVICE = "com.lunarclient.websocket.badge.v1";
    private static final String COSMETIC_SERVICE = "com.lunarclient.websocket.cosmetic.v2";
    private static final String EMOTE_SERVICE = "com.lunarclient.websocket.emote.v1";
    private static final String SPRAY_SERVICE = "com.lunarclient.websocket.spray.v1";

    private PrometheusHooks() {
    }

    private static File storageDirectory() {
        String nativeDirectory = System.getProperty("vape.directory");
        File base;
        if (nativeDirectory != null && !nativeDirectory.trim().isEmpty()) {
            base = new File(nativeDirectory, ".vapeclient");
        } else {
            String appData = System.getenv("APPDATA");
            base = new File(appData == null ? System.getProperty("user.home")
                    : appData, ".vapeclient");
        }
        File directory = new File(base, "lunar");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Vape.debugLog("LUNAR storage: vape.directory='"
                + (nativeDirectory == null ? "<unset>" : nativeDirectory)
                + "' APPDATA='" + System.getenv("APPDATA") + "'"
                + " -> " + directory.getAbsolutePath()
                + " exists=" + directory.exists());
        return directory;
    }

    private static File storageFile(String name) {
        return new File(storageDirectory(), name);
    }

    private static void runCallback(Object rpcCallback, Object response) {
        if (rpcCallback == null) {
            Vape.debugLog("LUNAR callback: null callback, skipping");
            return;
        }
        try {
            Method run = Class.forName("com.google.protobuf.RpcCallback")
                    .getMethod("run", Object.class);
            run.invoke(rpcCallback, response);
            Vape.debugLog("LUNAR callback: delivered");
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: callback failed: " + error);
            
            
            
            
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                    ? ((java.lang.reflect.InvocationTargetException)error).getCause()
                    : error;
            if (cause != null) {
                Vape.debugLog("Lunar hook: callback cause: " + cause);
                for (StackTraceElement element : cause.getStackTrace()) {
                    Vape.debugLog("  at " + element);
                }
            }
        }
    }

    private static Object buildSingle(Object messageClass, String[] setters,
                                      Object[] values) {
        try {
            Class<?> clazz = (Class<?>)messageClass;
            Object builder = clazz.getMethod("newBuilder").invoke(null);
            for (int i = 0; i < setters.length; ++i) {
                String[] parts = setters[i].split(";");
                String setter = parts[0];
                Class<?>[] paramTypes = new Class<?>[parts.length - 1];
                for (int p = 1; p < parts.length; ++p) {
                    String type = parts[p];
                    if ("B".equals(type)) {
                        paramTypes[p - 1] = Boolean.TYPE;
                    } else if ("I".equals(type)) {
                        paramTypes[p - 1] = Integer.TYPE;
                    } else if ("J".equals(type)) {
                        paramTypes[p - 1] = Long.TYPE;
                    } else if ("E".equals(type)) {
                        paramTypes[p - 1] = Enum.class;
                    } else {
                        paramTypes[p - 1] = Class.forName(type);
                    }
                }
                Object value = values[i];
                if ("E".equals(parts[parts.length - 1]) && value instanceof Enum) {
                    paramTypes[paramTypes.length - 1] = value.getClass().getSuperclass();
                }
                builder.getClass().getMethod(setter, paramTypes).invoke(builder, value);
            }
            return builder.getClass().getMethod("build").invoke(builder);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: build failed: " + error);
            return null;
        }
    }

    private static Object newBuilder(Class<?> messageClass) {
        try {
            return messageClass.getMethod("newBuilder").invoke(null);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: newBuilder failed: " + error);
            return null;
        }
    }

    private static Object invokeBuilder(Object builder, String setter,
                                        Class<?>[] paramTypes, Object value) {
        try {
            return builder.getClass().getMethod(setter, paramTypes).invoke(builder, value);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: " + setter + " failed: " + error);
            return builder;
        }
    }

    

    public static void badgeLogin(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook badgeLogin invoked");
            Class<?> loginResponse = Class.forName(BADGE_SERVICE + ".LoginResponse");
            Object builder = newBuilder(loginResponse);
            if (builder == null) {
                return;
            }
            invokeBuilder(builder, "setHasAllBadgesFlag",
                    new Class<?>[]{Boolean.TYPE}, true);
            invokeBuilder(builder, "setEquippedBadgeId",
                    new Class<?>[]{Integer.TYPE}, readBadge());
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook badgeLogin delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: badgeLogin failed: " + error);
        }
    }

    public static void badgeEquip(Object equipBadgeRequest) {
        try {
            Vape.debugLog("LUNAR hook badgeEquip invoked");
            Method getBadgeId = equipBadgeRequest.getClass().getMethod("getBadgeId");
            Object value = getBadgeId.invoke(equipBadgeRequest);
            writeBadge(value instanceof Number ? ((Number)value).intValue() : 0);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: badgeEquip failed: " + error);
        }
    }

    

    public static void cosmeticLogin(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook cosmeticLogin invoked");
            Class<?> outfitClass = Class.forName(COSMETIC_SERVICE + ".Outfit");
            Object outfitBuilder = newBuilder(outfitClass);
            if (outfitBuilder == null) {
                return;
            }
            invokeBuilder(outfitBuilder, "setName",
                    new Class<?>[]{String.class}, "Prometheus");
            invokeBuilder(outfitBuilder, "setFavorite",
                    new Class<?>[]{Boolean.TYPE}, true);
            OutfitFile saved = readOutfit();
            if (saved != null && saved.message != null) {
                try {
                    outfitBuilder.getClass().getMethod("mergeFrom", outfitClass)
                            .invoke(outfitBuilder, saved.message);
                }
                catch (Throwable ignored) {
                    
                }
            }
            Object outfit = outfitBuilder.getClass().getMethod("build").invoke(outfitBuilder);

            Class<?> loginResponse = Class.forName(COSMETIC_SERVICE + ".LoginResponse");
            Object builder = newBuilder(loginResponse);
            if (builder == null) {
                return;
            }
            invokeBuilder(builder, "setArtistTools",
                    new Class<?>[]{Boolean.TYPE}, true);
            invokeBuilder(builder, "setTesterTools",
                    new Class<?>[]{Boolean.TYPE}, true);
            
            
            
            
            
            
            {
                List<Integer> ids = LunarCosmeticCatalog.currentIds();
                if (ids.isEmpty()) {
                    Vape.debugLog("Lunar cosmetic catalog unavailable; no ownership applied");
                } else {
                    List<Object> owned = new ArrayList<Object>();
                    try {
                        Class<?> ownedCosmetic = Class.forName(COSMETIC_SERVICE + ".OwnedCosmetic");
                        Method ownedNewBuilder = ownedCosmetic.getMethod("newBuilder");
                        Method ownedSetId = ownedNewBuilder.getReturnType()
                                .getMethod("setCosmeticId", Integer.TYPE);
                        Method ownedBuild = ownedNewBuilder.getReturnType().getMethod("build");
                        for (Integer idValue : ids) {
                            int id = idValue.intValue();
                            Object ownedBuilder = ownedNewBuilder.invoke(null);
                            ownedSetId.invoke(ownedBuilder, id);
                            owned.add(ownedBuild.invoke(ownedBuilder));
                        }
                        invokeBuilder(builder, "addAllOwnedCosmetics",
                                new Class<?>[]{Iterable.class}, owned);
                        Vape.debugLog("LUNAR hook cosmeticLogin ownedCosmetics set ("
                                + owned.size() + ")");
                    }
                    catch (Throwable error) {
                        Vape.debugLog("Lunar hook: ownedCosmetics failed: " + error);
                    }
                }
            }
            invokeBuilder(builder, "addOutfits",
                    new Class<?>[]{outfitClass}, outfit);
            try {
                Class<?> outfitTree = Class.forName(COSMETIC_SERVICE + ".OutfitTree");
                Object treeBuilder = newBuilder(outfitTree);
                
                
                Method getId = outfit.getClass().getMethod("getId");
                Object outfitId = getId.invoke(outfit);
                invokeBuilder(treeBuilder, "setDefaultOutfitId",
                        new Class<?>[]{Class.forName("com.lunarclient.common.v1.Uuid")},
                        outfitId);
                Object tree = treeBuilder.getClass().getMethod("build").invoke(treeBuilder);
                invokeBuilder(builder, "setOutfitTree",
                        new Class<?>[]{outfitTree}, tree);
            }
            catch (Throwable ignored) {
                
            }
            
            
            
            
            if (LunarUnlockSettings.current().lunarPlusAppearanceEnabled()) {
                try {
                    Class<?> colorClass = Class.forName("com.lunarclient.common.v1.Color");
                    Method colorNewBuilder = colorClass.getMethod("newBuilder");
                    Object colorBuilder = colorNewBuilder.invoke(null);
                    Method colorSetColor = colorBuilder.getClass()
                            .getMethod("setColor", Integer.TYPE);
                    colorSetColor.invoke(colorBuilder, 0xA855F7);
                    Object plusColor = colorBuilder.getClass().getMethod("build")
                            .invoke(colorBuilder);
                    invokeBuilder(builder, "setPlusColor",
                            new Class<?>[]{colorClass}, plusColor);
                    int[] palette = {0xA855F7, 0x9333EA, 0x6366F1, 0x0EA5E9,
                            0x10B981, 0xF59E0B, 0xEF4444, 0xEC4899};
                    for (int rgb : palette) {
                        Object cb = colorNewBuilder.invoke(null);
                        colorSetColor.invoke(cb, rgb);
                        Object c = cb.getClass().getMethod("build").invoke(cb);
                        invokeBuilder(builder, "addAvailableLunarPlusColors",
                                new Class<?>[]{colorClass}, c);
                    }
                    Vape.debugLog("LUNAR hook cosmeticLogin plusIcon fields set");
                }
                catch (Throwable error) {
                    Vape.debugLog("Lunar hook: plusIcon fields failed: " + error);
                }
            }
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook cosmeticLogin delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: cosmeticLogin failed: " + error);
        }
    }

    public static void cosmeticUpdateOutfit(Object updateOutfitRequest) {
        try {
            Vape.debugLog("LUNAR hook cosmeticUpdateOutfit invoked");
            Method getOutfit = updateOutfitRequest.getClass().getMethod("getOutfit");
            Object outfit = getOutfit.invoke(updateOutfitRequest);
            writeOutfit(outfit);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: cosmeticUpdateOutfit failed: " + error);
        }
    }

    

    public static void emoteLogin(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook emoteLogin invoked");
            List<Object> equippedEmotes = readDelimited(
                    EMOTE_SERVICE + ".EquippedEmote", "emotes.bin");
            Class<?> loginResponse = Class.forName(EMOTE_SERVICE + ".LoginResponse");
            Object builder = newBuilder(loginResponse);
            if (builder == null) {
                return;
            }
            invokeBuilder(builder, "setHasAllEmotesFlag",
                    new Class<?>[]{Boolean.TYPE}, true);
            invokeBuilder(builder, "addAllEquippedEmotes",
                    new Class<?>[]{Iterable.class}, equippedEmotes);
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook emoteLogin delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: emoteLogin failed: " + error);
        }
    }

    public static void emoteUseEmote(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook emoteUseEmote invoked");
            Class<?> useEmoteResponse = Class.forName(EMOTE_SERVICE + ".UseEmoteResponse");
            Object builder = newBuilder(useEmoteResponse);
            if (builder == null) {
                return;
            }
            try {
                Class<?> status = Class.forName(EMOTE_SERVICE + ".UseEmoteResponse$Status");
                Object statusOk = Enum.valueOf((Class<? extends Enum>)status, "STATUS_OK");
                invokeBuilder(builder, "setStatus",
                        new Class<?>[]{status}, statusOk);
            }
            catch (Throwable ignored) {
                
            }
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook emoteUseEmote delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: emoteUseEmote failed: " + error);
        }
    }

    public static void emoteUpdateEquippedEmotes(Object updateRequest) {
        try {
            Vape.debugLog("LUNAR hook emoteUpdateEquippedEmotes invoked");
            Method getList = updateRequest.getClass().getMethod("getEquippedEmotesList");
            Object list = getList.invoke(updateRequest);
            writeDelimited("emotes.bin", list);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: emoteUpdateEquippedEmotes failed: " + error);
        }
    }

    

    public static void sprayLogin(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook sprayLogin invoked");
            List<Object> equippedSprays = readDelimited(
                    SPRAY_SERVICE + ".EquippedSpray", "sprays.bin");
            Class<?> loginResponse = Class.forName(SPRAY_SERVICE + ".LoginResponse");
            Object builder = newBuilder(loginResponse);
            if (builder == null) {
                return;
            }
            invokeBuilder(builder, "setHasAllSpraysFlag",
                    new Class<?>[]{Boolean.TYPE}, true);
            invokeBuilder(builder, "addAllEquippedSprays",
                    new Class<?>[]{Iterable.class}, equippedSprays);
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook sprayLogin delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: sprayLogin failed: " + error);
        }
    }

    public static void sprayUseSpray(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook sprayUseSpray invoked");
            Class<?> useSprayResponse = Class.forName(SPRAY_SERVICE + ".UseSprayResponse");
            Object builder = newBuilder(useSprayResponse);
            if (builder == null) {
                return;
            }
            try {
                Class<?> status = Class.forName(SPRAY_SERVICE + ".UseSprayResponse$Status");
                Object statusOk = Enum.valueOf((Class<? extends Enum>)status, "STATUS_OK");
                invokeBuilder(builder, "setStatus",
                        new Class<?>[]{status}, statusOk);
            }
            catch (Throwable ignored) {
                
            }
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook sprayUseSpray delivered");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: sprayUseSpray failed: " + error);
        }
    }

    public static void sprayUpdateEquippedSprays(Object updateRequest) {
        try {
            Vape.debugLog("LUNAR hook sprayUpdateEquippedSprays invoked");
            Method getList = updateRequest.getClass().getMethod("getEquippedSpraysList");
            Object list = getList.invoke(updateRequest);
            writeDelimited("sprays.bin", list);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: sprayUpdateEquippedSprays failed: " + error);
        }
    }

    

    public static void jamLogin(Object rpcCallback) {
        try {
            Vape.debugLog("LUNAR hook jamLogin invoked");
            Class<?> ownedJam = Class.forName("com.lunarclient.websocket.jam.v1.OwnedJam");
            Class<?> loginResponse = Class.forName("com.lunarclient.websocket.jam.v1.LoginResponse");
            Object builder = newBuilder(loginResponse);
            if (builder == null) {
                return;
            }
            
            
            
            
            List<Object> jams = new ArrayList<Object>();
            for (int id = 1; id <= 500; ++id) {
                Object jamBuilder = newBuilder(ownedJam);
                if (jamBuilder == null) {
                    continue;
                }
                invokeBuilder(jamBuilder, "setJamId",
                        new Class<?>[]{Integer.TYPE}, id);
                jams.add(jamBuilder.getClass().getMethod("build").invoke(jamBuilder));
            }
            invokeBuilder(builder, "addAllOwnedJams",
                    new Class<?>[]{Iterable.class}, jams);
            Object response = builder.getClass().getMethod("build").invoke(builder);
            Vape.debugLog("LUNAR hook jamLogin delivered (" + jams.size() + " jams)");
            runCallback(rpcCallback, response);
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: jamLogin failed: " + error);
        }
    }

    

    private static void writeBadge(int badgeId) {
        File file = storageFile("badge.bin");
        try (DataOutputStream os = new DataOutputStream(
                new FileOutputStream(file))) {
            
            
            
            os.writeInt(badgeId);
            os.flush();
            Vape.debugLog("LUNAR storage: writeBadge OK -> "
                    + file.getAbsolutePath() + " id=" + badgeId
                    + " (" + file.length() + " bytes)");
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: writeBadge failed: " + error
                    + " path=" + file.getAbsolutePath());
        }
    }

    private static int readBadge() {
        File file = storageFile("badge.bin");
        try (DataInputStream is = new DataInputStream(
                new FileInputStream(file))) {
            if (file.length() >= 4L) {
                int badgeId = is.readInt();
                Vape.debugLog("LUNAR storage: readBadge OK from "
                        + file.getAbsolutePath() + " id=" + badgeId);
                return badgeId;
            }
            
            return is.read();
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR storage: readBadge none at "
                    + file.getAbsolutePath() + " (" + error + ")");
            return 0;
        }
    }

    private static final class OutfitFile {
        final Object message;

        OutfitFile(Object message) {
            this.message = message;
        }
    }

    private static OutfitFile readOutfit() {
        File file = storageFile("outfit.bin");
        try (InputStream is = new FileInputStream(file)) {
            Class<?> outfitClass = Class.forName(COSMETIC_SERVICE + ".Outfit");
            Method parseFrom = outfitClass.getMethod("parseFrom", InputStream.class);
            Object outfit = parseFrom.invoke(null, is);
            Vape.debugLog("LUNAR storage: readOutfit OK from "
                    + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            return new OutfitFile(outfit);
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR storage: readOutfit none at "
                    + file.getAbsolutePath() + " (" + error + ")");
            return null;
        }
    }

    private static void writeOutfit(Object outfit) {
        File file = storageFile("outfit.bin");
        try (OutputStream os = new FileOutputStream(file)) {
            outfit.getClass().getMethod("writeTo", OutputStream.class)
                    .invoke(outfit, os);
            os.flush();
            Vape.debugLog("LUNAR storage: writeOutfit OK -> "
                    + file.getAbsolutePath() + " (" + file.length() + " bytes)");
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: writeOutfit failed: " + error
                    + " path=" + file.getAbsolutePath());
        }
    }

    private static List<Object> readDelimited(String messageClassName,
                                              String fileName) {
        List<Object> messages = new ArrayList<Object>();
        try (InputStream is = new FileInputStream(storageFile(fileName))) {
            Class<?> messageClass = Class.forName(messageClassName);
            Method parseDelimited = messageClass.getMethod(
                    "parseDelimitedFrom", InputStream.class);
            while (is.available() > 0) {
                Object message = parseDelimited.invoke(null, is);
                if (message != null) {
                    messages.add(message);
                }
            }
        }
        catch (Throwable ignored) {
            
        }
        return messages;
    }

    private static void writeDelimited(String fileName, Object list) {
        if (list == null) {
            return;
        }
        try (OutputStream os = new FileOutputStream(storageFile(fileName))) {
            for (Object message : (Iterable<?>)list) {
                message.getClass().getMethod("writeDelimitedTo", OutputStream.class)
                        .invoke(message, os);
            }
        }
        catch (Throwable error) {
            Vape.debugLog("Lunar hook: writeDelimited failed: " + error);
        }
    }
}
