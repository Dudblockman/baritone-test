package nrl.actorsim.minecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileNameUtil;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.OptionalLong;

/**
 * This was reverse-engineered by looking through the following code:
 * 1. TitleScreen.initWidgetsNormal() - look at the menu.singleplayer button, which leads to:
 * 2. SelectWorldScreen.init - which shows the selectWorld.create action loads a new screen:
 * 3. CreateWorldScreen.method_31130(this[parent) - which calls
 * 4.   DynamicRegistryManager.Impl impl = DynamicRegistryManager.create()
 * 5.   return new CreateWorldScreen(screen,  //ignore
 * 6.             DataPackSettings.SAFE_MODE,
 * 7.             new MoreOptionsDialog(impl,
 * 8.                                   GeneratorOptions.getDefaultOptions(impl.get(Registry.DIMENSION_TYPE_KEY),
 * 9.                                                                impl.get(Registry.BIOME_KEY),
 * 10.                                                                impl.get(Registry.NOISE_SETTINGS_WORLDGEN)),
 * 11.                                                                Optional.of(GeneratorType.DEFAULT),
 * 12.                                                                OptionalLong.empty()));
 * which is another call to the constructor
 * 13. CreateWorldScreen(screen, impl,  dataPackSettings, moreOptionsDialog) and which calls or sets
 * 14.   this.currentMode = CreateWorldScreen.Mode.SURVIVAL;
 * 15.   this.field_24289 = Difficulty.NORMAL;  //Difficulty
 * 16.   this.field_24290 = Difficulty.NORMAL;  //Difficulty but not sure why a second field is needed
 * 17.   this.gameRules = new GameRules();
 * 18.   this.parent = screen;  //ignore
 * 19.   this.levelName = I18n.translate("selectWorld.newWorld"); //ignore
 * 20.   this.field_25479 = dataPackSettings;
 * 21.   this.moreOptionsDialog = moreOptionsDialog;
 * once the user clicks the "selectWorld.create" button after modifying the options, this calls [simplified]
 * 22. CreateWorldScreen.createLevel() which calls:
 * 23.   this.client.method_29970(new SaveLevelScreen(new TranslatableText("createWorld.preparing")));  //ignore
 * 24.   if (this.method_29696())    //seems to create a temporary test to ensure a viable temp save directory; ignore
 * 25.      this.method_30298();     //seems to delete the temporary save directory; ignore
 * 26.      GeneratorOptions generatorOptions = this.moreOptionsDialog.getGeneratorOptions(this.hardcore);
 * 27.      LevelInfo levelInfo2 = new LevelInfo(this.levelNameField.getText().trim(),
 * 28.                                              this.currentMode.defaultGameMode,
 * 29.                                              this.hardcore,
 * 30.                                              this.field_24290,  //Difficulty
 * 31.                                              this.cheatsEnabled && !this.hardcore,
 * 32.                                              this.gameRules,
 * 33.                                              this.field_25479);  //DataPackSettings.SAFE_MODE from above
 * 34.      this.client.method_29607(this.saveDirectoryName, levelInfo2, this.moreOptionsDialog.method_29700(), generatorOptions);
 * where this.moreOptionsDialog.getGeneratorOptions(this.hardcore) calls:
 * 26.1       OptionalLong optionalLong = this.method_30511();  //converts seed to OptionLong; if seed is empty or 0L, returns OptionLong.empty()
 * 26.2       return this.generatorOptions.withHardcore(hardcore, optionalLong); //updates dimension registries with seed returns a GeneratorOptions
 */
class MinecraftWorldCreator {
    String worldName;
    String saveDirectoryName;
    String seed;
    DynamicRegistryManager.Impl impl = DynamicRegistryManager.create();  // line 4
    DataPackSettings dataPackSettings = DataPackSettings.SAFE_MODE;  // line 6
    GameMode gameMode = GameMode.SURVIVAL;  // line 14
    Difficulty difficulty = Difficulty.NORMAL;  // lines 15 and 16
    GameRules gameRules = new GameRules();  // line 17

    boolean hardcore = false; // line 29
    boolean allowCommands = true; // line 31
    GeneratorOptions defaultGeneratorOptions;

    MinecraftWorldCreator(String worldName, String seed) {
        this.worldName = worldName;
        this.saveDirectoryName = worldName;
        this.seed = seed;
        defaultGeneratorOptions = GeneratorOptions.getDefaultOptions(
                impl.get(Registry.DIMENSION_TYPE_KEY),
                impl.get(Registry.BIOME_KEY),
                impl.get(Registry.NOISE_SETTINGS_WORLDGEN));
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, (MinecraftServer) null);
    }

    /**
     * Adapted from package net.minecraft.client.gui.screen.world.CreateWorldScreen.createLevel()
     */
    void createLevel(MinecraftClient minecraftClient) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                GeneratorOptions generatorOptions = defaultGeneratorOptions.withHardcore(hardcore, seedAsOptionalLong());
                LevelInfo levelInfo = new LevelInfo(worldName, gameMode, hardcore, difficulty, allowCommands, gameRules, dataPackSettings);
                saveDirectoryName = FileNameUtil.getNextUniqueName(minecraftClient.getLevelStorage().getSavesDirectory(), this.saveDirectoryName, "");
                saveDirectoryName = saveDirectoryName.replace("(", "").replace(")", "").trim();
                minecraftClient.method_29607(saveDirectoryName, levelInfo, impl, generatorOptions);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Adapted from code in MoreOptionsDialog.method_30511()
     *
     * @return an OptionalLong representing the seed
     */
    private OptionalLong seedAsOptionalLong() {
        OptionalLong returnValue = OptionalLong.empty();
        if (!StringUtils.isEmpty(seed)) {
            OptionalLong tmp = OptionalLong.empty();
            try {
                tmp = OptionalLong.of(Long.parseLong(seed));
            } catch (NumberFormatException var2) {
            }
            if (tmp.isPresent() && tmp.getAsLong() != 0L) {
                returnValue = tmp;
            } else {
                returnValue = OptionalLong.of((long) seed.hashCode());
            }
        }
        return returnValue;
    }
}
