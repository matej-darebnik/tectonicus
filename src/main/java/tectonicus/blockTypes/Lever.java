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

public class Lever implements BlockType
{	
	private final String name;

	private final SubTexture base, leverSideTexture, leverTopTexture;
	
	public Lever(String name, SubTexture lever, SubTexture base)
	{
		this.name = name;
		this.base = base;
		
		final float texelSize;
		if (lever.texturePackVersion == VERSION_4)
			texelSize = 1.0f / 16.0f / 16.0f;
		else
			texelSize = 1.0f / 16.0f;
		
		final float topOffset = texelSize * 6;
		this.leverSideTexture = new SubTexture(lever.texture, lever.u0, lever.v0 + topOffset, lever.u1, lever.v1);
		
		final float uOffset = texelSize * 7;
		final float vOffset0 = texelSize * 6;
		final float vOffset1 = texelSize * 8;
		this.leverTopTexture = new SubTexture(lever.texture, lever.u0 + uOffset, lever.v0 + vOffset0, lever.u1 - uOffset, lever.v1 - vOffset1);
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
		final int data = rawChunk.getBlockData(x, y, z);
		
		final float lightness = Chunk.getLight(world.getLightStyle(), LightFace.Top, rawChunk, x, y, z, world.getNightLightAdjustment());
		Vector4f white = new Vector4f(lightness, lightness, lightness, 1);
		
		final boolean isOn = (data & 0x8) > 0;
		
		final float offSet = 1.0f / 16.0f;
		
		SubMesh baseMesh = new SubMesh();
		SubMesh leverMesh = new SubMesh();
		
		SubMesh.addBlock(baseMesh, offSet*4, 0, offSet*5, offSet*8, offSet*3, offSet*6, white, base, base, base);
		addLever(leverMesh, offSet*12, offSet*4, offSet*7);
		
		Rotation horizRotation = Rotation.Clockwise;
		float horizAngle = 0;
		
		Rotation vertRotation = Rotation.None;
		float vertAngle = 0;
		
		// Set angle/rotation from block data flags
		if(data == 1 || data == 9) // Facing east
		{

			vertRotation = Rotation.Clockwise;
			vertAngle = 90;
			
			horizRotation = Rotation.Clockwise;
			horizAngle = 180;
			if(isOn)
			{
				horizAngle = 0;
				vertAngle = 270;
			}
			
		}
		else if (data == 2 || data == 10) // Facing west
		{
			vertRotation = Rotation.Clockwise;
			vertAngle = 90;
			
			if(isOn)
			{
				vertRotation = Rotation.Clockwise;
				vertAngle = 270;
				horizRotation = Rotation.Clockwise;
				horizAngle = 180;
			}
		}
		else if (data == 3 || data == 11) // Facing south
		{
			vertRotation = Rotation.Clockwise;
			vertAngle = 90;
			
			horizRotation = Rotation.Clockwise;
			horizAngle = 90;
			if(isOn)
			{
				vertRotation = Rotation.Clockwise;
				vertAngle = 270;
				horizRotation = Rotation.Clockwise;
				horizAngle = 270;
			}
		}
		else if (data == 4 || data == 12) // Facing north
		{
			vertRotation = Rotation.Clockwise;
			vertAngle = 90;
			
			horizRotation = Rotation.AntiClockwise;
			horizAngle = 90;
			
			if(isOn)
			{
				vertRotation = Rotation.Clockwise;
				vertAngle = 270;
				
				horizRotation = Rotation.Clockwise;
				horizAngle = 90;
			}
		}
		else if (data == 5 || data == 13) // North/South ground 
		{
			horizRotation = Rotation.Clockwise;
			horizAngle = 270;
			
			if(isOn)
			{
				horizRotation = Rotation.Clockwise;
				horizAngle = 90;
			}
		}
		else if(data == 6 || data == 14) // East/West ground
		{
			vertRotation = Rotation.Clockwise;
			if(isOn)
			{
				horizRotation = Rotation.Clockwise;
				horizAngle = 180;
			}
		}
		else if (data == 7 || data == 15) // North/South ceiling
		{
			vertRotation = Rotation.Clockwise;
			vertAngle = 180;

			horizRotation = Rotation.Clockwise;
			horizAngle = 90;
			
			if(isOn)
				horizAngle = 270;
		}
		else if (data == 0 || data == 8) // East/West ceiling
		{
			vertRotation = Rotation.Clockwise;
			vertAngle = 180;
			
			horizRotation = Rotation.Clockwise;
			horizAngle = 180;
			
			if(isOn)
				horizAngle = 0;
		}
		
		
		baseMesh.pushTo(geometry.getMesh(base.texture, Geometry.MeshType.Solid), x, y, z, horizRotation, horizAngle, vertRotation, vertAngle);
		leverMesh.pushTo(geometry.getMesh(leverTopTexture.texture, Geometry.MeshType.AlphaTest), x, y, z, horizRotation, horizAngle, vertRotation, vertAngle-45);
	}
	
	private void addLever(SubMesh subMesh, float x, float y, float z)
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
								leverTopTexture);
		
		// North
		subMesh.addQuad(		new Vector3f(x+leftSide,					y+height+bottomOffsetY,	z),
								new Vector3f(x+leftSide,					y+height+bottomOffsetY,	z+1),
								new Vector3f(x+leftSide + bottomOffsetX,	y+bottomOffsetY,			z+1 + bottomOffsetZ),
								new Vector3f(x+leftSide + bottomOffsetX,	y+bottomOffsetY,			z + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								leverSideTexture);
		
		// South
		subMesh.addQuad(		new Vector3f(x+rightSide,					y+height+bottomOffsetY,		z+1),
								new Vector3f(x+rightSide,					y+height+bottomOffsetY,		z),
								new Vector3f(x+rightSide + bottomOffsetX,	y+bottomOffsetY,			z + bottomOffsetZ),
								new Vector3f(x+rightSide + bottomOffsetX,	y+bottomOffsetY,			z+1 + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								leverSideTexture);
		
		// East
		subMesh.addQuad(		new Vector3f(x+1,					y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x,						y+height+bottomOffsetY,	z+leftSide),
								new Vector3f(x + bottomOffsetX,		y+bottomOffsetY,			z+leftSide + bottomOffsetZ),
								new Vector3f(x+1 + bottomOffsetX,	y+bottomOffsetY,			z+leftSide + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								leverSideTexture);
		
		// West
		subMesh.addQuad(		new Vector3f(x,						y+height+bottomOffsetY,	z+rightSide),
								new Vector3f(x+1,					y+height+bottomOffsetY,	z+rightSide),
								new Vector3f(x+1 + bottomOffsetX,	y+bottomOffsetY,			z+rightSide + bottomOffsetZ),
								new Vector3f(x + bottomOffsetX,		y+bottomOffsetY,			z+rightSide + bottomOffsetZ),
								new Vector4f(colour.x * lightness, colour.y * lightness, colour.z * lightness, colour.w),
								leverSideTexture);
	}
	
}
