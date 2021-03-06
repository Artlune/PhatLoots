package com.codisimus.plugins.phatloots;

import java.util.*;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.time.DateUtils;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A PhatLoot is a reward made up of money and items
 *
 * @author Codisimus
 */
public class PhatLoot {
    public static final int
        INDIVIDUAL = 0, COLLECTIVE1 = 1, COLLECTIVE2 = 2, COLLECTIVE3 = 3,
        COLLECTIVE4 = 4, COLLECTIVE5 = 5, COLLECTIVE6 = 6, COLLECTIVE7 = 7,
        COLLECTIVE8 = 8, COLLECTIVE9 = 9, COLLECTIVE10 = 10;
    static boolean onlyDropOnPlayerKill;
    static boolean replaceMobLoot;
    static boolean displayTimeRemaining;
    static boolean displayMobTimeRemaining;

    private static PhatLootsCommandSender cs = new PhatLootsCommandSender();

    public String name; //A unique name for the Warp
    public int numberCollectiveLoots = PhatLoots.defaultNumberOfLoots; //Amount of loots received from each collective loot

    public int moneyLower; //Range of money that may be given
    public int moneyUpper;

    public int expLower; //Range of experience gained when looting
    public int expUpper;

    public LinkedList<String> commands = new LinkedList<String>(); //Commands that will be run upon looting the Chest

    @SuppressWarnings("unchecked")
    private HashSet<Loot>[] lootTables = (HashSet<Loot>[]) new HashSet[11]; //List of items that may be given

    public int days = PhatLoots.defaultDays; //Reset time (will never reset if any are negative)
    public int hours = PhatLoots.defaultHours;
    public int minutes = PhatLoots.defaultMinutes;
    public int seconds = PhatLoots.defaultSeconds;

    public boolean global = PhatLoots.defaultGlobal; //Reset Type
    public boolean round = PhatLoots.defaultRound;

    private HashSet<PhatLootChest> chests = new HashSet<PhatLootChest>(); //List of PhatLootChests that activate the Warp

    Properties lootTimes = new Properties(); //PhatLootChest'PlayerName=Year'Day'Hour'Minute'Second

    /**
     * Constructs a new PhatLoot
     *
     * @param name The name of the PhatLoot which will be created
     */
    public PhatLoot(String name) {
        this.name = name;
        for (int i = 0; i < 11; i++) {
            lootTables[i] = new HashSet<Loot>();
        }
    }

    /**
     * Activates the PhatLoot by checking for remaining time and receiving loots
     *
     * @param player The Player who is looting
     * @param block The Block being looted
     */
    @SuppressWarnings("deprecation")
    public void rollForLoot(Player player, PhatLootChest chest, Inventory inventory) {
        //Get the user to be looked up for last time of use
        String user = player.getName();
        if (global) {
            user = "global";
        }

        //Find out how much time remains
        String timeRemaining = getTimeRemaining(getTime(chest, user));

        //User can never loot the Chest again if timeRemaining is null
        if (timeRemaining == null) {
            return;
        }

        //Display remaining time if it is not
        if (!timeRemaining.equals("0")) {
            if (displayTimeRemaining) {
                player.sendMessage(PhatLootsMessages.timeRemaining.replace("<time>", timeRemaining));
            }

            return;
        }

        //Reset(Clear) the Inventory
        inventory.clear();

        //Roll for money amount if the range is above 0
        if (moneyUpper > 0) {
            int amount = PhatLoots.random.nextInt((moneyUpper + 1) - moneyLower);
            amount = amount + moneyLower;

            //Give money to the Player if there is money to give
            if (amount > 0) {
                String money = Econ.reward(player.getName(), amount);
                player.sendMessage(PhatLootsMessages.moneyLooted
                        .replace("<amount>", money));
            }
        }

        //Roll for exp amount if the range is above 0
        if (expUpper > 0) {
            int amount = PhatLoots.random.nextInt((expUpper + 1) - expLower);
            amount = amount + expLower;

            //Give exp to the Player if there is exp to give
            if (amount > 0) {
                player.giveExp(amount);
                player.sendMessage(PhatLootsMessages.experienceLooted
                        .replace("<amount>", String.valueOf(amount)));
            }
        }

        //Execute each command
        for (String cmd: commands) {
            PhatLoots.server.dispatchCommand(cs, cmd.replace("<player>", player.getName()));
        }

        //Give individual loots
        boolean itemsInChest = chest.addLoots(lootIndividual(), player, inventory);

        //Give collective loots
        if (chest.addLoots(lootCollective(), player, inventory)) {
            itemsInChest = true;
        }

        //Update the Inventory View
        if (!chest.isDispenser) {
            player.updateInventory();
        }

        if (PhatLoots.autoLoot && !itemsInChest) {
            player.closeInventory();
            PhatLoots.closeInventory(player, inventory, chest.getBlock().getLocation(), global);
        }

        //Set the new time for the User and return true
        setTime(chest, user);
    }

