package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import nrl.actorsim.minecraft.BaritoneAdapter;
import nrl.actorsim.minecraft.BaritoneConnector;
import nrl.actorsim.minecraft.MemcachedServer;

import java.io.IOException;

public class ExampleMod implements ModInitializer {
	private static BaritoneAdapter baritoneAdapter;
	private static MemcachedServer memcachedServer;

	@Override
	public void onInitialize() {
		System.out.println("Attempting to initialize Baritone Test Mod");
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		memcachedServer = new MemcachedServer();
		baritoneAdapter = new BaritoneConnector();
		try {
			memcachedServer.initAndStart(baritoneAdapter);
			System.out.print("MemcachedServer started and running");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Hello Fabric world!");
	}
}
