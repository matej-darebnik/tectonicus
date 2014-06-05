/*
 * Copyright (c) 2012-2014, John Campbell and other contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.Chunk;
import tectonicus.configuration.LightFace;
import tectonicus.rasteriser.SubMesh;
import tectonicus.rasteriser.SubMesh.Rotation;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;

public class Door implements BlockType
{
	private final String name;
	private final SubTexture topTexture;
	private final SubTexture bottomTexture;
	private final SubTexture edgeTexture;
	private final SubTexture topEdgeTexture;
	
	public Door(String name, SubTexture topTexture, SubTexture bottomTexture)
	{
		this.name = name;
		this.topTexture = topTexture;
		this.bottomTexture = bottomTexture;
		
		final float uWidth; // fudge factor
		if (topTexture.texturePackVersion =="1.4")
			uWidth = 1.0f / 16.0f / 16.0f * 2.5f;
		else
			uWidth = 1.0f / 16.0f * 2.5f;
		
		this.edgeTexture = new SubTexture(topTexture.texture, topTexture.u0, topTexture.v0, topTexture.u0+uWidth, topTexture.v1);
		this.topEdgeTexture = new SubTexture(topTexture.texture, topTexture.u0, topTexture.v0, topTexture.u1, topTexture.v0+uWidth);
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
		final float thickness = 1.0f / 16.0f * 3.0f;
		
		final int data = rawChunk.getBlockData(x, y, z);
		final boolean isTop = (data & 0x8) > 0;
		final boolean isOpen = (data & 0x4) > 0;
		final int hingePos = data & 0x3;
		
		SubMesh subMesh = new SubMesh();
		
		SubTexture frontTexture = isTop ? topTexture : bottomTexture;
		
		final float lightness = Chunk.getLight(world.getLightStyle(), LightFace.Top, rawChunk, x, y, z);
		
		Vector4f white = new Vector4f(lightness, lightness, lightness, 1);
		
		// Front face
		subMesh.addQuad(new Vector3f(0, 1, thickness), new Vector3f(1, 1, thickness), new Vector3f(1, 0, thickness), new Vector3f(0, 0, thickness),
						white, frontTexture);
		
		// Back face
		subMesh.addQuad(new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), white,
						new Vector2f(frontTexture.u1, frontTexture.v0), new Vector2f(frontTexture.u0, frontTexture.v0),new Vector2f(frontTexture.u0, frontTexture.v1), new Vector2f(frontTexture.u1, frontTexture.v1) );
		
		// Top edge
		subMesh.addQuad(new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, thickness), new Vector3f(0, 1, thickness),
						white, topEdgeTexture);
		
		// Hinge edge
		subMesh.addQuad(new Vector3f(0, 1, 0), new Vector3f(0, 1, thickness), new Vector3f(0, 0, thickness), new Vector3f(0, 0, 0),
						white, edgeTexture);
		
		// Non-hinge edge
		subMesh.addQuad(new Vector3f(1, 1, thickness), new Vector3f(1, 1, 0), new Vector3f(1, 0, 0), new Vector3f(1, 0, thickness),
				white, edgeTexture);
		
		// Now rotate depending on hinge position and open flag
		
		Rotation rotation = Rotation.None;
		float angle = 0;
		
		final float texel = 1.0f / 16.0f;
		
		float xOffset = x;
		float yOffset = y;
		float zOffset = z;
		
		if (isOpen)
		{
			if (hingePos == 0)
			{
				// Hinge in north-east corner
				// ...already correct
			}
			else if (hingePos == 1)
			{
				// Hinge in south-east corner
				rotation = Rotation.AntiClockwise;
				angle = 90;
			}
			else if (hingePos == 2)
			{
				// Hinge in south-west corner
				rotation = Rotation.Clockwise;
				angle = 180;
			}
			else if (hingePos == 3)
			{
				// Hinge in north-west corner
				rotation = Rotation.Clockwise;
				angle = 90;
			}
		}
		else
		{
			if (hingePos == 0)
			{
				// Hinge in north-east corner
				rotation = Rotation.AntiClockwise;
				angle = 90;
				xOffset -= texel * 13;
			}
			else if (hingePos == 1)
			{
				// Hinge in south-east corner
				rotation = Rotation.Clockwise;
				angle = 180;
				zOffset -= texel * 13;
			}
			else if (hingePos == 2)
			{
				// Hinge in south-west corner
				rotation = Rotation.Clockwise;
				angle = 90;
				xOffset += texel * 13;
			}
			else if (hingePos == 3)
			{
				// Hinge in north-west corner
				zOffset += texel * 13;
			}
		}
		
		if(isTop)
			subMesh.pushTo(geometry.getMesh(topTexture.texture, Geometry.MeshType.AlphaTest), xOffset, yOffset, zOffset, rotation, angle);
		else
			subMesh.pushTo(geometry.getMesh(bottomTexture.texture, Geometry.MeshType.AlphaTest), xOffset, yOffset, zOffset, rotation, angle);
	}
	
}
