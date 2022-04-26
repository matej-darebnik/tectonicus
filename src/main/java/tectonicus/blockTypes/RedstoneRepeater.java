/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import org.joml.Vector3f;
import org.joml.Vector4f;

import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.chunk.Chunk;
import tectonicus.configuration.LightFace;
import tectonicus.rasteriser.SubMesh;
import tectonicus.rasteriser.SubMesh.Rotation;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;

import static tectonicus.Version.VERSION_4;

public class RedstoneRepeater implements BlockType
{
	private static final int HEIGHT_IN_TEXELS = 4;
	
	private final String name;
	
	private final SubTexture baseTexture, sideTexture;
	private final SubTexture torchTopTexture, torchSideTexture, litTorchTopTexture, baseLit;
	
	public RedstoneRepeater(String name, SubTexture baseTexture, SubTexture sideTexture, SubTexture torchTexture, SubTexture litTorchTexture, SubTexture baseLit)
	{
		this.name = name;
		
		final float texelSize, baseTile;
		if (baseTexture.texturePackVersion == VERSION_4)
		{
			texelSize = 1.0f / 16.0f / 16.0f;
			baseTile = 0;
		}
		else
		{
			texelSize = 1.0f / 16.0f;
			baseTile = (1.0f / baseTexture.texture.getHeight()) * baseTexture.texture.getWidth();
		}
			
		this.baseTexture = new SubTexture(baseTexture.texture, baseTexture.u0, baseTexture.v0, baseTexture.u1, baseTexture.v0+baseTile);
		this.baseLit = baseLit;
		
		final float vHeight = texelSize * 14;
		this.sideTexture = new SubTexture(sideTexture.texture, sideTexture.u0, sideTexture.v0+vHeight, sideTexture.u1, sideTexture.v1);
		
		
		// Torch textures
		
		final float topOffset = texelSize * 6;
		this.torchSideTexture = new SubTexture(torchTexture.texture, torchTexture.u0, torchTexture.v0 + topOffset, torchTexture.u1, torchTexture.v1);
		
		final float uOffset = texelSize * 7;
		final float vOffset0 = texelSize * 6;
		final float vOffset1 = texelSize * 8;
		this.torchTopTexture = new SubTexture(torchTexture.texture, torchTexture.u0 + uOffset, torchTexture.v0 + vOffset0, torchTexture.u1 - uOffset, torchTexture.v1 - vOffset1);
		
		if(litTorchTexture != null)
		{
			this.litTorchTopTexture = new SubTexture(litTorchTexture.texture, torchTexture.u0 + uOffset, torchTexture.v0 + vOffset0, torchTexture.u1 - uOffset, torchTexture.v1 - vOffset1);
		}
		else
			litTorchTopTexture = null;
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
	public void addEdgeGeometry(int x, int y, int z, BlockContext world, BlockTypeRegistry registry, RawChunk chunk, Geometry geometry)
	{
		final int data = chunk.getBlockData(x, y, z);
		
		final float height = 1.0f / 16.0f * HEIGHT_IN_TEXELS;
		
		final float lightness = Chunk.getLight(world.getLightStyle(), LightFace.Top, chunk, x, y, z, world.getNightLightAdjustment());
		Vector4f white = new Vector4f(lightness, lightness, lightness, 1);
		
		SubMesh subMesh = new SubMesh();
		SubMesh baseMesh = new SubMesh();
		SubMesh torchMesh = new SubMesh();
		SubMesh litTorchMesh = new SubMesh();
		
		SubTexture base = null;
		if((data & 0x8) > 0 && baseLit != null)
		{
			final float baseLitTile = (1.0f / baseLit.texture.getHeight()) * baseLit.texture.getWidth();
			base = new SubTexture(baseLit.texture, baseLit.u0, baseLit.v0, baseLit.u1, baseLit.v0+baseLitTile);
		}
		else
		{
			base = baseTexture;
		}
		
		baseMesh.addQuad(new Vector3f(0, height, 0), new Vector3f(1, height, 0), new Vector3f(1, height, 1), new Vector3f(0, height, 1), white, base);
		
		// North edge
		subMesh.addQuad(new Vector3f(0, height, 0), new Vector3f(0, height, 1), new Vector3f(0, 0, 1), new Vector3f(0, 0, 0), white, sideTexture);
		
		// South edge
		subMesh.addQuad(new Vector3f(1, height, 1), new Vector3f(1, height, 0), new Vector3f(1, 0, 0), new Vector3f(1, 0, 1), white, sideTexture);
		
		// East edge
		subMesh.addQuad(new Vector3f(1, height, 0), new Vector3f(0, height, 0), new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), white, sideTexture);
		
		// West edge
		subMesh.addQuad(new Vector3f(0, height, 1), new Vector3f(1, height, 1), new Vector3f(1, 0, 1), new Vector3f(0, 0, 1), white, sideTexture);
		
		
		final float texel = 1.0f / 16.0f;
		
		//This is rather messy because block ID 150 "Redstone Comparator (Active)" doesn't seem to be valid, so we have to pass in all possibly needed textures, etc.
		if(name.equals("Redstone Repeater"))
		{
			// Static torch
			addTorch(torchMesh, texel*7, 0, texel*2);
			
			// Delay torch
			final int delay = (data>>2) & 0x3;
			
			final float yPixel = delay * 2 + 6; // Valid offsets are from 6 to 12
			addTorch(torchMesh, texel*7, 0, texel*yPixel);
		}
		else if(data == 4 || data == 5 || data == 6 || data == 7 && litTorchTopTexture != null)
		{
			addTorch(litTorchMesh, texel*7, -texel, texel*2);
			addTorch(torchMesh, texel*4, -texel, texel*11);
			addTorch(torchMesh, texel*10, -texel, texel*11);
		}
		else if(data == 8 || data == 9 || data == 10 || data == 11 && litTorchTopTexture != null)
		{
			addTorch(torchMesh, texel*7, texel*-4, texel*2);
			addTorch(litTorchMesh, texel*4, -texel, texel*11);
			addTorch(litTorchMesh, texel*10, -texel, texel*11);
		}
		else if(data == 12 || data == 13 || data == 14 || data == 15 && litTorchTopTexture != null)
		{
			addTorch(litTorchMesh, texel*7, -texel, texel*2);
			addTorch(litTorchMesh, texel*4, -texel, texel*11);
			addTorch(litTorchMesh, texel*10, -texel, texel*11);
		}
		else
		{
			addTorch(torchMesh, texel*7, texel*-4, texel*2);
			addTorch(torchMesh, texel*4, -texel, texel*11);
			addTorch(torchMesh, texel*10, -texel, texel*11);
		}
		
		
		
		// Now do rotation
		Rotation rotation = Rotation.None;
		float angle = 0;
		
		final int direction = data & 0x3;
		if (direction == 2)
		{
			// Facing east
			rotation = Rotation.Clockwise;
			angle = 180;
		}
		else if (direction == 3)
		{
			// Facing south
			rotation = Rotation.Clockwise;
			angle = 90;
		}
		else if (direction == 0)
		{
			// Facing west (built direction)
		}
		else if (direction == 1)
		{
			// Facing south
			rotation = Rotation.AntiClockwise;
			angle = 90;			
		}
		
		subMesh.pushTo(geometry.getMesh(sideTexture.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
		if((data & 0x8) > 0 && baseLit != null)
		{
			final float baseLitTile = (1.0f / baseLit.texture.getHeight()) * baseLit.texture.getWidth();
			SubTexture lit = new SubTexture(baseLit.texture, baseLit.u0, baseLit.v0, baseLit.u1, baseLit.v0+baseLitTile);
			baseMesh.pushTo(geometry.getMesh(lit.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
		}
		else
			baseMesh.pushTo(geometry.getMesh(baseTexture.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
		torchMesh.pushTo(geometry.getMesh(torchSideTexture.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
		if(litTorchTopTexture != null)
		{
			litTorchMesh.pushTo(geometry.getMesh(litTorchTopTexture.texture, Geometry.MeshType.AlphaTest), x, y, z, rotation, angle);
		}
	}
	
	private void addTorch(SubMesh subMesh, float x, float y, float z)
	{
		final float lightness = 1.0f;
		Vector4f colour = new Vector4f(1, 1, 1, 1);
		
		final float leftSide = 7.0f / 16.0f;
		final float rightSide = 9.0f / 16.0f;
		final float height = 10.0f / 16.0f;
		
		final float texel = 1.0f / 16.0f;
		
		// Shift so x/y/z of zero starts the torch just next to the origin
		x -= texel*7;
		z -= texel*7;
		
		// Data defines torch placement
		// 0x1: Pointing south
		// 0x2: Pointing north
		// 0x3; Pointing west
		// 0x4: Pointing east
		// 0x5: Standing on the floor
		
		final float bottomOffsetX;
		final float bottomOffsetZ;
		final float bottomOffsetY;

		{
			// Standing on the floor
			bottomOffsetX = 0.0f;
			bottomOffsetZ = 0.0f;
			bottomOffsetY = 0.0f;
		}
		
		// Top
		subMesh.addQuad(		new Vector3f(x+leftSide,	y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x+rightSide,	y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x+rightSide,	y+height+bottomOffsetY,	z+rightSide),
								new Vector3f(x+leftSide,	y+height+bottomOffsetY,	z+rightSide),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								torchTopTexture);
		
		// North
		subMesh.addQuad(		new Vector3f(x+leftSide,					y+height+bottomOffsetY,	z),
								new Vector3f(x+leftSide,					y+height+bottomOffsetY,	z+1),
								new Vector3f(x+leftSide + bottomOffsetX,	y+bottomOffsetY,			z+1 + bottomOffsetZ),
								new Vector3f(x+leftSide + bottomOffsetX,	y+bottomOffsetY,			z + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								torchSideTexture);
		
		// South
		subMesh.addQuad(		new Vector3f(x+rightSide,					y+height+bottomOffsetY,		z+1),
								new Vector3f(x+rightSide,					y+height+bottomOffsetY,		z),
								new Vector3f(x+rightSide + bottomOffsetX,	y+bottomOffsetY,			z + bottomOffsetZ),
								new Vector3f(x+rightSide + bottomOffsetX,	y+bottomOffsetY,			z+1 + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								torchSideTexture);
		
		// East
		subMesh.addQuad(		new Vector3f(x+1,					y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x,						y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x + bottomOffsetX,		y+bottomOffsetY,			z+leftSide + bottomOffsetZ),
								new Vector3f(x+1 + bottomOffsetX,	y+bottomOffsetY,			z+leftSide + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								torchSideTexture);
		
		// West
		subMesh.addQuad(		new Vector3f(x,						y+height+bottomOffsetY,	z+rightSide),
								new Vector3f(x+1,					y+height+bottomOffsetY,	z+rightSide),
								new Vector3f(x+1 + bottomOffsetX,	y+bottomOffsetY,			z+rightSide + bottomOffsetZ),
								new Vector3f(x + bottomOffsetX,		y+bottomOffsetY,			z+rightSide + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								torchSideTexture);
	}
}
