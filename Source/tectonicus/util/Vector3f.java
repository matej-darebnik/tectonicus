/*
 * Copyright (c) 2012-2015, John Campbell and other contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.util;

public class Vector3f implements ImmutableVector3f
{
	private final float x, y, z;
	
	public float x() { return x; }
	public float y() { return y; }
	public float z() { return z; }
	
	public Vector3f()
	{
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}
	
	public Vector3f(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	public String toString()
	{
		return "[" + x + ", " + y + ", " + z + "]";
	}
	
	public static float length(final float x, final float y, final float z)
	{
		return (float)Math.sqrt(x*x + y*y + z*z);
	}
	
	public static float separation(Vector3f lhs, Vector3f rhs)
	{
		final float dx = lhs.x - rhs.x;
		final float dy = lhs.y - rhs.y;
		final float dz = lhs.z - rhs.z;
		
		return (float)Math.sqrt( dx*dx + dy*dy + dz*dz );
	}
}
