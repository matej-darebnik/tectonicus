/*
 * Copyright (c) 2024 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tectonicus.texture.VersionJson;
import tectonicus.texture.ZipStack;
import tectonicus.util.FileUtils;
import tectonicus.util.OsDetect;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;


@Slf4j
@UtilityClass
public class Minecraft
{
	@Setter
	private static boolean useOldColorPalette;

	@Getter
	@Setter
	private static int worldVersion;
	@Getter
	@Setter
	private static int chunkHeight;
	
	public static boolean useOldColorPalette()
	{
		return useOldColorPalette;
	}
	
	public static File findMinecraftDir()
	{
		File result;
		
		File userHome = new File(System.getProperty("user.home"));
		
		if (OsDetect.isMac())
		{
			result = new File(userHome, "Library/Application Support/minecraft");
		}
		else if (OsDetect.isWindows())
		{
			result = new File(System.getenv("APPDATA"), ".minecraft");
		}
		else
		{
			result = new File(userHome, ".minecraft");
		}
		
		return result;
	}
	
	public static Optional<File> findMatchingMinecraftJar(String worldVersion) {
		File versionsDir = new File(findMinecraftDir(), "versions");
		File minecraftJar = null;
		
		if(versionsDir.exists()) {
			log.info("Trying to find jar matching world version {}", worldVersion);
			List<Path> jars = new ArrayList<>();
			try (Stream<Path> paths = Files.find(versionsDir.toPath(), 2,
					(path, attr) -> attr.isRegularFile() && path.getFileName().toString().toLowerCase().endsWith(".jar"))) {
				jars = paths.collect(toList());
			} catch (IOException e) {
				log.error("Exception: ", e);
			}
			
			for(Path jar : jars) {
				log.trace(jar.getFileName().toString());
				String jarVersion = StringUtils.removeEndIgnoreCase(jar.getFileName().toString(), ".jar");
				try {
					VersionJson versionJson = getVersionJson(new ZipStack(jar.toFile(), null, null));
					if (jarVersion.equals(worldVersion) || versionJson.getName().equals(worldVersion)) { //match exact version
						log.info("Found exact match: {}", jarVersion);
						minecraftJar = jar.toFile();
						break;
					} else { //try to match only major and minor version
						String[] splitWorldVersion = worldVersion.split("\\.");
						String[] splitJarVersion = jarVersion.split("\\.");
						
						if (splitJarVersion.length >= 2 && splitWorldVersion.length >= 2
								&& splitJarVersion[0].equals(splitWorldVersion[0]) && splitJarVersion[1].matches("\\d+") && splitJarVersion[1].equals(splitWorldVersion[1])) {
							if (splitJarVersion.length == 3 && !splitJarVersion[2].matches("\\d+")) { //Ignore pre-release or release candidate versions
								continue;
							}
							minecraftJar = jar.toFile();
						}
					}
				} catch (IOException e) {
					log.error("Exception: ", e);
				}
			}
			if (minecraftJar == null) {
				log.info("No matching jar found.");
			}
		}
		
		return Optional.ofNullable(minecraftJar);
	}
	
	public static VersionJson getVersionJson(ZipStack zipStack) {
		VersionJson versionJson = new VersionJson();
		try {
			if (zipStack.hasFile("version.json")) { //version.json was added in 1.14
				versionJson = FileUtils.getOBJECT_MAPPER().readerFor(VersionJson.class).readValue(zipStack.getStream("version.json"));
			}
		} catch (IOException e) {
			log.error("Failed to read version.json file.", e);
		}
		
		return versionJson;
	}
	
	public static File findLatestMinecraftJar()
	{
		File versionsDir = new File(findMinecraftDir(), "versions");
		
		if(versionsDir.exists())
		{
			log.info("Searching for most recent Minecraft jar...");
			List<Path> jars = new ArrayList<>();
			try (Stream<Path> paths = Files.find(versionsDir.toPath(), 2,
					(path, attr) -> attr.isRegularFile() && path.getFileName().toString().toLowerCase().endsWith(".jar"))){
				jars = paths.collect(toList());
			} catch (IOException e) {
				log.error("Exception: ", e);
			}

			String major = "0";
			String minor = "0";
			String patch = "0";
			for(Path jar : jars)
			{
				String[] version = StringUtils.removeEndIgnoreCase(jar.getFileName().toString(), ".jar").split("\\.");
				try 
				{
					if(version.length == 2 && version[1].matches("\\d+"))
					{
						if(Integer.parseInt(major) < Integer.parseInt(version[0]))
						{
							major = version[0];
							minor = "0";
							patch = "0";
						}
						if(Integer.parseInt(minor) < Integer.parseInt(version[1]))
						{
							minor = version[1];
							patch = "0";
						}
					}
					else if (version.length == 3 && version[2].matches("\\d+"))
					{
						if(Integer.parseInt(major) < Integer.parseInt(version[0]))
						{
							major = version[0];
							minor = "0";
							patch = "0";
						}
						if(Integer.parseInt(minor) < Integer.parseInt(version[1]))
						{
							minor = version[1];
							patch = "0";
						}
						if(Integer.parseInt(minor) == Integer.parseInt(version[1]) && Integer.parseInt(patch) < Integer.parseInt(version[2]))
						{
							patch = version[2];
						}
					}
				}
				catch(NumberFormatException e)
				{
					log.error("Error parsing version number: {}", jar);
				}
			}
			
			String version;
			if(patch.equals("0"))
				version = major + "." + minor;
			else
				version = major + "." + minor + "." + patch;

			return new File(findMinecraftDir(), "versions/" + version + "/" + version + ".jar");
		}
		else	
			return new File(findMinecraftDir(), "bin/minecraft.jar");
	}
	
	public static File findWorldDir(final int index)
	{
		assert (index >= 0);
		assert (index < 5);
		
		return new File(Minecraft.findMinecraftDir(), "saves/World"+(index+1));
	}

	public static boolean isValidWorldDir(Path worldDir)
	{
		if (worldDir == null)
			return false;
		
		return Files.exists(findLevelDat(worldDir));
	}
	
	public static boolean isValidMinecraftJar(File minecraftJar) //TODO:  This is only used by the old Swing GUI, refactor to remove it
	{
		if (minecraftJar == null)
			return false;
		
		if (!minecraftJar.exists())
			return false;
		
		try
		{
			ZipStack zips = new ZipStack(minecraftJar, null, null);
			
			return zips.hasFile("terrain.png") || zips.hasFile("textures/blocks/activatorRail.png") || zips.hasFile("assets/minecraft/textures/blocks/rail_activator.png");
		}
		catch (Exception e)
		{
			log.error("Exception: ", e);
		}
		
		return false;
	}

	public static Path findLevelDat(Path worldDir)
	{
		if (worldDir == null)
			return null;
		
		return worldDir.resolve("level.dat");
	}
	
	public static File findPlayersDir(File worldDir)
	{
		if (worldDir == null)
			return null;
		
		File newDir = new File(worldDir, "playerdata");
		if (newDir.exists())
			return newDir;
		else
			return new File(worldDir, "players");
	}

	public static Path findServerPlayerFile(Path worldDir, String name)
	{	
		if (worldDir == null)
			return null;
		Path json = worldDir.getParent().resolve(name + ".json");

		Path txt = null;
		if (name.equals("whitelist"))
			txt = worldDir.getParent().resolve("white-list.txt");
		else
			txt = worldDir.getParent().resolve(name + ".txt");
		
		if (Files.exists(json) && !Files.isDirectory(json))
		{
			return json;
		}
		else if (Files.exists(txt) && !Files.isDirectory(txt))
		{		
			return txt;
		}
		else
		{
			return worldDir.getParent();
		}
	}

	/** Look for dimensionDir/region/*.mcr or dimensionDir/region/*.mca */
	public static boolean isValidDimensionDir(File dimensionDir)
	{
		File regionDir = new File(dimensionDir, "region");
		if (!regionDir.exists())
			return false;
		
		File[] mcRegionFiles = regionDir.listFiles(new McRegionFileFilter());
		
		File[] anvilRegionFiles = regionDir.listFiles(new AnvilFileFilter());
		
		return (mcRegionFiles != null && mcRegionFiles.length > 0)
				|| (anvilRegionFiles != null && anvilRegionFiles.length > 0);
	}
}
