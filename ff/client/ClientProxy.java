/* This file is part of PilesOfBlocks.

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

package mbrx.ff.client;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import mbrx.ff.CommonProxy;
import mbrx.ff.Sounds;

public class ClientProxy extends CommonProxy {

  public ClientProxy() {}

  @Override
  public void registerRenderers() {
    // MinecraftForgeClient.preloadTexture(ITEMS_PNG);
    // MinecraftForgeClient.preloadTexture(BLOCK_PNG);
  }

  @Override
  public void registerSounds() {
    MinecraftForge.EVENT_BUS.register(new Sounds());
  }
}
