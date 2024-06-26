/*
 * Copyright (c) 2022 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.blockregistry;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import tectonicus.raw.BlockProperties;

@Getter
public class BlockVariant extends BlockState
{
	private final String name;
	private final Map<String, String> states;
	private final BlockStateModelsWeight modelsAndWeight;
	
	public BlockVariant(String name, BlockStateModelsWeight models)
	{
		this.name = name;
		this.states = new HashMap<>();
		this.modelsAndWeight = models;

		String[] statesArray = name.split(",");
		
		for(String s : statesArray)
		{
			String[] state = s.split("=");
			if(state.length > 1)
				states.put(state[0], state[1]);
			else
				states.put(state[0], "");
		}
	}
        
        @Override
        void addModels(List<BlockStateModel> models, BlockProperties properties) {
                String variantProperties = name;
                if (variantProperties.equals(StringUtils.EMPTY) || properties.contains(variantProperties) || properties.containsAll(states)) {
                        models.add(getRandomWeightedModel(modelsAndWeight));
                }
        }
}
