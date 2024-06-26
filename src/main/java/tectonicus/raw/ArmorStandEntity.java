/*
 * Copyright (c) 2024, Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.raw;

public class ArmorStandEntity extends BlockEntity
{
        private final float yaw;
        private final boolean invisible;
        private final boolean noBasePlate;
        
        private final Item feetArmor;
        private final Item legsArmor;
        private final Item chestArmor;
        private final Item headArmor;
    
	public ArmorStandEntity(int x, int y, int z, int localX, int localY, int localZ, float yaw, boolean invisible, boolean noBasePlate,
                                Item feetArmor, Item legsArmor, Item chestArmor, Item headArmor)
	{
		super(x, y, z, localX, localY, localZ);
                
                this.yaw = yaw;
                this.invisible = invisible;
                this.noBasePlate = noBasePlate;
                
                this.feetArmor = feetArmor;
                this.legsArmor = legsArmor;
                this.chestArmor = chestArmor;
                this.headArmor = headArmor;
	}
        
        public float getYaw() { return yaw; }
        public boolean getInvisible() { return invisible; }
        public boolean getNoBasePlate() { return noBasePlate; }
        
        public Item getFeetArmor() { return feetArmor; }
        public Item getLegsArmor() { return legsArmor; }
        public Item getChestArmor() { return chestArmor; }
        public Item getHeadArmor() { return headArmor; }
}
