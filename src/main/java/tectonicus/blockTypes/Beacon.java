/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import java.awt.Color;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joml.Vector4f;

import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.rasteriser.MeshUtil;
import tectonicus.rasteriser.SubMesh;
import tectonicus.rasteriser.SubMesh.Rotation;
import tectonicus.raw.RawChunk;
import tectonicus.raw.BeaconEntity;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;
import tectonicus.util.Colour4f;
import tectonicus.world.Colors;

import static tectonicus.Version.VERSION_4;

public class Beacon implements BlockType
{
	private final String id;
	private final String name;

	private final SubTexture glass, obsidian, beacon, beam;

	public Beacon(String id, String name, SubTexture beam)
	{
		this.id = id;
		this.name = name;
		this.beam = beam;
		this.glass = null;
		this.obsidian = null;
		this.beacon = null;
	}

	public Beacon(String name, SubTexture glass, SubTexture beacon, SubTexture obsidian, SubTexture beam)
	{
		this.id = StringUtils.EMPTY;
		this.name = name;
		this.glass = glass;
		this.obsidian = obsidian;
		this.beam = beam;

		final float texel;
		if (glass.texturePackVersion == VERSION_4)
			texel = 1.0f / 16.0f / 16.0f;
		else
			texel = 1.0f / 16.0f;

		this.beacon = new SubTexture(beacon.texture, beacon.u0+texel, beacon.v0+texel, beacon.u1-texel, beacon.v1-texel);
	}
	
	@Override
	public String getName()
	{
		return name;
	}
	
	@Override
	public boolean isSolid()
	{
		return false;
	}
	
	@Override
	public boolean isWater()
	{
		return false;
	}
	
	@Override
	public void addInteriorGeometry(int x, int y, int z, BlockContext world, BlockTypeRegistry registry, RawChunk rawChunk, Geometry geometry)
	{
		addEdgeGeometry(x, y, z, world, registry, rawChunk, geometry);
	}
	
	@Override
	public void addEdgeGeometry(int x, int y, int z, BlockContext world, BlockTypeRegistry registry, RawChunk rawChunk, Geometry geometry)
	{
		final float offSet = 1.0f / 16.0f;

		SubMesh beamMesh = new SubMesh();

		if (StringUtils.isNotEmpty(id)) {
			BlockStateWrapper beaconBlock = world.getModelRegistry().getBlock(id);
			List<BlockStateModel> models = beaconBlock.getModels(rawChunk.getBlockState(x, y, z));
			for (BlockStateModel bsc : models) {  //There is only a single beacon model in vanilla Minecraft
				bsc.getBlockModel().createGeometry(x, y, z, world, rawChunk, geometry, bsc.getXRotation(), bsc.getYRotation());
			}
		} else {
			Vector4f colour = new Vector4f(1, 1, 1, 1);

			SubMesh glassMesh = new SubMesh();
			SubMesh beaconMesh = new SubMesh();
			SubMesh obsidianMesh = new SubMesh();
			SubMesh.addBlock(glassMesh, 0, 0, 0, offSet * 16, offSet * 16, offSet * 16, colour, glass, glass, glass);
			SubMesh.addBlock(beaconMesh, offSet * 3, offSet * 3, offSet * 3, offSet * 10, offSet * 10, offSet * 10, colour, beacon, beacon, beacon);
			SubMesh.addBlock(obsidianMesh, offSet * 2, offSet * 0.5f, offSet * 2, offSet * 12, offSet * 3, offSet * 12, colour, obsidian, obsidian, obsidian);

			glassMesh.pushTo(geometry.getMesh(glass.texture, Geometry.MeshType.AlphaTest), x, y, z, Rotation.None, 0);
			beaconMesh.pushTo(geometry.getMesh(beacon.texture, Geometry.MeshType.Solid), x, y, z, Rotation.None, 0);
			obsidianMesh.pushTo(geometry.getMesh(obsidian.texture, Geometry.MeshType.Solid), x, y, z, Rotation.None, 0);
		}

		String xyz = "x" + x + "y" + y + "z" + z;
		BeaconEntity entity = rawChunk.getBeacons().get(xyz);
		if (entity.getLevels() > 0)
		{
			final int localY = entity.getLocalY();
			
			Colour4f color = new Colour4f(1, 1, 1, 1);
			for (int i=1; i<RawChunk.HEIGHT-localY; i++)
			{
				final int blockID = world.getBlockId(rawChunk.getChunkCoord(), x, localY+i, z);
				final String blockName = rawChunk.getBlockName(x, localY+i, z);
				
				if (blockID == 95 || (blockName != null && blockName.contains("stained_glass")))
				{
					Color c;
					if (blockName != null) {
						c = Colors.byName(blockName.replace("minecraft:", "").replace("_stained_glass", "")).getColor();
					} else {
						final int colorID = rawChunk.getBlockData(x, localY + i, z);
						c = Colors.byId(colorID).getColor();
					}
					Colour4f newColor = new Colour4f(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, 1);
					
					color.average(newColor);
				}
				
				SubMesh.addBlockSimple(beamMesh, offSet*5, offSet*(16*i), offSet*5, offSet*5, 1, offSet*5, 
												new Vector4f(color.r, color.g, color.b, 1), beam, null, null);  //Beacon beam
				SubMesh.addBlockSimple(beamMesh, offSet*3, offSet*(16*i), offSet*3, offSet*10, 1, offSet*10, 
												new Vector4f(color.r, color.g, color.b, 0.4f), beam, null, null);
			}
		}

		beamMesh.pushTo(geometry.getMesh(beam.texture, Geometry.MeshType.Transparent), x, y, z, Rotation.Clockwise, 35);
	}
	
}
