package gg.vape.lunar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


public final class LunarServicePlan {
    public enum Service {
        COSMETICS,
        BADGES,
        EMOTES,
        SPRAYS,
        JAMS
    }

    public static final class ServiceEntry {
        private final Service service;
        private final String targetClassName;
        private final String[] transformMethodNames;
        private final int[] argumentSlots;
        private final String[] hookMethodNames;
        private final String currentLoginMethod;
        private final String legacyLoginMethod;

        private ServiceEntry(Service service, String targetClassName,
                             String[] transformMethodNames, int[] argumentSlots,
                             String[] hookMethodNames, String currentLoginMethod,
                             String legacyLoginMethod) {
            this.service = service;
            this.targetClassName = targetClassName;
            this.transformMethodNames = transformMethodNames.clone();
            this.argumentSlots = argumentSlots.clone();
            this.hookMethodNames = hookMethodNames.clone();
            this.currentLoginMethod = currentLoginMethod;
            this.legacyLoginMethod = legacyLoginMethod;
        }

        public Service service() {
            return this.service;
        }

        public String targetClassName() {
            return this.targetClassName;
        }

        public String[] transformMethodNames() {
            return this.transformMethodNames.clone();
        }

        public int[] argumentSlots() {
            return this.argumentSlots.clone();
        }

        public String[] hookMethodNames() {
            return this.hookMethodNames.clone();
        }

        public String currentLoginMethod() {
            return this.currentLoginMethod;
        }

        public String legacyLoginMethod() {
            return this.legacyLoginMethod;
        }
    }

    private static final List<ServiceEntry> ALL = Collections.unmodifiableList(
            Arrays.asList(
                    new ServiceEntry(Service.COSMETICS,
                            "com.lunarclient.websocket.cosmetic.v2.CosmeticService$Stub",
                            new String[]{"login", "updateOutfit"},
                            new int[]{3, 2},
                            new String[]{"cosmeticLogin", "cosmeticUpdateOutfit"},
                            "OROCHOCOHRHOCCCCRIRROHHHHHRCOO",
                            "HCOHCRIOIIROROHICROHIICOIOHIRO"),
                    new ServiceEntry(Service.BADGES,
                            "com.lunarclient.websocket.badge.v1.BadgeService$Stub",
                            new String[]{"login", "equipBadge"},
                            new int[]{3, 2},
                            new String[]{"badgeLogin", "badgeEquip"},
                            "OROCHRHHOCIOICOCHCOHOOIIHHRICR",
                            "RRCIHCRCIICOCOCIROHHOCHIRCHRCC"),
                    new ServiceEntry(Service.EMOTES,
                            "com.lunarclient.websocket.emote.v1.EmoteService$Stub",
                            new String[]{"login", "useEmote", "updateEquippedEmotes"},
                            new int[]{3, 3, 2},
                            new String[]{"emoteLogin", "emoteUseEmote",
                                    "emoteUpdateEquippedEmotes"},
                            "HCOHCRIOIIROROHICROHIICOIOHIRO",
                            "OCICIHRRRIIHHCICCHICRCOIOIIROC"),
                    new ServiceEntry(Service.SPRAYS,
                            "com.lunarclient.websocket.spray.v1.SprayService$Stub",
                            new String[]{"login", "useSpray", "updateEquippedSprays"},
                            new int[]{3, 3, 2},
                            new String[]{"sprayLogin", "sprayUseSpray",
                                    "sprayUpdateEquippedSprays"},
                            "RCOHOOCCICRIIRHOIRORIRIHCHRHIH",
                            "OCORRORIIOHRCIOIOORCHHOORROROO"),
                    new ServiceEntry(Service.JAMS,
                            "com.lunarclient.websocket.jam.v1.JamService$Stub",
                            new String[]{"login"},
                            new int[]{3},
                            new String[]{"jamLogin"},
                            "OCORRORIIOHRCIOIOORCHHOORROROO",
                            "OROCHRHHOCIOICOCHCOHOOIIHHRICR")));

    private LunarServicePlan() {
    }

    public static List<ServiceEntry> enabled(LunarUnlockSettings settings) {
        Objects.requireNonNull(settings, "settings");
        List<ServiceEntry> enabled = new ArrayList<ServiceEntry>(ALL.size());
        for (ServiceEntry entry : ALL) {
            if (isEnabled(entry.service(), settings)) {
                enabled.add(entry);
            }
        }
        return Collections.unmodifiableList(enabled);
    }

    private static boolean isEnabled(Service service,
                                     LunarUnlockSettings settings) {
        switch (service) {
            case COSMETICS:
                return settings.cosmeticsEnabled();
            case BADGES:
                return settings.badgesEnabled();
            case EMOTES:
                return settings.emotesEnabled();
            case SPRAYS:
                return settings.spraysEnabled();
            case JAMS:
                return settings.jamsEnabled();
            default:
                throw new AssertionError("Unknown Lunar service: " + service);
        }
    }
}
