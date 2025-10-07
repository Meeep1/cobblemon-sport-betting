package com.example.tournament;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class TournamentBookService {
	public static ItemStack createBook(Tournament t) {
		ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
		List<Text> lore = new ArrayList<>();
		if (t != null) {
			lore.add(Text.literal("ID: " + t.getId()).formatted(net.minecraft.util.Formatting.AQUA));
			lore.add(Text.literal("State: " + t.getState()).formatted(net.minecraft.util.Formatting.YELLOW));
			if (t.getMatches() != null) {
				for (var m : t.getMatches()) {
					String line = m.a + " vs " + m.b + (m.winner != null ? (" (" + m.winner + ")") : "");
					lore.add(Text.literal(line));
				}
			}
		} else {
			lore.add(Text.literal("No active tournament"));
		}
		book.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
		return book;
	}
}
