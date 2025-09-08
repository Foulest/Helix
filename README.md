# Helix

**Helix** is an auto W-Tap mod for Minecraft 1.8.9.

It's disguised as the [Optibye](https://modrinth.com/mod/optibye) mod to avoid detection from server anti-cheat systems.
But, if you want to be extra safe, you can rename the `.jar`, `mcmod.info`, and `modid` in `@Mod` to whatever you want.

## Using Helix

1. Download the latest version of Helix from the Releases section.
2. Insert the downloaded `.jar` file into your Minecraft `mods` folder.
3. Launch Minecraft with the Forge profile.
4. The W-Tap feature will be enabled by default. You can toggle it on or off by entering F5 mode (make sure you're
   looking at your player's back) and pressing the `SEMICOLON` key. You should see your screen cycle through the next F5
   view. This is as discreet as possible to avoid detection.

This mod won't hold up in screenshares, but it's completely undetectable by server-side anti-cheat systems.

It works by simulating button presses instead of modifying your `isSprinting` state, which is what some other W-Tap
modules in hacked clients do, which is very detectable.

## Building Helix

1. Clone the repository and open it in the IDE of your choice.
2. Set up your Gradle environment and by running `setupDecompWorkspace`.
3. Run the `reObfShadowJar` Gradle task to build the mod. The output `.jar` file will be located in the `build/libs`
   directory.
