/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockTypes;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import tectonicus.BlockContext;
import tectonicus.BlockType;
import tectonicus.BlockTypeRegistry;
import tectonicus.chunk.Chunk;
import tectonicus.configuration.LightFace;
import tectonicus.rasteriser.Mesh;
import tectonicus.rasteriser.MeshUtil;
import tectonicus.raw.RawChunk;
import tectonicus.renderer.Geometry;
import tectonicus.texture.SubTexture;

import static tectonicus.Version.VERSION_4;

public class Cake implements BlockType
{
	private final String name;
	
	private SubTexture top, side, interior;
	
	public Cake(String name, SubTexture top, SubTexture s, SubTexture interior)
	{
		this.name = name;
		
		this.top = top;
		
		final float half;
		if (top.texturePackVersion == VERSION_4)
			half = 1.0f / 16.0f / 2.0f;
		else
			half = 1.0f / 2.0f;
		
		this.side = new SubTexture(s.texture, s.u0, s.v0+half, s.u1, s.v1);
		this.interior = new SubTexture(interior.texture, interior.u0, interior.v0+half, interior.u1, interior.v1);
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
		
		Mesh topMesh = geometry.getMesh(top.texture, Geometry.MeshType.AlphaTest);
		Mesh sideMesh = geometry.getMesh(side.texture, Geometry.MeshType.AlphaTest);
		Mesh interiorMesh = geometry.getMesh(interior.texture, Geometry.MeshType.AlphaTest);
		
		final float lightness = Chunk.getLight(world.getLightStyle(), LightFace.Top, chunk, x, y, z, world.getNightLightAdjustment());
		
		Vector4f white = new Vector4f(lightness, lightness, lightness, 1);
		
		final float texel = 1.0f / 16.0f;
		final float offset = texel * 2 * data;
		
		final float actualY = y + 0.5f;
		
		final float uRange = top.u1 - top.u0;
		final float uEdge = uRange / 16.0f;
		final float uSeg = (uRange / 8.0f);
		float uInc = uEdge + uSeg * data;
		
		// Top
		MeshUtil.addQuad(topMesh,	new Vector3f(x + texel + offset,	actualY, z),
									new Vector3f(x+1-texel,				actualY, z),
									new Vector3f(x+1-texel,				actualY, z+1),
									new Vector3f(x + texel + offset,	actualY, z+1),
									white,
									new Vector2f(top.u0+uInc, top.v0),
									new Vector2f(top.u1-uSeg, top.v0),
									new Vector2f(top.u1-uSeg, top.v1),
									new Vector2f(top.u0+uInc, top.v1)
					);
		
		// South
		MeshUtil.addQuad(sideMesh,	new Vector3f(x + texel + offset,	actualY,	z+1-texel),
									new Vector3f(x+1-texel,				actualY,	z+1-texel),
									new Vector3f(x+1-texel,				y,			z+1-texel),
									new Vector3f(x + texel + offset,	y,			z+1-texel),
									white,
									new Vector2f(side.u0+uInc, side.v0),
									new Vector2f(side.u1-uSeg, side.v0),
									new Vector2f(side.u1-uSeg, side.v1),
									new Vector2f(side.u0+uInc, side.v1)
					);

		// North
		MeshUtil.addQuad(sideMesh,	new Vector3f(x+1-texel,	actualY,	z+texel),
									new Vector3f(x+texel+offset,	actualY,	z+texel),
									new Vector3f(x+texel+offset,	y,			z+texel),
									new Vector3f(x+1-texel,	y,			z+texel),
									white,
									new Vector2f(side.u0+uInc, side.v0),
									new Vector2f(side.u1-uSeg, side.v0),
									new Vector2f(side.u1-uSeg, side.v1),
									new Vector2f(side.u0+uInc, side.v1)
					);
		
		// West
		SubTexture westTex = data == 0 ? side : interior;
		Mesh westMesh = data == 0 ? sideMesh : interiorMesh;
		MeshUtil.addQuad(westMesh,	new Vector3f(x+texel+offset,	actualY,	z+texel),
									new Vector3f(x+texel+offset,	actualY,	z+1-texel),
									new Vector3f(x+texel+offset,	y,			z+1-texel),
									new Vector3f(x+texel+offset,	y,			z+texel),
									white,
									new Vector2f(westTex.u0+uSeg, westTex.v0),
									new Vector2f(westTex.u1-uSeg, westTex.v0),
									new Vector2f(westTex.u1-uSeg, westTex.v1),
									new Vector2f(westTex.u0+uSeg, westTex.v1)
					);
		
		// East (always the same)
		MeshUtil.addQuad(sideMesh,	new Vector3f(x+1-texel, actualY,	z+1),
									new Vector3f(x+1-texel, actualY,	z),
									new Vector3f(x+1-texel, y,			z),
									new Vector3f(x+1-texel, y,			z+1),
									white,
									side);
	}
	
}
