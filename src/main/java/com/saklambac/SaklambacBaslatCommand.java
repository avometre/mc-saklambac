package com.saklambac;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.BossInfo;
import net.minecraft.world.server.ServerBossInfo;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.world.GameType;
import java.util.UUID;
import net.minecraft.server.management.PlayerList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.MinecraftForge;
import static com.saklambac.SaklambacMod.FLUT;
import static com.saklambac.SaklambacMod.SAKSOFON;
import net.minecraft.util.SoundEvent;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.Hand;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.nbt.ByteNBT;
import net.minecraft.nbt.IntArrayNBT;

// Sınıf dışındaki TRAMPET tanımı ve static blokları kaldırıldı. Tüm item referansları SaklambacMod.TRAMPET, SaklambacMod.FLUT, SaklambacMod.SAKSOFON olarak kullanılacak.

@Mod.EventBusSubscriber
public class SaklambacBaslatCommand {
    private static final Map<ServerPlayerEntity, ServerBossInfo> bossBars = new HashMap<>();
    private static Timer waitTimer;
    private static int waitSeconds = 60;
    // Arayanlar için kartopu refill zamanları
    private static final Map<ServerPlayerEntity, Integer> snowballRefillTimers = new HashMap<>();
    private static final Map<ServerPlayerEntity, Boolean> snowballFirstRefill = new HashMap<>();
    // Saklanan oyuncuların kartopu isabet sayacı
    private static final Map<UUID, Integer> snowballHits = new HashMap<>();
    // Saklanan oyuncuların orijinal boyutları
    private static final Map<ServerPlayerEntity, Float> originalScales = new HashMap<>();

    // Oyuncu başına sopa cooldown'u
    private static final HashMap<UUID, Long> stickCooldowns = new HashMap<>();
    private static final HashMap<UUID, Long> savurgacCooldowns = new HashMap<>();
    private static final String SAVURGAC_NAME = "Savurgac";

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("saklambacbaslat")
                .executes(context -> {
                    context.getSource().sendSuccess(
                        new StringTextComponent("Saklambac oyunu baslatildi!"), true
                    );

                    MinecraftServer server = context.getSource().getServer();
                    List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerList().getPlayers());
                    Collections.shuffle(players); // Rastgele karıştır

                    int total = players.size();
                    int seekersCount = Math.max(1, total / 4); // 1/4'ü arayan olsun (en az 1)

                    List<ServerPlayerEntity> seekers = players.subList(0, seekersCount);
                    List<ServerPlayerEntity> hiders = players.subList(seekersCount, total);

                    // Önce eski boss barları temizle
                    for (ServerBossInfo bar : bossBars.values()) {
                        bar.removeAllPlayers();
                    }
                    bossBars.clear();

                    // Tüm oyuncuların isim etiketini gizle
                    hideAllNametags(server);

                    // Boss barı tüm oyunculara göstermek için
                    ServerBossInfo waitBar = new ServerBossInfo(
                        new StringTextComponent(TextFormatting.YELLOW + "Arayan serbest kalmasina: " + waitSeconds + " sn"),
                        BossInfo.Color.YELLOW,
                        BossInfo.Overlay.NOTCHED_20
                    );
                    for (ServerPlayerEntity p : players) {
                        waitBar.addPlayer(p);
                    }

                    // Oyuncuları takımlara ata ve boss bar göster
                    for (ServerPlayerEntity p : seekers) {
                        p.sendMessage(new StringTextComponent("Sen ARAYAN takimina atandin! 1 dakika bekle!"), p.getUUID());
                        ServerBossInfo bar = new ServerBossInfo(
                            new StringTextComponent(TextFormatting.RED + "Takimin: ARAYAN"),
                            BossInfo.Color.RED,
                            BossInfo.Overlay.NOTCHED_6
                        );
                        bar.addPlayer(p);
                        bossBars.put(p, bar);
                        // Çok yüksek yavaşlık ve jump boost ile sabitle
                        p.addEffect(new EffectInstance(Effects.BLINDNESS, 20 * waitSeconds, 1));
                        p.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 20 * waitSeconds, 250));
                        p.addEffect(new EffectInstance(Effects.JUMP, 20 * waitSeconds, 128));
                        snowballFirstRefill.put(p, true);

