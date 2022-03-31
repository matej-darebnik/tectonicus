/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.texture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackMcmeta {
	private Pack pack = new Pack();

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public class Pack {
		private String description = "";
		@JsonProperty("pack_format")
		private int packVersion = 0;
	}
}