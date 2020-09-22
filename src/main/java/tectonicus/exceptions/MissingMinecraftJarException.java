/*
 * Copyright (c) 2020 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.exceptions;

public class MissingMinecraftJarException extends RuntimeException {

	public MissingMinecraftJarException(String message) {
		super(message);
	}
}