                        // Arayanlara rastgele tabanca ve uygun mermi ver
                        String[] silahlar = {"tti_g34", "cz75", "deagle_357"};
                        String secilenSilah = silahlar[(int)(Math.random() * silahlar.length)];
                        ItemStack silah = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tac", secilenSilah)), 1);
                        if (silah != null && silah.getItem() != null) {
                            p.addItem(silah);
                            // Uygun mermiyi ver
                            ItemStack mermi = null;
                            if (secilenSilah.equals("tti_g34") || secilenSilah.equals("cz75")) {
                                mermi = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tac", "9mm_round")), 128);
                            } else if (secilenSilah.equals("deagle_357")) {
                                mermi = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tac", "ae50")), 128);
                            }
                            if (mermi != null && mermi.getItem() != null) {
                                p.addItem(mermi);
                            } else {
                                p.sendMessage(new StringTextComponent("Mermi bulunamadı!"), p.getUUID());
                            }
                        } else {
                            p.sendMessage(new StringTextComponent("Silah bulunamadı! Lütfen tac modunun yüklü ve item ID'lerinin doğru olduğundan emin ol."), p.getUUID());
                        }
                    }
                    for (ServerPlayerEntity p : hiders) {
                        p.sendMessage(new StringTextComponent("Sen SAKLANAN takimina atandin! Boyun küçültüldü!"), p.getUUID());
                        ServerBossInfo bar = new ServerBossInfo(
                            new StringTextComponent(TextFormatting.GREEN + "Takimin: SAKLANAN"),
                            BossInfo.Color.GREEN,
                            BossInfo.Overlay.NOTCHED_6
                        );
                        bar.addPlayer(p);
                        bossBars.put(p, bar);
                        
                        // Saklananlara küçültme ve yavaşlatma efekti ver
                        if (p != null && p.isAlive()) {
                            // Küçültme komutu uygula
                            p.getServer().getCommands().performCommand(
                                p.getServer().createCommandSourceStack(),
                                "shrink " + p.getName().getString() + " 0.15"
                            );

                        }
                    }
                    // Saklananlara sopa ver
                    for (ServerPlayerEntity p : hiders) {
                        if (p != null && p.isAlive()) {
                            p.getServer().getCommands().performCommand(
                                p.getServer().createCommandSourceStack(),
                                "shrink " + p.getName().getString() + " 0.15"
                            );
                            giveSavurgac(p);
                        }
                    }

                    // Kalan süreyi boss bar başlığında göster
                    waitSeconds = 60;
                    if (waitTimer != null) waitTimer.cancel();
                    waitTimer = new Timer();
                    waitTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            waitSeconds--;
                            waitBar.setName(new StringTextComponent(TextFormatting.YELLOW + "Arayan serbest kalmasina: " + waitSeconds + " sn"));
                            // Geri sayım ve bol şans title'ı artık everyone'a gönderilecek
                            List<ServerPlayerEntity> everyone = new ArrayList<>();
                            everyone.addAll(seekers);
                            everyone.addAll(hiders);
                            if (waitSeconds <= 10 && waitSeconds > 0) {
                                for (ServerPlayerEntity p : everyone) {
                                    p.connection.send(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent(String.valueOf(waitSeconds))));
                                }
                            }
                            if (waitSeconds == 0) {
                                for (ServerPlayerEntity p : everyone) {
                                    if (p != null && p.isAlive()) {
                                        p.connection.send(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent("bol sans!")));
                                    }
                                }
                                // Efektleri kaldır ve silah ver
                                for (ServerPlayerEntity p : seekers) {
                                    if (p != null && p.isAlive()) {
                                        // Title'ı temizle
                                        p.connection.send(new STitlePacket(STitlePacket.Type.CLEAR, null));
                                        p.removeEffect(Effects.BLINDNESS);
                                        p.removeEffect(Effects.MOVEMENT_SLOWDOWN);
                                        p.removeEffect(Effects.JUMP);
                                        p.sendMessage(new StringTextComponent("Artik hareket edebilirsin! Silahin verildi."), p.getUUID());
                                        
                                        // Timeless and Classics Guns silahları ver
                                        // Pistol
                                        ItemStack pistol = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("timelessandclassics", "pistol")), 1);
                                        if (pistol.getItem() != null) {
                                            p.addItem(pistol);
                                        }
                                        
                                        // Rifle
                                        ItemStack rifle = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("timelessandclassics", "rifle")), 1);
                                        if (rifle.getItem() != null) {
                                            p.addItem(rifle);
                                        }
                                        
                                        // Shotgun
                                        ItemStack shotgun = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("timelessandclassics", "shotgun")), 1);
                                        if (shotgun.getItem() != null) {
                                            p.addItem(shotgun);
                                        }
                                        
                                        // Mermi ver
                                        ItemStack ammo = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("timelessandclassics", "pistol_ammo")), 64);
                                        if (ammo.getItem() != null) {
                                            p.addItem(ammo);
                                        }
                                        
                                        // Eğer silah bulunamazsa kartopu ver
                                        if (pistol.getItem() == null && rifle.getItem() == null && shotgun.getItem() == null) {
                                            ItemStack snowball = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "snowball")), 16);
                                            p.addItem(snowball);
                                            p.sendMessage(new StringTextComponent("Silah modu bulunamadi, kartopu verildi."), p.getUUID());
                                        }
                                    }
                                }
                                waitBar.setName(new StringTextComponent(TextFormatting.GREEN + "Oyun basladi!"));
                                waitTimer.cancel();
                            }
                        }
                    }, 1000, 1000); // Her saniye

                    return Command.SINGLE_SUCCESS;
                })
        );
        
        // Oyun bitirme komutu ekle
        dispatcher.register(
            Commands.literal("saklambacbitir")
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    
                    // Tüm oyuncuların boyutunu normale döndür
                    for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                        resetPlayerScale(player);
                    }
                    
                    // Boss barları temizle
                    for (ServerBossInfo bar : bossBars.values()) {
                        bar.removeAllPlayers();
                    }
                    bossBars.clear();
                    
                    // Timer'ı durdur
                    if (waitTimer != null) {
                        waitTimer.cancel();
                        waitTimer = null;
                    }
                    
                    // Map'leri temizle
                    snowballRefillTimers.clear();
                    snowballFirstRefill.clear();
                    snowballHits.clear();
                    originalScales.clear();
                    
                    context.getSource().sendSuccess(
                        new StringTextComponent("Saklambac oyunu bitirildi!"), true
                    );
                    
                    return Command.SINGLE_SUCCESS;
                })
        );
    }

    // --- PACKET: SnowballRefillPacket ---
    public static class SnowballRefillPacket {
        public int seconds;
        public boolean isFirst;
        public SnowballRefillPacket(int seconds, boolean isFirst) { this.seconds = seconds; this.isFirst = isFirst; }
        public static void encode(SnowballRefillPacket msg, net.minecraft.network.PacketBuffer buf) { 
            buf.writeInt(msg.seconds); 
            buf.writeBoolean(msg.isFirst); 
        }
        public static SnowballRefillPacket decode(net.minecraft.network.PacketBuffer buf) { 
            return new SnowballRefillPacket(buf.readInt(), buf.readBoolean()); 
        }
        public static void handle(SnowballRefillPacket msg, java.util.function.Supplier<net.minecraftforge.fml.network.NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getPersistentData().putInt("saklambac_snowball_refill", msg.seconds);
                    mc.player.getPersistentData().putBoolean("saklambac_snowball_first", msg.isFirst);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        if (!bossBars.containsKey(player)) return;
        ServerBossInfo bar = bossBars.get(player);
        if (bar == null || bar.getColor() != BossInfo.Color.RED) return;
        // Oyun başında refill timer waitSeconds ile senkronize
        if (snowballFirstRefill.getOrDefault(player, false)) {
            if (snowballRefillTimers.containsKey(player)) {
                int t = snowballRefillTimers.get(player) - 1;
                if (t <= 0) {
                    // Mermi ver
                    ItemStack ammo = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("timelessandclassics", "pistol_ammo")), 32);
                    if (ammo.getItem() != null) {
                        player.addItem(ammo);
                    } else {
                        // Eğer mermi bulunamazsa kartopu ver
                        ItemStack snowball = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "snowball")), 16);
                        player.addItem(snowball);
                    }
                    snowballRefillTimers.remove(player);
                    com.saklambac.SaklambacMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnowballRefillPacket(0, false));
                } else {
                    snowballRefillTimers.put(player, t);
                    if (t % 20 == 0) {
                        com.saklambac.SaklambacMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnowballRefillPacket(t / 20, false));
                    }
                }
            } else {
                snowballRefillTimers.put(player, 20 * 30); // 30 saniye refill
                com.saklambac.SaklambacMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnowballRefillPacket(30, false));
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        // Sadece silah mermileri
        boolean isBullet = event.getEntity().getType().getRegistryName().getPath().contains("bullet");
        if (!isBullet) return;
        // Çarpılan entity oyuncu mu?
        if (!(event.getRayTraceResult() instanceof net.minecraft.util.math.EntityRayTraceResult)) return;
        net.minecraft.util.math.EntityRayTraceResult hitResult = (net.minecraft.util.math.EntityRayTraceResult) event.getRayTraceResult();
        if (!(hitResult.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity hitPlayer = (ServerPlayerEntity) hitResult.getEntity();
        // Sadece saklananlar için
        ScorePlayerTeam team = hitPlayer.getScoreboard().getPlayerTeam(hitPlayer.getScoreboardName());
        if (team == null || !team.getName().equals("SAKLANAN")) return;
        // Hasar verme! Sadece sayaçla ilerle
        UUID uuid = hitPlayer.getUUID();
        int hits = snowballHits.getOrDefault(uuid, 0) + 1;
        snowballHits.put(uuid, hits);
        if (hits >= 3) {
            hitPlayer.getServer().getCommands().performCommand(
                hitPlayer.getServer().createCommandSourceStack(),
                "gamemode spectator " + hitPlayer.getScoreboardName()
            );
            hitPlayer.sendMessage(new StringTextComponent("YAKALANDIN! Artik izleyicisin."), hitPlayer.getUUID());
            // Saklananlar bitti mi kontrol et
            MinecraftServer server = hitPlayer.getServer();
            List<ServerPlayerEntity> saklananlar = new ArrayList<>();
            for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
                ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                if (t != null && t.getName().equals("SAKLANAN") && p.gameMode.getGameModeForPlayer() != net.minecraft.world.GameType.SPECTATOR) {
                    saklananlar.add(p);
                }
            }
            if (saklananlar.isEmpty()) {
                // Arayanlara basit kazanma efekti (mermi ile elenen için de)
                for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
                    ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                    if (t != null && t.getName().equals("ARAYAN")) {
                        p.connection.send(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent("§b§lKAZANDINIZ!")));
                        p.connection.send(new STitlePacket(STitlePacket.Type.SUBTITLE, new StringTextComponent("§eTum saklananlar elendi!")));
                        p.sendMessage(new StringTextComponent("§a§lKAZANDINIZ! Tum saklananlar elendi!"), p.getUUID());
                    }
                }
                // Tüm saklananların envanterini temizle
                for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
                    ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                    if (t != null && t.getName().equals("SAKLANAN")) {
                        for (int i = 0; i < p.inventory.items.size(); i++) {
                            p.inventory.items.set(i, ItemStack.EMPTY);
                        }
                    }
                }
            }
        } else {
            int kalan = 3 - hits;
            hitPlayer.sendMessage(new StringTextComponent("§eMermi isabeti: " + hits + "/3 | Kalan: " + kalan), hitPlayer.getUUID());
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ScorePlayerTeam team = mc.player.getScoreboard().getPlayerTeam(mc.player.getScoreboardName());
        if (team == null || !team.getName().equalsIgnoreCase("ARAYAN")) return;
        int snowballCount = 0;
        for (ItemStack stack : mc.player.inventory.items) {
            if (stack.getItem() == ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "snowball"))) {
                snowballCount += stack.getCount();
            }
        }
        int refill = mc.player.getPersistentData().getInt("saklambac_snowball_refill");
        int x = mc.getWindow().getGuiScaledWidth() - 80;
        int y = mc.getWindow().getGuiScaledHeight() - 50;
        MatrixStack ms = event.getMatrixStack();
        ms.pushPose();
        mc.font.draw(ms, "Kartopu", x, y, 0x00FFFF);
        ms.popPose();
        String bigStr;
        if (refill > 0) {
            bigStr = String.format("%02ds", refill);
        } else {
            bigStr = String.format("%02d", snowballCount);
        }
        ms.pushPose();
        ms.scale(2.0f, 2.0f, 2.0f);
        mc.font.draw(ms, bigStr, (x+0)/2f, (y+12)/2f, 0xFFFFFF);
        ms.popPose();
    }

    // onServerTick fonksiyonu kaldırıldı, çünkü bu kod her tickte herkesi eski boyuta döndürüyor ve istenmeyen bir davranış.

    // Oyun bitişi veya reseti için çağrılacak fonksiyon:
    // resetAllPlayerScales fonksiyonunu kaldır

    // Tüm oyuncuların isim etiketini gizle
    public static void hideAllNametags(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        String teamName = "hidden_nametag";
        ScorePlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        team.setNameTagVisibility(Team.Visible.NEVER);
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    // Oyuncu boyutunu ayarla (Pehkui ile)
    private static void setPlayerScale(ServerPlayerEntity player, float scale) {
        // Orijinal boyutu kaydet
        originalScales.put(player, 1.0f); // Varsayılan boyut
        
        // Pehkui komutu ile boyutu değiştir
        String command = String.format("pehkui scale set %s %.2f", player.getScoreboardName(), scale);
        player.getServer().getCommands().performCommand(
            player.getServer().createCommandSourceStack(),
            command
        );
    }
    
    // Oyuncu boyutunu normale döndür
    private static void resetPlayerScale(ServerPlayerEntity player) {
        if (originalScales.containsKey(player)) {
            String command = String.format("pehkui scale set %s %.2f", player.getScoreboardName(), 1.0f);
            player.getServer().getCommands().performCommand(
                player.getServer().createCommandSourceStack(),
                command
            );
            originalScales.remove(player);
        }
    }

    // Saklananlara savurganlik buyulu sopa ver
    private static void giveSavurgac(ServerPlayerEntity player) {
        for (ItemStack stack : player.inventory.items) {
            if (stack.getItem() == Items.STICK && stack.hasCustomHoverName() && stack.getHoverName().getString().equals(SAVURGAC_NAME)) {
                return;
            }
        }
        ItemStack stick = new ItemStack(Items.STICK);
        stick.enchant(Enchantments.KNOCKBACK, 2);
        stick.setHoverName(new StringTextComponent(SAVURGAC_NAME).withStyle(TextFormatting.GOLD));
        CompoundNBT display = stick.getOrCreateTagElement("display");
        ListNBT lore = new ListNBT();
        lore.add(StringNBT.valueOf(ITextComponent.Serializer.toJson(new StringTextComponent("30 saniyede bir kullanilir").withStyle(TextFormatting.GRAY))));
        display.put("Lore", lore);
        player.addItem(stick);
    }

    // Sopa ile vurunca savurma ve cooldown
    @SubscribeEvent
    public static void onSavurgacHit(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity user = (ServerPlayerEntity) event.getPlayer();
        ItemStack stack = user.getMainHandItem();
        if (stack.getItem() == Items.STICK && stack.hasCustomHoverName() && stack.getHoverName().getString().equals(SAVURGAC_NAME)) {
            // Sadece saklananlar kullanabilsin
            ScorePlayerTeam team = user.getScoreboard().getPlayerTeam(user.getScoreboardName());
            if (team == null || !team.getName().equals("SAKLANAN")) return;
            // Sadece oyuncuya vurulunca
            if (!(event.getTarget() instanceof ServerPlayerEntity)) return;
            ServerPlayerEntity target = (ServerPlayerEntity) event.getTarget();
            // Cooldown kontrolü
            UUID uuid = user.getUUID();
            long now = System.currentTimeMillis();
            long last = savurgacCooldowns.getOrDefault(uuid, 0L);
            if (now - last < 30000) {
                int kalan = (int)((30000 - (now - last)) / 1000);
                user.sendMessage(new StringTextComponent("Savurgac tekrar kullanilabilir: " + kalan + " sn sonra!"), uuid);
                event.setCanceled(true);
                return;
            }
            savurgacCooldowns.put(uuid, now);
            // Savurma
            double dx = target.getX() - user.getX();
            double dz = target.getZ() - user.getZ();
            double dist = Math.sqrt(dx*dx + dz*dz);
            if (dist > 0.1) {
                double power = 1.5;
                target.push(dx/dist * power, 0.4, dz/dist * power);
            }
            // Hasari sifirla
            event.setCanceled(true);
            // Sopa kesinlikle silinsin (daha kesin yöntem)
            user.inventory.removeItem(user.inventory.selected, 1);
            user.inventoryMenu.broadcastChanges();
            user.sendMessage(new StringTextComponent("Savurgac kullandin! 30 saniye sonra tekrar verilecek."), uuid);
            // 30 saniye sonra tekrar ver
            user.getServer().execute(() -> {
                try { Thread.sleep(30000); } catch (InterruptedException ignored) {}
                giveSavurgac(user);
            });
        }
    }

    @SubscribeEvent
    public static void onInstrumentUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() == FLUT || stack.getItem() == SAKSOFON) {
            // Test mesajı - kod çalışıyor mu?
            event.getPlayer().sendMessage(new StringTextComponent("§a[Saklambac] Enstrüman kullanıldı!"), event.getPlayer().getUUID());
            
            // Sadece server tarafında çalışsın
            if (event.getPlayer().level.isClientSide()) return;
            
            ServerPlayerEntity user = (ServerPlayerEntity) event.getPlayer();
            double radius = 6.0;
            List<ServerPlayerEntity> players = user.getLevel().getEntitiesOfClass(ServerPlayerEntity.class, user.getBoundingBox().inflate(radius));
            for (ServerPlayerEntity p : players) {
                if (p == user) continue;
                ScorePlayerTeam team = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                if (team != null && team.getName().equals("ARAYAN")) {
                    double dx = p.getX() - user.getX();
                    double dz = p.getZ() - user.getZ();
                    double dist = Math.sqrt(dx*dx + dz*dz);
                    if (dist > 0.1) {
                        double power = 1.5;
                        p.push(dx/dist * power, 0.3, dz/dist * power);
                    }
                }
            }
            // Rastgele ses seç
            String[] flutSounds = {"flut_1", "flut_2", "flut_3"};
            String[] saksofonSounds = {"saksofon_1", "saksofon_2", "saksofon_3"};
            String sound;
            if (stack.getItem() == FLUT) {
                sound = flutSounds[(int)(Math.random() * flutSounds.length)];
            } else {
                sound = saksofonSounds[(int)(Math.random() * saksofonSounds.length)];
            }
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.util.ResourceLocation("saklambac", sound));
            System.out.println("Saklambac: Çalınacak ses: " + sound + ", SoundEvent: " + soundEvent);
            if (soundEvent != null) {
                user.level.playSound(null, user.blockPosition(), soundEvent, net.minecraft.util.SoundCategory.PLAYERS, 1.0f, 1.0f);
                System.out.println("Saklambac: Ses çalındı!");
            } else {
                System.out.println("Saklambac: SoundEvent bulunamadı!");
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStickUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() == net.minecraft.item.Items.STICK) {
            // Sadece server tarafında çalışsın
            if (event.getPlayer().level.isClientSide()) return;
            ServerPlayerEntity user = (ServerPlayerEntity) event.getPlayer();
            UUID uuid = user.getUUID();
            long now = System.currentTimeMillis();
            long last = stickCooldowns.getOrDefault(uuid, 0L);
            if (now - last < 30000) {
                int kalan = (int)((30000 - (now - last)) / 1000);
                user.sendMessage(new StringTextComponent("Savurgac tekrar kullanilabilir: " + kalan + " sn sonra!"), uuid);
                return;
            }
            stickCooldowns.put(uuid, now);
            double radius = 6.0;
            List<ServerPlayerEntity> players = user.getLevel().getEntitiesOfClass(ServerPlayerEntity.class, user.getBoundingBox().inflate(radius));
            int savurulan = 0;
            for (ServerPlayerEntity p : players) {
                if (p == user) continue;
                double dx = p.getX() - user.getX();
                double dz = p.getZ() - user.getZ();
                double dist = Math.sqrt(dx*dx + dz*dz);
                if (dist > 0.1) {
                    double power = 1.5;
                    p.push(dx/dist * power, 0.4, dz/dist * power);
                    savurulan++;
                }
            }
            user.sendMessage(new StringTextComponent("§aSavurgaç kullanıldı! Savrulan kişi: " + savurulan), uuid);
            event.setCanceled(true);
        }
    }

    // Saklanan ölünce spectator moda geç (daha güvenli)
    @SubscribeEvent
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
        ScorePlayerTeam team = player.getScoreboard().getPlayerTeam(player.getScoreboardName());
        if (team != null && team.getName().equals("SAKLANAN")) {
            // Ölümü iptal et ve spectator moda geç
            event.setCanceled(true);
            player.setHealth(1.0f);
            player.setGameMode(net.minecraft.world.GameType.SPECTATOR);
            player.sendMessage(new StringTextComponent("Öldun! Artik izleyicisin."), player.getUUID());
            
            // 1 tick sonra kontrol et (gecikme için)
            player.getServer().execute(() -> {
                checkWinCondition(player.getServer());
            });
        }
    }

    // Kazanma durumu kontrolü (ayrı fonksiyon)
    private static void checkWinCondition(MinecraftServer server) {
        List<ServerPlayerEntity> saklananlar = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
            ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
            if (t != null && t.getName().equals("SAKLANAN") && p.gameMode.getGameModeForPlayer() != net.minecraft.world.GameType.SPECTATOR) {
                saklananlar.add(p);
            }
        }
        if (saklananlar.isEmpty()) {
            // Arayanlara kazanma efekti
            for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
                ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                if (t != null && t.getName().equals("ARAYAN")) {
                    p.connection.send(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent("§b§lKAZANDINIZ!")));
                    p.connection.send(new STitlePacket(STitlePacket.Type.SUBTITLE, new StringTextComponent("§eTum saklananlar elendi!")));
                    p.sendMessage(new StringTextComponent("§a§lKAZANDINIZ! Tum saklananlar elendi!"), p.getUUID());
                }
            }
            // Saklananların envanterini temizle
            for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) {
                ScorePlayerTeam t = p.getScoreboard().getPlayerTeam(p.getScoreboardName());
                if (t != null && t.getName().equals("SAKLANAN")) {
                    for (int i = 0; i < p.inventory.items.size(); i++) {
                        p.inventory.items.set(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }
} 