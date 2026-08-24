package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OemGuideRecipeTest {
    @Test
    public void samsungRecipeUsesSoftwareInformationAndBuildNumber() {
        String copy = rows(OemFamily.SAMSUNG);
        assertTrue(copy.contains("Información de software"));
        assertTrue(copy.contains("Número de compilación"));
    }

    @Test
    public void motorolaRecipeProvidesBothStableEntryRoutes() {
        OemGuideRecipe recipe = recipe(OemFamily.MOTOROLA);
        assertTrue(rows(OemFamily.MOTOROLA).contains("Acerca del teléfono"));
        assertTrue(recipe.developerOptions().help().copy().contains("Sistema"));
    }

    @Test
    public void xiaomiRecipeUsesDetailedInformationAndOsVariant() {
        OemGuideRecipe recipe = recipe(OemFamily.XIAOMI_FAMILY);
        String copy = String.join(" ", recipe.developerOptions().rows());
        assertTrue(copy.contains("Información detallada y especificaciones"));
        assertTrue(copy.contains("OS o MIUI"));
        assertTrue(String.join(" ", recipe.wirelessDebugging().rows()).contains("Ajustes adicionales"));
    }

    private static String rows(OemFamily family) {
        return String.join(" ", recipe(family).developerOptions().rows());
    }

    private static OemGuideRecipe recipe(OemFamily family) {
        return OemGuideRecipe.forProfile(new DeviceProfile("maker", "brand", "model", "16", 36, "", family));
    }
}
