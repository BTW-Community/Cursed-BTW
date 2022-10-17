package net.fabricmc.example;

import btw.AddonHandler;
import net.fabricmc.api.ModInitializer;

import java.util.logging.Logger;

public class ExampleMod implements ModInitializer {
	// This logger can be used to write text to the console and the log file.
	// That way, it's clear which mod wrote info, warnings, and errors.
	// public static final Logger LOGGER = Logger.getLogger("modid");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		AddonHandler.logMessage("Hello Fabric world!");
	}

}
