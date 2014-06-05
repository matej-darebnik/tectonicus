/*
 * Copyright (c) 2012-2014, John Campbell and other contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.rasteriser.Mesh;
import tectonicus.rasteriser.MeshUtil;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;
import tectonicus.util.Colour4f;

public class EnderPortalFrame implements BlockType
{
	private final String name;
	
	private final SubTexture top, side, bottom;
	
	private final SubTexture eyeSide, eyeTop;
	
	public EnderPortalFrame(String name, SubTexture top, SubTexture side, SubTexture bottom, SubTexture eye)
	{
		this.name = name;
		
		this.top = top;
		this.side = side;
		this.bottom = bottom;
		
		final float texel;
		if (eye.texturePackVersion == "1.4")
			texel = 1.0f / 16.0f / 16.0f;
		else
			texel = 1.0f / 16.0f;
		
		eyeSide = new SubTexture(eye.texture, eye.u0+(texel*4), eye.v0, eye.u0+(texel*12), eye.v0+(texel*4));
		eyeTop = new SubTexture(eye.texture, eye.u0+(texel*4), eye.v0+(texel*4), eye.u0+(texel*12), eye.v0+(texel*12));
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
	public void addInteriorGeometry(int x, int y, int z, BlockContext world, BlockTypeRegistry registry, RawChunk chunk, Geometry geometry)
	{
		addEdgeGeometry(x, y, z, world, registry, chunk, geometry);
	}

	@Override
	public void addEdgeGeometry(int x, int y, int z, BlockContext context, BlockTypeRegistry registry, RawChunk rawChunk, Geometry geometry)
	{		
		final int data = rawChunk.getBlockData(x, y, z);
		
		Colour4f colour = new Colour4f(1, 1, 1, 1);
		Vector4f colourV = new Vector4f(1, 1, 1, 1);
		
		Mesh topMesh = geometry.getMesh(top.texture, Geometry.MeshType.Solid);
		Mesh bottomMesh = geometry.getMesh(bottom.texture, Geometry.MeshType.Solid);
		Mesh sideMesh = geometry.getMesh(side.texture, Geometry.MeshType.AlphaTest);
		Mesh eyeMesh = geometry.getMesh(eyeSide.texture, Geometry.MeshType.Solid);
		
		// Top quad
		final float height = 13.0f / 16.0f;
		MeshUtil.addQuad(topMesh, new Vector3f(x,	y+height, z),
								  new Vector3f(x+1, y+height, z),
								  new Vector3f(x+1, y+height, z+1),
								  new Vector3f(x, 	y+height, z+1),
								  colourV, top);
		
		// Bottom
		BlockUtil.addBottom(context, rawChunk, bottomMesh, x, y, z, colour, bottom, registry);
		
		// Sides
		BlockUtil.addNorth(context, rawChunk, sideMesh, x, y, z, colour, side, registry);
		BlockUtil.addSouth(context, rawChunk, sideMesh, x, y, z, colour, side, registry);
		BlockUtil.addEast(context, rawChunk, sideMesh, x, y, z, colour, side, registry);
		BlockUtil.addWest(context, rawChunk, sideMesh, x, y, z, colour, side, registry);
		
		if ((data & 0x4) > 0)
		{
			// Optional eye stone
			final float eyeLeft = 4.0f / 16.0f;
			final float eyeRight = 12.0f / 16.0f;
			
			// Eye top
			MeshUtil.addQuad(eyeMesh,	new Vector3f(x+eyeLeft,		y+1,		z+eyeLeft),
										new Vector3f(x+eyeRight,	y+1,		z+eyeLeft),
										new Vector3f(x+eyeRight,	y+1,		z+eyeRight),
										new Vector3f(x+eyeLeft, 	y+1,		z+eyeRight),
										colourV, eyeTop);
			
			// North
			MeshUtil.addQuad(eyeMesh,	new Vector3f(x+eyeLeft,		y+1,		z+eyeLeft),
										new Vector3f(x+eyeLeft,		y+1,		z+eyeRight),
										new Vector3f(x+eyeLeft,		y+height,	z+eyeRight),
										new Vector3f(x+eyeLeft, 	y+height,	z+eyeLeft),
										colourV, eyeSide);
			
			// South
			MeshUtil.addQuad(eyeMesh,	new Vector3f(x+eyeRight,	y+1,	z+eyeRight),
										new Vector3f(x+eyeRight,	y+1,		z+eyeLeft),
										new Vector3f(x+eyeRight,	y+height,	z+eyeLeft),
										new Vector3f(x+eyeRight, 	y+height,	z+eyeRight),
										colourV, eyeSide);
			
			// West
			MeshUtil.addQuad(eyeMesh,	new Vector3f(x+eyeLeft,		y+1,		z+eyeRight),
					  					new Vector3f(x+eyeRight,	y+1,		z+eyeRight),
					  					new Vector3f(x+eyeRight,	y+height,	z+eyeRight),
					  					new Vector3f(x+eyeLeft, 	y+height,	z+eyeRight),
					  					colourV, eyeSide);
			
			// East
			MeshUtil.addQuad(eyeMesh,	new Vector3f(x+eyeRight,	y+1,		z+eyeLeft),
										new Vector3f(x+eyeLeft,		y+1,		z+eyeLeft),
										new Vector3f(x+eyeLeft,		y+height,	z+eyeLeft),
										new Vector3f(x+eyeRight, 	y+height,	z+eyeLeft),
										colourV, eyeSide);
		}
	}
	
}
