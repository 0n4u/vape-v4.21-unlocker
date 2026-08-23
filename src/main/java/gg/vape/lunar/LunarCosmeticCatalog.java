package gg.vape.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import gg.vape.Vape;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


public final class LunarCosmeticCatalog {
    private static String cachedPath;
    private static long cachedLength;
    private static long cachedLastModified;
    private static List<Integer> cachedIds;

    private LunarCosmeticCatalog() {
    }

    public static synchronized List<Integer> currentIds() {
        File catalog = new File(System.getProperty("user.home"),
                ".lunarclient/textures/assets/lunar/cosmetics.json");
        try {
            catalog = catalog.getCanonicalFile();
            String canonicalPath = catalog.getPath();
            long length = catalog.length();
            long lastModified = catalog.lastModified();
            if (canonicalPath.equals(cachedPath) && length == cachedLength
                    && lastModified == cachedLastModified
                    && cachedIds != null) {
                return cachedIds;
            }
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(catalog), StandardCharsets.UTF_8)) {
                List<Integer> ids = parse(reader);
                cachedPath = canonicalPath;
                cachedLength = length;
                cachedLastModified = lastModified;
                cachedIds = ids;
                return ids;
            }
        }
        catch (Throwable failure) {
            Vape.debugLog("LUNAR cosmetic catalog load failed: path="
                    + catalog.getAbsolutePath() + " error=" + failure);
            return Collections.emptyList();
        }
    }

    static List<Integer> parse(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        Set<Integer> ids = new TreeSet<Integer>();
        for (JsonElement entry : root.getAsJsonArray()) {
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            JsonElement idElement = object.get("id");
            if (idElement == null || !idElement.isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive primitive = idElement.getAsJsonPrimitive();
            if (!primitive.isNumber()) {
                continue;
            }
            try {
                BigInteger exact = primitive.getAsBigDecimal().toBigIntegerExact();
                int id = exact.intValueExact();
                if (id > 0) {
                    ids.add(id);
                }
            }
            catch (ArithmeticException | NumberFormatException ignored) {
                
            }
        }
        return Collections.unmodifiableList(new ArrayList<Integer>(ids));
    }
}
