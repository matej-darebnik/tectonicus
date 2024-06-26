/*
 * Copyright (c) 2024 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.texture;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import tectonicus.Version;
import tectonicus.rasteriser.Rasteriser;
import tectonicus.rasteriser.Texture;
import tectonicus.rasteriser.TextureFilter;

import static tectonicus.util.ImageUtils.copy;

public class PackTexture
{
	private final Rasteriser rasteriser;
	
        @Getter
	private final String path;

	@Getter
	private BufferedImage image;

	@Getter
	@Setter
	private boolean isTranslucent;
	@Getter
	@Setter
	private boolean isTransparent;
	
	private Texture fullTexture;
	private Texture tileTexture;
	
	private Map<TextureRequest, SubTexture> subTextures;
	
	public PackTexture(Rasteriser rasteriser, String path, BufferedImage image) {
		this(rasteriser, path, image, false, false);
	}

	public PackTexture(Rasteriser rasteriser, String path, BufferedImage image, boolean isTransparent, boolean isTranslucent) {
		assert (rasteriser != null);

		this.rasteriser = rasteriser;

		this.path = path;

		this.image = image;
		this.isTransparent = isTransparent;
		this.isTranslucent = isTranslucent;

		subTextures = new HashMap<>();

		fullTexture = rasteriser.createTexture(image, TextureFilter.NEAREST);
	}

	public SubTexture find(TextureRequest request, Version version)
	{
		assert(request.path.equals(path));
		
		SubTexture sub = subTextures.get(request);
		
		if (sub == null)
		{
			if (request.isFullTexture())
			{
				sub = new SubTexture(fullTexture, 0, 0, 1, 1, version);
				
				subTextures.put(request, sub);
			}
			else if (request.isTile())
			{
				if (tileTexture == null)
					genTileTexture();
				
				sub = subTextures.get(request);
			}
		}
		
		return sub;
	}
	
	public SubTexture getFullTexture()
	{
		return new SubTexture(fullTexture, 0, 0, 1, 1);
	}
	
	private void genTileTexture()
	{
		BufferedImage[] mipmaps = PackTexture.generateTileMips(image);
		
		tileTexture = rasteriser.createTexture(mipmaps, TextureFilter.NEAREST);
		
		final float tileU = 1.0f / 16.0f;
		final float tileV = 1.0f / 16.0f;
		
		final float uNudge = tileU / 64.0f;
		final float vNudge = tileV / 64.0f;
		
		for (int tileX=0; tileX<16; tileX++)
		{
			for (int tileY=0; tileY<16; tileY++)
			{
				final float u0 = tileX * tileU + uNudge;
				final float u1 = (tileX+1) * tileU - uNudge;
				
				final float v0 = tileY * tileV + vNudge;
				final float v1 = (tileY+1) * tileV - vNudge;
				
				SubTexture sub = new SubTexture(tileTexture, u0, v0, u1, v1, Version.VERSION_4);
				subTextures.put(new TextureRequest(path, tileX, tileY), sub);
			}
		}
	}
	
	public static BufferedImage[] generateTileMips(BufferedImage inputImage)
	{
		ArrayList<BufferedImage> mipmaps = new ArrayList<>();
		
		mipmaps.add(inputImage);
		
		int size = inputImage.getWidth() / 2;
		
		BufferedImage previousImage = inputImage;
		while (size >= 16)
		{
			BufferedImage nextImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			
			final int prevTileSize = previousImage.getWidth() / 16;
			
			// Downsample
			for (int x=0; x<previousImage.getWidth()-1; x+=prevTileSize)
			{
				for (int y=0; y<previousImage.getHeight()-1; y+=prevTileSize)
				{
					BufferedImage srcTile = previousImage.getSubimage(x, y, prevTileSize, prevTileSize);
					BufferedImage dest = downsample(srcTile);
					
					copy(nextImage, dest, x/2, y/2);
				}
			}
			
			mipmaps.add(nextImage);
			previousImage = nextImage;
			
			size /= 2;
		}
		
		// Make the final mips
		while (size > 0)
		{
			BufferedImage nextImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			for (int x=0; x<nextImage.getWidth(); x++)
			{
				for (int y=0; y<nextImage.getHeight(); y++)
				{
					nextImage.setRGB(x, y, 0xFFFFFFFF);
				}
			}
			mipmaps.add(nextImage);
			
			size /= 2;
		}
		
		return mipmaps.toArray(new BufferedImage[0]);
	}
	
	private static BufferedImage downsample(BufferedImage src)
	{
		BufferedImage dest = new BufferedImage(src.getWidth()/2, src.getHeight()/2, BufferedImage.TYPE_INT_ARGB);
		
		for (int x=0; x<dest.getWidth(); x++)
		{
			for (int y=0; y<dest.getHeight(); y++)
			{
				int r = 0;
				int g = 0;
				int b = 0;
				int a = 0;
				int count = 0;
				
				Point[] offsets = { new Point(x*2,		y*2),
									new Point(x*2+1,	y*2),
									new Point(x*2,		y*2+1),
									new Point(x*2+1,	y*2+1) };
				
				for (Point p : offsets)
				{
					final int rgba = src.getRGB(p.x, p.y);
					
					final int srcA = (rgba >> 24) & 0xFF;
					final int srcR = (rgba >> 16) & 0xFF;
					final int srcG = (rgba >>  8) & 0xFF;
					final int srcB = (rgba >>  0) & 0xFF;
					
					if (srcA > 0)
					{
						r += srcR;
						g += srcG;
						b += srcB;
						a += srcA;
						
						count++;
					}
				}
				
				if (count > 0)
				{
					r /= count;
					g /= count;
					b /= count;
					a /= count;
				}
				
				final int outRgba = (a << 24) | (r << 16) | (g << 8) | (b);
				dest.setRGB(x, y, outRgba);
			}
		}
		
		return dest;
	}
}
