package edu.utah.cs.qed.actorsim;

import net.fabricmc.api.ModInitializer;
import nrl.actorsim.minecraft.MinecraftConnector;

public class ActorSimMod implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("Initialize ActorSim's Minecraft Mod");
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		System.out.println("Hello Fabric world!");

		MinecraftConnector.getInstance().initServerInstance();
	}
}
