/*
 * Copyright (c) 2025 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.rasteriser;


public enum TextureFormat {
	RGB(3),
	RGBA(4);
	
	private final int bytesPerPixel;
	
	TextureFormat(final int bytes) {
		this.bytesPerPixel = bytes;
	}
	
	public int bytesPerPixel() {
		return bytesPerPixel;
	}
}