    public int rollForLoot(Player player, List<ItemStack> drops) {
        if (replaceMobLoot) {
            drops.clear();
        }
        if (onlyDropOnPlayerKill && player == null) {
            return 0;
        }

        //Find out how much time remains
        String timeRemaining = getTimeRemaining(getMobLootTime(player.getName()));

        //User can never loot the Chest again if timeRemaining is null
        if (timeRemaining == null) {
            return 0;
        }

        //Display remaining time if it is not
        if (!timeRemaining.equals("0T")) {
            if (displayMobTimeRemaining) {
                player.sendMessage(PhatLootsMessages.mobTimeRemaining.replace("<time>", timeRemaining));
            }

            return 0;
        }

        List<ItemStack> loot = lootIndividual();
        loot.addAll(lootCollective());
        if (player != null && !PhatLootsMessages.mobDroppedMoney.isEmpty()) {
            for (ItemStack item : loot) {
                player.sendMessage(PhatLootsMessages.mobDroppedMoney.replace("<item>", getItemName(item)));
            }
        }
        drops.addAll(loot);

        //Roll for money amount if the range is above 0
        if (moneyUpper > 0 && player != null) {
            int amount = PhatLoots.random.nextInt((moneyUpper + 1) - moneyLower);
            amount = amount + moneyLower;

            //Give money to the Player if there is money to give
            if (amount > 0 && !player.getGameMode().equals(GameMode.CREATIVE)
                    && PhatLoots.hasPermission(player, "moneyfrommobs")) {
                String money = Econ.reward(player.getName(), amount);
                player.sendMessage(PhatLootsMessages.mobDroppedMoney.replace("<amount>", money));
            }
        }

        //Execute each command
        for (String cmd: commands) {
            PhatLoots.server.dispatchCommand(cs, cmd.replace("<player>", player.getName()));
        }

        //Roll for exp amount if the range is above 0
        if (expUpper > 0) {
            int amount = PhatLoots.random.nextInt((expUpper + 1) - expLower);
            return amount + expLower;
        }

        return 0;
    }

    /**
     * Returns the remaining time until the PhatLootChest resets
     * Returns null if the PhatLootChest never resets
     *
     * @param time The given time
     * @return the remaining time until the PhatLootChest resets
     */
    public String getTimeRemaining(long time) {
        //Return null if the reset time is set to never
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0) {
            return null;
        }

        //Calculate the time that the Warp will reset
        time += days * DateUtils.MILLIS_PER_DAY
                + hours * DateUtils.MILLIS_PER_HOUR
                + minutes * DateUtils.MILLIS_PER_MINUTE
                + seconds * DateUtils.MILLIS_PER_SECOND;

        long timeRemaining = time - System.currentTimeMillis();

