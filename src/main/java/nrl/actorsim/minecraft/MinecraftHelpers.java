package nrl.actorsim.minecraft;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MinecraftHelpers {

    public static String sanitizeItemName(String item) {
        return item;
    }

    public static Set<Item> findAllMatching(String item) {
        return Registry.ITEM.stream()
                .filter(itemToCheck -> itemToCheck.getName().toString().contains(item))
                .collect(Collectors.toSet());
    }

    public static Optional<Item> findExactMatch(String item) {
        return Registry.ITEM.stream()
                .filter(itemToCheck -> itemToCheck.getName().toString().equals(item))
                .findFirst();
    }

    public static Item findBestItemMatch(Command command) {
        return findBestItemMatch(command.item, command.action);
    }

    public static Item findBestItemMatch(String item, Command.ActionName context) {
        Optional<Item> exactMatch = findExactMatch(item);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        switch (context) {
            case MINE:
                switch (item) {
                    case "iron":
                        return Items.IRON_ORE;
                    case "diamond":
                        return Items.DIAMOND_ORE;
                    case "log":
                        return Items.OAK_LOG;
                    case "wood":
                        return Items.OAK_LOG;
                    case "coal":
                        return Items.COAL_ORE;
                }
            case CRAFT:
            case GIVE:
                switch (item) {
                    case "iron":
                        return Items.IRON_INGOT;
                    case "diamond":
                        return Items.DIAMOND;
                    case "coal":
                        return Items.COAL;
                }
            default:
                switch (item) {
                    case "sticks":
                        return Items.STICK;
                    case "stick":
                        return Items.STICK;
                    case "torch":
                        return Items.TORCH;
                    case "wood":
                        return Items.OAK_PLANKS;
                    case "planks":
                        return Items.OAK_PLANKS;
                    case "pickaxe":
                        return Items.IRON_PICKAXE;
                }
        }
        return Registry.ITEM.stream()
                .filter(itemToCheck -> itemToCheck.getName().toString().contains(item))
                .findFirst()
                .orElse(null);
    }
}
