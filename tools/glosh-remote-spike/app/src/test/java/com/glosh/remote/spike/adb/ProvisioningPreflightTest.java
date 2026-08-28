package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProvisioningPreflightTest {
    @Test
    public void newOwnerRequiresUserZeroOnlyAndNoAccounts() {
        ProvisioningPreflight.Snapshot value = ProvisioningPreflight.parse(
                "Users:\n UserInfo{0:Owner:13} running\n",
                "0 owners:\n",
                "User UserInfo{0:Owner:13}:\n  Accounts: 0\n");

        assertTrue(value.eligible());
        assertEquals(ProvisioningPreflight.OwnerState.NONE, value.ownerState());
    }

    @Test
    public void accountTypesAreCountedWithoutRetainingNames() {
        ProvisioningPreflight.Snapshot value = ProvisioningPreflight.parse(
                "Users:\n UserInfo{0:Owner:13} running\n",
                "0 owners:\n",
                "User UserInfo{0:Owner:13}:\n"
                        + "  Accounts: 2\n"
                        + "    Account {name=private@example.test, type=com.google}\n"
                        + "    Account {name=secret, type=com.google}\n");

        assertFalse(value.eligible());
        assertEquals(Integer.valueOf(2), value.accountTypes().get("com.google"));
        assertFalse(value.accountTypes().toString().contains("private@example.test"));
        assertFalse(value.accountTypes().toString().contains("secret"));
    }

    @Test
    public void existingGloshOwnerAllowsSignedUpdateWithAccounts() {
        ProvisioningPreflight.Snapshot value = ProvisioningPreflight.parse(
                "Users:\n UserInfo{0:Owner:13} running\n",
                "1 owner:\nUser 0: admin=" + ProvisioningPreflight.EXPECTED_COMPONENT
                        + ",DeviceOwner,Affiliated\n",
                "User UserInfo{0:Owner:13}:\n"
                        + "  Accounts: 1\n"
                        + "    Account {name=hidden, type=com.vendor}\n");

        assertTrue(value.eligible());
        assertEquals(ProvisioningPreflight.OwnerState.GLOSH, value.ownerState());
    }

    @Test
    public void otherOwnerFailsClosed() {
        ProvisioningPreflight.Snapshot value = ProvisioningPreflight.parse(
                "Users:\n UserInfo{0:Owner:13} running\n",
                "1 owner:\nUser 0: admin=com.other/.Admin,DeviceOwner\n",
                "User UserInfo{0:Owner:13}:\n  Accounts: 0\n");

        assertFalse(value.eligible());
        assertEquals(ProvisioningPreflight.OwnerState.OTHER, value.ownerState());
    }
}