        if (timeRemaining > DateUtils.MILLIS_PER_DAY) {
            return (int) timeRemaining / DateUtils.MILLIS_PER_DAY + " day(s)";
        } else if (timeRemaining > DateUtils.MILLIS_PER_HOUR) {
            return (int) timeRemaining / DateUtils.MILLIS_PER_HOUR + " hour(s)";
        } else if (timeRemaining > DateUtils.MILLIS_PER_MINUTE) {
            return (int) timeRemaining / DateUtils.MILLIS_PER_MINUTE + " minute(s)";
        } else if (timeRemaining > DateUtils.MILLIS_PER_SECOND) {
            return (int) timeRemaining / DateUtils.MILLIS_PER_SECOND + " second(s)";
        } else {
            return "0";
        }
    }

    /**
     * Fills the Chest (Block) with loot
     * Each item is rolled for to determine if it will by added to the Chest
     * Money is rolled for to determine how much will be given within the range
     */
    public List<ItemStack> lootIndividual() {
        List<ItemStack> itemList = new LinkedList<ItemStack>();
        for (Loot loot : lootTables[INDIVIDUAL]) {
            //Roll for item
            if (PhatLoots.random.nextInt(100) + PhatLoots.random.nextDouble()
                    < loot.getProbability()) {
                itemList.add(loot.getItem());
            }
        }
        return itemList;
    }

    /**
     * Fills the Chest (Block) with loot
     * Items are rolled for in order until the maximum number is added to the Chest
     */
    public List<ItemStack> lootCollective() {
        List<ItemStack> itemList = new LinkedList<ItemStack>();

        //Loot from each of the 10 collective loots
        for (int i = 1; i <= 10; i++) {
            //Make sure there are items that will be looted before entering the loop
            if (!lootTables[i].isEmpty()) {
                //Do not loot if the probability does not add up to 100
                if (getPercentRemaining(i) != 0) {
                    PhatLoots.logger.warning("Cannot loot Coll" + i + " of "
                            + name + " because the probability does not equal 100%");
                } else {
                    //Create an array of 100 Loots
                    Loot[] collLoots = new Loot[100];
                    int j = 0;

                    //Add each loot to the array of Loots
                    for (Loot loot : lootTables[i]) {
                        //The amount of times the Loot is added is determined by the probability
                        for (int k = 0; k < loot.getProbability(); k++) {
                            try {
                                collLoots[j] = loot;
                            } catch (ArrayIndexOutOfBoundsException e) {
                                PhatLoots.logger.warning("Cannot loot Coll" + i
                                        + " of " + name + " because the probability does not equal 100%");
                            }
                            j++;
                        }
                    }

                    if (j < 100) {
                        PhatLoots.logger.warning("Cannot loot Coll" + i + " of "
                                + name + " because the probability does not equal 100%");
                    }

                    //Loot the specified number of items
                    for (int numberLooted = 0; numberLooted < numberCollectiveLoots; numberLooted++) {
                        //Generate a random int to determine the index of the array that holds the Loot
                        itemList.add(collLoots[PhatLoots.random.nextInt(100)].getItem());
                    }
                }
            }
        }

        return itemList;
    }

    /**
     * Updates the Player's time value in the Map with the current time
     *
     * @param chest The PhatLootChest to set the time for
     * @param player The Player whose time is to be updated
     */
    public void setTime(PhatLootChest chest, String player) {
        Calendar calendar = Calendar.getInstance();

        if (round) {
            if (seconds == 0) {
                calendar.clear(Calendar.SECOND);
                if (minutes == 0) {
                    calendar.clear(Calendar.MINUTE);
                    if (hours == 0) {
                        calendar.clear(Calendar.HOUR_OF_DAY);
                    }
                }
            }
        }

        lootTimes.setProperty(chest.toString() + "'" + player, String.valueOf(System.currentTimeMillis()));
    }

    /**
     * Retrieves the time for the given Player
     *
     * @param player The Player whose time is requested
     * @return The time as an array of ints
     */
    public long getMobLootTime(String player) {
        String string = lootTimes.getProperty(player);
        long time = 0;

        if (string != null) {
            try {
                time = Long.parseLong(string);
            } catch (Exception corruptData) {
                PhatLoots.logger.severe("Fixed corrupted time value!");
            }
        }

        return time;
    }

    /**
     * Retrieves the time for the given Player
     *
     * @param chest The PhatLootChest to set the time for
     * @param player The Player whose time is requested
     * @return The time as an array of ints
     */
    public long getTime(PhatLootChest chest, String player) {
        String string = lootTimes.getProperty(chest.toString() + "'" + player);
        long time = 0;

        if (string != null) {
            try {
                time = Long.parseLong(string);
            } catch (Exception corruptData) {
                PhatLoots.logger.severe("Fixed corrupted time value!");
            }
        }

        return time;
    }

    /**
     * Returns the Remaining Percent of the given collective Loots
     *
     * @param id The id of the collective Loots
     * @return Total probability of all Loots in the collective Loots subtracted from 100
     */
    public double getPercentRemaining(int id) {
        //Subtract the probabilty of each loot from 100
        double total = 100;
        for (Loot loot : lootTables[id]) {
            total -= loot.getProbability();
        }
        return total;
    }

    /**
     * Loads data from the save file
     *
     * @param id The id of the Loots (0 for individual loots)
     * @param string The data of the Loots
     */
    public void setLoots(int id, String lootsString) {
        //Cancel if no data was provided
        if (lootsString.isEmpty()) {
            return;
        }

        while (lootsString.endsWith(",") || lootsString.endsWith(" ")) {
            lootsString = lootsString.substring(0, lootsString.length() - 1);
        }

        //Load data for each loot
        for (String lootString: lootsString.split(", ")) {
            try {
                String[] lootData = lootString.split("'");

                String item = lootData[0];
                int itemID;
                //Check for Dyed Color
                Color color = null;
                if (item.startsWith("(")) {
                    color = Color.fromRGB(Integer.parseInt(item.substring(1, 8)));
                    item = item.substring(9);
                }
                //Check for Name of Item Description
                if (item.contains("+")) {
                    int index = item.indexOf('+');
                    itemID = Integer.parseInt(item.substring(0, index));
                    item = item.substring(index + 1);
                } else {
                    itemID = Integer.parseInt(item);
                    item = "";
                }

                String data = lootData[1];
                Map<Enchantment, Integer> enchantments = null;
                //Check for Enchantments
                if (data.contains("+")) {
                    int index = data.indexOf('+');
                    enchantments = PhatLootsCommand.getEnchantments(
                            data.substring(index + 1));
                    data = data.substring(0, index);
                }

                String amount = lootData[2];
                int lower = PhatLootsCommand.getLowerBound(amount);
                int upper = PhatLootsCommand.getUpperBound(amount);

                if (lower == -1 || upper == -1) {
                    throw new RuntimeException();
                }

                Loot loot = new Loot(itemID, lower, upper);
                if (color != null) {
                    loot.setColor(color);
                }
                loot.setProbability(Double.parseDouble(lootData[3]));

                try {
                    loot.setDurability(Short.parseShort(data));
                } catch (Exception notDurability) {
                    enchantments = PhatLootsCommand.getEnchantments(data);
                }
                loot.setEnchantments(enchantments);

                loot.name = item;
                lootTables[id].add(loot);
            } catch (Exception invalidLoot) {
                PhatLoots.logger.info("Error occured while loading PhatLoot "
                                        + '"' + name + '"' + ", " + '"' + lootString
                                        + '"' + " is not a valid Loot");
                invalidLoot.printStackTrace();
            }
        }
    }

    /**
     * Loads data from the save file
     *
     * @param string The data of the Chests
     */
    public void setChests(String data) {
        //Cancel if no data was provided
        if (data.isEmpty()) {
            return;
        }

        for (String chest: data.split(", ")) {
            try {
                String[] chestData = chest.split("'");

                //Check if the World is not loaded
                if (PhatLoots.server.getWorld(chestData[0]) == null) {
                    continue;
                }

                //Construct a new PhatLootChest with the Location data
                PhatLootChest phatLootChest = new PhatLootChest(chestData[0], Integer.parseInt(chestData[1]),
                        Integer.parseInt(chestData[2]), Integer.parseInt(chestData[3]));

                chests.add(phatLootChest);
            } catch (Exception invalidChest) {
                PhatLoots.logger.info("Error occured while loading PhatLoot "
                                        + '"' + name + '"' + ", " + '"' + chest
                                        + '"' + " is not a valid PhatLootChest");
                invalidChest.printStackTrace();
            }
        }
    }

    /**
     * Returns the HashSet of Loots
     *
     * @param id The id of the LootTable
     * @return The HashSet of Loots
     */
    public HashSet<Loot> getLootTable(int id) {
        return lootTables[id];
    }

    /**
     * Returns true if the given Loot is inside the specified LootTable
     *
     * @param id The id of the LootTable
     * @return true if the LootTable contains the Loot
     */
    public boolean containsLoot(int id, Loot loot) {
        return lootTables[id].contains(loot);
    }

    /**
     * Returns the List of Loots as a String
     *
     * @param id The id of the LootTable
     * @return The List of Loots as a String
     */
    public String lootTableToString(int id) {
        String list = "";

        //Concat each Loot onto the list
        for (Loot loot : lootTables[id]) {
            list += loot.toInfoString();
        }

        if (!list.isEmpty()) {
            list = list.substring(2);
        }

        return list;
    }

    /**
     * Creates a PhatLootChest for the given Block and links it to this PhatLoot
     *
     * @param block The given Block
     */
    public void addChest(Block block) {
        chests.add(new PhatLootChest(block));
    }

    /**
     * Removes the PhatLootChest for the given Block from this PhatLoot
     *
     * @param block The given Block
     */
    public void removeChest(Block block) {
        chests.remove(new PhatLootChest(block));
    }

    /**
     * Returns whether the given PhatLootChest is linked to this PhatLoot
     *
     * @param chest The given PhatLootChest
     * @return true if the PhatLoot chest is linked
     */
    public boolean containsChest(PhatLootChest chest) {
        return chests.contains(chest);
    }

    /**
     * Returns a Collection of PhatLootChests linked to this PhatLoot
     *
     * @return a Collection of linked chests
     */
    public Collection<PhatLootChest> getChests() {
        return chests;
    }

    /**
     * Resets the user times for all PhatLootChests of this PhatLoot
     * If a Block is given then only reset that PhatLootChest
     *
     * @param block The given Block
     */
    public void reset(Block block) {
        if (block == null) {
            //Reset all PhatLootChests
            lootTimes.clear();
        } else {
            //Find the PhatLootChest of the given Block and reset it
            String chest = block.getWorld() + "'" + block.getX() + "'" + block.getY()+ "'" + block.getZ() + "'";
            for (String key : lootTimes.stringPropertyNames()) {
                if (key.startsWith(chest)) {
                    lootTimes.remove(key);
                }
            }
        }
        save();
    }

    /**
     * Writes the PhatLoot data to file
     */
    public void save() {
        PhatLoots.savePhatLoot(this);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta()) {
            String name = item.getItemMeta().getDisplayName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return WordUtils.capitalizeFully(item.getType().toString().replace("_", " "));
    }

    public void convertLootTimes() {
        Calendar cal = Calendar.getInstance();
        for (Object key : lootTimes.keySet()) {
            try {
                String s = key.toString();
                String[] fields = lootTimes.getProperty(s).split("'");
                cal.set(Calendar.YEAR, Integer.parseInt(fields[0]));
                cal.set(Calendar.DAY_OF_YEAR, Integer.parseInt(fields[1]));
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(fields[2]));
                cal.set(Calendar.MINUTE, Integer.parseInt(fields[3]));
                cal.set(Calendar.SECOND, Integer.parseInt(fields[4]));
                lootTimes.setProperty(s, String.valueOf(cal.getTimeInMillis()));
            } catch (Exception ex) {
                PhatLoots.logger.severe(name + ".loottimes has been corrupted");
            }
        }
    }

    public int phatLootChestHashCode(Block block) {
        int hash = 7;
        hash = 47 * hash + block.getWorld().getName().hashCode();
        hash = 47 * hash + block.getX();
        hash = 47 * hash + block.getY();
        hash = 47 * hash + block.getZ();
        return hash;
    }
}
