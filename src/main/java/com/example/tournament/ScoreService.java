package com.example.tournament;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class ScoreService {
    private static int scoreA = 0;
    private static int scoreB = 0;

    public static void reset() { scoreA = 0; scoreB = 0; }
    public static void addA(int d) { scoreA = Math.max(0, scoreA + d); }
    public static void addB(int d) { scoreB = Math.max(0, scoreB + d); }
    public static int getA() { return scoreA; }
    public static int getB() { return scoreB; }

    public static ItemStack createScoreBook(String nameA, String nameB) {
        ItemStack book = new ItemStack(Items.BOOK);
        String title = "Scoreboard";
        book.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title));
        var lore = new java.util.ArrayList<Text>();
        lore.add(Text.literal(nameA + ": " + scoreA).styled(s -> s.withColor(0x55FF55)));
        lore.add(Text.literal(nameB + ": " + scoreB).styled(s -> s.withColor(0xFF5555)));
        book.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
        book.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return book;
    }
}
