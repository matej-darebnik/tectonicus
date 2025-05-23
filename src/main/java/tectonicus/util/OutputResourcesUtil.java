/*
 * Copyright (c) 2025 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tectonicus.Block;
import tectonicus.BlockIds;
import tectonicus.BlockTypeRegistry;
import tectonicus.BuildInfo;
import tectonicus.ItemRenderer;
import tectonicus.MemoryMonitor;
import tectonicus.PlayerIconAssembler;
import tectonicus.Portal;
import tectonicus.TileRenderer;
import tectonicus.Version;
import tectonicus.blockregistry.BlockRegistry;
import tectonicus.cache.swap.HddObjectListReader;
import tectonicus.configuration.Configuration;
import tectonicus.configuration.Dimension;
import tectonicus.configuration.ImageFormat;
import tectonicus.configuration.Layer;
import tectonicus.configuration.filter.PlayerFilter;
import tectonicus.configuration.filter.SignFilterType;
import tectonicus.itemmodeldefinitionregistry.ItemModelDefinition;
import tectonicus.itemmodeldefinitionregistry.ItemModelDefinitionRegistry;
import tectonicus.itemregistry.ItemModel;
import tectonicus.itemregistry.ItemRegistry;
import tectonicus.rasteriser.Rasteriser;
import tectonicus.raw.ArmorTrimTag;
import tectonicus.raw.BeaconEntity;
import tectonicus.raw.BedEntity;
import tectonicus.raw.BiomesOld;
import tectonicus.raw.BlockProperties;
import tectonicus.raw.ContainerEntity;
import tectonicus.raw.CustomNameTag;
import tectonicus.raw.DyedColorTag;
import tectonicus.raw.EnchantmentTag;
import tectonicus.raw.EnchantmentsTag;
import tectonicus.raw.Item;
import tectonicus.raw.Player;
import tectonicus.raw.PotionContentsTag;
import tectonicus.raw.StoredEnchantmentsTag;
import tectonicus.texture.TexturePack;
import tectonicus.world.Colors;
import tectonicus.world.Sign;
import tectonicus.world.World;
import tectonicus.world.subset.WorldSubset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static tectonicus.Version.VERSION_12;
import static tectonicus.Version.VERSION_13;
import static tectonicus.Version.VERSION_16;

@Slf4j
@UtilityClass
public class OutputResourcesUtil {
	public static void outputSigns(File outputFile, File signListFile, tectonicus.configuration.Map map) {
		HddObjectListReader<Sign> signsIn = null;
		try {
			signsIn = new HddObjectListReader<>(signListFile);
			outputSigns(outputFile, signsIn, map);
		} catch (Exception e) {
			log.error("Exception: ", e);
		} finally {
			if (signsIn != null)
				signsIn.close();
		}
	}

	private static void outputSigns(File signFile, HddObjectListReader<Sign> signs, tectonicus.configuration.Map map) throws IOException {
		log.info("Exporting signs to {}", signFile.getAbsolutePath());

		Files.deleteIfExists(signFile.toPath());

		try (JsArrayWriter jsWriter = new JsArrayWriter(signFile, map.getId() + "_signData")) {

			WorldSubset worldSubset = map.getWorldSubset();
			Sign sign = new Sign();
			while (signs.hasNext()) {
				signs.read(sign);
				String message = "\"" + sign.getText(0) + "/n" + sign.getText(1) + "/n" + sign.getText(2) + "/n" + sign.getText(3) + "\"";
				if (map.getSignFilter().getType() == SignFilterType.OBEY)
					message = "\"/nOBEY/n/n\"";

				Map<String, String> signArgs = new HashMap<>();

				final float worldX = sign.getX() + 0.5f;
				final float worldY = sign.getY();
				final float worldZ = sign.getZ() + 0.5f;

				String posStr = "new WorldCoord(" + worldX + ", " + worldY + ", " + worldZ + ")";
				signArgs.put("worldPos", posStr);
				signArgs.put("message", message);
				if (map.getSignFilter().getType() == SignFilterType.OBEY) {
					signArgs.put("text1", "\"\"");
					signArgs.put("text2", "\"OBEY\"");
					signArgs.put("text3", "\"\"");
					signArgs.put("text4", "\"\"");
				} else {
					signArgs.put("text1", "\"" + sign.getText(0) + "\"");
					signArgs.put("text2", "\"" + sign.getText(1) + "\"");
					signArgs.put("text3", "\"" + sign.getText(2) + "\"");
					signArgs.put("text4", "\"" + sign.getText(3) + "\"");
				}

				if (worldSubset.containsBlock(sign.getX(), sign.getZ())) {
					jsWriter.write(signArgs);
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}

	public static void outputPlayers(File playersFile, File imagesDir, tectonicus.configuration.Map map, List<Player> players, PlayerIconAssembler playerIconAssembler) {
		try {
			Files.deleteIfExists(playersFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		FileUtils.ensureExists(imagesDir);

		log.info("Exporting players to {}", playersFile.getAbsolutePath());

		int numOutput = 0;
		ExecutorService executor = Executors.newCachedThreadPool();
		try (JsArrayWriter jsWriter = new JsArrayWriter(playersFile, map.getId() + "_playerData")) {

			PlayerFilter playerFilter = map.getPlayerFilter();
			WorldSubset worldSubset = map.getWorldSubset();
			for (Player player : players) {
				if (playerFilter.passesFilter(player)) {
					Vector3d position = player.getPosition();
					if (worldSubset.containsBlock(position.x, position.z)) {
						log.debug("\texporting {}", player.getName());

						Map<String, String> args = new HashMap<>();

						Vector3d pos = player.getPosition();
						args.put("name", "\"" + player.getName() + "\"");

						String posStr = "new WorldCoord(" + pos.x + ", " + pos.y + ", " + pos.z + ")";
						args.put("worldPos", posStr);

						args.put("health", "" + player.getHealth());
						args.put("food", "" + player.getFood());
						args.put("air", "" + player.getAir());

						args.put("xpLevel", "" + player.getXpLevel());
						args.put("xpTotal", "" + player.getXpTotal());

						jsWriter.write(args);

						File iconFile = new File(imagesDir, player.getName() + ".png");
						PlayerIconAssembler.WriteIconTask task = playerIconAssembler.new WriteIconTask(player, iconFile);
						executor.submit(task);

						numOutput++;
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		} finally {
			executor.shutdown();
		}
		log.debug("Exported {} players", numOutput);
	}

	public static void outputBeds(File exportDir, tectonicus.configuration.Map map, List<Player> players, Queue<BedEntity> beds) {
		File bedsFile = new File(exportDir, "beds.js");
		try {
			Files.deleteIfExists(bedsFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		log.info("Exporting beds to {}", bedsFile.getAbsolutePath());

		int numOutput = 0;

		try (JsArrayWriter jsWriter = new JsArrayWriter(bedsFile, map.getId() + "_bedData")) {

			if (map.getDimension() == Dimension.OVERWORLD) // Beds only exist in the overworld dimension
			{
				WorldSubset worldSubset = map.getWorldSubset();
				PlayerFilter filter = map.getPlayerFilter();
				for (Player player : players) {
					if (filter.isShowBeds() && filter.passesFilter(player) && player.getSpawnDimension() == Dimension.OVERWORLD && player.getSpawnPosition() != null) {
						Map<String, String> bedArgs = new HashMap<>();

						Vector3l spawn = player.getSpawnPosition();

						if (worldSubset.containsBlock(spawn.x, spawn.z)) {
							log.debug("\texporting {}'s bed", player.getName());

							bedArgs.put("playerName", "\"" + player.getName() + "\"");

							String posStr = "new WorldCoord(" + spawn.x + ", " + spawn.y + ", " + spawn.z + ")";
							bedArgs.put("worldPos", posStr);
							
							if (!beds.isEmpty()) {
								for (BedEntity bed : beds) {
									if (bed.getX() == spawn.x && bed.getY() == spawn.y && bed.getZ() == spawn.z) {
										bedArgs.put("color", "\"" + Colors.byId(bed.getColor()).getName() + "\"");
									}
								}
							} else {
								bedArgs.put("color", "\"red\"");
							}

							jsWriter.write(bedArgs);
							numOutput++;
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}

		log.debug("Exported {} beds", numOutput);
	}

	public static void outputRespawnAnchors(File exportDir, tectonicus.configuration.Map map, List<Player> players) {
		File anchorsFile = new File(exportDir, "respawnAnchors.js");
		try {
			Files.deleteIfExists(anchorsFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		log.info("Exporting respawn anchors to {}", anchorsFile.getAbsolutePath());

		int numOutput = 0;

		try (JsArrayWriter jsWriter = new JsArrayWriter(anchorsFile, map.getId() + "_respawnAnchorData")) {

			if (map.getDimension() == Dimension.NETHER) // Respawn anchors only work in the nether dimension
			{
				WorldSubset worldSubset = map.getWorldSubset();
				PlayerFilter filter = map.getPlayerFilter();
				for (Player player : players) {
					if (filter.isShowRespawnAnchors() && filter.passesFilter(player) && player.getSpawnDimension() == Dimension.NETHER && player.getSpawnPosition() != null) {
						Map<String, String> anchorArgs = new HashMap<>();

						Vector3l spawn = player.getSpawnPosition();

						if (worldSubset.containsBlock(spawn.x, spawn.z)) {
							log.debug("\texporting {}'s respawn anchor", player.getName());

							anchorArgs.put("playerName", "\"" + player.getName() + "\"");

							String posStr = "new WorldCoord(" + spawn.x + ", " + spawn.y + ", " + spawn.z + ")";
							anchorArgs.put("worldPos", posStr);


							jsWriter.write(anchorArgs);
							numOutput++;
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}

		log.debug("Exported {} respawn anchors", numOutput);
	}

	public static List<Portal> outputPortals(File outFile, File portalListFile, tectonicus.configuration.Map map) {
		List<Portal> portals = new ArrayList<>();

		try {
			HddObjectListReader<Portal> portalsIn = new HddObjectListReader<>(portalListFile);
			portals = outputPortals(outFile, portalsIn, map);
			portalsIn.close();
		} catch (Exception e) {
			log.error("Exception: ", e);
		}

		return portals;
	}

	private static List<Portal> outputPortals(File portalFile, HddObjectListReader<Portal> portalPositions, tectonicus.configuration.Map map) throws IOException {
		log.info("Exporting portals...");

		Files.deleteIfExists(portalFile.toPath());

		List<Portal> portals = new ArrayList<>();
		try (JsArrayWriter jsWriter = new JsArrayWriter(portalFile, map.getId() + "_portalData")) {
			if (portalPositions.hasNext()) {
				long prevX;
				long prevY;
				long prevZ;
				long firstX;
				long firstZ;

				Portal portal = new Portal();
				portalPositions.read(portal);
				firstX = portal.getX();
				firstZ = portal.getZ();
				prevX = portal.getX();
				prevY = portal.getY();
				prevZ = portal.getZ();

				while (portalPositions.hasNext()) {
					portalPositions.read(portal);

					//Find the horizontal center portal block location
					if ((portal.getX() == prevX && portal.getZ() == prevZ + 1) || (portal.getX() == prevX + 1 && portal.getZ() == prevZ)) {
						prevX = portal.getX();
						prevY = portal.getY();
						prevZ = portal.getZ();
					} else {
						portals.add(new Portal(prevX + (firstX - prevX) / 2, prevY, prevZ + (firstZ - prevZ) / 2));
						prevX = portal.getX();
						prevY = portal.getY();
						prevZ = portal.getZ();
						firstX = portal.getX();
						firstZ = portal.getZ();
					}
				}
				portals.add(new Portal(portal.getX() + ((firstX - prevX) / 2), portal.getY(), portal.getZ() + (firstZ - prevZ) / 2));

				WorldSubset worldSubset = map.getWorldSubset();
				for (Portal p : portals) {
					final float worldX = p.getX();
					final float worldY = p.getY();
					final float worldZ = p.getZ();

					Map<String, String> portalArgs = new HashMap<>();
					String posStr = "new WorldCoord(" + worldX + ", " + worldY + ", " + worldZ + ")";
					portalArgs.put("worldPos", posStr);

					if (worldSubset.containsBlock(p.getX(), p.getZ())) {
						jsWriter.write(portalArgs);
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}

		log.debug("Exported {} portals", portals.size());
		return portals;
	}

	public static void outputViews(File outputFile, File viewsListFile, tectonicus.configuration.Map map) {
		HddObjectListReader<Sign> viewsIn = null;
		try {
			viewsIn = new HddObjectListReader<>(viewsListFile);
			outputViews(outputFile, viewsIn, map);
		} catch (Exception e) {
			log.error("Exception: ", e);
		} finally {
			if (viewsIn != null)
				viewsIn.close();
		}
	}

	private static void outputViews(File viewsFile, HddObjectListReader<Sign> views, tectonicus.configuration.Map map) throws IOException {
		log.info("Exporting views...");

		Files.deleteIfExists(viewsFile.toPath());

		try (JsArrayWriter jsWriter = new JsArrayWriter(viewsFile, map.getId() + "_viewData")) {
			Sign sign = new Sign();
			while (views.hasNext()) {
				views.read(sign);

				Map<String, String> viewArgs = new HashMap<>();

				final float worldX = sign.getX() + 0.5f;
				final float worldY = sign.getY();
				final float worldZ = sign.getZ() + 0.5f;

				String posStr = "new WorldCoord(" + worldX + ", " + worldY + ", " + worldZ + ")";
				viewArgs.put("worldPos", posStr);

				StringBuilder text = new StringBuilder();
				for (int i = 0; i < 4; i++) {
					if (!sign.getText(i).startsWith("#")) {
						text.append(sign.getText(i)).append(" ");
					}
				}

				viewArgs.put("text", "\"" + text.toString().trim() + "\"");

				ImageFormat imageFormat = map.getViewConfig().getImageFormat();
				String filename = map.getId() + "/Views/View_" + sign.getX() + "_" + sign.getY() + "_" + sign.getZ() + "." + imageFormat.getExtension();
				viewArgs.put("imageFile", "\"" + filename + "\"");

				if (map.getWorldSubset().containsBlock(sign.getX(), sign.getZ())) {
					jsWriter.write(viewArgs);
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}

	public static void outputChests(File chestFile, tectonicus.configuration.Map map, ConcurrentLinkedQueue<ContainerEntity> chestList) {
		log.info("Exporting chests to {}", chestFile.getAbsolutePath());

		try {
			Files.deleteIfExists(chestFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		try (JsArrayWriter jsWriter = new JsArrayWriter(chestFile, map.getId() + "_chestData")) {
			WorldSubset worldSubset = map.getWorldSubset();
			for (ContainerEntity entity : chestList) {
                                if ("left".equals(entity.getType())) {
                                        // Skip left part of large chests (we will merge them with right part and display them as one sigle large chest)
                                        continue;
                                }
                            
				float worldX = entity.getX() + 0.5f;
				float worldY = entity.getY();
				float worldZ = entity.getZ() + 0.5f;
				Map<String, String> chestArgs = new HashMap<>();

                                String items = "[\r\n";
                                for (var item : entity.getItems()) {
                                        items += outputItem(item, false);
                                }
                                if ("right".equals(entity.getType())) {
                                        // Find right part and add its items
                                        int leftX = entity.getX();
                                        int leftY = entity.getY();
                                        int leftZ = entity.getZ();

                                        switch (entity.getFacing()) {
                                                case "east":
                                                        leftZ -= 1;
                                                        break;
                                                case "north":
                                                        leftX -= 1;
                                                        break;
                                                case "south":
                                                        leftX += 1;
                                                        break;
                                                case "west":
                                                        leftZ += 1;
                                                        break;
                                        }
                                        
                                        for (ContainerEntity left : chestList) {
                                                if (left.getX()!=leftX || left.getY()!=leftY || left.getZ()!=leftZ || !"left".equals(left.getType())) {
                                                     continue;
                                                }
                                                for (var item : left.getItems()) {
                                                    items += outputItem(item, true);
                                                }
                                                break;
                                        }
                                }
                                items += "\t\t]";
                                
				chestArgs.put("worldPos", "new WorldCoord(" + worldX + ", " + worldY + ", " + worldZ + ")");
                                chestArgs.put("name", "\"" + entity.getCustomName() + "\"");
                                chestArgs.put("items", items);
                                if ("right".equals(entity.getType())) {
                                        chestArgs.put("large", "true");
                                }

				if (worldSubset.containsBlock(entity.getX(), entity.getZ())) {
					jsWriter.write(chestArgs);
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}
	
	public static void outputBeacons(File beaconFile, tectonicus.configuration.Map map, Queue<BeaconEntity> beacons) {
		log.info("Exporting beacons to {}", beaconFile.getAbsolutePath());
		
		try {
			Files.deleteIfExists(beaconFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}
		
		try (JsArrayWriter jsWriter = new JsArrayWriter(beaconFile, map.getId() + "_beaconData")) {
			WorldSubset worldSubset = map.getWorldSubset();
			for (BeaconEntity beacon : beacons) {
				float worldX = beacon.getX() + 0.5f;
				float worldY = beacon.getY();
				float worldZ = beacon.getZ() + 0.5f;
				
				Map<String, String> beaconArgs = new HashMap<>();
				beaconArgs.put("worldPos", "new WorldCoord(" + worldX + ", " + worldY + ", " + worldZ + ")");
				beaconArgs.put("levels", Integer.toString(beacon.getLevels()));
				beaconArgs.put("primaryEffect", "\"" + beacon.getPrimaryEffect().name().toLowerCase() + "\"");
				beaconArgs.put("secondaryEffect", "\"" + beacon.getSecondaryEffect().name().toLowerCase() + "\"");
				
				if (worldSubset.containsBlock(beacon.getX(), beacon.getZ())) {
					jsWriter.write(beaconArgs);
				}
			}
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}
        
        private static String outputItem(Item item, Boolean isLeft) {
                String result = "\t\t\t{ id: \"" + item.id + "\", ";

                CustomNameTag customNameTag = item.getComponent(CustomNameTag.class);
                if (customNameTag != null) {
                        if (customNameTag.name != null) {
                                result += "customName: \"" + customNameTag.name + "\", ";
                        }
                }

                DyedColorTag dyedColorTag = item.getComponent(DyedColorTag.class);
                if (dyedColorTag != null) {
                        result += "color: " + dyedColorTag.color + ", ";
                }
                
                int slot = item.slot;
                slot += isLeft ? 3 * 9 : 0;
                result += "count: " + item.count + ", slot: " + slot + ", ";
                
                PotionContentsTag potionContents = item.getComponent(PotionContentsTag.class);
                if (potionContents != null && potionContents.potion != null) {
                        result += "components: { potionContents: { potion: \"" + potionContents.potion + "\" } }, ";
                }
                
                ArmorTrimTag trimTag = item.getComponent(ArmorTrimTag.class);
                if (trimTag != null) {
                        result += "trim: { pattern: \"" + trimTag.pattern + "\", material: \"" + trimTag.material + "\" }, ";
                }
                
                List<EnchantmentTag> enchantments = null;
                
                EnchantmentsTag enchantmentsTag = item.getComponent(EnchantmentsTag.class);
                if (enchantmentsTag != null) {
                        enchantments = enchantmentsTag.enchantments;
                }
                StoredEnchantmentsTag storedEnchantmentsTag = item.getComponent(StoredEnchantmentsTag.class);
                if (storedEnchantmentsTag != null) {
                        enchantments = storedEnchantmentsTag.enchantments;
                }
                
                if (enchantments != null) {
                        result += "enchantments: [";
                        for (var enchantment : enchantments) {
                                result += "{ id: \"" + enchantment.id + "\", level: " + enchantment.level.toString() + " }, ";
                        }
                        result += "], ";
                }

                result += "},\r\n";
                
                return result;
        }

	public static void outputInventoryItemIcons(Configuration args, Rasteriser rasteriser, TexturePack texturePack, BlockTypeRegistry blockTypeRegistry, BlockRegistry blockRegistry, ItemRegistry itemRegistry, ItemModelDefinitionRegistry itemModelDefinitionRegistry) {
		log.info("Rendering icons for inventory items");
		if (texturePack.getVersion().getNumVersion() <= VERSION_12.getNumVersion()) { //A hack to skip rendering icons if the resource pack is for 1.12 or older
			log.info("Skipping icon rendering. Chest items are supported for 1.13 and newer.");
			return;
		}
                
		try {
			ItemRenderer itemRenderer = new ItemRenderer(rasteriser);
			File itemIconDir = new File(args.getOutputDir(), "Images/Items/");
			Files.createDirectories(itemIconDir.toPath());
			
                        for (Map.Entry<String, ItemModelDefinition> entry : itemModelDefinitionRegistry.getModelDefinitions().entrySet()) {
                                final String entryKey = entry.getKey();
				final ItemModelDefinition itemModelDefinition = entry.getValue();
                                File outFile = new File(itemIconDir, entryKey + ".png");

                                System.out.print("\tRendering icon for: " + entryKey + "                    \r"); //prints a carriage return after line
                                log.trace("\tRendering icon for: " + entryKey);

                                String modelName = itemModelDefinition.getModelName();
                                
                                if (modelName == null) {
                                    continue;
                                }
                                
                                if (modelName.startsWith("minecraft:block")) {
                                        itemRenderer.renderInventoryBlockModel(outFile, blockRegistry, texturePack, modelName);
                                }
                                else if (modelName.startsWith("minecraft:item")) {
                                        // We should get entry from itemRegistry and render 2d texture item.
                                        
                                        // We do not need to do anything though, since all items from itemRegistry are iterated through and rendered below.
                                        // This also ensures compatibility with pre-1.21.4 versions
                                }
                        }
                        
			for (Map.Entry<String, ItemModel> entry : itemRegistry.getModels().entrySet()) {
				final String entryKey = entry.getKey();
				final ItemModel itemModel = entry.getValue();
				final ItemModel ultimatePredecessorModel = itemRegistry.findUltimatePredecessor(itemModel);
				File outFile = new File(itemIconDir, entryKey + ".png");
				
				System.out.print("\tRendering icon for: " + entryKey + "                    \r"); //prints a carriage return after line
				log.trace("\tRendering icon for: " + entryKey);
				String modelName = ultimatePredecessorModel.getParent();
				
				if (entryKey.endsWith("_bed")) {
					modelName = "minecraft:" + entryKey;
					itemRenderer.renderBed(outFile, blockTypeRegistry, texturePack, modelName);
					continue;
				}
				
				if (modelName == null) {
					//TODO: multiple models now no longer have parent models because of the new item model definitions
					//System.out.println("Models with no parent: " + entryKey);
					// Do not crash for blocks without parent (Air)
					continue;
				}
				
				// Some items need special handling (shulker boxes, mob heads, banners, etc.)
				if (modelName.endsWith("builtin/entity")) {  //TODO: this doesn't appear to exist anymore with 1.21.4
					modelName = "minecraft:" + entryKey;
					List<Map<String, ArrayList<Float>>> transforms = itemRegistry.getTransformsList(itemModel);
					itemRenderer.renderItem(outFile, blockTypeRegistry, texturePack, modelName, transforms);
					continue;
				}
                                
                                // Items that are just 2d textures
                                if (modelName.endsWith("builtin/generated")) {
                                        final Map<String, String> textures = itemModel.getTextures();
                                        if (textures != null) {
                                                BufferedImage composited = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                                                for (var layer : textures.entrySet()) {
                                                        if (!layer.getKey().startsWith("layer")) {
                                                                // Ignore particles
                                                                continue;
                                                        }
                                                        final String[] layerTexture = layer.getValue().split(":");
                                                        final String namespace = layerTexture.length == 1 ? "minecraft" : layerTexture[0];
                                                        final String textureId = layerTexture.length == 1 ? layerTexture[0] : layerTexture[1];
                                                        
                                                        BufferedImage texture;
                                                        
                                                        if (!layer.getKey().equals("layer0") && textureId.contains("trim")) {
                                                                String[] trimParts = textureId.split("_trim_");
                                                                
                                                                String trim = "assets/" + namespace + "/textures/" + trimParts[0] + "_trim.png";
                                                                String palette = "assets/" + namespace + "/textures/trims/color_palettes/" + trimParts[1] + ".png";
                                                                String keyPalette = "assets/" + namespace + "/textures/trims/color_palettes/trim_palette.png";
                                                                
                                                                texture = texturePack.loadPalettedTexture(trim, palette, keyPalette);
                                                        } else {
                                                                texture = texturePack.loadTexture("assets/" + namespace + "/textures/" + textureId + ".png");
                                                        }
                                                        
                                                        var block = blockRegistry.getBlockModels().getIfPresent(layer.getValue());
                                                        if (block != null) {
                                                                if (block.getElements().get(0).getFaces().values().iterator().next().isTinted()) {
                                                                        Colour4f tintColor = texturePack.getFoliageColor(BiomesOld.FOREST);
                                                                        for (int y=0; y<texture.getHeight(); y++) {
                                                                                for (int x=0; x<texture.getWidth(); x++) {
                                                                                        Colour4f pixel = new Colour4f(texture.getRGB(x, y));
                                                                                        pixel.multiply(tintColor);
                                                                                        texture.setRGB(x, y, pixel.toArgb());
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                              
                                                        if (layer.getKey().equals("layer0") &&
                                                                (
                                                                    textureId.contains("leather_") ||
                                                                    textureId.contains("potion") ||
                                                                    textureId.contains("tipped_arrow")
                                                                )) {
                                                                // Split the leather armor, potion and tipped arrow icons into base layer and overlay
                                                                // so that the base layer can be coloured in CSS due to the colour not being known at this time.
                                                                writeImage(texture, 16, 16, outFile);
                                                                outFile = new File(args.getOutputDir(), "Images/Items/" + entryKey + "_overlay.png");
                                                        } else {
                                                                composited.getGraphics().drawImage(texture, 0, 0, null);
                                                        }
                                                }
                                                writeImage(composited, 16, 16, outFile);
                                                continue;
                                        }
                                }
                                
                                // Inventory block models are not loaded in the registry because they do not have a block state. Let's load them manually
                                if (modelName.contains("_inventory")) {
                                        var model = blockRegistry.loadModel(modelName, "", new HashMap<>(), null);
                                        itemRenderer.renderInventoryBlockModel(outFile, blockRegistry, texturePack, model);
                                        continue;
                                }
                                
                                // Rest of the items
                                itemRenderer.renderInventoryBlockModel(outFile, blockRegistry, texturePack, modelName);
			}
                        System.out.println();
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}

	public static void outputIcons(File exportDir, Configuration args, tectonicus.configuration.Map map, World world, Rasteriser rasteriser)
	{
		BlockTypeRegistry registryOld = world.getBlockTypeRegistry();
		BlockRegistry registry = world.getModelRegistry();
		TexturePack texturePack = world.getTexturePack();
		Version version = world.getWorldInfo().getVersion();

		try {
			ItemRenderer itemRenderer = new ItemRenderer(rasteriser);
			if (texturePack.getVersion().getNumVersion() < VERSION_13.getNumVersion()) {
				itemRenderer.renderBlockOld(new File(exportDir, "Images/Chest.png"), registryOld, texturePack, BlockIds.CHEST, 5);
			} else {
				Map<String, String> properties = new HashMap<>();
				properties.put("facing", "south");
				itemRenderer.renderBlock(new File(exportDir, "Images/Chest.png"), registryOld, registry, texturePack, Block.CHEST, new BlockProperties(properties));
			}

			itemRenderer.renderBed(new File(exportDir, "Images/Bed.png"), registryOld, texturePack);
			itemRenderer.renderCompass(map, new File(exportDir, map.getId()+"/Compass.png"));
			itemRenderer.renderPortal(new File(args.getOutputDir(), "Images/Portal.png"), registryOld, texturePack);
			if (version.getNumVersion() >= VERSION_16.getNumVersion()) {
				itemRenderer.renderBlockModel(new File(args.getOutputDir(), "Images/RespawnAnchor.png"), registry, texturePack, Block.RESPAWN_ANCHOR, "_4");
			}
			itemRenderer.renderBlock(new File(exportDir, "Images/beacon.png"), registryOld, registry, texturePack, Block.BEACON, new BlockProperties());
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}

	public static void outputHtmlResources(TexturePack texturePack, PlayerIconAssembler playerIconAssembler, Configuration config, File exportDir, int numZoomLevels, int tileWidth, int tileHeight) {
		log.info("Writing javascript and image resources...");
		String defaultSkin = config.getDefaultSkin();

		File imagesDir = new File(exportDir, "Images");
		File itemsDir = new File(imagesDir, "Items");
		itemsDir.mkdirs();
		File effectsDir = new File(imagesDir, "effects");
		effectsDir.mkdir();

		FileUtils.extractResource("Images/Spawn.png", new File(imagesDir, "Spawn.png"));
		FileUtils.extractResource("Images/Logo.png", new File(imagesDir, "Logo.png"));

		FileUtils.extractResource("Images/Spacer.png", new File(imagesDir, "Spacer.png"));

		String defaultSkinPath = defaultSkin;
		Version texturePackVersion = texturePack.getVersion();
		switch (texturePackVersion) {
			case VERSION_4:
				writeImage(texturePack.getItem(10, 2), 32, 32, new File(imagesDir, "Sign.png"));
				writeImage(texturePack.getItem(10, 1), 32, 32, new File(imagesDir, "Picture.png"));
				writeImage(texturePack.getItem(7, 1), 32, 32, new File(imagesDir, "IronIcon.png"));
				writeImage(texturePack.getItem(7, 2), 32, 32, new File(imagesDir, "GoldIcon.png"));
				writeImage(texturePack.getItem(7, 3), 32, 32, new File(imagesDir, "DiamondIcon.png"));
				writeImage(texturePack.getItem(13, 2), 32, 32, new File(imagesDir, "Bed.png"));
				if (defaultSkin.equals("steve"))
					defaultSkinPath = "mob/char.png";
				break;

			case VERSION_5:
				writeImage(texturePack.getItem("textures/items/sign.png"), 32, 32, new File(imagesDir, "Sign.png"));
				writeImage(texturePack.getItem("textures/items/painting.png"), 32, 32, new File(imagesDir, "Picture.png"));
				writeImage(texturePack.getItem("textures/items/ingotIron.png"), 32, 32, new File(imagesDir, "IronIcon.png"));
				writeImage(texturePack.getItem("textures/items/ingotGold.png"), 32, 32, new File(imagesDir, "GoldIcon.png"));
				writeImage(texturePack.getItem("textures/items/diamond.png"), 32, 32, new File(imagesDir, "DiamondIcon.png"));
				writeImage(texturePack.getItem("textures/items/bed.png"), 32, 32, new File(imagesDir, "Bed.png"));
				if (defaultSkin.equals("steve"))
					defaultSkinPath = "mob/char.png";
				break;

			default: //assume version is 1.6 or higher
				if (texturePack.fileExists("assets/minecraft/textures/items/bed.png")) { //Use the old bed image for 1.6 - 1.11 if found
					writeImage(texturePack.getItem("assets/minecraft/textures/items/bed.png"), 32, 32, new File(itemsDir, "red_bed.png"));
				}

				String path = "assets/minecraft/textures/items/"; //path for 1.6 - 1.12
				if (texturePackVersion.getNumVersion() >= VERSION_13.getNumVersion()) {
					path = "assets/minecraft/textures/item/"; //path for 1.13+
				}
				
				String beaconPath = "assets/minecraft/textures/gui/container/beacon.png";

				if (texturePack.fileExists(path + "oak_sign.png")) { //1.14 and higher use the new sign image
					writeImage(texturePack.getItem(path + "oak_sign.png"), 32, 32, new File(imagesDir, "Sign.png"));
				} else {
					writeImage(texturePack.getItem(path + "sign.png"), 32, 32, new File(imagesDir, "Sign.png"));
				}
				writeImage(texturePack.getItem(path + "painting.png"), 32, 32, new File(imagesDir, "Picture.png"));
				writeImage(texturePack.getItem(path + "iron_ingot.png"), 32, 32, new File(imagesDir, "IronIcon.png"));
				writeImage(texturePack.getItem(path + "gold_ingot.png"), 32, 32, new File(imagesDir, "GoldIcon.png"));
				writeImage(texturePack.getItem(path + "diamond.png"), 32, 32, new File(imagesDir, "DiamondIcon.png"));
				writeImage(texturePack.getSubImage(beaconPath, 17, 21, 21, 22), 32, 32, new File(imagesDir, "beacon_level_1.png"));
				writeImage(texturePack.getSubImage(beaconPath, 17, 46, 21, 22), 32, 32, new File(imagesDir, "beacon_level_2.png"));
				writeImage(texturePack.getSubImage(beaconPath, 17, 71, 21, 22), 32, 32, new File(imagesDir, "beacon_level_3.png"));
				writeImage(texturePack.getSubImage(beaconPath, 157, 21, 21, 22), 32, 32, new File(imagesDir, "beacon_level_4.png"));
				writeImage(texturePack.getSubImage(beaconPath, 232, 0, 18, 18), 18, 18, new File(effectsDir, "none.png"));
				//TODO: for older resource packs we need to get the effect icons from assets/minecraft/textures/gui/container/inventory.png (1.13 and older use this texture)
				try (FileSystem fs = FileSystems.newFileSystem(Paths.get(texturePack.getZipStack().getBaseFileName()), null);
					 DirectoryStream<Path> entries = Files.newDirectoryStream(fs.getPath("assets/minecraft/textures/mob_effect"))) {
					for (Path entry : entries) {
						String filename = entry.getFileName().toString();
						writeImage(texturePack.getItem(entry.toString()), 18, 18, new File(effectsDir, entry.getFileName().toString()));
					}
				} catch (IOException e) {
					log.warn("No effect images found.");
					log.trace("Error: ", e);
				}

				if (defaultSkin.equals("steve") || defaultSkin.equals("alex") || defaultSkin.equals("ari") || defaultSkin.equals("efe") || defaultSkin.equals("kai") || defaultSkin.equals("makena")
						|| defaultSkin.equals("noor") || defaultSkin.equals("sunny") || defaultSkin.equals("zuri")) {

					defaultSkinPath = "assets/minecraft/textures/entity/player/wide/" + defaultSkin + ".png";
					if (!texturePack.fileExists(defaultSkinPath)) {
						defaultSkinPath = "assets/minecraft/textures/entity/steve.png";
						//Check for Alex skin which was added in 1.8
						if (defaultSkin.equals("alex") && texturePack.fileExists("assets/minecraft/textures/entity/alex.png")) {
							defaultSkinPath = "assets/minecraft/textures/entity/alex.png";
						}
					}
				}
		}

                writeImage(texturePack.getEmptyHeartImage(), 18, 18, new File(imagesDir, "EmptyHeart.png"));
                writeImage(texturePack.getHalfHeartImage(), 18, 18, new File(imagesDir, "HalfHeart.png"));
                writeImage(texturePack.getFullHeartImage(), 18, 18, new File(imagesDir, "FullHeart.png"));

                writeImage(texturePack.getEmptyFoodImage(), 18, 18, new File(imagesDir, "EmptyFood.png"));
                writeImage(texturePack.getHalfFoodImage(), 18, 18, new File(imagesDir, "HalfFood.png"));
                writeImage(texturePack.getFullFoodImage(), 18, 18, new File(imagesDir, "FullFood.png"));

		writeImage(texturePack.getEmptyAirImage(), 18, 18, new File(imagesDir, "EmptyAir.png"));
		writeImage(texturePack.getFullAirImage(), 18, 18, new File(imagesDir, "FullAir.png"));

		writeImage(texturePack.getChestImage(), 176, 78, new File(imagesDir, "SmallChest.png"));
		writeImage(texturePack.getLargeChestImage(), 176, 132, new File(imagesDir, "LargeChest.png"));
                
                // Write font texture
                writeImage(texturePack.getFont().getFontSheet(), 128, 128, new File(imagesDir, "Font.png"));

		// Write default player icon
		BufferedImage defaultSkinIcon = texturePack.getItem(defaultSkinPath);
		if (defaultSkinIcon == null) {
			log.warn("Unable to find default skin!");
		} else {
			playerIconAssembler.writeDefaultIcon(defaultSkinIcon, new File(imagesDir, "PlayerIcons/Tectonicus_Default_Player_Icon.png"));
		}
                
                // Extract enchanted glint texture
                extractFile(texturePack, "assets/minecraft/textures/misc/enchanted_glint_item.png", new File(imagesDir, "EnchantedGlint.png"), true);

		// Extract Leaflet resources
                File scriptsDir = new File(exportDir, "Scripts");
		extractMapResources(scriptsDir);
                
                // Extract localized texts
                extractFile(texturePack, "assets/minecraft/lang/en_us.json", new File(scriptsDir, "localizations.json"), false);

		List<String> scriptResources = new ArrayList<>();
		scriptResources.add("marker.js");
		scriptResources.add("controls.js");
		scriptResources.add("minecraftProjection.js");
		scriptResources.add("containers.js");
		scriptResources.add("main.js");
		outputMergedJs(new File(exportDir, "Scripts/tectonicus.js"), scriptResources, numZoomLevels, config, tileWidth, tileHeight);
	}

	public static void writeImage(BufferedImage img, final int width, final int height, File file) {
		try {
			BufferedImage toWrite;
			if (img.getWidth() != width || img.getHeight() != height) {
				toWrite = new BufferedImage(width, height, img.getType());
				toWrite.getGraphics().drawImage(img, 0, 0, width, height, null);
			} else {
				toWrite = img;
			}
			ImageIO.write(toWrite, "png", file);
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}

        private void extractFile(TexturePack texturePack, String filepath, File outputFile, boolean minecraftJarLoaded) {
                if (texturePack.fileExists(filepath)) {
                        try {
                                try (var stream = texturePack.getZipStack().getStream(filepath, minecraftJarLoaded)) {
                                        Files.copy(stream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                }
                        } catch (IOException e) {
                                log.error("Exception: ", e);
                        }
                }
        }

	private static void extractMapResources(File scriptsDir) {
		scriptsDir.mkdirs();
		File scriptImagesDir = new File(scriptsDir, "images");
		scriptImagesDir.mkdirs();

		FileUtils.extractResource("math.js", new File(scriptsDir, "math.js"));
		FileUtils.extractResource("leaflet.js", new File(scriptsDir, "leaflet.js"));
		FileUtils.extractResource("leaflet.css", new File(scriptsDir, "leaflet.css"));
		FileUtils.extractResource("tectonicusStyles.css", new File(scriptsDir, "tectonicusStyles.css"));
		FileUtils.extractResource("Images/layers.png", new File(scriptImagesDir, "layers.png"));
		FileUtils.extractResource("Images/layers-2x.png", new File(scriptImagesDir, "layers-2x.png"));
		FileUtils.extractResource("Images/marker-icon.png", new File(scriptImagesDir, "marker-icon.png"));
		FileUtils.extractResource("Images/marker-icon-2x.png", new File(scriptImagesDir, "marker-icon-2x.png"));
		FileUtils.extractResource("Images/marker-shadow.png", new File(scriptImagesDir, "marker-shadow.png"));
		FileUtils.extractResource("popper.min.js", new File(scriptsDir, "popper.min.js"));
		FileUtils.extractResource("tippy-bundle.umd.min.js", new File(scriptsDir, "tippy-bundle.umd.min.js"));
		FileUtils.extractResource("tippy-light-theme.css", new File(scriptsDir, "tippy-light-theme.css"));
	}
        
	private void outputMergedJs(File outFile, List<String> inputResources, int numZoomLevels, Configuration config, int tileWidth, int tileHeight)
	{
		InputStream in = null;
		final int scale = (int)Math.pow(2, numZoomLevels);
		try (PrintWriter writer = new PrintWriter(new FileOutputStream(outFile)))
		{
			for (String res : inputResources)
			{
				in = TileRenderer.class.getClassLoader().getResourceAsStream(res);

				assert in != null;
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));

				String line;
				while ((line = reader.readLine()) != null)
				{
					StringBuilder outLine = new StringBuilder();

					List<Util.Token> tokens = Util.split(line);

					while (!tokens.isEmpty())
					{
						Util.Token first = tokens.remove(0);
						if (first.isReplaceable)
						{
							if (first.value.equals("tileWidth")) {
								outLine.append(tileWidth);
							} else if (first.value.equals("tileHeight")) {
								outLine.append(tileHeight);
							} else if (first.value.equals("maxZoom")) {
								outLine.append(numZoomLevels);
							} else if (first.value.equals("mapCoordScaleFactor")) {
								outLine.append(scale);
								outLine.append(".0"); // Append .0 so that it's treated as float in the javascript
							} else if (first.value.equals("showSpawn")) {
								outLine.append(config.showSpawn());
							} else if (first.value.equals("signsInitiallyVisible")) {
								outLine.append(config.areSignsInitiallyVisible());
							} else if (first.value.equals("playersInitiallyVisible")) {
								outLine.append(config.arePlayersInitiallyVisible());
							} else if (first.value.equals("portalsInitiallyVisible")) {
								outLine.append(config.arePortalsInitiallyVisible());
							} else if (first.value.equals("bedsInitiallyVisible")) {
								outLine.append(config.areBedsInitiallyVisible());
							} else if (first.value.equals("respawnAnchorsInitiallyVisible")) {
								outLine.append(config.areRespawnAnchorsInitiallyVisible());
							} else if (first.value.equals("spawnInitiallyVisible")) {
								outLine.append(config.isSpawnInitiallyVisible());
							} else if (first.value.equals("viewsInitiallyVisible")) {
								outLine.append(config.areViewsInitiallyVisible());
							} else if (first.value.equals("chestsInitiallyVisible")) {
								outLine.append(config.isChestsInitiallyVisible());
							} else if (first.value.equals("beaconsInitiallyVisible")) {
								outLine.append(config.isBeaconsInitiallyVisible());
							}
						}
						else
						{
							outLine.append(first.value);
						}
					}
					writer.write(outLine.append("\n").toString());
				}

				writer.flush();
			}
		}
		catch (Exception e)
		{
			log.error("Exception: ", e);
		}
		finally
		{
			try
			{
				if (in != null) {
					in.close();
				}
			}
			catch (Exception e) {}
		}
	}

	public static void outputContents(File outputFile, Configuration config)
	{
		try {
			Files.deleteIfExists(outputFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		log.info("Writing master contents to {}", outputFile.getAbsolutePath());

		try (PrintWriter writer = new PrintWriter(outputFile))
		{
			writer.println("tileSize = "+config.getTileSize()+";");
			writer.println("maxZoom = "+config.getNumZoomLevels()+";");
			writer.println();

			writer.println("var contents = ");
			writer.println("[");

			List<tectonicus.configuration.Map> maps = config.getMaps();
			for (int i=0; i<maps.size(); i++)
			{
				tectonicus.configuration.Map m = maps.get(i);

				writer.println("\t{");

				writer.println("\t\tid: \""+m.getId()+"\",");
				writer.println("\t\tname: \""+m.getName()+"\",");
				writer.println("\t\tplayers: "+m.getId()+"_playerData,");
				writer.println("\t\tbeds: "+m.getId()+"_bedData,");
				writer.println("\t\trespawnAnchors: "+m.getId()+"_respawnAnchorData,");
				writer.println("\t\tbeacons: "+m.getId()+"_beaconData,");
				writer.println("\t\tsigns: "+m.getId()+"_signData,");
				writer.println("\t\tportals: "+m.getId()+"_portalData,");
				writer.println("\t\tviews: "+m.getId()+"_viewData,");
				writer.println("\t\tchests: "+m.getId()+"_chestData,");
				writer.println("\t\tblockStats: "+m.getId()+"_blockStats,");
				writer.println("\t\tworldStats: "+m.getId()+"_worldStats,");
				writer.println("\t\tworldVectors: "+m.getId()+"_worldVectors,");

				writer.println("\t\tlayers:");
				writer.println("\t\t[");
				for (int j=0; j<m.numLayers(); j++)
				{
					Layer l = m.getLayer(j);

					writer.println("\t\t\t{");

					writer.println("\t\t\t\tid: \""+l.getId()+"\",");
					writer.println("\t\t\t\tname: \""+l.getName()+"\",");
					writer.println("\t\t\t\tdimension: \"" + m.getDimension() + "\",");
					writer.println("\t\t\t\tbackgroundColor: \""+l.getBackgroundColor()+"\",");
					writer.println("\t\t\t\timageFormat: \""+l.getImageFormat().getExtension()+"\",");
					writer.println("\t\t\t\tisPng: \""+l.getImageFormat().isPng()+"\"");

					if (j < m.numLayers()-1)
						writer.println("\t\t\t},");
					else
						writer.println("\t\t\t}");
				}
				writer.println("\t\t]");

				if (i < maps.size()-1)
					writer.println("\t},");
				else
					writer.println("\t}");
			}

			writer.println("]");
		}
		catch (Exception e)
		{
			log.error("Exception: ", e);
		}
	}

	public static File outputHtml(File exportDir, Configuration config) throws IOException {
		File outputHtmlFile = new File(exportDir, config.getOutputHtmlName());
		log.info("Writing html to {}", outputHtmlFile.getAbsolutePath());

		URL url = OutputResourcesUtil.class.getClassLoader().getResource("mapWithSigns.html");
		if (url == null)
			throw new IOException("resource not found");
		try (Scanner scanner = new Scanner(url.openStream());
			 PrintWriter writer = new PrintWriter(new FileOutputStream(outputHtmlFile)))
		{
			while (scanner.hasNext())
			{
				String line = scanner.nextLine();
				StringBuilder outLine = new StringBuilder();

				List<Util.Token> tokens = Util.split(line);

				while (!tokens.isEmpty())
				{
					Util.Token first = tokens.remove(0);
					if (first.isReplaceable) {
						if (first.value.equals("title")) {
							outLine.append(config.getHtmlTitle());
						} else if (first.value.equals("styleIncludes")) {
							switch(config.getUseCdn()) {
								case "unpkg":
									outLine.append("<link rel=\"stylesheet\" href=\"https://unpkg.com/tippy.js@6/themes/light.css\" />\n");
									outLine.append("\t\t<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9/dist/leaflet.css\" integrity=\"sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=\" crossorigin=\"\" />");
									break;
								case "cdnjs":
									outLine.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/tippy.js/6.3.7/themes/light.min.css\" integrity=\"sha512-zpbTFOStBclqD3+SaV5Uz1WAKh9d2/vOtaFYpSLkosymyJKnO+M4vu2CK2U4ZjkRCJ7+RvLnISpNrCfJki5JXA==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\" />\n");
									outLine.append("\t\t<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css\" integrity=\"sha512-Zcn6bjR/8RZbLEpLIeOwNtzREBAJnUKESxces60Mpoj+2okopSAcSUIUOseddDm0cxnGQzxIR7vJgsLZbdLE3w==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\" />");
									break;
								case "jsdelivr":
									outLine.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/tippy.js@6/themes/light.css\" />\n");
									outLine.append("\t\t<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/leaflet@1.9/dist/leaflet.min.css\">");
									break;
								default:
									outLine.append("<link rel=\"stylesheet\" href=\"Scripts/tippy-light-theme.css\" />");
									outLine.append("\t\t<link rel=\"stylesheet\" href=\"Scripts/leaflet.css\" />");
							}
						} else if (first.value.equals("customStyleIncludes")) {
							if (config.getCustomStyle() != null) {
								outLine.append("<link rel=\"stylesheet\" href=\"Scripts/");
								outLine.append(config.getCustomStyle());
								outLine.append("\" />");
							}
						} else if (first.value.equals("customScriptIncludes")) {
							if (config.getCustomScript() != null) {
								outLine.append("<script src=\"Scripts/");
								outLine.append(config.getCustomScript());
								outLine.append("\"></script>");
							}
						} else if (first.value.equals("scriptIncludes")) {
							switch(config.getUseCdn()) {
								case "unpkg":
									outLine.append("<script src=\"https://unpkg.com/@popperjs/core@2\"></script>\n");
									outLine.append("\t\t<script src=\"https://unpkg.com/tippy.js@6\"></script>\n");
									outLine.append("\t\t<script src=\"https://unpkg.com/leaflet@1.9/dist/leaflet.js\" integrity=\"sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=\" crossorigin=\"\"></script>\n");
									break;
								case "cdnjs":
									outLine.append("<script src=\"https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.11.8/umd/popper.min.js\" integrity=\"sha512-TPh2Oxlg1zp+kz3nFA0C5vVC6leG/6mm1z9+mA81MI5eaUVqasPLO8Cuk4gMF4gUfP5etR73rgU/8PNMsSesoQ==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"></script>\n");
									outLine.append("\t\t<script src=\"https://cdnjs.cloudflare.com/ajax/libs/tippy.js/6.3.7/tippy-bundle.umd.min.js\" integrity=\"sha512-gbruucq/Opx9jlHfqqZeAg2LNK3Y4BbpXHKDhRC88/tARL/izPOE4Zt2w6X9Sn1UeWaGbL38zW7nkL2jdn5JIw==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"></script>\n");
									outLine.append("\t\t<script src=\"https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js\" integrity=\"sha512-BwHfrr4c9kmRkLw6iXFdzcdWV/PGkVgiIyIWLLlTSXzWQzxuSg4DiQUCpauz/EWjgk5TYQqX/kvn9pG1NpYfqg==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"></script>\n");
									break;
								case "jsdelivr":
									outLine.append("<script src=\"https://cdn.jsdelivr.net/npm/@popperjs/core@2/dist/umd/popper.min.js\"></script>\n");
									outLine.append("\t\t<script src=\"https://cdn.jsdelivr.net/npm/tippy.js@6/dist/tippy-bundle.umd.min.js\"></script>\n");
									outLine.append("\t\t<script src=\"https://cdn.jsdelivr.net/npm/leaflet@1.9/dist/leaflet.min.js\"></script>\n");
									break;
								default:
									outLine.append("<script src=\"Scripts/popper.min.js\"></script>\n");
									outLine.append("\t\t<script src=\"Scripts/tippy-bundle.umd.min.js\"></script>\n");
									outLine.append("\t\t<script src=\"Scripts/leaflet.js\"></script>\n");
							}
							
							String templateStart = "		<script src=\"";
							String templateEnd = "\"></script>\n";
                                                        
							for (tectonicus.configuration.Map map : config.getMaps())
							{
								outLine.append(templateStart);
								outLine.append(map.getId()).append("/players.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/beds.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/respawnAnchors.js");
								outLine.append(templateEnd);
								
								outLine.append(templateStart);
								outLine.append(map.getId()).append("/beacons.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/portals.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/signs.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/views.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/chests.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/worldVectors.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/blockStats.js");
								outLine.append(templateEnd);

								outLine.append(templateStart);
								outLine.append(map.getId()).append("/worldStats.js");
								outLine.append(templateEnd);

								// Any per layer includes?
							}
						}
					} else {
						outLine.append(first.value);
					}
				}

				writer.write(outLine.append("\n").toString());
			}

			writer.flush();
		}
		catch (Exception e)
		{
			log.error("Exception: ", e);
		}

		return outputHtmlFile;
	}

	public static void outputRenderStats(File exportDir, MemoryMonitor memoryMonitor, final String timeTaken)
	{
		File statsFile = new File(new File(exportDir, "Scripts"), "stats.js");
		try {
			Files.deleteIfExists(statsFile.toPath());
		} catch (IOException e) {
			log.error("Exception: ", e);
		}

		log.info("Exporting stats to {}", statsFile.getAbsolutePath());

		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy");
		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm z");
		final String renderedDateStr = dateFormat.format( new Date() );
		final String renderedTimeStr = timeFormat.format( new Date() );

		try (JsObjectWriter jsWriter = new JsObjectWriter(statsFile)) {

			Map<String, Object> stats = new HashMap<>();

			stats.put("tectonicusVersion", BuildInfo.getVersion());

			stats.put("renderTime", timeTaken);
			stats.put("renderedOnDate", renderedDateStr);
			stats.put("renderedOnTime", renderedTimeStr);
			stats.put("peakMemoryBytes", memoryMonitor.getPeakMemory());

			jsWriter.write("stats", stats);
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
	}
}
