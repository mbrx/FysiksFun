/* This file is part of FlowingFluids.

 Copyright 2013 Mathias Broxvall

 PilesOfBlocks is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 PilesOfBlocks is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with PilesOfBlocks.  If not, see <http://www.gnu.org/licenses/>.
 */

package mbrx.ff;

import net.minecraft.entity.player.EntityPlayer;

public class CommonProxy {
	public static String ITEMS_PNG = "/mbrx/ff/items.png";
	public static String BLOCK_PNG = "/mbrx/ff/block.png";

	// Client stuff
	public void registerRenderers() {
		// Nothing here as the server doesn't render graphics!
	}
	
	public void registerSounds() {
	  // Nothing here as the server shouldn't register any sounds
	}
}
