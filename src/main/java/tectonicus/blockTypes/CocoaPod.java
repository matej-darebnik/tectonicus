/*
 * Copyright (c) 2024 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.rasteriser.Mesh;
import tectonicus.rasteriser.MeshUtil;
import tectonicus.rasteriser.SubMesh;
import tectonicus.rasteriser.SubMesh.Rotation;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;

import static tectonicus.Version.VERSION_4;

@Slf4j
public class CocoaPod implements BlockType
{
	private final String name;
	
	private final SubTexture smallTexture, mediumTexture, largeTexture;

	public CocoaPod(String name, SubTexture smallTexture, SubTexture mediumTexture, SubTexture largeTexture)
	{
		this.name = name;
		this.smallTexture = smallTexture;
		this.mediumTexture = mediumTexture;
		this.largeTexture = largeTexture;
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
		SubMesh podMesh = new SubMesh();
		
		SubTexture side = null;
		SubTexture topBottom = null;
		SubTexture stem = null;
		
		int width = 0;
		int height = 0;
		int xpos = 0;
		int ypos = 0;
		int zpos = 0;
		
		Vector4f colour = new Vector4f(1, 1, 1, 1);
		
		final int data = rawChunk.getBlockData(x, y, z);
		final int size = data & 0xC;
		final int direction = data & 0x3;

		final float offSet = 1.0f / 16.0f;
		
		final float texel;
		if (smallTexture.texturePackVersion == VERSION_4)
			texel = 1.0f / 16.0f / 16.0f;
		else
			texel = 1.0f / 16.0f;
		
		if(size == 0)
		{
			topBottom = new SubTexture(smallTexture.texture, smallTexture.u0, smallTexture.v0, smallTexture.u0+texel*4, smallTexture.v0+texel*4);
			side = new SubTexture(smallTexture.texture, smallTexture.u0+texel*11, smallTexture.v0+texel*5, smallTexture.u0+texel*15, smallTexture.v0+texel*9);
			stem = new SubTexture(smallTexture.texture, smallTexture.u0+texel*12, smallTexture.v0, smallTexture.u1, smallTexture.v0+texel*4);
			
			width = 4;
			height = 5;
			xpos = 6;
			ypos = 7;
			zpos = 6;
		}
		else if(size == 4)
		{
			topBottom = new SubTexture(mediumTexture.texture, mediumTexture.u0, mediumTexture.v0, mediumTexture.u0+texel*6, mediumTexture.v0+texel*6);
			side = new SubTexture(mediumTexture.texture, mediumTexture.u0+texel*9, mediumTexture.v0+texel*5, mediumTexture.u0+texel*15, mediumTexture.v0+texel*11);
			stem = new SubTexture(mediumTexture.texture, mediumTexture.u0+texel*12, mediumTexture.v0, mediumTexture.u1, mediumTexture.v0+texel*4);
			
			width = 6;
			height = 7;
			xpos = 5;
			ypos = 5;
			zpos = 5;
		}
		else if(size == 8)
		{
			topBottom = new SubTexture(largeTexture.texture, largeTexture.u0, largeTexture.v0, largeTexture.u0+texel*7, largeTexture.v0+texel*7);
			side = new SubTexture(largeTexture.texture, largeTexture.u0+texel*7, largeTexture.v0+texel*5, largeTexture.u0+texel*15, largeTexture.v0+texel*13);
			stem = new SubTexture(largeTexture.texture, largeTexture.u0+texel*12, largeTexture.v0, largeTexture.u1, largeTexture.v0+texel*4);
			
			width = 8;
			height = 9;
			xpos = 4;
			ypos = 3;
			zpos = 4;
		}
		else
			log.warn("Wrong cocoa pod size!");
		//final float lightness = world.getLight(rawChunk.getChunkCoord(), x, y+1, z, LightFace.Top);
	
		Mesh stemMesh = geometry.getMesh(stem.texture, Geometry.MeshType.AlphaTest);
	
		if (direction == 0)
		{
			if(size == 0)
			{
				zpos = 11;
			}
			else if(size == 4)
			{
				zpos = 9;
			}
			else if(size == 8)
			{
				zpos = 7;
			}
			// north			
			SubMesh.addBlockSimple(podMesh, offSet*xpos, offSet*ypos, offSet*zpos, offSet*width, offSet*height, offSet*width, colour, side, topBottom, topBottom);
			
			MeshUtil.addDoubleSidedQuad(stemMesh,	new Vector3f(x+offSet*8,	y+1,	z+offSet*12),
													new Vector3f(x+offSet*8,	y+1,	z+1),
													new Vector3f(x+offSet*8,	y+offSet*12,		z+1),
													new Vector3f(x+offSet*8,	y+offSet*12,		z+offSet*12),
													colour, stem);
		}
		else if (direction == 1)
		{
			// east
			SubMesh.addBlockSimple(podMesh, offSet, offSet*ypos, offSet*zpos, offSet*width, offSet*height, offSet*width, colour, side, topBottom, topBottom);
			
			MeshUtil.addDoubleSidedQuad(stemMesh,	new Vector3f(x+offSet*4,		y+1,	z+offSet*8),
													new Vector3f(x,			y+1,	z+offSet*8),
													new Vector3f(x,			y+offSet*12,		z+offSet*8),
													new Vector3f(x+offSet*4,		y+offSet*12,		z+offSet*8),
													colour, stem);
		}
		else if (direction == 2)
		{
			// south
			SubMesh.addBlockSimple(podMesh, offSet*xpos, offSet*ypos, offSet, offSet*width, offSet*height, offSet*width, colour, side, topBottom, topBottom);
			
			MeshUtil.addDoubleSidedQuad(stemMesh,	new Vector3f(x+offSet*8,	y+1,	z+offSet*4),
													new Vector3f(x+offSet*8,	y+1,	z),
													new Vector3f(x+offSet*8,	y+offSet*12,		z),
													new Vector3f(x+offSet*8,	y+offSet*12,		z+offSet*4),
													colour,	stem);
		}
		else if (direction == 3)
		{
			if(size == 0)
			{
				xpos = 11;
			}
			else if(size == 4)
			{
				xpos = 9;
			}
			else if(size == 8)
			{
				xpos = 7;
			}
			// west
			SubMesh.addBlockSimple(podMesh, offSet*xpos, offSet*ypos, offSet*zpos, offSet*width, offSet*height, offSet*width, colour, side, topBottom, topBottom);
			
			MeshUtil.addDoubleSidedQuad(stemMesh,	new Vector3f(x+offSet*12,	y+1,	z+offSet*8),
													new Vector3f(x+1,		y+1,	z+offSet*8),
													new Vector3f(x+1,		y+offSet*12,		z+offSet*8),
													new Vector3f(x+offSet*12,	y+offSet*12,		z+offSet*8),
													colour, stem);
		}
		
		podMesh.pushTo(geometry.getMesh(side.texture, Geometry.MeshType.Solid), x, y, z, Rotation.None, 0);
	}

}
