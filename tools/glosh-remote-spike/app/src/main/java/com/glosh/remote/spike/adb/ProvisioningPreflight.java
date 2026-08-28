package com.glosh.remote.spike.adb;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the minimum non-secret state needed before a Device Owner attempt. */
public final class ProvisioningPreflight {
    public static final String EXPECTED_PACKAGE = "com.contentfilter.user.dev";
    public static final String EXPECTED_RECEIVER =
            "com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver";
    public static final String EXPECTED_COMPONENT = EXPECTED_PACKAGE + "/" + EXPECTED_RECEIVER;

    private static final Pattern USER_PATTERN = Pattern.compile("UserInfo\\{(\\d+):");
    private static final Pattern ACCOUNT_COUNT_PATTERN =
            Pattern.compile("(?m)^\\s*Accounts:\\s*(\\d+)\\s*$");
    private static final Pattern ACCOUNT_TYPE_PATTERN =
            Pattern.compile("Account \\{[^\\r\\n}]*type=([^}\\r\\n]+)}");

    public enum OwnerState {
        NONE,
        GLOSH,
        OTHER
    }

    public record Snapshot(
            int userCount,
            boolean hasPrimaryUser,
            int accountCount,
            Map<String, Integer> accountTypes,
            OwnerState ownerState) {
        public Snapshot {
            accountTypes = Collections.unmodifiableMap(new LinkedHashMap<>(accountTypes));
        }

        public boolean eligible() {
            if (ownerState == OwnerState.GLOSH) {
                return hasPrimaryUser;
            }
            return ownerState == OwnerState.NONE
                    && userCount == 1
                    && hasPrimaryUser
                    && accountCount == 0;
        }

        public String blockReason() {
            if (ownerState == OwnerState.OTHER) {
                return "Existe otro Device/Profile Owner.";
            }
            if (!hasPrimaryUser) {
                return "No se encontró el usuario principal 0.";
            }
            if (ownerState == OwnerState.NONE && userCount != 1) {
                return "Device Owner nuevo exige un único usuario principal.";
            }
            if (ownerState == OwnerState.NONE && accountCount != 0) {
                return "Retirá manualmente las cuentas antes de Device Owner.";
            }
            return "";
        }
    }

    private ProvisioningPreflight() {
    }

    public static Snapshot parse(String users, String owners, String accounts) {
        int userCount = 0;
        boolean hasPrimary = false;
        Matcher usersMatcher = USER_PATTERN.matcher(safe(users));
        while (usersMatcher.find()) {
            userCount++;
            hasPrimary |= "0".equals(usersMatcher.group(1));
        }

        String ownerText = safe(owners);
        String ownerLower = ownerText.toLowerCase(Locale.ROOT);
        OwnerState ownerState;
        if (ownerText.contains(EXPECTED_COMPONENT)
                && (ownerLower.contains("deviceowner") || ownerLower.contains("device owner"))) {
            ownerState = OwnerState.GLOSH;
        } else if (ownerLower.contains("no owners")
                || ownerLower.matches("(?s).*\\b0\\s+owners?\\b.*")) {
            ownerState = OwnerState.NONE;
        } else {
            ownerState = OwnerState.OTHER;
        }

        String primaryAccounts = primaryUserSection(safe(accounts));
        Matcher countMatcher = ACCOUNT_COUNT_PATTERN.matcher(primaryAccounts);
        if (!countMatcher.find()) {
            throw new IllegalArgumentException("No se pudo determinar la cantidad de cuentas del usuario 0.");
        }
        int accountCount = Integer.parseInt(countMatcher.group(1));
        Map<String, Integer> types = new LinkedHashMap<>();
        Matcher typeMatcher = ACCOUNT_TYPE_PATTERN.matcher(primaryAccounts);
        while (typeMatcher.find()) {
            String type = typeMatcher.group(1).trim();
            if (!type.isEmpty() && type.length() <= 160) {
                types.merge(type, 1, Integer::sum);
            }
        }
        return new Snapshot(userCount, hasPrimary, accountCount, types, ownerState);
    }

    private static String primaryUserSection(String accounts) {
        int start = accounts.indexOf("User UserInfo{0:");
        if (start < 0) {
            return accounts;
        }
        int next = accounts.indexOf("\nUser UserInfo{", start + 1);
        return next < 0 ? accounts.substring(start) : accounts.substring(start, next);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
