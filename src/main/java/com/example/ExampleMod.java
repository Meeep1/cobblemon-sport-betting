package com.example;

import com.example.bet.BetManager;
import com.example.bet.PredefinedOddsRegistry;
import com.example.gui.BettingMenuFactory;
import com.example.gui.AdminMenuFactory;
import com.example.config.ConfigManager;
import com.google.gson.JsonObject;
import com.example.region.RegionService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.integration.CobblemonIntegration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Cobblemon Betting mod initializing");

		// Load config
		JsonObject cfg = ConfigManager.load();
		if (cfg.has("housePays")) {
			BetManager.INSTANCE.setHousePays(cfg.get("housePays").getAsBoolean());
		}
		// Load feature flags
		com.example.config.FeatureFlags.loadFromConfig();
		// Load dynamic odds config
		com.example.config.DynamicOddsConfig.loadFromConfig();
		// Load props config
		com.example.config.PropsConfig.loadFromConfig();
		// Initialize region service (registers tick listener and loads config)
		RegionService unused = RegionService.INSTANCE;
		// Try to hook Cobblemon events if the mod is present
		com.example.integration.CobblemonIntegration.tryHookCobblemon();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// /betflags <receipts|sounds|self> <on|off>
			dispatcher.register(CommandManager.literal("betflags")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("receipts")
					.then(CommandManager.literal("on").executes(ctx -> { com.example.config.FeatureFlags.setBetReceipts(true); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Bet receipts ENABLED"), true); return 1; }))
					.then(CommandManager.literal("off").executes(ctx -> { com.example.config.FeatureFlags.setBetReceipts(false); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Bet receipts DISABLED"), true); return 1; }))
				)
				.then(CommandManager.literal("sounds")
					.then(CommandManager.literal("on").executes(ctx -> { com.example.config.FeatureFlags.setSoundCues(true); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Sound cues ENABLED"), true); return 1; }))
					.then(CommandManager.literal("off").executes(ctx -> { com.example.config.FeatureFlags.setSoundCues(false); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Sound cues DISABLED"), true); return 1; }))
				)
				.then(CommandManager.literal("self")
					.then(CommandManager.literal("on").executes(ctx -> { com.example.config.FeatureFlags.setSelfBetRestrict(true); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Self-bet restriction ENABLED"), true); return 1; }))
					.then(CommandManager.literal("off").executes(ctx -> { com.example.config.FeatureFlags.setSelfBetRestrict(false); com.example.config.FeatureFlags.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Self-bet restriction DISABLED"), true); return 1; }))
				)
			);
			// /setodds <playerA> <oddsA> <playerB> <oddsB>
			dispatcher.register(CommandManager.literal("setodds")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.argument("playerA", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("oddsA", IntegerArgumentType.integer(1))
						.then(CommandManager.argument("playerB", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
							.then(CommandManager.argument("oddsB", IntegerArgumentType.integer(1))
								.executes(ctx -> {
									String aName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerA").iterator().next().getName();
									int oddsA = IntegerArgumentType.getInteger(ctx, "oddsA");
									String bName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerB").iterator().next().getName();
									int oddsB = IntegerArgumentType.getInteger(ctx, "oddsB");
									BetManager.INSTANCE.setOdds(aName, oddsA, bName, oddsB);
									// Also store as predefined for these two players
									PredefinedOddsRegistry.INSTANCE.set(aName, oddsA, bName, oddsB);
									ctx.getSource().sendFeedback(() -> Text.literal("Odds set: " + aName + "=" + (oddsA/100.0) + "x, " + bName + "=" + (oddsB/100.0) + "x"), true);
									return 1;
								})
							)
						)
					)
				)
			);

			// /odds team add/remove/list
			dispatcher.register(CommandManager.literal("odds")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("team")
					.then(CommandManager.literal("add")
						.then(CommandManager.argument("teamA", StringArgumentType.string())
							.then(CommandManager.argument("oddsA", IntegerArgumentType.integer(1))
								.then(CommandManager.argument("teamB", StringArgumentType.string())
									.then(CommandManager.argument("oddsB", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											String teamAStr = StringArgumentType.getString(ctx, "teamA");
											String teamBStr = StringArgumentType.getString(ctx, "teamB");
											int oa = IntegerArgumentType.getInteger(ctx, "oddsA");
											int ob = IntegerArgumentType.getInteger(ctx, "oddsB");
											java.util.LinkedHashSet<String> teamA = parseTeamNames(teamAStr);
											java.util.LinkedHashSet<String> teamB = parseTeamNames(teamBStr);
											if (teamA.isEmpty() || teamB.isEmpty()) { ctx.getSource().sendError(Text.literal("Teams cannot be empty. Use comma-separated names.")); return 0; }
											PredefinedOddsRegistry.INSTANCE.setTeam(teamA, oa, teamB, ob);
											ctx.getSource().sendFeedback(() -> Text.literal("Team odds saved: " + String.join(" & ", teamA) + "=" + (oa/100.0) + "x, " + String.join(" & ", teamB) + "=" + (ob/100.0) + "x"), true);
											return 1;
										})
									)
								)
							)
						)
					)
					.then(CommandManager.literal("remove")
						.then(CommandManager.argument("teamA", StringArgumentType.string())
							.then(CommandManager.argument("teamB", StringArgumentType.string())
								.executes(ctx -> {
									java.util.LinkedHashSet<String> teamA = parseTeamNames(StringArgumentType.getString(ctx, "teamA"));
									java.util.LinkedHashSet<String> teamB = parseTeamNames(StringArgumentType.getString(ctx, "teamB"));
									boolean removed = PredefinedOddsRegistry.INSTANCE.removeTeam(teamA, teamB);
									if (removed) ctx.getSource().sendFeedback(() -> Text.literal("Removed team odds for " + String.join(" & ", teamA) + " vs " + String.join(" & ", teamB)), true);
									else ctx.getSource().sendError(Text.literal("No predefined team odds found."));
									return removed ? 1 : 0;
								})
							)
						)
					)
					.then(CommandManager.literal("list").executes(ctx -> {
						var list = PredefinedOddsRegistry.INSTANCE.listTeamStrings(20);
						if (list.isEmpty()) ctx.getSource().sendFeedback(() -> Text.literal("No predefined team odds saved."), false);
						else list.forEach(s -> ctx.getSource().sendFeedback(() -> Text.literal(s), false));
						return 1;
					}))
				)
			);

			// /openadminmenu
			dispatcher.register(CommandManager.literal("openbetmenu")
				.executes(ctx -> {
					ServerCommandSource src = ctx.getSource();
					var player = src.getPlayer();
					if (player == null) {
						src.sendError(Text.literal("This command can only be used by a player."));
						return 0;
					}
					if (!BetManager.INSTANCE.isBettingOpen()) {
						src.sendFeedback(() -> Text.literal("Betting is currently closed.").formatted(Formatting.RED), false);
						return 1;
					}
					player.openHandledScreen(BettingMenuFactory.create());
					return 1;
				})
			);


			// /openprops (only while betting is open)
			dispatcher.register(CommandManager.literal("openprops")
				.executes(ctx -> {
					ServerCommandSource src = ctx.getSource();
					var player = src.getPlayer();
					if (player == null) { src.sendError(Text.literal("This command can only be used by a player.")); return 0; }
					if (!BetManager.INSTANCE.isBettingOpen()) {
						src.sendFeedback(() -> Text.literal("Betting is currently closed.").formatted(Formatting.RED), false);
						return 1;
					}
					player.openHandledScreen(com.example.gui.PropsMenuFactory.create());
					return 1;
				})
			);

			// /bethelp - player and admin help
			dispatcher.register(CommandManager.literal("bethelp")
				.executes(ctx -> {
					ServerCommandSource s = ctx.getSource();
					s.sendFeedback(() -> Text.literal("Cobblemon Betting — Player Help" ).formatted(Formatting.GOLD), false);
					s.sendFeedback(() -> Text.literal("- Currency: Numismatic coins (bronze/silver/gold); 1g=100s=10000b").formatted(Formatting.GRAY), false);
					s.sendFeedback(() -> Text.literal("- Open Betting Menu: /openbetmenu (only while betting is open)"), false);
					s.sendFeedback(() -> Text.literal("- Place Bet: Put coins in the center slot, pick A/B, click Confirm."), false);
					s.sendFeedback(() -> Text.literal("- Refund: Use 'Take Back Bet' in the menu (only while betting is open)."), false);
					s.sendFeedback(() -> Text.literal("- Props/Parlays: /openprops to add 1–2 legs then stake + place.").formatted(Formatting.AQUA), false);
					s.sendFeedback(() -> Text.literal("  • First Faint A/B; Total Faints > threshold — multipliers are configurable."), false);
					s.sendFeedback(() -> Text.literal("- Bossbar shows odds when open; switches to live counts during battle."), false);
					s.sendFeedback(() -> Text.literal("- Bet slips and sound cues may be enabled by admins."), false);
					s.sendFeedback(() -> Text.literal("More: /bethelp props | /bethelp admin" ).formatted(Formatting.DARK_GRAY), false);
					return 1;
				})
				.then(CommandManager.literal("props").executes(ctx -> {
					ServerCommandSource s = ctx.getSource();
					s.sendFeedback(() -> Text.literal("Props & Parlays" ).formatted(Formatting.AQUA), false);
					s.sendFeedback(() -> Text.literal("- Open with /openprops while betting is open."), false);
					int ff = com.example.config.PropsConfig.getFirstFaintMultiplier();
					int thr = com.example.config.PropsConfig.getTotalFaintsOverThreshold();
					int tom = com.example.config.PropsConfig.getTotalFaintsOverMultiplier();
					s.sendFeedback(() -> Text.literal(String.format("- Current: First Faint %.2fx; Total Faints > %d at %.2fx", ff/100.0, thr, tom/100.0)), false);
					s.sendFeedback(() -> Text.literal("- Parlays: legs multiply (house-backed payout)."), false);
					return 1;
				}))
				.then(CommandManager.literal("admin").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
					ServerCommandSource s = ctx.getSource();
					s.sendFeedback(() -> Text.literal("Admin Commands" ).formatted(Formatting.YELLOW), false);
					s.sendFeedback(() -> Text.literal("- /setodds <playerA> <oddsA> <playerB> <oddsB> (odds x100; 150=1.5x)"), false);
					s.sendFeedback(() -> Text.literal("- /odds add|remove|list — manage predefined odds pairs."), false);
					s.sendFeedback(() -> Text.literal("- /openadminmenu — GUI to toggle House Pays, Auto-Open, regions, fireworks."), false);
					s.sendFeedback(() -> Text.literal("- /betflags receipts|sounds|self on|off — feature toggles."), false);
					s.sendFeedback(() -> Text.literal("- /betprops show|set firstfaintmult|totaloverthr|totalovermult — props config."), false);
					s.sendFeedback(() -> Text.literal("- /betdebug menu|reset|resolve <A|B> — testing & manual resolution."), false);
					return 1;
				}))
			);

			// /betprops admin config
			dispatcher.register(CommandManager.literal("betprops")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("show").executes(ctx -> {
					var c = com.example.config.PropsConfig.class;
					ctx.getSource().sendFeedback(() -> Text.literal("First Faint Mult: " + (com.example.config.PropsConfig.getFirstFaintMultiplier()/100.0) + "x"), false);
					ctx.getSource().sendFeedback(() -> Text.literal("Total Faints Over Threshold: " + com.example.config.PropsConfig.getTotalFaintsOverThreshold()), false);
					ctx.getSource().sendFeedback(() -> Text.literal("Total Faints Over Mult: " + (com.example.config.PropsConfig.getTotalFaintsOverMultiplier()/100.0) + "x"), false);
					return 1;
				}))
				.then(CommandManager.literal("set")
					.then(CommandManager.literal("firstfaintmult")
						.then(CommandManager.argument("value", IntegerArgumentType.integer(100, 1000))
							.executes(ctx -> {
								int v = IntegerArgumentType.getInteger(ctx, "value");
								com.example.config.PropsConfig.setFirstFaintMultiplier(v);
								com.example.config.PropsConfig.saveToConfig();
								ctx.getSource().sendFeedback(() -> Text.literal("First Faint multiplier set to " + (v/100.0) + "x"), true);
								return 1;
							})
						)
					)
					.then(CommandManager.literal("totaloverthr")
						.then(CommandManager.argument("value", IntegerArgumentType.integer(1, 20))
							.executes(ctx -> {
								int v = IntegerArgumentType.getInteger(ctx, "value");
								com.example.config.PropsConfig.setTotalFaintsOverThreshold(v);
								com.example.config.PropsConfig.saveToConfig();
								ctx.getSource().sendFeedback(() -> Text.literal("Total Faints Over threshold set to " + v), true);
								return 1;
							})
						)
					)
					.then(CommandManager.literal("totalovermult")
						.then(CommandManager.argument("value", IntegerArgumentType.integer(100, 1000))
							.executes(ctx -> {
								int v = IntegerArgumentType.getInteger(ctx, "value");
								com.example.config.PropsConfig.setTotalFaintsOverMultiplier(v);
								com.example.config.PropsConfig.saveToConfig();
								ctx.getSource().sendFeedback(() -> Text.literal("Total Faints Over multiplier set to " + (v/100.0) + "x"), true);
								return 1;
							})
						)
					)
				)
			);

			// /betdebug reset
			dispatcher.register(CommandManager.literal("betdebug")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("menu").executes(ctx -> {
					var player = ctx.getSource().getPlayer();
					if (player == null) {
						ctx.getSource().sendError(Text.literal("Only players can use this."));
						return 0;
					}
					player.openHandledScreen(com.example.gui.DebugMenuFactory.create());
					return 1;
				}))
				.then(CommandManager.literal("reset").executes(ctx -> {
					BetManager.INSTANCE.clearBets();
					ctx.getSource().sendFeedback(() -> Text.literal("Bets cleared."), true);
					return 1;
				}))
				.then(CommandManager.literal("resolve")
					.then(CommandManager.argument("winner", StringArgumentType.word())
						.executes(ctx -> {
							CobblemonIntegration.onBattleEnd(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "winner"));
							ctx.getSource().sendFeedback(() -> Text.literal("Resolved bets for winner."), true);
							return 1;
						})
					)
				)
			);

			// /openadminmenu
			dispatcher.register(CommandManager.literal("odds")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("add")
					.then(CommandManager.argument("playerA", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
						.then(CommandManager.argument("oddsA", IntegerArgumentType.integer(1))
							.then(CommandManager.argument("playerB", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
								.then(CommandManager.argument("oddsB", IntegerArgumentType.integer(1))
									.executes(ctx -> {
										String aName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerA").iterator().next().getName();
										int oa = IntegerArgumentType.getInteger(ctx, "oddsA");
										String bName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerB").iterator().next().getName();
										int ob = IntegerArgumentType.getInteger(ctx, "oddsB");
										PredefinedOddsRegistry.INSTANCE.set(aName, oa, bName, ob);
										ctx.getSource().sendFeedback(() -> Text.literal("Predefined odds saved: " + aName + "=" + (oa/100.0) + "x, " + bName + "=" + (ob/100.0) + "x"), true);
										return 1;
									})
								)
							)
						)
					)
				)
				.then(CommandManager.literal("remove")
					.then(CommandManager.argument("playerA", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
						.then(CommandManager.argument("playerB", net.minecraft.command.argument.GameProfileArgumentType.gameProfile())
							.executes(ctx -> {
								String aName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerA").iterator().next().getName();
								String bName = net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument(ctx, "playerB").iterator().next().getName();
								boolean removed = PredefinedOddsRegistry.INSTANCE.remove(aName, bName);
								if (removed) ctx.getSource().sendFeedback(() -> Text.literal("Removed predefined odds for " + aName + " vs " + bName), true);
								else ctx.getSource().sendError(Text.literal("No predefined odds found for that pair."));
								return removed ? 1 : 0;
							})
						)
					)
				)
				.then(CommandManager.literal("list")
					.executes(ctx -> {
						var list = PredefinedOddsRegistry.INSTANCE.listStrings(20);
						if (list.isEmpty()) {
							ctx.getSource().sendFeedback(() -> Text.literal("No predefined odds saved."), false);
						} else {
							list.forEach(s -> ctx.getSource().sendFeedback(() -> Text.literal(s), false));
						}
						return 1;
					})
				)
			);
			dispatcher.register(CommandManager.literal("openadminmenu")
				.requires(src -> src.hasPermissionLevel(2))
				.executes(ctx -> {
					var player = ctx.getSource().getPlayer();
					if (player == null) {
						ctx.getSource().sendError(Text.literal("Only players can use this."));
						return 0;
					}
					player.openHandledScreen(AdminMenuFactory.create());
					return 1;
				})
			);
		});

		// /betodds dynamic on|off and show
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("betodds")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("dynamic")
					.then(CommandManager.literal("on").executes(ctx -> { com.example.config.DynamicOddsConfig.setEnabled(true); com.example.config.DynamicOddsConfig.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Dynamic odds ENABLED"), true); return 1; }))
					.then(CommandManager.literal("off").executes(ctx -> { com.example.config.DynamicOddsConfig.setEnabled(false); com.example.config.DynamicOddsConfig.saveToConfig(); ctx.getSource().sendFeedback(() -> Text.literal("Dynamic odds DISABLED"), true); return 1; }))
				)
				.then(CommandManager.literal("show").executes(ctx -> {
					boolean en = com.example.config.DynamicOddsConfig.isEnabled();
					double wr = com.example.config.DynamicOddsConfig.getwRating();
					double wb = com.example.config.DynamicOddsConfig.getwBST();
					double wt = com.example.config.DynamicOddsConfig.getwType();
					double ws = com.example.config.DynamicOddsConfig.getwSpeed();
					double a = com.example.config.DynamicOddsConfig.getAlpha();
					int min = com.example.config.DynamicOddsConfig.getMinOdds();
					int max = com.example.config.DynamicOddsConfig.getMaxOdds();
					double m = com.example.config.DynamicOddsConfig.getMargin();
					ctx.getSource().sendFeedback(() -> Text.literal(String.format("Dynamic: %s | wRating=%.2f wBST=%.2f wType=%.2f wSpeed=%.2f alpha=%.3f clamp=[%d,%d] margin=%.2f", en?"ON":"OFF", wr, wb, wt, ws, a, min, max, m)), false);
					return 1;
				}))
			);
		});
	}

	private static java.util.LinkedHashSet<String> parseTeamNames(String input) {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
		for (String part : input.split(",")) {
			String s = part.trim();
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}
}