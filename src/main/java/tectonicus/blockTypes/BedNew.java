/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import org.apache.commons.lang3.StringUtils;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.chunk.Chunk;
import tectonicus.configuration.LightFace;
import tectonicus.rasteriser.SubMesh;
import tectonicus.rasteriser.SubMesh.Rotation;
import tectonicus.raw.BedEntity;
import tectonicus.raw.BlockProperties;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;
import tectonicus.world.Colors;

public class BedNew implements BlockType
{
	private final String id;
	private final String name;

	public BedNew(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public BedNew(String name)
	{
		this(StringUtils.EMPTY, name);
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
	public void addEdgeGeometry(final int x, final int y, final int z, BlockContext world, BlockTypeRegistry registry, RawChunk rawChunk, Geometry geometry)
	{
		final int data = rawChunk.getBlockData(x, y, z);
		final BlockProperties properties = rawChunk.getBlockState(x, y, z);
		boolean isHead = (data & 0x8) > 0;
		if (properties != null) {
			isHead = properties.get("part").equals("head");
		}
		
		final float texel = 1.0f / 64.0f;

		String color = Colors.RED.getName();
		if (StringUtils.isNotEmpty(id)) {
			color = id.replace("minecraft:", "").replace("_bed", "");
		} else {
			String xyz = "x" + x + "y" + y + "z" + z;
			BedEntity be = rawChunk.getBeds().get(xyz);
			if (be != null)
				color = Colors.byId(be.getColor()).getName();
		}

		SubTexture texture = world.getTexturePack().findTexture(null, "bed_"+color);
		SubTexture headTop = new SubTexture(texture.texture, texture.u0+texel*6, texture.v0+texel*6, texture.u0+texel*22, texture.v0+texel*21.8f);
		SubTexture footTop = new SubTexture(texture.texture, texture.u0+texel*6, texture.v0+texel*28.2f, texture.u0+texel*22, texture.v0+texel*43.9f);
		SubTexture headSide = new SubTexture(texture.texture, texture.u0, texture.v0+texel*6.1f, texture.u0+texel*6, texture.v0+texel*21.9f);
		SubTexture footSide = new SubTexture(texture.texture, texture.u0, texture.v0+texel*28.1f, texture.u0+texel*6, texture.v0+texel*43.9f);
		SubTexture headEdge = new SubTexture(texture.texture, texture.u0+texel*6.1f, texture.v0, texture.u0+texel*21.9f, texture.v0+texel*6);
		SubTexture footEdge = new SubTexture(texture.texture, texture.u0+texel*22.1f, texture.v0+texel*22, texture.u0+texel*37.9f, texture.v0+texel*27.9f);
		SubTexture leg = new SubTexture(texture.texture, texture.u0+texel*50, texture.v0+texel*3.1f, texture.u0+texel*53, texture.v0+texel*6);
		
		final float lightness = Chunk.getLight(world.getLightStyle(), LightFace.Top, rawChunk, x, y, z, world.getNightLightAdjustment());
		Vector4f white = new Vector4f(lightness, lightness, lightness, 1);
		
		final float height = 1.0f / 16.0f * 9.0f;
		final float legHeight = 1.0f / 16.0f * 3.0f;
		
		SubMesh bedMesh = new SubMesh();
		
		SubTexture topTex = isHead ? headTop : footTop;
		bedMesh.addQuad(new Vector3f(0, height, 0), new Vector3f(1, height, 0), new Vector3f(1, height, 1), new Vector3f(0, height, 1), white, topTex);
		
		// Head or feet sides
		if (isHead)
		{
			//Top edge
			bedMesh.addQuad(new Vector3f(0, legHeight, 0), new Vector3f(1, legHeight, 0), new Vector3f(1, height, 0), new Vector3f(0, height, 0), white, headEdge);
			
			//Head sides
			bedMesh.addQuad(new Vector3f(1, height, 0), new Vector3f(1, legHeight, 0), new Vector3f(1, legHeight, 1), new Vector3f(1, height, 1), white, 
					new Vector2f(headSide.u1, headSide.v0), new Vector2f(headSide.u0, headSide.v0),new Vector2f(headSide.u0, headSide.v1), new Vector2f(headSide.u1, headSide.v1));
			bedMesh.addQuad(new Vector3f(0, legHeight, 0), new Vector3f(0, height, 0), new Vector3f(0, height, 1), new Vector3f(0, legHeight, 1), white, headSide);
			
			//Legs
			SubMesh.addBlockSimple(bedMesh, 0, 0, 0, legHeight, legHeight, legHeight, white, leg, leg, leg);
			SubMesh.addBlockSimple(bedMesh, 1-legHeight, 0, 0, legHeight, legHeight, legHeight, white, leg, leg, leg);
		}
		else
		{
			//Bottom edge
			bedMesh.addQuad(new Vector3f(1, legHeight, 1), new Vector3f(0, legHeight, 1), new Vector3f(0, height, 1), new Vector3f(1, height, 1), white, footEdge);
			
			//Feet sides
			bedMesh.addQuad(new Vector3f(1, height, 0), new Vector3f(1, legHeight, 0), new Vector3f(1, legHeight, 1), new Vector3f(1, height, 1), white, 
					new Vector2f(footSide.u1, footSide.v0), new Vector2f(footSide.u0, footSide.v0),new Vector2f(footSide.u0, footSide.v1), new Vector2f(footSide.u1, footSide.v1));
			bedMesh.addQuad(new Vector3f(0, legHeight, 0), new Vector3f(0, height, 0), new Vector3f(0, height, 1), new Vector3f(0, legHeight, 1), white, footSide);
			
			//Legs
			SubMesh.addBlockSimple(bedMesh, 0, 0, 1-legHeight, legHeight, legHeight, legHeight, white, leg, leg, leg);
			SubMesh.addBlockSimple(bedMesh, 1-legHeight, 0, 1-legHeight, legHeight, legHeight, legHeight, white, leg, leg, leg);
		}

		
		SubMesh.Rotation rotation = Rotation.None;
		float angle = 0;
		
		int dir = (data & 0x3);
		if (properties != null) {
			String facing = properties.get("facing");
			switch (facing) {
				case "south":
					dir = 0;
					break;
				case "west":
					dir = 1;
					break;
				case "north":
					dir = 2;
					break;
				case "east":
					dir = 3;
					break;
				default:
			}
		}
		if (dir == 0)
		{
			// Head is pointing south
			rotation = Rotation.AntiClockwise;
			angle = 180;
		}
		else if (dir == 1)
		{
			// Head is pointing west
			rotation = Rotation.Clockwise;
			angle = 90;
		}
		// dir == 2
		// Head is pointing north
		else if (dir == 3)
		{
			// Head is pointing east
			rotation = Rotation.AntiClockwise;
			angle = 90;
		}
		
		// Apply rotation
		bedMesh.pushTo(geometry.getMesh(texture.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
	}
	
}
