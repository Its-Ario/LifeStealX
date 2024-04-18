package org.strassburger.lifestealz.listener

import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.strassburger.lifestealz.Lifestealz
import org.strassburger.lifestealz.util.ManageCustomItems
import org.strassburger.lifestealz.util.ManagePlayerdata
import org.strassburger.lifestealz.util.Replaceable
import java.io.File
import java.util.*

class PlayerInteractionListener : Listener {
    @EventHandler
    fun playerInteractionListener(event: PlayerInteractEvent) {
        val player = event.player

        val worldWhitelisted = Lifestealz.instance.config.getList("worlds")?.contains(player.location.world.name)

        if (event.action.isRightClick) { // Check if it's a right-click event
            val item = event.item

            if (item != null) {
                if (isHeart(item)) {
                    if (worldWhitelisted == null || !worldWhitelisted) {
                        player.sendMessage(Lifestealz.getAndFormatMsg(false, "messages.worldNotWhitelisted", "&cThis world is not whitelisted for LifeStealZ!"))
                        return
                    }

                    val timeoutTime = Lifestealz.instance.config.getLong("heartCooldown")
                    if (Lifestealz.heartconsumeMap.get(player.uniqueId) != null && (Lifestealz.heartconsumeMap.get(player.uniqueId) as Long) + timeoutTime > System.currentTimeMillis()) {
                        player.sendMessage(Lifestealz.getAndFormatMsg(false, "messages.heartconsumeCooldown", "&cYou have to wait before using another heart!"))
                        return
                    }

                    val playerdata = ManagePlayerdata().getPlayerData(name = player.name, uuid = player.uniqueId.toString())

                    val configLimit = Lifestealz.instance.config.getInt("maxHearts")
                    if (playerdata.maxhp >= (configLimit * 2).toDouble()) {
                        player.sendMessage(Lifestealz.getAndFormatMsg(false, "messages.maxHeartLimitReached", "&cYou already reached the limit of %limit% hearts!", Replaceable("%limit%", configLimit.toString())))
                        return
                    }

                    val itemStack = item.clone() // Create a copy of the item
                    itemStack.amount -= 1

                    if (player.inventory.itemInMainHand == item) {
                        player.inventory.setItemInMainHand(itemStack)
                    } else if (player.inventory.itemInOffHand == item) {
                        player.inventory.setItemInOffHand(itemStack)
                    }

                    ManagePlayerdata().manageHearts(player = player, amount = 2.0, direction = "inc")
                    player.maxHealth += 2.0
                    player.health += 2.0

                    if (Lifestealz.instance.config.getBoolean("heartuseSound.enabled")) {
                        val sound = Sound.valueOf(Lifestealz.instance.config.getString("heartuseSound.sound")!!)
                        val volume = Lifestealz.instance.config.getDouble("heartuseSound.volume").toFloat()
                        val pitch = Lifestealz.instance.config.getDouble("heartuseSound.pitch").toFloat()
                        player.playSound(player.location, sound, volume, pitch)
                    }

                    val heartuseCommands = Lifestealz.instance.config.getStringList("heartuseCommands")
                    heartuseCommands.forEach {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it.replace("&player&", player.name))
                    }

                    if (Lifestealz.instance.config.getBoolean("playTotemEffect")) player.playEffect(EntityEffect.TOTEM_RESURRECT)

                    player.sendMessage(Lifestealz.getAndFormatMsg(true, "messages.heartconsume", "&7Cansumed a heart and got &c%amount% &7hearts!", Replaceable("%amount%", "1")))
                    Lifestealz.heartconsumeMap.put(player.uniqueId, System.currentTimeMillis())
                }

                if (isReviveCrystal(item)) {
                    if (worldWhitelisted == null || !worldWhitelisted) {
                        player.sendMessage(Lifestealz.getAndFormatMsg(false, "messages.worldNotWhitelisted", "&cThis world is not whitelisted for LifeStealZ!"))
                        return
                    }

                    val dir: File = Lifestealz.instance.dataFolder
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }

                    val uuidList = mutableListOf<String>()

                    for (file in dir.walkTopDown()) {
                        val filename = file.name.substringBefore(".")
                        if (!uuidList.contains(filename) && filename != "userData") uuidList.add(filename)
                    }

                    val inventory: Inventory =  Bukkit.createInventory(null, 6 * 9, Lifestealz.getAndFormatMsg(false, "messages.reviveTitle", "&8Revive a player"))

                    addNavbar(inventory)

//                    player.sendMessage(uuidList.toString())

                    for (uuidString in uuidList) {
//                        player.sendMessage(Component.text(uuidString))
                        val uuid: UUID = convertStringToUUID(uuidString) ?: continue

                        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)

//                        player.sendMessage(Component.text(offlinePlayer.name?: "no name"))

                        if (offlinePlayer.name == null) continue

                        val offlinePlayerData = ManagePlayerdata().getPlayerData(name = offlinePlayer.name!!, uuid = offlinePlayer.uniqueId.toString())

//                        player.sendMessage(Component.text("Maxhp: " + offlinePlayerData.maxhp.toString()))

                        if (offlinePlayerData.maxhp > 0.0) continue

                        inventory.addItem(getPlayerHead(offlinePlayer))
                    }

                    player.openInventory(inventory)
                    Lifestealz.reviveGuiMap[player.uniqueId] = inventory
                }
            }
        }
    }

    private fun isHeart(item: ItemStack): Boolean {
        val itemMeta = item.itemMeta

        if (itemMeta != null && itemMeta.persistentDataContainer.has(Lifestealz.HEART_KEY, PersistentDataType.STRING)) {
            return itemMeta.persistentDataContainer.get(Lifestealz.HEART_KEY, PersistentDataType.STRING) == "heart"
        }

        return false
    }

    private fun isReviveCrystal(item: ItemStack): Boolean {
        val itemMeta = item.itemMeta

        if (itemMeta != null && itemMeta.persistentDataContainer.has(Lifestealz.REVIVEITEM_KEY, PersistentDataType.STRING)) {
            return itemMeta.persistentDataContainer.get(Lifestealz.REVIVEITEM_KEY, PersistentDataType.STRING) == "reviveitem"
        }

        return false
    }

    private fun addNavbar(inventory: Inventory) {
        inventory.setItem(49, ManageCustomItems().createCloseItem())

        val glass = ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1)
        val glassMeta = glass.itemMeta
        glassMeta.displayName(Component.text("§r "))
        glass.itemMeta = glassMeta
        val glassSlots = listOf(45,46,47,48,50,51,52,53)
        for (slot in glassSlots) {
            inventory.setItem(slot, glass)
        }
    }

    private fun makeCustomItem(material: Material, name: Component, amount: Int, lore: MutableList<String>) : ItemStack {
        val customItem = ItemStack(material, amount)
        val customItemMeta = customItem.itemMeta
        customItemMeta.displayName(name)
        customItemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        customItemMeta.lore = lore
        customItem.itemMeta = customItemMeta

        return customItem
    }

    private fun convertStringToUUID(uuidString: String): UUID? {
        return try {
            UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun getPlayerHead(offlinePlayer: OfflinePlayer) : ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = head.itemMeta as SkullMeta

        skullMeta.displayName(Component.text("§d" + offlinePlayer.name))
        val lines: MutableList<Component> = mutableListOf(
                Lifestealz.getAndFormatMsg(false, "messages.revivePlayerDesc", "&7Click to revive this player"),
                Lifestealz.formatMsg("<dark_gray>" + offlinePlayer.uniqueId.toString())
        )
        skullMeta.lore(lines)
        skullMeta.owningPlayer = offlinePlayer
        head.itemMeta = skullMeta
        return head
    }
}