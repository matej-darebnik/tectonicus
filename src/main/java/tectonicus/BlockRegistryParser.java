/*
 * Copyright (c) 2023 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tectonicus.blockTypes.Air;
import tectonicus.blockTypes.Anvil;
import tectonicus.blockTypes.ArmorStand;
import tectonicus.blockTypes.Banner;
import tectonicus.blockTypes.Beacon;
import tectonicus.blockTypes.Bed;
import tectonicus.blockTypes.BedNew;
import tectonicus.blockTypes.Bell;
import tectonicus.blockTypes.BrewingStand;
import tectonicus.blockTypes.Button;
import tectonicus.blockTypes.Cactus;
import tectonicus.blockTypes.Cake;
import tectonicus.blockTypes.Carpet;
import tectonicus.blockTypes.Cauldron;
import tectonicus.blockTypes.Chest;
import tectonicus.blockTypes.ChestNew;
import tectonicus.blockTypes.ChiseledBookshelf;
import tectonicus.blockTypes.ChorusFlower;
import tectonicus.blockTypes.ChorusPlant;
import tectonicus.blockTypes.CocoaPod;
import tectonicus.blockTypes.Conduit;
import tectonicus.blockTypes.Crops;
import tectonicus.blockTypes.DataSolid;
import tectonicus.blockTypes.DaylightSensor;
import tectonicus.blockTypes.DecoratedPot;
import tectonicus.blockTypes.Dispenser;
import tectonicus.blockTypes.Door;
import tectonicus.blockTypes.DragonEgg;
import tectonicus.blockTypes.EnchantmentTable;
import tectonicus.blockTypes.EndRod;
import tectonicus.blockTypes.EnderPortal;
import tectonicus.blockTypes.EnderPortalFrame;
import tectonicus.blockTypes.Fence;
import tectonicus.blockTypes.FenceGate;
import tectonicus.blockTypes.Fire;
import tectonicus.blockTypes.FlowerPot;
import tectonicus.blockTypes.FruitStem;
import tectonicus.blockTypes.Furnace;
import tectonicus.blockTypes.Glass;
import tectonicus.blockTypes.GlassPane;
import tectonicus.blockTypes.GlazedTerracotta;
import tectonicus.blockTypes.Grass;
import tectonicus.blockTypes.HangingSign;
import tectonicus.blockTypes.Hopper;
import tectonicus.blockTypes.HugeMushroom;
import tectonicus.blockTypes.Ice;
import tectonicus.blockTypes.ItemFrame;
import tectonicus.blockTypes.ItemFrameNew;
import tectonicus.blockTypes.JackOLantern;
import tectonicus.blockTypes.Ladder;
import tectonicus.blockTypes.Leaves;
import tectonicus.blockTypes.Lever;
import tectonicus.blockTypes.Lilly;
import tectonicus.blockTypes.Log;
import tectonicus.blockTypes.MinecartTracks;
import tectonicus.blockTypes.NetherWart;
import tectonicus.blockTypes.Observer;
import tectonicus.blockTypes.Painting;
import tectonicus.blockTypes.PaintingNew;
import tectonicus.blockTypes.PistonBase;
import tectonicus.blockTypes.PistonExtension;
import tectonicus.blockTypes.Plant;
import tectonicus.blockTypes.Portal;
import tectonicus.blockTypes.PressurePlate;
import tectonicus.blockTypes.RedstoneRepeater;
import tectonicus.blockTypes.RedstoneWire;
import tectonicus.blockTypes.ShulkerBox;
import tectonicus.blockTypes.Sign;
import tectonicus.blockTypes.Skull;
import tectonicus.blockTypes.Slab;
import tectonicus.blockTypes.Snow;
import tectonicus.blockTypes.Soil;
import tectonicus.blockTypes.SolidBlockType;
import tectonicus.blockTypes.Stairs;
import tectonicus.blockTypes.TallGrass;
import tectonicus.blockTypes.Torch;
import tectonicus.blockTypes.Trapdoor;
import tectonicus.blockTypes.Tripwire;
import tectonicus.blockTypes.TripwireHook;
import tectonicus.blockTypes.Vines;
import tectonicus.blockTypes.Wall;
import tectonicus.blockTypes.Water;
import tectonicus.blockTypes.Workbench;
import tectonicus.cache.BiomeCache;
import tectonicus.configuration.SignFilter;
import tectonicus.configuration.SignFilterType;
import tectonicus.texture.SubTexture;
import tectonicus.texture.TexturePack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static tectonicus.Version.VERSION_14;
import static tectonicus.Version.VERSION_5;

@Log4j2
public class BlockRegistryParser
{
	private final TexturePack texturePack;
	private final BiomeCache biomeCache;
	private final SignFilter signFilter;
	private final Map<String, BufferedImage> patternImages;
	
	public BlockRegistryParser(TexturePack texturePack, BiomeCache biomeCache, SignFilter signFilter)
	{
		this.texturePack = texturePack;
		this.biomeCache = biomeCache;
		this.signFilter = signFilter;
		
		patternImages = texturePack.loadPatterns();
	}
	
	public void parse(final String resName, BlockTypeRegistry registry)
	{	
		if (resName == null || resName.trim().length() == 0)
			return;
		
		Element root = loadXml(resName, "blockConfig");
		
		if (root == null)
			throw new RuntimeException("Couldn't load block config from: '"+resName+"'");
		
		// TODO: Check version here
		// ..
		
		NodeList children = root.getChildNodes();
		for (int i=0; i<children.getLength(); i++)
		{
			Node n = children.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE)
			{
				Element element = (Element)n;
				
				try
				{
					parse(element, registry);
				}
				catch (Exception e)
				{
					log.error("Error while parsing blockConfig element: "+n+"\n", e);
				}
			}
		}
	}
	
	public static InputStream openStream(String name) throws Exception
	{
		if (name == null)
			return null;
		
		InputStream in = null;
		
		// Try classpath
		in = BlockRegistryParser.class.getClassLoader().getResourceAsStream(name);
		
		if (in == null)
		{
			in = new FileInputStream(new File(name) );
		}
		
		return in;
	}
	
	private void parse(Element element, BlockTypeRegistry registry)
	{
		BlockType blockType = null;
		
		// Every type has an id
		String idStr = element.getAttribute("id");
		IdDataPair id = null;
		if (!idStr.equals("")) {  // Starting with MC 1.13 blocks no longer use numeric ids
			id = parseIdDataPair(idStr);
		}
		String stringId = element.getAttribute("stringId");
		
		// Every type has a name
		String name = element.getAttribute("name");
		
		String nodeName = element.getTagName().toLowerCase();
		
		if (nodeName.equals("solid"))
		{
			SubTexture tex = parseTexture(element, "texture", null);
			SubTexture side = parseTexture(element, "side", tex);
			SubTexture top = parseTexture(element, "top", tex);
			
			String alphaTestStr = element.getAttribute("alphaTest");
			final boolean alphaTest = (alphaTestStr != null && alphaTestStr.equalsIgnoreCase("true"));
			
			blockType = new SolidBlockType(name, side, top, alphaTest);
		}
		else if (nodeName.equals("datasolid"))
		{
			ArrayList<SubTexture> sides = new ArrayList<>();
			ArrayList<SubTexture> tops = new ArrayList<>();
			
			int sideIndex = 0;
			while (true)
			{
				String attribName = "side"+sideIndex;
				sideIndex++;
				
				SubTexture side = parseTexture(element, attribName, null);
				if (side != null)
				{
					sides.add(side);
				}
				else
					break;
			}
			
			int topIndex = 0;
			while (true)
			{
				String attribName = "top"+topIndex;
				topIndex++;
				
				SubTexture top = parseTexture(element, attribName, null);
				if (top != null)
				{
					tops.add(top);
				}
				else
					break;
			}
			
			String alphaTestStr = element.getAttribute("alphaTest");
			final boolean alphaTest = (alphaTestStr != null && alphaTestStr.equalsIgnoreCase("true"));
			
			String transparentStr = element.getAttribute("transparent");
			final boolean transparent = (transparentStr != null && transparentStr.equalsIgnoreCase("true"));
			
			blockType = new DataSolid(name, sides.toArray(new SubTexture[0]), tops.toArray(new SubTexture[0]), alphaTest, transparent);
		}
		else if (nodeName.equals("air"))
		{
			blockType = new Air(name);
		}
		else if (nodeName.equals("water"))
		{
			SubTexture tex = parseTexture(element, "texture", null);
			
			blockType = new Water(name, tex, parseFrame(element));
		}
		else if (nodeName.equals("grass"))
		{
			SubTexture side = parseTexture(element, "dirtSide", null);
			SubTexture grassSide = parseTexture(element, "grassSide", null);
			SubTexture snowSide = parseTexture(element, "snowSide", null);
			SubTexture top = parseTexture(element, "top", null);
			SubTexture bottom = parseTexture(element, "bottom", null);

			String betterGrassMode = element.getAttribute("betterGrass");
			Grass.BetterGrassMode betterGrass =
					betterGrassMode == null ? Grass.BetterGrassMode.None
					: betterGrassMode.equalsIgnoreCase("fast") ? Grass.BetterGrassMode.Fast
					: betterGrassMode.equalsIgnoreCase("fancy") ? Grass.BetterGrassMode.Fancy
					: Grass.BetterGrassMode.None;
			
			blockType = new Grass(name, betterGrass, side, grassSide, snowSide, top, bottom, biomeCache, texturePack);
		}
		else if (nodeName.equals("log"))
		{
			SubTexture side = parseTexture(element, "side", null);
			SubTexture top = parseTexture(element, "top", null);
			
			blockType = new Log(name, side, top);
		}
		else if (nodeName.equals("leaves"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			Color color = parseColor(element, "color", null);

			blockType = new Leaves(name, texture, color, biomeCache, texturePack);
			registry.register(id.id, id.data | 0x4, blockType);
			registry.register(id.id, id.data | 0x8, blockType);
			registry.register(id.id, id.data | 0x4 | 0x8, blockType);
		}
		else if (nodeName.equals("glass"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Glass(name, texture);
		}
		else if (nodeName.equals("bed"))
		{
			SubTexture headTop = parseTexture(element, "headTop", null);
			SubTexture footTop = parseTexture(element, "footTop", null);

			SubTexture headSide = parseTexture(element, "headSide", null);
			SubTexture footSide = parseTexture(element, "footSide", null);

			SubTexture headEdge = parseTexture(element, "headEdge", null);
			SubTexture footEdge = parseTexture(element, "footEdge", null);

			blockType = new Bed(headTop, footTop, headSide, footSide, headEdge, footEdge);
		}
		else if (nodeName.equals("bednew"))
		{
			if (StringUtils.isNotEmpty(stringId)) {
				blockType = new BedNew(stringId, name);
			} else {
				blockType = new BedNew(name);
			}
		}
		else if (nodeName.equals("dispenser"))
		{
			//System.out.println("Warning: Dispenser block type is obsolete. It will be removed in a future version. Use Furnace instead.");

			SubTexture top = parseTexture(element, "top", null);
			SubTexture topBottom = parseTexture(element, "topBottom", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture front = parseTexture(element, "front", null);
			
			blockType = new Dispenser(name, top, topBottom, side, front);
		}
		else if (nodeName.equals("minecarttracks"))
		{
			SubTexture straight = parseTexture(element, "straight", null);
			SubTexture corner = parseTexture(element, "corner", null);
			SubTexture powered= parseTexture(element, "powered", null);
			
			String isStraightStr = element.getAttribute("isStraightOnly");
			final boolean isStraightOnly = (isStraightStr != null && isStraightStr.equalsIgnoreCase("true"));
			
			blockType = new MinecartTracks(name, straight, corner, powered, isStraightOnly);
		}
		else if (nodeName.equals("plant") || nodeName.equals("sapling"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			SubTexture top = parseTexture(element, "top", texture);
			SubTexture bottom = parseTexture(element, "bottom", texture);
			
			blockType = new Plant(name, id.id, top, bottom);
			
			if (name.contains("Sapling"))
				registry.register(id.id, id.data | 0x8, blockType);
		}
		else if (nodeName.equals("tallgrass"))
		{
			SubTexture dead = parseTexture(element, "dead", null);
			SubTexture tall = parseTexture(element, "tall", null);
			SubTexture fern = parseTexture(element, "fern", null);
			
			blockType = new TallGrass(name, dead, tall, fern, biomeCache, texturePack);
		}
		else if (nodeName.equals("slab"))
		{
			SubTexture tex = parseTexture(element, "texture", null);
			SubTexture side = parseTexture(element, "side", tex);
			SubTexture top = parseTexture(element, "top", tex);
			
			blockType = new Slab(name, side, top);
			
			// upsidedown half-slab has bit 0x8
			registry.register(id.id, id.data | 0x8, blockType);
		}
		else if (nodeName.equals("torch"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Torch(name, texture);
		}
		else if (nodeName.equals("stairs"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Stairs(name, texture);
		}
		else if (nodeName.equals("chest"))
		{
			SubTexture small = parseTexture(element, "small", null);
			SubTexture large = parseTexture(element, "large", null);
			SubTexture ender = parseTexture(element, "ender", null);
			SubTexture trappedsmall = null;
			SubTexture trappedlarge = null;
			SubTexture xmassmall = null;
			SubTexture xmaslarge = null;
			
			if(texturePack.getVersion().getNumVersion() >= VERSION_5.getNumVersion())
			{
				trappedsmall = parseTexture(element, "trappedsmall", null);
				trappedlarge = parseTexture(element, "trappedlarge", null);
				xmassmall = parseTexture(element, "xmassmall", null);
				xmaslarge = parseTexture(element, "xmaslarge", null);
			}
			
			blockType = new Chest(name, small, large, ender, trappedsmall, trappedlarge, xmassmall, xmaslarge);
		}
		else if (nodeName.equals("chestnew"))
		{
			SubTexture single = parseTexture(element, "single", null);
			SubTexture left = parseTexture(element, "left", single);
			SubTexture right = parseTexture(element, "right", single);
			SubTexture christmasSmall = parseTexture(element, "christmasSmall", null);
			SubTexture christmasLeft = parseTexture(element, "christmasLeft", christmasSmall);
			SubTexture christmasRight = parseTexture(element, "christmasRight", christmasSmall);

			blockType = new ChestNew(name, stringId, single, left, right, christmasSmall, christmasLeft, christmasRight);
		}
		else if (nodeName.equals("redstonewire"))
		{
			SubTexture offJunction = parseTexture(element, "offJunction", null);
			SubTexture onJunction = parseTexture(element, "onJunction", null);
			SubTexture offLine = parseTexture(element, "offLine", null);
			SubTexture onLine = parseTexture(element, "onLine", null);
			
			blockType = new RedstoneWire(offJunction, onJunction, offLine, onLine);
		}
		else if (nodeName.equals("workbench"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side1 = parseTexture(element, "side1", null);
			SubTexture side2 = parseTexture(element, "side2", null);
			
			blockType = new Workbench(name, top, side1, side2);
		}
		else if (nodeName.equals("crops"))
		{
			SubTexture tex0 = parseTexture(element, "tex0", null);
			SubTexture tex1 = parseTexture(element, "tex1", null);
			SubTexture tex2 = parseTexture(element, "tex2", null);
			SubTexture tex3 = parseTexture(element, "tex3", null);
			SubTexture tex4 = parseTexture(element, "tex4", null);
			SubTexture tex5 = parseTexture(element, "tex5", null);
			SubTexture tex6 = parseTexture(element, "tex6", null);
			SubTexture tex7 = parseTexture(element, "tex7", null);
			
			blockType = new Crops(name, tex0, tex1, tex2, tex3, tex4, tex5, tex6, tex7);
		}
		else if (nodeName.equals("soil"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			
			blockType = new Soil(name, top, side);
		}
		else if (nodeName.equals("furnace"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture front = parseTexture(element, "front", null);
			
			blockType = new Furnace(name, top, side, front);
		}
		else if (nodeName.equals("sign"))
		{
			Version texturePackVersion = texturePack.getVersion();

			SubTexture texture;
			if (texturePackVersion.getNumVersion() < VERSION_14.getNumVersion()) {
				texture = parseTexture(element, "texture", null);
			} else {
				SubTexture defaultTex = texturePack.findTexture("assets/minecraft/textures/entity/signs/oak.png");
				texture = parseTexture(element, "texture", defaultTex);
			}
			
			final boolean obey = signFilter.getType() == SignFilterType.OBEY;
			if (obey) {
				texture = parseTexture(element, "obey", null);
			}
			
			String isWallStr = element.getAttribute("isWall");
			final boolean isWall = (isWallStr.equalsIgnoreCase("true"));
                        			
			blockType = new Sign(name, texture, isWall, obey);
		}
                else if (nodeName.equals("hangingsign"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			final boolean obey = signFilter.getType() == SignFilterType.OBEY;
			if (obey) {
				texture = parseTexture(element, "obey", null);
			}
			
			String isWallStr = element.getAttribute("isWall");
			final boolean isWall = (isWallStr.equalsIgnoreCase("true"));
                        			
			blockType = new HangingSign(name, texture, isWall, obey);
		}
		else if (nodeName.equals("door"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture bottom = parseTexture(element, "bottom", null);
			
			blockType = new Door(name, top, bottom);
		}
		else if (nodeName.equals("ladder"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Ladder(name, texture);
			
		}
		else if (nodeName.equals("pressureplate"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new PressurePlate(name, texture);
		}
		else if (nodeName.equals("button"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Button(name, texture);
		}
		else if (nodeName.equals("snow"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Snow(name, texture);
		}
		else if (nodeName.equals("ice"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Ice(name, texture);
		}
		else if (nodeName.equals("cactus"))
		{
			SubTexture side = parseTexture(element, "side", null);
			SubTexture top = parseTexture(element, "top", null);
			
			blockType = new Cactus(name, side, top);
		}
		else if (nodeName.equals("fence"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Fence(name, id.id, texture);
		}
		else if (nodeName.equals("fencegate"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new FenceGate(name, texture);
		}
		else if (nodeName.equals("pumpkin"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture front = parseTexture(element, "front", null);
			
			blockType = new JackOLantern(name, top, side, front);
		}
		else if (nodeName.equals("cake"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture interior = parseTexture(element, "interior", null);
			
			blockType = new Cake(name, top, side, interior);
		}
		else if (nodeName.equals("redstonerepeater"))
		{
			SubTexture base = parseTexture(element, "base", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture torch = parseTexture(element, "torch", null);
			SubTexture torchlit = parseTexture(element, "torchlit", null);
			SubTexture baselit = parseTexture(element, "baselit", null);
			
			blockType = new RedstoneRepeater(name, base, side, torch, torchlit, baselit);
		}
		else if (nodeName.equals("glasspane"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new GlassPane(name, texture);
		}
		else if (nodeName.equals("pistonbase"))
		{
			SubTexture side = parseTexture(element, "side", null);
			SubTexture top = parseTexture(element, "top", null);
			SubTexture bottom = parseTexture(element, "bottom", null);
			SubTexture pistonFace = parseTexture(element, "pistonFace", null);
			
			blockType = new PistonBase(name, side, top, bottom, pistonFace);
		}
		else if (nodeName.equals("pistonextension"))
		{
			SubTexture normalFace = parseTexture(element, "normalFace", null);
			SubTexture stickyFace = parseTexture(element, "stickyFace", null);
			SubTexture edge = parseTexture(element, "edge", null);
			
			blockType = new PistonExtension(name, edge, normalFace, stickyFace);
		}
		else if (nodeName.equals("vines"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Vines(name, texture, biomeCache, texturePack);
		}
		else if (nodeName.equals("fruitstem"))
		{
			SubTexture growingStem = parseTexture(element, "growingStem", null);
			SubTexture bentStem = parseTexture(element, "bentStem", null);
			
			String fruitIdStr = element.getAttribute("fruitId");
			final int fruitId = Integer.parseInt(fruitIdStr);
			
			blockType = new FruitStem(name, fruitId, growingStem, bentStem, biomeCache, texturePack);
		}
		else if (nodeName.equals("hugemushroom"))
		{
			SubTexture cap = parseTexture(element, "cap", null);
			SubTexture pores = parseTexture(element, "pores", null);
			SubTexture stem = parseTexture(element, "stem", null);
			
			blockType = new HugeMushroom(name, cap, pores, stem);
		}
		else if (nodeName.equals("fire"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Fire(name, texture, parseFrame(element));
		}
		else if (nodeName.equals("portal"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Portal(name, texture, parseFrame(element));
		}
		else if (nodeName.equals("lilly"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Lilly(name, texture, biomeCache, texturePack);
		}
		else if (nodeName.equals("dragonegg"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new DragonEgg(name, texture);
		}
		else if (nodeName.equals("netherwart"))
		{
			SubTexture tex0 = parseTexture(element, "texture0", null);
			SubTexture tex1 = parseTexture(element, "texture1", null);
			SubTexture tex2 = parseTexture(element, "texture2", null);
			
			blockType = new NetherWart(name, tex0, tex1, tex2);
		}
		else if (nodeName.equals("enderportalframe"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture bottom = parseTexture(element, "bottom", null);
			SubTexture eye = parseTexture(element, "eye", null);
			
			blockType = new EnderPortalFrame(name, top, side, bottom, eye);
		}
		else if (nodeName.equals("enderportal"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new EnderPortal(name, texture);
		}
		else if (nodeName.equals("cauldron"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture bottom = parseTexture(element, "bottom", null);
			SubTexture water = parseTexture(element, "water", null);
			
			blockType = new Cauldron(name, top, side, bottom, water);
		}
		else if (nodeName.equals("enchantmenttable"))
		{
			SubTexture top = parseTexture(element, "top", null);
			SubTexture side = parseTexture(element, "side", null);
			SubTexture bottom = parseTexture(element, "bottom", null);
			
			blockType = new EnchantmentTable(name, top, side, bottom);
		}
		else if (nodeName.equals("brewingstand"))
		{
			SubTexture base = parseTexture(element, "base", null);
			SubTexture stand = parseTexture(element, "stand", null);
	
			blockType = new BrewingStand(name, base, stand);
		}
		else if (nodeName.equals("trapdoor"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Trapdoor(name, texture);
		}
		else if (nodeName.equals("wall"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Wall(name, id.id, texture);
		}
		else if (nodeName.equals("flowerpot"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			SubTexture dirt = parseTexture(element, "dirt", null);
			SubTexture plant = parseTexture(element, "plant", null);
			
			blockType = new FlowerPot(name, texture, dirt, plant);
		}
		else if (nodeName.equals("cocoapod"))
		{
			SubTexture smallTexture = parseTexture(element, "small", null);
		 	SubTexture mediumTexture = parseTexture(element, "medium", null);
		 	SubTexture largeTexture = parseTexture(element, "large", null);
		 	
		 	blockType = new CocoaPod(name, smallTexture, mediumTexture, largeTexture);
		}
		else if (nodeName.equals("beacon"))
		{
		 	SubTexture beam = parseTexture(element, "beam", null);
		 	if (StringUtils.isNotEmpty(stringId)) {
				blockType = new Beacon(stringId, name, beam);
			} else {
				SubTexture glass = parseTexture(element, "glass", null);
				SubTexture beacon = parseTexture(element, "beacon", null);
				SubTexture obsidian = parseTexture(element, "obsidian", null);
				blockType = new Beacon(name, glass, beacon, obsidian, beam);
			}
		}
		else if (nodeName.equals("anvil"))
		{
			SubTexture base = parseTexture(element, "base", null);
		 	SubTexture top0 = parseTexture(element, "top0", null);
		 	SubTexture top1 = parseTexture(element, "top1", null);
		 	SubTexture top2 = parseTexture(element, "top2", null);
		 	
		 	blockType = new Anvil(name, base, top0, top1, top2);
		}
		else if (nodeName.equals("daylightsensor"))
		{
			SubTexture top = parseTexture(element, "top", null);
		 	SubTexture bottom = parseTexture(element, "bottom", null);
		 	
		 	blockType = new DaylightSensor(name, top, bottom);
		}
		else if (nodeName.equals("lever"))
		{
			SubTexture lever = parseTexture(element, "lever", null);
		 	SubTexture base = parseTexture(element, "base", null);
		 	
		 	blockType = new Lever(name, lever, base);
		}
		else if (nodeName.equals("hopper"))
		{
			SubTexture top = parseTexture(element, "top", null);
		 	SubTexture side = parseTexture(element, "side", null);
		 	SubTexture inside = parseTexture(element, "inside", null);
		 	
		 	blockType = new Hopper(name, top, side, inside);
		}
		else if (nodeName.equals("tripwirehook"))
		{
			SubTexture base = parseTexture(element, "texture", null);
		 	SubTexture hook = parseTexture(element, "texture2", null);
		 	SubTexture tripwire = parseTexture(element, "tripwire", null);
		 	
		 	blockType = new TripwireHook(name, base, hook, tripwire);
		}
		else if (nodeName.equals("carpet"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
		 	
		 	blockType = new Carpet(name, texture);
		}
		else if (nodeName.equals("painting"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
		 	
		 	blockType = new Painting(name, texture);
		}
		else if (nodeName.equals("paintingnew")) {
			blockType = new PaintingNew(name);
		}
		else if (nodeName.equals("skull"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			if (texture == null && element.hasAttribute("newTexture")) {
				texture = parseTexture(element, "newTexture", null);
			}

			SubTexture ctexture = parseTexture(element, "ctexture", texture);
			SubTexture stexture = parseTexture(element, "stexture", texture);
			SubTexture wtexture = parseTexture(element, "wtexture", texture);
			SubTexture ztexture = parseTexture(element, "ztexture", texture);
			SubTexture dtexture = parseTexture(element, "dtexture", texture);
		 	
		 	blockType = new Skull(name, texture, ctexture, stexture, wtexture, ztexture, dtexture);
		}
		else if (nodeName.equals("tripwire"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			blockType = new Tripwire(name, texture);
		}
		else if (nodeName.equals("banner"))
		{
			SubTexture texture = parseTexture(element, "texture", null);
			
			String hasPostStr = element.getAttribute("hasPost");
			final boolean hasPost = (hasPostStr.equalsIgnoreCase("true"));

			if (StringUtils.isNotEmpty(stringId)) {
				blockType = new Banner(stringId, name, texture, hasPost, patternImages);
			} else {
				blockType = new Banner(name, texture, hasPost, patternImages);
			}
		}
		else if (nodeName.equals("itemframe") || nodeName.equals("itemframenew"))
		{
			SubTexture background = parseTexture(element, "background", null);
			SubTexture glowBackground = parseTexture(element, "glowBackground", background);
			SubTexture border = parseTexture(element, "border", null);
			SubTexture map = parseTexture(element, "map", null);

			if (nodeName.equals("itemframe")) {
				blockType = new ItemFrame(name, background, border, map);
			} else {
				blockType = new ItemFrameNew(name, background, glowBackground, border, map);
			}
		}
		else if (nodeName.equals("endrod"))
		{
			SubTexture tex = parseTexture(element, "texture", null);
		 	
		 	blockType = new EndRod(name, tex);
		}
		else if (nodeName.equals("chorusflower"))
		{
			SubTexture alive = parseTexture(element, "alive", null);
			SubTexture dead = parseTexture(element, "dead", null);
		 	
		 	blockType = new ChorusFlower(name, alive, dead);
		}
		else if (nodeName.equals("chorusplant"))
		{
			SubTexture tex = parseTexture(element, "texture", null);
		 	
		 	blockType = new ChorusPlant(name, tex);
		}
		else if (nodeName.equals("shulkerbox"))
		{
			blockType = new ShulkerBox(name, stringId);
		}
		else if (nodeName.equals("glazedterracotta"))
		{
			blockType = new GlazedTerracotta(name, stringId);
		}
		else if (nodeName.equals("observer"))
		{
			blockType = new Observer(name, stringId);
		}
		else if (nodeName.equals("conduit")) {
			SubTexture texture = parseTexture(element, "texture", null);
			blockType = new Conduit(name, stringId, texture);
		}
		else if (nodeName.equals("bell")) {
			SubTexture texture = parseTexture(element, "texture", null);
			blockType = new Bell(name, stringId, texture);
		}
                else if (nodeName.equals("decoratedpot")) {
                        blockType = new DecoratedPot(name, texturePack);
                }
                else if (nodeName.equals("chiseledbookshelf")) {
                        blockType = new ChiseledBookshelf(name, texturePack);
                }
                else if (nodeName.equals("armorstand")) {
                        blockType = new ArmorStand(name, texturePack);
                }
		else
		{
			log.warn("Unrecognised block type: {}", nodeName);
		}
		
		if (blockType != null)
		{
			if (id != null) {
				if (id.data == -1)
					registry.register(id.id, blockType);
				else
					registry.register(id.id, id.data, blockType);
			}

			if(!stringId.equals(""))
				registry.register(stringId, blockType);
		}
	}
	
	private SubTexture parseTexture(Element element, String attribName, SubTexture defaultTex)
	{
		if (!element.hasAttribute(attribName))
			return defaultTex;

		String texName = element.getAttribute(attribName);

		SubTexture result = texturePack.findTextureOrDefault(texName, defaultTex);
		
		if (result == null)
			return defaultTex;
		
		return result;
	}

	private boolean hasTexture(Element element, String attribName) {
		if (!element.hasAttribute(attribName))
			return false;

		return texturePack.fileExists(element.getAttribute(attribName));
	}
	
	private Color parseColor(Element element, String attribName, Color defaultColor)
	{
		if (!element.hasAttribute(attribName))
			return defaultColor;
		
		String colorName = element.getAttribute(attribName);
		Color result = parseHtmlColor(colorName);
		
		if (result == null)
			return defaultColor;
		
		return result;
	}
	
	private Color parseHtmlColor(String colorStr)
	{
		Color result = null;
		if (colorStr != null && colorStr != "" && colorStr.charAt(0) == '#')
		{
			try {
				result = new Color(Integer.parseInt(colorStr.substring(1,7), 16));
			} catch (Exception e) {}
		}
		return result;
	}
	
	private int parseFrame(Element element)
	{
		String frameStr = element.getAttribute("frame");
		return StringUtils.isNotBlank(frameStr) ? Integer.parseInt(frameStr) : 0;
	}
	
	private static Element loadXml(String resource, String rootName)
	{
		try
		{
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
			InputStream in = openStream(resource);
			
			Document doc = docBuilder.parse(in);
			NodeList nodeList = doc.getElementsByTagName(rootName);
			Element root = (Element)nodeList.item(0);
			
			in.close();
			
			return root;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	private static IdDataPair parseIdDataPair(String str)
	{
		final int colonPos = str.indexOf(':');
		if (colonPos == -1)
		{
			final int id = Integer.parseInt(str);
			return new IdDataPair(id, -1);
		}
		else
		{
			String idStr = str.substring(0, colonPos);
			String dataStr = str.substring(colonPos+1);
			
			final int id = Integer.parseInt(idStr);
			final int data = Integer.parseInt(dataStr);
			
			return new IdDataPair(id, data);
		}
	}
	
}
